package ai.ibiko.ibiko1.formulas;

/**
 * How sure Ibiko 1 is of a fact: its strength {@code n} (0 to 5) and when it was last confirmed. Pure arithmetic, so
 * every change to a fact's strength is traceable to one rule here.
 *
 * <pre>
 * C1  first sight              n = 1        (an encyclopedia read counts as 2 sightings: n = 2)
 * C2  seen again               n + 1        capped at 5
 * C3  contradicted             n - 2        dropped at 0
 * C4  answer accepted          n + 1        capped at 5
 * C5  answer corrected         n - 2        dropped at 0
 * C6  30 days unconfirmed      n - 1        dropped at 0
 * C7  answer only from the strongest fact with n >= 2
 * C8  two different answers within 1 of each other: ask "which is right?"
 * </pre>
 */
public final class Confidence {
    private Confidence() {
    }

    public static final int FIRST = 1, CAP = 5, ANSWERS = 2;
    public static final long DECAY_MS = 30L * 24 * 60 * 60 * 1000;

    /** C1: a first sighting's strength; {@code sightings} is 2 for an encyclopedia read. */
    public static int first(int sightings) {
        return clamp(Math.max(FIRST, sightings));
    }

    /** C2 / C4. */
    public static int up(int n) {
        return clamp(n + 1);
    }

    /** C3 / C5: 0 means the fact is dropped. */
    public static int down(int n) {
        return Math.max(0, n - 2);
    }

    /** C6: the strength after decay at {@code now}, given when it was last confirmed. */
    public static int decayed(int n, long confirmed, long now) {
        return now - confirmed > DECAY_MS ? Math.max(0, n - 1) : n;
    }

    /** C7. */
    public static boolean answers(int n) {
        return n >= ANSWERS;
    }

    /** C8: the best and the runner-up are too close to pick. */
    public static boolean tie(int best, int second) {
        return second >= ANSWERS && best - second <= 1;
    }

    static int clamp(int n) {
        return Math.max(0, Math.min(CAP, n));
    }
}
