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

/** No internet: the message waits and goes when the connection is back. Moved out of MainActivity. */
final class InternetWait {
    private final MainActivity a;

    InternetWait(MainActivity a) {
        this.a = a;
    }

    /**
     * No internet: the message is kept (it is already in the chat) and goes to the API as soon as the connection is
     * back, and Ibiko says so plainly, so a missing connection never looks like a broken API.
     */
    void waitForInternet(String text) {
        String chat = a.chats == null || a.chats.current() == null ? "" : a.chats.current().id;
        a.waitingOnline.add(new String[] { chat, text });
        // Ibiko answers for itself: one plain line, no model or provider talk
        a.play(LocalTurn.speakAs(a.stage.agent(), "I'm offline right now. I've kept your message and I'll answer it the moment"
                + " the connection is back."), null);
    }

    /** Back online: the messages that waited are sent, each in the chat it was written in. */
    void sendWaiting() {
        if (a.waitingOnline.isEmpty() || a.connectivity == null || !a.connectivity.isOnline() || a.runChat != null && !a.agentWork(a.stage.agent()).isEmpty()) {
            return;
        }
        String here = a.chats == null || a.chats.current() == null ? "" : a.chats.current().id;
        for (int i = 0; i < a.waitingOnline.size(); i++) {
            String[] w = a.waitingOnline.get(i);
            if (!w[0].equals(here)) {
                continue; // it goes when its chat is open
            }
            a.waitingOnline.remove(i);
            a.note("Back online: answering your message.");
            a.resending = true;
            try {
                a.answer(w[1], false);
            } finally {
                a.resending = false;
            }
            return; // one at a time: the next goes after this answer
        }
    }
}
