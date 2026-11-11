package ai.ibiko.ibiko1.formulas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Learning how the other brains think, not only what they answer. The reasoning they show in the live chat (a Deep
 * Thinking plan and review, the working steps, the reasoning written into a reply: "first…, then…, so…") is read
 * for its moves, in order; the moves are kept per kind of request; and Ibiko 1's own thinking for a request of that
 * kind follows the pattern seen most, so its thought process adapts to what worked instead of being a fixed list.
 *
 * <pre>
 * T1  moves          each sentence is one move by its cue words: understand, recall, plan, look up, compare,
 *                    consider (other options), compute, check, conclude; a sentence with none is skipped
 * T2  pattern        a request's moves in order, a repeat of the move just before it dropped, at most 6
 * T3  keep           per kind of request, each pattern seen is counted; at most 8 patterns a kind, the weakest
 *                    forgotten first
 * T4  follow         a pattern is followed once seen twice; the most seen wins, the newest on a tie
 * T5  fade           each new pattern for a kind multiplies the others' counts by 0.9, so the way of thinking
 *                    follows how the brains think now
 * T6  said           each move is shown as a step in Ibiko 1's own words, ending in its own last steps
 * </pre>
 *
 * Kept in {@code brain/thinking.tsv}. Pure text; no model.
 */
public final class Thinking {
    /** T1. */
    public enum Move {
        UNDERSTAND("Working out what's really asked", "(?i)\\b(the (user|question|request) (is |are )?(ask|want|need|mean)|asks? (for|about)"
                + "|they want|what is (really )?being asked|restat|in other words|the goal is|clarif)"),
        RECALL("Recalling what I know about it", "(?i)\\b(i (know|recall|remember)|known|from memory|by definition|as a rule"
                + "|recall that|background)"),
        PLAN("Planning the steps", "(?i)\\b(first(ly)?|plan|step \\d|steps?:|then i|next,? i|approach)"),
        LOOK_UP("Looking it up", "(?i)\\b(search|look(ed|ing)? (it )?up|read (the )?(page|source|doc|file)s?|according to|source)"),
        COMPARE("Comparing the options", "(?i)\\b(compare|comparing|versus|vs\\.?|whereas|on the other hand|difference between"
                + "|bigger|smaller|better|worse)"),
        CONSIDER("Weighing other possibilities", "(?i)\\b(alternatively|or maybe|could also|another (option|possibility|way)"
                + "|unless|however|but what if|consider|edge case|counter)"),
        COMPUTE("Working out the numbers", "(?i)(\\d+\\s*[-+*/x×=]\\s*\\d+|\\b(calculat|comput|multipl|divid|sum|total|percent|average))"),
        CHECK("Checking it holds", "(?i)\\b(check|verif|make sure|double-check|confirm|sanity|test (it|that)|does that hold"
                + "|is that right|wait)"),
        CONCLUDE("Putting the answer together", "(?i)\\b(therefore|so the answer|in conclusion|to sum up|overall|final answer"
                + "|so,|thus|hence)");

        public final String said;
        final java.util.regex.Pattern cue;

        Move(String said, String cue) {
            this.said = said;
            this.cue = java.util.regex.Pattern.compile(cue);
        }
    }

    public static final int MOST_MOVES = 6, MOST_PATTERNS = 8, FOLLOW_AT = 2;
    public static final double FADE = 0.9;

    private final File file;
    /** kind → pattern ("UNDERSTAND,RECALL,CHECK") → weight, in the order first seen. */
    private final Map<String, LinkedHashMap<String, Double>> kinds = new LinkedHashMap<String, LinkedHashMap<String, Double>>();

    public Thinking(File file) {
        this.file = file;
        load();
    }

    /** T1, T2: the moves in a piece of reasoning, in order. */
    public static List<Move> moves(String text) {
        List<Move> out = new ArrayList<Move>();
        if (text == null) {
            return out;
        }
        for (String sentence : text.split("(?<=[.!?:])\\s+|\\n+")) {
            if (sentence.trim().length() < 8) {
                continue;
            }
            for (Move m : Move.values()) {
                if (m.cue.matcher(sentence).find()) {
                    if (out.isEmpty() || out.get(out.size() - 1) != m) {
                        out.add(m);
                    }
                    break;
                }
            }
            if (out.size() >= MOST_MOVES) {
                break;
            }
        }
        return out;
    }

    /** T3, T5: one piece of reasoning seen for a request of {@code kind}. False when it had fewer than two moves. */
    public synchronized boolean learn(String kind, String reasoning) {
        List<Move> m = moves(reasoning);
        if (kind == null || m.size() < 2) {
            return false;
        }
        String key = join(m);
        LinkedHashMap<String, Double> of = kinds.get(kind);
        if (of == null) {
            of = new LinkedHashMap<String, Double>();
            kinds.put(kind, of);
        }
        Double was = of.remove(key);
        for (Map.Entry<String, Double> e : of.entrySet()) {
            e.setValue(e.getValue() * FADE); // T5
        }
        of.put(key, (was == null ? 0 : was) + 1); // put back last: the newest
        if (of.size() > MOST_PATTERNS) {
            String weakest = null;
            for (Map.Entry<String, Double> e : of.entrySet()) {
                if (weakest == null || e.getValue() < of.get(weakest)) {
                    weakest = e.getKey();
                }
            }
            of.remove(weakest);
        }
        save();
        return true;
    }

    /** T4: the pattern to follow for {@code kind}, or null when none has been seen twice. */
    public synchronized List<Move> pattern(String kind) {
        LinkedHashMap<String, Double> of = kind == null ? null : kinds.get(kind);
        if (of == null) {
            return null;
        }
        String best = null;
        double w = 0;
        for (Map.Entry<String, Double> e : of.entrySet()) {
            if (e.getValue() >= w) { // the later (newer) wins a tie
                best = e.getKey();
                w = e.getValue();
            }
        }
        if (best == null || w < FOLLOW_AT - 0.75) { // two sightings, after a little fading
            return null;
        }
        List<Move> out = new ArrayList<Move>();
        for (String s : best.split(",")) {
            out.add(Move.valueOf(s));
        }
        return out;
    }

    /** T6: the steps Ibiko 1 shows for a request of {@code kind}: the learned moves, then its own last steps. */
    public List<String> steps(String kind, List<String> own) {
        List<Move> p = pattern(kind);
        List<String> out = new ArrayList<String>();
        if (own.isEmpty()) {
            return out;
        }
        out.add(own.get(0)); // "Reading your question"
        if (p != null) {
            for (Move m : p) {
                if (m != Move.CONCLUDE) {
                    out.add(m.said);
                }
            }
        }
        for (int i = 1; i < own.size(); i++) {
            if (!out.contains(own.get(i))) {
                out.add(own.get(i));
            }
        }
        return out;
    }

    public synchronized int size() {
        return kinds.size();
    }

    private static String join(List<Move> m) {
        StringBuilder b = new StringBuilder();
        for (Move x : m) {
            b.append(b.length() == 0 ? "" : ",").append(x.name());
        }
        return b.toString();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t");
                if (p.length != 3) {
                    continue;
                }
                LinkedHashMap<String, Double> of = kinds.get(p[0]);
                if (of == null) {
                    of = new LinkedHashMap<String, Double>();
                    kinds.put(p[0], of);
                }
                double w = Double.parseDouble(p[2]);
                of.put(p[1], w);
            }
        } catch (IOException | RuntimeException ignored) {
            // what was read stays
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, LinkedHashMap<String, Double>> k : kinds.entrySet()) {
            for (Map.Entry<String, Double> e : k.getValue().entrySet()) {
                b.append(k.getKey()).append('\t').append(e.getKey()).append('\t')
                        .append(String.format(Locale.US, "%.3f", e.getValue())).append('\n');
            }
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory
        }
    }
}
