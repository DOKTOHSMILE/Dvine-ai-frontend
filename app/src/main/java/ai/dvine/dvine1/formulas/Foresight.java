package ai.dvine.dvine1.formulas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Premeditation: thinking ahead before acting. Two parts. A habit model, learned from the pilot's own messages, says
 * what they are likely to need next after a request like this one; and a check the brain runs before it answers or
 * acts says what the request is for, what could go wrong and what comes after it. Pure arithmetic and text.
 *
 * <pre>
 * P1  transition        after a request of kind A, the next request of kind B:  n(A to B) += 1
 * P2  forgetting        each new transition from A first multiplies every n(A to *) by 0.95, so habits that stop fade
 * P3  likelihood        P(B | A) = (n(A to B) + 1) / (n(A) + k + 1)   k = how many kinds have followed A (Laplace)
 * P4  confident         a prediction is offered only when n(A) >= 2.5 (about three sightings after P2) and P(B | A) >= 0.40
 * P5  look before       before answering or acting, the brain weighs: what is asked; what it is for; what could go
 *                       wrong (cost, loss, safety, a step that cannot be undone); what will be needed next
 * P6  offer, not act    a predicted next step is offered in one short line; it is never carried out unasked
 * P7  same thread       a gap of more than 6 hours between two messages is a new session: no transition is counted
 * </pre>
 *
 * The kind of a request is its answer type and how it opens ("how:how do i", "other:fix the"), so one habit serves every
 * request of that kind. Kept in {@code brain/foresight.tsv}.
 */
public final class Foresight {
    /** P2, P4, P7. */
    public static final double FADE = 0.95, SURE = 0.40;
    public static final double SEEN = 2.5;
    public static final long SESSION_MS = 6L * 60 * 60 * 1000;

    /** P5: the check the brain runs before it answers or acts. */
    public static final String LOOK_BEFORE = " Think ahead before you answer or act (premeditate): silently weigh what the"
            + " user is really asking, what it is for, what could go wrong (cost, lost data, safety, a step that cannot be"
            + " undone) and what they will need next. Let that shape the answer: warn about a real risk in one line, do the"
            + " safe order of steps, and prepare what the next step needs. Do not write out this weighing.";

    /** One predicted next request. */
    public static final class Guess {
        public final String kind, example;
        public final double p;
        public final int seen;

        Guess(String kind, String example, double p, int seen) {
            this.kind = kind;
            this.example = example;
            this.p = p;
            this.seen = seen;
        }
    }

    private final File file;
    /** n(A to B), A then B joined by a tab. */
    private final Map<String, Double> counts = new LinkedHashMap<String, Double>();
    /** The last words seen of each kind: how a predicted request is put to the pilot. */
    private final Map<String, String> examples = new LinkedHashMap<String, String>();
    private String lastKind;
    private long lastAt;

    public Foresight(File file) {
        this.file = file;
        load();
    }

    /** P1, P2, P7: one of the pilot's messages, at {@code now}. */
    public synchronized void heard(String message, String kind, long now) {
        if (kind == null || kind.isEmpty() || message == null || message.trim().isEmpty()) {
            return;
        }
        if (lastKind != null && now - lastAt <= SESSION_MS) {
            for (Map.Entry<String, Double> e : counts.entrySet()) {
                if (e.getKey().startsWith(lastKind + "\t")) {
                    e.setValue(e.getValue() * FADE);
                }
            }
            String key = lastKind + "\t" + kind;
            counts.put(key, (counts.containsKey(key) ? counts.get(key) : 0) + 1);
        }
        examples.put(kind, clip(Shapes.mask(message.trim()), 120));
        lastKind = kind;
        lastAt = now;
        save();
    }

    /** P3, P4: the most likely next request after one of {@code kind}, or null when no habit is sure enough. */
    public synchronized Guess next(String kind) {
        double all = 0;
        int k = 0;
        String best = null;
        double bestN = 0;
        for (Map.Entry<String, Double> e : counts.entrySet()) {
            String[] ab = e.getKey().split("\t", 2);
            if (!ab[0].equals(kind)) {
                continue;
            }
            all += e.getValue();
            k++;
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = ab[1];
            }
        }
        if (best == null || all < SEEN) {
            return null;
        }
        double p = (bestN + 1) / (all + k + 1);
        if (p < SURE) {
            return null;
        }
        return new Guess(best, examples.get(best), p, (int) Math.ceil(all));
    }

    /** P5 and P6 for any brain, with the request: the look-before check, and the likely next step when it is sure. */
    public synchronized String hint(String kind) {
        Guess g = kind == null ? null : next(kind);
        return LOOK_BEFORE + (g == null || g.example == null ? "" : " From this user's habits, after a request like this they"
                + " usually go on to something like: “" + g.example + "” (" + Math.round(g.p * 100) + "% of " + g.seen
                + " times). If it fits, end with one short offer to do that next; never do it unasked.");
    }

    public synchronized int size() {
        return counts.size();
    }

    public synchronized void clear() {
        counts.clear();
        examples.clear();
        lastKind = null;
        save();
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
                if (p.length == 4 && "n".equals(p[0])) {
                    counts.put(p[1] + "\t" + p[2], Double.parseDouble(p[3]));
                } else if (p.length == 3 && "e".equals(p[0])) {
                    examples.put(p[1], p[2]);
                }
            }
        } catch (IOException | RuntimeException e) {
            // starts again from what it hears next
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Double> e : counts.entrySet()) {
            b.append("n\t").append(e.getKey()).append('\t').append(String.format(Locale.US, "%.4f", e.getValue())).append('\n');
        }
        for (Map.Entry<String, String> e : examples.entrySet()) {
            b.append("e\t").append(e.getKey()).append('\t').append(e.getValue().replace('\t', ' ').replace('\n', ' ')).append('\n');
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
