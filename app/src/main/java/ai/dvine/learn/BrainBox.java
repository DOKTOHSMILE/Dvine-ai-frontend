package ai.dvine.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DVINE's own light brain: it learns how the user's words become actions from every model it works with, so the
 * next time the same kind of message comes it acts at once, without asking a model (a device model can take minutes
 * to route one sentence).
 *
 * <p>Each time a model turns a message into an order ("DO TIME", "DO SEARCH weather in Lagos", "DO TOOL
 * github.issues me/app"), the words and the order line are kept. A new message is compared with what was learned by
 * the words they share, rare words counting more than common ones (TF-IDF cosine). A close match whose order needs
 * no words of its own (the time, a greeting) is answered at once; an order that carries the user's words (a search,
 * a command, a tool argument) is reused only for the same words, so a learned "search the weather" never answers
 * "search the news". Everything else still goes to the model, and its answer teaches the box.
 *
 * <p>Kept on the device in one small file, newest last, at most {@link #MOST} examples. Pure Java.
 */
public final class BrainBox {
    /** How many examples are kept; the oldest goes first. */
    public static final int MOST = 2000;
    /** How close a message must be to a learned one to act on it without a model. */
    static final double CLOSE = 0.86;
    /** Order words that carry nothing of the user's: safe to reuse for a close message. */
    private static final Set<String> NO_ARGUMENT = new HashSet<String>(java.util.Arrays.asList("TIME", "GREET", "FINDZIP", "UNZIP"));
    /** Order words never learned: talk is the model's to write, a refusal is not an action. */
    private static final Set<String> NOT_ACTIONS = new HashSet<String>(java.util.Arrays.asList("SAY", "CANNOT"));
    private static final Set<String> STOP = new HashSet<String>(java.util.Arrays.asList(
            "a", "an", "the", "please", "pls", "can", "could", "would", "you", "u", "me", "my", "i", "to", "for", "of",
            "is", "are", "it", "this", "that", "and", "or", "on", "in", "at", "be", "do", "just", "now", "kindly", "hey",
            "dvine", "ivie", "obi", "ibiko", "what", "whats"));

    /** One learned example: the words and the order the model chose for them. */
    static final class Example {
        final List<String> words;
        final String order;

        Example(List<String> words, String order) {
            this.words = words;
            this.order = order;
        }
    }

    private final File file;
    private final List<Example> examples = new ArrayList<Example>();

    public BrainBox(File file) {
        this.file = file;
        load();
    }

    /** How many examples it has learned. */
    public synchronized int size() {
        return examples.size();
    }

    /**
     * Learns from a model's routing: {@code text} the user's words, {@code orderLine} the order the model wrote ("DO
     * SEARCH weather in Lagos"). Talk and refusals are not learned. The same words learned again replace the old order.
     */
    public synchronized void learn(String text, String orderLine) {
        String order = orderLine == null ? "" : orderLine.trim();
        int nl = order.indexOf('\n');
        if (nl >= 0) {
            order = order.substring(0, nl).trim();
        }
        String kind = kind(order);
        List<String> words = words(text);
        if (kind.isEmpty() || NOT_ACTIONS.contains(kind) || words.isEmpty() || order.length() > 400) {
            return;
        }
        for (int i = examples.size() - 1; i >= 0; i--) {
            if (examples.get(i).words.equals(words)) {
                examples.remove(i); // the newest order for these words wins
            }
        }
        examples.add(new Example(words, order));
        while (examples.size() > MOST) {
            examples.remove(0);
        }
        save();
    }

    /**
     * The order learned for a message like {@code text}, or null when nothing learned is close enough (the model then
     * decides). An order with the user's own words in it is returned only for the same words.
     */
    public synchronized String recall(String text) {
        List<String> words = words(text);
        if (words.isEmpty() || examples.isEmpty()) {
            return null;
        }
        Map<String, Integer> df = new HashMap<String, Integer>();
        for (Example e : examples) {
            for (String w : new HashSet<String>(e.words)) {
                Integer n = df.get(w);
                df.put(w, n == null ? 1 : n + 1);
            }
        }
        int docs = examples.size();
        Example best = null;
        double bestScore = 0;
        for (int i = examples.size() - 1; i >= 0; i--) { // newest first: a tie goes to what was learned last
            Example e = examples.get(i);
            double s = cosine(words, e.words, df, docs);
            if (s > bestScore + 1e-9) {
                bestScore = s;
                best = e;
            }
        }
        if (best == null) {
            return null;
        }
        boolean same = best.words.equals(words);
        if (same) {
            return best.order;
        }
        return bestScore >= CLOSE && NO_ARGUMENT.contains(kind(best.order)) ? best.order : null;
    }

    /** Forgets everything learned. */
    public synchronized void clear() {
        examples.clear();
        save();
    }

    // ------------------------------------------------------------ pieces

    /** The order word: "DO SEARCH x" is SEARCH, "CANNOT …" is CANNOT, "SAY …" is SAY. */
    static String kind(String order) {
        String[] p = order.trim().split("\\s+");
        if (p.length == 0 || p[0].isEmpty()) {
            return "";
        }
        String first = p[0].toUpperCase(Locale.US);
        if (first.equals("DO")) {
            return p.length > 1 ? p[1].toUpperCase(Locale.US).replaceAll("[^A-Z]", "") : "";
        }
        return first.replaceAll("[^A-Z]", "");
    }

    /** The message's meaningful words, lower case, in a fixed order, with the filler left out. */
    static List<String> words(String text) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : (text == null ? "" : text).toLowerCase(Locale.US).split("[^\\p{L}\\p{N}./_-]+")) {
            String t = w.replaceAll("^[./_-]+|[./_-]+$", "");
            if (t.length() < 2 || STOP.contains(t)) {
                continue;
            }
            out.add(t.length() > 4 && t.endsWith("s") && !t.endsWith("ss") ? t.substring(0, t.length() - 1) : t);
        }
        List<String> sorted = new ArrayList<String>(out);
        java.util.Collections.sort(sorted);
        return sorted;
    }

    private static double cosine(List<String> a, List<String> b, Map<String, Integer> df, int docs) {
        Set<String> sb = new HashSet<String>(b);
        double dot = 0, na = 0, nb = 0;
        for (String w : a) {
            double idf = idf(w, df, docs);
            na += idf * idf;
            if (sb.contains(w)) {
                dot += idf * idf;
            }
        }
        for (String w : b) {
            double idf = idf(w, df, docs);
            nb += idf * idf;
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    private static double idf(String w, Map<String, Integer> df, int docs) {
        Integer n = df.get(w);
        return Math.log(1.0 + (docs + 1.0) / ((n == null ? 0 : n) + 1.0));
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                List<String> words = new ArrayList<String>(java.util.Arrays.asList(line.substring(0, tab).split(" ")));
                examples.add(new Example(words, line.substring(tab + 1)));
            }
        } catch (IOException e) {
            examples.clear(); // an unreadable file starts the box empty, it never stops the app
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Example e : examples) {
            b.append(String.join(" ", e.words)).append('\t').append(e.order.replace('\n', ' ').replace('\t', ' ')).append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (IOException ignored) {
            // what was learned stays in memory for this run
        }
    }
}
