package ai.ibiko.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ibiko's thought process (M1–M5): not one lookup but several sections of the brain, each working on its own kind of
 * data, whose results are put together into one thought. Each section says what it did, in a line a person can read,
 * so the thinking can be shown on the chat screen while it happens.
 * <ul>
 * <li>M1 Reader: turns the words into codes and finds what is being asked about (the topics).</li>
 * <li>M2 Memory: pulls what recurred about each topic across the answers heard ({@link Concepts}).</li>
 * <li>M3 Weigher: weighs each link by its share of the answers and says how sure it is
 * ({@link ai.ibiko.ibiko1.formulas.Probability}).</li>
 * <li>M4 Critic: drops what is too thin to say (heard too few times, or no link recurring) and names what is missing.</li>
 * <li>M5 Speaker: forms one sentence from what is left, or says it has not heard enough yet.</li>
 * </ul>
 * The sections are re-run on every question, so the same question a week later, after more answers, is thought through
 * again from the new counts. Pure Java.
 */
public final class Mind {
    /** One section's line of the thinking. */
    public static final class Step {
        public final String section, did;

        Step(String section, String did) {
            this.section = section;
            this.did = did;
        }

        /** "Memory: football came with game in 4 of 5 answers" */
        public String line() {
            return section + ": " + did;
        }
    }

    /** The thinking and what came of it. */
    public static final class Thought {
        public final List<Step> steps = new ArrayList<Step>();
        /** The thought in words, or null when there is not enough to say. */
        public String said;
        /** How sure, from 0 to 1. */
        public double sure;
    }

    private final Concepts concepts;

    public Mind(Concepts concepts) {
        this.concepts = concepts;
    }

    public Thought think(String question) {
        Thought t = new Thought();
        // M1 Reader
        Set<String> topics = Concepts.words(question);
        int codes = Shorthand.tokens(question).size();
        if (topics.isEmpty()) {
            t.steps.add(new Step("Reader", "read " + codes + " codes, none of them a subject"));
            return t;
        }
        t.steps.add(new Step("Reader", "read " + codes + " codes; it is about " + String.join(", ", topics)));
        // M2 Memory: the topic heard about most
        String topic = null;
        for (String w : topics) {
            if (topic == null || concepts.seen(w) > concepts.seen(topic)) {
                topic = w;
            }
        }
        int heard = concepts.seen(topic);
        List<Concepts.Link> meaning = concepts.meaning(topic), related = concepts.related(topic);
        if (heard == 0) {
            t.steps.add(new Step("Memory", "nothing heard about " + topic + " yet"));
            return t;
        }
        StringBuilder m = new StringBuilder();
        for (int i = 0; i < Math.min(3, related.size()); i++) {
            Concepts.Link k = related.get(i);
            m.append(m.length() == 0 ? "" : ", ").append(k.word).append(" (").append(k.count).append(" of ").append(heard).append(")");
        }
        t.steps.add(new Step("Memory", heard + (heard == 1 ? " answer" : " answers") + " heard about " + topic
                + (m.length() == 0 ? "; nothing recurring yet" : "; recurring: " + m)));
        // M3 Weigher
        if (!meaning.isEmpty()) {
            Concepts.Link top = meaning.get(0);
            // not certainty from a handful of answers: the share, pulled toward a toss-up while few were heard
            t.sure = (top.count + 1.0) / (heard + 2.0);
            t.steps.add(new Step("Weigher", topic + " → " + top.word + " is "
                    + ai.ibiko.ibiko1.formulas.Probability.words(t.sure) + " (" + Math.round(t.sure * 100) + "%)"));
        }
        // M4 Critic
        String said = concepts.thought(topic);
        if (said == null) {
            t.steps.add(new Step("Critic", heard < 3 ? "too few answers to form a view (" + heard + " of 3)"
                    : "nothing recurs strongly enough to say"));
            return t;
        }
        t.steps.add(new Step("Critic", "enough to say: " + heard + " answers, " + meaning.size()
                + (meaning.size() == 1 ? " link recurs" : " links recur") + "; passing remarks left out"));
        // M5 Speaker
        t.said = said;
        t.steps.add(new Step("Speaker", said));
        return t;
    }
}
