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

/** The side drawer and the keyboard. Moved out of MainActivity. */
final class Drawer {
    private final MainActivity a;

    Drawer(MainActivity a) {
        this.a = a;
    }

    void toggleDrawer() {
        a.drawer.toggle();
    }

    void openDrawer() {
        a.drawer.open();
    }

    void closeDrawer() {
        a.drawer.close();
    }

    boolean drawerOpen() {
        return a.drawer.isOpen();
    }

    void hideKeyboard() {
        android.view.View focus = a.getCurrentFocus();
        android.view.inputmethod.InputMethodManager im =
                (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (im != null) {
            im.hideSoftInputFromWindow((focus != null ? focus : a.getWindow().getDecorView()).getWindowToken(), 0);
        }
        if (focus != null) {
            focus.clearFocus();
        }
    }
}
