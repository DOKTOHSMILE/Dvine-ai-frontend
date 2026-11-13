package ai.ibiko;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import ai.ibiko.act.Actions;
import ai.ibiko.brain.BrainAnswer;
import ai.ibiko.brain.LearningJournal;
import ai.ibiko.brain.OfflineBrain;
import ai.ibiko.brain.Onboarding;
import ai.ibiko.brain.PendingAction;
import ai.ibiko.stage.StageMode;
import ai.ibiko.stage.StageListener;
import ai.ibiko.stage.StageView;
import ai.ibiko.net.Connectivity;
import ai.ibiko.net.KnowledgeStore;
import ai.ibiko.net.SyncWorker;
import ai.ibiko.turn.LocalTurn;
import ai.ibiko.turn.TurnLine;
import ai.ibiko.turn.TurnRoute;
import ai.ibiko.voice.AmbientPad;
import ai.ibiko.voice.ListenBridge;
import ai.ibiko.voice.ListenCallback;
import ai.ibiko.voice.TalkBridge;

/**
 * No screen UI. One persona on black; touch and hold to speak, double-tap to type. Anything that leaves the app is asked out loud first and runs only after a spoken yes.
 */
public final class MainActivity extends AppCompatActivity implements StageListener, ListenCallback {
    /** The model menu under the title (ModelMenu.java). */
    final ModelMenu modelMenu = new ModelMenu(this);

    /** The tools button by the microphone (ToolsMenu.java). */
    final ToolsMenu toolsMenu = new ToolsMenu(this);

    static final int MIC = 21;

    final Handler handler = new Handler(Looper.getMainLooper());
    final FileTurns fileTurns = new FileTurns(this);
    final ShellTurn shellTurn = new ShellTurn(this);
    final ThinkTurns thinkTurns = new ThinkTurns(this);
    final WritingTurns writingTurns = new WritingTurns(this);
    final Commands commands_ = new Commands(this);
    final VoiceInput voiceInput = new VoiceInput(this);
    final LocalAsk localAsk = new LocalAsk(this);
    final Handovers handovers = new Handovers(this);
    final InternetWait internetWait = new InternetWait(this);
    final Steering steering = new Steering(this);
    final BrainGate gates = new BrainGate(this);
    final Pages pageTurns = new Pages(this);
    final Generation generationTurns = new Generation(this);
    final ComposerRow composerRow = new ComposerRow(this);
    final Drawer drawerTurns = new Drawer(this);
    final ThinkBar thinkBar = new ThinkBar(this);
    final Retries retries = new Retries(this);
    final IbikoTools ibikoTools = new IbikoTools(this);
    final Attachments attachmentTurns = new Attachments(this);
    final Pickers pickers = new Pickers(this);
    final Creation creationTurns = new Creation(this);
    final DrawingShelf drawingShelf = new DrawingShelf(this);
    final StagedActions stagedActions = new StagedActions(this);
    final SocialPosts socialPosts = new SocialPosts(this);
    final Brains brains = new Brains(this);
    final AgentWork agentWorkBook = new AgentWork(this);
    /** The on-device brain-and-body control engine (primary functions; runs offline). */
    ai.ibiko.engine.Engine engine;
    /** Behaviour rate: about 10 Hz, between the 60 Hz frame loop and the slower brain. */
    final Runnable engineTick = new Runnable() {
        @Override
        public void run() {
            engine.tick(android.os.SystemClock.uptimeMillis());
            handler.postDelayed(this, 500L); // twice a second is plenty; ten times kept the processor from resting
        }
    };
    StageView stage;
    TalkBridge talk;
    ListenBridge listen;
    AmbientPad ambient;
    OfflineBrain brain;
    final ai.ibiko.brain.ContextExaminer examiner = new ai.ibiko.brain.ContextExaminer();

    // Declare typeBar at class level to resolve variable scope errors
    ai.ibiko.stage.TypeBar typeBar;

    // the on-device language model (llama.cpp + Qwen3 1.7B): answers what the offline brain can't, offline
    ai.ibiko.llm.LocalModel model;
    boolean confirmModelDownload, confirmModelRemove, streamOpen;
    /** The message came from the microphone: the reply is spoken too, sentence by sentence as it is written. */
    boolean voiceTurn;
    /** Lines already read out while they were being written (not read again when shown). */
    final java.util.Set<TurnLine> spokenLive = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<TurnLine, Boolean>());
    /** Showing a reply: the lines, the voice, and which walk is still live. Owned here so a merge can't drop it. */
    final ai.ibiko.main.PlayStep.Host walkHost = new ai.ibiko.main.PlayStep.Host() {
        @Override
        public android.os.Handler handler() {
            return handler;
        }

        @Override
        public android.content.Context context() {
            return MainActivity.this;
        }

        @Override
        public ai.ibiko.stage.StageView stage() {
            return stage;
        }

        @Override
        public ai.ibiko.stage.ChatPane chatPane() {
            return chatPane;
        }

        @Override
        public ai.ibiko.voice.TalkBridge talk() {
            return talk;
        }

        @Override
        public ai.ibiko.voice.ListenBridge listen() {
            return listen;
        }

        @Override
        public boolean online() {
            return connectivity != null && connectivity.isOnline();
        }

        @Override
        public boolean speakReply() {
            return MainActivity.this.speakReply();
        }

        @Override
        public boolean dictating() {
            return MainActivity.this.dictating();
        }

        @Override
        public void endThink() {
            MainActivity.this.endThink();
        }

        @Override
        public void keepAwake(boolean on) {
            MainActivity.this.keepAwake(on);
        }

        @Override
        public void switchFace(String persona, boolean fromTap) {
            MainActivity.this.switchFace(persona, fromTap);
        }

        @Override
        public boolean spokenLive(TurnLine line) {
            return spokenLive.contains(line);
        }

        @Override
        public void markSpokenLive(TurnLine line) {
            spokenLive.add(line);
        }

        @Override
        public boolean streaming() {
            return streamOpen;
        }

        @Override
        public void clearStreaming() {
            setStreamOpen(false);
        }

        @Override
        public void setStreaming() {
            setStreamOpen(true);
        }

        @Override
        public int playToken() {
            return walker.token();
        }

        @Override
        public boolean samePlay(int token) {
            return walker.token() == token;
        }

        @Override
        public void cancelWalk() {
            walker.abandon();
        }
    };
    final ai.ibiko.main.PlayStep walker = new ai.ibiko.main.PlayStep(walkHost);
    ai.ibiko.llm.ModelHub hub;
    ai.ibiko.llm.DownloadStatus.Phase lastPhase = ai.ibiko.llm.DownloadStatus.Phase.IDLE;
    boolean downloadBarHidden;
    ai.ibiko.chat.ui.DownloadStrip downloadStrip;
    static final int NOTIFY = 22;

    /** The background download changed: the card on screen, the activity panel, and a word when it ends. */
    final Runnable onDownload = () -> {
        ai.ibiko.llm.DownloadStatus st = hub.status;
        ai.ibiko.llm.DownloadStatus.Phase phase = st.phase();
        boolean show = !downloadBarHidden && (st.active()
                || phase != lastPhase && phase != ai.ibiko.llm.DownloadStatus.Phase.IDLE);
        stage.setDownload(show || stage.downloadShown() && !downloadBarHidden,
                "Downloading the model", st.percent(),
                phase == ai.ibiko.llm.DownloadStatus.Phase.DOWNLOADING, st.detail());
        if (downloadStrip != null) {
            downloadStrip.model("Downloading the model", st.percent(),
                    phase == ai.ibiko.llm.DownloadStatus.Phase.DOWNLOADING, st.detail(),
                    show || stage.downloadShown() && !downloadBarHidden);
        }
        if (phase == lastPhase) {
            return;
        }
        boolean wasRunning = lastPhase == ai.ibiko.llm.DownloadStatus.Phase.DOWNLOADING
                || lastPhase == ai.ibiko.llm.DownloadStatus.Phase.CONNECTING;
        lastPhase = phase;
        switch (phase) {
            case DONE:
                stage.activity("DOWNLOAD", "verified");
                stage.setDownload(true, "Model downloaded", 100, false,
                        "verified, starting now");
                if (downloadStrip != null) {
                    downloadStrip.model("Model downloaded", 100, false, "verified, starting now", true);
                }
                handler.postDelayed(() -> {
                    stage.setDownload(false, "", 100, false, "");
                    if (downloadStrip != null) {
                        downloadStrip.model("", 100, false, "", false);
                    }
                }, 6000);
                // a short note, not a chat reply: a download finishing in the background must not land inside
                // whatever the persona is saying at that moment
                note(model.spec.weights + " is downloaded and starting. It answers offline too.");
                loadModel(false);
                break;
            case PAUSED:
                stage.activity("DOWNLOAD", "paused at " + st.percent() + "%");
                break;
            case BAD_FILE:
            case FAILED:
                if (wasRunning) {
                    // a note, not a chat reply: a reply here would cut off whatever Ibiko is doing
                    note("The model download stopped at " + st.percent() + "%. Resume it in Settings.");
                }
                break;
            default:
                break;
        }
    };
    LearningJournal journal;
    Connectivity connectivity;
    SyncWorker sync;
    /** M7: questions the phone can't answer go to the server's Obi/Ivie desk when online. */
    ai.ibiko.net.DeskClient desk;
    final java.util.concurrent.ExecutorService deskThread = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    final java.util.concurrent.ExecutorService buildThread = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    volatile int askId;
    /** Which page (persona) each job belongs to, and each page's latest job: a page's job runs on its own. */
    final java.util.Map<Integer, String> jobOf = new java.util.concurrent.ConcurrentHashMap<Integer, String>();
    final java.util.Map<String, Integer> latestOf = new java.util.concurrent.ConcurrentHashMap<String, Integer>();
    /** Jobs still writing a reply (to show "Still working" when their page is opened again). */
    final java.util.Set<Integer> writing = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());

    /** A new job for the page on screen. It replaces that page's earlier job, never another page's. */
    synchronized int nextAsk() {
        int id = ++askId;
        String page = stage == null ? "" : stage.agent();
        jobOf.put(id, page);
        latestOf.put(page, id);
        return id;
    }

    /** The job is still its page's latest (not replaced by a new message there, not stopped). */
    boolean alive(int id) {
        String page = jobOf.get(id);
        if (page == null) {
            return id == askId;
        }
        Integer latest = latestOf.get(page);
        return latest != null && latest == id;
    }

    /** The job's page is the one on screen: only then does it draw, speak or show its progress. */
    boolean visible(int id) {
        String page = jobOf.get(id);
        return page == null || stage == null || page.equals(stage.agent());
    }

    /** The latest job of this job's page (for the agent's "was I stopped or replaced?" check). */
    int latestJob(int id) {
        String page = jobOf.get(id);
        Integer latest = page == null ? Integer.valueOf(askId) : latestOf.get(page);
        return latest == null ? -1 : latest;
    }

    /** Stop: the job of the page on screen stops; another page's job keeps running. */
    void stopVisibleJob() {
        String page = stage == null ? "" : stage.agent();
        latestOf.remove(page);
        boolean others = false;
        for (java.util.Map.Entry<String, Integer> e : latestOf.entrySet()) {
            others |= writing.contains(e.getValue());
        }
        if (model != null && !others) {
            model.cancel(); // the call running is this page's (no other page has a reply being written)
        }
    }
    String activeQuestion;
    volatile int readToken;
    volatile ai.ibiko.llm.ThinkPace thinkPace;
    int thinkToken;
    String thinkWrite = "Thinking\u2026";
    boolean inFront = true;
    long thinkStartedMs;
    String lastTaskLine = "";
    final Runnable taskBeat = new Runnable() {
        public void run() {
            if (assistant != null && !isFinishing()) {
                String line = assistant.activityLine();
                if (line != null && !line.isEmpty() && !line.equals(lastTaskLine)) {
                    lastTaskLine = line;
                    stage.activity("TASK", line);
                    if (thinkPace == null) {
                        stage.progress(line);
                    }
                }
            }
            if (!isFinishing()) {
                handler.postDelayed(this, 1000);
            }
        }
    };
    /**
     * True while a 3D design runs: the model is asked several times (the specification, the brief, every part, the
     * fixes), each a long answer with no words shown, so the 3-minute "no first word" limit for a reply doesn't apply.
     */
    volatile boolean designing;

    /** The last sign of life: a step, a reasoning ping, a word, or the model being inside a call. */
    long thinkProgressMs;

    final Runnable thinkBeat = new Runnable() {
        public void run() {
            ai.ibiko.llm.ThinkPace pace = thinkPace;
            if (pace == null || thinkToken != readToken) {
                return;
            }
            pace.tick(System.currentTimeMillis());
            boolean modelWorking = model != null && model.working();
            if (modelWorking) {
                thinkProgressMs = System.currentTimeMillis(); // reading a long prompt is still work
            }
            // A quiet model is not stopped on a clock. The old 3-minute and 10-minute cutoffs
            // were ending work that was still going.
            stage.progress(pace.line());
            updateThoughtStatus(pace.line(), true);
            handler.postDelayed(this, 800);
        }
    };
    /** ANRE: the particles build researched, editable 3D objects. */
    ai.ibiko.create.CreationDirector creation;

    /** The GL scene under the stage; both go into Ivie's 3D canvas when she shows something. */
    ai.ibiko.render.PodView podView;

    /** Ivie's 3D canvas and its full-screen mode ({@link ai.ibiko.main.IvieCanvas}). */
    final ai.ibiko.main.IvieCanvas ivieCanvas = new ai.ibiko.main.IvieCanvas(new ai.ibiko.main.IvieCanvas.Host() {
        @Override
        public <T extends android.view.View> T find(int id) {
            return findViewById(id);
        }

        @Override
        public android.content.res.Resources getResources() {
            return MainActivity.this.getResources();
        }

        @Override
        public int color(int id) {
            return getColor(id);
        }

        @Override
        public android.view.Window getWindow() {
            return MainActivity.this.getWindow();
        }

        @Override
        public ai.ibiko.stage.StageView stage() {
            return stage;
        }

        @Override
        public ai.ibiko.render.PodView podView() {
            return podView;
        }
    });

    void showIvieCanvas(boolean on) {
        ivieCanvas.show(on);
    }

    void designFullScreen(boolean on) {
        ivieCanvas.fullScreen(on);
    }
    ai.ibiko.net.WebClient web;
    SharedPreferences prefs;

    List<TurnLine> paused;
    /** Dual-AI: the discussion in progress (cancelled by any new input). */
    ai.ibiko.turn.DualAgentCoordinator discussion;
    ai.ibiko.stage.ChatPane chatPane;
    ai.ibiko.chat.ChatBook chats;
    /** The last thing drawn or formed, while it is on show: context for the model. */
    String lastDrawn;

    boolean awaitingName;

    /**
     * The crash banner ({@link ai.ibiko.main.CrashReport}): every crash is saved to files/crash.txt and to
     * Download/Ibiko, and on the next start the banner offers that report once. This is the screen it serves.
     */
    final ai.ibiko.main.CrashReport.Host crashHost = new ai.ibiko.main.CrashReport.Host() {
        @Override
        public <T extends android.view.View> T find(int id) {
            return findViewById(id);
        }

        @Override
        public android.content.Context context() {
            return MainActivity.this;
        }

        @Override
        public String version() {
            return BuildConfigInfo.version(MainActivity.this);
        }

        @Override
        public SharedPreferences prefs() {
            return prefs;
        }
    };
    final ai.ibiko.main.CrashReport crash = new ai.ibiko.main.CrashReport(crashHost);

    android.widget.FrameLayout chatPaneHost;
    android.widget.FrameLayout typeBarHost;
    android.widget.TextView thoughtStatus;

    /** In {@link ThinkBar}. */
    void updateThoughtStatus(String message, boolean visible) {
        thinkBar.updateThoughtStatus(message, visible);
    }

    /** The XML chat screen owns sizing; these views fill their dedicated hosts ({@link ai.ibiko.main.ChatFit}). */
    final ai.ibiko.main.ChatFit chatFit = new ai.ibiko.main.ChatFit(new ai.ibiko.main.ChatFit.Host() {
        @Override
        public android.view.View typeBar() {
            return typeBar;
        }

        @Override
        public android.view.View chatPane() {
            return chatPane;
        }

        @Override
        public android.content.res.Resources getResources() {
            return MainActivity.this.getResources();
        }
    });

    void fitTypeBar() {
        chatFit.fitTypeBar();
    }

    void fitChatPane() {
        chatFit.fitChatPane();
    }

    // --- persona tabs: handed over to main/PersonaTabs.java ---
    /** What main/PersonaTabs.java needs from this screen: the views, the stage, the chats, and a few screen actions. */
    final ai.ibiko.main.PersonaTabs.Host personaTabsHost = new ai.ibiko.main.PersonaTabs.Host() {
        @Override
        public android.content.Context context() {
            return MainActivity.this;
        }

        @Override
        public SharedPreferences prefs() {
            return prefs;
        }

        @Override
        public boolean hasStage() {
            return stage != null;
        }

        @Override
        public void ensureFace(String persona) {
            stage.ensureFace(persona, null);
        }

        @Override
        public boolean objectShown() {
            return stage != null && stage.objectShown();
        }

        @Override
        public boolean unread(String persona) {
            return unreadPages.contains(persona);
        }

        @Override
        public void chatsForPersona(String persona) {
            if (chats != null && !groupChat()) {
                chats.forPersona(persona); // three pages: the persona's own page; the group stays in its one thread
            }
            refreshSend(); // Stop belongs to the page that is working; another page shows Send
            handler.post(releaseHeld); // a message that waited on this page goes out if nothing is running now
        }

        @Override
        public void showChatPane() {
            if (chatPane != null) {
                chatPane.show();
            }
        }

        @Override
        public <T extends android.view.View> T find(int id) {
            return findViewById(id);
        }

        @Override
        public android.content.res.Resources resources() {
            return getResources();
        }

        @Override
        public int color(int resId) {
            return getColor(resId);
        }

        @Override
        public String brainLabel() {
            return MainActivity.this.brainLabel();
        }

        @Override
        public void showIvieCanvas(boolean on) {
            MainActivity.this.showIvieCanvas(on);
        }

        @Override
        public void showIbikoMode(boolean visible) {
            MainActivity.this.showIbikoMode(visible);
        }

        @Override
        public void warmModel() {
            MainActivity.this.warmModel();
        }

        @Override
        public void play(java.util.List<ai.ibiko.turn.TurnLine> next, Runnable done) {
            MainActivity.this.play(next, done);
        }
    };
    final ai.ibiko.main.PersonaTabs personaTabs = new ai.ibiko.main.PersonaTabs(personaTabsHost);

    /** The persona pages of the open chat with a reply the pilot hasn't seen (a dot on their tab). */
    final java.util.Set<String> unreadPages = new java.util.HashSet<String>();

    /** In {@link Pages}. */
    void selectPersona(String persona) {
        pageTurns.selectPersona(persona);
    }

    /** In {@link Pages}. */
    void selectPersona(String persona, boolean fromTap) {
        pageTurns.selectPersona(persona, fromTap);
    }

    void offerTermux() {
        personaTabs.offerTermux();
    }

    /** In {@link Pages}. */
    void showPersonaBar() {
        pageTurns.showPersonaBar();
    }

    void bindChatHeader() {
        new ChatChrome(this).bindHeader();
    }

    void bindComposer() {
        new ChatChrome(this).bindComposer();
    }

    /** In {@link Pages}. */
    void wireStarter(int id, String text) {
        pageTurns.wireStarter(id, text);
    }

    // --- send: a message during work is steered into that work, not queued and not used to stop it ---
    /** The send button: a double tap is ignored, and a message sent while work runs joins that work. */
    ai.ibiko.main.SendQueue sendQueue;
    /** What the user sent while a task was still running. The task reads it; it does not get cancelled to make room. */
    final ai.ibiko.main.SteerInbox steer = new ai.ibiko.main.SteerInbox();

    /** A reply, an agent run, or a model call is still going on this chat. Speech, and another chat's run, are not. */
    boolean busy() {
        return thisChatBusy();
    }

    boolean modelGoing() {
        return generating || streamOpen || (model != null && (model.working() || model.coding));
    }

    /** The chat the current run belongs to. Another chat stays idle. */
    String runChat;
    /** Messages sent in a chat while a different chat is the one running. They go out when that run ends. */
    final java.util.ArrayDeque<String[]> heldChat = new java.util.ArrayDeque<String[]>();
    boolean replacing;

    static String labelOf(String persona) {
        return "Ibiko"; // one assistant, one name
    }

    /** The persona whose page the current run belongs to. */
    String runAgent;

    /**
     * The chat is a group: one thread for Obi, Ivie and Ibiko, like a group of people. Everyone's messages are in the
     * one conversation and the words choose who answers. It is the only kind of chat (the three separate pages are gone).
     */
    boolean groupChat() {
        return true;
    }

    /** In {@link Pages}. */
    String pageKey() {
        return pageTurns.pageKey();
    }

    /** In {@link Pages}. */
    boolean thisChatBusy() {
        return pageTurns.thisChatBusy();
    }

    /** In {@link Pages}. */
    boolean runningElsewhere() {
        return pageTurns.runningElsewhere();
    }

    void sendComposer() {
        sendQueue.sendComposer();
    }

    /** What main/SendQueue.java needs from this screen: views, the two ways a message goes out, and the stop. */
    final ai.ibiko.main.SendQueue.Host sendHost = new ai.ibiko.main.SendQueue.Host() {
        @Override
        public <T extends android.view.View> T find(int id) {
            return findViewById(id);
        }

        @Override
        public android.os.Handler handler() {
            return handler;
        }

        @Override
        public android.widget.TextView findNote() {
            return findViewById(R.id.composer_note);
        }

        @Override
        public boolean busy() {
            return MainActivity.this.busy();
        }

        @Override
        public boolean hasAttachments() {
            return attachments != null && !attachments.isEmpty();
        }

        @Override
        public boolean takeVoiceDraft() {
            return dictation != null && dictation.takeDraft();
        }

        @Override
        public void markVoiceTurn() {
            voiceTurn = true;
        }

        @Override
        public void answer(String text) {
            MainActivity.this.answer(text);
        }

        @Override
        public void sendAttached(String text) {
            sendWithAttachments(text);
        }

        @Override
        public void haltGeneration() {
            stopGeneration();
        }

        @Override
        public void steer(String text, boolean attached) {
            steerIntoTask(text, attached);
        }

        @Override
        public void cancelNoteHide() {
            handler.removeCallbacks(hideNote);
        }
    };

    /** In {@link Attachments}. */
    void recordIbikoFiles(java.io.File workspace, java.util.List<String> changed) {
        attachmentTurns.recordIbikoFiles(workspace, changed);
    }

    /** In {@link Attachments}. */
    void attachWork(final java.util.List<String> changed, final long since) {
        attachmentTurns.attachWork(changed, since);
    }

    /** Images, files and long pastes waiting above the words; sent with the message. */
    ai.ibiko.chat.ui.ComposerAttachments attachments;

    /** In {@link Attachments}. */
    void sendWithAttachments(String words) {
        attachmentTurns.sendWithAttachments(words);
    }

    /** In {@link Attachments}. */
    void showSentAttachments(String words) {
        attachmentTurns.showSentAttachments(words);
    }

    /** In {@link Attachments}. */
    void takeShared() {
        attachmentTurns.takeShared();
    }

    /** In {@link Attachments}. */
    void attachPicked(final android.net.Uri uri) {
        attachmentTurns.attachPicked(uri);
    }

    /** The mic writing into the message box. */
    ai.ibiko.voice.Dictation dictation;

    boolean dictating() {
        return dictation != null && dictation.active();
    }

    /** In {@link ComposerRow}. */
    void toggleDictation() {
        composerRow.toggleDictation();
    }


    /** In {@link ComposerRow}. */
    void startNewChatSafely() {
        composerRow.startNewChatSafely();
    }

    /** The drawer, dragged open and shut like any Android drawer (main/DrawerController). */
    final ai.ibiko.main.DrawerController drawer = new ai.ibiko.main.DrawerController(new ai.ibiko.main.DrawerController.Host() {
        @Override
        public android.view.View drawer() {
            return findViewById(R.id.historyDrawer);
        }

        @Override
        public android.view.View scrim() {
            return findViewById(R.id.drawerScrim);
        }

        @Override
        public boolean blocked() {
            return ivieCanvas.isFull();
        }

        @Override
        public void beforeOpen() {
            hideKeyboard();
            if (chatPane != null) {
                chatPane.show();
            }
        }

        @Override
        public void cancel(android.view.MotionEvent e) {
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
            MainActivity.super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }

        @Override
        public android.view.View root() {
            return getWindow().getDecorView();
        }

        @Override
        public float density() {
            return getResources().getDisplayMetrics().density;
        }

        @Override
        public int touchSlop() {
            return android.view.ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
        }
    });

    /** In {@link Drawer}. */
    void toggleDrawer() {
        drawerTurns.toggleDrawer();
    }

    /** In {@link Drawer}. */
    void openDrawer() {
        drawerTurns.openDrawer();
    }

    /** In {@link Drawer}. */
    void closeDrawer() {
        drawerTurns.closeDrawer();
    }

    /** In {@link Drawer}. */
    boolean drawerOpen() {
        return drawerTurns.drawerOpen();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        return drawer.onTouch(e) || super.dispatchTouchEvent(e);
    }

    /** In {@link Drawer}. */
    void hideKeyboard() {
        drawerTurns.hideKeyboard();
    }

    /** In {@link ThinkBar}. */
    void bindThink() {
        thinkBar.bindThink();
    }

    /** Repaints the Think pill (after the Tools sheet or a new chat changed deep thinking). */
    Runnable thinkPaint;

    /** In {@link ThinkBar}. */
    void repaintThink() {
        thinkBar.repaintThink();
    }

    /** In {@link ComposerRow}. */
    void bindQuickRow() {
        composerRow.bindQuickRow();
    }

    /** In {@link Attachments}. */
    void showAttachMenu(android.view.View anchor) {
        attachmentTurns.showAttachMenu(anchor);
    }

    /**
     * Turned, folded or resized: nothing restarts (the conversation, a drawing and the brain carry on); the stage lays itself out again for the new size and the text field fits the new width.
     */
    @Override
    public void onBackPressed() {
        if (ivieCanvas.isFull()) {
            designFullScreen(false); // Back leaves full screen first
            return;
        }
        android.view.View drawer = findViewById(R.id.historyDrawer);
        if (drawer != null && drawer.getVisibility() == android.view.View.VISIBLE) {
            closeDrawer();
            return;
        }
        // Back never quits Ibiko: it goes to the background like Home, and everything carries on. Closing is the
        // notification's "Close Ibiko", or swiping it away from the recent apps.
        moveTaskToBack(true);
    }

    /** The pilot closed Ibiko from the notification: the screen goes too. */
    final android.content.BroadcastReceiver closedByPilot = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            finishAndRemoveTask();
        }
    };

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        fitTypeBar();
        fitChatPane();
        if (chatPaneHost != null)
            chatPaneHost.requestLayout();
        if (typeBarHost != null)
            typeBarHost.requestLayout();
        if (stage != null)
            stage.invalidate();
    }

    /** Day, night, or the phone's setting (the default). */
    public static void applyTheme(String choice) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode("light".equals(choice)
                ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                : "dark".equals(choice) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /** Whether this reply is read out: always, after a dictated message, or never, as chosen in the drawer. */
    /**
     * Whether a line is read out by itself. Only a reply to something the pilot just asked, while the chat is on
     * screen: a morning report, a job that ends in the background, a note or a message that waited are shown, not
     * spoken (the play button under any reply still reads it). Then the read-aloud setting decides.
     */
    boolean speakReply() {
        return inFront && System.currentTimeMillis() - askedAt < SPEAK_WINDOW_MS && readAloudHelper.speakReply();
    }

    /** When the pilot last sent a message, and how long a reply to it may still be read out by itself. */
    long askedAt;
    static final long SPEAK_WINDOW_MS = 3 * 60_000L;

    /**
     * Makes a picture with the image tool (\u00A7IM, from any brain): an image model on one of the pilot's providers
     * draws it, it is saved and shown in the chat. While it is made, the working line says so.
     */
    /**
     * This chat's own folder (files/ibiko/chats/<chat>): its pulled repositories (repos/), its memories (memory/) and
     * pictures (images/). Every file tool, agent and Linux command of this chat works in it and sees nothing else.
     */
    public java.io.File chatSpace() {
        ai.ibiko.chat.ChatBook.Section s = chats == null ? null : chats.current();
        return ai.ibiko.main.ChatSpace.of(getFilesDir(), s == null ? "chat" : s.group != null ? s.group : s.id);
    }

    /**
     * The reason a reply failed, only when it tells the person something they can act on (credit, a key, a limit), in a
     * few words; never a raw host name, status code or stack of provider text.
     */
    static String detail(String why) {
        String w = why == null ? "" : why.toLowerCase(java.util.Locale.US);
        if (w.matches(".*(credit|quota|billing|insufficient|402).*")) {
            return " (The connected account is out of credit.)";
        }
        if (w.matches(".*(401|403|api key|unauthori[sz]ed|invalid key|forbidden).*")) {
            return " (The connected account's key was refused: check it in Settings \u203A Models.)";
        }
        if (w.matches(".*(rate limit|429|too many requests).*")) {
            return " (Too many requests at once: give it a minute.)";
        }
        return "";
    }

    /** The API couldn't be reached at all (its address, the network path), not a refusal or an empty answer. */
    public static boolean unreachable(String why) {
        return why != null && why.matches("(?is).*(couldn't reach|could not reach|unable to resolve host|no address associated"
                + "|failed to connect|connection refused|timed out|timeout|unreachable).*");
    }

    /** Makes a picture in the background and pastes it into the chat when ready (ImageTurn). */
    void makeImage(final String agent, final String what) {
        new ImageTurn(this).make(agent, what);
    }


    /** This start only handed over to the first-start download (AppFilesActivity). */
    boolean waitingForFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme(getSharedPreferences("ibiko", MODE_PRIVATE).getString("theme", "system"));
        super.onCreate(savedInstanceState);
        if (!"robolectric".equals(android.os.Build.FINGERPRINT) && !ai.ibiko.main.AppFiles.ready(getFilesDir())) {
            // the first start: the app files are downloaded before anything opens, and without them it doesn't open
            android.content.Intent then = new android.content.Intent(getIntent()).setClass(this, MainActivity.class);
            startActivity(new android.content.Intent(this, ai.ibiko.chat.ui.AppFilesActivity.class).putExtra("then", then));
            waitingForFiles = true;
            finish();
            return;
        }
        crash.install();
        // the splash (Ibiko's face, glancing and blinking) stays while the start-up work finishes, at most a moment
        final android.view.View splashHold = findViewById(android.R.id.content);
        splashHold.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!ai.ibiko.main.Warmup.done()) {
                    return false;
                }
                splashHold.getViewTreeObserver().removeOnPreDrawListener(this);
                return true;
            }
        });
        // kept running in the background until the pilot closes it (main/KeepAlive); opening Ibiko clears a Close
        ai.ibiko.main.KeepAlive.opened(this);
        registerReceiver(closedByPilot, new android.content.IntentFilter(ai.ibiko.main.KeepAlive.CLOSED), RECEIVER_NOT_EXPORTED);
        handler.postDelayed(() -> {
            if (!isFinishing()) {
                ai.ibiko.main.KeepAlive.askBatteryOnce(this); // once: so the system doesn't freeze Ibiko in the background
            }
        }, 4000);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(getColor(R.color.bg_main)); // and the theme sets light or dark bar icons
        window.setNavigationBarColor(getColor(R.color.bg_main));

        // The chat is laid out inside the status and navigation bars. It does not draw under them.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);

        ai.ibiko.social.SocialWeb.onRead = text -> new Thread(() -> ibiko1().readSocial(text), "ibiko-read").start();
        stage = new StageView(this);
        stage.setListener(this);
        podView = new ai.ibiko.render.PodView(this);
        stage.attach(podView);

        // Inflate the full-screen chat layout. The hologram StageView remains
        // alive as the rendering/agent surface, while this screen is dedicated
        // to the conversation UI.
        setContentView(R.layout.activity_main);
        downloadStrip = new ai.ibiko.chat.ui.DownloadStrip(this);
        downloadStrip.bind();
        ai.ibiko.net.Transfers.listen(downloadStrip);
        sendQueue = new ai.ibiko.main.SendQueue(sendHost);
        android.widget.LinearLayout root = findViewById(R.id.chatScreenRoot);
        chatPaneHost = findViewById(R.id.chatPaneHost);
        thoughtStatus = findViewById(R.id.thoughtStatus);
        updateThoughtStatus("Thinking…", false);

        chats = ai.ibiko.chat.ChatBook.open(new File(getFilesDir(), "chats.json"));
        chats.coalesce(!"robolectric".equals(android.os.Build.FINGERPRINT)); // gathered saves on a device; tests read at once
        chatPane = new ai.ibiko.stage.ChatPane(this, chats, new ai.ibiko.stage.ChatPane.Host() {
            @Override
            public void onSend(String text) {
                voiceTurn = false;
                answer(text);
            }

            @Override
            public void onSection(ai.ibiko.chat.ChatBook.Section section) {
                if (section == null || section.persona == null)
                    return;
                unreadPages.clear(); // another chat: its own pages
                selectPersona(section.persona);
                repaintThink(); // the pill shows Think as it is, whichever chat opened
                examiner.clearTopic();
                handler.postDelayed(MainActivity.this::sendWaiting, 300); // a message that waited here for the internet
                refreshSend();
                handler.post(releaseHeld);
            }

            @Override
            public void onReadAloud(String text) {
                if (talk != null && text != null && !text.isEmpty() && stage != null) {
                    talk.speak("ivie".equals(stage.agent()), text, null);
                }
            }

            @Override
            public boolean audioReady(String owner, String text) {
                // every reply's voice is made ahead and kept: Listen plays the kept recording
                if (talk == null) {
                    return true;
                }
                talk.setOwner(owner);
                return talk.prepared(text);
            }

            @Override
            public void prepareAudio(String owner, String text, Runnable ready) {
                if (talk == null) {
                    ready.run();
                    return;
                }
                talk.setOwner(owner);
                talk.prepareAll(text, ready); // made now, kept under this message for a week: Listen lights up when done
            }

            @Override
            public void aheadAudio(String owner, String sentence) {
                if (talk == null || owner == null || sentence == null || sentence.trim().isEmpty()) {
                    return;
                }
                talk.setOwner(owner);
                talk.prepare(true, sentence); // made while the reply is still being written, not spoken yet
            }

            @Override
            public void speakingFor(String owner) {
                if (talk != null) {
                    talk.setOwner(owner); // Listen plays this message's own kept voice, in order
                }
            }

            @Override
            public boolean onReadAloudToggle(String text) {
                return toggleReading(text);
            }

            @Override
            public void onWorkSteps(final java.util.List<String> steps) {
                final String asked = lastAsked;
                if (asked == null || steps == null || steps.isEmpty()) {
                    return;
                }
                // learned as we go, from every brain's work: how the answer was reached, not only what it was
                new Thread(() -> ibiko1().learnWay(ai.ibiko.chat.Attachments.withoutImages(asked)
                        .replaceAll("(?s)<attachment.*?</attachment>", "").trim(), steps), "ibiko-method").start();
            }

            @Override
            public boolean onReadAloudToggle(String text, String persona) {
                return readAloudHelper.toggleReading(text, persona);
            }

            @Override
            public void onHandoff(String persona, String task) {
                handOff(persona, task);
            }

            @Override
            public void onRetry() {
                retryLast(); // the Retry button under a reply that stopped
            }

            @Override
            public void onRate(String question, String answer, boolean right) {
                ibiko1().endpoint().feedback(question, answer, right);
            }

            @Override
            public void onEdit(String before, String edited) {
                // the old message and its replies are gone from the chat; the model forgets them too, then the
                // edited message is sent like a new one (a reply still being written to the old one is stopped)
                if (model != null && hub != null) {
                    // on the model's own worker: it waits for a reply being written to end (sending stops it), and
                    // runs before the edited message is answered, which is queued after it
                    final ai.ibiko.llm.LocalModel m = model;
                    hub.worker.execute(() -> m.rewindTo(before));
                }
                lastAsked = null;
                voiceTurn = false;
                answer(edited);
            }
        });

        if (chatPaneHost != null) {
            chatPaneHost.addView(chatPane,
                    new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // ChatPane contains the regular message composer. TypeBar is an
        // optional alternate input surface and stays collapsed unless onType()
        // explicitly requests it. This prevents two visible composers.
        typeBar = new ai.ibiko.stage.TypeBar(this, text -> {
            if (typeBarHost != null)
                typeBarHost.setVisibility(android.view.View.GONE);
            answer(text);
        });
        if (typeBarHost != null) {
            typeBarHost.addView(typeBar,
                    new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            typeBarHost.setVisibility(android.view.View.GONE);
        }
        fitTypeBar();
        fitChatPane();

        android.view.View insetHost = findViewById(R.id.screen);
        if (insetHost == null) {
            insetHost = root;
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(insetHost, (view, insets) -> {
            androidx.core.graphics.Insets barsInsets = insets
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                            | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(barsInsets.left, barsInsets.top, barsInsets.right, barsInsets.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat
                .getInsetsController(window, root);
        if (bars != null) {
            bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            // dark icons and text on the light theme's bars, light ones on the dark theme's
            boolean light = getResources().getBoolean(R.bool.light_bars);
            bars.setAppearanceLightStatusBars(light);
            bars.setAppearanceLightNavigationBars(light);
        }

        prefs = getSharedPreferences("ibiko", MODE_PRIVATE);
        if (!prefs.getBoolean("chat_spaces_adopted", false)) {
            // once: files from before each chat had its own folder go to the chat open now (repositories into repos/)
            final java.io.File here = chatSpace();
            new Thread(() -> {
                ai.ibiko.main.ChatSpace.adoptOld(getFilesDir(), here.getName());
                prefs.edit().putBoolean("chat_spaces_adopted", true).apply();
            }, "ibiko-chat-spaces").start();
        }
        bindChatHeader();
        showPersonaBar();
        ai.ibiko.turn.Names.restore(prefs.getString("name_aliases", null));
        talk = new TalkBridge(this);
        listen = new ListenBridge(this);
        ambient = new AmbientPad();
        ambient.setEnabled(false); // the low pad under the voice: off unless asked for
        stage.setSubtitles(prefs.getBoolean("subtitles", true));
        stage.ensureFace(StageView.OBI, null); // Obi greets on opening
        new VoiceWiring(this).wire(); // the voice's tuning, listeners, notification and the floating player
        android.content.IntentFilter readControls = new android.content.IntentFilter();
        readControls.addAction(ai.ibiko.voice.ReadAloudNotice.PAUSE);
        readControls.addAction(ai.ibiko.voice.ReadAloudNotice.RESUME);
        readControls.addAction(ai.ibiko.voice.ReadAloudNotice.STOP);
        registerReceiver(readControl, readControls, android.content.Context.RECEIVER_NOT_EXPORTED);

        KnowledgeStore store = new KnowledgeStore(this);
        journal = LearningJournal.open(new File(getFilesDir(), "learning"));
        learned = ai.ibiko.agent.LearnedIntents.open(new File(getFilesDir(), "learned_intents.txt"));
        library = new ai.ibiko.library.DrawingLibrary(new File(getFilesDir(), "drawings"));
        assistant = new ai.ibiko.ops.AssistantCore(new File(getFilesDir(), "ops"),
                new ai.ibiko.ops.termux.TermuxCapabilityDetector.Probe() {
                    // the task system's device commands run in Ibiko's one Alpine Linux, not in another app
                    @Override
                    public boolean installed() {
                        return ai.ibiko.linux.AlpineRun.ready(MainActivity.this);
                    }

                    @Override
                    public boolean permissionGranted() {
                        return true;
                    }

                    @Override
                    public boolean resultBridgeConnected() {
                        return true;
                    }
                }, (argv, timeoutMs) -> {
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        // never wait for Linux on the screen's thread: the task uses its own quick reading instead
                        return new ai.ibiko.ops.termux.TermuxExecutionService.Result(127, "", "on the main thread", false, false);
                    }
                    StringBuilder line = new StringBuilder();
                    for (String a : argv) {
                        line.append(line.length() == 0 ? "" : " ").append(ai.ibiko.linux.Guest.quote(a));
                    }
                    String text = ai.ibiko.linux.LinuxHub.run(MainActivity.this, line.toString(), chatSpace()); // this chat's files only
                    java.util.regex.Matcher exit = java.util.regex.Pattern.compile("\\[exit (\\d+)\\]\\s*$").matcher(text.trim());
                    int code = exit.find() ? Integer.parseInt(exit.group(1)) : 0;
                    return new ai.ibiko.ops.termux.TermuxExecutionService.Result(code, text, "", false, false);
                }, null, null);
        String restored = assistant.restoredSummary();
        if (restored != null) {
            stage.activity("TASK", restored);
        }
        handler.postDelayed(taskBeat, 1000);
        brain = new OfflineBrain(store.loadCore(), journal, System::currentTimeMillis,
                TimeZone.getDefault());
        brain.setThoughts(prefs.getBoolean("thoughts", true));
        Thread loader = new Thread(() -> {
            try {
                final ai.ibiko.brain.KnowledgePack full = store.load();
                handler.post(() -> brain.replacePack(full));
            } catch (RuntimeException broken) {
            }
        }, "ibiko-knowledge");
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
        String storedName = prefs.getString("user_name", null);
        if (Onboarding.isPersona(storedName)) {
            prefs.edit().remove("user_name").remove("given_name").apply();
            storedName = null;
        }
        brain.setUserName(storedName);
        brain.setGivenName(prefs.getString("given_name", null));
        hub = ai.ibiko.llm.ModelHub.get(this);
        modelGeneration = hub.generation;
        model = hub.model;
        model.onStep = this::activity;
        model.learning = ai.ibiko.chat.ui.UsageActivity.learningOn(prefs) ? new ai.ibiko.learn.LearningLog(new java.io.File(getFilesDir(), "learning")) : null;
        model.usage.restore(prefs.getString("usage_today", null), System.currentTimeMillis());
        hub.addListener(onDownload);
        reportModel();
        if (hub.downloading() || hub.status.active()) {
            onDownload.run();
        }
        if (model.installed() && prefs.getBoolean("llm_on", true)) {
            loadModel(false);
        }
        final ai.ibiko.net.SessionClient session = new ai.ibiko.net.SessionClient(this::serverUrl,
                prefs, new ai.ibiko.net.DeviceVault(prefs));
        ai.ibiko.llm.ProviderStore.serverSession = session::token; // the Ibiko server's own model signs in with it
        sync = new SyncWorker(this::serverUrl, session::deviceId, journal, store, session::token,
                pack -> {
                    final ai.ibiko.brain.KnowledgePack full = store.load();
                    handler.post(() -> brain.replacePack(full));
                });
        desk = new ai.ibiko.net.DeskClient(session::serverRoot, session::token, session::invalidate,
                new ai.ibiko.net.DeskClient.RoomStore() {
                    @Override
                    public String get() {
                        return prefs.getString("desk_room", null);
                    }

                    @Override
                    public void put(String roomId) {
                        prefs.edit().putString("desk_room", roomId).apply();
                    }
                }, null);
        web = new ai.ibiko.net.WebClient(session::serverRoot, session::token, session::invalidate,
                null);
        creation = new ai.ibiko.create.CreationDirector(stage.objects,
                new File(getFilesDir(), "designs"));
        engine = new ai.ibiko.engine.Engine(stage, getFilesDir(), journal::consent);
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
                handler.postDelayed(this::sendWaiting, 800); // back online: what waited goes now
            }
            if (online) {
                sync.kick(brain.pack().version);
            }
            creation.setFetcher(
                    online && serverUrl() != null && serverUrl().trim().startsWith("https://")
                            ? web::fetch
                            : null);
        });
        applyProvider();
        ai.ibiko.ibiko1.LearningJob.schedule(this); // night learning, if the pilot turned it on

        stage.wake();
        handler.postDelayed(this::greetOnOpen, 700);
    }

    void greetOnOpen() {
        if (chats != null) {
            chats.dropGreetings();
        }
        if (chatPane != null) {
            chatPane.show();
        }
        // no name question at start: the chat opens ready; "call me …" sets a name whenever the user likes
    }

    String serverUrl() {
        String set = prefs.getString("server", null);
        return set != null && !set.isEmpty() ? set : getString(R.string.api_base);
    }

    void syncSoon() {
        if (connectivity != null && connectivity.isOnline()) {
            sync.kick(brain.pack().version);
        }
    }

    ai.ibiko.agent.LearnedIntents learned;
    boolean fromBrain;
    ai.ibiko.ops.AssistantCore assistant;
    /** One utterance, opened only by a long-press on the microphone. Chat text never opens this. */
    ai.ibiko.ops.task.IbikoCommandGateway.VoiceSession commandSession;
    static final int PICK_CONTENT = 47;
    static final int HISTORY = 48;
    /** The Social accounts screen; it may come back with words for the message box ("Write a Facebook post about "). */
    static final int SOCIAL = 49;
    boolean generating;
    boolean pickerLearn;
    boolean pickerReconstruct;

    /** In {@link BrainGate}. */
    void answer(String text) {
        gates.answer(text);
    }

    /** The voice of {@code body}, made and kept under the reply it belongs to (the id that reply will be saved with). */
    void keepVoice(String body) {
        if (talk == null || chatPane == null || body == null || body.trim().isEmpty()) {
            return;
        }
        String owner = chatPane.beginVoice();
        if (owner != null) {
            talk.setOwner(owner);
        }
        talk.prepareAll(body);
    }

    /** The id reserved for the reply being written, once it is saved. Null when this reply already took it. */
    String stampVoice() {
        return chatPane == null ? null : chatPane.claimVoiceId();
    }

    /** In {@link ThinkBar}. */
    void armThink(String phase, String writing) {
        thinkBar.armThink(phase, writing);
    }

    /** In {@link Commands}. */
    void applySettings() {
        commands_.applySettings();
    }

    /** One step of what Ibiko is doing, shown live in the chat ("Searching the web for …"); any thread. */
    /** The reply being written on each job, as a worker on the board. */
    final java.util.Map<Integer, String> replyWorkers = new java.util.concurrent.ConcurrentHashMap<Integer, String>();

    static String cap(String persona) {
        return "Ibiko"; // one assistant, one name
        // a.substring(1);
    }

    /** In {@link ThinkBar}. */
    void activity(final String line) {
        thinkBar.activity(line);
    }

    /** In {@link ThinkBar}. */
    void endThink() {
        thinkBar.endThink();
    }

    final Runnable hideNote = () -> {
        android.view.View n = findViewById(R.id.composer_note);
        if (n != null) {
            n.setVisibility(android.view.View.GONE);
        }
    };

    /** A short line above the message box for a few seconds ("…, so OpenAI did."). */
    void note(String text) {
        android.widget.TextView n = findViewById(R.id.composer_note);
        if (n == null) {
            return;
        }
        n.setText(text);
        n.setVisibility(android.view.View.VISIBLE);
        handler.removeCallbacks(hideNote);
        handler.postDelayed(hideNote, 7000);
    }

    /** Whether this screen's reply holds the foreground service now (held once, let go once). */
    boolean replyHeld;

    void keepAwake(boolean on) {
        if (on == replyHeld) {
            return;
        }
        replyHeld = on;
        if (on) {
            ai.ibiko.llm.ReplyService.hold(this);
        } else {
            ai.ibiko.llm.ReplyService.release(this);
        }
    }

    /** In {@link Generation}. */
    void interruptReply() {
        generationTurns.interruptReply();
    }

    // --- read-aloud: handed over to main/ReadAloud.java ---
    /** What main/ReadAloud.java needs from this screen: the voice, the persona, the setting, and when reading stops. */
    final ai.ibiko.main.ReadAloud.Host readAloudHost = new ai.ibiko.main.ReadAloud.Host() {
        @Override
        public ai.ibiko.voice.TalkBridge talk() {
            return talk;
        }

        @Override
        public String agent() {
            return stage == null ? null : stage.agent();
        }

        @Override
        public SharedPreferences prefs() {
            return prefs;
        }

        @Override
        public boolean voiceTurn() {
            return voiceTurn;
        }

        @Override
        public void readingStopped() {
            if (chatPane != null) {
                chatPane.readingStopped();
            }
        }
    };
    final ai.ibiko.main.ReadAloud readAloudHelper = new ai.ibiko.main.ReadAloud(readAloudHost);

    /** In {@link Generation}. */
    boolean toggleReading(String text) {
        return generationTurns.toggleReading(text);
    }

    /** In {@link Generation}. */
    void stopGeneration() {
        generationTurns.stopGeneration();
    }

    /** In {@link Generation}. */
    void setGenerating(boolean on) {
        generationTurns.setGenerating(on);
    }

    /** In {@link Generation}. */
    void setStreamOpen(boolean open) {
        generationTurns.setStreamOpen(open);
    }

    /** While Stop shows, the button looks again each second, so it turns back into Send the moment nothing runs. */
    final Runnable sendRecheck = this::refreshSend;

    /** In {@link Generation}. */
    void refreshSend() {
        generationTurns.refreshSend();
    }

    void openHistory() {
        closeDrawer();
        startActivityForResult(
                new android.content.Intent(this, ai.ibiko.chat.ui.HistoryActivity.class), HISTORY);
    }

    /** The last question asked, whole (with any file it carried), for "retry". */
    String lastAsked;

    /** In {@link BrainGate}. */
    void askTheBrain(String question) {
        gates.askTheBrain(question);
    }

    /** The message as the user said it, before "continue" was read as the question it continues. */
    String brainAsked;
    ai.ibiko.gate.BrainGateway messageGate, brainGate;

    static String control(String text) {
        return text.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
    }

    /** In {@link BrainGate}. */
    ai.ibiko.gate.BrainGateway brainGate() {
        return gates.brainGate();
    }

    /** The tools this chat page lets its brain use (per conversation and persona), from the tools button. */
    ai.ibiko.suit.ChatTools chatTools(String persona) {
        ai.ibiko.suit.ChatTools t = ai.ibiko.suit.ChatTools.read(persona, prefs.getString(toolsKey(persona), null));
        // Think is one switch, kept as it was last set, for every chat (new ones too): not a per-chat setting that a new
        // chat, another chat or a restart quietly turned off
        t.set(ai.ibiko.suit.ChatTools.Tool.DEEP, prefs.getBoolean(THINK_ON, false));
        return t;
    }

    /** Think (the pill, and Tools › Deep thinking): on or off for every chat until it is changed. */
    static final String THINK_ON = "think_on";

    /** In {@link BrainGate}. */
    String toolsKey(String persona) {
        return gates.toolsKey(persona);
    }

    /** In {@link BrainGate}. */
    String systemFor(String agent, boolean acts) {
        return gates.systemFor(agent, acts);
    }

    /** What goes with a question to the device's own model: the most useful part only (it is read before the first word). */
    static final int DEVICE_CONTEXT_CHARS = 1500;

    /** The same for an everyday message (a greeting, a short question): about a hundred tokens. */
    static final int QUICK_CONTEXT_CHARS = 400;

    /**
     * How the brain engages on the pilot's behalf: replies and comments as the pilot, in the open, each one approved.
     * Never fake accounts, hidden paid promotion, mass identical replies, or pretending to be an independent fan.
     */
    static final String ENGAGE = " Engagement for the pilot: read the comments on their posts or a trend (§SI or §SO), then"
            + " draft replies in the pilot's own voice that add something (an answer, thanks, a useful link) and offer each with"
            + " §AC post on <network>: <reply>, one at a time; the pilot approves every one. Schedule posts with §SP, once or"
            + " repeating. Never write as a different person, never hide that the pilot benefits from what they promote, never"
            + " send the same reply to many people, and never pose as an independent fan or customer.";

    /** In {@link SocialPosts}. */
    void schedulePost(String arg) {
        socialPosts.schedulePost(arg);
    }

    /** In {@link SocialPosts}. */
    void showScheduledPosts() {
        socialPosts.showScheduledPosts();
    }

    ai.ibiko.social.intel.SocialExecutionEngine socialIntel;

    /** In {@link SocialPosts}. */
    ai.ibiko.social.intel.SocialExecutionEngine socialIntel() {
        return socialPosts.socialIntel();
    }

    /** In {@link SocialPosts}. */
    void useSocialIntel(final String question, final String call) {
        socialPosts.useSocialIntel(question, call);
    }





    ai.ibiko.learn.BrainBox brainBox;
    ai.ibiko.suit.Cortex cortex;

    /** Ibiko's own light brain in the suit: reads every message and works out arithmetic exactly, for any model. */
    ai.ibiko.learn.PilotProfile pilotProfile;

    /** What the pilot has said about themselves, kept on the device and told to every brain. */
    synchronized ai.ibiko.learn.PilotProfile pilotProfile() {
        if (pilotProfile == null) {
            pilotProfile = ai.ibiko.learn.PilotProfile.at(new java.io.File(getFilesDir(), "brain/pilot.tsv"));
        }
        return pilotProfile;
    }

    /** After a learning session: what Ibiko 1 learned, said once in the chat. */
    void morningReport() {
        String said = stage == null ? null : ibiko1().report();
        if (said != null) {
            play(LocalTurn.speakAs(stage.agent(), said), null);
        }
    }


    ai.ibiko.ibiko1.Ibiko1 ibiko1;

    /** Ibiko 1 · 0.1, Ibiko's own model (its whole code is in the ibiko1 folder), plugged into this screen. */
    synchronized ai.ibiko.ibiko1.Ibiko1 ibiko1() {
        if (ibiko1 == null) {
            ibiko1 = new ai.ibiko.ibiko1.Ibiko1(new ai.ibiko.main.Ibiko1Screen(this, ibikoScreen), getFilesDir());
        }
        return ibiko1;
    }

    /** What Ibiko 1 is given of this screen (see main/Ibiko1Screen). */
    final ai.ibiko.main.Ibiko1Screen.Parts ibikoScreen = new ai.ibiko.main.Ibiko1Screen.Parts() {
        @Override
        public String pilotName() {
            String n = brain == null ? null : brain.userName();
            return n == null || n.trim().isEmpty() ? null : n.trim();
        }

        @Override
        public String persona() {
            return stage.agent();
        }

        @Override
        public ai.ibiko.stage.ChatPane chat() {
            return chatPane;
        }

        @Override
        public void say(String persona, String text) {
            play(LocalTurn.speakAs(persona, text), null);
        }

        @Override
        public boolean online() {
            // Settings › Web and services: with web look-ups off, Ibiko 1 answers from what it already knows
            return (connectivity == null || connectivity.isOnline()) && prefs.getBoolean("fresh_facts", true);
        }

        @Override
        public ai.ibiko.learn.BrainBox brainBox() {
            return MainActivity.this.brainBox();
        }

        @Override
        public boolean modelReady() {
            // a model downloaded on the device: Ibiko 1 asks it for what its own memory doesn't hold
            return model != null && model.installed() && prefs.getBoolean("ibiko1_uses_model", true);
        }

        @Override
        public String askModel(String system, String question) {
            try {
                return model == null ? null : model.task(system, question, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(prefs), null);
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        public java.util.List<String> aboutPilot() {
            return pilotProfile().lines();
        }

        @Override
        public void answered(ai.ibiko.ibiko1.Ibiko1.How how) {
            if (model != null) {
                model.usage.recordOwn(how == ai.ibiko.ibiko1.Ibiko1.How.MEMORY ? ai.ibiko.llm.UsageLog.Own.MEMORY
                        : how == ai.ibiko.ibiko1.Ibiko1.How.RESEARCH ? ai.ibiko.llm.UsageLog.Own.RESEARCH : ai.ibiko.llm.UsageLog.Own.OTHER,
                        System.currentTimeMillis());
            }
            checkWorkload();
        }

        @Override
        public void note(String text) {
            MainActivity.this.note(text);
        }

        @Override
        public android.os.Handler handler() {
            return handler;
        }

        @Override
        public boolean allows(String code) {
            try {
                return chatTools(stage.agent()).allowedCodes().contains(ai.ibiko.suit.FunctionCode.valueOf(code));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        @Override
        public void act(String message, String codeLine) {
            // the same gate every model's code goes through
            ai.ibiko.llm.BrainOrder order = ai.ibiko.llm.BrainOrder.leading(codeLine);
            if (order != null) {
                carryOut(stage.agent(), message, order);
            }
        }
    };

    /** In {@link Brains}. */
    ai.ibiko.suit.Cortex cortex() {
        return brains.cortex();
    }
    /** The words the brain last acted on from what it learned, until the next message (for "no, that's wrong"). */
    String actedFromBox, boxBefore;

    /** In {@link Brains}. */
    boolean apiDirect() {
        return brains.apiDirect();
    }

    /** In {@link Brains}. */
    ai.ibiko.learn.BrainBox brainBox() {
        return brains.brainBox();
    }

    /** In {@link Brains}. */
    boolean brainReads() {
        return brains.brainReads();
    }

    /** In {@link Brains}. */
    boolean modelOn() {
        return brains.modelOn();
    }

    /** In {@link Brains}. */
    void warmModel() {
        brains.warmModel();
    }

    /** When the device's model was last made ready for a message being typed. */
    long modelPrepared;

    /** In {@link Brains}. */
    void prepareModel() {
        brains.prepareModel();
    }

    void carryOut(String agent, String question, ai.ibiko.llm.BrainOrder order) {
        new PhoneActs(this).run(agent, question, order);
    }

    /** One hand-over a message: the one it was passed to answers it and does not pass it on again. */
    boolean handedOver;

    /** In {@link Handovers}. */
    void handOver(String from, String question, String to) {
        handovers.handOver(from, question, to);
    }

    /**
     * Ibiko as a coding agent (Claude Code style): it plans, reads and writes files in its workspace, runs commands
     * (asking first unless Auto-accept is on), fetches pages and uses connectors, step by step, then answers. Each
     * step shows in the chat. The strong model does the work; the stop button stops it between steps.
     */
    void runIbiko(final String task) {
        String repos = chatTools("ibiko").brief();
        runAgent(repos.isEmpty() ? task : task + "\n\n(" + repos + ")", "ibiko", false);
    }

    /** In {@link Handovers}. */
    boolean aboutPhoneFiles(String text) {
        return handovers.aboutPhoneFiles(text);
    }

    /**
     * An agent at work on the phone's files: Ibiko with every tool (write, run, git), or Obi or Ivie only reading
     * ({@code readOnly}). Each step shows in the chat; the stop button stops it between steps.
     */
    void runAgent(final String task, final String persona, final boolean onlyReading) {
        new AgentSession(this).run(task, persona, onlyReading);
    }

    /** In {@link AgentWork}. */
    void markRunning(String persona, String task) {
        agentWorkBook.markRunning(persona, task);
    }

    /** In {@link AgentWork}. */
    void offerToCarryOn() {
        agentWorkBook.offerToCarryOn();
    }

    boolean carryOnOffered;

    /** The reply being written, backed up every few seconds so a crash doesn't lose it. */
    long partialSavedAt;

    /** In {@link AgentWork}. */
    void backupPartial(String soFar) {
        agentWorkBook.backupPartial(soFar);
    }

    /** In {@link AgentWork}. */
    void clearPartial() {
        agentWorkBook.clearPartial();
    }

    /** In ThinkTurns. */
    void thinkTogether(final String question, final String speaker) {
        thinkTurns.thinkTogether(question, speaker);
    }

    /** In ThinkTurns. */
    void factCheck(final int id, final String persona, final String question, final String answer) {
        thinkTurns.factCheck(id, persona, question, answer);
    }

    /** In {@link Handovers}. */
    void handOff(String persona, String task) {
        handovers.handOff(persona, task);
    }

    /** A clear-out of the working memory is under way (one at a time). */
    boolean compacting;

    static final String SUM_UP = "Sum up the conversation below for your own memory, in under 150 words: the facts the"
            + " user gave, what was decided, what is being worked on, and open questions. Plain lines, no greeting. /no_think";

    /** In {@link AgentWork}. */
    void checkWorkload() {
        agentWorkBook.checkWorkload();
    }

    /** Steps in one round of long work: a progress note in the chat after each. */
    static final int ROUND_STEPS = 8;
    /** Long work stops (and says how to go on) after this long, or this many rounds. */
    static final long MAX_WORK_MS = 8L * 3_600_000;
    static final int MAX_ROUNDS = 400;
    static final String CARRY_ON = "Carry on with the work from where you stopped. Read PLAN.md if there is one and go on with"
            + " the next unticked item; test what you build. Do not tell the user you are continuing: do the next step."
            + " The repository folder is repos/<name> in this chat's own folder (guest /workspace/repos/<name>). Pull and"
            + " clone only there, then read, edit and run in that same folder. Memories about this chat are in memory/;"
            + " other chats' files are not here. projects/<name> is not a folder.";

    /** In {@link AgentWork}. */
    void progressNote(int id, String persona, int round, String note) {
        agentWorkBook.progressNote(id, persona, round, note);
    }

    /** In {@link AgentWork}. */
    static boolean carriesOn(String said) {
        return AgentWork.carriesOn(said);
    }

    /** In {@link AgentWork}. */
    String agentWork(String persona) {
        return agentWorkBook.agentWork(persona);
    }

    /** In {@link AgentWork}. */
    String workKey(String persona) {
        return agentWorkBook.workKey(persona);
    }

    /** In {@link AgentWork}. */
    void saveAgentWork(String key, String work) {
        agentWorkBook.saveAgentWork(key, work);
    }

    /** In {@link IbikoTools}. */
    void showIbikoMode(boolean visible) {
        ibikoTools.showIbikoMode(visible);
    }

    /** In {@link IbikoTools}. */
    void chooseIbikoMode() {
        ibikoTools.chooseIbikoMode();
    }

    /** In {@link IbikoTools}. */
    void approvePlan(final String task, final String plan) {
        ibikoTools.approvePlan(task, plan);
    }

    /** In {@link IbikoTools}. */
    String connectorTools() {
        return ibikoTools.connectorTools();
    }

    /** In {@link IbikoTools}. */
    void useConnector(final String question, String call) {
        ibikoTools.useConnector(question, call);
    }

    /** In {@link IbikoTools}. */
    static String cut(String s, int most) {
        return IbikoTools.cut(s, most);
    }

    /** A command waiting for Termux's permission (asked in the chat): the question and the command. */
    String[] shellWaiting;
    static final int TERMUX = 4107;

    /** In {@link IbikoTools}. */
    void runShell(final String question, final String command) {
        ibikoTools.runShell(question, command);
    }

    /** In {@link IbikoTools}. */
    void lookUpAnswer(final String question) {
        ibikoTools.lookUpAnswer(question);
    }

    /** In {@link IbikoTools}. */
    java.util.List<java.io.File> shellRoots() {
        return ibikoTools.shellRoots();
    }

    volatile boolean writingShown, reasoningShown;

    /** In {@link BrainGate}. */
    boolean modelPiece(int id, String text) {
        return gates.modelPiece(id, text);
    }

    /** In {@link BrainGate}. */
    void answer(String text, boolean interpreted) {
        gates.answer(text, interpreted);
    }

    /** Messages written while the API was out of reach (no internet): chat id and words, sent when it's back. */
    final java.util.List<String[]> waitingOnline = new java.util.ArrayList<String[]>();
    /** True while a waiting message is sent again (it is already in the chat). */
    boolean resending;

    /** The pilot picked an API: it needs the internet to answer. */
    boolean needsInternet() {
        return "api".equals(brainPick()) && !new ai.ibiko.llm.ProviderStore(this).usable().isEmpty()
                && !(model != null && model.fallsBackOffline()); // the phone's model answers offline, when allowed
    }

    /** In {@link InternetWait}. */
    void waitForInternet(String text) {
        internetWait.waitForInternet(text);
    }

    /** In {@link InternetWait}. */
    void sendWaiting() {
        internetWait.sendWaiting();
    }

    /** Posts, shares and copies waiting for a yes (the spec's staged actions). */
    final ai.ibiko.brain.ActionPlanner stager = new ai.ibiko.brain.ActionPlanner();
    android.app.Dialog stagedCard;

    /** In {@link StagedActions}. */
    boolean stagedAction(String text) {
        return stagedActions.stagedAction(text);
    }

    /** In {@link StagedActions}. */
    void stageAction(String what) {
        stagedActions.stageAction(what);
    }

    /** "make a PDF of that", "save this as a Word document", "write a spreadsheet of …": a file to make. */
    public static boolean asksForFile(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(java.util.Locale.US);
        return t.matches("(?s)^(please |can you |could you )?(make|create|save|export|turn|convert|put|write|generate|give me|produce)\\b.*"
                + "\\b(pdf|word (document|file|doc)|docx|excel|spreadsheet|xlsx|html (file|page)|web page|csv|markdown|text file|json file)\\b.*")
                && !t.matches("(?s).*\\b(parser|reader|library|code|class|function|script|app|program|converter)\\b.*");
    }

    static final String WRITE_DOC = "Write the document the user asks for, in Markdown: a # title, ## sections, short"
            + " paragraphs, - lists, and | tables | where they help. Write the whole document and nothing else (no"
            + " greeting, no note about the file). /no_think";

    /** The brain's SOCIAL order ({@link ai.ibiko.main.SocialFlow}). */
    final ai.ibiko.main.SocialFlow socialFlow = new ai.ibiko.main.SocialFlow(new ai.ibiko.main.SocialFlow.Host() {
        @Override
        public String agent() {
            return stage.agent();
        }

        @Override
        public void say(String line) {
            play(LocalTurn.speakAs(stage.agent(), line), null);
        }

        @Override
        public void openAccounts() {
            startActivityForResult(new android.content.Intent(MainActivity.this, ai.ibiko.chat.ui.SocialActivity.class), SOCIAL);
        }

        @Override
        public void openLogin(ai.ibiko.social.Platform platform) {
            startActivity(new android.content.Intent(MainActivity.this, ai.ibiko.chat.ui.SocialLoginActivity.class)
                    .putExtra(ai.ibiko.chat.ui.SocialLoginActivity.EXTRA_PLATFORM, platform.name()));
        }

        @Override
        public boolean signedIn(ai.ibiko.social.Platform platform) {
            return ai.ibiko.social.SocialWeb.accounts(MainActivity.this).inUse(platform) != null;
        }

        @Override
        public boolean anySignedIn() {
            return !ai.ibiko.social.SocialWeb.accounts(MainActivity.this).ready().isEmpty();
        }

        @Override
        public boolean online() {
            return connectivity == null || connectivity.isOnline();
        }

        @Override
        public void writePost(String prompt, int tokens) {
            askLocal(prompt, null, false, tokens);
        }

        @Override
        public boolean versions() {
            return prefs.getBoolean("post_versions", true);
        }

        @Override
        public void writeVersions(ai.ibiko.social.Platform platform, String words) {
            postVersions(platform, words);
        }

        @Override
        public void writeComment(String app, String link, String what) {
            commentDraft(app, link, what);
        }

        @Override
        public int chatTokens() {
            return ai.ibiko.chat.Tuning.CHAT_TOKENS.get(prefs);
        }

        @Override
        public void search(String question, String call) {
            useConnector(question, call);
        }
    });

    /**
     * The brain's SOCIAL order, carried out: connect (the sign-in page), help (how to use the accounts), post (the
     * model writes it; "post it on …" then opens the app with it), search and trending (read with the user's own
     * accounts, within the limits that keep them safe, answered from what people posted, labelled as their claims).
     * {@code arg}: "connect facebook", "help", "post facebook: our sale", "search x: fuel price", "trending reddit".
     */
    void social(String question, String arg) {
        socialFlow.run(question, arg);
    }

    /** In WritingTurns. */
    boolean makeDocument(final String text) {
        return writingTurns.makeDocument(text);
    }

    /** The last reply in this chat (what "post that" means). */
    String lastReply() {
        if (chats == null) {
            return null;
        }
        java.util.List<ai.ibiko.chat.ChatBook.Message> ms = chats.current().messages;
        for (int i = ms.size() - 1; i >= 0; i--) {
            ai.ibiko.chat.ChatBook.Message m = ms.get(i);
            if ("assistant".equals(m.role) && "text".equals(m.kind) && m.body != null && !m.body.trim().isEmpty()) {
                return m.body.trim();
            }
        }
        return null;
    }

    /** In {@link SocialPosts}. */
    void postVersions(final ai.ibiko.social.Platform platform, String words) {
        socialPosts.postVersions(platform, words);
    }

    /** In {@link SocialPosts}. */
    void pickVersion(final ai.ibiko.social.Platform platform, java.util.List<String> versions, final java.util.List<String> tags) {
        socialPosts.pickVersion(platform, versions, tags);
    }

    /** In {@link SocialPosts}. */
    boolean autoPost() {
        return socialPosts.autoPost();
    }

    /** In {@link SocialPosts}. */
    void offerOrPost(PendingAction p) {
        socialPosts.offerOrPost(p);
    }

    /** In {@link SocialPosts}. */
    void commentDraft(final String app, final String link, final String what) {
        socialPosts.commentDraft(app, link, what);
    }

    /** In {@link StagedActions}. */
    void showStaged(final PendingAction p) {
        stagedActions.showStaged(p);
    }

    /** In {@link StagedActions}. */
    void closeStaged() {
        stagedActions.closeStaged();
    }

    /** In WritingTurns. */
    void runWritingPlan(final String persona, final String task) {
        writingTurns.runWritingPlan(persona, task);
    }

    /** In {@link SocialPosts}. */
    void comment(final PendingAction p, final boolean auto) {
        socialPosts.comment(p, auto);
    }

    /** In {@link StagedActions}. */
    void doAction(PendingAction p, boolean auto) {
        stagedActions.doAction(p, auto);
    }

    /** The phrasings already understood ("are you done?" → give me the result), kept on the phone. */
    ai.ibiko.turn.Meaning.Memory meanings;

    ai.ibiko.turn.Meaning.Memory meanings() {
        if (meanings == null) {
            meanings = ai.ibiko.turn.Meaning.Memory.read(prefs == null ? null : prefs.getString("meanings", null));
        }
        return meanings;
    }

    final ai.ibiko.main.DirectCommands.Host commandHost = new ai.ibiko.main.DirectCommands.Host() {
        @Override
        public boolean workUnderWay() {
            return paused != null || !agentWork(stage.agent()).isEmpty();
        }

        @Override
        public ai.ibiko.turn.Meaning.Command remembered(String text) {
            return meanings().get(text);
        }

        @Override
        public void remember(String text, ai.ibiko.turn.Meaning.Command command) {
            meanings().put(text, command);
            if (prefs != null) {
                prefs.edit().putString("meanings", meanings().write()).apply();
            }
        }

        @Override
        public boolean remoteModel() {
            return model != null && model.remoteOn();
        }

        @Override
        public void ask(String text) {
            askTheBrain(text);
        }

        @Override
        public void say(String canonical) {
            answer(canonical, true);
        }

        @Override
        public String classify(String prompt, String text) {
            return model.task(prompt, text, 24, null);
        }

        @Override
        public android.os.Handler handler() {
            return handler;
        }

        @Override
        public android.view.View clock() {
            return chatPane != null ? chatPane : getWindow().getDecorView();
        }

        @Override
        public boolean alive() {
            return !isFinishing();
        }

        @Override
        public void note(String line) {
            MainActivity.this.note(line);
        }

        @Override
        public void perform(ai.ibiko.turn.Meaning.Command command) {
            command(command);
        }

        @Override
        public void holdReply(boolean on) {
            if (on) {
                ai.ibiko.llm.ReplyService.hold(MainActivity.this);
            } else {
                ai.ibiko.llm.ReplyService.release(MainActivity.this);
            }
        }

        @Override
        public void cancelModel() {
            if (model != null) {
                model.cancel();
            }
        }
    };
    final ai.ibiko.main.DirectCommands commands = new ai.ibiko.main.DirectCommands(commandHost);

    /** In {@link Commands}. */
    void command(ai.ibiko.turn.Meaning.Command c) {
        commands_.command(c);
    }

    /** In {@link Retries}. */
    static boolean isRetry(String text) {
        return Retries.isRetry(text);
    }

    /** In {@link Retries}. */
    void retryLast() {
        retries.retryLast();
    }

    /** In {@link Retries}. */
    void clearWorkspace() {
        retries.clearWorkspace();
    }

    /** In {@link Retries}. */
    static void deleteTree(java.io.File f) {
        Retries.deleteTree(f);
    }

    /** In {@link Steering}. */
    static String spoken(List<TurnLine> said) {
        return Steering.spoken(said);
    }

    static String blank(String extra) {
        return extra == null || extra.isEmpty() ? "" : "\n" + extra;
    }

    /** In {@link Steering}. */
    void steerIntoTask(String text, boolean attached) {
        steering.steerIntoTask(text, attached);
    }

    /** What the job running in the background is doing, for a reply given beside it. */
    volatile String backgroundTask;

    /**
     * A message sent in a chat that was not the one running. When that run ends, and this chat is the one on screen,
     * it is asked. The other chat was not cancelled to make room.
     */
    final Runnable releaseHeld = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || heldChat.isEmpty() || chats == null || chats.current() == null) {
                return;
            }
            if (modelGoing()) {
                handler.postDelayed(this, 400);
                return;
            }
            String here = pageKey();
            String[] match = null;
            for (String[] h : heldChat) {
                if (here.equals(h[0])) {
                    match = h;
                    break;
                }
            }
            if (match == null) {
                return;
            }
            heldChat.remove(match);
            answer(match[1], true);
        }
    };

    /**
     * A steered message the task never read (it finished, or it was never a multi-step run). It goes out as its own
     * turn. While work is still going this waits, so it is not delivered twice and the task is not cancelled to send it.
     */
    final Runnable releaseUnseenSteer = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || !steer.pending()) {
                return;
            }
            if (busy()) {
                handler.postDelayed(this, 500);
                return;
            }
            String more = steer.drain();
            if (more != null && !more.isEmpty()) {
                answer(more, true); // already shown in the chat
            }
        }
    };

    /** In {@link Steering}. */
    static String tail(String s, int most) {
        return Steering.tail(s, most);
    }

    /** In {@link LocalAsk}. */
    String foldSteer(int id, String system, String question, String context, int length, String soFar, ai.ibiko.llm.NativeLlm.Sink sink) {
        return localAsk.foldSteer(id, system, question, context, length, soFar, sink);
    }

    /** In {@link Steering}. */
    void noteUser(String text) {
        steering.noteUser(text);
    }

    /** In {@link Steering}. */
    String situation(boolean ivie) {
        return steering.situation(ivie);
    }


    /** In {@link Steering}. */
    void reportModel() {
        steering.reportModel();
    }

    static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() > 60 ? t.substring(0, 57) + "\u2026" : t;
    }

    /** In {@link LocalAsk}. */
    boolean askLocal(final String question, final BrainAnswer offline) {
        return localAsk.askLocal(question, offline);
    }

    /** In {@link LocalAsk}. */
    boolean askLocal(final String question, final BrainAnswer offline, final boolean research) {
        return localAsk.askLocal(question, offline, research);
    }

    /** In {@link LocalAsk}. */
    boolean askLocal(final String question, final BrainAnswer offline, final boolean research, final int length) {
        return localAsk.askLocal(question, offline, research, length);
    }

    /** In {@link LocalAsk}. */
    boolean askLocal(final String question, final BrainAnswer offline, final boolean research, final int length, final boolean acts) {
        return localAsk.askLocal(question, offline, research, length, acts);
    }

    final java.util.LinkedHashSet<String> shownPhotos = new java.util.LinkedHashSet<String>();

    /** In {@link DrawingShelf}. */
    void lookUpPhotos(final String subject) {
        drawingShelf.lookUpPhotos(subject);
    }

    ai.ibiko.render.field.SolidSpec currentSolid;

    ai.ibiko.library.DrawingLibrary library;

    interface Awaiting {
        void take(String said);
    }

    Awaiting awaiting;

    String currentName;

    /** In {@link DrawingShelf}. */
    static String names(java.util.List<ai.ibiko.library.DrawingLibrary.Entry> list) {
        return DrawingShelf.names(list);
    }

    /** In {@link DrawingShelf}. */
    static String describe(java.util.List<ai.ibiko.library.DrawingLibrary.Entry> list) {
        return DrawingShelf.describe(list);
    }

    /** In {@link Brains}. */
    void applyProvider() {
        brains.applyProvider();
    }

    /** In {@link Brains}. */
    String brainLabel() {
        return brains.brainLabel();
    }

    /** In {@link Brains}. */
    void askForBrainIfNone() {
        brains.askForBrainIfNone();
    }

    /** In {@link Brains}. */
    String brainPick() {
        return brains.brainPick();
    }

    /** In {@link Brains}. */
    boolean ibikoOwn() {
        return brains.ibikoOwn();
    }

    static final String IBIKO_OWN = "Ibiko 1 \u00B7 0.1";


    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }





    /** In {@link Brains}. */
    void openProviders() {
        brains.openProviders();
    }

    /** The model choice this screen loaded ({@link ai.ibiko.llm.ModelHub#generation}). */
    int modelGeneration;

    /** In {@link Brains}. */
    void loadModel(final boolean tell) {
        brains.loadModel(tell);
    }

    /** In {@link Brains}. */
    boolean modelCommand(String said) {
        return brains.modelCommand(said);
    }


    /** In {@link Brains}. */
    void startModelDownload() {
        brains.startModelDownload();
    }

    static boolean plain(BrainAnswer a) {
        return a.action == null && a.command == null && !a.listenAfter && a.sketch == null;
    }

    /** In {@link Creation}. */
    boolean create(final String text) {
        return creationTurns.create(text);
    }

    /** In {@link Handovers}. */
    void discuss(final ai.ibiko.turn.DualAgentCoordinator.Request r) {
        handovers.discuss(r);
    }

    /** In {@link LocalAsk}. */
    ai.ibiko.turn.DualAgentCoordinator.Agent agentFor(final String persona, final boolean online) {
        return localAsk.agentFor(persona, online);
    }


    void saveName(String name) {
        saveName(name, name);
    }

    void saveName(String called, String given) {
        String full = given == null || given.isEmpty() ? called : given;
        prefs.edit().putString("user_name", called).putString("given_name", full).apply();
        brain.setUserName(called);
        brain.setGivenName(full);
        if (journal != null) {
            journal.add(LearningJournal.TAUGHT_FACT, "my name", full);
        }
        syncSoon();
    }

    @Override
    public void onHeadTap() {
        stopPlay();
        play(LocalTurn.call(stage.agent()), null);
    }

    /** In {@link VoiceInput}. */
    void switchFace(String agent, boolean greet) {
        voiceInput.switchFace(agent, greet);
    }

    boolean holdingMic, releasedMic;
    final StringBuilder heldWords = new StringBuilder();
    String lastPartial = "";
    int holdToken;

    @Override
    public void onMic() {
        if (assistant != null) {
            assistant.onMicrophone(ai.ibiko.ops.task.MicState.MICROPHONE_STARTING);
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

    /** In {@link VoiceInput}. */
    void sendHeld(String last) {
        voiceInput.sendHeld(last);
    }

    /** In {@link VoiceInput}. */
    void startListening(boolean hold) {
        voiceInput.startListening(hold);
    }

    @Override
    public void onType() {
        android.widget.EditText field = findViewById(R.id.et_message);
        if (field != null) {
            field.requestFocus();
            return;
        }
        if (typeBar != null) {
            if (typeBarHost != null)
                typeBarHost.setVisibility(android.view.View.VISIBLE);
            typeBar.setVisibility(android.view.View.VISIBLE);
            typeBar.show(awaitingName ? "Your name" : "Type to Ibiko");
        }
    }

    @Override
    public void onHistory(String action) {
        if ("undo".equals(action) || "redo".equals(action)) {
            armThink(action.equals("undo") ? "Undoing the last change" : "Redoing the last change",
                    "Updating the model");
            create(action);
            endThink();
        }
    }

    @Override
    public void onPart(String label) {
        if (label == null || label.isBlank()) {
            return;
        }
        stage.innerVoice("Selected " + label + ". Not moved.");
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
        voiceTurn = true;
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
            assistant.onMicrophone(ai.ibiko.ops.task.MicState.MICROPHONE_OFF);
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

    /** In {@link VoiceInput}. */
    String selfSheet() {
        return voiceInput.selfSheet();
    }

    /** The words the voice says for a line: a model's own connect failure is said plainly when the phone is online. */
    String spokenLine(String line) {
        return walker.spokenLine(line);
    }

    /** Show these lines in order, then run {@code done} once the voice has gone quiet. */
    void play(List<TurnLine> next, Runnable done) {
        walker.play(next, done);
    }

    /** Play the next line, or finish the walk when the lines run out. */
    void step(final int token) {
        walker.step(token);
    }

    void stopPlay() {
        walker.stopPlay();
    }

    /** In {@link Pickers}. */
    void openContentPicker(String kind, boolean learn) {
        pickers.openContentPicker(kind, learn);
    }

    /** In {@link Pickers}. */
    void openReconstructionPicker() {
        pickers.openReconstructionPicker();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SOCIAL) {
            String words = data == null ? null : data.getStringExtra("compose");
            android.widget.EditText field = findViewById(R.id.et_message);
            if (words != null && field != null) {
                field.setText(words);
                field.setSelection(words.length());
                field.requestFocus();
            }
            return;
        }
        if (requestCode == HISTORY) {
            if (chats != null) {
                chats.reload();
            }
            if (chatPane != null) {
                chatPane.show();
            }
            return;
        }
        if (requestCode != PICK_CONTENT) {
            return;
        }
        if (pickerReconstruct) {
            pickerReconstruct = false;
            pickerLearn = false;
            if (resultCode != RESULT_OK || data == null) {
                return; // the picker was closed: that is the answer, nothing to say
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
                note("No photographs were chosen.");
                return;
            }
            for (android.net.Uri uri : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
            }
            armThink("Reading the photographs", "Triangulating views");
            final int token = nextAsk();
            final java.util.List<android.net.Uri> chosen = uris;
            new Thread(() -> deliverReconstruction(chosen, token), "ibiko-recon").start();
            return;
        }
        if (resultCode == RESULT_OK && data != null && !pickerLearn && attachments != null) {
            java.util.List<android.net.Uri> picked = pickedUris(data);
            if (!picked.isEmpty()) {
                for (android.net.Uri u : picked) {
                    try {
                        getContentResolver().takePersistableUriPermission(u,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                    attachPicked(u); // each waits in the box for the user's instructions
                }
                return;
            }
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pickerLearn = false;
            return; // the picker was closed: no reply in the chat
        }
        final android.net.Uri uri = data.getData();
        final boolean learn = pickerLearn;
        pickerLearn = false;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        if (!learn && attachments != null) {
            attachPicked(uri); // waits in the box for the user's instructions, as in other chat apps
            return;
        }
        stage.innerVoice("Reading the file");
        stage.activity("FILE", "Reading the file");
        final int token = nextAsk();
        new Thread(() -> deliverFile(uri, learn, token), "ibiko-file").start();
    }

    /** In {@link Pickers}. */
    static java.util.List<android.net.Uri> pickedUris(android.content.Intent data) {
        return Pickers.pickedUris(data);
    }

    /** In {@link Pickers}. */
    void deliverReconstruction(java.util.List<android.net.Uri> uris, int token) {
        pickers.deliverReconstruction(uris, token);
    }

    /** In {@link Pickers}. */
    ai.ibiko.ops.recon.RasterImage readRaster(android.net.Uri uri) throws java.io.IOException {
        return pickers.readRaster(uri);
    }

    /** In {@link Pickers}. */
    void deliverFile(android.net.Uri uri, boolean learn, int token) {
        pickers.deliverFile(uri, learn, token);
    }

    /** In {@link Pickers}. */
    boolean showSelected(android.net.Uri uri, String kind, String name) {
        return pickers.showSelected(uri, kind, name);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == TERMUX) {
            String[] waiting = shellWaiting;
            shellWaiting = null;
            if (waiting != null && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runShell(waiting[0], waiting[1]); // allowed: the command the user asked for runs now
            } else if (waiting != null) {
                note("Not run: that command needs the permission. It is asked again next time.");
            }
            return;
        }
        if (requestCode != MIC) {
            return;
        }
        if (results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toggleDictation(); // the tap that asked for the permission: dictation into the box, as the mic button does
        } else {
            note("The microphone needs the permission. You can still type.");
        }
    }

    /**
     * The offline model is let go five minutes after the app leaves the screen, not at once: freeing it on every
     * glance at another app threw away everything it had read, and the next question read it all again.
     */
    static final long RELEASE_AFTER_MS = 5 * 60_000L;
    final Runnable releaseModel = () -> {
        if (model != null && hub != null && hub.worker != null && !model.working() && !inFront) {
            final ai.ibiko.llm.LocalModel leaving = model;
            hub.worker.execute(leaving::releaseIfIdle);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(releaseModel); // back within five minutes: the model and what it read are still there
        connectivity.start();
        ambient.start();
    }

    @Override
    protected void onStop() {
        inFront = false;
        handler.removeCallbacks(releaseModel);
        handler.postDelayed(releaseModel, RELEASE_AFTER_MS); // let go if the app stays away

        // the connection is watched off screen too: a message waiting for the internet goes as soon as it is back
        ambient.stop();
        super.onStop();
    }

    @Override
    protected void onPause() {
        inFront = false;
        if (chats != null) {
            chats.flush(); // nothing waits unsaved once the app leaves the screen
        }
        if (model != null && prefs != null) {
            prefs.edit().putString("usage_today", model.usage.save()).apply();
        }
        handler.removeCallbacks(engineTick);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(this::takeShared, 400); // something shared to Ibiko from another app
        inFront = true;
        showPersonaBar(); // Settings may have turned the group chat on or off
        connectivity.start();
        ai.ibiko.llm.ReplyNotice.clear(this);
        handler.postDelayed(this::sendWaiting, 1000);
        handler.postDelayed(this::morningReport, 1500);
        handler.post(this::checkWorkload); // the ring shows the brain in use as soon as the chat is back
        if (chatPane != null) {
            chatPane.show(); // replies that came in while Ibiko was in the background are in the chat
        }
        applyProvider(); // back from the AI providers sheet: the brain the user chose answers
        applySettings(); // back from Settings: name, voice speed, Enter key, deleted chats
        meanings = null; // back from "What Ibiko remembers": phrasings it forgot are read again from the phone
        if (!carryOnOffered) {
            carryOnOffered = true; // once a start: work cut off by a crash or a close can carry on
            handler.postDelayed(this::offerToCarryOn, 600);
        }
        if (model != null && model.context(ai.ibiko.chat.Tuning.OFFLINE_CONTEXT.get(prefs)) && prefs.getBoolean("llm_on", true)) {
            loadModel(false); // the offline model's memory was changed in Settings: it starts again with the new size
        }
        if (hub != null && hub.generation != modelGeneration) {
            modelGeneration = hub.generation; // another offline model was chosen in Settings: it starts now
            reportModel();
            if (model.installed() && prefs.getBoolean("llm_on", true)) {
                loadModel(false);
            }
        }
        askForBrainIfNone();
        handler.removeCallbacks(engineTick);
        handler.post(engineTick);
        ai.ibiko.llm.ThinkPace pace = thinkPace;
        if (pace != null && thinkToken == readToken) {
            pace.tick(System.currentTimeMillis());
            updateThoughtStatus(pace.line(), true);
            handler.removeCallbacks(thinkBeat);
            handler.post(thinkBeat);
        }
    }

    /** Pause, Resume and Stop from the reading-aloud notification. */
    final android.content.BroadcastReceiver readControl = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            if (talk == null || i == null || i.getAction() == null) {
                return;
            }
            switch (i.getAction()) {
                case ai.ibiko.voice.ReadAloudNotice.PAUSE:
                    talk.pause();
                    break;
                case ai.ibiko.voice.ReadAloudNotice.RESUME:
                    talk.resume();
                    break;
                default:
                    readAloudHelper.stop();
                    talk.stop();
                    ai.ibiko.voice.ReadAloudNotice.show(MainActivity.this, false, false, null, "", MainActivity.class);
            }
        }
    };

    @Override
    protected void onDestroy() {
        if (waitingForFiles) {
            super.onDestroy(); // closed at once for the first-start download: nothing was set up
            return;
        }
        if (downloadStrip != null) {
            ai.ibiko.net.Transfers.unlisten(downloadStrip);
        }
        try {
            unregisterReceiver(closedByPilot);
        } catch (IllegalArgumentException notRegistered) {
            // never registered
        }
        handler.removeCallbacks(thinkBeat);
        try {
            unregisterReceiver(readControl);
        } catch (IllegalArgumentException notRegistered) {
            // created without it
        }
        ai.ibiko.voice.ReadAloudNotice.show(this, false, false, null, "", MainActivity.class);
        handler.removeCallbacks(taskBeat);
        handler.removeCallbacks(engineTick);
        talk.shutdown();
        keepAwake(false);
        listen.stop();
        sync.shutdown();
        connectivity.stop();
        deskThread.shutdownNow();
        buildThread.shutdownNow();
        if (model != null) {
            model.cancel();
        }
        hub.removeListener(onDownload);
        super.onDestroy();
    }
}
