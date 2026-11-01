package ai.dvine.llm;

/**
 * Stops a reply that has gone quiet, not one that is still writing.
 * Idle time resets when a token arrives. A hard cap still ends a reply that never finishes.
 */
public final class ReplyWatch {
    private final long idleMs;
    private final long capMs;
    private final long started;
    private volatile long lastTouch;
    private volatile boolean heard;
    private volatile boolean finished;

    public ReplyWatch(long idleMs, long capMs, long nowMs) {
        this.idleMs = idleMs;
        this.capMs = capMs;
        this.started = nowMs;
        this.lastTouch = nowMs;
    }

    public void touch(long nowMs) {
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
        if (nowMs - started >= capMs) {
            return true;
        }
        return heard && nowMs - lastTouch >= idleMs;
    }

    public String reason(long nowMs) {
        if (nowMs - started >= capMs) {
            return "cap";
        }
        if (heard && nowMs - lastTouch >= idleMs) {
            return "idle";
        }
        return "";
    }
}
