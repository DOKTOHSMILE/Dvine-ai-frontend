package ai.dvine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import ai.dvine.act.Actions;
import ai.dvine.brain.BrainAnswer;
import ai.dvine.brain.LearningJournal;
import ai.dvine.brain.OfflineBrain;
import ai.dvine.brain.Onboarding;
import ai.dvine.brain.PendingAction;
import ai.dvine.face.FaceMode;
import ai.dvine.face.Look;
import ai.dvine.face.StageListener;
import ai.dvine.face.StageView;
import ai.dvine.net.Connectivity;
import ai.dvine.net.KnowledgeStore;
import ai.dvine.net.SyncWorker;
import ai.dvine.turn.LocalTurn;
import ai.dvine.turn.TurnLine;
import ai.dvine.turn.TurnRoute;
import ai.dvine.voice.AmbientPad;
import ai.dvine.voice.ListenBridge;
import ai.dvine.voice.ListenCallback;
import ai.dvine.voice.TalkBridge;

/**
 * No screen UI. One persona on black; swipe to speak. Anything that leaves
 * the app is asked out loud first and runs only after a spoken yes.
 */
public final class MainActivity extends Activity implements StageListener, ListenCallback {
    private static final int MIC = 21;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** The on-device brain-and-body control engine (primary functions; runs offline). */
    private ai.dvine.engine.Engine engine;
    /** Behaviour rate: about 10 Hz, between the 60 Hz frame loop and the slower brain. */
    private final Runnable engineTick = new Runnable() {
        @Override
        public void run() {
            engine.tick(android.os.SystemClock.uptimeMillis());
            handler.postDelayed(this, 100L);
        }
    };
    private StageView stage;
    private TalkBridge talk;
    private ListenBridge listen;
    private AmbientPad ambient;
    private OfflineBrain brain;
    private final ai.dvine.brain.ContextExaminer examiner = new ai.dvine.brain.ContextExaminer();
    // the on-device language model (llama.cpp + Qwen3 1.7B): answers what the offline brain can't, offline
    private ai.dvine.llm.LocalModel model;
    private final java.util.concurrent.ExecutorService modelThread = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean confirmModelDownload, confirmModelRemove, streamOpen;
    private ai.dvine.llm.ModelHub hub;
    private ai.dvine.llm.DownloadStatus.Phase lastPhase = ai.dvine.llm.DownloadStatus.Phase.IDLE;
    private boolean downloadBarHidden;
    private static final int NOTIFY = 22;

    /** The background download changed: the card on screen, the activity panel, and a word when it ends. */
    private final Runnable onDownload = () -> {
        ai.dvine.llm.DownloadStatus st = hub.status;
        ai.dvine.llm.DownloadStatus.Phase phase = st.phase();
        boolean show = !downloadBarHidden && (st.active() || phase != lastPhase && phase != ai.dvine.llm.DownloadStatus.Phase.IDLE);
        stage.setDownload(show || stage.downloadShown() && !downloadBarHidden, "Downloading " + model.spec.name, st.percent(),
                phase == ai.dvine.llm.DownloadStatus.Phase.DOWNLOADING, st.detail());
        if (phase == lastPhase) {
            return;
        }
        boolean wasRunning = lastPhase == ai.dvine.llm.DownloadStatus.Phase.DOWNLOADING
                || lastPhase == ai.dvine.llm.DownloadStatus.Phase.CONNECTING;
        lastPhase = phase;
        switch (phase) {
            case DONE:
                stage.activity("DOWNLOAD", "verified");
                stage.setDownload(true, model.spec.name + " downloaded", 100, false, "verified, starting it now");
                handler.postDelayed(() -> stage.setDownload(false, "", 100, false, ""), 6000);
                loadModel(true);
                break;
            case PAUSED:
                stage.activity("DOWNLOAD", "paused at " + st.percent() + "%");
                break;
            case BAD_FILE:
            case FAILED:
                if (wasRunning) {
                    play(LocalTurn.speakAs(stage.isIvie(), st.spoken()), null);
                }
                break;
            default:
                break;
        }
    };
    private LearningJournal journal;
    private Connectivity connectivity;
    private SyncWorker sync;
    /** M7: questions the phone can't answer go to the server's Obi/Ivie desk when online. */
    private ai.dvine.net.DeskClient desk;
    private final java.util.concurrent.ExecutorService deskThread = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int askId;
    /** ANRE: the particles build researched, editable 3D objects. */
    private ai.dvine.create.CreationDirector creation;
    private ai.dvine.net.WebClient web;
    private SharedPreferences prefs;

    private List<TurnLine> lines = new ArrayList<TurnLine>();
    private int index;
    /** Dual-AI: the discussion in progress (cancelled by any new input), and lines held by "pause". */
    private ai.dvine.turn.DualAgentCoordinator discussion;
    private List<TurnLine> paused;
    private int playId;
    private Runnable afterPlay;

    private boolean awaitingName;
    private int nameTries;

    /** Saves any crash to files/crash.txt, then lets Android close the app as usual. */
    private void installCrashReporter() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        final File out = new File(getFilesDir(), "crash.txt");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(out))) {
                w.println("DVINE " + BuildConfigInfo.version(this) + " on Android " + android.os.Build.VERSION.RELEASE
                        + " (" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + ")");
                error.printStackTrace(w);
            } catch (java.io.IOException ignored) {
                // nothing more we can do
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private String takeCrashReport() {
        File f = new File(getFilesDir(), "crash.txt");
        if (!f.exists()) {
            return null;
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashReporter();
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(0xFF000000);
        window.setNavigationBarColor(0xFF000000);
        window.setDecorFitsSystemWindows(false);
        stage = new StageView(this);
        stage.setListener(this);
        // the 3D wireframe human renders with OpenGL under the transparent stage overlay
        final ai.dvine.render.HumanView humanView = new ai.dvine.render.HumanView(this);
        final ai.dvine.render.HumanStage humanStage = new ai.dvine.render.HumanStage(humanView.renderer);
        stage.attachHuman(humanStage, humanView);
        humanStage.load(name -> getAssets().open(name), null);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(humanView);
        root.addView(stage);
        setContentView(root);
        // only after setContentView: before it the window has no decor and this throws
        WindowInsetsController bars = window.getInsetsController();
        if (bars != null) {
            bars.hide(WindowInsets.Type.systemBars());
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        prefs = getSharedPreferences("dvine", MODE_PRIVATE);
        talk = new TalkBridge(this);
        listen = new ListenBridge(this);
        ambient = new AmbientPad();
        ambient.setEnabled(prefs.getBoolean("music", true));
        stage.setSubtitles(prefs.getBoolean("subtitles", true));
        Look obiLook = Look.parse(prefs.getString("look_obi", null), false);
        Look ivieLook = Look.parse(prefs.getString("look_ivie", null), true);
        if (prefs.getInt("look_version", 1) < 2) {
            // looks saved before the 3D wireframe human was the default: keep the shape, switch the style once
            obiLook.style = Look.Style.WIRE;
            ivieLook.style = Look.Style.WIRE;
            prefs.edit().putInt("look_version", 2).putString("look_obi", obiLook.serialize())
                    .putString("look_ivie", ivieLook.serialize()).apply();
        }
        if (prefs.getInt("look_version", 1) < 3) {
            // v0.14: lifelike skin becomes the default look (the hologram stays a mode)
            obiLook.style = Look.Style.SKIN;
            ivieLook.style = Look.Style.SKIN;
            prefs.edit().putInt("look_version", 3).putString("look_obi", obiLook.serialize())
                    .putString("look_ivie", ivieLook.serialize()).apply();
        }
        if (prefs.getInt("look_version", 1) < 4) {
            // v0.19: back to the line-art faces of the DVINE design (the 3D body stays a mode: "skin mode")
            obiLook.style = Look.Style.TOON;
            ivieLook.style = Look.Style.TOON;
            obiLook.beard = false;
            prefs.edit().putInt("look_version", 4).putString("look_obi", obiLook.serialize())
                    .putString("look_ivie", ivieLook.serialize()).apply();
        }
        if (prefs.getInt("look_version", 1) < 5) {
            // v0.22: the line-art screen is gone; the main screen is the full-body cartoon in the 3D stage
            obiLook.style = Look.Style.TOON;
            ivieLook.style = Look.Style.TOON;
            prefs.edit().putInt("look_version", 5).putString("look_obi", obiLook.serialize())
                    .putString("look_ivie", ivieLook.serialize()).apply();
        }
        stage.setLook(false, obiLook);
        stage.setLook(true, ivieLook);
        talk.setTuning(false, ai.dvine.voice.VoiceTuning.parse(prefs.getString("voice_obi", null), false));
        talk.setTuning(true, ai.dvine.voice.VoiceTuning.parse(prefs.getString("voice_ivie", null), true));
        talk.setWordListener(stage::speechWord);
        talk.setListener((ivie, speaking) -> {
            ambient.setPersona(ivie);
            ambient.setLevel(speaking ? 1f : 0f);
        });

        KnowledgeStore store = new KnowledgeStore(this);
        journal = new LearningJournal(new File(getFilesDir(), "learning"));
        // core pack now (small); the dictionary and other shards load in the background
        brain = new OfflineBrain(store.loadCore(), journal, System::currentTimeMillis, TimeZone.getDefault());
        brain.setThoughts(prefs.getBoolean("thoughts", true));
        Thread loader = new Thread(() -> {
            try {
                final ai.dvine.brain.KnowledgePack full = store.load();
                handler.post(() -> brain.replacePack(full));
            } catch (RuntimeException broken) {
                // keep the core pack
            }
        }, "dvine-knowledge");
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
        brain.setUserName(prefs.getString("user_name", null));
        brain.setLooks(obiLook, ivieLook);
        hub = ai.dvine.llm.ModelHub.get(this);
        model = hub.model;
        hub.addListener(onDownload);
        reportModel();
        if (hub.downloading() || hub.status.active()) {
            onDownload.run(); // the download carried on while the app was closed: show where it is
        }
        if (model.installed() && prefs.getBoolean("llm_on", true)) {
            loadModel(false);
        }
        final ai.dvine.net.SessionClient session = new ai.dvine.net.SessionClient(this::serverUrl, prefs,
                new ai.dvine.net.DeviceVault(prefs));
        sync = new SyncWorker(this::serverUrl, session::deviceId, journal, store,
                session::token, pack -> {
                    // a newer core pack arrived: re-merge it with the dictionary and shards
                    final ai.dvine.brain.KnowledgePack full = store.load();
                    handler.post(() -> brain.replacePack(full));
                });
        desk = new ai.dvine.net.DeskClient(session::serverRoot, session::token, session::invalidate,
                new ai.dvine.net.DeskClient.RoomStore() {
                    @Override
                    public String get() {
                        return prefs.getString("desk_room", null);
                    }

                    @Override
                    public void put(String roomId) {
                        prefs.edit().putString("desk_room", roomId).apply();
                    }
                }, null);
        web = new ai.dvine.net.WebClient(session::serverRoot, session::token, session::invalidate, null);
        creation = new ai.dvine.create.CreationDirector(humanStage.objects, new File(getFilesDir(), "designs"));
        engine = new ai.dvine.engine.Engine(stage, getFilesDir(), journal::consent);
        sync.setEpisodes(engine.episodes, profile -> handler.post(
                () -> engine.applyProfile(profile, android.os.SystemClock.uptimeMillis())));
        sync.setUploadListener(count -> handler.post(() -> {
            stage.burst(12);
            stage.setSubtitle("Backed up " + count + (count == 1 ? " thing" : " things") + " I learned.");
        }));
        connectivity = new Connectivity(this, online -> {
            stage.setOnline(online);
            if (online) {
                sync.kick(brain.pack().version);
            }
            // research for 3D objects goes through the server while online
            creation.setFetcher(online && serverUrl() != null && serverUrl().trim().startsWith("https://") ? web::fetch : null);
        });

        stage.wake();
        handler.postDelayed(this::greetOnOpen, 700);
    }

    private void greetOnOpen() {
        if (takeCrashReport() != null) {
            play(LocalTurn.speakAs(true, "I closed unexpectedly last time. Say copy the crash report, and I'll put the details on your clipboard so you can send them to the developer."),
                    this::greetAfterCrashNotice);
            return;
        }
        greetAfterCrashNotice();
    }

    private void greetAfterCrashNotice() {
        String name = brain.userName();
        if (name == null) {
            awaitingName = true;
            nameTries = 0;
            List<TurnLine> l = new ArrayList<TurnLine>();
            l.add(new TurnLine("obi", "SAY", Onboarding.welcomeQuestion()));
            stage.gesture("👋");
            play(l, this::startListening);
        } else {
            List<TurnLine> l = new ArrayList<TurnLine>();
            l.add(new TurnLine(stage.agent(), "SAY", "Welcome back, " + name + "."));
            stage.gesture("👋");
            play(l, null);
        }
    }

    /** Server address: the one set by voice, else the build's api_base. */
    private String serverUrl() {
        String set = prefs.getString("server", null);
        return set != null && !set.isEmpty() ? set : getString(R.string.api_base);
    }

    /** Uploads right away when online; otherwise the next time we come online. */
    private void syncSoon() {
        if (connectivity != null && connectivity.isOnline()) {
            sync.kick(brain.pack().version);
        }
    }

    // ------------------------------------------------------------ the one entry point

    private void answer(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        askId++; // a newer question makes any desk answer still on its way stale
        if (discussion != null) {
            discussion.cancel(); // the user interrupts: queued turns are dropped
            discussion = null;
        }
        String said = text.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        if (modelCommand(said)) {
            return;
        }
        if (said.matches("^(pause|hold on|wait|hang on)( please)?$") && index < lines.size()) {
            paused = new ArrayList<TurnLine>(lines.subList(Math.max(0, index - 1), lines.size()));
            stopPlay();
            stage.setMode(FaceMode.LISTENING);
            return;
        }
        if (said.matches("^(resume|continue|go on|carry on)( please)?$") && paused != null) {
            List<TurnLine> rest = paused;
            paused = null;
            play(rest, null);
            return;
        }
        paused = null;
        if (awaitingName) {
            takeName(text);
            return;
        }
        if (text.toLowerCase(java.util.Locale.US).matches(".*copy (the |my )?crash( report| log)?.*")) {
            String report = takeCrashReport();
            if (report == null) {
                play(LocalTurn.speakAs(stage.isIvie(), "There's no crash report. Everything has been running fine."), null);
            } else if (Actions.copy(this, report)) {
                new File(getFilesDir(), "crash.txt").delete();
                stage.burst(18);
                play(LocalTurn.speakAs(stage.isIvie(), "Copied. The crash report is on your clipboard; paste it to the developer."), null);
            }
            return;
        }
        // read the whole sentence first: who it is for, whether it is negated, what "it" means, corrections, several requests
        ai.dvine.brain.ContextExaminer.Reading reading = examiner.examine(text);
        stage.activity("VOICE", text.trim());
        stage.activity("CONTEXT", reading.summary());
        if (reading.kind == ai.dvine.brain.ContextExaminer.Kind.NEVER_MIND) {
            play(LocalTurn.speakAs(stage.isIvie(), "Okay, never mind."), null);
            return;
        }
        if (reading.negated) {
            String not = reading.core.replaceFirst("^(please do not|do not|never) ", "").replaceAll("\\bmy\\b", "your");
            play(LocalTurn.speakAs(stage.isIvie(), "Okay, I won't " + not + "."), null);
            return;
        }
        if (reading.clarify != null) {
            play(LocalTurn.speakAs(stage.isIvie(), reading.clarify), this::startListening);
            return;
        }
        final String asked = reading.rewritten ? reading.resolved : text;
        if (asked.toLowerCase(java.util.Locale.US).matches("^(clear|hide|remove|erase|delete) (the |your |that )?(drawing|picture|sketch|image)$")) {
            stage.clearDrawing();
            play(LocalTurn.speakAs(stage.isIvie(), "Cleared."), null);
            return;
        }
        if (create(asked)) {
            return;
        }
        ai.dvine.turn.DualAgentCoordinator.Request both = ai.dvine.turn.DualAgentCoordinator.parse(asked);
        if (both != null) {
            discuss(both);
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        engine.onHeard(asked, now);
        TurnRoute route = LocalTurn.route(asked, stage.agent());
        if (route.switchOnly) {
            play(route.lines, null);
            return;
        }
        final BrainAnswer reply;
        synchronized (brain) { // a cancelled discussion may still be finishing a turn on its thread
            reply = respondInContext(asked, reading);
        }
        stage.activity("ANSWER", reply.answered ? "from " + reply.source.toLowerCase(java.util.Locale.US) : "not in my notes yet");
        engine.setPersona(stage.agent(), now);
        if (reply.newName != null) {
            prefs.edit().putString("user_name", reply.newName).apply();
        }
        if ("VOICE_TEST".equals(reply.command)) {
            List<TurnLine> test = new ArrayList<TurnLine>();
            test.add(new TurnLine("obi", "SAY", "This is Obi."));
            test.add(new TurnLine("ivie", "SAY", "And this is Ivie."));
            String missing = talk.missingGender();
            if (missing != null) {
                boolean ivieBorrows = "female".equals(missing);
                test.add(new TurnLine(ivieBorrows ? "ivie" : "obi", "SAY", "This phone has no " + missing
                        + " voice, so I'm borrowing one and tuning it. Say install voices to add one."));
            }
            play(test, null);
            return;
        }
        if ("VOICE_INSTALL".equals(reply.command)) {
            play(reply.lines, () -> {
                try {
                    startActivity(new android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                } catch (RuntimeException noInstaller) {
                    try {
                        startActivity(new android.content.Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (RuntimeException none) {
                        play(LocalTurn.speakAs(stage.isIvie(), "I couldn't open the voice settings on this phone."), null);
                    }
                }
            });
            return;
        }
        if (reply.command != null && reply.command.startsWith("VOICE_")) {
            if (talk.adjust(reply.lookIvie, reply.command)) {
                prefs.edit().putString(reply.lookIvie ? "voice_ivie" : "voice_obi",
                        talk.tuning(reply.lookIvie).serialize()).apply();
            }
        } else {
            applyCommand(reply.command, reply.commandValue);
        }
        if ("SERVER_SET".equals(reply.command) && reply.commandValue != null) {
            prefs.edit().putString("server", reply.commandValue).apply();
            syncSoon();
        }
        if ("LEARN".equals(reply.source) || "MEMORY".equals(reply.source) || "OFFLINE".equals(reply.source)) {
            syncSoon(); // new learning (or a saved unanswered question) goes up as soon as we can
        }
        if (reply.look != null) {
            stage.setLook(reply.lookIvie, reply.look);
            prefs.edit().putString(reply.lookIvie ? "look_ivie" : "look_obi", reply.look.serialize()).apply();
        }
        // the brain proposes; the engine validates, arbitrates and moves the body
        engine.perform(reply, now);
        if ("SELF_REPORT".equals(reply.command)) {
            play(LocalTurn.speakAs(stage.isIvie(), engine.selfReport(now) + " " + stage.inspect()), null);
            return;
        }
        if ("FAVOURITES".equals(reply.command)) {
            play(LocalTurn.speakAs(stage.isIvie(), engine.favouritesLine()), null);
            return;
        }
        final PendingAction act = reply.action;
        if (act != null && act.kind == PendingAction.Kind.COPY) {
            if (Actions.copy(this, act.content)) {
                stage.burst(18);
                play(reply.lines, null);
            } else {
                play(LocalTurn.speakAs(stage.isIvie(), "The clipboard refused that. Nothing was copied."), null);
            }
            return;
        }
        if (act != null && act.kind == PendingAction.Kind.SHARE) {
            play(reply.lines, () -> {
                stage.burst(18);
                if (!Actions.share(this, act)) {
                    play(LocalTurn.speakAs(stage.isIvie(), "I couldn't find an app to share with on this phone."), null);
                }
            });
            return;
        }
        // a drawing with no built-in form: the language model describes it in shapes and the particles form it
        String drawWhat = ai.dvine.llm.DrawPrompt.subject(asked);
        boolean canDraw = model != null && model.ready() && prefs.getBoolean("llm_on", true)
                || connectivity != null && connectivity.isOnline();
        if (drawWhat != null && !"DRAW".equals(reply.source) && canDraw) {
            askDrawing(drawWhat);
            return;
        }
        // knowledge and conversation go to the language model when it's installed; commands, maths, time, memory,
        // what the user taught and anything that acts stay with the offline brain (exact, and instant)
        boolean modelTopic = (!reply.answered || reply.needsOnline || MODEL_SOURCES.contains(reply.source)) && plain(reply);
        if (modelTopic && model != null && model.ready() && prefs.getBoolean("llm_on", true) && askLocal(asked, reply)) {
            return;
        }
        if (modelTopic && offerModel(reply)) {
            return;
        }
        if (reply.needsOnline && askDesk(asked, reply)) {
            return;
        }
        play(reply.lines, reply.listenAfter ? this::startListening : null);
    }

    // ------------------------------------------------------------ the on-device language model

    /** Answer sources the language model does better: dictionary, packed facts, wisdom, comfort, unknown questions. */
    private static final java.util.Set<String> MODEL_SOURCES = new java.util.HashSet<String>(
            java.util.Arrays.asList("DICT", "PACK", "WISDOM", "FACT", "CARE", "OFFLINE"));

    /**
     * Once, the first time a question would have gone to the language model but it isn't installed: the offline
     * answer, then the offer to install it.
     */
    private boolean offerModel(BrainAnswer reply) {
        if (model == null || model.installed() || prefs.getBoolean("llm_offered", false)
                || model.state() == ai.dvine.llm.LocalModel.State.UNSUPPORTED || model.state() == ai.dvine.llm.LocalModel.State.DOWNLOADING
                || !ai.dvine.llm.NativeLlm.available()) {
            return false;
        }
        prefs.edit().putBoolean("llm_offered", true).apply();
        List<TurnLine> lines2 = new ArrayList<TurnLine>(reply.lines);
        String agent = stage.isIvie() ? "ivie" : "obi";
        lines2.add(new TurnLine(agent, "SAY", "By the way, I can answer questions like this much better with my own language model."
                + " It runs on your phone, even offline, after a " + model.spec.sizeWords() + " download. Shall I install it?"));
        confirmModelDownload = true;
        play(lines2, this::startListening);
        return true;
    }

    /** The model's state for the activity panel at start. */
    private void reportModel() {
        String line;
        if (model.state() == ai.dvine.llm.LocalModel.State.UNSUPPORTED) {
            line = model.problem();
        } else if (!ai.dvine.llm.NativeLlm.available()) {
            line = "not in this build (the native library didn't load)";
        } else if (model.installed()) {
            line = "starting " + model.spec.name;
        } else {
            line = "not installed: say \"download your brain\"";
        }
        stage.activity("MODEL", line);
    }

    /**
     * The language model answers a question the offline brain couldn't: in the persona's voice, spoken sentence by
     * sentence while the rest is still being written. Anything new the user says stops it.
     */
    private boolean askLocal(final String question, final BrainAnswer offline) {
        final int id = ++askId;
        final boolean ivie = stage.isIvie();
        final String agent = ivie ? "ivie" : "obi";
        stopPlay();
        stage.setThought(ivie ? "Let me think about that…" : "Thinking it through…");
        stage.setMode(FaceMode.THINKING);
        stage.activity("MODEL", "answering offline with " + model.spec.name);
        final List<TurnLine> stream = new ArrayList<TurnLine>();
        streamOpen = true;
        lines = stream;
        index = 0;
        afterPlay = null;
        step(playId);
        final String system = ai.dvine.llm.PersonaPrompt.system(ivie, brain.userName());
        modelThread.execute(() -> {
            final ai.dvine.llm.SpeechChunker chunker = new ai.dvine.llm.SpeechChunker();
            final String all = model.reply(system, question, 220, text -> {
                if (id != askId) {
                    return false;
                }
                final List<String> ready = chunker.add(text);
                if (!ready.isEmpty()) {
                    handler.post(() -> {
                        if (id == askId) {
                            for (String sentence : ready) {
                                stream.add(new TurnLine(agent, "SAY", sentence));
                            }
                        }
                    });
                }
                return true;
            });
            final String rest = chunker.flush();
            handler.post(() -> {
                if (id != askId) {
                    return;
                }
                if (all == null && stream.isEmpty()) {
                    stream.addAll(offline.lines); // the model failed: the offline answer instead
                } else if (!rest.isEmpty()) {
                    stream.add(new TurnLine(agent, "SAY", rest));
                }
                streamOpen = false;
            });
        });
        return true;
    }

    /**
     * "Draw me a lighthouse" with no built-in form: online, a reference picture of it (Wikipedia's lead image) becomes
     * the particles; offline (or when none is found), the language model plans it as parts and shapes. The particles
     * form it beside the persona.
     */
    private void askDrawing(final String subject) {
        final int id = ++askId;
        final boolean ivie = stage.isIvie();
        final boolean online = connectivity != null && connectivity.isOnline();
        stopPlay();
        stage.setThought((online ? "Finding a picture of " : "Sketching ") + subject + "\u2026");
        stage.setMode(FaceMode.THINKING);
        stage.activity("DRAW", subject);
        modelThread.execute(() -> {
            ai.dvine.render.field.Shape picture = null;
            boolean photo = false;
            if (online) {
                android.graphics.Bitmap bm = ai.dvine.render.field.Raster.decode(ai.dvine.render.field.PictureFinder.find(subject));
                if (bm != null && id == askId) {
                    picture = ai.dvine.render.field.Raster.image(bm, ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f);
                    photo = picture.count > 200;
                }
            }
            if (!photo && id == askId && model != null && model.ready()) {
                String planned = model.task(ai.dvine.llm.DrawPrompt.system(), ai.dvine.llm.DrawPrompt.user(subject), 360, text -> id == askId);
                ai.dvine.render.field.DrawSpec d = ai.dvine.llm.DrawPrompt.read(planned);
                picture = d.items.size() < 2 ? null : ai.dvine.render.field.Raster.drawing(d, ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f);
            }
            final ai.dvine.render.field.Shape shown = photo || picture != null && picture.count > 0 ? picture : null;
            final boolean fromPhoto = photo;
            handler.post(() -> {
                if (id != askId || isFinishing()) {
                    return;
                }
                stage.setThought(null);
                if (shown == null) {
                    play(LocalTurn.speakAs(ivie, "I tried to draw " + subject + ", but it didn't come out right. Ask me again?"), null);
                    return;
                }
                stage.showPicture(shown, subject);
                play(LocalTurn.speakAs(ivie, "Here's " + ai.dvine.llm.DrawPrompt.article(subject)
                        + (fromPhoto ? ", from a picture I found online, made of particles." : ", drawn in particles. It's a rough sketch.")), null);
            });
        });
    }

    /** Loads the installed model in the background; {@code tell} says so aloud when it's ready. */
    private void loadModel(final boolean tell) {
        modelThread.execute(() -> {
            final boolean ok = model.load(ai.dvine.llm.LocalModel.threads(Runtime.getRuntime().availableProcessors()));
            handler.post(() -> {
                stage.activity("MODEL", ok ? model.spec.name + " ready, offline" : model.problem());
                if (tell) {
                    play(LocalTurn.speakAs(stage.isIvie(), ok ? "My language model is ready. Ask me anything, even offline."
                            : "I couldn't start my language model: " + model.problem() + "."), null);
                }
            });
        });
    }

    /** Voice control of the language model: install, cancel, remove, on, off, status (and the yes or no that follows). */
    private boolean modelCommand(String said) {
        if (model == null) {
            return false;
        }
        boolean yes = said.matches("^(yes|yeah|yep|ok|okay|sure|go ahead|start|do it|please do)( please)?$");
        boolean no = said.matches("^(no|nope|not now|cancel|don't|do not|leave it)( please)?$");
        if (confirmModelDownload && (yes || no)) {
            confirmModelDownload = false;
            if (yes) {
                startModelDownload();
            } else {
                play(LocalTurn.speakAs(stage.isIvie(), "Okay, not now. Say download your brain whenever you're ready."), null);
            }
            return true;
        }
        if (confirmModelRemove && (yes || no)) {
            confirmModelRemove = false;
            String line = !yes ? "Okay, I'll keep it." : model.uninstall() ? "Done. My language model is removed; I'm on my offline notes again."
                    : "I couldn't delete the model file.";
            play(LocalTurn.speakAs(stage.isIvie(), line), null);
            return true;
        }
        confirmModelDownload = false;
        confirmModelRemove = false;
        String thing = "(your |the |a |my )?(brain|language model|llm|ai model|offline model|model)";
        boolean ivie = stage.isIvie();
        if (said.matches("^(please )?(download|install|get|add|set up)( me)? " + thing + "( please)?$")
                || said.matches("^(upgrade your brain|make yourself smarter|get smarter)$")) {
            if (model.state() == ai.dvine.llm.LocalModel.State.UNSUPPORTED) {
                play(LocalTurn.speakAs(ivie, "I can't run a language model on this phone: " + model.problem() + "."), null);
            } else if (model.installed()) {
                play(LocalTurn.speakAs(ivie, model.ready() ? "My language model is already installed and running."
                        : "It's already installed. Starting it now."), null);
                if (!model.ready()) {
                    loadModel(true);
                }
            } else if (model.state() == ai.dvine.llm.LocalModel.State.DOWNLOADING) {
                play(LocalTurn.speakAs(ivie, "It's downloading now. I'll tell you when it's ready."), null);
            } else {
                confirmModelDownload = true;
                play(LocalTurn.speakAs(ivie, "My language model is " + model.spec.name + ", an open model under the "
                        + model.spec.licence + " licence. It's a " + model.spec.sizeWords()
                        + " download from Hugging Face, best on Wi-Fi, and then it works offline. Shall I start?"), this::startListening);
            }
            return true;
        }
        if (said.matches("^(cancel|stop|pause) (the |that |my )?download$")) {
            if (!hub.downloading()) {
                play(LocalTurn.speakAs(ivie, "There's no download running."), null);
            } else {
                hub.stopDownload();
                play(LocalTurn.speakAs(ivie, "Pausing the download. What's done is kept; say continue the download to carry on."), null);
            }
            return true;
        }
        if (said.matches("^(continue|resume|restart|carry on with) (the |my )?download$")) {
            if (model.installed()) {
                play(LocalTurn.speakAs(ivie, "It's already downloaded."), null);
            } else {
                startModelDownload();
            }
            return true;
        }
        String dl = "(the |my |your )?(download|downloading)( progress| bar| status)?";
        if (said.matches("^(show|display|open)( me)? " + dl + "$") || said.matches("^(show|display)( me)? (the )?progress( bar)?$")) {
            downloadBarHidden = false;
            ai.dvine.llm.DownloadStatus st = hub.status;
            stage.setDownload(true, "Downloading " + model.spec.name, model.installed() ? 100 : st.percent(),
                    st.phase() == ai.dvine.llm.DownloadStatus.Phase.DOWNLOADING,
                    model.installed() ? "installed" : st.phase() == ai.dvine.llm.DownloadStatus.Phase.IDLE ? "not started: say download your brain" : st.detail());
            play(LocalTurn.speakAs(ivie, model.installed() ? "My language model is already downloaded." : st.spoken()), null);
            return true;
        }
        if (said.matches("^(hide|close|remove)( the)? " + dl + "$")) {
            downloadBarHidden = true;
            stage.setDownload(false, "", 0, false, "");
            play(LocalTurn.speakAs(ivie, "Hidden. It keeps downloading; ask me how far it is any time."), null);
            return true;
        }
        if (said.matches("^(how('s| is) (the |my )?download( going)?|how far (is|along is) (the |my )?download|(what('s| is) the )?download (progress|status)|is (the |my )?download (done|finished|complete))$")) {
            play(LocalTurn.speakAs(ivie, model.installed() ? "The download is finished. My language model is installed." : hub.status.spoken()), null);
            return true;
        }
        if (said.matches("^(delete|remove|uninstall) " + thing + "$")) {
            if (!model.installed()) {
                play(LocalTurn.speakAs(ivie, "There's no language model installed."), null);
            } else {
                confirmModelRemove = true;
                play(LocalTurn.speakAs(ivie, "Remove my language model and free " + model.spec.sizeWords() + "? Say yes or no."),
                        this::startListening);
            }
            return true;
        }
        if (said.matches("^(turn off|disable|stop using|switch off) " + thing + "$")) {
            prefs.edit().putBoolean("llm_on", false).apply();
            play(LocalTurn.speakAs(ivie, "Okay. I'll answer from my offline notes only."), null);
            return true;
        }
        if (said.matches("^(turn on|enable|use|switch on|start) " + thing + "( again)?$")) {
            prefs.edit().putBoolean("llm_on", true).apply();
            if (model.installed() && !model.ready()) {
                loadModel(true);
            } else {
                play(LocalTurn.speakAs(ivie, model.ready() ? "My language model is on." : "It isn't installed yet. Say download your brain."), null);
            }
            return true;
        }
        if (said.matches("^(is|are) " + thing + " (installed|ready|on|working)$|^(language model|model|brain) status$")) {
            String line;
            switch (model.state()) {
                case READY: line = model.spec.name + " is running on this phone, offline."; break;
                case LOADING: line = "It's starting up now."; break;
                case DOWNLOADING: line = "It's downloading."; break;
                case INSTALLED: line = "It's installed but switched off. Say turn on your brain."; break;
                case UNSUPPORTED: line = "This phone can't run it: " + model.problem() + "."; break;
                case FAILED: line = "It's installed but didn't start: " + model.problem() + "."; break;
                default: line = "Not installed yet. Say download your brain.";
            }
            play(LocalTurn.speakAs(ivie, line), null);
            return true;
        }
        return false;
    }

    /** Starts (or resumes) the download in the background; it carries on when the app is minimised. */
    private void startModelDownload() {
        if (connectivity == null || !connectivity.isOnline()) {
            play(LocalTurn.speakAs(stage.isIvie(), "I'm offline right now. Connect to the internet, ideally Wi-Fi, and ask me again."), null);
            return;
        }
        if (hub.downloading()) {
            play(LocalTurn.speakAs(stage.isIvie(), hub.status.spoken()), null);
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFY); // for the progress notification
        }
        downloadBarHidden = false;
        hub.startDownload(this);
        play(LocalTurn.speakAs(stage.isIvie(), "Downloading now, in the background. You can minimise me; the notification shows how far it is."), null);
    }

    /**
     * The brain's answer for a sentence the examiner has read: each separate request answered in turn (plain answers
     * joined, anything that acts stops there), and when the sentence as said isn't understood, its core
     * without fillers is tried as well.
     */
    private BrainAnswer respondInContext(String asked, ai.dvine.brain.ContextExaminer.Reading reading) {
        String agent = stage.agent();
        if (reading.parts.size() > 1) {
            BrainAnswer first = brain.respond(reading.parts.get(0), agent);
            for (int i = 1; i < reading.parts.size() && plain(first); i++) {
                BrainAnswer more = brain.respond(reading.parts.get(i), agent);
                if (!plain(more)) {
                    break;
                }
                first = first.followedBy(more);
            }
            return first;
        }
        BrainAnswer reply = brain.respond(asked, agent);
        if (!reply.answered && "OFFLINE".equals(reply.source) && reading.core != null && !reading.core.isEmpty()
                && !reading.core.equals(asked.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", ""))) {
            BrainAnswer again = brain.respond(reading.core, agent);
            if (again.answered) {
                return again;
            }
        }
        return reply;
    }

    /** An answer that only speaks (no action, command, look, drawing or follow-up question). */
    private static boolean plain(BrainAnswer a) {
        return a.action == null && a.command == null && !a.listenAfter && a.look == null && a.sketch == null
                && a.object3d == null && a.scenePoses == null;
    }

    /**
     * 3D creation: "design me a modern two-storey house with a pool", "make it
     * three storeys", "show the floor plan", "return to your normal form".
     * Building (and reading references) runs off the main thread while a
     * thought bubble shows; the particles then leave the body and build it.
     */
    private boolean create(final String text) {
        final ai.dvine.create.ObjectRequestInterpreter.Request r = creation.parse(text, stage.objectShown());
        if (r == null) {
            return false;
        }
        boolean slow = r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.CREATE
                || r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.OPEN;
        if (!slow) {
            applyCreation(creation.perform(r));
            return true;
        }
        final int id = ++askId;
        stopPlay();
        stage.setThought(creation.researchAvailable() ? "Researching it, then building the geometry…" : "Designing the geometry…");
        stage.setMode(FaceMode.THINKING);
        deskThread.execute(() -> {
            final ai.dvine.create.CreationDirector.Outcome o = creation.perform(r);
            handler.post(() -> {
                if (id == askId && !isFinishing()) {
                    applyCreation(o);
                }
            });
        });
        return true;
    }

    private void applyCreation(ai.dvine.create.CreationDirector.Outcome o) {
        switch (o.action) {
            case BECOME:
                stage.setBodyMode(true);
                if (!stage.becomeObject(o.model)) {
                    o.lines.clear();
                    o.lines.add("I couldn't start the transformation just now. Try again in a moment.");
                }
                break;
            case CHANGED:
                stage.objectChanged();
                break;
            case RESTORE:
                stage.restoreBody();
                break;
            default:
                break;
        }
        List<TurnLine> lines = new ArrayList<TurnLine>();
        for (String l : o.lines) {
            lines.add(new TurnLine(stage.agent(), "SAY", l));
        }
        play(lines, null);
    }

    @Override
    public void onObjectPart(ai.dvine.create.Component part) {
        play(LocalTurn.speakAs(stage.isIvie(), creation.describe(part)), null);
    }

    /**
     * Online: hands the question to the server's desk while a thought bubble
     * shows, then plays its lines. If the server can't be reached the
     * offline answer plays instead. Returns false when offline.
     */
    /**
     * Dual-AI: both personas on stage, walking to face each other, while each answers through
     * its own session (the offline brain as that persona, or that persona's own desk question
     * online); the second hears the first. Runs off the main thread; any new input cancels it.
     */
    private void discuss(final ai.dvine.turn.DualAgentCoordinator.Request r) {
        stopPlay();
        stage.arrange("meet");
        final String first = r.first != null ? r.first : stage.agent();
        stage.setThought(first.equals("ivie") ? "Let me think, then Obi can weigh in…" : "Let me think, then Ivie can weigh in…");
        stage.setMode(FaceMode.THINKING);
        final boolean online = desk != null && connectivity != null && connectivity.isOnline() && serverUrl() != null
                && serverUrl().trim().startsWith("https://");
        final ai.dvine.turn.DualAgentCoordinator c = new ai.dvine.turn.DualAgentCoordinator(
                agentFor("obi", online), agentFor("ivie", online));
        discussion = c;
        final int id = ++askId;
        deskThread.execute(() -> {
            final List<TurnLine> out = c.discuss(r.question, first, r.mode, null);
            handler.post(() -> {
                if (id != askId || discussion != c || isFinishing()) {
                    return; // interrupted
                }
                play(out, () -> {
                    if (discussion == c) {
                        discussion = null;
                    }
                });
            });
        });
    }

    /** One persona's own answering session for a discussion. */
    private ai.dvine.turn.DualAgentCoordinator.Agent agentFor(final String persona, final boolean online) {
        return (question, heard) -> {
            BrainAnswer a;
            synchronized (brain) {
                a = brain.respond(question, persona);
            }
            List<String> say = new ArrayList<String>();
            if (a.needsOnline && online) {
                List<TurnLine> d = desk.ask(question, persona);
                if (d != null) {
                    for (TurnLine l : d) {
                        if ("SAY".equals(l.cue) && l.agent.equals(persona)) {
                            say.add(l.text);
                        }
                    }
                    if (!say.isEmpty()) {
                        return new ai.dvine.turn.DualAgentCoordinator.Reply(say, true);
                    }
                }
            }
            for (TurnLine l : a.lines) {
                if ("SAY".equals(l.cue)) {
                    say.add(l.text);
                }
            }
            return new ai.dvine.turn.DualAgentCoordinator.Reply(say, a.answered && !a.needsOnline);
        };
    }

    private boolean askDesk(final String text, final BrainAnswer offline) {
        if (desk == null || connectivity == null || !connectivity.isOnline() || serverUrl() == null
                || !serverUrl().trim().startsWith("https://")) {
            return false;
        }
        final int id = ++askId;
        final String onScreen = stage.agent();
        stopPlay();
        stage.setThought(stage.isIvie() ? "Let me ask the desk…" : "Checking with the desk…");
        stage.setMode(FaceMode.THINKING);
        deskThread.execute(() -> {
            final List<TurnLine> lines = desk.ask(text, onScreen);
            handler.post(() -> {
                if (id != askId || isFinishing()) {
                    return; // the user has moved on
                }
                play(lines != null ? lines : offline.lines, null);
            });
        });
        return true;
    }

    private void takeName(String text) {
        String name = Onboarding.parseName(text);
        if (name == null) {
            nameTries++;
            if (nameTries < 2) {
                play(LocalTurn.speakAs(true, "Sorry, I didn't catch your name. What should we call you?"), this::startListening);
            } else {
                awaitingName = false;
                play(LocalTurn.speakAs(true, "No problem. Whenever you like, just say call me, and your name."), null);
            }
            return;
        }
        awaitingName = false;
        prefs.edit().putString("user_name", name).apply();
        brain.setUserName(name);
        journal.add(LearningJournal.TAUGHT_FACT, "my name", name);
        syncSoon();
        stage.gesture("🫶");
        stage.burst(24);
        play(LocalTurn.speakAs(true, Onboarding.welcome(name)), null);
    }

    private void applyCommand(String command, String value) {
        if (command == null) {
            return;
        }
        switch (command) {
            case "ANATOMY":
                stage.anatomy(value);
                break;
            case "MUSIC_OFF":
                ambient.setEnabled(false);
                prefs.edit().putBoolean("music", false).apply();
                break;
            case "MUSIC_ON":
                ambient.setEnabled(true);
                prefs.edit().putBoolean("music", true).apply();
                break;
            case "SYNC_NOW":
                syncSoon();
                break;
            case "THOUGHTS_OFF":
                prefs.edit().putBoolean("thoughts", false).apply();
                break;
            case "THOUGHTS_ON":
                prefs.edit().putBoolean("thoughts", true).apply();
                break;
            case "SUBS_OFF":
                stage.setSubtitles(false);
                prefs.edit().putBoolean("subtitles", false).apply();
                break;
            case "SUBS_ON":
                stage.setSubtitles(true);
                prefs.edit().putBoolean("subtitles", true).apply();
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------ stage input

    @Override
    public void onHeadTap() {
        stopPlay();
        play(LocalTurn.call(stage.isIvie()), null);
    }

    /** A classified touch on the 3D body: the persona names the part, and reacts to repeats and holds. */
    @Override
    public void onBodyTouch(ai.dvine.human.touch.BodyTouch touch) {
        String part = touch.spoken();
        if (part == null) {
            return;
        }
        boolean ivie = stage.isIvie();
        if (touch.kind == ai.dvine.human.touch.BodyTouch.Kind.REPEAT && touch.count >= 3) {
            stopPlay();
            engine.onTouch(part, android.os.SystemClock.uptimeMillis());
            play(LocalTurn.speakAs(ivie, ivie ? "Okay, okay! My " + part + " is very popular today." : "That is the third time. My " + part + " is fine."), null);
            return;
        }
        if (touch.kind == ai.dvine.human.touch.BodyTouch.Kind.HOLD) {
            stopPlay();
            engine.onTouch(part, android.os.SystemClock.uptimeMillis());
            play(LocalTurn.speakAs(ivie, "You're holding my " + part + ". I can see your finger there, though a screen can't tell me how hard you press."), null);
            return;
        }
        if (touch.kind == ai.dvine.human.touch.BodyTouch.Kind.REPEAT) {
            return; // a second quick tap: the first one already answered
        }
        onBodyTap(part);
    }

    /** A tap on the full body: the persona notices where, and the hand waves back. */
    @Override
    public void onBodyTap(String part) {
        if (part == null || part.equals("face") || part.equals("head")) {
            onHeadTap();
            return;
        }
        stopPlay();
        boolean ivie = stage.isIvie();
        engine.onTouch(part, android.os.SystemClock.uptimeMillis());
        String[] said = ivie
                ? new String[]{"Hey, that's my " + part + "!", "You found my " + part + ".", "My " + part + ", gently please."}
                : new String[]{"That is my " + part + ".", "My " + part + ". Noted.", "You tapped my " + part + "."};
        play(LocalTurn.speakAs(ivie, said[(int) (System.nanoTime() / 1000L % said.length)]), null);
    }

    @Override
    public void onMic() {
        if (listen.isListening()) {
            listen.stop();
            stage.setListening(false);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        stopPlay();
        stage.setListening(true);
        stage.setSubtitle("");
        stage.setMode(FaceMode.LISTENING);
        listen.start(this);
    }

    @Override
    public void onType() {
        final EditText input = new EditText(this);
        input.setTextColor(0xFFEDEDED);
        input.setHint(awaitingName ? "Your name" : "Ask anything");
        input.setHintTextColor(0xFF8A8A8A);
        input.setBackgroundColor(0xFF000000);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Ask", (d, w) -> answer(input.getText().toString()))
                .show();
    }

    @Override
    public void onPartial(String text) {
        stage.setSubtitle(text);
    }

    @Override
    public void onResult(String text) {
        stage.setListening(false);
        stage.setSubtitle(text);
        answer(text);
    }

    @Override
    public void onError(String message) {
        stage.setListening(false);
        stage.setSubtitle(message);
        stage.setMode(FaceMode.ERROR);
    }

    // ------------------------------------------------------------ playback

    private void play(List<TurnLine> next, Runnable done) {
        stopPlay();
        lines = next;
        index = 0;
        afterPlay = done;
        step(playId);
    }

    private void step(final int token) {
        if (token != playId) {
            return;
        }
        if (index >= lines.size()) {
            if (streamOpen) {
                handler.postDelayed(() -> step(token), 120); // the model is still writing: wait for its next sentence
                return;
            }
            stage.setMode(FaceMode.DONE);
            Runnable done = afterPlay;
            afterPlay = null;
            if (done != null) {
                handler.postDelayed(() -> {
                    if (token == playId) {
                        done.run();
                    }
                }, 250);
            }
            return;
        }
        final TurnLine line = lines.get(index);
        index++;
        final boolean ivie = "ivie".equals(line.agent);
        stage.ensureFace(ivie, () -> {
            if (token != playId) {
                return;
            }
            if ("SAY".equals(line.cue)) {
                stage.setMode(FaceMode.SPEAKING);
                stage.setThought(null);
                stage.setSubtitle(line.text);
                talk.speak(ivie, line.text, () -> step(token));
            } else if (line.text != null && line.text.length() > 0) {
                // inner thought: softer voice, thinking face, the words in a bubble above the head
                stage.setThought(line.text);
                stage.setMode(FaceMode.THINKING);
                stage.setSubtitle("");
                talk.whisper(ivie, line.text, () -> step(token));
            } else {
                stage.setThought(null);
                stage.setMode(FaceMode.THINKING);
                handler.postDelayed(() -> step(token), 520);
            }
        });
    }

    private void stopPlay() {
        playId++;
        streamOpen = false;
        afterPlay = null;
        handler.removeCallbacksAndMessages(null);
        if (talk != null) {
            talk.stop();
        }
        if (listen != null && listen.isListening()) {
            listen.stop();
            stage.setListening(false);
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != MIC) {
            return;
        }
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            play(LocalTurn.speakAs(stage.isIvie(),
                    "I need the microphone to hear you. You can still type by pressing and holding the screen."), null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.start();
        ambient.start();
    }

    @Override
    protected void onStop() {
        connectivity.stop();
        ambient.stop();
        super.onStop();
    }

    @Override
    protected void onPause() {
        stopPlay();
        handler.removeCallbacks(engineTick);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(engineTick);
        handler.post(engineTick);
    }

    @Override
    protected void onDestroy() {
        talk.shutdown();
        listen.stop();
        sync.shutdown();
        deskThread.shutdownNow();
        if (model != null) {
            model.cancel();
        }
        hub.removeListener(onDownload); // the download itself carries on in its service
        modelThread.shutdownNow();
        super.onDestroy();
    }
}
