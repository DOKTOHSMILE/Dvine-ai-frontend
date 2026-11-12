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

/** Which brain answers: the provider in use, its label, preparing and loading the model, and the model commands. Moved out of MainActivity. */
final class Brains {
    private final MainActivity a;

    Brains(MainActivity a) {
        this.a = a;
    }

    synchronized ai.ibiko.suit.Cortex cortex() {
        if (a.cortex == null) {
            a.cortex = new ai.ibiko.suit.Cortex(q -> brainBox().recallTalk(q));
        }
        return a.cortex;
    }

    /**
     * An API is selected and reachable: the user talks to it directly. It reads the meaning and decides every action
     * of the app; no rule, learned shortcut or separate classifying call acts on the words first.
     */
    /** An API is the chosen brain (online or not): it decides, and no shortcut or device model answers instead. */
    boolean apiDirect() {
        return a.model != null && a.model.apiChosen();
    }

    /** Ibiko's own light brain: how the user's words became actions, learned from every model it works with. */
    synchronized ai.ibiko.learn.BrainBox brainBox() {
        if (a.brainBox == null) {
            a.brainBox = new ai.ibiko.learn.BrainBox(new java.io.File(a.getFilesDir(), "learning/brainbox.tsv"));
        }
        return a.brainBox;
    }

    /**
     * A brain is in the suit (the device's model, or an API): it alone reads what the pilot's words mean and gives Ibiko
     * the code of what to do. No wording runs a function then; the buttons stay the pilot's direct controls.
     */
    boolean brainReads() {
        return modelOn() || apiDirect();
    }

    boolean modelOn() {
        return !ibikoOwn() && a.model != null && a.model.ready() && a.prefs.getBoolean("llm_on", true);
    }

    /**
     * The phone's model reads its instructions ahead of the first question: the routing call's, then the persona's
     * (kept side by side in its memory), so a question is answered without first reading them. Off the main thread.
     */
    void warmModel() {
        if (a.model == null || !a.model.localReady() || a.model.remoteOn() || a.stage == null || a.isFinishing()) {
            return;
        }
        a.modelPrepared = 0; // just loaded: read the instructions now, whenever the last typing was
        prepareModel();
    }

    /**
     * The user started typing: the device's model is made ready now (loaded again if the app was away, its
     * instructions read), as each reply's audio is made while it is read, so Send waits only for the new words.
     * Once in a while, not on every key.
     */
    void prepareModel() {
        if (a.model == null || a.model.remoteOn() || ibikoOwn() || !a.model.installed() || a.stage == null || a.isFinishing()
                || !a.prefs.getBoolean("llm_on", true) || a.model.working()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - a.modelPrepared < 20_000) {
            return;
        }
        a.modelPrepared = now;
        // the one prompt every message now uses (the persona with the orders it may give): read once, ahead of time.
        // The separate routing prompt is gone, so it is not read into memory any more
        final String persona = a.systemFor(a.stage.agent(), true);
        final int threads = ai.ibiko.chat.Tuning.modelThreads(a.prefs);
        a.hub.worker.execute(() -> a.model.prepare(persona, threads));
    }

    /**
     * The brain the user chose on the AI providers sheet: an API answers while the phone is online (the phone's own
     * brain, when installed, answers when it isn't); "On this device" uses only the phone's.
     */
    void applyProvider() {
        if (a.model == null) {
            return;
        }
        ai.ibiko.llm.ProviderStore providers = new ai.ibiko.llm.ProviderStore(a);
        java.util.List<ai.ibiko.llm.RemoteBrain> brains = new java.util.ArrayList<ai.ibiko.llm.RemoteBrain>();
        java.util.List<ai.ibiko.llm.RemoteBrain> fasts = new java.util.ArrayList<ai.ibiko.llm.RemoteBrain>();
        java.util.List<String> names = new java.util.ArrayList<String>();
        java.util.List<Integer> windows = new java.util.ArrayList<Integer>();
        for (ai.ibiko.llm.Providers p : providers.usable()) { // the primary, then the backups it switches to
            ai.ibiko.llm.RemoteBrain b = providers.brain(p);
            if (b != null) {
                brains.add(b);
                fasts.add(providers.fastBrain(p));
                names.add(p.title);
                windows.add(providers.window(p));
            }
        }
        if (!"api".equals(brainPick())) {
            // Ibiko's own brain, or the offline model, was picked: no API is asked
            brains.clear();
            fasts.clear();
            names.clear();
            windows.clear();
        }
        a.model.useRemotes(brains, fasts, names, windows, () -> a.connectivity != null && a.connectivity.isOnline());
        if ((a.model.remoteOn() || ibikoOwn()) && a.hub != null) {
            // switched to an API: the offline model leaves memory now, not at the next question
            final ai.ibiko.llm.LocalModel leaving = a.model;
            a.hub.worker.execute(leaving::releaseIfIdle);
        }
        android.widget.TextView sub = a.findViewById(R.id.tv_header_sub);
        if (sub != null && a.stage != null) {
            sub.setText(brainLabel()); // the title already says Ibiko
        }
    }

    /** What the chat header says answers: the API's model, or the phone's brain. */
    String brainLabel() {
        if (ibikoOwn()) {
            return a.IBIKO_OWN;
        }
        ai.ibiko.llm.ProviderStore providers = new ai.ibiko.llm.ProviderStore(a);
        java.util.List<ai.ibiko.llm.Providers> use = providers.usable();
        if (!use.isEmpty() && "api".equals(brainPick())) {
            return providers.model(use.get(0)) + (use.size() > 1 ? " +" + (use.size() - 1) : "");
        }
        return a.hub == null ? "Ibiko model" : a.hub.chosen().weights; // the offline model on the phone, by its own name
    }

    /** No brain on the phone and no API: the setup screen (download or API key) comes first. */
    void askForBrainIfNone() {
        if (a.model != null && ai.ibiko.chat.ui.SetupActivity.needed(a)) {
            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SetupActivity.class));
        }
    }

    /**
     * The model under the title, tapped: switch between the offline models already on the phone (any time, no
     * download), or go to every model, or to the AI providers (API models).
     */
    /** Which brain the user picked: "ibiko" (Ibiko's own), "device" (the offline model) or "api" (the default). */
    String brainPick() {
        return a.prefs == null ? "api" : a.prefs.getString("brain_pick", "api");
    }

    /** Ibiko 1.0: Ibiko's own intelligence (the Cortex and what it learned), with no model loaded at all. */
    boolean ibikoOwn() {
        return "ibiko".equals(brainPick());
    }

    void openProviders() {
        a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.ProvidersActivity.class));
    }

    /**
     * Before loading the phone's own model: if it is too strong for this phone (more memory than the phone has, or it
     * stopped the app while starting last time), switch to the strongest model already on the phone that fits, and
     * say so. False when nothing on the phone fits (then nothing is loaded).
     */
    boolean safeModel() {
        ai.ibiko.llm.ModelGuard guard = new ai.ibiko.llm.ModelGuard(a.prefs);
        guard.crashedWhileLoading();
        long ram = ai.ibiko.llm.ModelGuard.ramGb(a);
        ai.ibiko.llm.ModelSpec now = a.hub.chosen();
        String why = guard.why(now, ram);
        if (why == null) {
            return true;
        }
        ai.ibiko.llm.ModelSpec instead = guard.fallback(a.model, ram, now.file);
        if (instead == null) {
            a.note(now.weights + " is too strong for this device (" + why + "). Choose a smaller model in Settings \u203A Change the"
                    + " offline model, or use an API.");
            return false;
        }
        a.hub.choose(a, instead, false); // the stronger one stays on the phone, to try again later
        a.modelGeneration = a.hub.generation;
        a.note(now.weights + " is too strong for this device (" + why + "), so Ibiko uses " + instead.weights + " instead.");
        applyProvider();
        return true;
    }

    void loadModel(final boolean tell) {
        if (!tell && ibikoOwn()) {
            return; // Ibiko 1.0 answers on its own: no model is loaded
        }
        if (!tell && a.model != null && a.model.remoteOn()) {
            // an API answers (and the user didn't ask to start it): the offline model is not loaded ahead of time. It loads when an answer needs it
            // (offline, or every API failed), and leaves memory again when an API answers
            return;
        }
        if (!safeModel()) {
            return; // nothing on the phone fits it: answers come from an API until a smaller model is chosen
        }
        a.model.context(ai.ibiko.chat.Tuning.OFFLINE_CONTEXT.get(a.prefs)); // Settings › Advanced › Offline model memory
        a.hub.worker.execute(() -> {
            final ai.ibiko.llm.ModelGuard guard = new ai.ibiko.llm.ModelGuard(a.prefs);
            final String file = a.model.spec.file;
            guard.loading(file); // if the phone stops the app during this load, the next start knows which model did it
            ai.ibiko.llm.ReplyService.hold(a); // loading runs in the foreground: full speed, not stopped off screen
            final boolean ok;
            try {
                ok = a.model.load(ai.ibiko.chat.Tuning.modelThreads(a.prefs)); // the fast cores, or the user's setting
            } finally {
                ai.ibiko.llm.ReplyService.release(a);
            }
            guard.loaded();
            if (ok) {
                a.handler.post(a::warmModel); // the instructions read now, not when the first question comes
            }
            if (!ok && a.model.state() == ai.ibiko.llm.LocalModel.State.FAILED) {
                // it didn't fit in memory: set it aside and start the strongest model that does, instead of nothing
                guard.markTooStrong(file);
                a.handler.post(() -> {
                    if (!a.isFinishing() && safeModel() && a.model.installed()) {
                        loadModel(false);
                    }
                });
                return;
            }
            if (a.assistant != null && a.model.spec != null) {
                new ai.ibiko.ops.mind.ModelAdapter(a.assistant.models()).report(a.model.spec.file, ok);
                a.assistant.orchestrator()
                        .setModel(ok ? ai.ibiko.ops.pipe.CheckedReading.of(a.model, piece -> {
                            ai.ibiko.llm.ThinkPace pace = a.thinkPace;
                            if (pace != null && piece != null) {
                                pace.addText(piece);
                                a.handler.post(() -> {
                                    if (a.thinkPace != null)
                                        a.stage.progress(a.thinkPace.line());
                                });
                            }
                        }) : null);
            }
            final String memory = ok ? a.model.takeMemoryNote() : null;
            a.handler.post(() -> {
                if (memory != null) {
                    a.note(memory); // it had to start with less memory than asked
                }
                a.stage.activity("MODEL", ok ? "ready, offline (" + a.model.localWindow() + " tokens of memory)" : a.model.problem());
                int taught = a.journal == null ? 0 : a.journal.taughtCount();
                if (ok) {
                    a.stage.activity("BRAIN",
                            "synced with Ibiko " + BuildConfigInfo.version(a) + ": 3 personas, "
                                    + taught + (taught == 1 ? " taught fact" : " taught facts"));
                }
                if (tell) {
                    a.play(LocalTurn.speakAs(a.stage.agent(), ok
                            ? "My model is ready and in sync with Ibiko. It knows what this app can do"
                                    + (taught > 0
                                            ? ", and the "
                                                    + (taught == 1 ? "thing" : taught + " things")
                                                    + " you've taught me"
                                            : "")
                                    + ". Ask me anything, even offline."
                            : "I couldn't start my model: " + a.model.problem() + "."), null);
                }
            });
        });
    }

    /** The words about the device's own model (ModelWords). */
    boolean modelCommand(String said) {
        return new ModelWords(a).handle(said);
    }

    void startModelDownload() {
        if (a.connectivity == null || !a.connectivity.isOnline()) {
            a.play(LocalTurn.speakAs(a.stage.agent(),
                    "I'm offline right now. Connect to the internet, ideally Wi-Fi, and ask me again."),
                    null);
            return;
        }
        if (a.hub.downloading()) {
            a.play(LocalTurn.speakAs(a.stage.agent(), a.hub.status.spoken()), null);
            return;
        }
        if (a.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                    a.NOTIFY);
        }
        a.downloadBarHidden = false;
        a.hub.startDownload(a);
        a.play(LocalTurn.speakAs(a.stage.agent(),
                "Downloading now, in the background. You can minimise me; the notification shows how far it is."),
                null);
    }
}
