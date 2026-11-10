package ai.ibiko.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small model that doesn't stop at the end of its answer goes on to write the rest of the conversation itself:
 * "User: Carry out the plan", then "ibiko: …" again, over and over. This finds where that invented turn starts, so
 * the reply is stopped there and cut. A name label at the very start ("ibiko: The issue is…") is not a new turn
 * ({@link ai.ibiko.agent.CodeAgent#unlabel} takes it off). Pure Java.
 */
public final class FakeTurn {
    private FakeTurn() {
    }

    /** A line that opens the user's turn ("User:", "Human:", "**User**:"). */
    private static final Pattern USER = Pattern.compile("(?m)^[ \\t]*(?:\\*\\*)?(?:user|human)(?:\\*\\*)?[ \\t]*:", Pattern.CASE_INSENSITIVE);
    /** A line that opens another turn of the assistant's after its first ("\nibiko: …"). */
    private static final Pattern AGAIN = Pattern.compile("(?m)^[ \\t]*(?:\\*\\*)?(?:ibiko|obi|ivie|assistant)(?:\\*\\*)?[ \\t]*:", Pattern.CASE_INSENSITIVE);

    /** Where the invented turn starts, or -1 when there is none. */
    public static int at(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int found = -1;
        Matcher u = USER.matcher(text);
        while (u.find()) {
            if (u.start() > 0 && !fenced(text, u.start())) {
                found = u.start();
                break;
            }
        }
        Matcher a = AGAIN.matcher(text);
        while (a.find()) {
            if (a.start() > 0 && !fenced(text, a.start())) {
                found = found < 0 ? a.start() : Math.min(found, a.start());
                break;
            }
        }
        return found;
    }

    /** A "User:" inside a code fence is the code being written, not a new prompt. */
    private static boolean fenced(String text, int index) {
        boolean in = false;
        int i = 0;
        while (i < index) {
            int tick = text.indexOf("```", i);
            if (tick < 0 || tick >= index) {
                break;
            }
            in = !in;
            i = tick + 3;
        }
        return in;
    }

    /** The reply without the invented turns (null stays null). */
    public static String cut(String text) {
        int at = at(text);
        return at < 0 ? text : text.substring(0, at).trim();
    }

    /**
     * Passes the reply on piece by piece until an invented turn starts, then stops the model (the sink says no more)
     * and passes nothing of it.
     */
    public static NativeLlm.Sink guard(final NativeLlm.Sink sink) {
        final StringBuilder sofar = new StringBuilder();
        return text -> {
            if (text != null && !"\u0001".equals(text)) {
                sofar.append(text);
                if (at(sofar.toString()) >= 0) {
                    return false;
                }
            }
            return sink == null || sink.onText(text);
        };
    }
}
