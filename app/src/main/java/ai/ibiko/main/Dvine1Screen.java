package ai.ibiko.main;

import java.util.List;

import ai.ibiko.dvine1.Dvine1;

/**
 * Plugs DVINE 1 (the dvine1 folder) into the chat screen: its steps and answers go to the chat, its check and cancel
 * to the reply, its sums to the Cortex's exact arithmetic, what it learns from other models to the brain box.
 */
public final class Dvine1Screen implements Dvine1.Host {
    /** The few parts of the screen DVINE 1 is given. */
    public interface Parts {
        String persona();

        ai.ibiko.stage.ChatPane chat();

        void say(String persona, String text);

        boolean online();

        ai.ibiko.learn.BrainBox brainBox();

        List<String> aboutPilot();

        void answered(Dvine1.How how);

        void note(String text);

        android.os.Handler handler();

        boolean allows(String code);

        void act(String message, String codeLine);
    }

    @Override
    public boolean allows(String code) {
        return parts.allows(code);
    }

    @Override
    public void act(String message, String codeLine) {
        parts.act(message, codeLine);
    }

    private final android.app.Activity activity;
    private final Parts parts;

    public Dvine1Screen(android.app.Activity activity, Parts parts) {
        this.activity = activity;
        this.parts = parts;
    }

    @Override
    public String persona() {
        return parts.persona();
    }

    @Override
    public void step(String step) {
        if (parts.chat() != null) {
            parts.chat().activityStep(step);
        }
    }

    @Override
    public void stepsDone() {
        if (parts.chat() != null) {
            parts.chat().activityEnd();
        }
    }

    @Override
    public void say(String persona, String text) {
        parts.say(persona, text);
    }

    @Override
    public void askRating(String question, String answer) {
        if (parts.chat() != null) {
            parts.chat().askRating(question, answer);
        }
    }

    @Override
    public void noRating(String text) {
        if (parts.chat() != null) {
            parts.chat().noRating(text);
        }
    }

    @Override
    public boolean ratingWaiting() {
        return parts.chat() != null && parts.chat().ratingWaiting();
    }

    @Override
    public void clearRating() {
        if (parts.chat() != null) {
            parts.chat().clearRating();
        }
    }

    @Override
    public boolean online() {
        return parts.online();
    }

    @Override
    public String sum(String message) {
        ai.ibiko.suit.Comprehension c = ai.ibiko.suit.Comprehension.of(message);
        return c.math != null && c.mathValue != null && !c.mathValue.isEmpty() ? c.math + " = " + c.mathValue : null;
    }

    @Override
    public String recallTalk(String question) {
        return parts.brainBox().recallTalk(question);
    }

    @Override
    public void learnTalk(String question, String answer) {
        parts.brainBox().learnTalk(question, answer);
    }

    @Override
    public void forgetTalk(String question) {
        parts.brainBox().forgetTalk(question);
    }

    @Override
    public int talkSize() {
        return parts.brainBox().talkSize();
    }

    @Override
    public int shapes() {
        return parts.brainBox().templateCount();
    }

    @Override
    public List<String> aboutPilot() {
        return parts.aboutPilot();
    }

    @Override
    public void answered(Dvine1.How how) {
        parts.answered(how);
    }

    @Override
    public void note(String text) {
        parts.note(text);
    }

    @Override
    public void later(Runnable r, long ms) {
        parts.handler().postDelayed(r, ms);
    }

    @Override
    public void onScreen(Runnable r) {
        activity.runOnUiThread(r);
    }

    @Override
    public android.content.Context context() {
        return activity;
    }
}
