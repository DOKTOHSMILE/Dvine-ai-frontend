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

    /** One learned example: the words, the order or answer, and how often it was chosen again. */
    static final class Example {
        final List<String> words;
        final String order;
        /** How many times this answer was chosen again. */
        int hits;
        /** When it was last chosen, in milliseconds. 0 when it was never touched. */
        long touched;

        Example(List<String> words, String order) {
            this(words, order, 0L);
            this.hits = 0;
        }

        /** A new answer, chosen once, at {@code touched}. */
        Example(List<String> words, String order, long touched) {
            this.words = words;
            this.order = order;
            this.touched = touched;
            this.hits = 1;
        }
    }

    private final File file;
    private final List<Example> examples = new ArrayList<Example>();
    /** Conversation it has learned: a question's words and the answer a model gave, newest last. */
    private final List<Example> talk = new ArrayList<Example>();
    /** How many answers are kept, and how long one may be. */
    public static final int MOST_TALK = 3000;
    static final int ANSWER_CHARS = 1500;
    /** How close a question must be to answer it from what was learned (only when no model can). */
    static final double CLOSE_TALK = 0.8;

    public BrainBox(File file) {
        this.file = file;
        load();
        loadTalk();
        loadTemplates();
    }

    /** How many answers it has learned. */
    public synchronized int talkSize() {
        return talk.size();
    }

    /**
     * Learns an answer a model gave (any model, API or on the device), so DVINE grows with every conversation. Very
     * short or very long answers, and answers about the moment (time, today, news, prices), are not kept: they are
     * facts about that day, not wisdom. When the same words are corrected, the newer answer wins; when they recur, the
     * older answer is strengthened (it stays, and is trusted more the next time).
     */
    public synchronized void learnTalk(String question, String answer) {
        List<String> words = words(question);
        String a = answer == null ? "" : answer.trim();
        if (words.isEmpty() || a.length() < 2 || a.length() > ANSWER_CHARS
                || (question + " " + a).toLowerCase(Locale.US).matches("(?s).*\\b(today|tonight|now|current|latest|news|price|weather|yesterday|tomorrow)\\b.*")) {
            return;
        }
        long now = System.currentTimeMillis();
        String kept = a.replace('\n', '\u2028');
        for (int i = talk.size() - 1; i >= 0; i--) {
            Example e = talk.get(i);
            if (e.words.equals(words)) {
                if (e.order.equals(kept)) {
                    e.hits++; // the pilot chose the same answer again: it keeps learning this
                    e.touched = now;
                    saveTalk();
                    return;
                }
                talk.remove(i); // a corrected answer replaces the wrong one
            } else if (cosineOf(words, e.words, talk) >= CLOSE_TALK && e.order.equals(kept)) {
                e.hits++; // the same answer, in a slightly different wording
                e.touched = now;
                saveTalk();
                return;
            }
        }
        talk.add(new Example(words, kept, now));
        while (talk.size() > MOST_TALK) {
            talk.remove(0); // the oldest, least used learning drops out first
        }
        saveTalk();
    }

    /**
     * An answer learned for a question like this one, or null. For when no model can answer. An older answer is used
     * only when a clearly better one does not turn up, and one that was corrected never comes back.
     */
    public synchronized String recallTalk(String question) {
        Example best = nearest(talk, words(question));
        if (best == null || score < CLOSE_TALK) {
            return null;
        }
        // how the answer looks when its learning is counted
        int asGood = 0;
        double adjBest = score * trust(best);
        for (int i = talk.size() - 1; i >= 0; i--) {
            Example e = talk.get(i);
            if (e == best || e.words.equals(best.words)) {
                continue;
            }
            double s = cosineOf(words(question), e.words, talk);
            if (s >= CLOSE_TALK && s > score + 1e-9) {
                double adj = s * trust(e);
                if (adj > adjBest + 1e-9) {
                    return null; // a clearly better answer exists: let the models judge
                }
                asGood++;
            }
        }
        // several answers learned for the same shape of question: without a correction, no favourite is taken
        if (asGood >= 1) {
            return null;
        }
        return best.order.replace('\u2028', '\n');
    }

    /** How much an answer is trusted: one learned once, or one chosen many times and so tried and true. */
    private static double trust(Example e) {
        return 1 + 0.1 * Math.min(20, e.hits);
    }

    /**
     * The user said it was wrong: what was learned for these words is forgotten, so the same mistake is not made
     * again (the model decides the next time, and teaches it afresh).
     */
    public synchronized boolean forget(String text) {
        List<String> words = words(text);
        boolean gone = false;
        for (int i = examples.size() - 1; i >= 0; i--) {
            if (examples.get(i).words.equals(words) || cosineOf(words, examples.get(i).words, examples) >= CLOSE) {
                examples.remove(i);
                gone = true;
            }
        }
        if (gone) {
            save();
        }
        if (templates.removeIf(t -> t.fill(text) != null)) {
            saveTemplates(); // the shape that gave the wrong order goes too
            gone = true;
        }
        return gone;
    }

    /** The pilot said a learned answer was wrong: answers learned for questions like this one are forgotten. */
    public synchronized boolean forgetTalk(String question) {
        List<String> words = words(question);
        boolean gone = false;
        for (int i = talk.size() - 1; i >= 0; i--) {
            if (talk.get(i).words.equals(words) || cosineOf(words, talk.get(i).words, talk) >= CLOSE_TALK) {
                talk.remove(i);
                gone = true;
            }
        }
        if (gone) {
            saveTalk();
        }
        return gone;
    }

    /** True when the user's words say the last thing done was not what they meant. */
    public static boolean correction(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.US).replaceAll("[.!?]+$", "");
        String rest = t.replaceFirst("^(no|nope)\\b[,!.]*\\s*", "");
        if (rest.isEmpty()) {
            return true; // just "no"
        }
        return rest.matches("^(wrong|that'?s wrong|not that|that'?s not (it|right|what i (meant|asked|wanted))"
                + "|i (didn'?t|did not) (mean|ask for) that|you got it wrong)$");
    }

    /** The score of the last {@link #nearest} call. */
    private double score;

    private Example nearest(List<Example> from, List<String> words) {
        score = 0;
        if (words.isEmpty() || from.isEmpty()) {
            return null;
        }
        Map<String, Integer> df = df(from);
        Example best = null;
        for (int i = from.size() - 1; i >= 0; i--) {
            double s = cosine(words, from.get(i).words, df, from.size());
            if (s > score + 1e-9) {
                score = s;
                best = from.get(i);
            }
        }
        return best;
    }

    private static Map<String, Integer> df(List<Example> from) {
        Map<String, Integer> df = new HashMap<String, Integer>();
        for (Example e : from) {
            for (String w : new HashSet<String>(e.words)) {
                Integer n = df.get(w);
                df.put(w, n == null ? 1 : n + 1);
            }
        }
        return df;
    }

    private static double cosineOf(List<String> a, List<String> b, List<Example> from) {
        return cosine(a, b, df(from), from.size());
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
        Template t = Template.of(text, order);
        if (t != null) {
            templates.removeIf(o -> o.before.equals(t.before) && o.after.equals(t.after));
            templates.add(t);
            while (templates.size() > MOST) {
                templates.remove(0);
            }
            saveTemplates();
        }
    }

    /** Kinds whose argument may be new words in a learned shape (never shell, posting or scheduling). */
    private static final Set<String> TEMPLATED = new HashSet<String>(java.util.Arrays.asList(
            "SR", "SEARCH", "PH", "PHOTO", "CA", "CALC", "BD", "BUILD", "NM", "NAME", "SW", "SWITCH", "MF", "MAKEFILE", "SI", "SOCIALINTEL"));

    /**
     * A learned shape: "what's the weather in {x}" became "§SR weather in {x}", so "what's the weather in Abuja" becomes
     * "§SR weather in Abuja" without asking a model. The words before and after the argument must match exactly.
     */
    static final class Template {
        final String before, after, orderBefore, orderAfter;

        Template(String before, String after, String orderBefore, String orderAfter) {
            this.before = before;
            this.after = after;
            this.orderBefore = orderBefore;
            this.orderAfter = orderAfter;
        }

        static String plain(String t) {
            return (t == null ? "" : t).toLowerCase(Locale.US).replaceAll("[?!.,;]+$", "").replaceAll("\\s+", " ").trim();
        }

        /** The shape of a message and the order it became, or null when the order doesn't carry the user's words. */
        static Template of(String text, String order) {
            String k = kind(order);
            String[] p = order.trim().split("\\s+", order.trim().startsWith("DO ") ? 3 : 2);
            if (!TEMPLATED.contains(k) || p.length < 2) {
                return null;
            }
            String arg = plain(p[p.length - 1]);
            String msg = plain(text);
            int at = arg.length() < 3 ? -1 : msg.indexOf(arg);
            if (at < 0 || msg.indexOf(arg, at + 1) >= 0) {
                return null; // the argument isn't the user's words, or appears twice
            }
            String before = msg.substring(0, at), after = msg.substring(at + arg.length());
            if (before.trim().length() < 3) {
                return null; // too little shape to match on
            }
            int oa = order.toLowerCase(Locale.US).lastIndexOf(arg);
            if (oa < 0) {
                return null;
            }
            return new Template(before, after, order.substring(0, oa), order.substring(oa + arg.length()));
        }

        /** The order for a message of this shape, or null. */
        String fill(String text) {
            String msg = plain(text);
            if (!msg.startsWith(before) || !msg.endsWith(after) || msg.length() <= before.length() + after.length()) {
                return null;
            }
            // the user's words as they wrote them (names keep their capitals)
            String as = (text == null ? "" : text).replaceAll("[?!.,;]+$", "").replaceAll("\\s+", " ").trim();
            String from = as.length() == msg.length() ? as : msg;
            String middle = from.substring(before.length(), from.length() - after.length()).trim();
            return middle.isEmpty() || middle.length() > 160 ? null : orderBefore + middle + orderAfter;
        }

        String write() {
            return (before + "\t" + after + "\t" + orderBefore + "\t" + orderAfter).replace('\n', ' ');
        }
    }

    /**
     * Learns an order a model invented for this message: the user's words are carried into the new order wherever it
     * put them, so a later message of the same shape is answered at once with its own words in it. The order is kept
     * only when it says something about the words it carries (never a bare "DO SEARCH"), never repeats them, and is
     * short enough to run.
     */
    public synchronized String learnShape(String text, String order) {
        String raw = order == null ? "" : order.trim();
        String k = kind(raw);
        if (text == null || text.trim().isEmpty() || raw.isEmpty() || NOT_ACTIONS.contains(k)) {
            return null;
        }
        String msg = Template.plain(text);
        if (msg.isEmpty()) {
            return null;
        }
        // where the user's words sit in the message (lower case, in the order they were written)
        String[] parts = msg.split("\\s+", -1);
        StringBuilder phrase = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (phrase.length() > 0) {
                phrase.append(' ');
            }
            phrase.append(part);
        }
        String arg = phrase.toString();
        if (arg.length() < 3) {
            return null;
        }
        int at = msg.indexOf(arg);
        if (at < 0 || msg.indexOf(arg, at + 1) >= 0) {
            return null;
        }
        String before = msg.substring(0, at), after = msg.substring(at + arg.length());
        if (before.trim().length() < 3) {
            return null; // too little shape to match on
        }
        // the same words where the order put them, in the order's own case
        String lower = raw.toLowerCase(Locale.US);
        int oa = lower.lastIndexOf(arg);
        String orderBefore, orderAfter;
        if (oa >= 0) {
            orderBefore = raw.substring(0, oa);
            orderAfter = raw.substring(oa + arg.length());
        } else {
            // the words are written differently in the order (capitalised, or with a space where the message has one):
            // the argument is the order's own last words, so it goes at the end
            int last = raw.lastIndexOf(' ');
            oa = last < 0 ? raw.length() : last + 1;
            orderBefore = raw.substring(0, oa);
            orderAfter = "";
        }
        String kept = (orderBefore + arg + orderAfter).trim();
        String head = orderBefore.trim();
        if (head.isEmpty() || kept.length() > 400 || repeats(kept, arg)) {
            return null;
        }
        // the shape is only worth keeping when the order says more than the words it carries
        String rest = kept.toLowerCase(Locale.US).replace(arg, " ");
        if (rest.replaceAll("[^a-z0-9]+", "").length() < 2) {
            return null;
        }
        // drop any shape the message already fills with an older order
        String have = fromShape(text);
        if (have != null) {
            return have;
        }
        templates.add(new Template(before, after, orderBefore, orderAfter));
        if (templates.size() > MOST / 4) {
            templates.remove(0);
        }
        saveTemplates();
        return kept;
    }

    /** Whether the order says the carried words more than once. */
    private static boolean repeats(String order, String arg) {
        String lower = order.toLowerCase(Locale.US);
        int at = lower.indexOf(arg);
        return at >= 0 && lower.indexOf(arg, at + arg.length()) >= 0;
    }

    private final List<Template> templates = new ArrayList<Template>();

    /** How many shapes it has learned. */
    public synchronized int templateCount() {
        return templates.size();
    }

    private File templateFile() {
        return file == null ? null : new File(file.getPath().replaceAll("\\.tsv$", "") + "-shapes.tsv");
    }

    private void loadTemplates() {
        File f = templateFile();
        if (f == null || !f.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length == 4) {
                    templates.add(new Template(p[0], p[1], p[2], p[3]));
                }
            }
        } catch (IOException e) {
            templates.clear();
        }
    }

    private void saveTemplates() {
        File f = templateFile();
        if (f == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Template t : templates) {
            b.append(t.write()).append('\n');
        }
        try {
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
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
            return fromShape(text);
        }
        boolean same = best.words.equals(words);
        if (same) {
            return best.order;
        }
        if (bestScore >= CLOSE && NO_ARGUMENT.contains(kind(best.order))) {
            return best.order;
        }
        return fromShape(text);
    }

    /** An order from a learned shape with the message's own words in it, newest shape first; or null. */
    private String fromShape(String text) {
        for (int i = templates.size() - 1; i >= 0; i--) {
            String o = templates.get(i).fill(text);
            if (o != null) {
                return o;
            }
        }
        return null;
    }

    /** Forgets everything learned. */
    public synchronized void clear() {
        examples.clear();
        talk.clear();
        templates.clear();
        save();
        saveTalk();
        saveTemplates();
    }

    private File talkFile() {
        return file == null ? null : new File(file.getPath().replaceAll("\\.tsv$", "") + "-talk.tsv");
    }

    private void loadTalk() {
        File f = talkFile();
        if (f == null || !f.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    // words TAB answer TAB hits TAB touched; files without the last two still load
                    String[] parts = line.split("\t", -1);
                    Example e = new Example(new ArrayList<String>(java.util.Arrays.asList(parts[0].split(" "))), parts[1]);
                    if (parts.length > 2) {
                        try {
                            e.hits = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException ignored) {
                            e.hits = 0;
                        }
                    }
                    if (parts.length > 3) {
                        try {
                            e.touched = Long.parseLong(parts[3]);
                        } catch (NumberFormatException ignored) {
                            e.touched = 0;
                        }
                    }
                    talk.add(e);
                }
            }
        } catch (IOException e) {
            talk.clear();
        }
    }

    private void saveTalk() {
        File f = talkFile();
        if (f == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Example e : talk) {
            b.append(String.join(" ", e.words)).append('\t')
                    .append(e.order.replace('\n', '\u2028').replace('\t', ' '))
                    .append('\t').append(e.hits).append('\t').append(e.touched).append('\n');
        }
        try {
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
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
