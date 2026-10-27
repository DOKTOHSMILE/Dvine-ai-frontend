package ai.dvine.brain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The user setting a fact straight: "no, the president of Nigeria is Bola Tinubu", "that's wrong, it's 2026",
 * "actually water boils at 100 degrees", "the capital is now Abuja", "remember that the meeting moved to Friday".
 * What the user says wins over what the model was trained on; this only reads it. Pure Java.
 */
public final class FactCorrection {
    /** What the fact is about ("the president of nigeria"), what it is ("Bola Tinubu"), and the whole statement. */
    public final String subject, value, statement;
    /** True when the user said it was wrong without saying what is right ("that's wrong"). */
    public final boolean needsValue;

    private FactCorrection(String subject, String value, String statement, boolean needsValue) {
        this.subject = subject;
        this.value = value;
        this.statement = statement;
        this.needsValue = needsValue;
    }

    private static final Pattern MARK = Pattern.compile("^(?:no+|nope|wrong|that'?s wrong|that is wrong|that'?s not (?:right|true|correct)"
            + "|that is not (?:right|true|correct)|not true|incorrect|you'?re wrong|you are wrong|you'?re mistaken|you are mistaken"
            + "|actually|correction|that'?s outdated|that is outdated|that'?s old news|you'?re out of date|you are out of date"
            + "|that'?s not it|not quite)(?:(?:[,.:;!]+|\\s)\\s*(.*))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IT_IS = Pattern.compile("^(?:it'?s|it is|it was|they are|they're|he is|he's|she is|she's|the answer is|the right answer is)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STATEMENT = Pattern.compile("^(.{2,80}?)\\s+(is|are|was|were|has|have|had|became|becomes|lives|died|won|means)\\s+(?:now\\s+|actually\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOW = Pattern.compile("^(.{2,80}?)\\s+(is|are)\\s+(?:now|no longer .+?,?\\s*(?:it'?s|it is|but|they are)?)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEW = Pattern.compile("^(?:the )?(?:new|current|latest|present) (.{2,80}?)\\s+(is|are)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE = Pattern.compile("^(?:please )?(?:remember|note|learn|know)(?: that)? (.{6,200})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_A_FIX = Pattern.compile(
            "^(?:thanks?|thank you|problem|worries|way|idea|thanks? though|not now|never ?mind|stop|wait|i meant .*|i mean .*|i said .*|i was asking .*|meant .*)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Reads {@code said} (as spoken, with its capitals) as a correction, or returns null. {@code lastTopic} is what
     * the last question was about ("the president of nigeria"), for "no, it's Bola Tinubu".
     */
    public static FactCorrection read(String said, String lastTopic) {
        if (said == null) {
            return null;
        }
        String s = said.trim().replaceAll("[.!]+$", "").replaceAll("\\s+", " ");
        if (s.isEmpty() || s.endsWith("?")) {
            return null;
        }
        Matcher m = MARK.matcher(s);
        if (m.matches()) {
            String rest = m.group(1) == null ? "" : m.group(1).trim();
            if (rest.isEmpty() || rest.matches("(?i)^(it'?s|it is)( not)?( right| true| correct)?$")) {
                return lastTopic == null ? null : new FactCorrection(lastTopic, null, null, true);
            }
            if (NOT_A_FIX.matcher(rest).matches() || question(rest)) {
                return null;
            }
            Matcher it = IT_IS.matcher(rest);
            if (it.matches()) {
                if (lastTopic == null) {
                    return null;
                }
                String v = it.group(1).trim();
                return new FactCorrection(lastTopic, v, capital(lastTopic) + " is " + v, false);
            }
            FactCorrection f = statement(rest);
            if (f != null) {
                return f;
            }
            return lastTopic == null || rest.split(" ").length < 2 ? null
                    : new FactCorrection(lastTopic, rest, capital(lastTopic) + ": " + rest, false);
        }
        m = NOW.matcher(s);
        if (m.matches() && !question(s)) {
            return made(m.group(1), m.group(2), m.group(3));
        }
        m = NEW.matcher(s);
        if (m.matches() && !question(s)) {
            return made("the " + m.group(1), m.group(2), m.group(3));
        }
        m = NOTE.matcher(s);
        if (m.matches()) {
            String body = m.group(1).trim();
            if (body.toLowerCase(Locale.US).matches("^(my|our) .*")) {
                return null; // personal facts have their own memory ("what is my …")
            }
            FactCorrection f = statement(body);
            return f != null ? f : new FactCorrection(keyOf(body), body, capital(body), false);
        }
        return null;
    }

    private static FactCorrection statement(String rest) {
        Matcher st = STATEMENT.matcher(rest);
        if (!st.matches() || question(st.group(1))) {
            return null;
        }
        return made(st.group(1), st.group(2), st.group(3));
    }

    private static FactCorrection made(String subject, String verb, String value) {
        String subj = subject.trim(), v = value.trim();
        String lv = verb.toLowerCase(Locale.US);
        boolean be = lv.matches("is|are|was|were");
        return new FactCorrection(be ? subj.toLowerCase(Locale.US) : (subj + " " + lv).toLowerCase(Locale.US),
                v, capital(subj) + " " + lv + " " + v, false);
    }

    private static boolean question(String t) {
        return t.toLowerCase(Locale.US).matches("^(what|who|whom|whose|where|when|why|how|which|is|are|was|were|do|does|did|can|could|will|would|should)\\b.*");
    }

    /** The words that name a note ("the meeting moved to friday" → "meeting moved friday"). */
    static String keyOf(String body) {
        return body.toLowerCase(Locale.US).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\b(the|a|an|that|to|of|on|in|at)\\b", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
