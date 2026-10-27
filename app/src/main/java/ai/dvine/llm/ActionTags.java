package ai.dvine.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes the brain's action tags out of what it writes, as it streams: "[[switch:obi]]" is acted on and never
 * spoken. A tag split across pieces is held back until it is whole; text that only looked like the start of a tag
 * is let through. Only the known personas are accepted. Pure Java.
 */
public final class ActionTags {
    private static final Pattern TAG = Pattern.compile("\\[\\[\\s*switch\\s*:\\s*(ivie|obi|ibiko)\\s*\\]\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY = Pattern.compile("\\[\\[[^\\]]{0,24}\\]\\]");
    private final StringBuilder held = new StringBuilder();
    private String switchTo;

    /** A new piece of the reply: the part safe to speak now (tags removed). */
    public String add(String piece) {
        held.append(piece == null ? "" : piece);
        String s = held.toString();
        Matcher m = TAG.matcher(s);
        while (m.find()) {
            switchTo = m.group(1).toLowerCase(java.util.Locale.US);
        }
        s = ANY.matcher(TAG.matcher(s).replaceAll("")).replaceAll("");
        int open = s.lastIndexOf("[[");
        int lone = s.endsWith("[") ? s.length() - 1 : -1;
        int cut = open >= 0 && s.indexOf("]]", open) < 0 && s.length() - open < 30 ? open : lone;
        held.setLength(0);
        if (cut >= 0) {
            held.append(s.substring(cut));
            return s.substring(0, cut);
        }
        return s;
    }

    /** The end of the reply: whatever was held back (an unfinished tag is dropped). */
    public String flush() {
        String s = held.toString();
        held.setLength(0);
        return s.startsWith("[[") ? "" : s;
    }

    /** The persona the brain asked to bring forward, or null. */
    public String switchTo() {
        return switchTo;
    }

    /** A whole reply without its tags. */
    public static String strip(String reply) {
        return reply == null ? null : ANY.matcher(TAG.matcher(reply).replaceAll("")).replaceAll("").trim();
    }
}
