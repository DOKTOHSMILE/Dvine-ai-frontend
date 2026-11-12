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

/** The creation engine's requests: parse, build and show the result. Moved out of MainActivity. */
final class Creation {
    private final MainActivity a;

    Creation(MainActivity a) {
        this.a = a;
    }

    boolean create(final String text) {
        final ai.ibiko.create.ObjectRequestInterpreter.Request r = a.creation.parse(text,
                a.stage.objectShown());
        if (r == null) {
            return false;
        }
        return runCreation(r);
    }

    boolean runCreation(final ai.ibiko.create.ObjectRequestInterpreter.Request r) {
        if (r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.RECON) {
            a.openReconstructionPicker();
            return true;
        }
        // there are no built-in objects: the model designs each thing with its measurements, and changes them. Every
        // model, the device's and an API's, gets the same full design request (the specification, the brief, every
        // part's exact coordinates, the fixes), so they all work the same way; only the reply room differs
        boolean capable = true;
        a.creation.setDesigner(a.modelOn() ? (system, prompt, max) -> {
                    final String doing = ai.ibiko.create.MeasuredDesign.stageOf(system);
                    a.handler.post(() -> a.activity(doing)); // each design step shows as it starts
                    return a.model.task(system, prompt, max, null);
                } : null,
                a.model != null && a.model.remoteOn() ? ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs) : ai.ibiko.chat.Tuning.AGENT_TOKENS.get(a.prefs), capable);
        boolean slow = r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.CREATE
                || r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.MODIFY
                || r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.OPEN
                || r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.IMAGE;
        if (!slow) {
            a.armThink("Updating the model", "Updating the model");
            applyCreation(a.creation.perform(r));
            a.endThink();
            return true;
        }
        final int id = a.nextAsk();
        a.stopPlay();
        String phase = r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.IMAGE
                ? "Drawing the picture"
                : r.kind == ai.ibiko.create.ObjectRequestInterpreter.Kind.MODIFY ? "Changing the measurements"
                : a.creation.researchAvailable() ? "Researching it and working out the measurements" : "Working out the measurements";
        a.armThink(phase, phase);
        a.designing = true; // several long model calls with no words between them: not a reply that stalled
        a.buildThread.execute(() -> {
            ai.ibiko.create.CreationDirector.Outcome made;
            try {
                made = a.creation.perform(r);
            } finally {
                a.designing = false;
            }
            final ai.ibiko.create.CreationDirector.Outcome o = made;
            a.handler.post(() -> {
                if (a.alive(id) && !a.isFinishing()) {
                    a.endThink();
                    applyCreation(o);
                }
            });
        });
        return true;
    }

    void applyCreation(ai.ibiko.create.CreationDirector.Outcome o) {
        switch (o.action) {
            case BECOME:
                if (!a.stage.becomeObject(o.model)) {
                    o.lines.clear();
                    o.lines.add(
                            "I couldn't start the transformation just now. Try again in a moment.");
                }
                break;
            case CHANGED:
                a.stage.objectChanged();
                break;
            case RESTORE:
                a.stage.restoreBody();
                break;
            default:
                break;
        }
        if (o.picture != null) {
            a.stage.showGenerated(o.picture);
        }
        if (o.picture != null || o.model != null || a.stage.objectShown()) {
            a.showIvieCanvas(true);
        }
        List<TurnLine> lines = new ArrayList<TurnLine>();
        for (String l : o.lines) {
            lines.add(new TurnLine(a.stage.agent(), "SAY", l));
        }
        a.play(lines, null);
    }
}
