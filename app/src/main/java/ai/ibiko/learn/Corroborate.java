package ai.ibiko.learn;

import java.util.Locale;
import java.util.Set;

/**
 * How Ibiko's brain learns from the models' answers without keeping a wrong one (V1–V7). An answer is never taken as
 * true from one sighting: each time an API answers a question like one already in memory, the two are compared;
 * agreement makes the kept answer firmer, disagreement counts against it, and when the other answers keep coming and
 * outweigh it, the kept answer is marked false and the new one is learned in its place.
 * <ul>
 * <li>V1 Only real answers are learned: never a connection failure, a status line, Ibiko's own stand-in reply while the
 * API was away, or an apology for not answering ({@link #failure}).</li>
 * <li>V2 A new answer starts unconfirmed (seen once).</li>
 * <li>V3 The same answer again (the same words, or the same content words: a paraphrase) confirms it
 * ({@link #agrees}).</li>
 * <li>V4 A different answer to the same question counts once against every kept answer it differs from, and is kept
 * itself, unconfirmed, as the challenger.</li>
 * <li>V5 A kept answer is marked false when the answers against it are at least two and at least as many as the times
 * it was confirmed ({@link #overturned}): one odd reply never overturns an answer confirmed many times.</li>
 * <li>V6 A false answer is never given again; the challenger, confirmed in turn, takes its place.</li>
 * <li>V7 The pilot's own check or cancel overrules all of this at once.</li>
 * </ul>
 * Answers are compared by their word ids ({@link Codes#tags}, {@link Symbols}), not by spelling. Pure Java.
 */
public final class Corroborate {
    private Corroborate() {
    }

    /** V1: not an answer to learn: a failure, a status line, a stand-in reply or a refusal to answer. */
    public static boolean failure(String reply) {
        String t = reply == null ? "" : reply.trim().toLowerCase(Locale.US);
        if (t.length() < 2) {
            return true;
        }
        return t.matches("(?s).*\\b(couldn'?t reach|could not reach|unable to resolve host|no address associated|timed? ?out"
                + "|connection (refused|reset|failed|lost)|network (error|is unreachable)|i'?m offline|you'?re offline"
                + "|no internet|tap retry|say .?retry|i lost the answer|my reply stopped|couldn'?t finish that reply"
                + "|(http|status) ?[45]\\d\\d|\\b[45]\\d\\d (error|bad request|unauthori[sz]ed|forbidden|not found)"
                + "|rate limit|out of credit|insufficient (credit|quota|balance)|api key|server error|service unavailable"
                + "|answered by ibiko 1|while the api (is|was) (away|down|offline)|from my offline notes"
                + "|i can'?t (help|answer) (with )?that|as an ai\\b).*");
    }

    /** V3: the same answer, word for word or in other words (most of the content words shared). */
    public static boolean agrees(String kept, String fresh) {
        if (kept == null || fresh == null) {
            return false;
        }
        String a = kept.trim().replaceAll("\\s+", " "), b = fresh.trim().replaceAll("\\s+", " ");
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        Set<String> x = Codes.tags(a), y = Codes.tags(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        int same = 0;
        for (String t : x) {
            if (y.contains(t)) {
                same++;
            }
        }
        double jaccard = same / (double) (x.size() + y.size() - same);
        return jaccard >= 0.6;
    }

    /** V5: answers against at least two, and at least as many as the times it was confirmed. */
    public static boolean overturned(int confirmed, int against) {
        return against >= 2 && against >= confirmed;
    }
}
