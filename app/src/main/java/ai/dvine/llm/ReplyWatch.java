package ai.dvine.llm;

/**
 * Stops a reply that has gone quiet, not one that is still writing.
 * Idle time resets when a token arrives. A hard cap still ends a reply that never finishes.
 */
public final class ReplyWatch {
    private final long idleMs;
    private final long capMs;
    /** How long the model may read the question before its first word (a long prompt on a phone takes minutes). */
    private final long readMs;
    private final long started;
    private volatile long lastTouch, firstHeard;
    private volatile boolean heard;
    private volatile boolean finished;

    public ReplyWatch(long idleMs, long capMs, long nowMs) {
        this(idleMs, capMs, capMs, nowMs);
    }

    /**
     * {@code capMs} counts from the first word: the time spent reading the question (up to {@code readMs}) isn't
     * writing, and a big model on a phone can spend minutes reading a long one before it says anything.
     */
    public ReplyWatch(long idleMs, long capMs, long readMs, long nowMs) {
        this.idleMs = idleMs;
        this.capMs = capMs;
        this.readMs = readMs;
        this.started = nowMs;
        this.lastTouch = nowMs;
    }

    public void touch(long nowMs) {
        if (!heard) {
            firstHeard = nowMs;
        }
        heard = true;
        if (nowMs > lastTouch) {
            lastTouch = nowMs;
        }
    }

    public void finish() {
        finished = true;
    }

    public boolean finished() {
        return finished;
    }

    /** True once tokens have stalled, or the hard cap has passed. Waiting for the first token is not a stall. */
    public boolean expired(long nowMs) {
        if (finished) {
            return false;
        }
        if (!heard) {
            return nowMs - started >= readMs; // still reading the question
        }
        if (nowMs - firstHeard >= capMs) {
            return true;
        }
        return nowMs - lastTouch >= idleMs;
    }

    public String reason(long nowMs) {
        if (!heard && nowMs - started >= readMs) {
            return "reading";
        }
        if (heard && nowMs - firstHeard >= capMs) {
            return "cap";
        }
        if (heard && nowMs - lastTouch >= idleMs) {
            return "idle";
        }
        return "";
    }
}
