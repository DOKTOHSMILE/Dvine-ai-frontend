package ai.ibiko.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * How much Ibiko may do on each of the user's accounts, so the network doesn't flag it as a bot: a cap an hour and a
 * day for each kind of action (well under what people do by hand), a gap between actions that varies like a person's,
 * and a pause on an account when its site shows a warning or a check (captcha, "unusual activity"), until the user
 * resumes it. The caps can be made stricter ({@link #scale}), never looser. Thread-safe; pure Java.
 */
public final class SocialLimits {
    public enum Kind {
        READ("searches and pages read", 8_000L),
        POST("posts", 10 * 60_000L),
        COMMENT("comments and replies", 90_000L),
        LIKE("likes", 30_000L),
        FOLLOW("follows", 2 * 60_000L),
        MESSAGE("messages", 2 * 60_000L);

        public final String shown;
        final long gap;

        Kind(String shown, long gap) {
            this.shown = shown;
            this.gap = gap;
        }
    }

    /** Whether an action may go ahead now; if not, how long to wait (-1: not today, or paused) and why. */
    public static final class Verdict {
        public final boolean ok;
        public final long waitMs;
        public final String why;

        Verdict(boolean ok, long waitMs, String why) {
            this.ok = ok;
            this.waitMs = waitMs;
            this.why = why;
        }
    }

    static final long HOUR = 3_600_000L, DAY = 24 * HOUR;

    /** {hour, day} for each kind, per network: about a third of what an active person does by hand. */
    static int[] caps(Platform p, Kind k) {
        int[][] t; // READ, POST, COMMENT, LIKE, FOLLOW, MESSAGE
        switch (p) {
            case FACEBOOK: t = new int[][]{{20, 150}, {2, 8}, {6, 30}, {15, 80}, {5, 20}, {5, 20}}; break;
            case X: t = new int[][]{{30, 250}, {4, 20}, {10, 50}, {20, 150}, {5, 30}, {5, 20}}; break;
            case INSTAGRAM: t = new int[][]{{15, 100}, {1, 3}, {5, 25}, {15, 80}, {5, 25}, {5, 20}}; break;
            default: t = new int[][]{{30, 300}, {1, 5}, {5, 30}, {20, 150}, {5, 30}, {3, 10}}; break;
        }
        return t[k.ordinal()];
    }

    private final Map<String, List<Long>> done = new HashMap<String, List<Long>>();
    private final Map<String, Long> next = new HashMap<String, Long>();
    private final Map<String, String[]> paused = new HashMap<String, String[]>(); // account -> {until, why}
    private final Random random;
    private float scale = 1f;

    public SocialLimits() {
        this(new Random());
    }

    SocialLimits(Random random) {
        this.random = random;
    }

    /** 1 = the normal caps; 0.5 = careful (half). Never above 1. */
    public synchronized void scale(float s) {
        scale = Math.max(0.2f, Math.min(1f, s));
    }

    public synchronized int cap(Platform p, Kind k, boolean day) {
        return Math.max(1, Math.round(caps(p, k)[day ? 1 : 0] * scale));
    }

    public synchronized Verdict check(String account, Platform p, Kind k, long now) {
        String why = pausedWhy(account, now);
        if (why != null) {
            return new Verdict(false, -1, "This " + p.shown + " account is paused: " + why);
        }
        List<Long> times = times(account, k, now);
        int inHour = 0;
        long oldestInHour = Long.MAX_VALUE;
        for (long t : times) {
            if (now - t < HOUR) {
                inHour++;
                oldestInHour = Math.min(oldestInHour, t);
            }
        }
        if (times.size() >= cap(p, k, true)) {
            return new Verdict(false, -1, "Today's limit for " + k.shown + " on " + p.shown + " is reached ("
                    + cap(p, k, true) + "), to keep the account safe.");
        }
        if (inHour >= cap(p, k, false)) {
            long wait = oldestInHour + HOUR - now;
            return new Verdict(false, wait, "This hour's limit for " + k.shown + " on " + p.shown + " is reached ("
                    + cap(p, k, false) + "); next in " + minutes(wait) + ".");
        }
        Long at = next.get(key(account, k));
        if (at != null && at > now) {
            return new Verdict(false, at - now, "Pacing like a person: next in " + seconds(at - now) + ".");
        }
        return new Verdict(true, 0, null);
    }

    /** It was done: counted, and the next one spaced out by the kind's gap plus up to 60% more, at random. */
    public synchronized void record(String account, Kind k, long now) {
        times(account, k, now).add(now);
        long gap = Math.round(k.gap / Math.max(0.2f, scale) * (1 + random.nextFloat() * 0.6f));
        next.put(key(account, k), now + gap);
    }

    public synchronized int used(String account, Kind k, long now) {
        return times(account, k, now).size();
    }

    /** The site showed a warning or a check: no more actions on this account until {@code until} or the user resumes. */
    public synchronized void pause(String account, String why, long until) {
        paused.put(account, new String[]{Long.toString(until), why == null ? "the site showed a warning" : why});
    }

    public synchronized void resume(String account) {
        paused.remove(account);
    }

    /** Why the account is paused, or null. */
    public synchronized String pausedWhy(String account, long now) {
        String[] p = paused.get(account);
        if (p == null) {
            return null;
        }
        if (Long.parseLong(p[0]) <= now) {
            paused.remove(account);
            return null;
        }
        return p[1];
    }

    public synchronized void forget(String account) {
        paused.remove(account);
        for (Kind k : Kind.values()) {
            done.remove(key(account, k));
            next.remove(key(account, k));
        }
    }

    private List<Long> times(String account, Kind k, long now) {
        List<Long> l = done.get(key(account, k));
        if (l == null) {
            l = new ArrayList<Long>();
            done.put(key(account, k), l);
        }
        l.removeIf(t -> now - t >= DAY || t > now + HOUR);
        return l;
    }

    private static String key(String account, Kind k) {
        return account + "|" + k.name();
    }

    /** As text, to keep across restarts; {@link #restore} reads it back. */
    public synchronized String save() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<Long>> e : done.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            b.append("E\t").append(e.getKey());
            for (long t : e.getValue()) {
                b.append('\t').append(t);
            }
            b.append('\n');
        }
        for (Map.Entry<String, Long> e : next.entrySet()) {
            b.append("N\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, String[]> e : paused.entrySet()) {
            b.append("P\t").append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t')
                    .append(e.getValue()[1].replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        return b.toString();
    }

    public synchronized void restore(String saved) {
        if (saved == null) {
            return;
        }
        for (String line : saved.split("\n")) {
            String[] f = line.split("\t");
            try {
                if (f[0].equals("E") && f.length >= 3) {
                    List<Long> l = new ArrayList<Long>();
                    for (int i = 2; i < f.length; i++) {
                        l.add(Long.parseLong(f[i]));
                    }
                    done.put(f[1], l);
                } else if (f[0].equals("N") && f.length == 3) {
                    next.put(f[1], Long.parseLong(f[2]));
                } else if (f[0].equals("P") && f.length == 4) {
                    paused.put(f[1], new String[]{f[2], f[3]});
                }
            } catch (NumberFormatException damaged) {
                // a damaged line is skipped
            }
        }
    }

    static String minutes(long ms) {
        long m = Math.max(1, (ms + 59_999) / 60_000);
        return m + (m == 1 ? " minute" : " minutes");
    }

    static String seconds(long ms) {
        long s = Math.max(1, (ms + 999) / 1000);
        return s < 90 ? s + " s" : minutes(ms);
    }
}
