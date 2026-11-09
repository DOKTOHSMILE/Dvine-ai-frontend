package ai.dvine.suit;

import java.util.function.BooleanSupplier;

/**
 * Deep thinking for any brain in the suit (an API model, the device's model): the suit makes the same brain think
 * in passes instead of answering in one breath. Understand and plan; draft from the plan; critique the draft as a
 * strict reviewer; revise. Depth 1 is plan + answer, depth 2 adds one critique and revision, depth 3 two. A critique
 * that finds nothing to fix ends the loop early, so an easy question doesn't pay for passes it doesn't need. Every
 * pass is one call, so it is opt-in (the Deep thinking tool of a chat). Pure Java.
 */
public final class DeepThink {
    /** One call to the brain: its instructions and the prompt, the reply back (null when it failed or was stopped). */
    public interface Brain {
        String ask(String system, String prompt);
    }

    /** Each pass as it finishes, for the steps sheet. */
    public interface Steps {
        void pass(String name, String result);
    }

    /** What a critique says when the draft needs no change. */
    static final String SOUND = "SOUND";

    static final String PLAN = "Think before answering. Restate what is really being asked in one line, list what"
            + " matters (facts, constraints, the user's intent, traps), then the steps to answer it. Write only the"
            + " understanding and the plan, not the answer.";
    static final String DRAFT = "Answer the question by following the plan. Be correct first, then clear and complete."
            + " Show working where it helps.";
    static final String CRITIC = "You are a strict reviewer. Check the answer against the question and the plan: wrong"
            + " facts, wrong arithmetic or logic, missing steps, unmet requirements, unclear parts. List each problem"
            + " and how to fix it. If there is nothing to fix, reply exactly " + SOUND + ".";
    static final String REVISE = "Write the final answer: the draft with every problem in the review fixed. Only the"
            + " answer, as it should reach the user.";

    private DeepThink() {
    }

    /**
     * The answer after {@code depth} rounds (1 to 3), or null when the brain gave nothing or {@code stopped} said so.
     * {@code system} is the brain's own instructions (persona, the suit's codes); each pass adds its own role.
     */
    public static String think(Brain brain, String system, String question, int depth, Steps steps, BooleanSupplier stopped) {
        int d = Math.max(1, Math.min(3, depth));
        String base = system == null ? "" : system + "\n\n";
        String plan = call(brain, base + PLAN, "Question:\n" + question, stopped);
        if (plan == null) {
            return null;
        }
        report(steps, "Understood and planned", plan);
        String answer = call(brain, base + DRAFT, "Question:\n" + question + "\n\nPlan:\n" + plan, stopped);
        if (answer == null) {
            return null;
        }
        report(steps, "Drafted", answer);
        for (int round = 1; round < d; round++) {
            String review = call(brain, base + CRITIC, "Question:\n" + question + "\n\nPlan:\n" + plan + "\n\nAnswer:\n" + answer, stopped);
            if (review == null) {
                return answer; // stopped or failed mid-way: the draft so far is still an answer
            }
            if (review.trim().toUpperCase(java.util.Locale.US).startsWith(SOUND)) {
                report(steps, "Checked: nothing to fix", "");
                break;
            }
            report(steps, "Reviewed (round " + round + ")", review);
            String better = call(brain, base + REVISE, "Question:\n" + question + "\n\nDraft:\n" + answer + "\n\nReview:\n" + review, stopped);
            if (better == null) {
                return answer;
            }
            answer = better;
            report(steps, "Revised (round " + round + ")", answer);
        }
        return answer;
    }

    private static String call(Brain brain, String system, String prompt, BooleanSupplier stopped) {
        if (stopped != null && stopped.getAsBoolean()) {
            return null;
        }
        String r = brain.ask(system, prompt);
        return r == null || r.trim().isEmpty() ? null : r.trim();
    }

    private static void report(Steps steps, String name, String result) {
        if (steps != null) {
            steps.pass(name, result);
        }
    }
}
