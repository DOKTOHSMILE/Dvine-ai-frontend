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

/** Answers that take more than one look: thinking together, the fact check and looking an answer up. Moved out of MainActivity. */
final class ThinkTurns {
    private final MainActivity a;

    ThinkTurns(MainActivity a) {
        this.a = a;
    }

    /**
     * The three think tanks on one question ({@link ai.ibiko.agent.Council}): Obi's structure, Ivie's review, one more
     * round if she disagrees, Ibiko's practical steps, then one answer from the persona on screen. Each tank's view is
     * a step a tap opens; the answer is remembered in the conversation like any other.
     */
    void thinkTogether(final String question, final String speaker) {
        final int id = a.nextAsk();
        a.stopPlay();
        a.stage.setMode(StageMode.THINKING);
        a.armThink("Thinking together", "Writing one answer");
        final String context = a.chats == null ? "" : a.chats.transcript(a.model.remoteOn() ? 6000 : 1500, a.model.remoteOn() ? 1500 : 300);
        a.hub.worker.execute(() -> {
            ai.ibiko.llm.ReplyService.hold(a);
            final String council = ai.ibiko.agent.AgentBoard.start("Ibiko \u00B7 council", question, a.brainLabel());
            ai.ibiko.agent.Council.Result r;
            try {
                r = ai.ibiko.agent.Council.run((system, prompt) -> a.model.task(system, prompt,
                        ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs) * 2, piece -> a.modelPiece(id, piece) && a.alive(id)),
                        question, context, speaker, (line, who, said) -> {
                            String detail = ai.ibiko.agent.StepDetail.save(a.getFilesDir(), who + "'s view",
                                    java.util.Arrays.asList(said.split("\n")), -1, -1);
                            a.handler.post(() -> {
                                if (a.alive(id) && a.chatPane != null) {
                                    a.chatPane.activityStep(line + ai.ibiko.agent.StepDetail.mark(detail));
                                }
                            });
                        }, () -> !a.alive(id));
            } finally {
                ai.ibiko.llm.ReplyService.release(a);
                ai.ibiko.agent.AgentBoard.end(council);
            }
            final ai.ibiko.agent.Council.Result done = r;
            a.handler.post(() -> {
                if (!a.alive(id) || a.isFinishing()) {
                    return;
                }
                a.endThink();
                if (done == null) {
                    // the council couldn't finish (the model failed): the usual single answer instead
                    a.askLocal(question, null, false, ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs));
                    return;
                }
                a.model.note(question, done.answer);
                a.play(LocalTurn.speakAs(speaker, done.answer), null);
                if (ai.ibiko.agent.Council.building(question) && !"ibiko".equals(speaker) && a.chats != null && a.chatPane != null) {
                    // something to build: Ibiko is offered the agreed plan to carry out, in a chat of his own
                    String plan = question + "\n\nThe council's plan (planned, then checked):\n" + done.obi
                            + "\n\nIvie's real-world and UI/UX notes:\n" + done.ivie
                            + (done.ibiko.isEmpty() ? "" : "\n\nHow you said you'd build it:\n" + done.ibiko);
                    a.chatPane.postDelayed(() -> {
                        a.chats.add("assistant", "handoff", "ibiko\n" + plan, speaker);
                        a.chatPane.show();
                    }, 400);
                }
            });
        });
    }

    /**
     * The fact check of a factual answer (online, Settings › Chat): its claims are looked up and judged in the
     * background after the answer is shown; a contradicted claim posts a correction with its source. Nothing is added
     * when the answer holds up.
     */
    void factCheck(final int id, final String persona, final String question, final String answer) {
        // G8 (formulas/Truth): every reply asked "true or false?" against what is known for certain, on the device
        if (answer != null && !answer.trim().isEmpty()) {
            a.hub.worker.execute(() -> {
                final String own = a.ibiko1().questionReply(answer);
                if (own != null && a.alive(id)) {
                    a.handler.post(() -> {
                        if (a.chatPane != null && !a.isFinishing()) {
                            a.chatPane.assistant(persona, own);
                        }
                    });
                }
            });
        }
        if (!a.prefs.getBoolean("fact_check", false) || a.connectivity == null || !a.connectivity.isOnline() || a.model == null
                || !ai.ibiko.llm.FactCheck.worth(question, answer)) {
            return;
        }
        final ai.ibiko.connect.ConnectorStore web = new ai.ibiko.connect.ConnectorStore(a);
        a.hub.worker.execute(() -> {
            if (!a.alive(id)) {
                return; // a new question came first
            }
            final String checker = ai.ibiko.agent.AgentBoard.start("Fact checker", question, a.brainLabel());
            ai.ibiko.llm.FactCheck.Result r;
            try {
                // the web first; with signed-in social accounts (and the setting on), what people post on one of them
                final java.util.List<ai.ibiko.social.Platform> social = a.prefs.getBoolean("social_factcheck", true)
                        ? ai.ibiko.social.SocialWeb.accounts(a).ready() : new java.util.ArrayList<ai.ibiko.social.Platform>();
                r = ai.ibiko.llm.FactCheck.run((system, prompt) -> a.model.task(system, prompt, 300, null), claim -> {
                    String found = web.research(claim, 1, 3000);
                    if (social.isEmpty() || !a.alive(id)) {
                        return found;
                    }
                    String posts = ai.ibiko.social.SocialWeb.search(a, social.get(0), claim, 1500);
                    return (found == null ? "" : found + "\n\n") + posts;
                }, question, answer, () -> !a.alive(id));
            } finally {
                ai.ibiko.agent.AgentBoard.end(checker);
            }
            final String correction = r.correction();
            if (correction == null) {
                return;
            }
            a.handler.post(() -> {
                if (a.chatPane != null && !a.isFinishing()) {
                    a.chatPane.assistant(persona, correction);
                }
            });
        });
    }

    void lookUpAnswer(final String question) {
        final int id = a.nextAsk();
        final String agent = a.stage.agent();
        boolean online = a.connectivity != null && a.connectivity.isOnline();
        if (!online) {
            BrainAnswer noted = a.brain.respond(question, agent);
            if (noted != null && noted.answered && a.plain(noted)) {
                a.play(noted.lines, null);
            } else {
                a.play(LocalTurn.speakAs(agent, "I need the network to look that up, and I don't have it in my notes."), null);
            }
            return;
        }
        if (a.model != null && a.model.ready() && a.prefs.getBoolean("llm_on", true)) {
            // a model is here: it searches, reads the pages and writes the answer with its sources
            a.askLocal(question, null, true, a.model.remoteOn() ? ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs) : 300);
            return;
        }
        a.stopPlay();
        a.armThink("Looking it up", "Looking it up");
        a.hub.worker.execute(() -> {
            final String found = ai.ibiko.ops.mind.CitedReference.collect(question, url -> {
                byte[] body = ai.ibiko.net.WebGet.get(url, 8000, 400_000);
                if (body == null) {
                    return null;
                }
                try {
                    return new String(body, "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    return null;
                }
            }, System.currentTimeMillis());
            a.handler.post(() -> {
                if (!a.alive(id) || a.isFinishing()) {
                    return;
                }
                a.endThink();
                if (found != null && !found.trim().isEmpty()) {
                    a.play(LocalTurn.speakAs(agent, found), null);
                    return;
                }
                BrainAnswer noted = a.brain.respond(question, agent);
                if (noted != null && noted.answered && a.plain(noted)) {
                    a.play(noted.lines, null);
                } else {
                    a.play(LocalTurn.speakAs(agent,
                            "I looked that up and don't have a source I can stand behind, so I won't guess."), null);
                }
            });
        });
    }
}
