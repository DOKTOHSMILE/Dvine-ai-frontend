package ai.dvine.dvine1.formulas;

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
 * Learning from its own interactions: every reply Ibiko gives is judged by what the pilot does next, with no tap
 * needed, and the lesson is kept for the next reply to a request of the same kind. Pure text and arithmetic.
 *
 * <pre>
 * R1  outcome         the pilot's next message says how the last reply went:
 *                       corrected  "no", "wrong", "that's not", "I meant", "actually", "you missed"     score -2
 *                       retried    "try again", "redo", or the same request asked again (similarity >= 0.75) -1
 *                       restyled   "shorter", "more detail", "simpler", "in a list", "no lists"         style lesson
 *                       accepted   "thanks", "great", "perfect", "that works", or a new request          +1
 * R2  record          per kind of request: the running score, and the last 3 lessons (what the pilot said when it
 *                     was corrected, in their words, private details masked)
 * R3  style           the pilot's taste across all replies: length (shorter / longer) and form (list / prose), each a
 *                     count that moves by one; a preference counts once it leads by 2
 * R4  trust           a kind with score <= -2 is told to slow down: check facts, ask when unsure, show its working
 * R5  check / cross   the pilot's own tick or cross counts double (+2 / -3)
 * R6  forgetting      each new outcome for a kind multiplies its score by 0.9, so old mistakes stop weighing forever
 * R7  apply           every brain gets, with the request, the lessons for its kind and the pilot's taste
 * </pre>
 * Kept in {@code brain/reflection.tsv}.
 */
public final class Reflection {
    public enum Outcome { ACCEPTED, CORRECTED, RETRIED, RESTYLED, NONE }

    static final java.util.regex.Pattern CORRECTED = java.util.regex.Pattern.compile("(?i)^\\s*(no\\b|nope\\b|wrong\\b|that'?s (not|wrong|incorrect)"
            + "|that is (not|wrong|incorrect)|incorrect\\b|not (what|right|correct)|i meant\\b|actually\\b|you (missed|forgot|got it wrong)"
            + "|you didn'?t\\b|it'?s not\\b|that doesn'?t work|still (wrong|broken|not))");
    static final java.util.regex.Pattern RETRIED = java.util.regex.Pattern.compile("(?i)^\\s*(try again|again|redo|retry|regenerate|one more time)\\b");
    static final java.util.regex.Pattern ACCEPTED = java.util.regex.Pattern.compile("(?i)\\b(thanks|thank you|great|perfect|nice|awesome"
            + "|that works|it works|good job|well done|exactly|correct|right)\\b");
    static final java.util.regex.Pattern SHORTER = java.util.regex.Pattern.compile("(?i)\\b(shorter|too long|in short|summari[sz]e it|briefly|less detail|tl;?dr)\\b");
    static final java.util.regex.Pattern LONGER = java.util.regex.Pattern.compile("(?i)\\b(more detail|explain more|longer|go deeper|elaborate|expand)\\b");
    static final java.util.regex.Pattern LIST = java.util.regex.Pattern.compile("(?i)\\b(in a list|as a list|bullet points?|step by step)\\b");
    static final java.util.regex.Pattern PROSE = java.util.regex.Pattern.compile("(?i)\\b(no (lists|bullets)|in prose|in a paragraph|as a paragraph)\\b");
    static final java.util.regex.Pattern SIMPLER = java.util.regex.Pattern.compile("(?i)\\b(simpler|in plain words|like i'?m (five|5)|too technical|easier)\\b");

    /** R6. R4. R1 similarity. */
    public static final double FADE = 0.9, CAREFUL = -2, SAME = 0.75;

    private final File file;
    private final Map<String, Double> score = new LinkedHashMap<String, Double>();
    private final Map<String, List<String>> lessons = new LinkedHashMap<String, List<String>>();
    private int shorter, longer, list, prose, simpler;

    public Reflection(File file) {
        this.file = file;
        load();
    }

    /** R1: how the last reply went, read from the pilot's next message. */
    public static Outcome outcome(String lastRequest, String next) {
        String n = next == null ? "" : next.trim();
        if (n.isEmpty()) {
            return Outcome.NONE;
        }
        if (CORRECTED.matcher(n).find()) {
            return Outcome.CORRECTED;
        }
        if (RETRIED.matcher(n).find() || similar(lastRequest, n) >= SAME) {
            return Outcome.RETRIED;
        }
        if (SHORTER.matcher(n).find() || LONGER.matcher(n).find() || LIST.matcher(n).find() || PROSE.matcher(n).find()
                || SIMPLER.matcher(n).find()) {
            return Outcome.RESTYLED;
        }
        return Outcome.ACCEPTED; // thanks, or the pilot moved on to something new: the reply did its job
    }

    /** R1–R3, R6: the pilot's next message judges the last reply to a request of {@code kind}. */
    public synchronized Outcome observe(String kind, String lastRequest, String next) {
        Outcome o = outcome(lastRequest, next);
        if (o == Outcome.NONE || kind == null) {
            return o;
        }
        String n = next.trim();
        if (o == Outcome.RESTYLED || o == Outcome.CORRECTED) {
            shorter += SHORTER.matcher(n).find() ? 1 : 0;
            longer += LONGER.matcher(n).find() ? 1 : 0;
            list += LIST.matcher(n).find() ? 1 : 0;
            prose += PROSE.matcher(n).find() ? 1 : 0;
            simpler += SIMPLER.matcher(n).find() ? 1 : 0;
        }
        double delta = o == Outcome.CORRECTED ? -2 : o == Outcome.RETRIED ? -1 : o == Outcome.ACCEPTED ? 1 : 0;
        bump(kind, delta);
        if (o == Outcome.CORRECTED || o == Outcome.RETRIED) {
            String lesson = (o == Outcome.CORRECTED ? "corrected: “" : "asked again: “") + clip(Shapes.mask(n), 140) + "”";
            List<String> l = lessons.get(kind);
            if (l == null) {
                l = new ArrayList<String>();
                lessons.put(kind, l);
            }
            l.add(lesson);
            while (l.size() > 3) {
                l.remove(0);
            }
        }
        save();
        return o;
    }

    /** R5: the pilot's own tick or cross. */
    public synchronized void judged(String kind, boolean right) {
        bump(kind, right ? 2 : -3);
        save();
    }

    private void bump(String kind, double delta) {
        double s = score.containsKey(kind) ? score.get(kind) * FADE : 0;
        score.put(kind, s + delta);
    }

    public synchronized double score(String kind) {
        return score.containsKey(kind) ? score.get(kind) : 0;
    }

    /** R3: the pilot's taste, as words for the brain; "" when nothing leads. */
    public synchronized String taste() {
        List<String> t = new ArrayList<String>();
        if (shorter - longer >= 2) {
            t.add("keep replies short");
        } else if (longer - shorter >= 2) {
            t.add("give fuller, more detailed replies");
        }
        if (list - prose >= 2) {
            t.add("use short lists for steps and options");
        } else if (prose - list >= 2) {
            t.add("write in plain paragraphs, not lists");
        }
        if (simpler >= 2) {
            t.add("use plain, simple words");
        }
        return t.isEmpty() ? "" : String.join("; ", t);
    }

    /** R4, R7: what the brain is told with a request of {@code kind}; "" when there is nothing learned yet. */
    public synchronized String hint(String kind) {
        StringBuilder b = new StringBuilder();
        String taste = taste();
        if (!taste.isEmpty()) {
            b.append(" From this user's reactions to your earlier replies: ").append(taste).append('.');
        }
        List<String> l = kind == null ? null : lessons.get(kind);
        if (l != null && !l.isEmpty()) {
            b.append(" Earlier replies to requests like this were ").append(String.join("; ", l))
                    .append(". Learn from that and do not repeat the mistake.");
        }
        if (kind != null && score(kind) <= CAREFUL) {
            b.append(" Your replies to this kind of request have often missed: slow down, check each fact, show your working,"
                    + " and ask one short question if the request is unclear.");
        }
        return b.toString();
    }

    public synchronized int size() {
        return score.size();
    }

    public synchronized void clear() {
        score.clear();
        lessons.clear();
        shorter = longer = list = prose = simpler = 0;
        save();
    }

    /** Word overlap (Jaccard) of two messages, 0 to 1: the same request asked again scores high. */
    static double similar(String a, String b) {
        java.util.Set<String> x = words(a), y = words(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        java.util.Set<String> both = new java.util.HashSet<String>(x);
        both.retainAll(y);
        java.util.Set<String> any = new java.util.HashSet<String>(x);
        any.addAll(y);
        return both.size() / (double) any.size();
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (String w : (s == null ? "" : s.toLowerCase(Locale.US)).split("[^a-z0-9]+")) {
            if (w.length() > 2) {
                out.add(w);
            }
        }
        return out;
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t");
                if (p.length == 3 && "s".equals(p[0])) {
                    score.put(p[1], Double.parseDouble(p[2]));
                } else if (p.length == 3 && "l".equals(p[0])) {
                    List<String> l = lessons.get(p[1]);
                    if (l == null) {
                        l = new ArrayList<String>();
                        lessons.put(p[1], l);
                    }
                    l.add(p[2]);
                } else if (p.length == 6 && "t".equals(p[0])) {
                    shorter = Integer.parseInt(p[1]);
                    longer = Integer.parseInt(p[2]);
                    list = Integer.parseInt(p[3]);
                    prose = Integer.parseInt(p[4]);
                    simpler = Integer.parseInt(p[5]);
                }
            }
        } catch (IOException | RuntimeException e) {
            // starts again from the next reply
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append("t\t").append(shorter).append('\t').append(longer).append('\t').append(list).append('\t').append(prose).append('\t')
                .append(simpler).append('\n');
        for (Map.Entry<String, Double> e : score.entrySet()) {
            b.append("s\t").append(e.getKey()).append('\t').append(String.format(Locale.US, "%.4f", e.getValue())).append('\n');
        }
        for (Map.Entry<String, List<String>> e : lessons.entrySet()) {
            for (String l : e.getValue()) {
                b.append("l\t").append(e.getKey()).append('\t').append(l.replace('\t', ' ').replace('\n', ' ')).append('\n');
            }
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // kept in memory
        }
    }
}
