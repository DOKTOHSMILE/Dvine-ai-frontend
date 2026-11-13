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

/** A message on its way to a brain: the gates it passes, the system prompt and tools, and the model's answer. Moved out of MainActivity. */
final class BrainGate {
    private final MainActivity a;

    BrainGate(MainActivity a) {
        this.a = a;
    }

    void answer(String text) {
        a.askedAt = System.currentTimeMillis(); // a reply to this may be read out (speakReply)
        if (ai.ibiko.social.SocialPage.cleared(text)) {
            ai.ibiko.social.SocialWeb.resumePaused(a); // the site's check is done: the 24-hour pause comes off
        }
        answer(text, false);
    }

    void askTheBrain(String question) {
        a.boxBefore = a.actedFromBox; // only the very next message can say the brain got it wrong
        a.actedFromBox = null;
        if (!a.isRetry(question)) {
            a.lastAsked = question; // "try again" is never what a retry asks again: that would ask itself forever
        }
        a.brainAsked = question; // as said: "carry on" is read as the agent's own work going on
        String bare = question.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        boolean carryOnWords = bare.matches("^(continue|go on|carry on|again|keep going)$");
        if (!(a.carriesOn(question) && !a.agentWork(a.stage.agent()).isEmpty() && a.model != null && a.model.ready())) {
            if (carryOnWords && a.activeQuestion != null && !a.activeQuestion.equalsIgnoreCase(question)) {
                question = a.activeQuestion;
            } else if (!carryOnWords) {
                a.activeQuestion = question;
            }
        }
        brainGate().handle(question, System.currentTimeMillis());
    }

    /**
     * Every message's way through Ibiko (the one gateway): first the "message" stage (controls, the model's own
     * commands, posts, files, social searches, reading the meaning), then the "brain" stage (Ivie's canvas, the
     * agents, the council, the model). Each route is asked in this order and the first that takes it handles it;
     * which one is recorded ({@link ai.ibiko.gate.BrainGateway#recent}).
     */
    ai.ibiko.gate.BrainGateway messageGate() {
        if (a.messageGate != null) {
            return a.messageGate;
        }
        a.messageGate = new ai.ibiko.gate.BrainGateway("message")
                .route("model command", t -> {
                    if (a.modelOn() || a.apiDirect()) {
                        return false; // a brain is in the suit: it reads the words, nothing else does
                    }
                    a.paused = null; // anything else lets a paused reply go
                    // the one thing that can't wait for the brain: getting the brain ("download your brain", the yes
                    // to it, pause / continue / show the download). Without it there is nothing to read the words.
                    return a.modelCommand(a.control(t));
                })
                // with a brain in the suit no wording runs anything, except a comment on the latest
                // post (that is the social accounts, not a coding job): the brain reads the sizes (\u00A7BD) and the yes or no
                // to an action shown waiting (\u00A7YS, \u00A7CX); the card's own buttons are the pilot's direct control
                .route("post, share or copy", t -> !a.brainReads() && a.stagedAction(t))
                // "latest Facebook post about X and write a comment" is the social accounts, not a coding job
                .route("comment on the latest post", t -> {
                    if (a.backgroundTask != null) {
                        return false;
                    }
                    String[] latest = ai.ibiko.social.PostDrafts.commentOnLatest(t);
                    if (latest == null) {
                        return false;
                    }
                    a.commentOnLatest(latest[0], latest[1], latest[2]);
                    return true;
                })
                // no "make a file", "retry" or "read the meaning" by wording: the brain gives those as codes
                .route("brain", t -> {
                    askTheBrain(t);
                    return true;
                });
        return a.messageGate;
    }

    ai.ibiko.gate.BrainGateway brainGate() {
        if (a.brainGate != null) {
            return a.brainGate;
        }
        a.brainGate = new ai.ibiko.gate.BrainGateway("brain", messageGate())
                .route("agent carries on", t -> {
                    String said = a.brainAsked == null ? t : a.brainAsked;
                    if (a.brainReads()) {
                        return false; // carrying on is the brain's code (\u00A7CO), not the word "continue"
                    }
                    if (!(a.carriesOn(said) && !a.agentWork(a.stage.agent()).isEmpty() && a.model != null && a.model.ready())) {
                        return false;
                    }
                    // Ibiko (or Obi or Ivie reading files) carries on with the same work, everything already read kept
                    a.runAgent(said, a.stage.agent(), !"ibiko".equals(a.stage.agent()));
                    return true;
                })
                .route("who am I", t -> {
                    if (a.brainReads()) {
                        return false;
                    }
                    String who = ai.ibiko.llm.PersonaPrompt.introduce(a.stage.agent(), t);
                    if (who == null) {
                        return false;
                    }
                    a.play(LocalTurn.speakAs(a.stage.agent(), who), null);
                    return true;
                })
                // Ivie's canvas by wording only when no brain is in the suit; a brain gives \u00A7BD and \u00A7DR.
                // Reading the workspace files is the brain's code too (\u00A7RF), never a file name in the message
                .route("attachment", t -> {
                    if (!ai.ibiko.chat.Attachments.has(t) || !a.modelOn()) {
                        return false;
                    }
                    // something is attached: the model reads it with the instructions, and may still give a code
                    // (\u00A7WK when it is work for the agents)
                    a.askLocal(t, null, false, ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs), true);
                    return true;
                })
                // a long piece planned first (\u00A7WP) and work passed to the one it belongs to (\u00A7HO) are the brain's codes
                .route("Ibiko's coding agent", t -> {
                    if (!"ibiko".equals(a.stage.agent()) || !a.modelOn() || a.brainReads()) {
                        return false; // with a brain, Ibiko reads the message first and starts the agents by code (\u00A7WK)
                    }
                    ai.ibiko.llm.BrainOrder known = null; // Ibiko's own brain reads the words: no wording shortcut
                    boolean shortcut = known != null && (known.kind == ai.ibiko.llm.BrainOrder.Kind.UNZIP
                            || known.kind == ai.ibiko.llm.BrainOrder.Kind.FINDZIP || known.kind == ai.ibiko.llm.BrainOrder.Kind.TIME
                            || known.kind == ai.ibiko.llm.BrainOrder.Kind.NAME || known.kind == ai.ibiko.llm.BrainOrder.Kind.SOCIAL
                            || known.kind == ai.ibiko.llm.BrainOrder.Kind.IMAGE);
                    if (shortcut) {
                        return false;
                    }
                    a.runIbiko(t); // Ibiko codes: the agent works on it, step by step
                    return true;
                })
                .route("correct the brain", t -> {
                    // the brain acted from what it learned and the user says that was wrong: it forgets that lesson
                    // and the model is asked properly (it teaches the box afresh)
                    String wrong = a.boxBefore;
                    a.boxBefore = null;
                    if (wrong == null || a.brainReads() || !ai.ibiko.learn.BrainBox.correction(t)) {
                        return false;
                    }
                    a.brainBox().forget(wrong);
                    if (a.chatPane != null) {
                        a.chatPane.activityStep("Forgot that; asking the model instead");
                    }
                    a.askLocal(wrong, null, false, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs), true);
                    return true;
                })
                .route("learned", t -> {
                    // Ibiko's own brain: an action it learned from the models, carried out without asking one.
                    // Only when no brain is in the suit: with one, the brain reads every message (the box keeps learning from it)
                    if (a.brainReads()) {
                        return false;
                    }
                    String learned = a.brainBox().recall(t);
                    ai.ibiko.llm.BrainOrder order = learned == null ? null : ai.ibiko.llm.BrainOrder.leading(learned);
                    if (order == null || order.kind == ai.ibiko.llm.BrainOrder.Kind.SAY
                            || order.kind == ai.ibiko.llm.BrainOrder.Kind.CANNOT) {
                        return false;
                    }
                    if (a.chatPane != null) {
                        a.chatPane.activityStep("Done from what Ibiko learned (no model needed)");
                    }
                    a.actedFromBox = t;
                    a.carryOut(a.stage.agent(), t, order);
                    return true;
                })
                .route("no model", t -> {
                    if (a.modelOn()) {
                        return false;
                    }
                    if (a.ibikoOwn()) {
                        a.ibiko1().endpoint().chat(t); // Ibiko 1, through its endpoint: what it knows, or it researches and asks whether it got it right
                        return true;
                    }
                    if (a.model != null && !a.model.remoteOn() && a.model.installed() && a.prefs.getBoolean("llm_on", true)) {
                        // the device's model is chosen but still starting: the message waits for it, it is not answered
                        // as if there were no brain ("No brain is connected" while the model was loading)
                        a.activity("Starting " + a.model.spec.weights + "\u2026");
                        final String waiting = t;
                        a.hub.worker.execute(() -> {
                            boolean ok = a.model.load(ai.ibiko.chat.Tuning.modelThreads(a.prefs));
                            a.handler.post(() -> {
                                if (a.isFinishing()) {
                                    return;
                                }
                                if (ok && a.model.ready()) {
                                    a.resending = true; // already in the chat: asked again now the model is up
                                    try {
                                        answer(waiting, false);
                                    } finally {
                                        a.resending = false;
                                    }
                                } else {
                                    a.play(LocalTurn.speakAs(a.stage.agent(), a.model.spec.weights + " couldn't start: "
                                            + (a.model.problem() == null ? "not enough memory" : a.model.problem())
                                            + ". Choose a smaller model or an API in the model menu."), null);
                                }
                            });
                        });
                        return true;
                    }
                    // no model in the suit: Ibiko's own Cortex answers what it can (exact sums, greetings, what it learned)
                    String alone = a.cortex().answerAlone(t);
                    if (alone != null) {
                        a.play(LocalTurn.speakAs(a.stage.agent(), alone), null);
                        return true;
                    }
                    a.carryOut(a.stage.agent(), t, null);
                    return true;
                })
                .route("think together", t -> {
                    // the Settings switch sends weighty questions to the three; asked for in words, it is the brain's code (\u00A7TT)
                    if (!a.prefs.getBoolean("think_together", false) || !ai.ibiko.agent.Council.wanted(t, true) || ai.ibiko.llm.TaskSize.mayNeedAction(t)) {
                        return false;
                    }
                    a.thinkTogether(t, a.stage.agent()); // a weighty question: Obi, Ivie and Ibiko work on it, one answer
                    return true;
                })
                .route("full answer", t -> {
                    if (a.brainReads()) {
                        return false; // the one call below answers in full, and may act
                    }
                    if (ai.ibiko.llm.TaskSize.of(t, false) != ai.ibiko.llm.TaskSize.Tier.STRONG || ai.ibiko.llm.TaskSize.mayNeedAction(t)) {
                        return false;
                    }
                    // real work that isn't an order for the phone: straight to the answer, without a routing call first
                    a.askLocal(t, null, false, ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs));
                    return true;
                })
                .route("the model decides", t -> {
                    // one call: the model answers, and gives an order on its first line when it decides Ibiko should
                    // act. Real work gets the long allowance, talk the everyday one
                    boolean big = ai.ibiko.llm.TaskSize.of(t, false) == ai.ibiko.llm.TaskSize.Tier.STRONG;
                    a.askLocal(t, null, false, big ? ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs)
                            : ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs), true);
                    return true;
                });
        return a.brainGate;
    }

    String toolsKey(String persona) {
        // the chat is a group: one set of tools for the conversation, whoever answers
        String group = a.chats == null ? "" : a.chats.current().group;
        return "tools:" + group + ":group";
    }

    /** The suit's codes this page's brain is told about: only the tools switched on, and the suit's own controls. */
    /**
     * The instructions for a reply: in full for an API model; short for the device's own model, which reads every
     * token of them before its first word (the full set is about 2,000 tokens; a small model reads them slowly and
     * cannot use them). The warm-up and the reply use the same text, so what was read ahead is kept.
     */
    String systemFor(String agent, boolean acts) {
        String today = new java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.UK).format(new java.util.Date());
        if (a.model != null && !a.model.remoteOn()) {
            java.util.Set<ai.ibiko.suit.FunctionCode> allowed = a.chatTools(agent).allowedCodes();
            return ai.ibiko.llm.PersonaPrompt.lean(a.brain.userName(), today)
                    + (acts ? ai.ibiko.suit.FunctionCode.lean(a.connectorTools(), allowed) : "");
        }
        return ai.ibiko.llm.PersonaPrompt.system(agent, a.brain.userName(), today, a.selfSheet()) + (acts ? suitCodes(agent) : "");
    }

    String suitCodes(String persona) {
        java.util.Set<ai.ibiko.suit.FunctionCode> allowed = a.chatTools(persona).allowedCodes();
        return ai.ibiko.suit.FunctionCode.instructions(a.connectorTools(), allowed)
                + (allowed.contains(ai.ibiko.suit.FunctionCode.SI)
                ? ai.ibiko.social.intel.SocialCapabilityRegistry.forBrain(a.socialIntel().providers()) + a.ENGAGE : "");
    }

    boolean modelPiece(int id, String text) {
        if (!a.alive(id)) {
            return false;
        }
        if (!a.visible(id)) {
            return true; // its page isn't on screen: it keeps writing, without drawing on this page
        }
        ai.ibiko.llm.ThinkPace pace = a.thinkPace;
        if (pace != null && text != null) {
            a.thinkProgressMs = System.currentTimeMillis();
            if ("\u0001".equals(text)) {
                pace.phase("Thinking");
                if (!a.reasoningShown) {
                    a.reasoningShown = true;
                    a.activity("Reasoning");
                }
            } else {
                if (!a.writingShown) {
                    a.writingShown = true;
                    a.activity("Replying");
                }
                pace.phase(a.thinkWrite);
                pace.addText(text);
            }
            a.handler.post(() -> {
                if (a.alive(id) && a.thinkPace != null) {
                    a.stage.progress(a.thinkPace.line());
                    a.updateThoughtStatus(a.thinkPace.line(), true);
                }
            });
        }
        return true;
    }

    void answer(String text, boolean interpreted) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!interpreted && !a.resending && a.stage != null && (a.groupChat() || a.prefs.getBoolean("handoff", true))) {
            // a task meant for another persona goes to their page before it is shown anywhere: it is sent there, as if
            // typed there, and the page that got it by mistake keeps nothing of it
            String mentioned = ai.ibiko.agent.Handoff.mentioned(text); // "@Ivie draw a cat": the one named answers
            // a persona addressed by name answers. Otherwise, with a brain in the suit, whoever is in the conversation
            // reads the message and passes it on by code when it is another's work (\u00A7HO); only with no brain do the
            // words themselves choose
            a.handedOver = false;
            // one assistant: Ibiko answers everything (talk, research, writing, design, code). Only with no brain at
            // all do the old wording routes still pick among the three
            String owner = "ibiko"; // the one assistant's own key inside the app; it is shown as Ibiko
            if (owner != null && !owner.equals(a.stage.agent())) {
                a.selectPersona(owner);
                if (!a.groupChat()) {
                    a.note("Sent to " + a.labelOf(owner) + ": " + ai.ibiko.agent.Handoff.why(owner) + ".");
                }
            }
        }
        if (a.runningElsewhere()) {
            // this chat is not the one running: show the message here and do not cancel the other chat
            if (!interpreted) {
                a.noteUser(text);
            }
            if (a.chats != null && a.chats.current() != null) {
                a.heldChat.add(new String[] { a.pageKey(), text });
            }
            // said on this page and kept there, not a notice that fades: the message is waiting, not lost
            String busyWith = a.runAgent == null ? "Another chat" : a.labelOf(a.runAgent);
            a.chats.noteHere(a.stage == null ? null : a.stage.agent(), "Waiting: " + busyWith + " is still working. Your message goes out"
                    + " the moment that finishes. Tap Stop to end it sooner.");
            if (a.chatPane != null) {
                a.chatPane.show();
            }
            a.note("Sent. It is answered when " + busyWith + " finishes.");
            return;
        }
        a.replacing = true;
        a.interruptReply();
        a.replacing = false;
        a.runChat = a.chats == null || a.chats.current() == null ? null : a.pageKey();
        a.runAgent = a.stage == null ? null : a.stage.agent();
        if (a.chats != null && a.runAgent != null) {
            a.chats.workOn(a.runAgent); // its replies, notes and steps go to this page, whatever is on screen when they come
        }
        if (a.chatPane != null) {
            a.chatPane.workFor(a.runAgent);
        }
        // a new message ends reading an old reply aloud
        a.readAloudHelper.stop();
        a.fromBrain = interpreted;
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!interpreted && !a.resending) {
            a.noteUser(text);
            final String said = text;
            new Thread(() -> {
                a.pilotProfile().notice(said); // what the pilot says about themselves
                // what the pilot asks about, with any model: learned at night if Ibiko 1 doesn't know it yet
                a.ibiko1().noticeQuestion(said);
                a.ibiko1().hear(said); // Ibiko 1 learns from every message: grammar, shapes, the pilot's word on its answers
            }, "ibiko-profile").start();
        }
        if (!interpreted && a.needsInternet() && (a.connectivity == null || !a.connectivity.isOnline())) {
            a.waitForInternet(text);
            return;
        }
        if (!interpreted && a.commandSession != null && a.assistant != null) {
            ai.ibiko.ops.task.IbikoCommandGateway.VoiceSession session = a.commandSession;
            a.commandSession = null;
            ai.ibiko.ops.Outcome controlled = a.assistant.command(session, text, false);
            if (controlled.handled) {
                if (controlled.speech != null && !controlled.speech.isEmpty()) {
                    a.play(LocalTurn.speakAs(a.stage.agent(), controlled.speech), null);
                }
                return;
            }
        }
        messageGate().handle(text, System.currentTimeMillis()); // every message takes one way through Ibiko
    }
}
