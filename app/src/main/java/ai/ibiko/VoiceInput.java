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

/** Voice input: listening, a held mic, the face shown, and Ibiko's self sheet. Moved out of MainActivity. */
final class VoiceInput {
    private final MainActivity a;

    VoiceInput(MainActivity a) {
        this.a = a;
    }

    void switchFace(String agent, boolean greet) {
        a.prefs.edit().putString("face", agent).apply();
        a.stopPlay();
        if (greet) {
            a.play(LocalTurn.call(agent), null);
        } else {
            a.stage.ensureFace(agent, null);
        }
    }

    void sendHeld(String last) {
        a.releasedMic = false;
        a.holdToken++;
        a.listen.stop();
        a.stage.setListening(false);
        String all = (a.heldWords + " " + (last == null ? "" : last)).trim().replaceAll("\\s+", " ");
        a.heldWords.setLength(0);
        a.lastPartial = "";
        if (all.isEmpty()) {
            a.stage.setMode(StageMode.CONTENT);
            a.stage.setSubtitle("");
            return;
        }
        a.stage.setSubtitle(all);
        a.voiceTurn = true;
        a.answer(all);
    }

    void startListening(boolean hold) {
        if (a.checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, a.MIC);
            return;
        }
        if (a.talk != null) {
            a.talk.stop();
        }
        if (a.assistant != null) {
            a.assistant.onMicrophone(ai.ibiko.ops.task.MicState.MICROPHONE_LISTENING);
            String line = a.assistant.activityLine();
            if (line != null) {
                a.stage.activity("TASK", line);
            }
        }
        a.stage.setListening(true);
        a.stage.setSubtitle("");
        a.stage.setMode(StageMode.LISTENING);
        a.listen.start(a, hold);
    }

    String selfSheet() {
        ai.ibiko.llm.IbikoSelf.Facts f = new ai.ibiko.llm.IbikoSelf.Facts();
        f.version = BuildConfigInfo.version(a);
        f.device = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim();
        f.userName = a.brain.userName();
        f.taughtFacts = a.journal == null ? 0 : a.journal.taughtCount();
        f.speaking = a.stage.agent();
        return ai.ibiko.llm.IbikoSelf.sheet(f);
    }
}
