package ai.ibiko.ibiko1.formulas;

import java.util.Locale;

/**
 * Ibiko 1's learning score, 0 to 100, from four things it can count:
 *
 * <pre>
 * A  accuracy        (correct + 1) / (answered + 2)                    Laplace-smoothed: 50% before any answer
 * K  knowledge       strong / (strong + 200)                           facts with n >= 2
 * R  reading rules   promoted / (promoted + 20)                        sentence shapes it learned to read
 * U  understanding   sure words / (words read + 1)                     words whose part of speech it was sure of
 *
 * score = 100 * (0.45 A + 0.25 K + 0.15 R + 0.15 U)
 * </pre>
 */
public final class Score {
    private Score() {
    }

    public static double accuracy(int correct, int answered) {
        return (correct + 1.0) / (answered + 2.0);
    }

    public static double knowledge(int strong) {
        return strong <= 0 ? 0 : strong / (strong + 200.0);
    }

    public static double rules(int promoted) {
        return promoted <= 0 ? 0 : promoted / (promoted + 20.0);
    }

    public static double understanding(long sure, long words) {
        return words <= 0 ? 0 : Math.min(1, sure / (words + 1.0));
    }

    public static int of(int correct, int answered, int strong, int promoted, long sure, long words) {
        double s = 100 * (0.45 * accuracy(correct, answered) + 0.25 * knowledge(strong) + 0.15 * rules(promoted)
                + 0.15 * understanding(sure, words));
        return (int) Math.round(Math.max(0, Math.min(100, s)));
    }

    /** "Learning score 41 · 12 of 15 answers right · 230 sure facts · 3 reading rules · 88% of words understood". */
    public static String said(int correct, int answered, int strong, int promoted, long sure, long words) {
        return String.format(Locale.US, "Learning score %d · %d of %d %s right · %d sure %s · %d reading %s · %d%% of words understood",
                of(correct, answered, strong, promoted, sure, words), correct, answered, answered == 1 ? "answer" : "answers", strong,
                strong == 1 ? "fact" : "facts", promoted, promoted == 1 ? "rule" : "rules",
                Math.round(100 * understanding(sure, words)));
    }
}
