package ai.dvine.llm;

/**
 * What the brain has been given and has written, for the Usage screen: the last call split the way the context
 * window is filled (instructions, conversation, this message, the reply), and today's totals. Tokens are counted
 * by the API when it reports them; otherwise estimated from the text (about four characters a token), and the
 * screen says which. Thread-safe. Pure Java.
 */
public final class UsageLog {
    /** One call to the brain. */
    public static final class Call {
        public final String brain;
        public final int window, system, conversation, message, reply;
        public final boolean measured;
        public final long millis, at;

        Call(String brain, int window, int system, int conversation, int message, int reply, boolean measured, long millis, long at) {
            this.brain = brain;
            this.window = window;
            this.system = system;
            this.conversation = conversation;
            this.message = message;
            this.reply = reply;
            this.measured = measured;
            this.millis = millis;
            this.at = at;
        }

        /** Everything in the window: what was sent and what came back. */
        public int used() {
            return system + conversation + message + reply;
        }

        /** Tokens written a second, or 0. */
        public float speed() {
            return millis <= 0 ? 0f : reply * 1000f / millis;
        }
    }

    private Call last;
    private long day = -1;
    private int calls, tokensIn, tokensOut, failed;
    private long millis;

    /** About four characters of English (or code) a token: an estimate, marked as one. */
    public static int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : Math.max(1, (text.length() + 3) / 4);
    }

    public synchronized void record(Call c) {
        roll(c.at);
        last = c;
        calls++;
        tokensIn += c.system + c.conversation + c.message;
        tokensOut += c.reply;
        millis += c.millis;
    }

    public synchronized void failure(long at) {
        roll(at);
        failed++;
    }

    public synchronized Call last() {
        return last;
    }

    public synchronized int callsToday(long now) {
        roll(now);
        return calls;
    }

    public synchronized int inToday(long now) {
        roll(now);
        return tokensIn;
    }

    public synchronized int outToday(long now) {
        roll(now);
        return tokensOut;
    }

    public synchronized int failedToday(long now) {
        roll(now);
        return failed;
    }

    /** Average tokens written a second today, or 0. */
    public synchronized float speedToday(long now) {
        roll(now);
        return millis <= 0 ? 0f : tokensOut * 1000f / millis;
    }

    /** Today's totals as text, to keep across restarts; {@link #restore} reads it back. */
    public synchronized String save() {
        return day + " " + calls + " " + tokensIn + " " + tokensOut + " " + failed + " " + millis;
    }

    public synchronized void restore(String saved, long now) {
        if (saved == null) {
            return;
        }
        String[] f = saved.trim().split(" ");
        try {
            if (f.length == 6 && Long.parseLong(f[0]) == dayOf(now)) {
                day = Long.parseLong(f[0]);
                calls = Integer.parseInt(f[1]);
                tokensIn = Integer.parseInt(f[2]);
                tokensOut = Integer.parseInt(f[3]);
                failed = Integer.parseInt(f[4]);
                millis = Long.parseLong(f[5]);
            }
        } catch (NumberFormatException e) {
            // a damaged line: start the day from zero
        }
    }

    /** A new day (the phone's own time zone) starts the totals again. */
    private void roll(long now) {
        long d = dayOf(now);
        if (d != day) {
            day = d;
            calls = tokensIn = tokensOut = failed = 0;
            millis = 0;
        }
    }

    static long dayOf(long millis) {
        return Math.floorDiv(millis + java.util.TimeZone.getDefault().getOffset(millis), 86_400_000L);
    }
}
