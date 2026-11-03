package ai.dvine.gate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A model's reply that says it did something on the phone or online ("I've sent the message", "I have saved the
 * file", "I posted it") when nothing was done this turn: a small model says so often, and saying a thing was done is
 * not doing it. The reply keeps its words, with one plain line after it saying that nothing was actually done and how
 * to have it done. Only first-person claims of a finished act count; "I can send it" or "you sent it" don't. Pure Java.
 */
public final class ClaimGuard {
    private ClaimGuard() {
    }

    static final Pattern CLAIM = Pattern.compile("(?i)\\b(?:I(?:'ve| have)|I(?:'ve| have) (?:just|now|already)|I just|I've just)\\s+"
            + "(sent|posted|shared|saved|deleted|removed|uploaded|downloaded|installed|scheduled|booked|emailed|"
            + "messaged|texted|called|ordered|paid|transferred|tweeted|published|forwarded|set (?:a|an|the|your) (?:reminder|alarm))\\b");
    static final Pattern PLAIN_PAST = Pattern.compile("(?i)(?:^|[.!?]\\s+)I\\s+(sent|posted|shared|saved|deleted|uploaded|"
            + "emailed|messaged|texted|tweeted|published|forwarded|paid|ordered|booked|scheduled)\\s+(?:it|that|this|the|your|a|an)\\b");

    /** The act the reply claims to have done ("sent"), or null when it claims none. */
    public static String claimed(String reply) {
        if (reply == null) {
            return null;
        }
        Matcher m = CLAIM.matcher(reply);
        if (m.find()) {
            return m.group(1).toLowerCase(java.util.Locale.ROOT);
        }
        m = PLAIN_PAST.matcher(reply);
        return m.find() ? m.group(1).toLowerCase(java.util.Locale.ROOT) : null;
    }

    /**
     * The reply as shown: unchanged when it claims nothing, or when something was really done this turn
     * ({@code actedThisTurn}); otherwise with a line saying it wasn't done.
     */
    public static String check(String reply, boolean actedThisTurn) {
        String act = actedThisTurn ? null : claimed(reply);
        if (act == null) {
            return reply;
        }
        return reply.trim() + "\n\n(Nothing was actually " + act + ": I only wrote about it. Ask me to do it and I will,"
                + " or show you how.)";
    }
}
