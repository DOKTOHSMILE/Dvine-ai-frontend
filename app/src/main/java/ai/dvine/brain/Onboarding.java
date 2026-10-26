package ai.dvine.brain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** First-run name capture. "my name is Valentine", "I'm Val", or just "Valentine". */
public final class Onboarding {
    private static final Pattern SAYS = Pattern.compile(
            "^(?:hi|hello|hey)?[, ]*(?:my name is|my name's|i am|i'm|im|call me|it's|it is|they call me|name's)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RENAME = Pattern.compile("^(?:please )?call me (.+)$", Pattern.CASE_INSENSITIVE);

    private Onboarding() {
    }

    /** Returns a tidy name, or null if the reply doesn't look like one. */
    public static String parseName(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replaceAll("[.!?]+$", "");
        Matcher m = SAYS.matcher(t);
        String name = m.matches() ? m.group(1) : t;
        name = name.replaceAll("(?i)\\b(thanks|thank you|please)\\b", "").trim();
        String[] parts = name.split("\\s+");
        if (parts.length == 0 || parts.length > 3 || name.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.matches("[\\p{L}'-]{2,24}")) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        String out = sb.toString();
        String lower = out.toLowerCase(Locale.ROOT);
        if (lower.matches("(yes|no|okay|ok|hello|hi|what|why|who|nothing|obi|ivie|dvine)")) {
            return null;
        }
        return out;
    }

    /** "call me Val" at any time after onboarding. */
    public static String rename(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = RENAME.matcher(raw.trim().replaceAll("[.!?]+$", ""));
        return m.matches() ? parseName(m.group(1)) : null;
    }

    public static String welcomeQuestion() {
        return "Welcome. I'm Obi, and with me is Ivie. Before we begin, what's your name?";
    }

    public static String welcome(String name) {
        return "Welcome, " + name + ". We'll remember that. Swipe the screen whenever you want to talk. "
                + "What you teach us is backed up to your DVINE account when you're online; say stop sharing to keep it on this phone.";
    }
}
