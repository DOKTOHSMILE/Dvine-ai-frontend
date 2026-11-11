package ai.ibiko.learn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ibiko's memory codes: every sentence it keeps gets a short id ({@code <message id>#<n>}), and every sentence is
 * filed under tags, the words that say what it is about ("lagos", "invoice", "gradle"). A tag points to the ids of
 * the sentences about it.
 * <p>
 * To think about a new question Ibiko does not read back everything it has: it turns the question into tags, the tags
 * pull the ids (a lookup, not a reading), the ids are scored by how many tags they share (rare tags count more), and
 * only those few sentences are read and given to the brain. Less to read before the first word, and only what is
 * relevant. Pure Java, nothing leaves the device.
 */
public final class Codes {
    /** id → the sentence. */
    private final Map<String, String> text = new LinkedHashMap<String, String>();
    /** tag → the ids filed under it. */
    private final Map<String, List<String>> byTag = new HashMap<String, List<String>>();
    /** id → its tags. */
    private final Map<String, Set<String>> tagsOf = new HashMap<String, Set<String>>();

    /** Words that say nothing about the subject. */
    static final Set<String> SMALL = new HashSet<String>(java.util.Arrays.asList(("a an the and or but if then so to of in on"
            + " at by for with from as is are was were be been being it its this that these those there here i me my you your"
            + " we our they them their he she his her what which who whom when where why how do does did done can could will"
            + " would should may might must shall not no yes ok okay please just also very more most some any all each every"
            + " about into over than too up down out off again only own same such have has had having get got make made like"
            + " one two three now new use used using want need know see said say tell let").split(" ")));

    /** The tags of a piece of text: its content words, lower case, plural and tense endings taken off. */
    public static Set<String> tags(String s) {
        Set<String> out = new HashSet<String>();
        if (s == null) {
            return out;
        }
        for (String w : s.toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() < 3 || SMALL.contains(w)) {
                continue;
            }
            out.add(stem(w));
        }
        return out;
    }

    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 4 && w.endsWith("ed")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** The sentences of a message, each with its own id ({@code <id>#0}, {@code #1}…). */
    public static List<String[]> sentences(String id, String body) {
        List<String[]> out = new ArrayList<String[]>();
        if (body == null) {
            return out;
        }
        int n = 0;
        for (String part : body.replace('\n', ' ').split("(?<=[.!?])\\s+")) {
            String t = part.replaceAll("\\s+", " ").trim();
            if (t.length() >= 3) {
                out.add(new String[]{id + "#" + n++, t});
            }
        }
        return out;
    }

    /** Files one sentence under its id and its tags. Filing the same id again changes nothing. */
    public synchronized void add(String id, String sentence) {
        if (id == null || sentence == null || text.containsKey(id)) {
            return;
        }
        Set<String> tags = tags(sentence);
        if (tags.isEmpty()) {
            return;
        }
        text.put(id, sentence);
        tagsOf.put(id, tags);
        for (String t : tags) {
            byTag.computeIfAbsent(t, k -> new ArrayList<String>()).add(id);
        }
    }

    /** Files every sentence of a message under the message's id. */
    public synchronized void addMessage(String id, String body) {
        for (String[] s : sentences(id, body)) {
            add(s[0], s[1]);
        }
    }

    public synchronized boolean has(String id) {
        return text.containsKey(id);
    }

    public synchronized int size() {
        return text.size();
    }

    /** The sentence an id stands for, or null. */
    public synchronized String read(String id) {
        return text.get(id);
    }

    /** The ids filed under a tag. */
    public synchronized List<String> ids(String tag) {
        List<String> l = byTag.get(stem(tag.toLowerCase(Locale.US)));
        return l == null ? new ArrayList<String>() : new ArrayList<String>(l);
    }

    /**
     * The ids of the sentences most about {@code question}: its tags pull the ids, each id scores by the tags it shares,
     * a rare tag counting more than a common one; at least {@code least} shared tags (1 for a one-tag question), at most
     * {@code most} ids, best first.
     */
    public synchronized List<String> recall(String question, int most, int least) {
        Set<String> want = tags(question);
        Map<String, Double> score = new HashMap<String, Double>();
        Map<String, Integer> shared = new HashMap<String, Integer>();
        double all = Math.max(1, text.size());
        for (String t : want) {
            List<String> ids = byTag.get(t);
            if (ids == null) {
                continue;
            }
            double weight = Math.log(1 + all / ids.size()); // a rare tag says more
            for (String id : ids) {
                score.merge(id, weight, Double::sum);
                shared.merge(id, 1, Integer::sum);
            }
        }
        int need = Math.min(Math.max(1, least), Math.max(1, want.size()));
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Double> e : score.entrySet()) {
            if (shared.get(e.getKey()) >= need) {
                out.add(e.getKey());
            }
        }
        out.sort((a, b) -> Double.compare(score.get(b), score.get(a)));
        return out.size() > most ? new ArrayList<String>(out.subList(0, most)) : out;
    }

    /**
     * The recalled sentences, read back in the order they were said, each with its code, within {@code room}
     * characters: "[m1a2#0] We fixed the Gradle path."
     */
    public synchronized String readBack(List<String> ids, int room) {
        List<String> ordered = new ArrayList<String>();
        for (String id : text.keySet()) {
            if (ids.contains(id)) {
                ordered.add(id);
            }
        }
        StringBuilder b = new StringBuilder();
        for (String id : ordered) {
            String line = "[" + id + "] " + text.get(id) + "\n";
            if (b.length() + line.length() > room) {
                break;
            }
            b.append(line);
        }
        return b.toString().trim();
    }
}
