package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a thing means to Ibiko, learned from the codes that keep coming back (N1–N6). It does not keep every answer it
 * hears about football; it keeps what the answers have in common.
 * <ul>
 * <li>N1 A topic is a content word of the question (its code).</li>
 * <li>N2 Each answer about the topic is read once as a set of codes; each code in it is counted once for the topic
 * (so one long answer can't outvote several short ones).</li>
 * <li>N3 A code that comes back in at least two answers and in at least {@link #SHARE} of them is part of what the
 * topic means ("football" → "game"); the rest are passing remarks ("boring", "nice") and stay as weaker links.</li>
 * <li>N4 Each new answer re-weighs the links: a link that stops recurring falls below the share and drops out of the
 * meaning by itself.</li>
 * <li>N5 The strength of a link is its share of the answers; it is said in words that match
 * ({@link ai.ibiko.ibiko1.formulas.Probability#words}).</li>
 * <li>N6 A thought of Ibiko's own is formed from the strongest links by their part of speech: a noun says what the
 * topic is, adjectives how it is described ({@link #thought}).</li>
 * </ul>
 * Kept in {@code concepts.tsv} beside the brain box. Pure Java.
 */
public final class Concepts {
    /** The share of answers a code must be in to be part of the meaning. */
    public static final double SHARE = 0.5;
    static final int MOST_TOPICS = 4000, MOST_LINKS = 60;

    private final File file;
    /** topic → how many answers were read about it. */
    private final Map<String, Integer> answers = new LinkedHashMap<String, Integer>();
    /** topic → code → in how many of those answers. */
    private final Map<String, Map<String, Integer>> links = new LinkedHashMap<String, Map<String, Integer>>();

    public Concepts(File file) {
        this.file = file;
        load();
    }

    /** N1–N2, N4: one answer to one question, read into the topics the question names. */
    public synchronized void observe(String question, String answer) {
        Set<String> topics = words(question), said = words(answer);
        if (topics.isEmpty() || said.size() < 1 || Corroborate.failure(answer)) {
            return;
        }
        for (String topic : topics) {
            if (!said.contains(topic) && topics.size() > 1) {
                continue; // an answer that never names this word is not about it
            }
            answers.merge(topic, 1, Integer::sum);
            Map<String, Integer> l = links.computeIfAbsent(topic, k -> new LinkedHashMap<String, Integer>());
            for (String code : said) {
                if (!code.equals(topic) && (l.containsKey(code) || l.size() < MOST_LINKS)) {
                    l.merge(code, 1, Integer::sum);
                }
            }
        }
        while (answers.size() > MOST_TOPICS) {
            String oldest = answers.keySet().iterator().next();
            answers.remove(oldest);
            links.remove(oldest);
        }
        save();
    }

    /**
     * The content words of a text as they are filed here: a word the dictionary knows is kept whole ("interesting"
     * stays an adjective, not "interest"); one it doesn't know is cut to its stem if the dictionary knows that
     * ("games" → "game").
     */
    public static Set<String> words(String text) {
        Set<String> out = new java.util.LinkedHashSet<String>();
        for (String t : Shorthand.tokens(text)) {
            String w = t.toLowerCase(java.util.Locale.US);
            if (w.length() < 3 || Codes.SMALL.contains(w) || !Character.isLetter(w.charAt(0))) {
                continue;
            }
            out.add(key(w));
        }
        return out;
    }

    static String key(String word) {
        String w = word == null ? "" : word.toLowerCase(java.util.Locale.US);
        if (WordBook.id(w) >= WordBook.FIRST) {
            return w;
        }
        String stem = Codes.stem(w);
        return WordBook.id(stem) >= WordBook.FIRST ? stem : w;
    }

    /** How many answers were read about a word. */
    public synchronized int seen(String word) {
        Integer n = answers.get(key(word));
        return n == null ? 0 : n;
    }

    /** One link of a topic: the code, and the share of the answers it was in. */
    public static final class Link {
        public final String word;
        public final int count;
        public final double share;

        Link(String word, int count, double share) {
            this.word = word;
            this.count = count;
            this.share = share;
        }
    }

    /** N3: what the topic means: the codes in at least two answers and at least {@link #SHARE} of them, strongest first. */
    public synchronized List<Link> meaning(String word) {
        return ranked(word, true);
    }

    /** Every link of the topic seen at least twice, strongest first (the meaning and the weaker remarks). */
    public synchronized List<Link> related(String word) {
        return ranked(word, false);
    }

    private List<Link> ranked(String word, boolean meaningOnly) {
        String topic = key(word);
        List<Link> out = new ArrayList<Link>();
        Integer n = answers.get(topic);
        Map<String, Integer> l = links.get(topic);
        if (n == null || l == null) {
            return out;
        }
        for (Map.Entry<String, Integer> e : l.entrySet()) {
            double share = e.getValue() / (double) n;
            if (e.getValue() >= 2 && (!meaningOnly || share >= SHARE)) {
                out.add(new Link(e.getKey(), e.getValue(), share));
            }
        }
        out.sort((a, b) -> Double.compare(b.share, a.share));
        return out;
    }

    /**
     * N6: Ibiko's own sentence about a topic from what recurred, or null when it has not heard enough (fewer than
     * three answers, or nothing recurring): "Football is a game, often described as interesting."
     */
    public synchronized String thought(String word) {
        String topic = key(word);
        if (seen(topic) < 3) {
            return null;
        }
        String noun = null;
        List<String> how = new ArrayList<String>();
        for (Link k : meaning(topic)) {
            int id = WordBook.id(k.word);
            if (noun == null && (id == 0 || WordBook.is(id, "noun"))) {
                noun = k.word; // the strongest link that can name a thing says what the topic is
            } else if (WordBook.is(id, "adjective") && !WordBook.is(id, "noun") && how.size() < 2) {
                how.add(k.word);
            }
        }
        if (noun == null) {
            return null;
        }
        for (Link k : related(topic)) {
            int id = WordBook.id(k.word);
            if (how.size() < 2 && !how.contains(k.word) && !k.word.equals(noun) && WordBook.is(id, "adjective")
                    && !WordBook.is(id, "noun")) {
                how.add(k.word);
            }
        }
        String a = "aeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ";
        String s = Character.toUpperCase(topic.charAt(0)) + topic.substring(1) + " is " + a + noun;
        return s + (how.isEmpty() ? "." : ", often described as " + String.join(" or ", how) + ".");
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : ai.ibiko.learn.CodeFile.lines(file)) {
                String[] p = line.split("\t");
                if (p.length < 2) {
                    continue;
                }
                answers.put(p[0], Integer.parseInt(p[1]));
                Map<String, Integer> l = new LinkedHashMap<String, Integer>();
                for (int i = 2; i < p.length; i++) {
                    int c = p[i].lastIndexOf(':');
                    if (c > 0) {
                        l.put(p[i].substring(0, c), Integer.parseInt(p[i].substring(c + 1)));
                    }
                }
                links.put(p[0], l);
            }
        } catch (IOException | NumberFormatException e) {
            answers.clear();
            links.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : answers.entrySet()) {
            b.append(e.getKey()).append('\t').append(e.getValue());
            Map<String, Integer> l = links.get(e.getKey());
            if (l != null) {
                for (Map.Entry<String, Integer> k : l.entrySet()) {
                    b.append('\t').append(k.getKey()).append(':').append(k.getValue());
                }
            }
            b.append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), ai.ibiko.learn.CodeFile.encode(b.toString()));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }
}
