package ai.dvine.human.touch;

/**
 * Tells a single tap, repeated taps and a sustained touch apart, and drops
 * duplicates (the same finger reported twice). Pure timing logic: feed it
 * down and up events with the touched region; it says what to report.
 */
public final class TouchClassifier {
    /** Longer than this is a hold. */
    public static final long HOLD_MS = 600L;
    /** A tap on the same part within this is a repeat. */
    public static final long REPEAT_MS = 450L;
    /** Two reports closer than this for the same part are one touch. */
    public static final long DUPLICATE_MS = 60L;

    private long downAt = -1L;
    private int downRegion = -1;
    private boolean holdSent;
    private long lastTapAt = -10_000L;
    private int lastTapRegion = -1;
    private int repeats;
    private long lastReportAt = -10_000L;
    private int lastReportRegion = -1;

    public void down(int region, long now) {
        downAt = now;
        downRegion = region;
        holdSent = false;
    }

    /** While the finger stays down: a HOLD once the threshold passes (reported once). */
    public BodyTouch.Kind held(long now) {
        if (downAt < 0 || holdSent || downRegion < 0 || now - downAt < HOLD_MS) {
            return null;
        }
        holdSent = true;
        return accept(downRegion, now) ? BodyTouch.Kind.HOLD : null;
    }

    /** The finger lifted: TAP or REPEAT (null after a hold, a duplicate, or off the body). */
    public BodyTouch.Kind up(long now) {
        long started = downAt;
        int region = downRegion;
        downAt = -1L;
        if (started < 0 || region < 0 || holdSent) {
            return null;
        }
        if (!accept(region, now)) {
            return null;
        }
        boolean repeat = region == lastTapRegion && now - lastTapAt <= REPEAT_MS;
        repeats = repeat ? repeats + 1 : 1;
        lastTapAt = now;
        lastTapRegion = region;
        return repeat ? BodyTouch.Kind.REPEAT : BodyTouch.Kind.TAP;
    }

    /** How many taps in the current run (1 for a fresh tap). */
    public int count() {
        return repeats;
    }

    public long duration(long now) {
        return downAt < 0 ? 0L : now - downAt;
    }

    private boolean accept(int region, long now) {
        if (region == lastReportRegion && now - lastReportAt < DUPLICATE_MS) {
            return false;
        }
        lastReportRegion = region;
        lastReportAt = now;
        return true;
    }
}
