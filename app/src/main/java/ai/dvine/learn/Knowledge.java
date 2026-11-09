package ai.dvine.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What DVINE 1 knows for sure: answers the pilot confirmed with a thumbs up. Each is kept by its intent, not its
 * wording: the question's kind (who, what, when, where, how many, how, why, yes/no) and its meaning words, with
 * synonyms folded together ("who made", "who created", "who founded" are one), word endings removed and small typos
 * forgiven, so the same question asked another way finds the same answer. A thumbs down keeps the answer as wrong
 * for that intent, so it is never offered again. Pure Java; kept in a file on the device.
 */
public final class Knowledge {
    /** The kind of answer a question wants. */
    public enum Kind { WHO, WHAT, WHEN, WHERE, COUNT, HOW, WHY, YESNO, OTHER }

    /** One thing learned: the question as first asked, its answer, and its intent. */
    public static final class Fact {
        public final String question, answer;
        public final Kind kind;
        final List<String> meaning;
        /** True when the pilot confirmed it; false when the pilot said it was wrong. */
        public final boolean right;

        Fact(String question, String answer, Kind kind, List<String> meaning, boolean right) {
            this.question = question;
            this.answer = answer;
            this.kind = kind;
            this.meaning = meaning;
            this.right = right;
        }
    }

    /** How close a question's meaning must be to a learned one to reuse its answer. */
    static final double CLOSE = 0.6;
    public static final int MOST = 5000;

    private static final Set<String> STOP = new HashSet<String>(Arrays.asList("a", "an", "the", "please", "pls", "can",
            "could", "would", "will", "you", "u", "me", "my", "i", "to", "for", "of", "is", "are", "was", "were", "be",
            "been", "it", "its", "this", "that", "these", "those", "and", "or", "on", "in", "at", "by", "do", "does", "did",
            "just", "now", "kindly", "hey", "dvine", "ivie", "obi", "ibiko", "what", "whats", "who", "whos", "whom",
            "when", "where", "which", "why", "how", "tell", "know", "explain", "describe", "give", "show", "about",
            "there", "any", "some", "much", "many", "really", "actually", "exactly", "please", "with", "as", "from",
            "into", "has", "have", "had", "if", "so", "let", "want", "like", "need", "wonder", "wondering", "curious",
            "question", "quick", "s", "t", "d", "ll", "re", "ve", "m", "your", "yours", "our", "we", "they", "them",
            "he", "she", "him", "her", "his", "their", "mean", "meaning", "define", "definition", "called", "name"));

    /** Words that mean the same thing for a question, folded to one. */
    private static final Map<String, String> SAME = new HashMap<String, String>();

    static {
        same("create", "made", "make", "maker", "created", "creator", "found", "founded", "founder", "invent", "invented",
                "inventor", "built", "build", "builder", "develop", "developed", "developer", "start", "started", "starter",
                "establish", "established", "author", "wrote", "write", "writer", "design", "designed", "designer");
        same("lead", "ceo", "head", "boss", "leader", "chief", "president", "chairman", "run", "runs", "manage", "manager", "owner", "own", "owns");
        same("born", "birth", "birthday", "birthplace");
        same("die", "died", "death", "dead", "pass");
        same("old", "age", "aged");
        same("big", "size", "large", "huge", "area");
        same("tall", "height", "high");
        same("far", "distance", "away");
        same("cost", "price", "expensive", "cheap", "pay", "worth");
        same("live", "lives", "living", "reside", "resides", "home", "hometown");
        same("capital", "capitol");
        same("speak", "spoken", "language", "tongue");
        same("money", "currency");
        same("people", "population", "inhabitants", "residents", "citizens");
        same("location", "located", "situated", "place", "locate");
        same("happen", "happened", "occur", "occurred", "event");
        same("work", "works", "function", "operate", "operates");
        same("car", "automobile", "vehicle");
        same("doctor", "physician");
        same("buy", "purchase");
        same("begin", "beginning", "commence");
        same("end", "ended", "finish", "finished");
        same("fast", "speed", "quick", "quickly");
        same("film", "movie");
        same("country", "nation");
        same("job", "occupation", "profession", "career");
    }

    private static void same(String to, String... words) {
        SAME.put(to, to);
        for (String w : words) {
            SAME.put(w, to);
        }
    }

    private final File file;
    private final List<Fact> facts = new ArrayList<Fact>();

    public Knowledge(File file) {
        this.file = file;
        load();
    }

    public synchronized int size() {
        int n = 0;
        for (Fact f : facts) {
            if (f.right) {
                n++;
            }
        }
        return n;
    }

    /** The pilot confirmed an answer (thumbs up): it is kept for this intent, replacing an older one. */
    public synchronized void confirm(String question, String answer) {
        keep(question, answer, true);
    }

    /** The pilot said the answer was wrong (thumbs down): it is never offered for this intent again. */
    public synchronized void reject(String question, String answer) {
        keep(question, answer, false);
    }

    private void keep(String question, String answer, boolean right) {
        String a = answer == null ? "" : answer.trim();
        List<String> meaning = meaning(question);
        if (meaning.isEmpty() || a.isEmpty()) {
            return;
        }
        Kind k = kind(question);
        for (int i = facts.size() - 1; i >= 0; i--) {
            Fact f = facts.get(i);
            boolean sameIntent = f.kind == k && f.meaning.equals(meaning);
            if (sameIntent && (right || f.answer.equals(a))) {
                facts.remove(i); // a newer confirmed answer replaces the old; the same wrong answer is kept once
            }
        }
        facts.add(new Fact(question.trim(), a, k, meaning, right));
        while (facts.size() > MOST) {
            facts.remove(0);
        }
        save();
    }

    /** The confirmed answer for a question with this intent, however it is worded, or null. */
    public synchronized Fact recall(String question) {
        List<String> m = meaning(question);
        if (m.isEmpty()) {
            return null;
        }
        Kind k = kind(question);
        Fact best = null;
        double top = 0;
        for (Fact f : facts) {
            if (!f.right || !compatible(k, f.kind)) {
                continue;
            }
            double s = similarity(m, f.meaning);
            if (s > top) {
                top = s;
                best = f;
            }
        }
        return top >= CLOSE ? best : null;
    }

    /** True when the pilot said this answer was wrong for a question with this intent. */
    public synchronized boolean rejected(String question, String answer) {
        List<String> m = meaning(question);
        String a = answer == null ? "" : answer.trim();
        for (Fact f : facts) {
            if (!f.right && f.answer.equals(a) && similarity(m, f.meaning) >= CLOSE) {
                return true;
            }
        }
        return false;
    }

    /** The confirmed answers, oldest first. */
    public synchronized List<Fact> confirmed() {
        List<Fact> out = new ArrayList<Fact>();
        for (Fact f : facts) {
            if (f.right) {
                out.add(f);
            }
        }
        return out;
    }

    /** Forgets one confirmed answer (by the question it was confirmed for). */
    public synchronized boolean forget(String question) {
        boolean gone = facts.removeIf(f -> f.right && f.question.equals(question));
        if (gone) {
            save();
        }
        return gone;
    }

    public synchronized void clear() {
        facts.clear();
        save();
    }

    // ------------------------------------------------------------ meaning

    /** What kind of answer the question wants. */
    public static Kind kind(String q) {
        String t = " " + (q == null ? "" : q).toLowerCase(Locale.US).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ") + " ";
        if (t.matches(".* how (many|much|old|long|far|tall|big|high|often) .*")) {
            return Kind.COUNT;
        }
        if (t.matches(".* (who|whom|whose|who's) .*") || t.matches(".* (name of the|name the) (person|man|woman) .*")) {
            return Kind.WHO;
        }
        if (t.matches(".* (when|what year|what date|which year|what time) .*")) {
            return Kind.WHEN;
        }
        if (t.matches(".* (where|which country|which city|what country|what city) .*")) {
            return Kind.WHERE;
        }
        if (t.matches(".* why .*")) {
            return Kind.WHY;
        }
        if (t.matches(".* how .*")) {
            return Kind.HOW;
        }
        if (t.matches(".* (what|which|what's|whats|define|meaning|tell me about|explain) .*")) {
            return Kind.WHAT;
        }
        if (t.matches("^ (is|are|was|were|do|does|did|can|could|will|would|should|has|have) .*")) {
            return Kind.YESNO;
        }
        return Kind.OTHER;
    }

    /** "What"/"other" questions match anything but a clearly different kind; the rest must agree. */
    static boolean compatible(Kind a, Kind b) {
        if (a == b) {
            return true;
        }
        boolean loose = a == Kind.WHAT || a == Kind.OTHER;
        boolean looseB = b == Kind.WHAT || b == Kind.OTHER;
        return loose && looseB;
    }

    /** The question's meaning words: fillers out, endings off, synonyms folded, sorted. */
    public static List<String> meaning(String text) {
        Set<String> out = new LinkedHashSet<String>();
        String t = (text == null ? "" : text).toLowerCase(Locale.US).replace("'s ", " ").replace("’s ", " ");
        for (String w : t.split("[^\\p{L}\\p{N}]+")) {
            if (w.isEmpty() || STOP.contains(w)) {
                continue;
            }
            String s = SAME.containsKey(w) ? SAME.get(w) : stem(w);
            s = SAME.containsKey(s) ? SAME.get(s) : s;
            if (s.length() >= 2 && !STOP.contains(s)) {
                out.add(s);
            }
        }
        List<String> sorted = new ArrayList<String>(out);
        java.util.Collections.sort(sorted);
        return sorted;
    }

    /** A light stemmer: the common English endings come off ("countries" → "country", "running" → "run"). */
    static String stem(String w) {
        if (w.length() <= 3 || w.matches("\\d+")) {
            return w;
        }
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("ing") && w.length() > 5) {
            String b = w.substring(0, w.length() - 3);
            return undouble(b);
        }
        if (w.endsWith("ed") && w.length() > 4) {
            String b = w.substring(0, w.length() - 2);
            return undouble(b);
        }
        if (w.endsWith("es") && w.length() > 4 && w.matches(".*(ss|sh|ch|x|z)es")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static String undouble(String b) {
        int n = b.length();
        if (n > 2 && b.charAt(n - 1) == b.charAt(n - 2) && "lsz".indexOf(b.charAt(n - 1)) < 0) {
            return b.substring(0, n - 1);
        }
        return b;
    }

    /**
     * How alike two meanings are, 0 to 1: shared words count fully, a word one typo away counts nearly, and a word
     * only one side has counts against (Dice coefficient over the matched words).
     */
    static double similarity(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        double matched = 0;
        Set<String> used = new HashSet<String>();
        for (String x : a) {
            double best = 0;
            String pick = null;
            for (String y : b) {
                if (used.contains(y)) {
                    continue;
                }
                double s = x.equals(y) ? 1 : x.length() >= 5 && y.length() >= 5 && edits(x, y) <= 1 ? 0.9
                        : x.length() >= 4 && y.length() >= 4 && (x.startsWith(y) || y.startsWith(x)) ? 0.8 : 0;
                if (s > best) {
                    best = s;
                    pick = y;
                }
            }
            if (pick != null) {
                used.add(pick);
                matched += best;
            }
        }
        return 2 * matched / (a.size() + b.size());
    }

    /** Edit distance, stopping early past 2. */
    static int edits(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 2) {
            return 3;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    // ------------------------------------------------------------ file

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 4) {
                    continue;
                }
                Kind k;
                try {
                    k = Kind.valueOf(p[1]);
                } catch (IllegalArgumentException e) {
                    k = Kind.OTHER;
                }
                String q = p[2].replace(' ', '\n');
                facts.add(new Fact(q, p[3].replace(' ', '\n'), k, meaning(q), "+".equals(p[0])));
            }
        } catch (IOException e) {
            facts.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Fact f : facts) {
            b.append(f.right ? "+" : "-").append('\t').append(f.kind.name()).append('\t')
                    .append(f.question.replace('\n', ' ').replace('\t', ' ')).append('\t')
                    .append(f.answer.replace('\n', ' ').replace('\t', ' ')).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }
}
