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

/** A reply being generated: interrupting, stopping, the streaming state and the Send/Stop button. Moved out of MainActivity. */
final class Generation {
    private final MainActivity a;

    Generation(MainActivity a) {
        this.a = a;
    }

    /** Drops the reply on screen and tells the model to stop, so a new message can start. */
    void interruptReply() {
        a.readToken++;
        a.stopVisibleJob();
        a.walker.abandon();
        setStreamOpen(false);
        a.endThink();
        a.keepAwake(false);
        if (a.talk != null) {
            a.talk.stop();
        }
    }

    boolean toggleReading(String text) {
        return a.readAloudHelper.toggleReading(text);
    }

    void stopGeneration() {
        a.readToken++;
        a.stopVisibleJob();
        // Stop is now: the steps of the stopped work are not kept in the chat (they hung at the bottom), and the
        // button is Send again at once, not when the job next looks up (a call can take minutes to give up)
        String page = a.stage == null ? null : a.stage.agent();
        if (a.chatPane != null) {
            a.chatPane.activityDrop();
        }
        a.endThink();
        a.keepAwake(false);
        a.stopPlay();
        if (a.model != null && a.model.coding) {
            a.model.coding = false; // the job sees it was stopped at its next step and ends without a word
        }
        a.generating = false;
        a.streamOpen = false;
        if (a.chats != null && page != null && a.chats.dropUnfinishedWork(page) > 0 && a.chatPane != null) {
            a.chatPane.show();
        }
        refreshSend();
    }

    void setGenerating(boolean on) {
        a.generating = on;
        android.widget.ImageButton send = a.findViewById(R.id.btn_send);
        if (send == null) {
            return;
        }
        refreshSend();
    }

    /** Send is lit when there is something to send, or a reply to stop; grey otherwise. */
    /**
     * Whether a reply is still streaming. Every change redraws the send button: a reply that finished used to redraw it
     * just before this went false, so the button kept showing Stop.
     */
    void setStreamOpen(boolean open) {
        a.streamOpen = open;
        a.handler.post(a::refreshSend);
    }

    void refreshSend() {
        android.view.View send = a.findViewById(R.id.btn_send);
        android.widget.EditText field = a.findViewById(R.id.et_message);
        if (send == null) {
            return;
        }
        boolean words = field != null && field.getText() != null && field.getText().toString().trim().length() > 0;
        boolean files = a.attachments != null && !a.attachments.isEmpty();
        if (words) {
            a.prepareModel(); // the model gets ready while the message is typed
        }
        boolean here = a.thisChatBusy();
        send.setEnabled(here || words || files);
        // while this chat's work runs, an empty box shows Stop; another chat stays a normal Send
        boolean stop = here && !words && !files;
        if (send instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) send).setImageResource(stop ? R.drawable.ic_stop : R.drawable.ic_send);
        }
        send.setContentDescription(stop ? "Stop" : here ? "Send to the running task" : "Send");
        a.handler.removeCallbacks(a.sendRecheck);
        if (here && !a.isFinishing()) {
            a.handler.postDelayed(a.sendRecheck, 1000); // work can end on another thread without telling the button
        }
    }
}
