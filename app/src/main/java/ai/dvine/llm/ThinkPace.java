package ai.dvine.llm;

/**
 * What the brain is doing right now, in words the screen can show while it works.
 * Seconds come from the clock. A word is counted only after it is finished.
 */
public final class ThinkPace {
    private String phase;
    private final long started;
    private long now;
    private int words;
    private boolean inWord;

    public ThinkPace(String phase, long startedMs) {
        this.phase = phase == null || phase.isEmpty() ? "Reading your words" : phase;
        this.started = startedMs;
        this.now = startedMs;
    }

    public synchronized String phase() {
        return phase;
    }

    public synchronized void phase(String next) {
        if (next == null || next.isEmpty() || next.equals(phase)) {
            return;
        }
        phase = next;
        words = 0;
        inWord = false;
    }

    public synchronized void tick(long nowMs) {
        if (nowMs > now) {
            now = nowMs;
        }
    }

    /** Adds streamed text. A marker that is only a progress ping is ignored. */
    public synchronized void addText(String piece) {
        if (piece == null || piece.isEmpty() || "\u0001".equals(piece)) {
            return;
        }
        for (int i = 0; i < piece.length(); i++) {
            char c = piece.charAt(i);
            if (c == '\u0001') {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                inWord = true;
            } else if (inWord) {
                words++;
                inWord = false;
            }
        }
    }

    public synchronized String line() {
        long sec = Math.max(0L, (now - started) / 1000L);
        if (words > 0) {
            return phase + " · " + sec + "s · " + words + (words == 1 ? " word" : " words");
        }
        return phase + " · " + sec + "s";
    }
}
