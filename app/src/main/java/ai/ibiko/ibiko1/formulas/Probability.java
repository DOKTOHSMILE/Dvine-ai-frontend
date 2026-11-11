package ai.ibiko.ibiko1.formulas;

import java.util.Locale;

/**
 * Thinking in probabilities, for every brain Ibiko uses (P1–P10). A decision is not "is it true?" but "how likely, and
 * what does each outcome cost or gain?": name the possibilities, start from how often each happens (the base rate),
 * move the odds with each piece of evidence by how much more likely it is under one than another, then choose the
 * action with the best expected outcome, keeping the cost of being wrong in view. Say how sure you are in words that
 * match the number, and look for what would change your mind.
 * <ul>
 * <li>P1 Possibilities: list the answers or outcomes that could be the case (at least two; "something else" counts).</li>
 * <li>P2 Base rate: before the details, how often is each one the case in general? Start there, not at 50/50.</li>
 * <li>P3 Evidence: for each clue, how much more likely is it if A than if B (the likelihood ratio)? Multiply the odds
 * by it ({@link #update}). A clue equally likely either way changes nothing, however vivid.</li>
 * <li>P4 Independence: clues that come from the same source are one clue, not several ({@link #combine} only for
 * independent ones).</li>
 * <li>P5 Expected value: for each action, the sum over outcomes of probability times what it gains or costs
 * ({@link #expected}). Choose the best; between near-equals, the one that is cheaper to undo.</li>
 * <li>P6 Cost of being wrong: when a mistake would be costly or can't be undone, demand more certainty first, or take
 * the step that gathers evidence (read, test, ask) before the step that commits.</li>
 * <li>P7 Value of a check: if a quick check could flip the decision, do the check first; if no result would change
 * it, don't spend time on it.</li>
 * <li>P8 Calibration: say how sure you are in words that match the chance ({@link #words}): "almost certainly" only
 * above 95%, "likely" for 65–85%, "a toss-up" near 50%. Never certainty you don't have.</li>
 * <li>P9 Chains: a plan that needs several things to go right is as likely as their product (three steps at 90% are
 * 73%, not 90%); look for the weakest link ({@link #all}).</li>
 * <li>P10 Update: when new evidence comes, move the estimate by it; don't hold the old answer for its own sake, and
 * don't swing on one weak clue.</li>
 * </ul>
 * Pure Java.
 */
public final class Probability {
    private Probability() {
    }

    /** Probability to odds: 0.75 → 3 (3 to 1). */
    public static double odds(double p) {
        double q = Math.max(1e-9, Math.min(1 - 1e-9, p));
        return q / (1 - q);
    }

    /** Odds to probability: 3 → 0.75. */
    public static double prob(double odds) {
        return odds / (1 + odds);
    }

    /** P3 (Bayes, in odds): the chance after a clue that is {@code ratio} times as likely if it is so as if not. */
    public static double update(double prior, double ratio) {
        return prob(odds(prior) * Math.max(0, ratio));
    }

    /** P4: several independent clues, each with its likelihood ratio, applied one after another. */
    public static double combine(double prior, double... ratios) {
        double o = odds(prior);
        for (double r : ratios) {
            o *= Math.max(0, r);
        }
        return prob(o);
    }

    /** P5: the expected value of an action, {@code probs[i]} chance of an outcome worth {@code values[i]}. */
    public static double expected(double[] probs, double[] values) {
        double e = 0;
        for (int i = 0; i < Math.min(probs.length, values.length); i++) {
            e += probs[i] * values[i];
        }
        return e;
    }

    /** P9: the chance that every one of several independent steps goes right. */
    public static double all(double... ps) {
        double p = 1;
        for (double x : ps) {
            p *= Math.max(0, Math.min(1, x));
        }
        return p;
    }

    /** The chance that at least one of several independent things happens. */
    public static double any(double... ps) {
        double none = 1;
        for (double x : ps) {
            none *= 1 - Math.max(0, Math.min(1, x));
        }
        return 1 - none;
    }

    /** P6: whether to act now: the chance of being right beats the bar the cost of a mistake sets. */
    public static boolean actNow(double pRight, double gainIfRight, double lossIfWrong) {
        return pRight * gainIfRight - (1 - pRight) * Math.abs(lossIfWrong) > 0;
    }

    /** P8: words that match a chance. */
    public static String words(double p) {
        if (p >= 0.95) {
            return "almost certainly";
        }
        if (p >= 0.85) {
            return "very likely";
        }
        if (p >= 0.65) {
            return "likely";
        }
        if (p >= 0.55) {
            return "somewhat more likely than not";
        }
        if (p > 0.45) {
            return "a toss-up";
        }
        if (p > 0.35) {
            return "somewhat less likely than not";
        }
        if (p > 0.15) {
            return "unlikely";
        }
        if (p > 0.05) {
            return "very unlikely";
        }
        return "almost certainly not";
    }

    /** A decision, a choice, a risk or a chance: where thinking in probabilities helps most. */
    public static boolean applies(String request) {
        String t = request == null ? "" : request.toLowerCase(Locale.US);
        return t.matches("(?s).*\\b(should i|should we|which (one|is better|should)|best (way|option|choice)|choose|decide"
                + "|decision|worth it|chance|chances|likely|likelihood|probab\\w*|odds|risk\\w*|bet|gamble|predict\\w*"
                + "|forecast|will it|is it safe|pros and cons|trade-?off|or should|either|versus|vs\\.?)\\b.*");
    }

    /** The formula, for any brain's instructions with a request it fits. */
    public static final String BRIEF = "\nTHINK IN PROBABILITIES (P1–P10) for this one: list the possibilities (P1); start"
            + " from how often each is the case in general (P2); move the odds by each clue, by how much more likely it is"
            + " under one than another, counting clues from one source once (P3, P4); for each choice weigh chance times"
            + " gain or cost, and pick the best, the cheaper-to-undo between near-equals (P5); demand more certainty when a"
            + " mistake is costly, or check first (P6, P7); a plan's chance is the product of its steps' chances (P9); say"
            + " how sure you are in words that match the chance (\"likely\" ~70%, \"almost certainly\" >95%) (P8); and say"
            + " what would change your mind (P10).\n";

    /** The same, short, for the agent's every step. */
    public static final String AGENT = "\nTHINK IN PROBABILITIES: before a step, ask which outcomes are likely and how"
            + " likely; take the step with the best expected result; when being wrong is costly or can't be undone, check"
            + " first (read, test) before you commit; a plan of several steps is only as likely as all of them together;"
            + " say how sure you are in words that match.\n";
}
