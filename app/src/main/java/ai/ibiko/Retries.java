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

/** Retry and a clean workspace. Moved out of MainActivity. */
final class Retries {
    private final MainActivity a;

    Retries(MainActivity a) {
        this.a = a;
    }

    /** "Retry", "try again", "ask that again": words that ask for the message before them, never asked again themselves. */
    static boolean isRetry(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        return t.matches("^(?:please )?(retry|try (that )?again|again|redo|regenerate|ask (that )?again|one more time)( please)?$");
    }

    /**
     * "Retry", "try again": the user's last message before this one is asked again, as it was (with any file it
     * carried), instead of being read as a new request.
     */
    void retryLast() {
        if (a.lastAsked != null && !a.lastAsked.trim().isEmpty() && !isRetry(a.lastAsked)) {
            String shown = ai.ibiko.chat.Attachments.withoutImages(a.lastAsked).replaceAll("(?s)<attachment.*?</attachment>", "").trim();
            a.askTheBrain(a.lastAsked); // the whole question, with the file it carried
            return;
        }
        java.util.List<ai.ibiko.chat.ChatBook.Message> msgs = a.chats == null ? new java.util.ArrayList<ai.ibiko.chat.ChatBook.Message>()
                : a.chats.current().messages;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ai.ibiko.chat.ChatBook.Message m = msgs.get(i);
            if (!"user".equals(m.role) || !"text".equals(m.kind) || m.body == null) {
                continue;
            }
            if (isRetry(m.body) || ai.ibiko.turn.Meaning.Command.RETRY == a.meanings().get(m.body)) {
                continue; // every "retry" is passed over, not only the newest
            }
            a.askTheBrain(m.body);
            return;
        }
        a.play(LocalTurn.speakAs(a.stage.agent(), "There's nothing earlier in this chat to ask again."), null);
    }

    /** Clears Ibiko's workspace (clones, sandbox, files) and its kept work, after a yes: it can't be undone. */
    void clearWorkspace() {
        final java.io.File ws = a.chatSpace();
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setMessage("Delete this chat's files on this device: its cloned repositories, its memories and pictures?"
                        + " Other chats' files stay. Changes that weren't pushed are lost.")
                .setPositiveButton("Delete", (d, w) -> new Thread(() -> {
                    deleteTree(ws);
                    a.handler.post(() -> a.play(LocalTurn.speakAs(a.stage.agent(),
                            "Done. This chat's files are deleted; other chats keep theirs."), null));
                }, "ibiko-clear-workspace").start())
                .setNegativeButton("Keep it", null) // nothing happened, so nothing is said
                .show();
    }

    static void deleteTree(java.io.File f) {
        java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }
}
