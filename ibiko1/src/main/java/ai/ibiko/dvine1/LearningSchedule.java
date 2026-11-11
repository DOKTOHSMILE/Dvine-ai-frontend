package ai.ibiko.dvine1;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Night learning for DVINE 1: while the pilot sleeps (a window they set, e.g. 23:00 to 06:00), DVINE goes online and
 * learns only from how it was used: first the questions it couldn't answer or got wrong and what the pilot asked
 * about, then what the pilot is into (their profile). Each is read from its encyclopedia summary and kept as learned-but-unchecked, so in
 * the morning it can answer more, and still asks "accurate?" until the pilot confirms. Pure Java: the plan, the
 * window and the topics; the device's job scheduler runs it.
 */
public final class LearningSchedule {
    /** The pilot's settings. Times are minutes after midnight. */
    public static final class Plan {
        public final boolean on;
        public final int start, end;
        public final boolean wifiOnly, chargingOnly;
        /** Most topics in one night. */
        public final int most;

        public Plan(boolean on, int start, int end, boolean wifiOnly, boolean chargingOnly, int most) {
            this.on = on;
            this.start = ((start % 1440) + 1440) % 1440;
            this.end = ((end % 1440) + 1440) % 1440;
            this.wifiOnly = wifiOnly;
            this.chargingOnly = chargingOnly;
            this.most = Math.max(1, most);
        }

        /** Off, 23:00 to 06:00, Wi-Fi only, charging only, 40 topics. */
        public static Plan defaults() {
            return new Plan(false, 23 * 60, 6 * 60, true, true, 40);
        }

        /** True when {@code now} is inside the window (which may cross midnight). */
        public boolean inWindow(long now, TimeZone zone) {
            int m = minuteOfDay(now, zone);
            return start <= end ? m >= start && m < end : m >= start || m < end;
        }

        /** Milliseconds from {@code now} until the window next opens (0 when it is open now). */
        public long untilStart(long now, TimeZone zone) {
            if (inWindow(now, zone)) {
                return 0;
            }
            int m = minuteOfDay(now, zone);
            int wait = (start - m + 1440) % 1440;
            Calendar c = Calendar.getInstance(zone, Locale.US);
            c.setTimeInMillis(now);
            long intoMinute = c.get(Calendar.SECOND) * 1000L + c.get(Calendar.MILLISECOND);
            return wait * 60_000L - intoMinute;
        }

        /** Milliseconds from {@code now} until the window closes (0 when it is shut). */
        public long untilEnd(long now, TimeZone zone) {
            if (!inWindow(now, zone)) {
                return 0;
            }
            int m = minuteOfDay(now, zone);
            return ((end - m + 1440) % 1440) * 60_000L;
        }

        public static String clock(int minutes) {
            return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
        }

        /** "23:00 to 06:00, on Wi-Fi while charging". */
        public String words() {
            return clock(start) + " to " + clock(end) + (wifiOnly && chargingOnly ? ", on Wi-Fi while charging"
                    : wifiOnly ? ", on Wi-Fi" : chargingOnly ? ", while charging" : "");
        }

        public String write() {
            return (on ? 1 : 0) + "," + start + "," + end + "," + (wifiOnly ? 1 : 0) + "," + (chargingOnly ? 1 : 0) + "," + most;
        }

        public static Plan read(String line) {
            try {
                String[] p = line.split(",");
                return new Plan("1".equals(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), "1".equals(p[3]),
                        "1".equals(p[4]), Integer.parseInt(p[5]));
            } catch (RuntimeException e) {
                return defaults();
            }
        }
    }

    static int minuteOfDay(long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone, Locale.US);
        c.setTimeInMillis(now);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /**
     * Tonight's topics, in order and without repeats: what the pilot asked that DVINE couldn't answer, then the
     * pilot's interests; never one it already knows ({@code known}), at most {@code most}.
     */
    public static List<String> topics(List<String> unanswered, List<String> interests,
            java.util.function.Predicate<String> known, int most) {
        Set<String> seen = new LinkedHashSet<String>();
        List<String> out = new ArrayList<String>();
        for (List<String> source : java.util.Arrays.asList(unanswered, interests)) {
            for (String t : source == null ? new ArrayList<String>() : source) {
                String topic = t == null ? "" : t.trim().replaceAll("\\s+", " ");
                String key = topic.toLowerCase(Locale.US);
                if (topic.length() < 2 || topic.length() > 80 || !seen.add(key) || known != null && known.test(topic)) {
                    continue;
                }
                out.add(topic);
                if (out.size() >= most) {
                    return out;
                }
            }
        }
        return out;
    }

    /** The question a topic is learned for: "Photosynthesis" → "What is Photosynthesis?". */
    public static String question(String topic) {
        String t = topic.trim();
        return t.matches("(?i)^(what|who|where|when|why|how)\\b.*") ? t : "What is " + t + "?";
    }
}
