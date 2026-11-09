package ai.dvine.social.intel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The claims in a message or post, each marked as a factual claim (something that can be checked: numbers, dates,
 * named events, "X is/was/has/announced Y") or an opinion (a judgement, a feeling, a recommendation). Opinions are
 * kept, but only factual claims are sent to be verified. Pure Java.
 */
public final class ClaimExtractor {
    /** One claim and whether it can be checked. */
    public static final class Claim {
        public final String text;
        public final boolean factual;

        Claim(String text, boolean factual) {
            this.text = text;
            this.factual = factual;
        }
    }

    private ClaimExtractor() {
    }

    private static final String OPINION = "(?i).*\\b(i think|i believe|i feel|in my opinion|imo|imho|should|must|best|worst"
            + "|amazing|terrible|awesome|awful|love|hate|beautiful|ugly|better than|worse than|overrated|underrated|seems?"
            + "|probably|maybe|perhaps|i guess|i hope|beware|guys)\\b.*";
    private static final String FACTUAL = "(?i).*(\\d|\\b(is|are|was|were|has|have|had|will|announced|confirmed|reported|said"
            + "|died|arrested|won|lost|launched|released|banned|approved|signed|killed|elected|resigned|increased|decreased"
            + "|according to|percent|million|billion|thousand)\\b).*";

    /** Every sentence that says something, marked factual or opinion; greetings, questions and fragments left out. */
    public static List<Claim> claims(String text) {
        List<Claim> out = new ArrayList<Claim>();
        String clean = (text == null ? "" : text).replaceAll("https?://\\S+", " ").replaceAll("[#@](\\w+)", "$1");
        for (String s : clean.split("(?<=[.!?])\\s+|\\n+")) {
            String t = s.trim();
            if (t.length() < 12 || t.endsWith("?") || t.split("\\s+").length < 3) {
                continue;
            }
            boolean opinion = t.matches(OPINION);
            boolean factual = t.matches(FACTUAL) && !opinion;
            out.add(new Claim(t, factual));
            if (out.size() >= 12) {
                break;
            }
        }
        return out;
    }

    /** Short search phrases for a message: its key words, the names in it, and each factual claim cut down. */
    public static List<String> searchPhrases(String text, int most) {
        List<String> out = new ArrayList<String>();
        ai.dvine.suit.Comprehension c = ai.dvine.suit.Comprehension.of(text);
        if (!c.topic.isEmpty()) {
            out.add(String.join(" ", c.topic.subList(0, Math.min(5, c.topic.size()))));
        }
        for (String n : c.names) {
            if (out.size() < most && !out.contains(n)) {
                out.add(n);
            }
        }
        for (Claim cl : claims(text)) {
            if (out.size() >= most) {
                break;
            }
            if (cl.factual) {
                String[] w = cl.text.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
                StringBuilder b = new StringBuilder();
                for (String x : w) {
                    if (x.length() > 3 && b.length() < 60) {
                        b.append(b.length() == 0 ? "" : " ").append(x);
                    }
                }
                if (b.length() > 0 && !out.contains(b.toString())) {
                    out.add(b.toString());
                }
            }
        }
        return out.isEmpty() ? java.util.Collections.singletonList(text == null ? "" : text.trim()) : out;
    }
}
