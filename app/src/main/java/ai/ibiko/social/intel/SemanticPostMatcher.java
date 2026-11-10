package ai.ibiko.social.intel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ranks posts by how close their meaning is to a subject, not by one keyword: TF-IDF cosine over the words of the
 * subject and the posts (rare shared words count more than common ones), with near-duplicates (reposts, copies, the
 * same text on two platforms) removed. Pure Java.
 */
public final class SemanticPostMatcher {
    private SemanticPostMatcher() {
    }

    private static final Set<String> STOP = new HashSet<String>(java.util.Arrays.asList(("the a an and or but to of in on at"
            + " for with is are was were be it this that these those i you me my your we our they them he she his her do"
            + " does did can could would should will just so if then there here about from up out have has had what how"
            + " why when where which who not no rt via amp").split(" ")));

    static List<String> words(String text) {
        List<String> out = new ArrayList<String>();
        for (String w : (text == null ? "" : text.toLowerCase(Locale.US)).replaceAll("https?://\\S+", " ").split("[^\\p{L}\\p{N}]+")) {
            if (w.length() > 2 && !STOP.contains(w)) {
                out.add(w.length() > 4 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }

    /** The posts most like the subject, best first, duplicates removed, those under {@code floor} left out. */
    public static List<SocialPost> rank(String subject, List<SocialPost> posts, double floor, int most) {
        List<List<String>> docs = new ArrayList<List<String>>();
        for (SocialPost p : posts) {
            docs.add(words(p.content));
        }
        List<String> q = words(subject);
        Map<String, Integer> df = new HashMap<String, Integer>();
        for (List<String> d : docs) {
            for (String w : new HashSet<String>(d)) {
                df.merge(w, 1, Integer::sum);
            }
        }
        int n = docs.size() + 1;
        List<SocialPost> scored = new ArrayList<SocialPost>();
        for (int i = 0; i < posts.size(); i++) {
            double s = cosine(q, docs.get(i), df, n);
            if (s >= floor) {
                scored.add(posts.get(i).withRelevance(s));
            }
        }
        scored.sort((a, b) -> Double.compare(b.relevance, a.relevance));
        List<SocialPost> out = new ArrayList<SocialPost>();
        List<Set<String>> kept = new ArrayList<Set<String>>();
        for (SocialPost p : scored) {
            Set<String> w = new HashSet<String>(words(p.content));
            boolean dup = false;
            for (Set<String> k : kept) {
                dup |= jaccard(w, k) >= 0.8;
            }
            if (!dup && !w.isEmpty()) {
                out.add(p);
                kept.add(w);
            }
            if (out.size() >= most) {
                break;
            }
        }
        return out;
    }

    static double cosine(List<String> a, List<String> b, Map<String, Integer> df, int n) {
        Map<String, Double> va = vector(a, df, n), vb = vector(b, df, n);
        double dot = 0, na = 0, nb = 0;
        for (Map.Entry<String, Double> e : va.entrySet()) {
            na += e.getValue() * e.getValue();
            Double o = vb.get(e.getKey());
            if (o != null) {
                dot += e.getValue() * o;
            }
        }
        for (double v : vb.values()) {
            nb += v * v;
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    private static Map<String, Double> vector(List<String> w, Map<String, Integer> df, int n) {
        Map<String, Double> tf = new HashMap<String, Double>();
        for (String x : w) {
            tf.merge(x, 1.0, Double::sum);
        }
        for (Map.Entry<String, Double> e : tf.entrySet()) {
            Integer d = df.get(e.getKey());
            e.setValue(e.getValue() * Math.log(1.0 + n / (1.0 + (d == null ? 0 : d))));
        }
        return tf;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int both = 0;
        for (String x : a) {
            if (b.contains(x)) {
                both++;
            }
        }
        return both / (double) (a.size() + b.size() - both);
    }
}
