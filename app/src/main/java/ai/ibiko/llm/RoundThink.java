package ai.ibiko.llm;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Several API models on one question, in the background, then one stronger answer. The selected model analyses.
 * The next proposes a solution from that analysis. Another looks from a different angle. Each then adds what it
 * would change (a higher pass, including the first model asking itself). The selected model writes the answer the
 * user sees. Pure Java; the calls are behind {@link Ask}.
 */
public final class RoundThink {
    /** One model, one prompt. Null when that model failed. */
    public interface Ask {
        String ask(String model, String system, String prompt);
    }

    /** A line for the thinking row as each pass finishes. */
    public interface Steps {
        void step(String line);
    }

    private RoundThink() {
    }

    /** A real question, not a greeting. Tool orders and coding jobs are left to the single call that can act. */
    public static boolean worth(String question) {
        String t = question == null ? "" : question.trim();
        if (t.length() < 8) {
            return false;
        }
        return !t.toLowerCase(Locale.US).matches("(hi|hey|hello|thanks|thank you|ok|okay|yes|no|yo|good morning|good night)[.!]*");
    }

    static final String ANALYSE = "You are the first pass on a question. Analyse it: the goal, what is already known,"
            + " what is uncertain, and the constraints. Not the final answer. Under 180 words. No greeting.";
    static final String SOLVE = "You are the second pass. You are given an analysis. Propose a solution: numbered steps,"
            + " the main idea, and what would show it worked. Do not repeat the analysis. Under 180 words.";
    static final String ANGLE = "You are another pass. Look at the question from a different angle: a simpler way, a risk"
            + " the others may miss, or an idea they did not use. Under 140 words. Do not repeat their steps.";
    static final String HIGHER = "You already have notes on this question. In under 80 words, what would you change if you"
            + " looked again from another perspective? One idea. No greeting.";

    /**
     * Runs the passes. {@code models} is the selected model first, then peers ({@link ModelRing}). {@code mergeSystem}
     * is the persona the final answer is written in (it may include the app's function codes). Null when nothing
     * came back, so the caller can ask the selected model once in the usual way.
     */
    public static String run(Ask ask, List<String> models, String question, String context, String mergeSystem,
            Steps steps, BooleanSupplier stopped) {
        if (ask == null || models == null || models.isEmpty() || question == null || question.trim().isEmpty()) {
            return null;
        }
        String base = (context == null || context.trim().isEmpty() ? "" : context.trim() + "\n\n")
                + "The user asks: " + question.trim();
        String first = models.get(0);
        if (stopped != null && stopped.getAsBoolean()) {
            return null;
        }
        String analysis = clean(ask.ask(first, ANALYSE, base));
        if (analysis.isEmpty() || stopped != null && stopped.getAsBoolean()) {
            return null;
        }
        step(steps, "Analysed with " + first);
        String solver = models.size() > 1 ? models.get(1) : first;
        String solution = clean(ask.ask(solver, SOLVE, base + "\n\nAnalysis:\n" + analysis));
        if (!solution.isEmpty()) {
            step(steps, "A solution from " + solver);
        }
        String angler = models.size() > 2 ? models.get(2) : first;
        String angle = clean(ask.ask(angler, ANGLE, base + "\n\nAnalysis:\n" + analysis
                + (solution.isEmpty() ? "" : "\n\nSolution:\n" + solution)));
        if (!angle.isEmpty()) {
            step(steps, "Another view from " + angler);
        }
        StringBuilder higher = new StringBuilder();
        for (String m : models) {
            if (stopped != null && stopped.getAsBoolean()) {
                return null;
            }
            String note = clean(ask.ask(m, HIGHER, base + "\n\nAnalysis:\n" + analysis
                    + (solution.isEmpty() ? "" : "\n\nSolution:\n" + solution)
                    + (angle.isEmpty() ? "" : "\n\nAnother view:\n" + angle)));
            if (!note.isEmpty()) {
                higher.append(m).append(": ").append(note).append("\n");
            }
        }
        if (higher.length() > 0) {
            step(steps, "A higher pass");
        }
        if (stopped != null && stopped.getAsBoolean()) {
            return null;
        }
        String notes = base + "\n\nAnalysis (" + first + "):\n" + analysis
                + (solution.isEmpty() ? "" : "\n\nSolution (" + solver + "):\n" + solution)
                + (angle.isEmpty() ? "" : "\n\nAnother view (" + angler + "):\n" + angle)
                + (higher.length() == 0 ? "" : "\n\nWhat each would change:\n" + higher);
        String system = (mergeSystem == null ? "" : mergeSystem.trim() + "\n")
                + "Write ONE stronger answer for the user from the notes below. Use the best of them. Where they"
                + " disagree, follow the stronger reasoning and mention the doubt in one line. Plain paragraphs."
                + " Do not say that several models wrote notes, and do not describe the passes.";
        String answer = clean(ask.ask(first, system, notes));
        return answer.isEmpty() ? null : answer;
    }

    private static void step(Steps steps, String line) {
        if (steps != null) {
            steps.step(line);
        }
    }

    private static String clean(String reply) {
        return reply == null ? "" : reply.replaceAll("(?s)<think>.*?(</think>|$)", "").replaceAll("</?final>", "").trim();
    }
}
