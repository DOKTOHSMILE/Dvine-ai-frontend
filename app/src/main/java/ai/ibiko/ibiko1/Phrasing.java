package ai.ibiko.ibiko1;

import java.util.Locale;
import java.util.Map;

/**
 * How Ibiko 1 says what it says, so it speaks like a professional and not like a fixed chat bot: a question asked
 * again is recognised ("as I said a moment ago"); what it doesn't know is said plainly and usefully, naming the
 * subject, what it would take to find out and what the person can do now, in different words each time.
 */
final class Phrasing {
    private Phrasing() {
    }

    /** How many times each question was asked in this run (its words, lower case, without punctuation). */
    private static final Map<String, Integer> asked = new java.util.LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> e) {
            return size() > 200;
        }
    };

    static String key(String q) {
        return q == null ? "" : q.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{Nd} ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Counts a question and says how many times it was asked before (0 the first time). */
    static synchronized int heard(String q) {
        String k = key(q);
        Integer n = asked.get(k);
        asked.put(k, n == null ? 1 : n + 1);
        return n == null ? 0 : n;
    }

    /** The same answer to a question asked again, said as a person would. */
    static String again(String answer, int before) {
        if (before <= 0 || answer == null || answer.isEmpty()) {
            return answer;
        }
        String[] lead = {"As I mentioned a moment ago: ", "Same answer as before: ", "To repeat what I found: "};
        return lead[(before - 1) % lead.length] + answer;
    }

    /**
     * What it doesn't know, said professionally: the subject named, why it can't answer now, what it has done and
     * what the person can do; worded differently each time the same question comes back.
     */
    static String unknown(String subject, boolean online, boolean nightLearning, int before) {
        String about = subject == null || subject.isEmpty() ? "that" : "“" + subject + "”";
        String later = nightLearning ? " I've also noted it for my next learning session." : " I've noted the question.";
        if (!online) {
            String[] off = {
                "I don't have reliable information on " + about + " in what I know so far, and I'm offline, so I can't"
                        + " research it right now. Ask me again once you're connected and I'll look it up properly." + later,
                "That's outside what I can confirm without the internet: I don't have " + about + " in my knowledge yet."
                        + " When you're back online I'll research it, or an API or offline model can answer it now.",
                "Still nothing I can stand behind on " + about + " while we're offline. Rather than guess, I'll research it"
                        + " the moment there's a connection." + later,
            };
            return off[before % off.length];
        }
        String[] on = {
            "I couldn't find a reliable answer on " + about + ": what I found didn't settle it, and I'd rather not guess."
                    + " If you can tell me a bit more about what you need, I'll look again, or an API or offline model"
                    + " can take this one." + later,
            "I've looked into " + about + " again and the sources I can reach still don't answer it clearly. It may be"
                    + " too recent or too specialised for them. A model from the brain menu would be the better choice"
                    + " here, and I'll learn from its answer.",
            "This one is beyond what I can verify for now: " + about + " isn't covered well enough by what I can reach."
                    + " Try phrasing it another way, or ask for the part you need most, and I'll work from that.",
        };
        return on[before % on.length];
    }
}
