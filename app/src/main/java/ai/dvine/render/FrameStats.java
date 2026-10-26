package ai.dvine.render;

/** Frame-time monitoring: a ring of recent frame times, average and 95th percentile. */
public final class FrameStats {
    private final long[] ns = new long[120];
    private int n;
    private int next;
    private long last = -1L;
    private long slow;

    /** Call once per drawn frame with a monotonic clock in nanoseconds. */
    public synchronized void frame(long now) {
        if (last >= 0) {
            long d = now - last;
            ns[next] = d;
            next = (next + 1) % ns.length;
            n = Math.min(ns.length, n + 1);
            if (d > 34_000_000L) {
                slow++;
            }
        }
        last = now;
    }

    public synchronized float averageMs() {
        if (n == 0) {
            return 0f;
        }
        long s = 0;
        for (int i = 0; i < n; i++) {
            s += ns[i];
        }
        return s / (float) n / 1e6f;
    }

    public synchronized float p95Ms() {
        if (n == 0) {
            return 0f;
        }
        long[] c = java.util.Arrays.copyOf(ns, n);
        java.util.Arrays.sort(c);
        return c[Math.min(n - 1, (int) (n * 0.95f))] / 1e6f;
    }

    /** Frames that took longer than two vsyncs at 60 Hz. */
    public synchronized long slowFrames() {
        return slow;
    }

    public synchronized String summary() {
        return n == 0 ? "no frames yet" : String.format(java.util.Locale.US, "%.1f ms average, %.1f ms at the 95th percentile, %d slow frames",
                averageMs(), p95Ms(), slow);
    }
}
