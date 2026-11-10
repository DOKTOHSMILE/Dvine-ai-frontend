package ai.dvine.dvine1.formulas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The foundation: truth. Everything Ibiko knows (what the pilot said, what was confirmed, what ships in the app, what
 * was read today, what it learned and has not checked, and what a model only remembers) is put together on one rule:
 * the more trustworthy source stands, the weaker one gives way, agreement makes it firm, and what is not sure is said
 * to be not sure. Reasoning, habits and lessons are built on top of this, never instead of it. Pure Java.
 *
 * <pre>
 * G1  ranks           most trusted first:
 *                       1 PILOT      the pilot's own word about themselves and their work
 *                       2 CONFIRMED  a fact the pilot confirmed (the tick, or "yes, that's right")
 *                       3 BUILT_IN   reference facts shipped in the app
 *                       4 SOURCE     a named source read today
 *                       5 LEARNED    learned by itself and not yet checked
 *                       6 MODEL      a model's own memory (its training): never stated as certain
 * G2  agreement       two different sources that say the same thing make it FIRM
 * G3  conflict        the higher rank stands; when ranks are equal the newer stands and "sources differ" is said
 * G4  change          for what changes (prices, office-holders, populations, versions) a dated newer source of the
 *                     same rank wins over an older one
 * G5  honesty         every claim is said with how sure it is (firm, likely, unsure); nothing is invented; a source is
 *                     named only when there is one; people's posts are opinions, not facts
 * G6  coherence       a new claim that contradicts a FIRM one is held back until it is checked
 * G7  foundation      every answer is built from all that is known, in this order, before any reasoning on top of it
 * G8  question all    every piece of data asks "is this true or false?", going in and going out:
 *                       in   what it reads or learns is checked, sentence by sentence, against what it knows for certain;
 *                            a sentence found FALSE is not learned; the pilot's own word is kept, with the difference said
 *                       out  every reply, from any brain, is checked the same way after it is shown; a FALSE sentence is
 *                            followed by the correction and where it comes from
 *                     TRUE   a more trusted source says the same thing
 *                     FALSE  a more trusted source says otherwise, about something with one answer
 *                     UNKNOWN nothing trusted speaks to it: kept as unsure, never stated as certain (G5)
 *                     a sentence that denies, hedges, asks or speaks of the past is not judged
 * G9  ledger          every TRUE and FALSE judgment is kept, both sides ({@link TruthLedger}): the true are confirmed
 *                     knowledge, the false are known mistakes; a sentence judged before is judged the same again, and
 *                     the mistakes and truths about a request go to every brain with it, the mistakes first
 * </pre>
 */
public final class Truth {
    private Truth() {
    }

    /** G1. */
    public enum Source {
        PILOT("you told me"), CONFIRMED("you confirmed"), BUILT_IN("built-in reference"), SOURCE("read today"),
        LEARNED("learned, not yet checked"), MODEL("from memory, unverified");

        public final String said;

        Source(String said) {
            this.said = said;
        }
    }

    /** How sure a settled claim is (G2, G5). */
    public enum Sure { FIRM, LIKELY, UNSURE }

    /** One thing known about something: what it is about, what it says, where it is from and when. */
    public static final class Claim {
        public final String about, says;
        public final Source source;
        public final long at;

        public Claim(String about, String says, Source source, long at) {
            this.about = about == null ? "" : about;
            this.says = says == null ? "" : says.trim();
            this.source = source;
            this.at = at;
        }
    }

    /** What stands about one thing, how sure it is, and what it overruled. */
    public static final class Settled {
        public final Claim stands;
        public final Sure sure;
        public final List<Claim> overruled;
        public final boolean differ;

        Settled(Claim stands, Sure sure, List<Claim> overruled, boolean differ) {
            this.stands = stands;
            this.sure = sure;
            this.overruled = overruled;
            this.differ = differ;
        }
    }

    /** The same claim, however it is worded or punctuated. */
    static boolean same(String a, String b) {
        return norm(a).equals(norm(b)) || !norm(a).isEmpty() && (norm(a).contains(norm(b)) || norm(b).contains(norm(a)))
                && Math.min(norm(a).length(), norm(b).length()) >= 4;
    }

    private static String norm(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{Nd} ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** G1–G4, G6: what stands about each thing. */
    public static List<Settled> settle(List<Claim> claims) {
        Map<String, List<Claim>> by = new LinkedHashMap<String, List<Claim>>();
        for (Claim c : claims) {
            if (c == null || c.says.isEmpty()) {
                continue;
            }
            List<Claim> l = by.get(c.about);
            if (l == null) {
                l = new ArrayList<Claim>();
                by.put(c.about, l);
            }
            l.add(c);
        }
        List<Settled> out = new ArrayList<Settled>();
        for (List<Claim> l : by.values()) {
            List<Claim> sorted = new ArrayList<Claim>(l);
            Collections.sort(sorted, (x, y) -> x.source != y.source ? x.source.ordinal() - y.source.ordinal() : Long.compare(y.at, x.at));
            Claim top = sorted.get(0);
            boolean agreed = false, differ = false;
            List<Claim> overruled = new ArrayList<Claim>();
            for (int i = 1; i < sorted.size(); i++) {
                Claim c = sorted.get(i);
                if (same(c.says, top.says)) {
                    agreed |= c.source != top.source; // G2: a second, different source
                } else {
                    overruled.add(c);
                    differ |= c.source == top.source; // G3: the same rank disagrees
                }
            }
            Sure sure = agreed || top.source.ordinal() <= Source.CONFIRMED.ordinal() ? Sure.FIRM
                    : top.source.ordinal() <= Source.SOURCE.ordinal() ? Sure.LIKELY : Sure.UNSURE;
            if (differ && sure == Sure.FIRM && !agreed) {
                sure = Sure.LIKELY;
            }
            out.add(new Settled(top, sure, overruled, differ));
        }
        return out;
    }

    /** G8: what a check of one sentence found. */
    public enum Verdict { TRUE, FALSE, UNKNOWN }

    /** G8: one sentence checked: its verdict, the sentence, and what the trusted source says instead (FALSE only). */
    public static final class Checked {
        public final Verdict verdict;
        public final String sentence, correction, source;

        public Checked(Verdict verdict, String sentence, String correction, String source) {
            this.verdict = verdict;
            this.sentence = sentence;
            this.correction = correction;
            this.source = source;
        }

        public static Checked unknown(String sentence) {
            return new Checked(Verdict.UNKNOWN, sentence, null, null);
        }
    }

    /** G8: a source that can judge a sentence; UNKNOWN when it has nothing to say about it. */
    public interface Witness {
        Checked check(String sentence);
    }

    /** G8: words that deny, hedge or speak of the past: such a sentence is not judged. */
    static final java.util.regex.Pattern NOT_A_CLAIM = java.util.regex.Pattern.compile(
            "(?i)\\b(not|never|no longer|isn['\u2019]?t|wasn['\u2019]?t|aren['\u2019]?t|former(ly)?|used to|until|"
                    + "before|if|whether|might|may|maybe|perhaps|probably|some say|rumou?r|claims?|allegedly|i think)\\b");

    /** The sentences of a text. */
    public static List<String> sentences(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        for (String s : text.split("(?<=[.!?])\\s+|\\n+")) {
            String t = s.trim().replaceFirst("^[-*\u2022\\d.)\\s]+", "");
            if (t.length() >= 6) {
                out.add(t);
            }
        }
        return out;
    }

    /** Whether a sentence states something plainly enough to be judged true or false. */
    public static boolean claims(String sentence) {
        String s = sentence == null ? "" : sentence.trim();
        return !s.isEmpty() && !s.endsWith("?") && !NOT_A_CLAIM.matcher(s).find();
    }

    /**
     * G8: every sentence of a text asked "true or false?", the witnesses in order of trust (G1): the first that knows
     * decides. Sentences no witness knows are UNKNOWN.
     */
    public static List<Checked> question(String text, List<Witness> witnesses) {
        List<Checked> out = new ArrayList<Checked>();
        for (String s : sentences(text)) {
            Checked found = Checked.unknown(s);
            if (claims(s)) {
                for (Witness w : witnesses) {
                    Checked c;
                    try {
                        c = w.check(s);
                    } catch (RuntimeException e) {
                        c = null;
                    }
                    if (c != null && c.verdict != Verdict.UNKNOWN) {
                        found = c;
                        break;
                    }
                }
            }
            out.add(found);
        }
        return out;
    }

    /** G8 in: the text without the sentences found FALSE, to learn from. */
    public static String trueOnly(String text, List<Witness> witnesses) {
        return trueOnly(question(text, witnesses));
    }

    /** G8 in: the sentences of a pass not found FALSE. */
    public static String trueOnly(List<Checked> checked) {
        StringBuilder b = new StringBuilder();
        for (Checked c : checked) {
            if (c.verdict != Verdict.FALSE) {
                b.append(b.length() == 0 ? "" : " ").append(c.sentence);
            }
        }
        return b.toString();
    }

    /** G8 out: what to say after a reply with FALSE sentences (each correction once, with its source); null when none. */
    public static String correction(List<Checked> checked) {
        java.util.LinkedHashSet<String> said = new java.util.LinkedHashSet<String>();
        for (Checked c : checked) {
            if (c.verdict == Verdict.FALSE && c.correction != null) {
                said.add(c.correction.trim() + (c.source == null ? "" : " (" + c.source + ")"));
            }
        }
        if (said.isEmpty()) {
            return null;
        }
        return "A correction to what I just said: " + String.join(" ", said);
    }

    /** The rules every brain stands on (G1, G5, G7), short. */
    public static final String RULES = " Base every answer on truth. Trust, most first: what the user told you about"
            + " themselves, what they confirmed, the built-in reference, a named source read today, what was learned and not"
            + " checked, and last your own memory. When these disagree, the more trusted one stands. Say how sure you are"
            + " (state it plainly when firm; \"I think\" when likely; \"I'm not sure\" when unsure), never invent a fact or a"
            + " source, and keep people's posts and opinions apart from facts. Before you state anything, ask of each"
            + " piece of it, and of everything you are given: is this true or false? State only what is true, say when"
            + " you can't tell, and drop what is false.";

    /** G7: the foundation for one request, as the brain is given it; "" when nothing is known about it yet. */
    public static String brief(List<Claim> claims) {
        List<Settled> settled = settle(claims);
        if (settled.isEmpty()) {
            return RULES;
        }
        StringBuilder b = new StringBuilder(RULES).append(" What is already known about this, most trusted first:");
        for (Settled s : settled) {
            b.append(" [").append(s.stands.source.said).append(", ").append(s.sure.name().toLowerCase(Locale.US)).append("] ")
                    .append(s.stands.says.replaceAll("\\s+", " "));
            if (s.differ) {
                b.append(" (sources differ: say so)");
            }
            for (Claim o : s.overruled) {
                b.append(" (overruled: ").append(o.source.said).append(" said “").append(clip(o.says, 120)).append("”)");
            }
            b.append('.');
        }
        return b.toString();
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
