package ai.ibiko.social;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A post on the pilot's own account, planned for later: once, or repeating (every day, every week, every few hours).
 * The brain writes it with {@code §SP}; the pilot sees the text, the account and the time and says yes before it is
 * kept. When it is due, DVINE brings it back with a notification and hands it to the network's app with the text
 * ready, so the pilot's account posts it. Pure Java.
 *
 * <p>The brain's line: {@code <network> <when> [<repeat>]: <text>}, where {@code when} is {@code yyyy-MM-dd HH:mm},
 * {@code HH:mm} (today, or tomorrow when it has passed) or {@code in 30m} / {@code in 2h}, and {@code repeat} is
 * {@code daily}, {@code weekly}, {@code weekdays} or {@code every 6h}.
 */
public final class PostPlan {
    public enum Repeat { ONCE, DAILY, WEEKLY, WEEKDAYS, HOURS }

    public final String id;
    /** The network's app key (x, facebook, instagram, linkedin, telegram, whatsapp, reddit). */
    public final String network;
    public final String text;
    /** When it is next due, in milliseconds since 1970. */
    public final long at;
    public final Repeat repeat;
    /** For {@link Repeat#HOURS}: how many hours apart. */
    public final int hours;

    public PostPlan(String id, String network, String text, long at, Repeat repeat, int hours) {
        this.id = id;
        this.network = network;
        this.text = text;
        this.at = at;
        this.repeat = repeat;
        this.hours = Math.max(1, hours);
    }

    private static final Pattern LINE = Pattern.compile("(?is)^\\s*(\\S+)\\s+(.+?)\\s*:(?!\\d)\\s*(.+)$"); // "09:30" is a time, not the colon before the text
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{2})");
    private static final Pattern TIME = Pattern.compile("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b");
    private static final Pattern IN = Pattern.compile("(?i)\\bin\\s+(\\d+)\\s*(m|min|mins|minutes?|h|hr|hrs|hours?|d|days?)\\b");
    private static final Pattern EVERY = Pattern.compile("(?i)\\bevery\\s+(\\d+)\\s*(h|hr|hrs|hours?)\\b");

    /** Reads the brain's line; null when it isn't a whole plan (a network, a time and some text). */
    public static PostPlan parse(String line, long now, TimeZone zone) {
        Matcher m = LINE.matcher(line == null ? "" : line);
        if (!m.matches()) {
            return null;
        }
        String net = network(m.group(1));
        String when = m.group(2).trim();
        String text = m.group(3).trim();
        if (net == null || text.isEmpty()) {
            return null;
        }
        Repeat repeat = Repeat.ONCE;
        int hours = 24;
        String w = when.toLowerCase(Locale.US);
        Matcher ev = EVERY.matcher(w);
        if (ev.find()) {
            repeat = Repeat.HOURS;
            hours = Integer.parseInt(ev.group(1));
            w = w.replace(ev.group(), " ");
        } else if (w.matches("(?s).*\\b(weekdays|every weekday)\\b.*")) {
            repeat = Repeat.WEEKDAYS;
        } else if (w.matches("(?s).*\\b(daily|every day|each day)\\b.*")) {
            repeat = Repeat.DAILY;
        } else if (w.matches("(?s).*\\b(weekly|every week|each week)\\b.*")) {
            repeat = Repeat.WEEKLY;
        }
        long at = time(w, now, zone);
        if (at <= 0) {
            if (repeat == Repeat.ONCE) {
                return null; // a single post needs a time
            }
            at = now + 60_000L; // a repeating post with no start time starts now
        }
        if (repeat == Repeat.WEEKDAYS) {
            at = nextWeekday(at, zone);
        }
        return new PostPlan(newId(now, text), net, text, at, repeat, hours);
    }

    /** The time asked for, or 0. A time of day that has passed today means tomorrow. */
    static long time(String w, long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone, Locale.US);
        c.setTimeInMillis(now);
        Matcher in = IN.matcher(w);
        if (in.find()) {
            long n = Long.parseLong(in.group(1));
            char u = in.group(2).toLowerCase(Locale.US).charAt(0);
            return now + n * (u == 'm' ? 60_000L : u == 'h' ? 3_600_000L : 86_400_000L);
        }
        Matcher dt = DATE_TIME.matcher(w);
        if (dt.find()) {
            c.set(Integer.parseInt(dt.group(1)), Integer.parseInt(dt.group(2)) - 1, Integer.parseInt(dt.group(3)),
                    Integer.parseInt(dt.group(4)), Integer.parseInt(dt.group(5)), 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }
        boolean tomorrow = w.contains("tomorrow");
        Matcher t = TIME.matcher(w.replace("tomorrow", " ").replace("today", " "));
        while (t.find()) {
            if (t.group(2) == null && t.group(3) == null) {
                continue; // a bare number is not a time
            }
            int h = Integer.parseInt(t.group(1));
            int min = t.group(2) == null ? 0 : Integer.parseInt(t.group(2));
            if (t.group(3) != null) {
                boolean pm = t.group(3).equalsIgnoreCase("pm");
                h = h % 12 + (pm ? 12 : 0);
            }
            if (h > 23 || min > 59) {
                return 0;
            }
            c.set(Calendar.HOUR_OF_DAY, h);
            c.set(Calendar.MINUTE, min);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (tomorrow || c.getTimeInMillis() <= now) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            return c.getTimeInMillis();
        }
        return 0;
    }

    /** The same plan, due next after {@code after}; null when it was a single post. */
    public PostPlan next(long after, TimeZone zone) {
        if (repeat == Repeat.ONCE) {
            return null;
        }
        Calendar c = Calendar.getInstance(zone, Locale.US);
        c.setTimeInMillis(at);
        do {
            switch (repeat) {
                case HOURS:
                    c.add(Calendar.HOUR_OF_DAY, hours);
                    break;
                case WEEKLY:
                    c.add(Calendar.WEEK_OF_YEAR, 1);
                    break;
                default:
                    c.add(Calendar.DAY_OF_YEAR, 1);
            }
        } while (c.getTimeInMillis() <= after);
        long n = repeat == Repeat.WEEKDAYS ? nextWeekday(c.getTimeInMillis(), zone) : c.getTimeInMillis();
        return new PostPlan(id, network, text, n, repeat, hours);
    }

    static long nextWeekday(long at, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone, Locale.US);
        c.setTimeInMillis(at);
        while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    /** The network's app key, or null when DVINE can't post there. */
    public static String network(String name) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.US).replaceAll("[^a-z]", "");
        switch (n) {
            case "x":
            case "twitter":
                return "x";
            case "facebook":
            case "fb":
                return "facebook";
            case "instagram":
            case "ig":
            case "insta":
                return "instagram";
            case "linkedin":
                return "linkedin";
            case "telegram":
            case "tg":
                return "telegram";
            case "whatsapp":
                return "whatsapp";
            case "reddit":
                return "reddit";
            default:
                return null;
        }
    }

    /** "Every day", "Every 6 hours", "Once". */
    public String repeatWords() {
        switch (repeat) {
            case DAILY: return "Every day";
            case WEEKLY: return "Every week";
            case WEEKDAYS: return "Every weekday";
            case HOURS: return "Every " + hours + (hours == 1 ? " hour" : " hours");
            default: return "Once";
        }
    }

    static String newId(long now, String text) {
        return Long.toString(now, 36) + Integer.toString(Math.abs(text.hashCode()) % 1296, 36);
    }

    /** One line: id, network, at, repeat, hours, text. */
    public String write() {
        return id + "\t" + network + "\t" + at + "\t" + repeat.name() + "\t" + hours + "\t" + text.replace('\n', ' ').replace('\t', ' ');
    }

    public static PostPlan read(String line) {
        String[] p = line == null ? new String[0] : line.split("\t", 6);
        if (p.length < 6) {
            return null;
        }
        try {
            return new PostPlan(p[0], p[1], p[5].replace(' ', '\n'), Long.parseLong(p[2]), Repeat.valueOf(p[3]), Integer.parseInt(p[4]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static List<PostPlan> readAll(String lines) {
        List<PostPlan> out = new ArrayList<PostPlan>();
        for (String l : (lines == null ? "" : lines).split("\n")) {
            PostPlan p = read(l);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public static String writeAll(List<PostPlan> plans) {
        StringBuilder b = new StringBuilder();
        for (PostPlan p : plans) {
            b.append(p.write()).append('\n');
        }
        return b.toString();
    }
}
