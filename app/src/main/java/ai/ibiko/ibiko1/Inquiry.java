package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ibiko 1's way of learning what it doesn't know, by asking back:
 * <ol>
 * <li>a question it can't answer from memory is researched online;</li>
 * <li>then it asks the person what they mean, in their own words ({@link #askBack});</li>
 * <li>that explanation is researched too;</li>
 * <li>the two results are compared by their word ids ({@link #compare}): when they agree it answers; when they don't,
 * it answers with the one closest to what the person explained and names the other;</li>
 * <li>the answer ends with {@link #MAY_BE_WRONG}, and a correction ("no, it's …") replaces it in memory
 * ({@link #correction});</li>
 * <li>what was learned is matched by its word ids against what it already knows for sure ({@link #related}), so new
 * knowledge is filed beside the old knowledge it belongs with.</li>
 * </ol>
 * Pure Java: the research, the memory and the chat are the caller's ({@link Ibiko1}).
 */
public final class Inquiry {
    private Inquiry() {
    }

    /** Said under every answer it worked out this way. */
    public static final String MAY_BE_WRONG = "I may be wrong. Please correct me if I am.";

    /** How long the question waits for the person's explanation. */
    public static final long WAIT_MS = 10 * 60_000L;

    /** The share of word ids two results must have in common to say the same thing. */
    static final double AGREE = 0.2;

    /** A question waiting for the person's explanation, with what the first search found (null: nothing). */
    public static final class Pending {
        public final String question, subject;
        public final ResearchAnswer first;
        public final long askedAt;

        public Pending(String question, String subject, ResearchAnswer first, long askedAt) {
            this.question = question;
            this.subject = subject;
            this.first = first;
            this.askedAt = askedAt;
        }

        public boolean expired(long now) {
            return now - askedAt > WAIT_MS;
        }
    }

    /** What it says after the first search, asking for the person's own words. */
    public static String askBack(String question, String subject, boolean foundSomething) {
        String about = subject != null ? "“" + subject + "”" : "that";
        return (foundSomething ? "I looked " + about + " up and found something, but I'm not sure it's what you mean."
                : "I looked " + about + " up and didn't find a clear answer.")
                + " Before I answer: what do you mean by " + about + "? Tell me in your own words, and I'll search on that"
                + " too and compare the two. (Say “just answer” to skip.)";
    }

    /** The person doesn't want to explain: "never mind", "skip", "just answer". */
    public static boolean dropsIt(String message) {
        String m = message == null ? "" : message.trim().toLowerCase(Locale.US);
        return m.matches("(?s)(never ?mind|nvm|skip( it)?|forget it|just answer( it| me)?|no idea|i don'?t know|cancel)\\b.*");
    }

    /** What to search for the person's explanation: their words, with the subject when they didn't name it. */
    public static String explanationQuery(Pending p, String explanation) {
        String e = explanation == null ? "" : explanation.trim().replaceFirst("(?i)^(i mean|i meant|it'?s|it is|like)\\s+", "");
        if (p.subject != null && !e.toLowerCase(Locale.US).contains(p.subject.toLowerCase(Locale.US))) {
            e = p.subject + " " + e;
        }
        return e;
    }

    /** The answer worked out from the two searches. */
    public static final class Outcome {
        public final String text;
        public final List<String> sources;
        /** Both searches found something and it says the same thing. */
        public final boolean agree;

        Outcome(String text, List<String> sources, boolean agree) {
            this.text = text;
            this.sources = sources;
            this.agree = agree;
        }

        /** The answer for the chat: the text, its sources, and the invitation to correct it. */
        public String shown() {
            return text + (sources.isEmpty() ? "" : "\nSource: " + String.join(", ", sources)) + "\n\n" + MAY_BE_WRONG;
        }
    }

    /**
     * Compares what the question's search found ({@code first}) with what the explanation's search found
     * ({@code second}); null when neither found anything.
     */
    public static Outcome compare(String question, String explanation, ResearchAnswer first, ResearchAnswer second) {
        if (first == null && second == null) {
            return null;
        }
        if (first == null || second == null) {
            ResearchAnswer only = first != null ? first : second;
            return new Outcome(only.text + "\n(Only the search on " + (first != null ? "your question" : "what you explained")
                    + " found something, so this rests on one search.)", only.sources, false);
        }
        // the subject is in both by how they were searched: agreement is in what they say about it
        Set<Integer> asked = ids(question), a = ids(first.text), b = ids(second.text);
        a.removeAll(asked);
        b.removeAll(asked);
        List<String> sources = new ArrayList<String>(new LinkedHashSet<String>(join(first.sources, second.sources)));
        if (similarity(a, b) >= AGREE) {
            return new Outcome(first.text + "\n(Both searches agree: the one on your question and the one on what you explained.)",
                    sources, true);
        }
        // they differ: the person's own words decide which one they meant
        Set<Integer> meant = ids(explanation);
        meant.addAll(ids(question));
        boolean secondCloser = similarity(b, meant) > similarity(a, meant);
        ResearchAnswer best = secondCloser ? second : first, other = secondCloser ? first : second;
        return new Outcome(best.text + "\n\nThe two searches found different things; I went with the one closest to what you"
                + " explained. If you meant the other: " + firstSentence(other.text), sources, false);
    }

    /** "No, it's X" / "Actually, X" / "Wrong. X": a correction of the answer just given. */
    public static boolean corrects(String message) {
        String m = message == null ? "" : message.trim().toLowerCase(Locale.US);
        return m.matches("(?s)(no|nope|wrong|incorrect|not quite|not really|actually|that'?s (not right|wrong|incorrect)|you'?re wrong)\\b.*")
                && correction(message).length() >= 3;
    }

    /** The right answer inside a correction: "No, it's a dance from Lagos." → "A dance from Lagos." */
    public static String correction(String message) {
        String c = message == null ? "" : message.trim();
        String before;
        do {
            before = c;
            c = c.replaceFirst("(?i)^(no|nope|wrong|incorrect|not quite|not really|actually|that'?s (not right|wrong|incorrect)|you'?re wrong"
                    + "|it'?s|it is|it was|the answer is|the right answer is|i mean|i meant)\\b[\\s,.:;!-]*", "");
        } while (!c.equals(before));
        c = c.trim();
        return c.isEmpty() ? c : Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    /** The word ids of a text's meaning words (synonyms and endings folded as {@link Knowledge} reads them). */
    public static Set<Integer> ids(String text) {
        Set<Integer> out = new LinkedHashSet<Integer>();
        if (text == null) {
            return out;
        }
        for (String w : Knowledge.meaning(text)) {
            int id = ai.ibiko.learn.Symbols.id(root(w));
            if (id != 0) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * A meaning word cut down until it stops changing: {@link Knowledge#meaning} takes one ending off at a time
     * ("weddings" → "wedding", "wedding" → "wed"), so every form of a word ends on the same root and the same id.
     */
    static String root(String w) {
        String r = w;
        for (int i = 0; i < 4; i++) {
            List<String> again = Knowledge.meaning(r);
            if (again.size() != 1 || again.get(0).equals(r)) {
                break;
            }
            r = again.get(0);
        }
        return r;
    }

    /** Shared ids over the smaller set: 1 when one says everything the other does. */
    static double similarity(Set<Integer> a, Set<Integer> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Integer i : a) {
            if (b.contains(i)) {
                n++;
            }
        }
        return (double) n / Math.min(a.size(), b.size());
    }

    /**
     * What it already knows that new knowledge belongs with, best first, at most {@code most}: a known fact whose topic
     * (the words of its question) the new knowledge names, or one sharing at least two word ids with it.
     */
    public static List<Knowledge.Fact> related(String question, String answer, List<Knowledge.Fact> known, int most) {
        Set<Integer> mine = ids(question);
        mine.addAll(ids(answer));
        List<Object[]> scored = new ArrayList<Object[]>();
        for (Knowledge.Fact f : known) {
            if (f.question.equalsIgnoreCase(question)) {
                continue; // itself
            }
            Set<Integer> topic = ids(f.question), theirs = new LinkedHashSet<Integer>(topic);
            theirs.addAll(ids(f.answer));
            int shared = 0, onTopic = 0;
            for (Integer i : theirs) {
                if (mine.contains(i)) {
                    shared++;
                    onTopic += topic.contains(i) ? 1 : 0;
                }
            }
            if (onTopic > 0 || shared >= 2) {
                scored.add(new Object[]{f, shared + 2 * onTopic});
            }
        }
        Collections.sort(scored, (x, y) -> Integer.compare((Integer) y[1], (Integer) x[1]));
        List<Knowledge.Fact> out = new ArrayList<Knowledge.Fact>();
        for (int i = 0; i < scored.size() && i < most; i++) {
            out.add((Knowledge.Fact) scored.get(i)[0]);
        }
        return out;
    }

    /** "This ties in with what I know: …" for the chat, or "" when nothing does. */
    public static String tiesIn(List<Knowledge.Fact> related) {
        if (related.isEmpty()) {
            return "";
        }
        List<String> said = new ArrayList<String>();
        for (Knowledge.Fact f : related) {
            said.add("“" + f.question.replaceAll("[?.!]+$", "") + "” (" + firstSentence(f.answer) + ")");
        }
        return "\nThis ties in with what I already know: " + String.join("; ", said) + ".";
    }

    private static List<String> join(List<String> a, List<String> b) {
        List<String> out = new ArrayList<String>(a);
        out.addAll(b);
        return out;
    }

    static String firstSentence(String text) {
        String t = text == null ? "" : text.trim();
        int end = t.indexOf(". ");
        String s = end > 0 ? t.substring(0, end + 1) : t;
        return s.length() > 200 ? s.substring(0, 197) + "…" : s;
    }
}
