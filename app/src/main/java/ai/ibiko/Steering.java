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

/** Messages while a task runs: steering it, side replies, and what the user said noted for the task. Moved out of MainActivity. */
final class Steering {
    private final MainActivity a;

    Steering(MainActivity a) {
        this.a = a;
    }

    static String spoken(List<TurnLine> said) {
        StringBuilder b = new StringBuilder();
        for (TurnLine l : said) {
            if ("SAY".equals(l.cue) && l.text != null) {
                b.append(b.length() == 0 ? "" : " ").append(l.text);
            }
        }
        return b.toString();
    }

    /**
     * A message sent while work is running. It is shown now. The running task reads it on its next step and keeps
     * going. This does not cancel the model. If that task ends without reading it, {@link #releaseUnseenSteer} sends
     * it as the next turn.
     */
    void steerIntoTask(String text, boolean attached) {
        if (!attached && a.model != null && a.model.coding && text != null && !text.trim().isEmpty()) {
            // sorted first (agent/Intake, no model call): only a change to the project reaches the job's plan; a note on
            // how to work reaches the job without re-planning; a question, a separate request or a "thanks" is answered
            // beside the job, which keeps working
            String t = text.trim();
            ai.ibiko.agent.Intake.Kind kind = ai.ibiko.agent.Intake.sort(t, a.backgroundTask);
            if (kind == ai.ibiko.agent.Intake.Kind.PLAN_CHANGE || kind == ai.ibiko.agent.Intake.Kind.STEER) {
                noteUser(t);
                a.steer.add(ai.ibiko.agent.Intake.forJob(kind, t));
                a.note(kind == ai.ibiko.agent.Intake.Kind.PLAN_CHANGE ? "Added to the plan. The job keeps going."
                        : "Passed to the job. It keeps going as planned.");
                a.handler.removeCallbacks(a.releaseUnseenSteer);
                a.handler.postDelayed(a.releaseUnseenSteer, 500);
                return;
            }
            if (a.model.remoteOn() || a.model.localReady()) {
                sideReply(t); // answered now, beside the job; nothing is added to its plan
                return;
            }
        }
        String payload = text == null ? "" : text.trim();
        if (attached && a.attachments != null && !a.attachments.isEmpty()) {
            String forModel = a.attachments.forModel(payload);
            a.showSentAttachments(payload);
            a.attachments.clear();
            payload = forModel == null ? payload : forModel;
        } else if (!payload.isEmpty()) {
            noteUser(payload);
        }
        if (payload == null || payload.trim().isEmpty()) {
            return;
        }
        a.steer.add(payload.trim());
        a.note("Sent \u2014 I'll read it and keep going");
        a.handler.removeCallbacks(a.releaseUnseenSteer);
        a.handler.postDelayed(a.releaseUnseenSteer, 500);
    }

    /**
     * A message sent while a long job runs. The job is not stopped or slowed: it keeps working in the background, and
     * the message is answered beside it, on the API without the device model's lock. Ibiko reads whether the message
     * is for the job: then it gives \u00A7ST and the words go to the job, which reads them on its next step.
     */
    void sideReply(final String text) {
        noteUser(text);
        final boolean onDevice = !a.model.remoteOn();
        a.note(onDevice ? "The job keeps running. I'll answer you between two of its steps."
                : "The job keeps running. Answering you meanwhile.");
        final String agent = a.stage == null ? "ibiko" : a.stage.agent();
        final String job = a.backgroundTask == null ? "a long job" : a.backgroundTask;
        final String last = a.chatPane == null ? "" : a.chatPane.lastStep();
        final String system = a.systemFor(agent, false)
                + " A job is running in the background right now: " + a.clip(job) + (last.isEmpty() ? "" : " (last step: " + last + ")")
                + ". It keeps going while you answer. The user's new message is a question, a separate request or a remark: answer"
                + " it yourself, briefly (for a question about the job, say what it is doing from the last step). It is not"
                + " added to the job's plan. Only if it clearly tells the job how to go on (stop part of it, do something"
                + " first), write \u00A7ST and that instruction alone on the first line, then one short sentence.";
        final String context = a.chats == null ? "" : a.chats.transcript(onDevice ? 1200 : 4000, onDevice ? 400 : 1500);
        a.hub.worker.execute(() -> {
            String asked = (context.isEmpty() ? "" : "The chat so far:\n" + context + "\n\n") + "The user's new message: " + text;
            // on an API, beside the job; on the device's own model, between two of the job's steps (the model keeps the
            // job's reading in another of its memory slots, so the job carries on where it was)
            String said = onDevice ? a.model.task(system, asked, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs), null)
                    : a.model.remoteTask(system, asked, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs));
            a.handler.post(() -> {
                if (a.isFinishing() || a.chatPane == null) {
                    return;
                }
                ai.ibiko.suit.FunctionCode.Call c = said == null ? null : ai.ibiko.suit.FunctionCode.parse(said);
                if (c != null && c.code == ai.ibiko.suit.FunctionCode.ST) {
                    // for the job, as a note on how to go on: it reads it on its next step, without re-planning
                    a.steer.add(ai.ibiko.agent.Intake.STEER + (c.args.isEmpty() ? text : c.args));
                    a.handler.removeCallbacks(a.releaseUnseenSteer);
                    a.handler.postDelayed(a.releaseUnseenSteer, 500);
                }
                String shown = said == null ? null : ai.ibiko.chat.ReplyText.prepare(ai.ibiko.suit.FunctionCode.withoutCode(said)).trim();
                if (shown == null || shown.isEmpty()) {
                    shown = c != null ? "Passed to the running job." : "I couldn't answer that just now; the job is still running.";
                }
                a.chatPane.assistant(agent, shown);
            });
        });
    }

    /** The end of a long reply, so a continuation can see what was already said without the whole thing. */
    static String tail(String s, int most) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.length() <= most ? s : s.substring(s.length() - most);
    }

    void noteUser(String text) {
        if (a.chats == null || a.chatPane == null || text == null) {
            return;
        }
        String t = text.trim();
        java.util.List<ai.ibiko.chat.ChatBook.Message> msgs = a.chats.current().messages;
        if (!msgs.isEmpty()) {
            ai.ibiko.chat.ChatBook.Message last = msgs.get(msgs.size() - 1);
            if ("user".equals(last.role) && t.equals(last.body.trim())) {
                return;
            }
        }
        if (t.toLowerCase(java.util.Locale.US).startsWith("remember ")) {
            a.chats.remember(t.substring("remember ".length()).trim());
        }
        if (a.chatPane != null) {
            a.chatPane.freezeLive(); // the reply already on screen stays above this message
        }
        a.chats.add("user", "text", t, a.chats.current().persona);
        a.chatPane.nextVoiceTurn(); // the next reply's voice is its own, not the one just kept
        a.chatPane.show();
    }

    String situation(boolean ivie) {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String part = hour < 5 ? "night"
                : hour < 12 ? "morning" : hour < 17 ? "afternoon" : hour < 21 ? "evening" : "night";
        String face = a.stage == null ? "ivie" : a.stage.agent();
        return ai.ibiko.llm.PersonaPrompt.situation(face, a.examiner.lastTopic(),
                a.stage.pictureShown() ? a.lastDrawn : null, part,
                a.connectivity != null && a.connectivity.isOnline())
                + waitingAction()
                + (a.chats == null ? ""
                        : a.blank(a.chats.transcript(700)) + a.blank(a.chats.memoryForPrompt(6)));
    }

    /** For the brain: the action shown waiting for the pilot's yes, so it can read a yes or a no to it (\u00A7YS, \u00A7CX). */
    String waitingAction() {
        PendingAction p = a.stager == null ? null : a.stager.pending(System.currentTimeMillis());
        return p == null ? "" : " An action is shown waiting for the user's yes: " + ai.ibiko.brain.ActionPlanner.question(p)
                + " If their message agrees, give \u00A7YS; if it refuses, \u00A7CX.";
    }

    void reportModel() {
        String line;
        if (a.model.state() == ai.ibiko.llm.LocalModel.State.UNSUPPORTED) {
            line = a.model.problem();
        } else if (!ai.ibiko.llm.NativeLlm.available()) {
            line = "not in this build (the native library didn't load)";
        } else if (a.model.installed()) {
            line = "starting up";
        } else {
            line = "not installed: AI providers in the menu";
        }
        a.stage.activity("MODEL", line);
    }
}
