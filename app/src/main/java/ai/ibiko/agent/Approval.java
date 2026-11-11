package ai.ibiko.agent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What waits for the pilot's yes before the agent does it (the permission check): shell commands, pushes and pull
 * requests, and any connector call that changes something on the pilot's accounts (creating, deleting, posting,
 * merging…). Reading, searching, listing and fetching go ahead. Writing files in the workspace goes ahead too
 * ("Accept edits", as in Claude Code); Plan mode changes nothing at all. Pure Java.
 */
public final class Approval {
    private Approval() {
    }

    /** Words in a connector's tool name that mean it changes something. */
    static final Pattern CHANGES = Pattern.compile("(?i)(^|[._\\-\\s/])(create|delete|remove|update|edit|write|post|send|reply"
            + "|comment|merge|push|close|reopen|archive|move|rename|upload|publish|invite|add|set|star|unstar|fork|approve"
            + "|dismiss|lock|unlock|transfer|assign|label|submit|cancel|rerun|trigger|dispatch|enable|disable|share|schedule"
            + "|like|follow|unfollow|block|mute|pin|unpin|tag|commit)(?=$|[._\\-\\s/A-Z]|s\\b|_)");

    /** A connector call ("github.create_issue {...}") that changes something and so asks first. */
    public static boolean changes(String call) {
        if (call == null) {
            return false;
        }
        String name = call.trim().split("\\s+", 2)[0];
        // camelCase ("createIssue") is read as words too
        String words = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.US);
        return CHANGES.matcher(words).find();
    }

    /** What the pilot is asked, in words. */
    public static String connectorQuestion(String call) {
        String c = call == null ? "" : call.trim();
        return "Use the connector: " + (c.length() > 400 ? c.substring(0, 400) + "…" : c);
    }
}
