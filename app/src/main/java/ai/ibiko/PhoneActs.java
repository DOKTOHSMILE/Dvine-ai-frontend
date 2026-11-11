package ai.ibiko;

import ai.ibiko.brain.BrainAnswer;
import ai.ibiko.brain.PendingAction;
import ai.ibiko.turn.LocalTurn;

/** What the brain asked the phone to do (open, share, remind, draw). Not the chat screen. */
final class PhoneActs {
    private final MainActivity a;

    PhoneActs(MainActivity a) {
        this.a = a;
    }

    void run(String agent, String question, ai.ibiko.llm.BrainOrder order) {

        if (order == null) {
            BrainAnswer noted = a.brain.respond(question, agent);
            if (noted != null && noted.answered && a.plain(noted)) {
                a.play(noted.lines, null);
                return;
            }
            if (a.model != null && !a.model.ready() && a.model.state() != ai.ibiko.llm.LocalModel.State.UNSUPPORTED
                    && ai.ibiko.llm.NativeLlm.available()) {
                // no brain to read the words yet: say so, and offer it, instead of a flat refusal
                if (a.model.state() == ai.ibiko.llm.LocalModel.State.FAILED) {
                    a.play(LocalTurn.speakAs(agent, "My model couldn't start: " + a.model.problem() + "."), null);
                } else if (a.model.installed()) {
                    a.play(LocalTurn.speakAs(agent, "My model is still starting. Ask me again in a moment."), null);
                    if (a.model.state() != ai.ibiko.llm.LocalModel.State.LOADING) {
                        a.loadModel(false);
                    }
                } else if (a.model.state() == ai.ibiko.llm.LocalModel.State.DOWNLOADING) {
                    a.play(LocalTurn.speakAs(agent, "My model is still downloading. I'll understand that once it's here."), null);
                } else {
                    a.play(LocalTurn.speakAs(agent, "I need a model to understand that: download one, or add an API key."), null);
                    a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SetupActivity.class));
                }
                return;
            }
            a.play(LocalTurn.speakAs(agent, "I can't carry that out."), null);
            return;
        }
        switch (order.kind) {
            case SAY:
                String said = order.speech.trim();
                a.play(LocalTurn.speakAs(agent, said.isEmpty() ? "I can't carry that out." : said),
                        null);
                return;
            case PHOTO:
                a.lookUpPhotos(order.arg);
                return;
            case UNZIP:
            case FINDZIP:
                String report = ai.ibiko.ops.shell.LocalShell.run(
                        order.kind == ai.ibiko.llm.BrainOrder.Kind.UNZIP ? "unzip the zip file"
                                : "find the zip file",
                        a.shellRoots(), new java.io.File(a.getFilesDir(), "shell"));
                a.play(LocalTurn.speakAs("ibiko",
                        report == null ? "I can't carry out that file task." : report), null);
                return;
            case NAME:
                ai.ibiko.brain.Onboarding.Names names = ai.ibiko.brain.Onboarding
                        .introduce(order.arg);
                if (names == null) {
                    names = ai.ibiko.brain.Onboarding.introduce("call me " + order.arg);
                }
                if (names == null) {
                    a.play(LocalTurn.speakAs(agent, "I heard a name, but I couldn't keep it."), null);
                    return;
                }
                a.saveName(names.called, names.given);
                a.play(LocalTurn.speakAs(agent, names.given.equalsIgnoreCase(names.called)
                        ? "Alright, " + names.called + ". I'll call you that."
                        : "I'll call you " + names.called + ". Your name is " + names.given + "."),
                        null);
                return;
            case GREET:
                a.play(LocalTurn.speakAs(agent, "Hello. What do you want to do?"), null);
                return;
            case TIME:
                String when = new java.text.SimpleDateFormat("h:mm a",
                        java.util.Locale.getDefault()).format(new java.util.Date());
                a.play(LocalTurn.speakAs(agent, "It's " + when + "."), null);
                return;
            case SHELL:
                a.runShell(question, order.arg);
                return;
            case SEARCH:
                a.lookUpAnswer(order.arg);
                return;
            case TOOL:
                a.useConnector(question, order.arg);
                return;
            case SOCIAL:
                a.social(question, order.arg);
                return;
            case IMAGE:
                a.makeImage(agent, order.arg);
                return;
            case CALC: {
                // exact arithmetic by the suit: the model asked, DVINE computes, nothing is guessed
                java.math.BigDecimal v = ai.ibiko.suit.Arithmetic.eval(order.arg);
                a.play(LocalTurn.speakAs(agent, v == null ? "I couldn't work out \u201c" + order.arg + "\u201d."
                        : order.arg + " = " + ai.ibiko.suit.Arithmetic.show(v)), null);
                return;
            }
            case MAKEFILE:
                a.makeDocument("make a " + order.arg + " of that");
                return;
            case STAGE:
                a.stageAction(order.arg);
                return;
            case SOCIALINTEL:
                a.useSocialIntel(question, order.arg);
                return;
            case SCHEDULEPOST:
                a.schedulePost(order.arg);
                return;
            case NEWCHAT:
                a.startNewChatSafely();
                return;
            case SWITCH:
                if (order.arg.matches("ivie|obi|ibiko")) {
                    a.selectPersona(order.arg, "ibiko".equals(order.arg));
                }
                return;
            case HANDOVER:
                a.handOver(agent, question, order.arg);
                return;
            case WORK:
                if (a.modelOn()) {
                    a.runIbiko(question); // real work: the agent organisation takes it
                } else {
                    a.play(LocalTurn.speakAs(agent, "That needs a model, and none is ready."), null);
                }
                return;
            case WRITEPLAN:
                if (a.model != null && a.model.ready() && !"ibiko".equals(agent)) {
                    a.runWritingPlan(agent, question);
                } else {
                    a.askLocal(question, null, false, ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs));
                }
                return;
            case THINKTOGETHER:
                a.thinkTogether(question, agent);
                return;
            case READFILES:
            case CARRYON:
                if (a.modelOn()) {
                    a.runAgent(order.kind == ai.ibiko.llm.BrainOrder.Kind.CARRYON ? "continue" : question, agent, !"ibiko".equals(agent));
                } else {
                    a.play(LocalTurn.speakAs(agent, "That needs a model, and none is ready."), null);
                }
                return;
            case CONFIRM: {
                PendingAction waiting = a.stager.pending(System.currentTimeMillis());
                if (waiting == null) {
                    a.play(LocalTurn.speakAs(agent, "Nothing is waiting for a yes."), null);
                } else {
                    a.doAction(a.stager.take(), false);
                }
                return;
            }
            case CANCEL:
                if (a.stager.pending(System.currentTimeMillis()) != null) {
                    a.stager.take();
                    a.closeStaged();
                    a.play(LocalTurn.speakAs(agent, "Cancelled. Nothing was sent."), null);
                } else {
                    a.play(LocalTurn.speakAs(agent, "Nothing was waiting."), null);
                }
                return;
            case PLANMODE:
                a.command(ai.ibiko.turn.Meaning.Command.MODE_PLAN);
                return;
            case AUTOON:
                a.command(ai.ibiko.turn.Meaning.Command.AUTO_ON);
                return;
            case AUTOOFF:
                a.command(ai.ibiko.turn.Meaning.Command.AUTO_OFF);
                return;
            case RETRY:
                a.retryLast();
                return;
            case SETTINGS:
                a.command(ai.ibiko.turn.Meaning.Command.OPEN_SETTINGS);
                return;
            case CANNOT:
            default:
                String why = order.speech == null || order.speech.isEmpty()
                        ? "That isn't a function I have, so I can't carry it out."
                        : "I can't carry that out. " + order.speech;
                a.play(LocalTurn.speakAs(agent, why), null);
        }
        }
}
