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

/** The commands Ibiko carries out with its own actions, and the settings applied. Moved out of MainActivity. */
final class Commands {
    private final MainActivity a;

    Commands(MainActivity a) {
        this.a = a;
    }

    /** What the user chose in Settings, applied when the chat comes back to the front. */
    void applySettings() {
        if (a.prefs == null) {
            return;
        }
        String name = a.prefs.getString("user_name", "");
        if (a.brain != null) {
            a.brain.setUserName(name == null || name.trim().isEmpty() ? null : name.trim());
        }
        if (a.talk != null) {
            a.talk.refreshVoice(); // a human voice chosen or downloaded in Settings
            try {
                a.talk.setRateScale(1f); // normal speed: not a setting
            } catch (NumberFormatException ignored) {
                a.talk.setRateScale(1f);
            }
        }
        android.widget.EditText field = a.findViewById(R.id.et_message);
        if (field != null) {
            boolean enterSends = a.prefs.getBoolean("send_enter", false);
            field.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | (enterSends ? 0 : android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
            field.setImeOptions(enterSends ? android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                    : android.view.inputmethod.EditorInfo.IME_ACTION_NONE);
        }
        if (a.prefs.getBoolean("chats_reset", false) && a.chats != null) {
            a.prefs.edit().remove("chats_reset").apply();
            a.chats.reload();
            if (a.chatPane != null) {
                a.chatPane.show();
            }
        }
    }

    /** The commands Ibiko carries out with its own actions, the same ones its buttons use. */
    void command(ai.ibiko.turn.Meaning.Command c) {
        switch (c) {
            case NEW_CHAT:
                a.startNewChatSafely();
                break;
            case SWITCH_IVIE:
                a.selectPersona("ivie");
                break;
            case SWITCH_OBI:
                a.selectPersona("obi");
                break;
            case SWITCH_IBIKO:
                a.selectPersona("ibiko", true);
                break;
            case STOP:
                a.stopGeneration();
                break;
            case MODE_PLAN:
                ai.ibiko.agent.IbikoMode.set(a.prefs, ai.ibiko.agent.IbikoMode.PLAN);
                a.showIbikoMode("ibiko".equals(a.stage.agent()));
                a.play(LocalTurn.speakAs(a.stage.agent(), "Plan mode is on: Ibiko reads and makes a plan, and asks before changing anything."), null);
                break;
            case AUTO_ON:
            case AUTO_OFF:
                ai.ibiko.agent.IbikoMode.set(a.prefs, c == ai.ibiko.turn.Meaning.Command.AUTO_ON ? ai.ibiko.agent.IbikoMode.AUTO
                        : ai.ibiko.agent.IbikoMode.EDITS);
                a.showIbikoMode("ibiko".equals(a.stage.agent()));
                a.play(LocalTurn.speakAs(a.stage.agent(), c == ai.ibiko.turn.Meaning.Command.AUTO_ON
                        ? "Auto-accept is on: Ibiko runs commands, pushes and pull requests without asking."
                        : "Auto-accept is off: Ibiko asks before running commands, pushes and pull requests."), null);
                break;
            case CLEAR_WORKSPACE:
                a.clearWorkspace();
                break;
            case RETRY:
                a.retryLast();
                break;
            case OPEN_SETTINGS:
                a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SettingsActivity.class));
                break;
            default:
                break;
        }
    }
}
