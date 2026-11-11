package ai.ibiko.dvine1.comprehend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Words and their meanings, offline: Open English WordNet 2024 (CC BY 4.0), kept as knowledge/lexicon.tsv and read by
 * {@link SortedTable}. A word's senses (part of speech, definition, synonyms, opposites, the broader term), most common
 * first; its base form found from what was written (tyres, removed, running, bigger). Pure Java.
 */
public final class Dictionary {
    /** One meaning of a word. */
    public static final class Sense {
        public final String word, pos, definition, broader;
        public final List<String> synonyms, opposites;

        Sense(String[] c) {
            word = c[0];
            pos = c.length > 1 ? c[1] : "";
            definition = c.length > 2 ? c[2] : "";
            synonyms = list(c.length > 3 ? c[3] : "");
            opposites = list(c.length > 4 ? c[4] : "");
            broader = c.length > 5 ? c[5] : "";
        }

        private static List<String> list(String s) {
            List<String> out = new ArrayList<String>();
            for (String p : s.split(",")) {
                if (!p.trim().isEmpty()) {
                    out.add(p.trim());
                }
            }
            return out;
        }
    }

    private final SortedTable table;

    public Dictionary(SortedTable table) {
        this.table = table;
    }

    public boolean present() {
        return table != null && table.present();
    }

    /** The senses of exactly this written form (lower case). */
    public List<Sense> exact(String word) {
        List<Sense> out = new ArrayList<Sense>();
        if (table == null || word == null) {
            return out;
        }
        for (String[] c : table.rows(word.trim().toLowerCase(Locale.US))) {
            out.add(new Sense(c));
        }
        return out;
    }

    /** The senses of a word as written or of its base form: "tyres" is "tyre", "removed" is "remove". */
    public List<Sense> senses(String word) {
        for (String base : bases(word)) {
            List<Sense> s = exact(base);
            if (!s.isEmpty()) {
                return s;
            }
        }
        return new ArrayList<Sense>();
    }

    /** The senses of one part of speech ("noun", "verb", "adjective", "adverb"). */
    public List<Sense> senses(String word, String pos) {
        List<Sense> out = new ArrayList<Sense>();
        for (String base : bases(word)) {
            for (Sense s : exact(base)) {
                if (s.pos.equals(pos)) {
                    out.add(s);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    /** The forms a word may be the inflection of, the word itself first. */
    public static List<String> bases(String word) {
        Set<String> out = new LinkedHashSet<String>();
        String w = word == null ? "" : word.trim().toLowerCase(Locale.US).replaceAll("['’]s$", "");
        if (w.isEmpty()) {
            return new ArrayList<String>();
        }
        out.add(w);
        String[] irregular = {"children", "child", "men", "man", "women", "woman", "people", "person", "mice", "mouse", "feet", "foot",
                "teeth", "tooth", "geese", "goose", "went", "go", "gone", "go", "made", "make", "took", "take", "taken", "take",
                "gave", "give", "given", "give", "bought", "buy", "brought", "bring", "thought", "think", "saw", "see", "seen", "see",
                "ran", "run", "wrote", "write", "written", "write", "built", "build", "better", "good", "best", "good", "worse", "bad",
                "worst", "bad", "did", "do", "done", "do", "was", "be", "were", "be", "is", "be", "are", "be", "has", "have", "had", "have",
                "knives", "knife", "lives", "life", "leaves", "leaf", "wives", "wife", "halves", "half", "shelves", "shelf"};
        for (int i = 0; i + 1 < irregular.length; i += 2) {
            if (w.equals(irregular[i])) {
                out.add(irregular[i + 1]);
            }
        }
        if (w.endsWith("ies") && w.length() > 4) {
            out.add(w.substring(0, w.length() - 3) + "y");
        }
        if (w.endsWith("es") && w.length() > 3) {
            out.add(w.substring(0, w.length() - 2));
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            out.add(w.substring(0, w.length() - 1));
        }
        if (w.endsWith("ied") && w.length() > 4) {
            out.add(w.substring(0, w.length() - 3) + "y");
        }
        if (w.endsWith("ed") && w.length() > 3) {
            String stem = w.substring(0, w.length() - 2);
            out.add(stem);
            out.add(stem + "e");
            if (stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                out.add(stem.substring(0, stem.length() - 1)); // stopped -> stop
            }
        }
        if (w.endsWith("ing") && w.length() > 4) {
            String stem = w.substring(0, w.length() - 3);
            out.add(stem);
            out.add(stem + "e");
            if (stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                out.add(stem.substring(0, stem.length() - 1)); // running -> run
            }
        }
        for (String end : new String[] {"est", "er"}) {
            if (w.endsWith(end) && w.length() > end.length() + 2) {
                String stem = w.substring(0, w.length() - end.length());
                out.add(stem);
                out.add(stem + "e");
                if (stem.endsWith("i")) {
                    out.add(stem.substring(0, stem.length() - 1) + "y"); // happier -> happy
                }
                if (stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                    out.add(stem.substring(0, stem.length() - 1)); // bigger -> big
                }
            }
        }
        return new ArrayList<String>(out);
    }

    /**
     * Which sense the sentence means (simplified Lesk): the one whose definition, synonyms and broader term share the
     * most words with the rest of the sentence; the most common sense when nothing tells them apart.
     */
    public Sense meant(String word, String sentence) {
        List<Sense> all = senses(word);
        if (all.isEmpty()) {
            return null;
        }
        Set<String> around = contentWords(sentence);
        around.removeAll(bases(word));
        // extended Lesk: the other words' own first definitions count too ("loan" is defined with "money", "river"
        // with "water"), at half the weight of the words themselves
        Set<String> wider = new LinkedHashSet<String>();
        for (String w : around) {
            List<Sense> ws = senses(w);
            if (!ws.isEmpty()) {
                wider.addAll(contentWords(ws.get(0).definition));
            }
        }
        Sense best = all.get(0);
        int bestScore = 0;
        for (Sense s : all) {
            Set<String> gloss = new LinkedHashSet<String>();
            for (String g : contentWords(s.definition + " " + String.join(" ", s.synonyms) + " " + s.broader)) {
                gloss.addAll(bases(g));
            }
            int score = 0;
            for (String w : around) {
                for (String b : bases(w)) {
                    if (gloss.contains(b)) {
                        score += 2;
                        break;
                    }
                }
            }
            for (String w : wider) {
                for (String b : bases(w)) {
                    if (gloss.contains(b)) {
                        score++;
                        break;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    static final Set<String> STOP = new java.util.HashSet<String>(java.util.Arrays.asList(("a an the of to in on at by for with from "
            + "and or but is are was were be been being it its this that these those my your his her their our i you he she we they "
            + "me him them us do does did have has had not no so as if then than there here what which who whom how why when where "
            + "can could will would should may might must shall just very too also into onto out up down off over under about "
            + "some any all each every one other such own same").split(" ")));

    static Set<String> contentWords(String text) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : (text == null ? "" : text.toLowerCase(Locale.US)).split("[^\\p{L}\\p{Nd}'-]+")) {
            if (w.length() > 2 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}
