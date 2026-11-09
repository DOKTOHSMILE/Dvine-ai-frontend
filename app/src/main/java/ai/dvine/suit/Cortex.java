package ai.dvine.suit;

import java.util.function.Function;

/**
 * DVINE's own light brain, always present in the suit whatever model is plugged in. It does what a language model
 * does badly or slowly, in microseconds and exactly: it reads the message ({@link Comprehension}) and works out any
 * arithmetic ({@link Arithmetic}), and hands both to the model as facts. With no model at all, it still answers
 * what it can: a sum, a greeting, thanks, or what it learned from earlier conversations ({@code learned}). It never
 * runs a function: functions are the model's to choose, by code. Pure Java.
 */
public final class Cortex {
    /** What it learned from the models' earlier answers (the brain box), or null. */
    private final Function<String, String> learned;

    public Cortex(Function<String, String> learned) {
        this.learned = learned;
    }

    /** What the model is given with the message: DVINE's reading of it and any exact arithmetic. */
    public String context(String message) {
        return Comprehension.of(message).brief();
    }

    /**
     * An answer with no model, or null when it needs one (then DVINE says it has no brain for that yet). Only
     * arithmetic, greetings, thanks and what was learned: nothing is made up.
     */
    public String answerAlone(String message) {
        Comprehension c = Comprehension.of(message);
        if (c.math != null && c.mathValue != null && !c.mathValue.isEmpty()) {
            return c.math + " = " + c.mathValue;
        }
        if (c.act == Comprehension.Act.GREETING) {
            return "Hello. No brain is connected right now: add an AI provider or download an offline model, and I can do much more.";
        }
        if (c.act == Comprehension.Act.THANKS) {
            return "You're welcome.";
        }
        String known = learned == null ? null : learned.apply(message);
        return known == null ? null : "From what I learned earlier: " + known;
    }
}
