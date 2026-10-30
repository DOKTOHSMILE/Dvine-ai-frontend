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
 * No screen UI. One persona on black; touch and hold to speak, double-tap to type. Anything that leaves the app is asked out loud first and runs only after a spoken yes.
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
    private final java.util.concurrent.ExecutorService modelThread = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    private boolean confirmModelDownload, confirmModelRemove, streamOpen;
    private ai.dvine.llm.ModelHub hub;
    private ai.dvine.llm.DownloadStatus.Phase lastPhase = ai.dvine.llm.DownloadStatus.Phase.IDLE;
    private boolean downloadBarHidden;
    private static final int NOTIFY = 22;

    /** The background download changed: the card on screen, the activity panel, and a word when it ends. */
    private final Runnable onDownload = () -> {
        ai.dvine.llm.DownloadStatus st = hub.status;
        ai.dvine.llm.DownloadStatus.Phase phase = st.phase();
        boolean show = !downloadBarHidden && (st.active()
                || phase != lastPhase && phase != ai.dvine.llm.DownloadStatus.Phase.IDLE);
        stage.setDownload(show || stage.downloadShown() && !downloadBarHidden,
                "Downloading brain files", st.percent(),
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
                stage.setDownload(true, "Brain files downloaded", 100, false,
                        "verified, starting now");
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
    private final java.util.concurrent.ExecutorService deskThread = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    private int askId;
    private int readToken;
    private volatile ai.dvine.llm.ThinkPace thinkPace;
    private int thinkToken;
    private String thinkWrite = "Writing the answer";
    private String lastTaskLine = "";
    private final Runnable taskBeat = new Runnable() {
        public void run() {
            if (assistant != null && !isFinishing()) {
                String line = assistant.activityLine();
                if (line != null && !line.isEmpty() && !line.equals(lastTaskLine)) {
                    lastTaskLine = line;
                    stage.activity("TASK", line);
                    stage.progress(line);
                }
            }
            if (!isFinishing()) {
                handler.postDelayed(this, 1000);
            }
        }
    };
    private final Runnable thinkBeat = new Runnable() {
        public void run() {
            ai.dvine.llm.ThinkPace pace = thinkPace;
            if (pace == null || thinkToken != readToken) {
                return;
            }
            pace.tick(System.currentTimeMillis());
            stage.progress(pace.line());
            handler.postDelayed(this, 800);
        }
    };
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
        final Thread.UncaughtExceptionHandler previous = Thread
                .getDefaultUncaughtExceptionHandler();
        final File out = new File(getFilesDir(), "crash.txt");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(out))) {
                w.println("DVINE " + BuildConfigInfo.version(this) + " on Android "
                        + android.os.Build.VERSION.RELEASE + " (" + android.os.Build.MANUFACTURER
                        + " " + android.os.Build.MODEL + ")");
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
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** The text field: full width on a phone, at most 560 dp and centred on a tablet or a phone on its side. */
    private void fitTypeBar() {
        if (typeBar == null) {
            return;
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int m = (int) (14 * dm.density);
        android.widget.FrameLayout.LayoutParams tb = (android.widget.FrameLayout.LayoutParams) typeBar
                .getLayoutParams();
        int screen = getResources().getConfiguration().screenWidthDp;
        tb.width = screen > 600 ? (int) (560 * dm.density)
                : android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        tb.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        boolean side = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        tb.setMargins(m, m, m, side ? m : m * 4);
        typeBar.setLayoutParams(tb);
    }

    /**
     * Turned, folded or resized: nothing restarts (the conversation, a drawing and the brain carry on); the stage lays itself out again for the new size and the text field fits the new width.
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        fitTypeBar();
        if (stage != null) {
            stage.requestLayout();
            stage.invalidate();
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

        // 1. Use WindowCompat instead of calling directly on window
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);

        stage = new StageView(this);
        stage.setListener(this);

        final ai.dvine.render.PodView podView = new ai.dvine.render.PodView(this);
        stage.attach(podView);

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(podView);
        root.addView(stage);

        typeBar = new ai.dvine.stage.TypeBar(this, text -> answer(text));
        android.widget.FrameLayout.LayoutParams tb = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        root.addView(typeBar, tb);
        fitTypeBar();
        setContentView(root);

        // 2. Add the OnApplyWindowInsetsListener to satisfy Android 15 requirements
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());

            // Adjust the typeBar bottom margin dynamically based on system navigation bar insets
            android.view.ViewGroup.LayoutParams lp = typeBar.getLayoutParams();
            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) lp;
                boolean side = getResources()
                        .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int m = (int) (14 * getResources().getDisplayMetrics().density);
                flp.setMargins(m, m, m, (side ? m : m * 4) + insets.bottom);
                typeBar.setLayoutParams(flp);
            }

            return windowInsets;
        });

        // only after setContentView: before it the window has no decor and this throws
        WindowInsetsController bars = window.getInsetsController();
        if (bars != null) {
            bars.hide(WindowInsets.Type.systemBars());
            bars.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
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
        talk.setTuning(false,
                ai.dvine.voice.VoiceTuning.parse(prefs.getString("voice_obi", null), false));
        talk.setTuning(true,
                ai.dvine.voice.VoiceTuning.parse(prefs.getString("voice_ivie", null), true));
        talk.setWordListener(stage::speechWord);
        talk.setListener((ivie, speaking) -> {
            ambient.setPersona(ivie);
            ambient.setLevel(speaking ? 1f : 0f);
        });

        KnowledgeStore store = new KnowledgeStore(this);
        journal = new LearningJournal(new File(getFilesDir(), "learning"));
        learned = new ai.dvine.agent.LearnedIntents(new File(getFilesDir(), "learned_intents.txt"));
        library = new ai.dvine.library.DrawingLibrary(new File(getFilesDir(), "drawings"));
        assistant = new ai.dvine.ops.AssistantCore(new File(getFilesDir(), "ops"),
                new ai.dvine.ops.termux.TermuxCapabilityDetector.Probe() {
                    @Override
                    public boolean installed() {
                        return ai.dvine.ops.TermuxAndroid.installed(MainActivity.this);
                    }

                    @Override
                    public boolean permissionGranted() {
                        return checkSelfPermission(
                                "com.termux.permission.RUN_COMMAND") == android.content.pm.PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public boolean resultBridgeConnected() {
                        return false;
                    }
                }, null, null, null);
        String restored = assistant.restoredSummary();
        if (restored != null) {
            stage.activity("TASK", restored);
        }
        handler.postDelayed(taskBeat, 1000);
        // core pack now (small); the dictionary and other shards load in the background
        brain = new OfflineBrain(store.loadCore(), journal, System::currentTimeMillis,
                TimeZone.getDefault());
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
        final ai.dvine.net.SessionClient session = new ai.dvine.net.SessionClient(this::serverUrl,
                prefs, new ai.dvine.net.DeviceVault(prefs));
        sync = new SyncWorker(this::serverUrl, session::deviceId, journal, store, session::token,
                pack -> {
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
        web = new ai.dvine.net.WebClient(session::serverRoot, session::token, session::invalidate,
                null);
        creation = new ai.dvine.create.CreationDirector(stage.objects,
                new File(getFilesDir(), "designs"));
        engine = new ai.dvine.engine.Engine(stage, getFilesDir(), journal::consent);
        sync.setEpisodes(engine.episodes, profile -> handler
                .post(() -> engine.applyProfile(profile, android.os.SystemClock.uptimeMillis())));
        sync.setUploadListener(count -> handler.post(() -> {
            stage.burst(12);
            stage.setSubtitle(
                    "Backed up " + count + (count == 1 ? " thing" : " things") + " I learned.");
        }));
        connectivity = new Connectivity(this, online -> {
            stage.setOnline(online);
            if (online) {
                sync.kick(brain.pack().version);
            }
            // research for 3D objects goes through the server while online
            creation.setFetcher(
                    online && serverUrl() != null && serverUrl().trim().startsWith("https://")
                            ? web::fetch
                            : null);
        });

        stage.wake();
        handler.postDelayed(this::greetOnOpen, 700);
    }

    private void greetOnOpen() {
        if (takeCrashReport() != null) {
            play(LocalTurn.speakAs(true,
                    "I closed unexpectedly last time. Say copy the crash report, and I'll put the details on your clipboard so you can send them to the developer."),
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

    private ai.dvine.ops.AssistantCore assistant;
    private static final int PICK_CONTENT = 47;
    private boolean pickerLearn;
    private boolean pickerReconstruct;

    private void answer(String text) {
        answer(text, false);
    }

    /** Starts a live progress line. Safe from the worker thread. */
    private void armThink(String phase, String writing) {
        thinkToken = readToken;
        thinkWrite = writing == null ? "Writing the answer" : writing;
        thinkPace = new ai.dvine.llm.ThinkPace(phase, System.currentTimeMillis());
        handler.post(() -> {
            if (thinkPace == null || thinkToken != readToken) {
                return;
            }
            stage.setMode(StageMode.THINKING);
            stage.progress(thinkPace.line());
            handler.removeCallbacks(thinkBeat);
            handler.postDelayed(thinkBeat, 800);
        });
    }

    private void endThink() {
        thinkPace = null;
        handler.removeCallbacks(thinkBeat);
    }

    /** Counts words as they arrive. A lone progress ping means hidden reasoning, which is not spoken. */
    private boolean modelPiece(int id, String text) {
        if (id != askId) {
            return false;
        }
        ai.dvine.llm.ThinkPace pace = thinkPace;
        if (pace != null && text != null) {
            if ("\u0001".equals(text)) {
                pace.phase("Thinking");
            } else {
                pace.phase(thinkWrite);
                pace.addText(text);
            }
            handler.post(() -> {
                if (id == askId && thinkPace != null) {
                    stage.progress(thinkPace.line());
                }
            });
        }
        return true;
    }

    /** {@code interpreted}: the words are a command the brain already read; they go straight to DVINE's own brain. */
    private void answer(String text, boolean interpreted) {
        readToken++;
        if (model != null) {
            model.cancel(); // a new question stops the reply already being written
        }
        endThink();
        fromBrain = interpreted;
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!interpreted && assistant != null) {
            ai.dvine.ops.Outcome outcome = assistant.hear(text, 1);
            if (outcome.persona != null && !outcome.persona.isEmpty()) {
                prefs.edit().putString("face", outcome.persona).apply();
                stage.ensureFace(outcome.persona, null);
            }
            if (outcome.handled) {
                presentOutcome(outcome);
                return;
            }
            ai.dvine.ops.pipe.BrainCoordinator.Decision decision = assistant.lastDecision();
            boolean unsupported = decision != null
                    && decision.kind == ai.dvine.ops.pipe.BrainCoordinator.Kind.UNSUPPORTED;
            if (unsupported && model != null && model.ready() && assistant.hasModel()) {
                final String pending = text;
                final int mine = readToken;
                armThink("Reading your words", "Writing the answer");
                modelThread.execute(() -> {
                    final ai.dvine.ops.Outcome later = assistant.consider(pending, 1);
                    handler.post(() -> {
                        if (mine != readToken) {
                            return;
                        }
                        if (later.persona != null && !later.persona.isEmpty()) {
                            prefs.edit().putString("face", later.persona).apply();
                            stage.ensureFace(later.persona, null);
                        }
                        if (later.handled) {
                            endThink();
                            presentOutcome(later);
                        } else {
                            answer(pending, true);
                        }
                    });
                });
                return;
            }
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
        if (libraryTurn(text)) {
            return;
        }
        if (text.toLowerCase(java.util.Locale.US)
                .matches(".*copy (the |my )?crash( report| log)?.*")) {
            String report = takeCrashReport();
            if (report == null) {
                play(LocalTurn.speakAs(stage.agent(),
                        "There's no crash report. Everything has been running fine."), null);
            } else if (Actions.copy(this, report)) {
                new File(getFilesDir(), "crash.txt").delete();
                stage.burst(18);
                play(LocalTurn.speakAs(stage.agent(),
                        "Copied. The crash report is on your clipboard; paste it to the developer."),
                        null);
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
            String not = reading.core.replaceFirst("^(please do not|do not|never) ", "")
                    .replaceAll("\\bmy\\b", "your");
            play(LocalTurn.speakAs(stage.agent(), "Okay, I won't " + not + "."), null);
            return;
        }
        if (reading.clarify != null) {
            play(LocalTurn.speakAs(stage.agent(), reading.clarify), this::startListening);
            return;
        }
        final String asked = reading.rewritten ? reading.resolved : text;
        final boolean modelReady = model != null && model.ready()
                && prefs.getBoolean("llm_on", true);
        String lowAsked = asked.toLowerCase(java.util.Locale.US);
        if (lowAsked.matches(
                "^(do not|don'?t|stop|never) (look|looking|check|checking) (things |anything )?(up )?online.*")) {
            prefs.edit().putBoolean("fresh_facts", false).apply();
            play(LocalTurn.speakAs(stage.agent(),
                    "Alright. I won't look anything up online; I'll answer from what I know and what you teach me."),
                    null);
            return;
        }
        if (lowAsked.matches(
                "^(you can |please )?(look|check) (things |anything )?up online( again)?$|^(start|keep) (looking|checking) (things )?up online$")) {
            prefs.edit().putBoolean("fresh_facts", true).apply();
            play(LocalTurn.speakAs(stage.agent(),
                    "Okay. When we're online, I'll check the latest before answering questions about the world."),
                    null);
            return;
        }
        // changing one part of the 3D drawing on show: "take out the tyres", "make the roof blue", "add a spoiler"
        if (editParts(asked)) {
            return;
        }
        // changing the thing on show: "make it bigger", "spin it", "make it red", "scatter it", "clear it"
        ai.dvine.render.field.ShapeCommand change = stage.pictureShown()
                ? ai.dvine.render.field.ShapeCommand.parse(asked)
                : null;
        if (change != null && stage.changePicture(change)) {
            play(LocalTurn.speakAs(stage.agent(), changeLine(change.op)), null);
            return;
        }
        // said some other way ("make it do cartwheels", "can I see it from above"): the brain rewrites it into one of
        // the commands DVINE knows, DVINE does it and remembers the words
        if (change == null && !interpreted && stage.pictureShown() && modelReady
                && ai.dvine.agent.ViewIntent.looksLikeOne(asked)) {
            askViewIntent(asked);
            return;
        }
        ai.dvine.create.ObjectRequestInterpreter.Request known = creation.parse(asked,
                stage.objectShown());
        if (known != null
                && known.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.UNKNOWN_OBJECT
                && known.detail != null && modelReady) {
            askDrawing(known.detail, true); // something the creation engine can't model: the language model plans it in 3D
            return;
        }
        if (create(asked)) {
            return;
        }
        String formWhat = ai.dvine.llm.DrawPrompt.formSubject(asked);
        if (formWhat != null && modelReady
                && ai.dvine.render.forms.Forms
                        .objectFor(asked.toLowerCase(java.util.Locale.US)) == null
                && ai.dvine.stage.Sketch
                        .forRequest(asked.toLowerCase(java.util.Locale.US)) == null) {
            askDrawing(formWhat, true);
            return;
        }
        ai.dvine.turn.DualAgentCoordinator.Request both = ai.dvine.turn.DualAgentCoordinator
                .parse(asked);
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
        stage.activity("ANSWER",
                reply.answered ? "from " + reply.source.toLowerCase(java.util.Locale.US)
                        : "not in my notes yet");
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
                test.add(new TurnLine(ivieBorrows ? "ivie" : "obi", "SAY", "This phone has no "
                        + missing
                        + " voice, so I'm borrowing one and tuning it. Say install voices to add one."));
            }
            play(test, null);
            return;
        }
        if ("VOICE_INSTALL".equals(reply.command)) {
            play(reply.lines, () -> {
                try {
                    startActivity(new android.content.Intent(
                            android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                } catch (RuntimeException noInstaller) {
                    try {
                        startActivity(
                                new android.content.Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (RuntimeException none) {
                        play(LocalTurn.speakAs(stage.agent(),
                                "I couldn't open the voice settings on this phone."), null);
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
        if ("LEARN".equals(reply.source) || "MEMORY".equals(reply.source)
                || "OFFLINE".equals(reply.source)) {
            syncSoon(); // new learning (or a saved unanswered question) goes up as soon as we can
        }
        if ("POSE".equals(reply.command) && !stage.bodyShown()) {
            play(LocalTurn.speakAs(stage.agent(),
                    capabilities().missing(ai.dvine.agent.Interpretation.Task.MOVE)), null);
            return;
        }
        // the brain proposes; the engine validates, arbitrates and moves the body
        engine.perform(reply, now);
        if ("SELF_REPORT".equals(reply.command)) {
            play(LocalTurn.speakAs(stage.agent(), engine.selfReport(now) + " " + stage.inspect()),
                    null);
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
                play(LocalTurn.speakAs(stage.agent(),
                        "The clipboard refused that. Nothing was copied."), null);
            }
            return;
        }
        if (act != null && act.kind == PendingAction.Kind.SHARE) {
            play(reply.lines, () -> {
                stage.burst(18);
                if (!Actions.share(this, act)) {
                    play(LocalTurn.speakAs(stage.agent(),
                            "I couldn't find an app to share with on this phone."), null);
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
            askDrawing(drawWhat,
                    model != null && model.ready() || ai.dvine.llm.DrawPrompt.in3d(asked));
            return;
        }
        // knowledge and conversation go to the language model when it's installed; commands, maths, time, memory,
        // what the user taught and anything that acts stay with the offline brain (exact, and instant)
        boolean modelTopic = (!reply.answered || reply.needsOnline
                || MODEL_SOURCES.contains(reply.source)) && plain(reply);
        if (modelTopic && model != null && model.ready() && prefs.getBoolean("llm_on", true)
                && (fromBrain ? askLocal(asked, reply, false) : agentTurn(asked, reply))) {
            return;
        }
        if (modelTopic && offerModel(reply)) {
            return;
        }
        if (modelTopic && (model == null || !model.ready()) && ai.dvine.llm.FreshFacts.worthLooking(asked)) {
            play(LocalTurn.speakAs(stage.agent(), "I don't have a current source for that, so I won't guess."), null);
            return;
        }
        if (reply.needsOnline && askDesk(asked, reply)) {
            return;
        }
        if (model != null && plain(reply)) {
            model.note(asked, spoken(reply.lines)); // so the model reads what follows in the light of it
        }
        play(reply.lines,
                reply.listenAfter || endsInQuestion(reply.lines) ? this::startListening : null);
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
        String part = hour < 5 ? "night"
                : hour < 12 ? "morning" : hour < 17 ? "afternoon" : hour < 21 ? "evening" : "night";
        return ai.dvine.llm.PersonaPrompt.situation(ivie, examiner.lastTopic(),
                stage.pictureShown() ? lastDrawn : null, part,
                connectivity != null && connectivity.isOnline());
    }

    // ------------------------------------------------------------ the on-device language model

    /** Answer sources the language model does better: dictionary, packed facts, wisdom, comfort, unknown questions. */
    private static final java.util.Set<String> MODEL_SOURCES = new java.util.HashSet<String>(
            java.util.Arrays.asList("DICT", "PACK", "WISDOM", "FACT", "CARE", "OFFLINE"));

    /**
     * Once, the first time a question would have gone to the language model but it isn't installed: the offline answer, then the offer to install it.
     */
    private boolean offerModel(BrainAnswer reply) {
        if (model == null || model.installed() || prefs.getBoolean("llm_offered", false)
                || model.state() == ai.dvine.llm.LocalModel.State.UNSUPPORTED
                || model.state() == ai.dvine.llm.LocalModel.State.DOWNLOADING
                || !ai.dvine.llm.NativeLlm.available()) {
            return false;
        }
        prefs.edit().putBoolean("llm_offered", true).apply();
        List<TurnLine> lines2 = new ArrayList<TurnLine>(reply.lines);
        String agent = stage.agent();
        lines2.add(new TurnLine(agent, "SAY",
                "By the way, I can answer questions like this much better once my brain files are on this phone."
                        + " It runs on your phone, even offline, after a " + model.spec.sizeWords()
                        + " download. Shall I install it?"));
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

    /** One turn the way DVINE works: the brain reads what the user said and returns its interpretation. */
    private boolean agentTurn(final String question, final BrainAnswer offline) {
        final int id = ++askId;
        stopPlay();
        stage.setMode(StageMode.THINKING);
        stage.innerVoice("Heard: \u201c" + clip(question) + "\u201d");
        stage.innerVoice("Asking my brain what you mean\u2026");
        modelThread.execute(() -> {
            final String written = id == askId
                    ? model.task(ai.dvine.agent.Interpretation.PROMPT, question, 90, null)
                    : null;
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
                ai.dvine.agent.Interpretation.Task task = it.internet
                        && it.task == ai.dvine.agent.Interpretation.Task.ANSWER
                                ? ai.dvine.agent.Interpretation.Task.SEARCH
                                : it.task;
                String missing = capabilities().missing(task);
                stage.innerVoice(
                        "Can I do it here? " + (missing == null ? "Yes." : "No: " + clip(missing)));
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
                    stage.innerVoice(
                            "That reading isn't about what you said, so I won't act on it.");
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
        final String today = new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK)
                .format(new java.util.Date());
        final String name = brain.userName();
        modelThread.execute(() -> {
            ai.dvine.agent.Debate d = new ai.dvine.agent.Debate(topic);
            while (id == askId && !d.done()) {
                final String who = d.speaker(), role = d.role();
                armThink((who.equals("obi") ? "Obi" : "Ivie") + " is preparing the " + role,
                        "Writing the line");
                String sys = ai.dvine.llm.PersonaPrompt.system(who, name, today) + " " + d.rules();
                String said = model.task(sys, d.prompt(), 96, text -> modelPiece(id, text));
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
                    endThink();
                    stream.add(new TurnLine("ivie", "SAY", ai.dvine.agent.Debate.closing()));
                    streamOpen = false;
                }
            });
        });
    }

    private boolean askLocal(final String question, final BrainAnswer offline) {
        return askLocal(question, offline, false);
    }

    private boolean askLocal(final String question, final BrainAnswer offline,
            final boolean research) {
        final int id = ++askId;
        final boolean ivie = stage.isIvie();
        final String agent = stage.agent();
        stopPlay();
        stage.setMode(StageMode.THINKING);
        stage.activity("MODEL", "thinking offline");
        final List<TurnLine> stream = new ArrayList<TurnLine>();
        streamOpen = true;
        lines = stream;
        index = 0;
        afterPlay = null;
        step(playId);
        final String system = ai.dvine.llm.PersonaPrompt.system(agent, brain.userName(),
                new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK)
                        .format(new java.util.Date()),
                selfSheet());
        final java.util.List<String> taught;
        synchronized (brain) {
            taught = brain.taughtFor(question, 6);
        }
        final boolean look = prefs.getBoolean("fresh_facts", true) && connectivity != null
                && connectivity.isOnline()
                && (research || ai.dvine.llm.FreshFacts.worthLooking(question));
        armThink(look ? "Checking sources" : "Reading your words", "Writing the answer");
        final String around = situation(ivie);
        modelThread.execute(() -> {
            final String reference = look && id == askId
                    ? ai.dvine.ops.mind.CitedReference.collect(question, url -> {
                        byte[] body = ai.dvine.net.WebGet.get(url, 6000, 400_000);
                        if (body == null) {
                            return null;
                        }
                        try {
                            return new String(body, "UTF-8");
                        } catch (java.io.UnsupportedEncodingException e) {
                            return null;
                        }
                    }, System.currentTimeMillis())
                    : null;
            if (look) {
                handler.post(() -> {
                    if (id == askId) {
                        stage.innerVoice(reference != null ? "Found: " + clip(reference)
                                : "Nothing current found. I'll use what I know.");
                    }
                });
            }
            armThink("Writing the answer", "Writing the answer");
            final String context = ai.dvine.llm.PersonaPrompt.context(taught, reference, around);
            final ai.dvine.llm.SpeechChunker chunker = new ai.dvine.llm.SpeechChunker();
            final ai.dvine.llm.ActionTags tags = new ai.dvine.llm.ActionTags();
            final String all = model.reply(system, question, context, 160, text -> {
                if (!modelPiece(id, text)) {
                    return false;
                }
                if ("\u0001".equals(text)) {
                    return true;
                }
                final java.util.List<String> ready = chunker.add(tags.add(text));
                if (!ready.isEmpty()) {
                    handler.post(() -> {
                        if (id == askId) {
                            endThink();
                            for (String sentence : ready) {
                                stream.add(new TurnLine(agent, "SAY", sentence));
                            }
                        }
                    });
                }
                return id == askId;
            });
            chunker.add(tags.flush());
            final String rest = chunker.flush();
            final String switchTo = tags.switchTo();
            handler.post(() -> {
                if (id != askId) {
                    return;
                }
                endThink();
                if (all == null && stream.isEmpty()) {
                    stream.addAll(offline.lines);
                } else if (!rest.isEmpty()) {
                    stream.add(new TurnLine(agent, "SAY", rest));
                }
                if (switchTo != null && !switchTo.equals(agent)) {
                    afterPlay = () -> switchFace(switchTo, true);
                } else if (all != null
                        && ai.dvine.turn.Followup.wantsReply(ai.dvine.llm.ActionTags.strip(all))) {
                    afterPlay = this::startListening;
                }
                streamOpen = false;
            });
        });
        return true;
    }

    /** "Draw me a lighthouse": always in 3D, and nothing is built in. */
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
            armThink(research ? "Checking sources" : "Planning the parts", "Planning the parts");
        } else {
            stage.setThought((online ? "Finding a picture of " : "Building ") + subject + "\u2026");
        }
        modelThread.execute(() -> {
            ai.dvine.render.field.Shape picture = null;
            ai.dvine.render.field.SolidSpec plan = null;
            if (plan == null && modelOk && id == askId) {
                String facts = research ? ai.dvine.render.field.PictureFinder.describe(subject)
                        : null;
                if (facts != null) {
                    handler.post(() -> {
                        if (id == askId) {
                            stage.innerVoice("Found how it looks. Planning its parts\u2026");
                        }
                    });
                }
                String planned = model.task(ai.dvine.llm.DrawPrompt.system(),
                        ai.dvine.llm.DrawPrompt.user(subject, facts), 280,
                        text -> modelPiece(id, text));
                plan = ai.dvine.llm.DrawPrompt.read(planned, subject);
                if (!ai.dvine.llm.DrawPrompt.usable(plan, subject) && id == askId) {
                    handler.post(() -> {
                        if (id == askId) {
                            stage.innerVoice("That plan wasn't right. Trying again\u2026");
                        }
                    });
                    planned = model.task(ai.dvine.llm.DrawPrompt.system(),
                            ai.dvine.llm.DrawPrompt.retry(subject), 280,
                            text -> modelPiece(id, text));
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
                android.graphics.Bitmap bm = ai.dvine.render.field.Raster
                        .decode(ai.dvine.render.field.PictureFinder.find(subject));
                if (bm != null && id == askId) {
                    picture = ai.dvine.render.field.Raster.image(bm,
                            ai.dvine.render.field.ParticleDisplay.SKIN_POINTS, 2f);
                    if (picture.count < 200) {
                        picture = null;
                    }
                }
            }
            final ai.dvine.render.field.Shape shown = picture != null && picture.count > 0 ? picture
                    : null;
            final ai.dvine.render.field.SolidSpec built = shown == null ? null : plan;
            handler.post(() -> {
                if (id != askId || isFinishing()) {
                    return;
                }
                endThink();
                stage.setThought(null);
                if (shown == null) {
                    play(LocalTurn.speakAs(ivie, modelOk || online
                            ? "I tried to make " + ai.dvine.llm.DrawPrompt.article(subject)
                                    + ", but it didn't come out right. Ask me again?"
                            : "I need my brain files for that. Say download your brain, and I'll be able to build anything you ask."),
                            null);
                    return;
                }
                String line = "Here's " + ai.dvine.llm.DrawPrompt.article(subject) + " in 3D.";
                if (built != null) {
                    currentSolid = built;
                    currentName = null;
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
                    stage.showPhotograph(shown, subject);
                    line = "Here's a particle picture of "
                            + ai.dvine.llm.DrawPrompt.article(subject)
                            + ". It follows the photo. It is not a measured 3D model.";
                }
                play(LocalTurn.speakAs(ivie, line), null);
            });
        });
    }

    private ai.dvine.render.field.SolidSpec currentSolid;
    private final List<ai.dvine.render.field.SolidSpec.Part> takenOut = new ArrayList<ai.dvine.render.field.SolidSpec.Part>();
    private final java.util.ArrayDeque<ai.dvine.render.field.SolidSpec> solidHistory = new java.util.ArrayDeque<ai.dvine.render.field.SolidSpec>();

    /** A change to one part of the 3D drawing on show. */
    private boolean editParts(final String asked) {
        if (currentSolid == null || !stage.pictureShown()) {
            return false;
        }
        String low = asked.toLowerCase(java.util.Locale.US).trim().replaceAll("[.!?]+$", "");
        if (low.matches(
                "^(undo|undo that|go back|take that back|put it back how it was|change it back)$")) {
            if (solidHistory.isEmpty()) {
                return false;
            }
            currentSolid = solidHistory.pop();
            stage.reshapeSolid(
                    currentSolid.points(ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f));
            play(LocalTurn.speakAs(stage.agent(), "Undone."), null);
            return true;
        }
        final ai.dvine.render.field.PartEdit edit = ai.dvine.render.field.PartEdit.parse(asked);
        if (edit == null) {
            return false;
        }
        if (edit.op == ai.dvine.render.field.PartEdit.Op.RESTORE) {
            List<ai.dvine.render.field.SolidSpec.Part> back = ai.dvine.render.field.PartEdit
                    .pick(takenOut, edit.target);
            if (back.isEmpty()) {
                play(LocalTurn.speakAs(stage.agent(),
                        takenOut.isEmpty() ? "Nothing has been taken out."
                                : "I didn't take out any " + edit.target + ". I took out: "
                                        + ai.dvine.render.field.PartEdit.inventory(takenOut) + "."),
                        null);
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
                play(LocalTurn.speakAs(stage.agent(),
                        "To add new parts I need my brain. Say download your brain."), null);
                return true;
            }
            final int id = ++askId;
            final ai.dvine.render.field.SolidSpec before = currentSolid;
            stage.setMode(StageMode.THINKING);
            stage.innerVoice("Adding " + clip(edit.target) + " to the " + lastDrawn + "\u2026");
            modelThread.execute(() -> {
                String written = model.task(ai.dvine.llm.DrawPrompt.system(),
                        ai.dvine.render.field.PartEdit.addPrompt(before, lastDrawn, edit.target),
                        200, text -> id == askId);
                final ai.dvine.render.field.SolidSpec next = before.extend(written);
                handler.post(() -> {
                    if (id != askId) {
                        return;
                    }
                    if (next.parts.size() <= before.parts.size()) {
                        play(LocalTurn
                                .speakAs(stage.agent(),
                                        "I couldn't work out where " + edit.target
                                                + " would go. Try saying where, like on top."),
                                null);
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
            play(LocalTurn
                    .speakAs(stage.agent(),
                            "I can't find " + edit.target + " on it. It has: "
                                    + ai.dvine.render.field.PartEdit.inventory(currentSolid) + "."),
                    null);
            return true;
        }
        final int id = ++askId;
        final ai.dvine.render.field.SolidSpec on = currentSolid;
        stage.setMode(StageMode.THINKING);
        stage.innerVoice(
                "Which part is \u201c" + clip(edit.target) + "\u201d? Asking my brain\u2026");
        modelThread.execute(() -> {
            // FIX: Use ai.dvine.render.field.PartEdit instead of ai.dvine.llm.PartEdit
            String written = model.task(ai.dvine.render.field.PartEdit.whichSystem(),
                    ai.dvine.render.field.PartEdit.whichPrompt(on, lastDrawn, edit.target), 60,
                    text -> id == askId);
            final List<Integer> found = ai.dvine.render.field.PartEdit.fromBrain(on, written);
            handler.post(() -> {
                if (id != askId || on != currentSolid) {
                    return;
                }
                if (found.isEmpty()) {
                    play(LocalTurn
                            .speakAs(stage.agent(),
                                    "I can't find " + edit.target + " on it. It has: "
                                            + ai.dvine.render.field.PartEdit.inventory(on) + "."),
                            null);
                    return;
                }
                stage.innerVoice(
                        "Those are the " + ai.dvine.render.field.PartEdit.names(on, found) + ".");
                applyEdit(edit, found);
            });
        });
        return true;
    }

    /**
     * The 3D drawing on show saved to the phone, in Downloads/DVINE, as a 3D file other software opens.
     */
    private void exportSolid(final ai.dvine.render.field.MeshExport.Format format,
            final String called) {
        final ai.dvine.render.field.SolidSpec spec = currentSolid;
        final String what = called != null ? called : lastDrawn == null ? "drawing" : lastDrawn;
        final String base = ai.dvine.render.field.Gltf.fileName(what).replaceAll("\\.glb$", "");
        final String file = base + "." + format.extension;
        stage.innerVoice("Saving " + file + " to Downloads/DVINE\u2026");
        modelThread.execute(() -> {
            boolean ok;
            try {
                switch (format) {
                    case OBJ:
                        ok = writeDownload(base + ".mtl", "text/plain",
                                ai.dvine.render.field.MeshExport.mtl(spec).getBytes("UTF-8"))
                                && writeDownload(file, format.mime, ai.dvine.render.field.MeshExport
                                        .obj(spec, what, base + ".mtl").getBytes("UTF-8"));
                        break;
                    case STL:
                        ok = writeDownload(file, format.mime,
                                ai.dvine.render.field.MeshExport.stl(spec, what));
                        break;
                    case PLY:
                        ok = writeDownload(file, format.mime,
                                ai.dvine.render.field.MeshExport.ply(spec));
                        break;
                    default:
                        ok = writeDownload(file, format.mime,
                                ai.dvine.render.field.Gltf.glb(spec, what));
                        break;
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean saved = ok;
            handler.post(
                    () -> play(
                            LocalTurn.speakAs(stage.agent(), saved
                                    ? "Saved " + file
                                            + (format == ai.dvine.render.field.MeshExport.Format.OBJ
                                                    ? " and its colours file"
                                                    : "")
                                            + " in Downloads, in the DVINE folder. It opens in "
                                            + format.opensIn + "."
                                    : "I couldn't save it to Downloads."),
                            null));
        });
    }

    private boolean writeDownload(String file, String mime, byte[] data)
            throws java.io.IOException {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file);
        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DVINE");
        android.net.Uri uri = getContentResolver()
                .insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) {
            return false;
        }
        try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) {
            if (o == null) {
                return false;
            }
            o.write(data);
        }
        return true;
    }

    // ------------------------------------------------------------ saved drawings

    private ai.dvine.library.DrawingLibrary library;

    private interface Awaiting {
        void take(String said);
    }

    private Awaiting awaiting;

    private boolean libraryTurn(String text) {
        if (awaiting != null) {
            Awaiting a = awaiting;
            awaiting = null;
            String low = text.toLowerCase(java.util.Locale.US).trim().replaceAll("[.!?]+$", "");
            if (low.matches("^(cancel|never ?mind|forget it|stop|no thanks|leave it)$")) {
                play(LocalTurn.speakAs(stage.agent(), "Okay, left it."), null);
                return true;
            }
            a.take(text);
            return true;
        }
        ai.dvine.library.LibraryWords w = ai.dvine.library.LibraryWords.parse(text);
        if (w == null) {
            return false;
        }
        boolean drawingOn = currentSolid != null && stage.pictureShown();
        if (!drawingOn
                && (w.kind == ai.dvine.library.LibraryWords.Kind.SAVE
                        || w.kind == ai.dvine.library.LibraryWords.Kind.EXPORT)
                && !text.toLowerCase(java.util.Locale.US).matches("^(please )?(save|export).*")) {
            return false;
        }
        switch (w.kind) {
            case EXPORT:
                if (currentSolid == null || !stage.pictureShown()) {
                    play(LocalTurn.speakAs(stage.agent(),
                            "There's no 3D drawing on show to save. Ask me to draw something, or open one you saved."),
                            null);
                    return true;
                }
                exportSolid(ai.dvine.render.field.MeshExport.formatIn(text), currentName);
                return true;
            case SAVE:
                if (currentSolid == null || !stage.pictureShown()) {
                    play(LocalTurn.speakAs(stage.agent(),
                            "There's no 3D drawing on show to save. Ask me to draw something first."),
                            null);
                    return true;
                }
                if (w.name == null) {
                    askForName(
                            "What should I call it? You can add a description after a colon, like Robo, colon, my red robot.",
                            w.description);
                } else {
                    saveDrawing(w.name, w.description, false);
                }
                return true;
            case LIST: {
                java.util.List<ai.dvine.library.DrawingLibrary.Entry> all = library.all();
                if (all.isEmpty()) {
                    play(LocalTurn.speakAs(stage.agent(),
                            "You haven't saved any drawings yet. When one is on show, say save it as, and a name."),
                            null);
                    return true;
                }
                StringBuilder b = new StringBuilder(all.size() == 1 ? "You have one saved drawing: "
                        : "You have " + all.size() + " saved drawings: ");
                for (int i = 0; i < all.size() && i < 8; i++) {
                    b.append(i == 0 ? "" : i == Math.min(all.size(), 8) - 1 ? ", and " : ", ")
                            .append(all.get(i).name);
                }
                play(LocalTurn.speakAs(stage.agent(),
                        b.append(all.size() > 8 ? ", and more." : ". Say show me, and a name.")
                                .toString()),
                        null);
                return true;
            }
            case DELETE: {
                ai.dvine.library.DrawingLibrary.Found f = library.find(w.name);
                if (f.entries.size() != 1 || f.score < 60) {
                    play(LocalTurn.speakAs(stage.agent(),
                            f.entries.isEmpty()
                                    ? "I don't have a saved drawing called " + w.name + "."
                                    : "Which one? " + names(f.entries) + "."),
                            null);
                    return true;
                }
                final ai.dvine.library.DrawingLibrary.Entry e = f.entries.get(0);
                awaiting = said -> {
                    Boolean yes = ai.dvine.turn.Names.yesNo(said);
                    if (Boolean.TRUE.equals(yes) && library.delete(e)) {
                        play(LocalTurn.speakAs(stage.agent(), "Deleted " + e.name + "."), null);
                    } else {
                        play(LocalTurn.speakAs(stage.agent(), "Kept " + e.name + "."), null);
                    }
                };
                play(LocalTurn.speakAs(stage.agent(),
                        "Delete " + e.name + ", " + e.about() + "? Yes or no?"),
                        this::startListening);
                return true;
            }
            default: {
                ai.dvine.library.DrawingLibrary.Found f = library.find(w.name);
                if (f.score < (w.plainlySaved ? 35 : 80)) {
                    if (w.plainlySaved) {
                        play(LocalTurn.speakAs(stage.agent(),
                                library.size() == 0 ? "You haven't saved any drawings yet."
                                        : "I can't find a saved drawing like " + w.name
                                                + ". You have " + names(library.all()) + "."),
                                null);
                        return true;
                    }
                    return false;
                }
                if (f.entries.size() > 1) {
                    final java.util.List<ai.dvine.library.DrawingLibrary.Entry> choices = f.entries;
                    awaiting = said -> {
                        ai.dvine.library.DrawingLibrary.Found again = library.find(said);
                        for (ai.dvine.library.DrawingLibrary.Entry e : again.entries) {
                            if (choices.contains(e)) {
                                openDrawing(e);
                                return;
                            }
                        }
                        play(LocalTurn.speakAs(stage.agent(),
                                "I didn't catch which. Say show me, and its name."), null);
                    };
                    play(LocalTurn.speakAs(stage.agent(), "I found " + choices.size() + ": "
                            + describe(choices) + ". Which one?"), this::startListening);
                    return true;
                }
                openDrawing(f.entries.get(0));
                return true;
            }
        }
    }

    private String currentName;

    private void askForName(String question, final String description) {
        awaiting = said -> {
            String reply = said.trim();
            String name = reply, about = description;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "^(.+?)\\s*(?::| colon | - |, described as |, it'?s |, which is )\\s*(.+)$",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(reply);
            if (m.matches()) {
                name = m.group(1);
                about = m.group(2);
            }
            name = ai.dvine.library.LibraryWords.nameFrom(name);
            if (name == null || name.length() > 60) {
                askForName("I didn't catch a name. What should I call it?", description);
                return;
            }
            saveDrawing(name, about, false);
        };
        play(LocalTurn.speakAs(stage.agent(), question), this::startListening);
    }

    private void saveDrawing(final String name, final String description, boolean replace) {
        final ai.dvine.library.DrawingLibrary.Entry clash = library.named(name);
        if (clash != null && !replace) {
            awaiting = said -> {
                String low = said.toLowerCase(java.util.Locale.US).trim().replaceAll("[.!?]+$", "");
                if (low.matches(
                        "^(replace it|replace|overwrite( it)?|yes,? replace( it)?|save over it|use it anyway)$")) {
                    saveDrawing(name, description, true);
                } else {
                    String other = ai.dvine.library.LibraryWords.nameFrom(said);
                    if (other == null) {
                        askForName("What should I call it instead?", description);
                    } else {
                        saveDrawing(other, description, false);
                    }
                }
            };
            String when = new java.text.SimpleDateFormat("d MMMM", java.util.Locale.UK)
                    .format(new java.util.Date(clash.saved));
            play(LocalTurn.speakAs(stage.agent(), "The name " + clash.name
                    + " is already used for another drawing: " + clash.about() + ", saved on "
                    + when
                    + ". Give me another name or a short description to tell them apart, or say replace it."),
                    this::startListening);
            return;
        }
        try {
            ai.dvine.library.DrawingLibrary.Entry e = library.save(name, lastDrawn, description,
                    currentSolid, System.currentTimeMillis());
            currentName = e.name;
            stage.innerVoice("Saved \u201c" + e.name + "\u201d: " + clip(e.summary));
            play(LocalTurn.speakAs(stage.agent(), "Saved as " + e.name + ". Later, say show me "
                    + e.name + ", or describe it, and I'll find it."), null);
        } catch (java.io.IOException ex) {
            play(LocalTurn.speakAs(stage.agent(), "I couldn't save it on the phone."), null);
        }
    }

    private void openDrawing(ai.dvine.library.DrawingLibrary.Entry e) {
        ai.dvine.render.field.SolidSpec spec = library.open(e);
        if (spec.parts.isEmpty()) {
            play(LocalTurn.speakAs(stage.agent(),
                    "The drawing " + e.name + " is damaged; I can't open it."), null);
            return;
        }
        currentSolid = spec.fixFrame();
        currentName = e.name;
        lastDrawn = e.subject.isEmpty() ? e.name : e.subject;
        solidHistory.clear();
        takenOut.clear();
        stage.showSolid(spec.points(ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS, 2f),
                e.name);
        play(LocalTurn.speakAs(stage.agent(), "Here's " + e.name + "."), null);
    }

    private static String names(java.util.List<ai.dvine.library.DrawingLibrary.Entry> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size() && i < 8; i++) {
            b.append(i == 0 ? "" : i == Math.min(list.size(), 8) - 1 ? " and " : ", ")
                    .append(list.get(i).name);
        }
        return b.toString();
    }

    private static String describe(java.util.List<ai.dvine.library.DrawingLibrary.Entry> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size() && i < 5; i++) {
            b.append(i == 0 ? "" : "; ").append(list.get(i).name).append(", ")
                    .append(list.get(i).about());
        }
        return b.toString();
    }

    private void applyEdit(ai.dvine.render.field.PartEdit edit, List<Integer> which) {
        List<ai.dvine.render.field.SolidSpec.Part> removed = new ArrayList<ai.dvine.render.field.SolidSpec.Part>();
        ai.dvine.render.field.SolidSpec next = edit.apply(currentSolid, which, removed);
        if (next.parts.isEmpty()) {
            play(LocalTurn.speakAs(stage.agent(),
                    "That would leave nothing. Say clear it if you want it gone."), null);
            return;
        }
        solidHistory.push(currentSolid);
        takenOut.addAll(removed);
        String names = ai.dvine.render.field.PartEdit.names(currentSolid, which);
        String line;
        switch (edit.op) {
            case REMOVE:
                line = "Took out the " + names + ".";
                break;
            case ONLY:
                line = "Just the " + names + " now.";
                break;
            case COLOUR:
                line = "The " + names + ", recoloured.";
                break;
            case BIGGER:
                line = "Bigger " + names + ".";
                break;
            case SMALLER:
                line = "Smaller " + names + ".";
                break;
            case WIDER:
                line = "Wider " + names + ".";
                break;
            case TALLER:
                line = "Taller " + names + ".";
                break;
            default:
                line = "Done.";
                break;
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

    private void askViewIntent(final String asked) {
        final int id = ++askId;
        stage.setMode(StageMode.THINKING);
        stage.innerVoice(
                "Heard: \u201c" + clip(asked) + "\u201d. Asking my brain how to move it\u2026");
        modelThread.execute(() -> {
            String written = model.task(ai.dvine.agent.ViewIntent.system(), asked, 30,
                    text -> id == askId);
            final String command = ai.dvine.agent.ViewIntent.read(written);
            handler.post(() -> {
                if (id != askId) {
                    return;
                }
                if (command == null) {
                    stage.setMode(StageMode.CONTENT);
                    play(LocalTurn.speakAs(stage.agent(),
                            "I'm not sure how to move it like that. Try spin it, rotate it vertically, or show me the top."),
                            null);
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
            case BIGGER:
                return "Bigger.";
            case SMALLER:
                return "Smaller.";
            case SPIN:
                return "Spinning.";
            case SPIN_VERTICAL:
                return "Rolling it over.";
            case FASTER:
                return "Faster.";
            case SLOWER:
                return "Slower.";
            case TURN_BY:
                return "Turned.";
            case TILT_BY:
                return "Tilted.";
            case VIEW:
                return "There's the view.";
            case STOP:
                return "Holding it still.";
            case TURN:
                return "Here's the other side.";
            case COLOUR:
                return "Done.";
            case SCATTER:
                return "Boom.";
            case GATHER:
                return "Back together.";
            case CLEAR:
                return "Cleared.";
            case RESET:
                return "Back to how it was.";
            default:
                return "There.";
        }
    }

    private void loadModel(final boolean tell) {
        modelThread.execute(() -> {
            final boolean ok = model.load(
                    ai.dvine.llm.LocalModel.threads(Runtime.getRuntime().availableProcessors()));
            if (assistant != null && model.spec != null) {
                new ai.dvine.ops.mind.ModelAdapter(assistant.models()).report(model.spec.file, ok);
                assistant.orchestrator().setModel(ok ? ai.dvine.ops.pipe.CheckedReading.of(model, piece -> {
                    ai.dvine.llm.ThinkPace pace = thinkPace;
                    if (pace != null && piece != null) {
                        pace.addText(piece);
                        handler.post(() -> { if (thinkPace != null) stage.progress(thinkPace.line()); });
                    }
                }) : null);
            }
            handler.post(() -> {
                stage.activity("MODEL", ok ? "ready, offline" : model.problem());
                int taught = journal == null ? 0 : journal.taughtCount();
                if (ok) {
                    stage.activity("BRAIN",
                            "synced with DVINE " + BuildConfigInfo.version(this) + ": 3 personas, "
                                    + taught + (taught == 1 ? " taught fact" : " taught facts"));
                }
                if (tell) {
                    play(LocalTurn.speakAs(stage.agent(), ok
                            ? "My brain is ready and in sync with DVINE. It knows Ivie, Obi and Ibiko, what this app can do"
                                    + (taught > 0
                                            ? ", and the "
                                                    + (taught == 1 ? "thing" : taught + " things")
                                                    + " you've taught me"
                                            : "")
                                    + ". Ask me anything, even offline."
                            : "I couldn't start my brain: " + model.problem() + "."), null);
                }
            });
        });
    }

    /** Voice control of the language model with proper boolean return statements. */
    private boolean modelCommand(String said) {
        if (model == null) {
            return false;
        }
        boolean yes = said
                .matches("^(yes|yeah|yep|ok|okay|sure|go ahead|start|do it|please do)( please)?$");
        boolean no = said.matches("^(no|nope|not now|cancel|don't|do not|leave it)( please)?$");
        if (confirmModelDownload && (yes || no)) {
            confirmModelDownload = false;
            if (yes) {
                startModelDownload();
            } else {
                play(LocalTurn.speakAs(stage.agent(),
                        "Okay, not now. Say download your brain whenever you're ready."), null);
            }
            return true;
        }
        if (confirmModelRemove && (yes || no)) {
            confirmModelRemove = false;
            String line = !yes ? "Okay, I'll keep it."
                    : model.uninstall()
                            ? "Done. My brain files are removed; I'm on my offline notes again."
                            : "I couldn't delete the model file.";
            play(LocalTurn.speakAs(stage.agent(), line), null);
            return true;
        }
        confirmModelDownload = false;
        confirmModelRemove = false;
        String thing = "(your |the |a |my )?(brain|language model|llm|ai model|offline model|model)";
        boolean ivie = stage.isIvie();
        if (said.matches(
                "^(please )?(download|install|get|add|set up)( me)? " + thing + "( please)?$")
                || said.matches("^(upgrade your brain|make yourself smarter|get smarter)$")) {
            if (model.state() == ai.dvine.llm.LocalModel.State.UNSUPPORTED) {
                play(LocalTurn.speakAs(ivie,
                        "My brain can't run on this phone: " + model.problem() + "."), null);
            } else if (model.installed()) {
                play(LocalTurn.speakAs(ivie,
                        model.ready() ? "My brain is already installed and running."
                                : "It's already installed. Starting it now."),
                        null);
                if (!model.ready()) {
                    loadModel(true);
                }
            } else if (model.state() == ai.dvine.llm.LocalModel.State.DOWNLOADING) {
                play(LocalTurn.speakAs(ivie,
                        "It's downloading now. I'll tell you when it's ready."), null);
            } else {
                confirmModelDownload = true;
                play(LocalTurn.speakAs(ivie, "My brain files are a " + model.spec.sizeWords()
                        + " download, best on Wi-Fi, and then I can think offline. Shall I start?"),
                        this::startListening);
            }
            return true;
        }
        if (said.matches("^(cancel|stop|pause) (the |that |my )?download$")) {
            if (!hub.downloading()) {
                play(LocalTurn.speakAs(ivie, "There's no download running."), null);
            } else {
                hub.stopDownload();
                play(LocalTurn.speakAs(ivie,
                        "Pausing the download. What's done is kept; say continue the download to carry on."),
                        null);
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
        if (said.matches("^(show|display|open)( me)? " + dl + "$")
                || said.matches("^(show|display)( me)? (the )?progress( bar)?$")) {
            downloadBarHidden = false;
            ai.dvine.llm.DownloadStatus st = hub.status;
            stage.setDownload(true, "Downloading brain files",
                    model.installed() ? 100 : st.percent(),
                    st.phase() == ai.dvine.llm.DownloadStatus.Phase.DOWNLOADING,
                    model.installed() ? "installed"
                            : st.phase() == ai.dvine.llm.DownloadStatus.Phase.IDLE
                                    ? "not started: say download your brain"
                                    : st.detail());
            play(LocalTurn.speakAs(ivie,
                    model.installed() ? "My brain files are already downloaded." : st.spoken()),
                    null);
            return true;
        }
        if (said.matches("^(hide|close|remove)( the)? " + dl + "$")) {
            downloadBarHidden = true;
            stage.setDownload(false, "", 0, false, "");
            play(LocalTurn.speakAs(ivie,
                    "Hidden. It keeps downloading; ask me how far it is any time."), null);
            return true;
        }
        if (said.matches(
                "^(how('s| is) (the |my )?download( going)?|how far (is|along is) (the |my )?download|(what('s| is) the )?download (progress|status)|is (the |my )?download (done|finished|complete))$")) {
            play(LocalTurn.speakAs(ivie,
                    model.installed() ? "The download is finished. My brain is installed."
                            : hub.status.spoken()),
                    null);
            return true;
        }
        if (said.matches("^(delete|remove|uninstall) " + thing + "$")) {
            if (!model.installed()) {
                play(LocalTurn.speakAs(ivie, "My brain files aren't installed."), null);
            } else {
                confirmModelRemove = true;
                play(LocalTurn.speakAs(ivie, "Remove my brain files and free "
                        + model.spec.sizeWords() + "? Say yes or no."), this::startListening);
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
                play(LocalTurn
                        .speakAs(ivie,
                                model.ready() ? "My brain is on."
                                        : "It isn't installed yet. Say download your brain."),
                        null);
            }
            return true;
        }
        if (said.matches("^(is|are) " + thing
                + " (installed|ready|on|working)$|^(language model|model|brain) status$")) {
            String line;
            switch (model.state()) {
                case READY:
                    line = "My brain is running on this phone, offline.";
                    break;
                case LOADING:
                    line = "It's starting up now.";
                    break;
                case DOWNLOADING:
                    line = "It's downloading.";
                    break;
                case INSTALLED:
                    line = "It's installed but switched off. Say turn on your brain.";
                    break;
                case UNSUPPORTED:
                    line = "This phone can't run it: " + model.problem() + ".";
                    break;
                case FAILED:
                    line = "It's installed but didn't start: " + model.problem() + ".";
                    break;
                default:
                    line = "Not installed yet. Say download your brain.";
            }
            play(LocalTurn.speakAs(ivie, line), null);
            return true;
        }
        return false;
    }

    private void startModelDownload() {
        if (connectivity == null || !connectivity.isOnline()) {
            play(LocalTurn.speakAs(stage.agent(),
                    "I'm offline right now. Connect to the internet, ideally Wi-Fi, and ask me again."),
                    null);
            return;
        }
        if (hub.downloading()) {
            play(LocalTurn.speakAs(stage.agent(), hub.status.spoken()), null);
            return;
        }
        if (checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFY);
        }
        downloadBarHidden = false;
        hub.startDownload(this);
        play(LocalTurn.speakAs(stage.agent(),
                "Downloading now, in the background. You can minimise me; the notification shows how far it is."),
                null);
    }

    private BrainAnswer respondInContext(String asked,
            ai.dvine.brain.ContextExaminer.Reading reading) {
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
        if (!reply.answered && "OFFLINE".equals(reply.source) && reading.core != null
                && !reading.core.isEmpty() && !reading.core.equals(
                        asked.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", ""))) {
            BrainAnswer again = brain.respond(reading.core, agent);
            if (again.answered) {
                return again;
            }
        }
        return reply;
    }

    private static boolean plain(BrainAnswer a) {
        return a.action == null && a.command == null && !a.listenAfter && a.sketch == null
                && a.object3d == null;
    }

    private boolean create(final String text) {
        final ai.dvine.create.ObjectRequestInterpreter.Request r = creation.parse(text,
                stage.objectShown());
        if (r == null) {
            return false;
        }
        if (r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.RECON) {
            openReconstructionPicker();
            return true;
        }
        boolean slow = r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.CREATE
                || r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.OPEN
                || r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.IMAGE;
        if (!slow) {
            armThink(r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.POSE ? "Posing the body" : "Updating the model", "Updating the model");
            applyCreation(creation.perform(r));
            endThink();
            return true;
        }
        final int id = ++askId;
        stopPlay();
        String phase = r.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.IMAGE ? "Drawing the picture"
                : creation.researchAvailable() ? "Researching it" : "Designing the geometry";
        armThink(phase, phase);
        deskThread.execute(() -> {
            final ai.dvine.create.CreationDirector.Outcome o = creation.perform(r);
            handler.post(() -> {
                if (id == askId && !isFinishing()) {
                    endThink();
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
                    o.lines.add(
                            "I couldn't start the transformation just now. Try again in a moment.");
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
        if (o.picture != null) {
            stage.showGenerated(o.picture);
        }
        List<TurnLine> lines = new ArrayList<TurnLine>();
        for (String l : o.lines) {
            lines.add(new TurnLine(stage.agent(), "SAY", l));
        }
        play(lines, null);
    }

    private void discuss(final ai.dvine.turn.DualAgentCoordinator.Request r) {
        stopPlay();
        final String first = r.first != null ? r.first : stage.agent();
        armThink(first.equals("ivie") ? "Ivie is preparing, then Obi" : "Obi is preparing, then Ivie", "Writing the line");
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
                    return;
                }
                endThink();
                play(out, () -> {
                    if (discussion == c) {
                        discussion = null;
                    }
                });
            });
        });
    }

    private ai.dvine.turn.DualAgentCoordinator.Agent agentFor(final String persona,
            final boolean online) {
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
                    return;
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
                play(LocalTurn.speakAs(true,
                        "Sorry, I didn't catch your name. What should we call you?"),
                        this::startListening);
            } else {
                awaitingName = false;
                play(LocalTurn.speakAs(true,
                        "No problem. Whenever you like, just say call me, and your name."), null);
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

    private void switchFace(String agent, boolean greet) {
        prefs.edit().putString("face", agent).apply();
        stopPlay();
        if (greet) {
            play(LocalTurn.call(agent), null);
        } else {
            stage.ensureFace(agent, null);
        }
    }

    private boolean holdingMic, releasedMic;
    private final StringBuilder heldWords = new StringBuilder();
    private String lastPartial = "";
    private int holdToken;

    private void presentOutcome(ai.dvine.ops.Outcome outcome) {
        if (outcome.stopGeneration) {
            askId++;
            if (discussion != null) {
                discussion.cancel();
                discussion = null;
            }
        }
        if (outcome.activity != null && !outcome.activity.isEmpty()) {
            stage.activity("TASK", outcome.activity);
            if (outcome.speak) {
                stage.innerVoice(outcome.activity);
            }
        }
        if (outcome.picker != null && !outcome.picker.isEmpty()) {
            openContentPicker(outcome.picker, outcome.learn);
        }
        if (outcome.speech != null && !outcome.speech.isEmpty()) {
            if (outcome.speak || outcome.stopGeneration) {
                play(LocalTurn.speakAs(stage.agent(), outcome.speech),
                        outcome.listen ? this::startListening : null);
            } else {
                stage.innerVoice(outcome.speech);
            }
        }
        if (outcome.solidText != null && !outcome.solidText.isEmpty()) {
            ai.dvine.render.field.SolidSpec spec = ai.dvine.render.field.SolidSpec
                    .parse(outcome.solidText, outcome.subject);
            if (spec != null && !spec.parts.isEmpty()) {
                currentSolid = spec.fixFrame();
                currentName = null;
                solidHistory.clear();
                takenOut.clear();
                stage.showSolid(
                        currentSolid.points(ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS,
                                2f),
                        outcome.subject == null || outcome.subject.isEmpty() ? "object"
                                : outcome.subject);
            }
        }
        if (outcome.resumeSpeech) {
            step(playId);
        }
    }

    @Override
    public void onMic() {
        if (assistant != null) {
            assistant.onMicrophone(ai.dvine.ops.task.MicState.MICROPHONE_STARTING);
        }
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
        handler.postDelayed(() -> {
            if (token == holdToken && releasedMic) {
                sendHeld(lastPartial);
            }
        }, 1500);
    }

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
        if (checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, MIC);
            return;
        }
        if (talk != null) {
            talk.stop();
        }
        if (assistant != null) {
            assistant.onMicrophone(ai.dvine.ops.task.MicState.MICROPHONE_LISTENING);
            String line = assistant.activityLine();
            if (line != null) {
                stage.activity("TASK", line);
            }
        }
        stage.setListening(true);
        stage.setSubtitle("");
        stage.setMode(StageMode.LISTENING);
        listen.start(this, hold);
    }

    @Override
    public void onType() {
        if (typeBar != null) {
            typeBar.show(awaitingName ? "Your name" : "Type to DVINE");
        }
    }

    @Override
    public void onHistory(String action) {
        if ("undo".equals(action) || "redo".equals(action)) {
            armThink(action.equals("undo") ? "Undoing the last change" : "Redoing the last change", "Updating the model");
            create(action);
            endThink();
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
            listen.start(this, true);
            return;
        }
        if (releasedMic) {
            sendHeld(lastPartial);
            return;
        }
        stage.setListening(false);
        if (assistant != null && assistant.hasActiveWork()) {
            assistant.onMicrophone(ai.dvine.ops.task.MicState.MICROPHONE_OFF);
            String line = assistant.activityLine();
            if (line != null) {
                stage.activity("TASK", line);
            }
            stage.innerVoice("I didn't catch that. I'll keep working.");
            stage.setMode(StageMode.CONTENT);
            return;
        }
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
                handler.postDelayed(() -> step(token), 120);
                return;
            }
            stage.setMode(StageMode.DONE);
            Runnable after = afterPlay;
            afterPlay = null;
            if (after == null && lastSaid(lines) != null
                    && ai.dvine.turn.Followup.wantsReply(lastSaid(lines))) {
                after = this::startListening;
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
            talk.setRobot("ibiko".equals(who));
            if ("SAY".equals(line.cue)) {
                stage.setMode(StageMode.SPEAKING);
                stage.setThought(null);
                stage.setSubtitle(line.text);
                talk.speak(ivie, line.text, () -> step(token));
            } else if (line.text != null && line.text.length() > 0) {
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

    private String selfSheet() {
        ai.dvine.llm.DvineSelf.Facts f = new ai.dvine.llm.DvineSelf.Facts();
        f.version = BuildConfigInfo.version(this);
        f.device = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim();
        f.userName = brain.userName();
        f.taughtFacts = journal == null ? 0 : journal.taughtCount();
        f.speaking = stage.agent();
        return ai.dvine.llm.DvineSelf.sheet(f);
    }

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

    private void openContentPicker(String kind, boolean learn) {
        pickerLearn = learn;
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if ("image".equals(kind)) {
            intent.setType("image/*");
        } else if ("video".equals(kind)) {
            intent.setType("video/*");
        } else if ("audio".equals(kind)) {
            intent.setType("audio/*");
        } else if ("document".equals(kind)) {
            intent.setType("*/*");
            intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                    new String[] { "text/plain", "text/*", "application/pdf" });
        } else {
            intent.setType("*/*");
        }
        try {
            startActivityForResult(intent, PICK_CONTENT);
        } catch (android.content.ActivityNotFoundException e) {
            play(LocalTurn.speakAs(stage.agent(), "This phone has no file picker I can open."),
                    null);
        }
    }

    private void openReconstructionPicker() {
        pickerReconstruct = true;
        pickerLearn = false;
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_CONTENT);
            play(LocalTurn.speakAs(stage.agent(),
                    "Choose two or more overlapping photographs of one object. I'll triangulate those views."),
                    null);
        } catch (android.content.ActivityNotFoundException e) {
            pickerReconstruct = false;
            play(LocalTurn.speakAs(stage.agent(), "This phone has no file picker I can open."),
                    null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_CONTENT) {
            return;
        }
        if (pickerReconstruct) {
            pickerReconstruct = false;
            pickerLearn = false;
            if (resultCode != RESULT_OK || data == null) {
                play(LocalTurn.speakAs(stage.agent(),
                        "You closed the picker. I didn't reconstruct anything."), null);
                return;
            }
            java.util.ArrayList<android.net.Uri> uris = new java.util.ArrayList<android.net.Uri>();
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    android.net.Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) {
                        uris.add(uri);
                    }
                }
            }
            if (uris.isEmpty() && data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) {
                play(LocalTurn.speakAs(stage.agent(), "The picker didn't give me any photographs."),
                        null);
                return;
            }
            for (android.net.Uri uri : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, data.getFlags()
                            & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
            }
            armThink("Reading the photographs", "Triangulating views");
            final int token = ++askId;
            final java.util.List<android.net.Uri> chosen = uris;
            new Thread(() -> deliverReconstruction(chosen, token), "dvine-recon").start();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pickerLearn = false;
            play(LocalTurn.speakAs(stage.agent(), "You closed the picker. I didn't open a file."),
                    null);
            return;
        }
        final android.net.Uri uri = data.getData();
        final boolean learn = pickerLearn;
        pickerLearn = false;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        stage.innerVoice("Reading the file");
        stage.activity("FILE", "Reading the file");
        final int token = ++askId;
        new Thread(() -> deliverFile(uri, learn, token), "dvine-file").start();
    }

    private void deliverReconstruction(java.util.List<android.net.Uri> uris, int token) {
        java.util.ArrayList<ai.dvine.ops.recon.RasterImage> images = new java.util.ArrayList<ai.dvine.ops.recon.RasterImage>();
        for (android.net.Uri uri : uris) {
            try {
                images.add(readRaster(uri));
            } catch (Exception ignored) {
            }
        }
        final ai.dvine.ops.recon.PhotoReconstruction.Built built = ai.dvine.ops.recon.PhotoReconstruction
                .from(images);
        handler.post(() -> {
            if (token != askId) {
                return;
            }
            endThink();
            if (built.model != null && stage.objects.showReconstruction(built.model) != null) {
                stage.becomeObject(built.model);
            }
            play(LocalTurn.speakAs(stage.agent(), built.speech), null);
        });
    }

    private ai.dvine.ops.recon.RasterImage readRaster(android.net.Uri uri)
            throws java.io.IOException {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new java.io.IOException("no stream");
            }
            android.graphics.BitmapFactory.decodeStream(in, null, bounds);
        }
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / opts.inSampleSize > 960) {
            opts.inSampleSize *= 2;
        }
        android.graphics.Bitmap bitmap;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            bitmap = android.graphics.BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) {
            throw new java.io.IOException("undecoded");
        }
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        bitmap.recycle();
        return new ai.dvine.ops.recon.RasterImage(w, h, px);
    }

    private void deliverFile(android.net.Uri uri, boolean learn, int token) {
        ai.dvine.ops.vision.FileIntake.Report report;
        String name = "file";
        try {
            String mime = getContentResolver().getType(uri);
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null,
                    null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int i = cursor
                                .getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (i >= 0 && cursor.getString(i) != null) {
                            name = cursor.getString(i);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            byte[] head = new byte[ai.dvine.ops.vision.FileIntake.HEAD];
            int n = 0;
            long size = 0;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new java.io.IOException("no stream");
                }
                int r;
                while (n < head.length && (r = in.read(head, n, head.length - n)) > 0) {
                    n += r;
                    size += r;
                }
                byte[] skip = new byte[8192];
                while ((r = in.read(skip)) > 0) {
                    size += r;
                }
            }
            report = new ai.dvine.ops.vision.FileIntake().inspect(name, mime,
                    java.util.Arrays.copyOf(head, n), size, learn);
        } catch (SecurityException e) {
            report = new ai.dvine.ops.vision.FileIntake.Report("unknown",
                    "I no longer have permission to read that file.", null, false, 0, 0);
        } catch (Exception e) {
            report = new ai.dvine.ops.vision.FileIntake.Report("unknown",
                    "I couldn't read that file.", null, false, 0, 0);
        }
        final ai.dvine.ops.vision.FileIntake.Report done = report;
        final String shown = name;
        handler.post(() -> {
            if (token != askId) {
                return;
            }
            if (learn && done.note != null && journal != null) {
                journal.add(LearningJournal.TAUGHT_FACT, shown, done.note);
            }
            if (done.showImage) {
                showSelected(uri, done.kind, shown);
            }
            play(LocalTurn.speakAs(stage.agent(), done.speech), null);
        });
    }

    private void showSelected(android.net.Uri uri, String kind, String name) {
        try {
            android.graphics.Bitmap bitmap = null;
            if ("image".equals(kind)) {
                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    android.graphics.BitmapFactory.decodeStream(in, null, bounds);
                }
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = 1;
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                while (max / opts.inSampleSize > 1024) {
                    opts.inSampleSize *= 2;
                }
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    bitmap = android.graphics.BitmapFactory.decodeStream(in, null, opts);
                }
            } else if ("pdf".equals(kind)) {
                android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri,
                        "r");
                if (pfd != null) {
                    try (android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(
                            pfd)) {
                        if (renderer.getPageCount() > 0) {
                            android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0);
                            int w = Math.max(1, page.getWidth());
                            int h = Math.max(1, page.getHeight());
                            float scale = 800f / Math.max(w, h);
                            bitmap = android.graphics.Bitmap.createBitmap(
                                    Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)),
                                    android.graphics.Bitmap.Config.ARGB_8888);
                            page.render(bitmap, null, null,
                                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            page.close();
                        }
                    } finally {
                        pfd.close();
                    }
                }
            } else if ("video".equals(kind)) {
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(this, uri);
                    bitmap = retriever.getFrameAtTime(0);
                } finally {
                    retriever.release();
                }
            }
            if (bitmap != null) {
                stage.showPhotograph(ai.dvine.render.field.Raster.image(bitmap,
                        ai.dvine.render.field.ParticleDisplay.SKIN_POINTS, 1.6f), name);
                bitmap.recycle();
            }
        } catch (Exception ignored) {
            stage.innerVoice("I couldn't put that file on the board.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != MIC) {
            return;
        }
        if (results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            play(LocalTurn.speakAs(stage.agent(),
                    "I need the microphone to hear you. You can still type by pressing and holding the screen."),
                    null);
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
        hub.removeListener(onDownload);
        modelThread.shutdownNow();
        super.onDestroy();
    }
}
