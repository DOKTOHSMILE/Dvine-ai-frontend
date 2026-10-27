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
import ai.dvine.stage.StageMode;
import ai.dvine.stage.StageListener;
import ai.dvine.stage.StageView;
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
 * No screen UI. One persona on black; touch and hold to speak, double-tap to type. Anything that leaves
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
        stage.setDownload(show || stage.downloadShown() && !downloadBarHidden, "Downloading brain files", st.percent(),
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
                stage.setDownload(true, "Brain files downloaded", 100, false, "verified, starting now");
                handler.postDelayed(() -> stage.setDownload(false, "", 100, false, ""), 6000);
                loadModel(true);
                break;
            case PAUSED:
                stage.activity("DOWNLOAD", "paused at " + st.percent() + "%");
                break;
            case BAD_FILE:
            case FAILED:
                if (wasRunning) {
                    play(LocalTurn.speakAs(stage.agent(), st.spoken()), null);
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
    private ai.dvine.stage.TypeBar typeBar;
    /** A word that only sounded like a name, waiting for the user's yes or no ("Did you mean Ivie?"). */
    private ai.dvine.turn.Names.Hearing nameCheck;
    /** The last thing drawn or formed, while it is on show: context for the model. */
    private String lastDrawn;

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
        // the pod in space and whatever is drawn above it render with OpenGL under the transparent stage overlay
        final ai.dvine.render.PodView podView = new ai.dvine.render.PodView(this);
        stage.attach(podView);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(podView);
        root.addView(stage);
        // the text field for typing (double-tap), drawn in particles along the bottom; hidden until asked for
        typeBar = new ai.dvine.stage.TypeBar(this, text -> answer(text));
        android.widget.FrameLayout.LayoutParams tb = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        int m = (int) (14 * getResources().getDisplayMetrics().density);
        tb.setMargins(m, m, m, m * 4);
        root.addView(typeBar, tb);
        setContentView(root);
        // only after setContentView: before it the window has no decor and this throws
        WindowInsetsController bars = window.getInsetsController();
        if (bars != null) {
            bars.hide(WindowInsets.Type.systemBars());
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        prefs = getSharedPreferences("dvine", MODE_PRIVATE);
        ai.dvine.turn.Names.restore(prefs.getString("name_aliases", null)); // spellings the user confirmed as a name
        talk = new TalkBridge(this);
        listen = new ListenBridge(this);
        ambient = new AmbientPad();
        ambient.setEnabled(prefs.getBoolean("music", true));
        stage.setSubtitles(prefs.getBoolean("subtitles", true));
        // the voice chosen last time (Ivie the first time)
        stage.ensureFace(prefs.getString("face", StageView.IVIE), null);
        talk.setTuning(false, ai.dvine.voice.VoiceTuning.parse(prefs.getString("voice_obi", null), false));
        talk.setTuning(true, ai.dvine.voice.VoiceTuning.parse(prefs.getString("voice_ivie", null), true));
        talk.setWordListener(stage::speechWord);
        talk.setListener((ivie, speaking) -> {
            ambient.setPersona(ivie);
            ambient.setLevel(speaking ? 1f : 0f);
        });

        KnowledgeStore store = new KnowledgeStore(this);
        journal = new LearningJournal(new File(getFilesDir(), "learning"));
        learned = new ai.dvine.agent.LearnedIntents(new File(getFilesDir(), "learned_intents.txt"));
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
        creation = new ai.dvine.create.CreationDirector(stage.objects, new File(getFilesDir(), "designs"));
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
            play(l, this::startListening);
        } else {
            List<TurnLine> l = new ArrayList<TurnLine>();
            l.add(new TurnLine(stage.agent(), "SAY", "Welcome back, " + name + "."));
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

    /** What DVINE learned from its brain: words the brain turned into commands, done at once next time. */
    private ai.dvine.agent.LearnedIntents learned;
    /** True while a command the brain interpreted is being done, so it isn't sent back to the brain. */
    private boolean fromBrain;

    private void answer(String text) {
        answer(text, false);
    }

    /** {@code interpreted}: the words are a command the brain already read; they go straight to DVINE's own brain. */
    private void answer(String text, boolean interpreted) {
        fromBrain = interpreted;
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!interpreted && learned != null) {
            String known = learned.commandFor(text);
            if (known != null) {
                stage.innerVoice("Heard: \u201c" + clip(text) + "\u201d");
                stage.innerVoice("I learned this one: " + known);
                text = known;
                fromBrain = true;
            }
        }
        if (nameCheck != null) { // the answer to "Did you mean Ivie?"
            ai.dvine.turn.Names.Hearing pending = nameCheck;
            nameCheck = null;
            Boolean yes = ai.dvine.turn.Names.yesNo(text);
            if (yes != null) {
                if (yes) {
                    ai.dvine.turn.Names.learn(pending.word, pending.guess); // that spelling is the name from now on
                } else {
                    ai.dvine.turn.Names.notAName(pending.word);
                }
                prefs.edit().putString("name_aliases", ai.dvine.turn.Names.saved()).apply();
                text = yes ? pending.suggested : pending.text;
            }
        }
        if (!awaitingName) { // a name being given is taken as said
            // "Evie", "Ivy", "Obie": the names as recognition hears them; a word that only sounds like one is asked about
            ai.dvine.turn.Names.Hearing heard = ai.dvine.turn.Names.hear(text);
            text = heard.text;
            String own = brain.userName();
            if (heard.word != null && (own == null || !own.equalsIgnoreCase(heard.word))) {
                nameCheck = heard;
                stage.activity("VOICE", text.trim());
                play(LocalTurn.speakAs(stage.agent(), heard.question()), this::startListening);
                return;
            }
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
            stage.setMode(StageMode.LISTENING);
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
                play(LocalTurn.speakAs(stage.agent(), "There's no crash report. Everything has been running fine."), null);
            } else if (Actions.copy(this, report)) {
                new File(getFilesDir(), "crash.txt").delete();
                stage.burst(18);
                play(LocalTurn.speakAs(stage.agent(), "Copied. The crash report is on your clipboard; paste it to the developer."), null);
            }
            return;
        }
        String debateOn = ai.dvine.agent.Debate.topicOf(text);
        if (debateOn != null) {
            startDebate(debateOn);
            return;
        }
        // read the whole sentence first: who it is for, whether it is negated, what "it" means, corrections, several requests
        ai.dvine.brain.ContextExaminer.Reading reading = examiner.examine(text);
        stage.activity("VOICE", text.trim());
        stage.activity("CONTEXT", reading.summary());
        if (reading.kind == ai.dvine.brain.ContextExaminer.Kind.NEVER_MIND) {
            play(LocalTurn.speakAs(stage.agent(), "Okay, never mind."), null);
            return;
        }
        if (reading.negated) {
            String not = reading.core.replaceFirst("^(please do not|do not|never) ", "").replaceAll("\\bmy\\b", "your");
            play(LocalTurn.speakAs(stage.agent(), "Okay, I won't " + not + "."), null);
            return;
        }
        if (reading.clarify != null) {
            play(LocalTurn.speakAs(stage.agent(), reading.clarify), this::startListening);
            return;
        }
        final String asked = reading.rewritten ? reading.resolved : text;
        final boolean modelReady = model != null && model.ready() && prefs.getBoolean("llm_on", true);
        String lowAsked = asked.toLowerCase(java.util.Locale.US);
        if (lowAsked.matches("^(do not|don'?t|stop|never) (look|looking|check|checking) (things |anything )?(up )?online.*")) {
            prefs.edit().putBoolean("fresh_facts", false).apply();
            play(LocalTurn.speakAs(stage.agent(), "Alright. I won't look anything up online; I'll answer from what I know and what you teach me."), null);
            return;
        }
        if (lowAsked.matches("^(you can |please )?(look|check) (things |anything )?up online( again)?$|^(start|keep) (looking|checking) (things )?up online$")) {
            prefs.edit().putBoolean("fresh_facts", true).apply();
            play(LocalTurn.speakAs(stage.agent(), "Okay. When we're online, I'll check the latest before answering questions about the world."), null);
            return;
        }
        // changing one part of the 3D drawing on show: "take out the tyres", "make the roof blue", "add a spoiler"
        if (editParts(asked)) {
            return;
        }
        // changing the thing on show: "make it bigger", "spin it", "make it red", "scatter it", "clear it"
        ai.dvine.render.field.ShapeCommand change = stage.pictureShown() ? ai.dvine.render.field.ShapeCommand.parse(asked) : null;
        if (change != null && stage.changePicture(change)) {
            play(LocalTurn.speakAs(stage.agent(), changeLine(change.op)), null);
            return;
        }
        // said some other way ("make it do cartwheels", "can I see it from above"): the brain rewrites it into one of
        // the commands DVINE knows, DVINE does it and remembers the words
        if (change == null && !interpreted && stage.pictureShown() && modelReady && ai.dvine.agent.ViewIntent.looksLikeOne(asked)) {
            askViewIntent(asked);
            return;
        }
        ai.dvine.create.ObjectRequestInterpreter.Request known = creation.parse(asked, stage.objectShown());
        if (known != null && known.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.UNKNOWN_OBJECT && known.detail != null && modelReady) {
            askDrawing(known.detail, true); // something the creation engine can't model: the language model plans it in 3D
            return;
        }
        if (create(asked)) {
            return;
        }
        String formWhat = ai.dvine.llm.DrawPrompt.formSubject(asked);
        if (formWhat != null && modelReady && ai.dvine.render.forms.Forms.objectFor(asked.toLowerCase(java.util.Locale.US)) == null
                && ai.dvine.stage.Sketch.forRequest(asked.toLowerCase(java.util.Locale.US)) == null) {
            askDrawing(formWhat, true);
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
            if (!route.lines.isEmpty()) {
                prefs.edit().putString("face", route.lines.get(0).agent).apply(); // the voice asked for stays next time
            }
            play(route.lines, null);
            return;
        }
        final BrainAnswer reply;
        synchronized (brain) { // a cancelled discussion may still be finishing a turn on its thread
            reply = respondInContext(asked, reading);
        }
        stage.activity("ANSWER", reply.answered ? "from " + reply.source.toLowerCase(java.util.Locale.US) : "not in my notes yet");
        engine.setPersona(stage.agent(), now);
        if ("MODEL_RESET".equals(reply.command) && model != null) {
            model.forget(); // a correction: the model's old answer shouldn't stay in its conversation
        }
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
                        play(LocalTurn.speakAs(stage.agent(), "I couldn't open the voice settings on this phone."), null);
                    }
                }
            });
            return;
        }
        if (reply.command != null && reply.command.startsWith("VOICE_")) {
            if (talk.adjust(reply.forIvie, reply.command)) {
                prefs.edit().putString(reply.forIvie ? "voice_ivie" : "voice_obi",
                        talk.tuning(reply.forIvie).serialize()).apply();
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
        if ("POSE".equals(reply.command) && !stage.bodyShown()) {
            play(LocalTurn.speakAs(stage.agent(), capabilities().missing(ai.dvine.agent.Interpretation.Task.MOVE)), null);
            return;
        }
        // the brain proposes; the engine validates, arbitrates and moves the body
        engine.perform(reply, now);
        if ("SELF_REPORT".equals(reply.command)) {
            play(LocalTurn.speakAs(stage.agent(), engine.selfReport(now) + " " + stage.inspect()), null);
            return;
        }
        if ("FAVOURITES".equals(reply.command)) {
            play(LocalTurn.speakAs(stage.agent(), engine.favouritesLine()), null);
            return;
        }
        final PendingAction act = reply.action;
        if (act != null && act.kind == PendingAction.Kind.COPY) {
            if (Actions.copy(this, act.content)) {
                stage.burst(18);
                play(reply.lines, null);
            } else {
                play(LocalTurn.speakAs(stage.agent(), "The clipboard refused that. Nothing was copied."), null);
            }
            return;
        }
        if (act != null && act.kind == PendingAction.Kind.SHARE) {
            play(reply.lines, () -> {
                stage.burst(18);
                if (!Actions.share(this, act)) {
                    play(LocalTurn.speakAs(stage.agent(), "I couldn't find an app to share with on this phone."), null);
                }
            });
            return;
        }
        // a drawing with no built-in form: the language model describes it in shapes and the particles form it
        String drawWhat = ai.dvine.llm.DrawPrompt.subject(asked);
        boolean canDraw = model != null && model.ready() && prefs.getBoolean("llm_on", true)
                || connectivity != null && connectivity.isOnline();
        if (drawWhat != null && !"DRAW".equals(reply.source) && canDraw) {
            // every drawing is built in 3D when the brain is here; without it, a picture from the internet
            askDrawing(drawWhat, model != null && model.ready() || ai.dvine.llm.DrawPrompt.in3d(asked));
            return;
        }
        // knowledge and conversation go to the language model when it's installed; commands, maths, time, memory,
        // what the user taught and anything that acts stay with the offline brain (exact, and instant)
        boolean modelTopic = (!reply.answered || reply.needsOnline || MODEL_SOURCES.contains(reply.source)) && plain(reply);
        if (modelTopic && model != null && model.ready() && prefs.getBoolean("llm_on", true)
                && (fromBrain ? askLocal(asked, reply, false) : agentTurn(asked, reply))) {
            return;
        }
        if (modelTopic && offerModel(reply)) {
            return;
        }
        if (reply.needsOnline && askDesk(asked, reply)) {
            return;
        }
        if (model != null && plain(reply)) {
            model.note(asked, spoken(reply.lines)); // so the model reads what follows in the light of it
        }
        play(reply.lines, reply.listenAfter || endsInQuestion(reply.lines) ? this::startListening : null);
    }

    private static String spoken(List<TurnLine> said) {
        StringBuilder b = new StringBuilder();
        for (TurnLine l : said) {
            if ("SAY".equals(l.cue) && l.text != null) {
                b.append(b.length() == 0 ? "" : " ").append(l.text);
            }
        }
        return b.toString();
    }

    /** A reply that ends by asking the user something: the microphone opens for the answer. */
    private static boolean endsInQuestion(List<TurnLine> said) {
        return spoken(said).trim().endsWith("?");
    }

    /** Who is on screen, what was just discussed and what is on show, for the model ({@link ai.dvine.llm.PersonaPrompt#situation}). */
    private String situation(boolean ivie) {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String part = hour < 5 ? "night" : hour < 12 ? "morning" : hour < 17 ? "afternoon" : hour < 21 ? "evening" : "night";
        return ai.dvine.llm.PersonaPrompt.situation(ivie, examiner.lastTopic(), stage.pictureShown() ? lastDrawn : null,
                part, connectivity != null && connectivity.isOnline());
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
        String agent = stage.agent();
        lines2.add(new TurnLine(agent, "SAY", "By the way, I can answer questions like this much better once my brain files are on this phone."
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
            line = "starting up";
        } else {
            line = "not installed: say \"download your brain\"";
        }
        stage.activity("MODEL", line);
    }

    /**
     * The language model answers a question the offline brain couldn't: in the persona's voice, spoken sentence by
     * sentence while the rest is still being written. Anything new the user says stops it.
     */
    // ------------------------------------------------------------ the agent: the brain reads, DVINE checks and does

    /** What DVINE can do right now. */
    private ai.dvine.agent.Capabilities capabilities() {
        ai.dvine.agent.Capabilities c = new ai.dvine.agent.Capabilities();
        c.brainReady = model != null && model.ready() && prefs.getBoolean("llm_on", true);
        c.online = connectivity != null && connectivity.isOnline();
        c.lookupsAllowed = prefs.getBoolean("fresh_facts", true);
        c.bodyShown = stage.bodyShown();
        return c;
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() > 60 ? t.substring(0, 57) + "\u2026" : t;
    }

    /**
     * One turn the way DVINE works: the brain (the language model) reads what the user said and returns its
     * interpretation (a question, a request, a prompt or chat; what it means; the task; whether it needs the
     * internet). DVINE checks it can do that here; a task it knows is done through its own checked commands (and
     * learned, so next time it needs no brain), a question about the world is researched online first, everything
     * else is answered. Each step is written by the inner voice at the top of the screen.
     */
    private boolean agentTurn(final String question, final BrainAnswer offline) {
        final int id = ++askId;
        stopPlay();
        stage.setMode(StageMode.THINKING);
        stage.innerVoice("Heard: \u201c" + clip(question) + "\u201d");
        stage.innerVoice("Asking my brain what you mean\u2026");
        modelThread.execute(() -> {
            final String written = id == askId ? model.task(ai.dvine.agent.Interpretation.PROMPT, question, 90, null) : null;
            final ai.dvine.agent.Interpretation it = ai.dvine.agent.Interpretation.parse(written);
            handler.post(() -> {
                if (id != askId) {
                    return;
                }
                if (it == null) {
                    stage.innerVoice("No clear reading, so I'll just answer.");
                    askLocal(question, offline, false);
                    return;
                }
                stage.innerVoice("Reading: " + it.summary());
                if (!it.means.isEmpty()) {
                    stage.innerVoice("You want: " + clip(it.means));
                }
                ai.dvine.agent.Interpretation.Task task = it.internet && it.task == ai.dvine.agent.Interpretation.Task.ANSWER
                        ? ai.dvine.agent.Interpretation.Task.SEARCH : it.task;
                String missing = capabilities().missing(task);
                stage.innerVoice("Can I do it here? " + (missing == null ? "Yes." : "No: " + clip(missing)));
                if (missing != null && task != ai.dvine.agent.Interpretation.Task.SEARCH) {
                    play(LocalTurn.speakAs(stage.agent(), missing), null);
                    return;
                }
                if (task == ai.dvine.agent.Interpretation.Task.DEBATE) {
                    startDebate(it.target.isEmpty() ? question : it.target);
                    return;
                }
                String command = it.command();
                if (command != null && !it.groundedIn(question)) {
                    stage.innerVoice("That reading isn't about what you said, so I won't act on it.");
                    command = null;
                }
                if (command != null) {
                    learned.learn(question, command); // DVINE learns it: next time, no brain needed
                    stage.innerVoice("Doing it: " + command);
                    answer(command, true);
                    return;
                }
                boolean look = task == ai.dvine.agent.Interpretation.Task.SEARCH && missing == null;
                askLocal(question, offline, look);
            });
        });
        return true;
    }

    /** A debate between two personas on the user's topic, written by the brain turn by turn while it is spoken. */
    private void startDebate(final String topic) {
        String missing = capabilities().missing(ai.dvine.agent.Interpretation.Task.DEBATE);
        if (missing != null) {
            play(LocalTurn.speakAs(stage.agent(), missing), null);
            return;
        }
        final int id = ++askId;
        stopPlay();
        stage.setMode(StageMode.THINKING);
        stage.innerVoice("Setting up a debate: " + clip(topic));
        final List<TurnLine> stream = new ArrayList<TurnLine>();
        stream.add(new TurnLine("obi", "SAY", ai.dvine.agent.Debate.opening(topic)));
        streamOpen = true;
        lines = stream;
        index = 0;
        afterPlay = null;
        step(playId);
        final String today = new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK).format(new java.util.Date());
        final String name = brain.userName();
        modelThread.execute(() -> {
            ai.dvine.agent.Debate d = new ai.dvine.agent.Debate(topic);
            while (id == askId && !d.done()) {
                final String who = d.speaker(), role = d.role();
                handler.post(() -> stage.innerVoice((who.equals("obi") ? "Obi" : "Ivie") + " prepares the " + role + "\u2026"));
                String sys = ai.dvine.llm.PersonaPrompt.system(who, name, today) + " " + d.rules();
                String said = model.task(sys, d.prompt(), 160, null);
                final String line = d.take(said);
                if (line == null) {
                    break;
                }
                handler.post(() -> {
                    if (id == askId) {
                        stream.add(new TurnLine(who, "SAY", line));
                    }
                });
            }
            handler.post(() -> {
                if (id == askId) {
                    stream.add(new TurnLine("ivie", "SAY", ai.dvine.agent.Debate.closing()));
                    streamOpen = false;
                }
            });
        });
    }

    private boolean askLocal(final String question, final BrainAnswer offline) {
        return askLocal(question, offline, false);
    }

    private boolean askLocal(final String question, final BrainAnswer offline, final boolean research) {
        final int id = ++askId;
        final boolean ivie = stage.isIvie();
        final String agent = stage.agent();
        stopPlay();
        stage.innerVoice(ivie ? "Let me think about that\u2026" : "ibiko".equals(agent) ? "Compiling a thought\u2026" : "Thinking it through\u2026");
        stage.setMode(StageMode.THINKING);
        stage.activity("MODEL", "thinking offline");
        final List<TurnLine> stream = new ArrayList<TurnLine>();
        streamOpen = true;
        lines = stream;
        index = 0;
        afterPlay = null;
        step(playId);
        final String system = ai.dvine.llm.PersonaPrompt.system(agent, brain.userName(),
                new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK).format(new java.util.Date()), selfSheet());
        final java.util.List<String> taught;
        synchronized (brain) {
            taught = brain.taughtFor(question, 6);
        }
        // a question about the world, online: a fresh reference, since the model's training is older
        final boolean look = prefs.getBoolean("fresh_facts", true) && connectivity != null && connectivity.isOnline()
                && (research || ai.dvine.llm.FreshFacts.worthLooking(question));
        if (look) {
            stage.innerVoice("Searching the internet for the latest on it\u2026");
        }
        final String around = situation(ivie);
        modelThread.execute(() -> {
            final String reference = look && id == askId ? ai.dvine.llm.FreshFacts.lookUp(question) : null;
            if (look) {
                handler.post(() -> stage.innerVoice(reference != null ? "Found: " + clip(reference) : "Nothing current found; I'll use what I know."));
            }
            handler.post(() -> stage.innerVoice("Writing the answer\u2026"));
            final String context = ai.dvine.llm.PersonaPrompt.context(taught, reference, around);
            final ai.dvine.llm.SpeechChunker chunker = new ai.dvine.llm.SpeechChunker();
            final ai.dvine.llm.ActionTags tags = new ai.dvine.llm.ActionTags(); // "[[switch:obi]]" is acted on, not said
            final String all = model.reply(system, question, context, 220, text -> {
                if (id != askId) {
                    return false;
                }
                final List<String> ready = chunker.add(tags.add(text));
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
            chunker.add(tags.flush());
            final String rest = chunker.flush();
            final String switchTo = tags.switchTo();
            handler.post(() -> {
                if (id != askId) {
                    return;
                }
                if (all == null && stream.isEmpty()) {
                    stream.addAll(offline.lines); // the model failed: the offline answer instead
                } else if (!rest.isEmpty()) {
                    stream.add(new TurnLine(agent, "SAY", rest));
                }
                if (switchTo != null && !switchTo.equals(agent)) {
                    afterPlay = () -> switchFace(switchTo, true); // the brain brought another persona forward
                } else if (all != null && ai.dvine.turn.Followup.wantsReply(ai.dvine.llm.ActionTags.strip(all))) {
                    afterPlay = this::startListening; // it asked something: listen for the answer
                }
                streamOpen = false;
            });
        });
        return true;
    }

    /**
     * "Draw me a lighthouse": always in 3D, and nothing is built in: the
     * brain plans as named 3D parts, first reading what the thing looks like online when it may, and the plan is
     * checked (enough parts, and about the right thing) with one stricter retry. Without the brain, online, a
     * reference picture is formed instead. Every 3D drawing is kept part by part so "take out the tyres" works.
     * Off the main thread; any new input cancels it.
     */
    private void askDrawing(final String subject, final boolean solidOnly) {
        final int id = ++askId;
        final boolean ivie = stage.isIvie();
        final boolean connected = connectivity != null && connectivity.isOnline();
        final boolean online = !solidOnly && connected;
        final boolean research = connected && prefs.getBoolean("fresh_facts", true);
        final boolean modelOk = model != null && model.ready();
        stopPlay();
        stage.setMode(StageMode.THINKING);
        stage.activity("DRAW", subject);
        lastDrawn = subject;
        if (modelOk) {
            stage.innerVoice(research ? "Looking up what " + ai.dvine.llm.DrawPrompt.article(subject) + " looks like\u2026"
                    : "Planning the parts of " + ai.dvine.llm.DrawPrompt.article(subject) + "\u2026");
        } else {
            stage.setThought((online ? "Finding a picture of " : "Building ") + subject + "\u2026");
        }
        modelThread.execute(() -> {
            ai.dvine.render.field.Shape picture = null;
            ai.dvine.render.field.SolidSpec plan = null;
            if (plan == null && modelOk && id == askId) {
                String facts = research ? ai.dvine.render.field.PictureFinder.describe(subject) : null;
                if (facts != null) {
                    handler.post(() -> {
                        if (id == askId) {
                            stage.innerVoice("Found how it looks. Planning its parts\u2026");
                        }
                    });
                }
                String planned = model.task(ai.dvine.llm.DrawPrompt.system(), ai.dvine.llm.DrawPrompt.user(subject, facts), 360, text -> id == askId);
                plan = ai.dvine.llm.DrawPrompt.read(planned, subject);
                if (!ai.dvine.llm.DrawPrompt.usable(plan, subject) && id == askId) {
                    handler.post(() -> {
                        if (id == askId) {
                            stage.innerVoice("That plan wasn't right. Trying again\u2026");
                        }
                    });
                    planned = model.task(ai.dvine.llm.DrawPrompt.system(), ai.dvine.llm.DrawPrompt.retry(subject), 360, text -> id == askId);
                    plan = ai.dvine.llm.DrawPrompt.read(planned, subject);
                }
                if (!ai.dvine.llm.DrawPrompt.usable(plan, subject)) {
                    plan = null;
                }
            }
            if (plan != null) {
                plan.fixFrame();
                picture = plan.points(ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f);
            } else if (online && id == askId) {
                android.graphics.Bitmap bm = ai.dvine.render.field.Raster.decode(ai.dvine.render.field.PictureFinder.find(subject));
                if (bm != null && id == askId) {
                    picture = ai.dvine.render.field.Raster.image(bm, ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f);
                    if (picture.count < 200) {
                        picture = null;
                    }
                }
            }
            final ai.dvine.render.field.Shape shown = picture != null && picture.count > 0 ? picture : null;
            final ai.dvine.render.field.SolidSpec built = shown == null ? null : plan;
            handler.post(() -> {
                if (id != askId || isFinishing()) {
                    return;
                }
                stage.setThought(null);
                if (shown == null) {
                    play(LocalTurn.speakAs(ivie, modelOk || online ? "I tried to make " + ai.dvine.llm.DrawPrompt.article(subject)
                            + ", but it didn't come out right. Ask me again?"
                            : "I need my brain files for that. Say download your brain, and I'll be able to build anything you ask."), null);
                    return;
                }
                // the words are always about what the user asked for, never what the brain wrote
                String line = "Here's " + ai.dvine.llm.DrawPrompt.article(subject) + " in 3D.";
                if (built != null) {
                    currentSolid = built;
                    solidHistory.clear();
                    takenOut.clear();
                    stage.showSolid(shown, subject);
                    if (!shapeHintGiven) {
                        shapeHintGiven = true;
                        line += " Turn it with your finger, pinch to zoom, or tell me to change a part, like take out the "
                                + ai.dvine.render.field.PartEdit.example(built) + ".";
                    }
                } else {
                    currentSolid = null;
                    stage.showPicture(shown, subject);
                }
                play(LocalTurn.speakAs(ivie, line), null);
            });
        });
    }

    /** The 3D drawing on show, part by part; what was taken out of it (to put back); the steps before (to undo). */
    private ai.dvine.render.field.SolidSpec currentSolid;
    private final List<ai.dvine.render.field.SolidSpec.Part> takenOut = new ArrayList<ai.dvine.render.field.SolidSpec.Part>();
    private final java.util.ArrayDeque<ai.dvine.render.field.SolidSpec> solidHistory = new java.util.ArrayDeque<ai.dvine.render.field.SolidSpec>();

    /**
     * A change to one part of the 3D drawing on show: "take out the tyres", "make the roof blue", "make the ears
     * bigger", "put the wheels back", "add a spoiler", "undo". The parts are found by their names and everyday
     * words for them (a tyre is a wheel) and by where they are (the left wing, the front wheels); when that finds
     * nothing, the brain is asked which of the parts are meant. True when the words were about the parts.
     */
    private boolean editParts(final String asked) {
        if (currentSolid == null || !stage.pictureShown()) {
            return false;
        }
        String low = asked.toLowerCase(java.util.Locale.US).trim().replaceAll("[.!?]+$", "");
        if (low.matches("^(?:please )?(?:export|save|download|send) (?:it|this|that|the (?:drawing|model|3d model)|the " + java.util.regex.Pattern.quote(String.valueOf(lastDrawn).toLowerCase(java.util.Locale.US)) + ")"
                + "(?: (?:for|to|into) (?:my |a )?(?:game|games|blender|unity|unreal|godot|downloads))?(?: as (?:a )?(?:glb|gltf|3d model|3d file|game file))?$")
                || low.matches("^(?:please )?(?:export|save) (?:it |this )?(?:as |to )(?:a )?(?:glb|gltf|game file|3d file)$")) {
            exportSolid();
            return true;
        }
        if (low.matches("^(undo|undo that|go back|take that back|put it back how it was|change it back)$")) {
            if (solidHistory.isEmpty()) {
                return false;
            }
            currentSolid = solidHistory.pop();
            stage.reshapeSolid(currentSolid.points(ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f));
            play(LocalTurn.speakAs(stage.agent(), "Undone."), null);
            return true;
        }
        final ai.dvine.render.field.PartEdit edit = ai.dvine.render.field.PartEdit.parse(asked);
        if (edit == null) {
            return false;
        }
        if (edit.op == ai.dvine.render.field.PartEdit.Op.RESTORE) {
            List<ai.dvine.render.field.SolidSpec.Part> back = ai.dvine.render.field.PartEdit.pick(takenOut, edit.target);
            if (back.isEmpty()) {
                play(LocalTurn.speakAs(stage.agent(), takenOut.isEmpty() ? "Nothing has been taken out." : "I didn't take out any "
                        + edit.target + ". I took out: " + ai.dvine.render.field.PartEdit.inventory(takenOut) + "."), null);
                return true;
            }
            solidHistory.push(currentSolid);
            ai.dvine.render.field.SolidSpec next = currentSolid.copy();
            next.parts.addAll(back);
            takenOut.removeAll(back);
            showEdit(next, "The " + edit.target + (back.size() == 1 ? " is" : " are") + " back.");
            return true;
        }
        if (edit.op == ai.dvine.render.field.PartEdit.Op.ADD) {
            if (model == null || !model.ready()) {
                play(LocalTurn.speakAs(stage.agent(), "To add new parts I need my brain. Say download your brain."), null);
                return true;
            }
            final int id = ++askId;
            final ai.dvine.render.field.SolidSpec before = currentSolid;
            stage.setMode(StageMode.THINKING);
            stage.innerVoice("Adding " + clip(edit.target) + " to the " + lastDrawn + "\u2026");
            modelThread.execute(() -> {
                String written = model.task(ai.dvine.llm.DrawPrompt.system(),
                        ai.dvine.render.field.PartEdit.addPrompt(before, lastDrawn, edit.target), 200, text -> id == askId);
                final ai.dvine.render.field.SolidSpec next = before.extend(written);
                handler.post(() -> {
                    if (id != askId) {
                        return;
                    }
                    if (next.parts.size() <= before.parts.size()) {
                        play(LocalTurn.speakAs(stage.agent(), "I couldn't work out where " + edit.target + " would go. Try saying where, like on top."), null);
                        return;
                    }
                    solidHistory.push(before);
                    showEdit(next, "Added " + edit.target + ".");
                });
            });
            return true;
        }
        List<Integer> which = ai.dvine.render.field.PartEdit.find(currentSolid, edit.target);
        if (!which.isEmpty()) {
            applyEdit(edit, which);
            return true;
        }
        if (model == null || !model.ready()) {
            play(LocalTurn.speakAs(stage.agent(), "I can't find " + edit.target + " on it. It has: "
                    + ai.dvine.render.field.PartEdit.inventory(currentSolid) + "."), null);
            return true;
        }
        // the brain knows what a word means when the part names don't say it ("the paws" are the feet)
        final int id = ++askId;
        final ai.dvine.render.field.SolidSpec on = currentSolid;
        stage.setMode(StageMode.THINKING);
        stage.innerVoice("Which part is \u201c" + clip(edit.target) + "\u201d? Asking my brain\u2026");
        modelThread.execute(() -> {
            String written = model.task(ai.dvine.render.field.PartEdit.whichSystem(),
                    ai.dvine.render.field.PartEdit.whichPrompt(on, lastDrawn, edit.target), 60, text -> id == askId);
            final List<Integer> found = ai.dvine.render.field.PartEdit.fromBrain(on, written);
            handler.post(() -> {
                if (id != askId || on != currentSolid) {
                    return;
                }
                if (found.isEmpty()) {
                    play(LocalTurn.speakAs(stage.agent(), "I can't find " + edit.target + " on it. It has: "
                            + ai.dvine.render.field.PartEdit.inventory(on) + "."), null);
                    return;
                }
                stage.innerVoice("Those are the " + ai.dvine.render.field.PartEdit.names(on, found) + ".");
                applyEdit(edit, found);
            });
        });
        return true;
    }

    /**
     * The 3D drawing on show saved to Downloads as a .glb, for Blender, Unity, Unreal or Godot: each part its own
     * mesh and a joint under the part it hangs from.
     */
    private void exportSolid() {
        final ai.dvine.render.field.SolidSpec spec = currentSolid;
        final String what = lastDrawn == null ? "drawing" : lastDrawn;
        final String file = ai.dvine.render.field.Gltf.fileName(what);
        stage.innerVoice("Saving " + file + " to Downloads\u2026");
        modelThread.execute(() -> {
            boolean ok;
            try {
                byte[] glb = ai.dvine.render.field.Gltf.glb(spec, what);
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file);
                v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "model/gltf-binary");
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    ok = false;
                } else {
                    try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) {
                        o.write(glb);
                    }
                    ok = true;
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean saved = ok;
            handler.post(() -> play(LocalTurn.speakAs(stage.agent(), saved
                    ? "Saved " + file + " in your Downloads. Every part is its own piece with a joint, ready for Blender, Unity, Unreal or Godot."
                    : "I couldn't save it to Downloads."), null));
        });
    }

    private void applyEdit(ai.dvine.render.field.PartEdit edit, List<Integer> which) {
        List<ai.dvine.render.field.SolidSpec.Part> removed = new ArrayList<ai.dvine.render.field.SolidSpec.Part>();
        ai.dvine.render.field.SolidSpec next = edit.apply(currentSolid, which, removed);
        if (next.parts.isEmpty()) {
            play(LocalTurn.speakAs(stage.agent(), "That would leave nothing. Say clear it if you want it gone."), null);
            return;
        }
        solidHistory.push(currentSolid);
        takenOut.addAll(removed);
        String names = ai.dvine.render.field.PartEdit.names(currentSolid, which);
        String line;
        switch (edit.op) {
            case REMOVE: line = "Took out the " + names + "."; break;
            case ONLY: line = "Just the " + names + " now."; break;
            case COLOUR: line = "The " + names + ", recoloured."; break;
            case BIGGER: line = "Bigger " + names + "."; break;
            case SMALLER: line = "Smaller " + names + "."; break;
            case WIDER: line = "Wider " + names + "."; break;
            case TALLER: line = "Taller " + names + "."; break;
            default: line = "Done."; break;
        }
        showEdit(next, line);
    }

    private void showEdit(ai.dvine.render.field.SolidSpec next, String line) {
        while (solidHistory.size() > 20) {
            solidHistory.removeLast();
        }
        currentSolid = next;
        stage.setMode(StageMode.CONTENT);
        stage.reshapeSolid(next.points(ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f));
        stage.innerVoice(line);
        play(LocalTurn.speakAs(stage.agent(), line), null);
    }

    private boolean shapeHintGiven;

    /** The brain reads a way of moving the drawing DVINE doesn't know, as one it does; DVINE does it and learns it. */
    private void askViewIntent(final String asked) {
        final int id = ++askId;
        stage.setMode(StageMode.THINKING);
        stage.innerVoice("Heard: \u201c" + clip(asked) + "\u201d. Asking my brain how to move it\u2026");
        modelThread.execute(() -> {
            String written = model.task(ai.dvine.agent.ViewIntent.system(), asked, 30, text -> id == askId);
            final String command = ai.dvine.agent.ViewIntent.read(written);
            handler.post(() -> {
                if (id != askId) {
                    return;
                }
                if (command == null) {
                    stage.setMode(StageMode.CONTENT);
                    play(LocalTurn.speakAs(stage.agent(), "I'm not sure how to move it like that. Try spin it, rotate it vertically, or show me the top."), null);
                    return;
                }
                learned.learn(asked, command);
                stage.innerVoice("You mean: " + command + ". I'll remember that.");
                answer(command, true);
            });
        });
    }

    private static String changeLine(ai.dvine.render.field.ShapeCommand.Op op) {
        switch (op) {
            case BIGGER: return "Bigger.";
            case SMALLER: return "Smaller.";
            case SPIN: return "Spinning.";
            case SPIN_VERTICAL: return "Rolling it over.";
            case FASTER: return "Faster.";
            case SLOWER: return "Slower.";
            case TURN_BY: return "Turned.";
            case TILT_BY: return "Tilted.";
            case VIEW: return "There's the view.";
            case STOP: return "Holding it still.";
            case TURN: return "Here's the other side.";
            case COLOUR: return "Done.";
            case SCATTER: return "Boom.";
            case GATHER: return "Back together.";
            case CLEAR: return "Cleared.";
            case RESET: return "Back to how it was.";
            default: return "There.";
        }
    }

    /** Loads the installed model in the background; {@code tell} says so aloud when it's ready. */
    private void loadModel(final boolean tell) {
        modelThread.execute(() -> {
            final boolean ok = model.load(ai.dvine.llm.LocalModel.threads(Runtime.getRuntime().availableProcessors()));
            handler.post(() -> {
                stage.activity("MODEL", ok ? "ready, offline" : model.problem());
                int taught = journal == null ? 0 : journal.taughtCount();
                if (ok) {
                    // in step with this build: the brain is told about DVINE, the three of us and what the user taught
                    stage.activity("BRAIN", "synced with DVINE " + BuildConfigInfo.version(this) + ": 3 personas, "
                            + taught + (taught == 1 ? " taught fact" : " taught facts"));
                }
                if (tell) {
                    play(LocalTurn.speakAs(stage.agent(), ok ? "My brain is ready and in sync with DVINE. It knows Ivie, Obi and Ibiko, what this app can do"
                            + (taught > 0 ? ", and the " + (taught == 1 ? "thing" : taught + " things") + " you've taught me" : "")
                            + ". Ask me anything, even offline."
                            : "I couldn't start my brain: " + model.problem() + "."), null);
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
                play(LocalTurn.speakAs(stage.agent(), "Okay, not now. Say download your brain whenever you're ready."), null);
            }
            return true;
        }
        if (confirmModelRemove && (yes || no)) {
            confirmModelRemove = false;
            String line = !yes ? "Okay, I'll keep it." : model.uninstall() ? "Done. My brain files are removed; I'm on my offline notes again."
                    : "I couldn't delete the model file.";
            play(LocalTurn.speakAs(stage.agent(), line), null);
            return true;
        }
        confirmModelDownload = false;
        confirmModelRemove = false;
        String thing = "(your |the |a |my )?(brain|language model|llm|ai model|offline model|model)";
        boolean ivie = stage.isIvie();
        if (said.matches("^(please )?(download|install|get|add|set up)( me)? " + thing + "( please)?$")
                || said.matches("^(upgrade your brain|make yourself smarter|get smarter)$")) {
            if (model.state() == ai.dvine.llm.LocalModel.State.UNSUPPORTED) {
                play(LocalTurn.speakAs(ivie, "My brain can't run on this phone: " + model.problem() + "."), null);
            } else if (model.installed()) {
                play(LocalTurn.speakAs(ivie, model.ready() ? "My brain is already installed and running."
                        : "It's already installed. Starting it now."), null);
                if (!model.ready()) {
                    loadModel(true);
                }
            } else if (model.state() == ai.dvine.llm.LocalModel.State.DOWNLOADING) {
                play(LocalTurn.speakAs(ivie, "It's downloading now. I'll tell you when it's ready."), null);
            } else {
                confirmModelDownload = true;
                play(LocalTurn.speakAs(ivie, "My brain files are a " + model.spec.sizeWords()
                        + " download, best on Wi-Fi, and then I can think offline. Shall I start?"), this::startListening);
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
            stage.setDownload(true, "Downloading brain files", model.installed() ? 100 : st.percent(),
                    st.phase() == ai.dvine.llm.DownloadStatus.Phase.DOWNLOADING,
                    model.installed() ? "installed" : st.phase() == ai.dvine.llm.DownloadStatus.Phase.IDLE ? "not started: say download your brain" : st.detail());
            play(LocalTurn.speakAs(ivie, model.installed() ? "My brain files are already downloaded." : st.spoken()), null);
            return true;
        }
        if (said.matches("^(hide|close|remove)( the)? " + dl + "$")) {
            downloadBarHidden = true;
            stage.setDownload(false, "", 0, false, "");
            play(LocalTurn.speakAs(ivie, "Hidden. It keeps downloading; ask me how far it is any time."), null);
            return true;
        }
        if (said.matches("^(how('s| is) (the |my )?download( going)?|how far (is|along is) (the |my )?download|(what('s| is) the )?download (progress|status)|is (the |my )?download (done|finished|complete))$")) {
            play(LocalTurn.speakAs(ivie, model.installed() ? "The download is finished. My brain is installed." : hub.status.spoken()), null);
            return true;
        }
        if (said.matches("^(delete|remove|uninstall) " + thing + "$")) {
            if (!model.installed()) {
                play(LocalTurn.speakAs(ivie, "My brain files aren't installed."), null);
            } else {
                confirmModelRemove = true;
                play(LocalTurn.speakAs(ivie, "Remove my brain files and free " + model.spec.sizeWords() + "? Say yes or no."),
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
                play(LocalTurn.speakAs(ivie, model.ready() ? "My brain is on." : "It isn't installed yet. Say download your brain."), null);
            }
            return true;
        }
        if (said.matches("^(is|are) " + thing + " (installed|ready|on|working)$|^(language model|model|brain) status$")) {
            String line;
            switch (model.state()) {
                case READY: line = "My brain is running on this phone, offline."; break;
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
            play(LocalTurn.speakAs(stage.agent(), "I'm offline right now. Connect to the internet, ideally Wi-Fi, and ask me again."), null);
            return;
        }
        if (hub.downloading()) {
            play(LocalTurn.speakAs(stage.agent(), hub.status.spoken()), null);
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFY); // for the progress notification
        }
        downloadBarHidden = false;
        hub.startDownload(this);
        play(LocalTurn.speakAs(stage.agent(), "Downloading now, in the background. You can minimise me; the notification shows how far it is."), null);
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
        return a.action == null && a.command == null && !a.listenAfter && a.sketch == null
                && a.object3d == null;
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
        stage.setMode(StageMode.THINKING);
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

    /**
     * Online: hands the question to the server's desk while a thought bubble
     * shows, then plays its lines. If the server can't be reached the
     * offline answer plays instead. Returns false when offline.
     */
    /**
     * Dual-AI: both personas in turn, while each answers through
     * its own session (the offline brain as that persona, or that persona's own desk question
     * online); the second hears the first. Runs off the main thread; any new input cancels it.
     */
    private void discuss(final ai.dvine.turn.DualAgentCoordinator.Request r) {
        stopPlay();
        final String first = r.first != null ? r.first : stage.agent();
        stage.setThought(first.equals("ivie") ? "Let me think, then Obi can weigh in…" : "Let me think, then Ivie can weigh in…");
        stage.setMode(StageMode.THINKING);
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
        stage.setMode(StageMode.THINKING);
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
        stage.burst(24);
        play(LocalTurn.speakAs(true, Onboarding.welcome(name)), null);
    }

    private void applyCommand(String command, String value) {
        if (command == null) {
            return;
        }
        switch (command) {
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
        play(LocalTurn.call(stage.agent()), null);
    }

    /** Brings a persona's voice forward and keeps the choice; they say hello. */
    private void switchFace(String agent, boolean greet) {
        prefs.edit().putString("face", agent).apply();
        stopPlay();
        if (greet) {
            play(LocalTurn.call(agent), null);
        } else {
            stage.ensureFace(agent, null);
        }
    }

    // push to talk: hold to speak, let go to send. Pauses while holding don't end it; the words gather up.
    private boolean holdingMic, releasedMic;
    private final StringBuilder heldWords = new StringBuilder();
    private String lastPartial = "";
    private int holdToken;

    @Override
    public void onMic() {
        holdingMic = true;
        releasedMic = false;
        heldWords.setLength(0);
        lastPartial = "";
        startListening(true);
    }

    @Override
    public void onMicRelease() {
        if (!holdingMic) {
            return;
        }
        holdingMic = false;
        releasedMic = true;
        final int token = ++holdToken;
        listen.finish();
        // if the recogniser has nothing more to say, send what was heard so far
        handler.postDelayed(() -> {
            if (token == holdToken && releasedMic) {
                sendHeld(lastPartial);
            }
        }, 1500);
    }

    /** Sends the words gathered while the finger was down (plus the last piece). */
    private void sendHeld(String last) {
        releasedMic = false;
        holdToken++;
        listen.stop();
        stage.setListening(false);
        String all = (heldWords + " " + (last == null ? "" : last)).trim().replaceAll("\\s+", " ");
        heldWords.setLength(0);
        lastPartial = "";
        if (all.isEmpty()) {
            stage.setMode(StageMode.CONTENT);
            stage.setSubtitle("");
            return;
        }
        stage.setSubtitle(all);
        answer(all);
    }

    private void startListening() {
        startListening(false);
    }

    private void startListening(boolean hold) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        stopPlay();
        stage.setListening(true);
        stage.setSubtitle("");
        stage.setMode(StageMode.LISTENING);
        listen.start(this, hold);
    }

    /** Double-tap: the text field, drawn in particles; tap it to type, then Send. */
    @Override
    public void onType() {
        if (typeBar != null) {
            typeBar.show(awaitingName ? "Your name" : "Type to DVINE");
        }
    }

    @Override
    public void onPartial(String text) {
        lastPartial = text;
        stage.setSubtitle((heldWords + " " + text).trim());
    }

    @Override
    public void onResult(String text) {
        if (holdingMic) {
            // a pause while the finger is still down: keep the words and keep listening
            heldWords.append(' ').append(text);
            lastPartial = "";
            stage.setSubtitle(heldWords.toString().trim());
            listen.start(this, true);
            return;
        }
        if (releasedMic) {
            sendHeld(text);
            return;
        }
        stage.setListening(false);
        stage.setSubtitle(text);
        answer(text);
    }

    @Override
    public void onError(String message) {
        if (holdingMic) {
            listen.start(this, true); // silence or a hiccup while holding: listen again
            return;
        }
        if (releasedMic) {
            sendHeld(lastPartial);
            return;
        }
        stage.setListening(false);
        stage.setSubtitle(message);
        stage.setMode(StageMode.ERROR);
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
            stage.setMode(StageMode.DONE);
            Runnable after = afterPlay;
            afterPlay = null;
            if (after == null && lastSaid(lines) != null && ai.dvine.turn.Followup.wantsReply(lastSaid(lines))) {
                after = this::startListening; // it asked something: the microphone opens for the answer
            }
            final Runnable done = after;
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
        final String who = line.agent == null ? stage.agent() : line.agent;
        final boolean ivie = "ivie".equals(who);
        stage.ensureFace(who, () -> {
            if (token != playId) {
                return;
            }
            talk.setRobot("ibiko".equals(who)); // Ibiko speaks with a brighter, quicker voice
            if ("SAY".equals(line.cue)) {
                stage.setMode(StageMode.SPEAKING);
                stage.setThought(null);
                stage.setSubtitle(line.text);
                talk.speak(ivie, line.text, () -> step(token));
            } else if (line.text != null && line.text.length() > 0) {
                // inner thought: softer voice, the words in the inner voice at the top
                stage.setThought(line.text);
                stage.setMode(StageMode.THINKING);
                stage.setSubtitle("");
                talk.whisper(ivie, line.text, () -> step(token));
            } else {
                stage.setThought(null);
                stage.setMode(StageMode.THINKING);
                handler.postDelayed(() -> step(token), 520);
            }
        });
    }

    /**
     * What the brain knows about DVINE, built from this build and this phone each time it answers: the app and its
     * version, the three personas and how to switch, what the app can do, the user's name and how much they taught.
     */
    private String selfSheet() {
        ai.dvine.llm.DvineSelf.Facts f = new ai.dvine.llm.DvineSelf.Facts();
        f.version = BuildConfigInfo.version(this);
        f.device = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim();
        f.userName = brain.userName();
        f.taughtFacts = journal == null ? 0 : journal.taughtCount();
        f.speaking = stage.agent();
        return ai.dvine.llm.DvineSelf.sheet(f);
    }

    /** The last line spoken in a turn, or null. */
    private static String lastSaid(List<TurnLine> said) {
        for (int i = said.size() - 1; i >= 0; i--) {
            TurnLine l = said.get(i);
            if ("SAY".equals(l.cue) && l.text != null && !l.text.trim().isEmpty()) {
                return l.text;
            }
        }
        return null;
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
            play(LocalTurn.speakAs(stage.agent(),
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
