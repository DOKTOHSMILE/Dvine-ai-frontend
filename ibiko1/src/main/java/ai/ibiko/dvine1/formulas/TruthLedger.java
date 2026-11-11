package ai.ibiko.dvine1.formulas;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * G9 of {@link Truth}: the truth ledger. Every sentence judged TRUE or FALSE, going in or out, is kept: what it said, the
 * verdict, the source that decided, the correction, how often it came up, where (read, replied, told by the pilot) and
 * when. Both sides are kept on purpose: the true ones are knowledge confirmed, the false ones are known mistakes,
 * recognised at once the next time they come up and told to every brain so it does not repeat them. Private details
 * are masked. Kept in {@code brain/truth.tsv}. Pure Java.
 *
 * <pre>
 * G9  ledger      record   each TRUE and FALSE judgment, once a sentence (seen again: counted, the latest kept)
 *                 recall   a sentence judged before is judged the same again, even when the source that decided
 *                          has since faded ("checked before")
 *                 warn     the known mistakes and the checked truths about a request go to every brain with it,
 *                          the mistakes first
 *                 forget   at most 3,000 kept; the least recently seen goes first
 * </pre>
 */
public final class TruthLedger implements Truth.Witness {
    static final int MOST = 3000;

    /** One sentence judged: its verdict, what decided it, the correction (FALSE), how often, where, first and last seen. */
    public static final class Entry {
        public final String sentence;
        public Truth.Verdict verdict;
        public String source, correction, way;
        public int seen;
        public long first, last;

        Entry(String sentence, Truth.Verdict verdict, String source, String correction, String way, int seen, long first, long last) {
            this.sentence = sentence;
            this.verdict = verdict;
            this.source = source;
            this.correction = correction;
            this.way = way;
            this.seen = seen;
            this.first = first;
            this.last = last;
        }
    }

    private final File file;
    private final Map<String, Entry> all = new LinkedHashMap<String, Entry>();

    public TruthLedger(File file) {
        this.file = file;
        load();
    }

    static String key(String sentence) {
        return (sentence == null ? "" : sentence).toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{Nd} ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** G9 record: the TRUE and FALSE judgments of one pass; {@code way} is "in", "out" or "pilot". */
    public synchronized void record(List<Truth.Checked> checked, String way, long now) {
        boolean changed = false;
        for (Truth.Checked c : checked) {
            if (c == null || c.verdict == Truth.Verdict.UNKNOWN || c.sentence == null) {
                continue;
            }
            String s = Shapes.mask(c.sentence.trim()).replaceAll("[\\t\\n]", " ");
            String k = key(s);
            if (k.isEmpty()) {
                continue;
            }
            Entry e = all.remove(k);
            if (e == null) {
                e = new Entry(s, c.verdict, c.source, c.correction, way, 1, now, now);
            } else {
                e.verdict = c.verdict;
                e.source = c.source;
                e.correction = c.correction;
                e.way = way;
                e.seen++;
                e.last = now;
            }
            all.put(k, e); // the latest at the end
            changed = true;
        }
        while (all.size() > MOST) {
            all.remove(all.keySet().iterator().next());
        }
        if (changed) {
            save();
        }
    }

    /** G9 recall: a sentence judged before, judged the same again. */
    @Override
    public synchronized Truth.Checked check(String sentence) {
        Entry e = all.get(key(Shapes.mask(sentence == null ? "" : sentence.trim())));
        if (e == null) {
            return Truth.Checked.unknown(sentence);
        }
        return new Truth.Checked(e.verdict, sentence, e.correction, "checked before");
    }

    /** G9 warn: the known mistakes, then the checked truths, about a request, as a line for the brain; "" when none. */
    public synchronized String about(String request, int most) {
        java.util.Set<String> words = words(request);
        if (words.isEmpty()) {
            return "";
        }
        List<Entry> wrong = new ArrayList<Entry>(), right = new ArrayList<Entry>();
        for (Entry e : all.values()) {
            java.util.Set<String> w = words(e.sentence);
            w.retainAll(words);
            if (!w.isEmpty()) {
                (e.verdict == Truth.Verdict.FALSE ? wrong : right).add(0, e); // newest first
            }
        }
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Entry e : wrong) {
            if (n++ >= most) {
                break;
            }
            b.append(b.length() == 0 ? " Checked before and found false (never repeat these):" : ";").append(" “")
                    .append(e.sentence).append("”").append(e.correction == null ? "" : ", in fact: " + e.correction);
        }
        int m = 0;
        boolean opened = false;
        for (Entry e : right) {
            if (m++ >= most) {
                break;
            }
            b.append(opened ? ";" : (b.length() == 0 ? "" : ".") + " Checked before and found true:").append(" “").append(e.sentence).append("”");
            opened = true;
        }
        return b.length() == 0 ? "" : b.append('.').toString();
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        for (String w : key(s).split(" ")) {
            if (w.length() >= 4 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static final java.util.Set<String> STOP = new java.util.HashSet<String>(java.util.Arrays.asList("what", "which", "where",
            "when", "that", "this", "with", "from", "have", "does", "about", "tell", "there", "their", "they", "your", "were", "been",
            "into", "than", "then", "them", "these", "those", "will", "would", "could", "should", "also", "just", "like", "some"));

    /** How many are kept as true and as false. */
    public synchronized int[] counts() {
        int t = 0, f = 0;
        for (Entry e : all.values()) {
            if (e.verdict == Truth.Verdict.TRUE) {
                t++;
            } else if (e.verdict == Truth.Verdict.FALSE) {
                f++;
            }
        }
        return new int[] {t, f};
    }

    /** Every entry, oldest seen first. */
    public synchronized List<Entry> entries() {
        return new ArrayList<Entry>(all.values());
    }

    /** Forgets one judgment. */
    public synchronized void forget(String sentence) {
        if (all.remove(key(sentence)) != null) {
            save();
        }
    }

    public synchronized void clear() {
        all.clear();
        save();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 8) {
                    continue;
                }
                try {
                    Entry e = new Entry(p[7], Truth.Verdict.valueOf(p[0]), p[4].isEmpty() ? null : p[4], p.length > 8 && !p[8].isEmpty() ? p[8] : null,
                            p[3], Integer.parseInt(p[1]), Long.parseLong(p[5]), Long.parseLong(p[6]));
                    all.put(key(e.sentence), e);
                } catch (IllegalArgumentException bad) {
                    // a damaged line is skipped
                }
            }
        } catch (java.io.IOException e) {
            // nothing kept yet
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Entry e : all.values()) {
            b.append(e.verdict.name()).append('\t').append(e.seen).append('\t').append("v1").append('\t').append(e.way == null ? "" : e.way)
                    .append('\t').append(e.source == null ? "" : e.source).append('\t').append(e.first).append('\t').append(e.last)
                    .append('\t').append(e.sentence).append('\t').append(e.correction == null ? "" : e.correction.replaceAll("[\\t\\n]", " "))
                    .append('\n');
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (java.io.IOException e) {
            // kept in memory; written next time
        }
    }
}
