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

/** Work handed between personas, files on the phone, and discussions. Moved out of MainActivity. */
final class Handovers {
    private final MainActivity a;

    Handovers(MainActivity a) {
        this.a = a;
    }

    /**
     * The brain said this message is another persona's work (\u00A7HO, or Ibiko's hand-over tool): that persona takes
     * it and answers, in the same group thread. The pilot's message is not shown twice.
     */
    void handOver(String from, String question, String to) {
        if (to == null || !to.matches("ivie|obi|ibiko") || a.handedOver || question == null) {
            // already passed once, or to itself: it answers as it is, with no codes, so the message is never dropped
            a.askLocal(question, null, false, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs));
            return;
        }
        a.handedOver = true;
        a.selectPersona(to);
        a.runChat = a.chats == null || a.chats.current() == null ? null : a.pageKey();
        a.runAgent = to;
        if (a.chats != null) {
            a.chats.workOn(to);
        }
        if (a.chatPane != null) {
            a.chatPane.workFor(to);
            a.chatPane.activityStep(a.labelOf(from) + " passed this to " + a.labelOf(to));
        }
        a.brainGate().handle(question, System.currentTimeMillis());
    }

    /**
     * The files on the phone (cloned repositories, Ibiko's projects) are the user's to ask about with any persona
     * and any model, including ones that take no files (DeepSeek and others): the files reach the model as text
     * through the agent's tools. True when the message is about them and there are some.
     */
    boolean aboutPhoneFiles(String text) {
        java.io.File ws = a.chatSpace();
        String[] kids = ws.list();
        if (kids == null || kids.length == 0) {
            return false;
        }
        String t = text.toLowerCase(java.util.Locale.US);
        if (t.matches("(?s).*\\b(repo|repos|repository|repositories|codebase|cloned|clone|workspace|project files?|source code|the code|this code)\\b.*")) {
            return true;
        }
        for (String k : kids) {
            if (k.length() > 2 && !k.startsWith(".") && t.contains(k.toLowerCase(java.util.Locale.US))) {
                return true; // names a cloned folder
            }
        }
        return t.matches("(?s).*\\b[\\w-]+\\.(java|kt|py|js|ts|tsx|jsx|md|json|xml|gradle|c|cpp|h|go|rs|rb|php|swift|html|css|ya?ml)\\b.*");
    }

    /** The hand-off button under a plan: that persona starts on the task at once, in this same group thread. */
    void handOff(String persona, String task) {
        if (a.chats == null || task == null || task.trim().isEmpty()) {
            return;
        }
        if (a.thisChatBusy()) {
            a.note("Something is still working here. Tap Stop first, or wait for it to finish.");
            return;
        }
        a.handedOver = false;
        handOver(a.stage.agent(), task, persona);
    }

    void discuss(final ai.ibiko.turn.DualAgentCoordinator.Request r) {
        a.stopPlay();
        final String first = r.first != null ? r.first : a.stage.agent();
        a.armThink("Preparing both sides", "Writing the line");
        final boolean online = a.desk != null && a.connectivity != null && a.connectivity.isOnline()
                && a.serverUrl() != null && a.serverUrl().trim().startsWith("https://");
        final ai.ibiko.turn.DualAgentCoordinator c = new ai.ibiko.turn.DualAgentCoordinator(
                a.agentFor("obi", online), a.agentFor("ivie", online));
        a.discussion = c;
        final int id = a.nextAsk();
        a.deskThread.execute(() -> {
            final List<TurnLine> out = c.discuss(r.question, first, r.mode, null);
            a.handler.post(() -> {
                if (!a.alive(id) || a.discussion != c || a.isFinishing()) {
                    return;
                }
                a.endThink();
                a.play(out, () -> {
                    if (a.discussion == c) {
                        a.discussion = null;
                    }
                });
            });
        });
    }
}
