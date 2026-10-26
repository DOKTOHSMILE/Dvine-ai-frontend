package ai.dvine.brain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns "post this on WhatsApp" or "copy that" into a pending action,
 * then waits for a spoken yes or no. Pure Java; the Activity runs the result.
 */
public final class ActionPlanner {
    public static final long EXPIRY_MS = 2 * 60 * 1000L;

    public enum Reply {
        NONE,
        CONFIRMED,
        CANCELLED
    }

    private static final Pattern SHARE = Pattern.compile(
            "^(?:please )?(?:post|share|tweet|publish|upload)(?: (?:this|that|it))?(?: post)?"
                    + "(?: (?:on|to|via|in) (whatsapp|whats app|x|twitter|facebook|fb|instagram|ig|linkedin|linked in|telegram))?"
                    + "(?:[:,]? (?:saying |that says )?(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COPY = Pattern.compile(
            "^(?:please )?copy(?: (?:this|that|it))?(?: to (?:the )?clipboard)?(?:[:,]? (.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YES = Pattern.compile(
            "^(yes|yeah|yep|yup|ok|okay|sure|go ahead|do it|send it|confirm|confirmed|please do|yes please|go on|proceed)( please)?$");
    private static final Pattern NO = Pattern.compile(
            "^(no|nope|cancel|stop|don'?t|do not|never mind|nevermind|not now|leave it)( it| that| please)?$");

    private PendingAction pending;

    public synchronized PendingAction pending(long now) {
        if (pending != null && now - pending.createdAt > EXPIRY_MS) {
            pending = null;
        }
        return pending;
    }

    /** Parses an action request. {@code lastSaid} fills "post that". Returns null when the text isn't one. */
    public synchronized PendingAction plan(String cased, String lastSaid, long now) {
        String t = cased == null ? "" : cased.trim();
        Matcher m = SHARE.matcher(t);
        if (m.matches()) {
            String content = m.group(2) != null ? m.group(2).trim() : lastSaid;
            if (content == null || content.isEmpty()) {
                return null;
            }
            pending = new PendingAction(PendingAction.Kind.SHARE, unquote(content), app(m.group(1)), now);
            return pending;
        }
        m = COPY.matcher(t);
        if (m.matches()) {
            String content = m.group(1) != null ? m.group(1).trim() : lastSaid;
            if (content == null || content.isEmpty()) {
                return null;
            }
            pending = new PendingAction(PendingAction.Kind.COPY, unquote(content), null, now);
            return pending;
        }
        return null;
    }

    /** Classifies a reply while something is pending, and clears it on yes or no. */
    public synchronized Reply reply(String lower, long now) {
        if (pending(now) == null) {
            return Reply.NONE;
        }
        String t = lower.replaceAll("[.!?,]+", "").trim();
        if (YES.matcher(t).matches()) {
            return Reply.CONFIRMED;
        }
        if (NO.matcher(t).matches()) {
            pending = null;
            return Reply.CANCELLED;
        }
        return Reply.NONE;
    }

    /** Takes the confirmed action out of the planner. */
    public synchronized PendingAction take() {
        PendingAction p = pending;
        pending = null;
        return p;
    }

    public static String question(PendingAction a) {
        String preview = a.content.length() > 120 ? a.content.substring(0, 117) + "..." : a.content;
        if (a.kind == PendingAction.Kind.COPY) {
            return "Shall I copy this to your clipboard: \"" + preview + "\"? Say yes or no.";
        }
        return "Shall I open " + a.appName() + " with this post: \"" + preview
                + "\"? You'll press send yourself. Say yes or no.";
    }

    static String app(String raw) {
        if (raw == null) {
            return null;
        }
        String r = raw.toLowerCase(Locale.US).replace(" ", "");
        switch (r) {
            case "whatsapp": return "whatsapp";
            case "x": case "twitter": return "x";
            case "facebook": case "fb": return "facebook";
            case "instagram": case "ig": return "instagram";
            case "linkedin": return "linkedin";
            case "telegram": return "telegram";
            default: return null;
        }
    }

    private static String unquote(String s) {
        return s.replaceAll("^[\"'“]+|[\"'”]+$", "").trim();
    }
}
