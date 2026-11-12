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

/** The thinking bar: armed when work starts, its status line, and ended when the reply comes. Moved out of MainActivity. */
final class ThinkBar {
    private final MainActivity a;

    ThinkBar(MainActivity a) {
        this.a = a;
    }

    /** Shows live reasoning progress inside the conversation, not only on the hologram. */
    void updateThoughtStatus(String message, boolean visible) {
        if (a.thoughtStatus == null)
            return;
        a.thoughtStatus.setText(message == null || message.trim().isEmpty() ? "Thinking…" : message);
        android.view.View container = a.findViewById(R.id.thoughtStatusContainer);
        if (container != null) {
            container.setVisibility(android.view.View.GONE);
        }
        if (a.chatPane != null) {
            a.chatPane.setThinking(visible ? a.thoughtStatus.getText().toString() : null);
        }
    }

    /**
     * The quick actions over the message box. Each chip opens exactly what it names: Tools the tools of this chat; Code
     * the files and the Linux shell; Media pictures, video, sound and files to attach; Explore the social accounts,
     * the connectors and scheduled posts; More the rest of the app.
     */
    /** The Think pill: deep thinking for this chat, on or off in one tap; lit while on (the same switch as Tools › Deep thinking). */
    void bindThink() {
        final android.widget.TextView think = a.findViewById(R.id.btn_think);
        if (think == null) {
            return;
        }
        final Runnable paint = () -> {
            boolean on = a.chatTools(a.stage == null ? "ibiko" : a.stage.agent()).deep();
            think.setSelected(on);
            think.setTextColor(a.getColor(on ? R.color.accent : R.color.text_secondary));
            android.graphics.drawable.Drawable[] d = think.getCompoundDrawablesRelative();
            if (d[0] != null) {
                d[0].mutate().setTint(a.getColor(on ? R.color.accent : R.color.icon));
            }
            think.setStateDescription(on ? "On" : "Off");
        };
        paint.run();
        think.setOnClickListener(v -> {
            String who = a.stage == null ? "ibiko" : a.stage.agent();
            ai.ibiko.suit.ChatTools tools = a.chatTools(who);
            boolean on = !tools.deep();
            tools.set(ai.ibiko.suit.ChatTools.Tool.DEEP, on);
            a.prefs.edit().putString(a.toolsKey(who), tools.write()).apply();
            paint.run();
            a.note(on ? "Think is on: Ibiko plans, checks and revises before answering (slower)." : "Think is off: quick answers.");
        });
        a.thinkPaint = paint;
    }

    void repaintThink() {
        if (a.thinkPaint != null) {
            a.thinkPaint.run();
        }
    }

    void armThink(String phase, String writing) {
        a.thinkToken = a.readToken;
        a.thinkWrite = writing == null ? "Thinking\u2026" : writing;
        a.thinkPace = new ai.ibiko.llm.ThinkPace(phase, System.currentTimeMillis());
        a.writingShown = false;
        a.reasoningShown = false;
        a.thinkStartedMs = System.currentTimeMillis();
        a.thinkProgressMs = a.thinkStartedMs;
        a.keepAwake(true);
        final String stepLine = phase == null ? null : phase.replace("\u2026", "").trim();
        a.handler.post(() -> {
            if (a.thinkPace == null || a.thinkToken != a.readToken) {
                return;
            }
            activity(stepLine == null || stepLine.isEmpty() ? "Thinking" : stepLine);
            a.stage.setMode(StageMode.THINKING);
            a.stage.progress(a.thinkPace.line());
            updateThoughtStatus(a.thinkPace.line(), true);
            a.handler.removeCallbacks(a.thinkBeat);
            a.handler.postDelayed(a.thinkBeat, 800);
            a.setGenerating(true);
        });
    }

    void activity(final String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        Integer job = a.stage == null ? null : a.latestOf.get(a.stage.agent());
        String w = job == null ? null : a.replyWorkers.get(job);
        if (w != null) {
            ai.ibiko.agent.AgentBoard.step(w, line); // the worker's current step, on the Workload sheet
        }
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            a.handler.post(() -> activity(line));
            return;
        }
        if (a.thinkPace != null) {
            a.thinkProgressMs = System.currentTimeMillis(); // a step means the job is still going
        }
        if (a.chatPane != null && a.thinkPace != null && (a.prefs == null || a.prefs.getBoolean("show_steps", true))) {
            a.chatPane.activityStep(line);
        }
    }

    void endThink() {
        if (a.chatPane != null) {
            a.chatPane.activityEnd();
        }
        a.handler.post(a::checkWorkload); // after the reply: the gauge, and a clear-out when it is nearly full
        final String slow = a.model == null ? null : a.model.slowAdvice(ai.ibiko.chat.Tuning.SLOW_SPEED.get(a.prefs));
        if (slow != null && a.chatPane != null) {
            // the phone's own model has been slow for three replies: say once what would make it faster (posted on the
            // chat itself: the activity's handler is cleared when the reply starts playing)
            final ai.ibiko.stage.ChatPane pane = a.chatPane;
            pane.postDelayed(() -> {
                if (a.stage != null) {
                    pane.assistant(a.stage.agent(), slow);
                }
            }, 2500);
        }
        a.thinkPace = null;
        if (a.chatPane != null && a.streamOpen == false) {
            a.chatPane.stepEnded(); // a report being written when the work ended is kept, not wiped
        }
        a.handler.removeCallbacks(a.thinkBeat);
        updateThoughtStatus("", false);
        a.setGenerating(false);
        if (!a.replacing) {
            a.runChat = null;
            a.handler.post(a.releaseHeld); // a message typed in another chat goes now, without having cancelled this one
        }
        if (a.steer.pending()) {
            a.handler.post(a.releaseUnseenSteer); // the task ended without reading it: it goes out now, as its own turn
        }
        String switched = a.model == null ? null : a.model.takeSwitchNote();
        if (switched != null) {
            a.note(switched);
        }
    }
}
