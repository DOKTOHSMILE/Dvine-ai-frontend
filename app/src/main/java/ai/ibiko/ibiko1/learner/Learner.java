package ai.ibiko.ibiko1.learner;

import ai.ibiko.ibiko1.Facts;
import ai.ibiko.ibiko1.Json;
import ai.ibiko.ibiko1.Lexicon;
import ai.ibiko.ibiko1.formulas.Confidence;
import ai.ibiko.ibiko1.formulas.Grammar;
import ai.ibiko.ibiko1.formulas.PartsOfSpeech;
import ai.ibiko.ibiko1.formulas.Score;
import ai.ibiko.ibiko1.formulas.Shapes;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ibiko 1's independent learner. It learns from everything that passes through the app, and only from that:
 *
 * <pre>
 * L1  the pilot's messages      grammar (parts of speech), sentence shapes, and their check or correction of an answer
 * L2  every model's answers     grammar and sentence shapes (language only: a model's claim is not a fact)
 * L3  research it read          facts at strength 2, grammar, sentence shapes
 * L4  social posts and pages    grammar only: people's words, never facts
 * L5  questions it couldn't answer  kept until a fact of strength 2 answers them; asked 3 times with none: unknown
 * L6  the review pass           facts fade and merge, shapes are promoted or pruned, one journal paragraph
 * </pre>
 *
 * The rules themselves live in {@code ibiko1/formulas}: {@link Confidence}, {@link Shapes}, {@link PartsOfSpeech},
 * {@link Score}. Files: learner-answers.jsonl (the last 2000 answers and how they were judged), learner-open.json,
 * learner-rules.json, learner-words.tsv, learner-state.json, learner-journal.txt. Pure Java.
 */
public final class Learner {
    public static final int RING = 2000, WORDS = 20000, ASKED_UNKNOWN = 3, WINDOW = 2, SENTENCES = 40;

    /** Where a text came from: what it may teach. */
    public enum Source { PILOT, MODEL, RESEARCH, SOCIAL }

    /** What the pilot's message said about the last answer. */
    public enum Verdict { NONE, ACCEPTED, CORRECTED }

    private final File dir;
    private final Facts facts;
    private final Lexicon lexicon;
    private final List<Shapes.Rule> rules = new ArrayList<Shapes.Rule>();
    private final Map<String, int[]> words = new HashMap<String, int[]>();
    private final Map<String, Integer> open = new LinkedHashMap<String, Integer>();
    private int correct, answered, unknown;
    private long sure, seen;
    /** The answer waiting for the pilot's word on it, and how many of their messages have passed since. */
    private String pendingQuestion, pendingAnswer;
    private int pendingAge;

    public Learner(File dir, Facts facts, Lexicon lexicon) {
        this.dir = dir;
        this.facts = facts;
        this.lexicon = lexicon;
        load();
    }

    // ---- L1 / T2: answers and the pilot's word on them ----------------------------------------------------------

    /** Ibiko 1 gave an answer: it waits up to two of the pilot's messages for a check or a correction. */
    public synchronized void answered(String question, String answer, long now) {
        pendingQuestion = question;
        pendingAnswer = answer;
        pendingAge = 0;
        ring("given", question, answer, now);
        if (question != null && facts.knows(question)) {
            open.remove(key(question));
        }
        save();
    }

    /** The check or cancel under an answer. */
    public synchronized void judged(boolean right, long now) {
        if (pendingAnswer == null) {
            return;
        }
        verdict(right, now);
        save();
    }

    /**
     * One of the pilot's messages: it may judge the last answer (a correction within two messages that mentions the
     * answer, or an acceptance), and it is read for grammar and sentence shapes.
     */
    public synchronized Verdict heard(String message, long now) {
        Verdict v = Verdict.NONE;
        if (pendingAnswer != null && message != null) {
            pendingAge++;
            if (pendingAge <= WINDOW) {
                String m = message.toLowerCase(Locale.US);
                if (corrects(m) && mentions(m, pendingAnswer)) {
                    verdict(false, now);
                    v = Verdict.CORRECTED;
                } else if (pendingAge == 1 && accepts(m)) {
                    verdict(true, now);
                    v = Verdict.ACCEPTED;
                }
            }
            if (pendingAge >= WINDOW) {
                pendingAnswer = null;
                pendingQuestion = null;
            }
        }
        read(message, Source.PILOT, now);
        return v;
    }

    private void verdict(boolean right, long now) {
        answered++;
        if (right) {
            correct++;
            facts.accepted();
        } else {
            facts.corrected();
            if (pendingQuestion != null) {
                ask(pendingQuestion, now);
            }
        }
        ring(right ? "hit" : "miss", pendingQuestion, pendingAnswer, now);
        pendingAnswer = null;
        pendingQuestion = null;
    }

    static boolean corrects(String m) {
        return m.matches("(?s)\\s*(no|nope|wrong|incorrect|false|not quite|that's not|that is not|actually)\\b.*")
                || m.matches("(?s).*\\b(that's wrong|is wrong|was wrong|not right|isn't right|is incorrect|you're wrong|it's not|it is not)\\b.*");
    }

    static boolean accepts(String m) {
        return m.matches("(?s)\\s*(yes|yeah|yep|right|correct|exactly|true|that's right|that is right|that's it|perfect|great|thanks|thank you)\\b[.! ]*.*")
                && !corrects(m);
    }

    /** The message names something from the answer: one of its longer words, or a number in it. */
    static boolean mentions(String message, String answer) {
        String m = " " + message.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N} ]", " ") + " ";
        for (String w : answer.toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if ((w.length() >= 4 && !PartsOfSpeech.Tag.PRONOUN.equals(tagOf(w)) || w.matches("\\d+")) && m.contains(" " + w + " ")) {
                return true;
            }
        }
        return false;
    }

    private static PartsOfSpeech.Tag tagOf(String w) {
        List<PartsOfSpeech.Word> t = PartsOfSpeech.tag(w, null);
        return t.isEmpty() ? null : t.get(0).tag;
    }

    // ---- L5: open questions ----------------------------------------------------------------------------------------

    /** A question Ibiko 1 couldn't answer: kept; the third time with still no fact, it is logged as unknown. */
    public synchronized void unanswered(String question, long now) {
        ask(question, now);
        save();
    }

    private void ask(String question, long now) {
        String k = key(question);
        if (k.isEmpty()) {
            return;
        }
        int n = open.containsKey(k) ? open.get(k) + 1 : 1;
        if (n >= ASKED_UNKNOWN) {
            open.remove(k);
            unknown++;
            ring("unknown", question, "", now);
        } else {
            open.remove(k);
            open.put(k, n);
            while (open.size() > 500) {
                open.remove(open.keySet().iterator().next());
            }
        }
    }

    /** The questions still waiting for an answer, oldest first. */
    public synchronized List<String> openQuestions() {
        return new ArrayList<String>(open.keySet());
    }

    // ---- L1-L4: reading ------------------------------------------------------------------------------------------

    /** Reads a text from {@code source}: grammar always, sentence shapes for all but social posts. */
    public synchronized void read(String text, Source source, long now) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String masked = Shapes.mask(text);
        int n = 0;
        for (String sentence : masked.split("(?<=[.!?])\\s+|\\n+")) {
            if (++n > SENTENCES) {
                break;
            }
            String s = sentence.trim();
            if (s.length() < 3) {
                continue;
            }
            grammar(s);
            if (source != Source.SOCIAL) {
                shapes(s, source, now);
            }
        }
        if (source == Source.RESEARCH) {
            save();
        }
    }

    /** The parts of speech of a sentence, learned: each word counted as what it surely was. */
    private void grammar(String sentence) {
        for (PartsOfSpeech.Word w : PartsOfSpeech.tag(sentence, known())) {
            if (w.tag == PartsOfSpeech.Tag.PUNCTUATION || w.text.startsWith("{")) {
                continue;
            }
            seen++;
            if (!w.sure()) {
                continue;
            }
            sure++;
            if (w.tag == PartsOfSpeech.Tag.NUMERAL || w.tag == PartsOfSpeech.Tag.PROPER_NOUN || w.rule == 1) {
                continue; // names, numbers and closed words need no learning
            }
            String k = w.text.toLowerCase(Locale.US).replaceAll("[^\\p{L}'-]", "");
            if (k.length() < 2) {
                continue;
            }
            int[] c = words.get(k);
            if (c == null) {
                if (words.size() >= WORDS) {
                    continue;
                }
                c = new int[PartsOfSpeech.Tag.values().length];
                words.put(k, c);
            }
            c[w.tag.ordinal()]++;
        }
    }

    /** What Ibiko has learned words to be: its own counts (3 sightings, 60% one way), else its dictionary. */
    public PartsOfSpeech.Known known() {
        return w -> {
            synchronized (Learner.this) {
                int[] c = words.get(w);
                if (c != null) {
                    int best = 0, total = 0;
                    for (int i = 0; i < c.length; i++) {
                        total += c[i];
                        if (c[i] > c[best]) {
                            best = i;
                        }
                    }
                    if (total >= 3 && c[best] >= 0.6 * total) {
                        return PartsOfSpeech.Tag.values()[best];
                    }
                }
            }
            Lexicon.Word d = lexicon == null ? null : lexicon.get(w);
            return d == null ? null : fromDictionary(d.part);
        };
    }

    static PartsOfSpeech.Tag fromDictionary(String part) {
        String p = part == null ? "" : part.toLowerCase(Locale.US);
        if (p.contains("proper")) {
            return PartsOfSpeech.Tag.PROPER_NOUN;
        }
        if (p.startsWith("noun")) {
            return PartsOfSpeech.Tag.NOUN;
        }
        if (p.startsWith("verb")) {
            return PartsOfSpeech.Tag.VERB;
        }
        if (p.startsWith("adjective")) {
            return PartsOfSpeech.Tag.ADJECTIVE;
        }
        if (p.startsWith("adverb")) {
            return PartsOfSpeech.Tag.ADVERB;
        }
        if (p.startsWith("pronoun")) {
            return PartsOfSpeech.Tag.PRONOUN;
        }
        if (p.startsWith("preposition")) {
            return PartsOfSpeech.Tag.PREPOSITION;
        }
        if (p.startsWith("conjunction")) {
            return PartsOfSpeech.Tag.CONJUNCTION;
        }
        if (p.startsWith("interjection")) {
            return PartsOfSpeech.Tag.INTERJECTION;
        }
        return null;
    }

    /** A word's part of speech as Ibiko reads it alone, or null. */
    public synchronized PartsOfSpeech.Tag partOf(String word) {
        return known().of(word.toLowerCase(Locale.US));
    }

    /** How many words it has learned a part of speech for. */
    public synchronized int wordsLearned() {
        int n = 0;
        PartsOfSpeech.Known k = known();
        for (String w : words.keySet()) {
            if (k.of(w) != null) {
                n++;
            }
        }
        return n;
    }

    /** T3: every shape tried on the sentence; new shapes made around facts it knows well. */
    private void shapes(String sentence, Source source, long now) {
        if (Shapes.question(sentence) || Shapes.negative(sentence)) {
            return;
        }
        String lower = sentence.toLowerCase(Locale.US);
        List<Facts.Row> rows = facts.rows();
        for (Shapes.Rule r : rules) {
            String v = r.read(sentence);
            if (v == null) {
                continue;
            }
            r.seen++;
            r.used = now;
            boolean judged = false;
            for (Facts.Row f : rows) {
                if (!f.fact.relation.equals(r.relation) || !lower.contains(f.fact.subject.toLowerCase(Locale.US))
                        || !Confidence.answers(f.n)) {
                    continue;
                }
                judged = true;
                if (Facts.same(f.fact.object, v)) {
                    r.hit++;
                } else if (Facts.opposes(f.fact, new Grammar.Triple(f.fact.subject, r.relation, v, f.fact.surface))) {
                    r.rejected++;
                }
                break;
            }
            if (!judged && r.promoted() && source != Source.MODEL) {
                // a proven shape reads a new fact: seen once, so it answers only once it is seen again
                String subject = subjectOf(sentence, v);
                if (subject != null) {
                    facts.see(new Grammar.Triple(subject, r.relation, v, r.relation.replaceAll(" (in|on|by|of)$", "")), 1, now);
                }
            }
        }
        for (Facts.Row f : rows) {
            if (!Confidence.answers(f.n) || !lower.contains(f.fact.subject.toLowerCase(Locale.US))
                    || !lower.contains(f.fact.object.toLowerCase(Locale.US))) {
                continue;
            }
            Shapes.Rule s = Shapes.shape(sentence, f.fact.object, f.fact.relation, now);
            if (s != null && !has(s)) {
                rules.add(s);
            }
        }
        List<Shapes.Rule> kept = Shapes.capped(rules);
        rules.clear();
        rules.addAll(kept);
    }

    private boolean has(Shapes.Rule s) {
        for (Shapes.Rule r : rules) {
            if (r.relation.equals(s.relation) && r.left.equals(s.left) && r.right.equals(s.right)) {
                return true;
            }
        }
        return false;
    }

    /** The subject of a sentence: the words before its verb, without the article; null when there is none. */
    String subjectOf(String sentence, String value) {
        String[] c = PartsOfSpeech.clause(PartsOfSpeech.tag(sentence, known()));
        if (c == null) {
            return null;
        }
        String s = c[0].replaceFirst("(?i)^(the|a|an)\\s+", "").trim();
        return s.length() < 2 || s.length() > 60 || s.equalsIgnoreCase(value) || s.contains("{") ? null : s;
    }

    /** The shapes it has, best first. */
    public synchronized List<Shapes.Rule> rules() {
        return new ArrayList<Shapes.Rule>(rules);
    }

    /** For tests: a shape added as it is. */
    synchronized void addRule(Shapes.Rule r) {
        rules.add(r);
    }

    public synchronized int promoted() {
        int n = 0;
        for (Shapes.Rule r : rules) {
            if (r.promoted()) {
                n++;
            }
        }
        return n;
    }

    // ---- L6 / T4: the review pass ---------------------------------------------------------------------------------

    /**
     * The review pass, kept to about {@code budgetMs} of work: facts fade and merge, shapes pruned, open questions a
     * strong fact now answers cleared; one paragraph written to the journal and returned.
     */
    public synchronized String review(long now, long budgetMs) {
        long start = System.nanoTime();
        int changed = facts.review(now);
        int pruned = 0;
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (rules.get(i).prune(now)) {
                rules.remove(i);
                pruned++;
            }
        }
        int cleared = 0;
        for (String q : new ArrayList<String>(open.keySet())) {
            if ((System.nanoTime() - start) / 1_000_000 > budgetMs) {
                break;
            }
            if (facts.knows(q)) {
                open.remove(q);
                cleared++;
            }
        }
        String paragraph = String.format(Locale.US,
                "Review: %d %s faded or merged, %d %s pruned, %d open %s now answered. %d sure facts, %d reading rules, %d words"
                        + " learned. %s.", changed, changed == 1 ? "fact" : "facts", pruned, pruned == 1 ? "shape" : "shapes", cleared,
                cleared == 1 ? "question" : "questions", facts.strong(), promoted(), wordsLearned(), scoreLine());
        append("learner-journal.txt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date(now))
                + "  " + paragraph + "\n", 200);
        save();
        return paragraph;
    }

    // ---- T5: the score ------------------------------------------------------------------------------------------------

    public synchronized int score() {
        return Score.of(correct, answered, facts.strong(), promoted(), sure, seen);
    }

    public synchronized String scoreLine() {
        return Score.said(correct, answered, facts.strong(), promoted(), sure, seen);
    }

    public synchronized int correct() {
        return correct;
    }

    public synchronized int judgedAnswers() {
        return answered;
    }

    public synchronized int unknown() {
        return unknown;
    }

    /** The last journal paragraphs, newest last. */
    public synchronized List<String> journal() {
        return lines("learner-journal.txt");
    }

    /** The answers it gave and how they were judged, newest last. */
    public synchronized List<String> answers() {
        return lines("learner-answers.jsonl");
    }

    // ---- T6: clear ----------------------------------------------------------------------------------------------------

    /** Forgets all it learned: shapes, words, answers, open questions, score (the facts are cleared by their owner). */
    public synchronized void clear() {
        rules.clear();
        words.clear();
        open.clear();
        correct = answered = unknown = 0;
        sure = seen = 0;
        pendingAnswer = pendingQuestion = null;
        for (String f : new String[] {"learner-answers.jsonl", "learner-open.json", "learner-rules.json", "learner-words.tsv",
                "learner-state.json", "learner-journal.txt"}) {
            new File(dir, f).delete();
        }
    }

    // ---- files ----------------------------------------------------------------------------------------------------------

    private static String key(String question) {
        return question == null ? "" : question.trim().replaceAll("\\s+", " ");
    }

    private void ring(String how, String question, String answer, long now) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("at", (double) now);
        m.put("how", how);
        m.put("q", Shapes.mask(question == null ? "" : question));
        m.put("a", Shapes.mask(answer == null ? "" : answer.length() > 400 ? answer.substring(0, 400) : answer));
        append("learner-answers.jsonl", Json.write(m) + "\n", RING);
    }

    private void append(String name, String line, int most) {
        if (dir == null) {
            return;
        }
        File f = new File(dir, name);
        try {
            dir.mkdirs();
            ai.ibiko.learn.CodeLog.append(f, line); // one line added in IBK codes, never the whole log again
            if (f.length() > 256 * 1024) {
                List<String> all = ai.ibiko.learn.CodeLog.lines(f);
                if (all.size() > most + most / 10) {
                    ai.ibiko.learn.CodeLog.write(f, new ArrayList<String>(all.subList(all.size() - most, all.size())));
                }
            }
        } catch (IOException ignored) {
            // the next line tries again
        }
    }

    private List<String> lines(String name) {
        File f = dir == null ? null : new File(dir, name);
        try {
            return f != null && f.isFile() ? ai.ibiko.learn.CodeLog.lines(f) : new ArrayList<String>();
        } catch (IOException e) {
            return new ArrayList<String>();
        }
    }

    private static void write(File f, String text) throws IOException {
        ai.ibiko.learn.CodeFile.write(f, text); // IBK codes, beside the file first and then over it
    }

    /** Writes what it learned (called after each message, cheap: a few small files). */
    public synchronized void save() {
        if (dir == null) {
            return;
        }
        try {
            dir.mkdirs();
            Map<String, Object> st = new LinkedHashMap<String, Object>();
            st.put("correct", (double) correct);
            st.put("answered", (double) answered);
            st.put("unknown", (double) unknown);
            st.put("sure", (double) sure);
            st.put("seen", (double) seen);
            write(new File(dir, "learner-state.json"), Json.write(st));
            Map<String, Object> q = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Integer> e : open.entrySet()) {
                q.put(e.getKey(), (double) e.getValue());
            }
            write(new File(dir, "learner-open.json"), Json.write(q));
            List<Object> rs = new ArrayList<Object>();
            for (Shapes.Rule r : rules) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("relation", r.relation);
                m.put("left", r.left);
                m.put("right", r.right);
                m.put("seen", (double) r.seen);
                m.put("hit", (double) r.hit);
                m.put("rejected", (double) r.rejected);
                m.put("born", (double) r.born);
                m.put("used", (double) r.used);
                rs.add(m);
            }
            write(new File(dir, "learner-rules.json"), Json.write(rs));
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, int[]> e : words.entrySet()) {
                b.append(e.getKey());
                for (int c : e.getValue()) {
                    b.append('\t').append(c);
                }
                b.append('\n');
            }
            write(new File(dir, "learner-words.tsv"), b.toString());
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }

    private void load() {
        if (dir == null) {
            return;
        }
        try {
            Map<String, Object> st = Json.obj(Json.parse(read("learner-state.json", "{}")));
            correct = (int) Json.num(st.get("correct"), 0);
            answered = (int) Json.num(st.get("answered"), 0);
            unknown = (int) Json.num(st.get("unknown"), 0);
            sure = (long) Json.num(st.get("sure"), 0);
            seen = (long) Json.num(st.get("seen"), 0);
            for (Map.Entry<String, Object> e : Json.obj(Json.parse(read("learner-open.json", "{}"))).entrySet()) {
                open.put(e.getKey(), (int) Json.num(e.getValue(), 1));
            }
            for (Object o : Json.list(Json.parse(read("learner-rules.json", "[]")))) {
                Map<String, Object> m = Json.obj(o);
                Shapes.Rule r = new Shapes.Rule(Json.str(m.get("relation"), ""), Json.str(m.get("left"), ""), Json.str(m.get("right"), ""),
                        (long) Json.num(m.get("born"), 0));
                r.seen = (int) Json.num(m.get("seen"), 0);
                r.hit = (int) Json.num(m.get("hit"), 0);
                r.rejected = (int) Json.num(m.get("rejected"), 0);
                r.used = (long) Json.num(m.get("used"), r.born);
                if (!r.relation.isEmpty()) {
                    rules.add(r);
                }
            }
            int tags = PartsOfSpeech.Tag.values().length;
            for (String line : lines("learner-words.tsv")) {
                String[] p = line.split("\t");
                if (p.length == tags + 1) {
                    int[] c = new int[tags];
                    for (int i = 0; i < tags; i++) {
                        c[i] = Integer.parseInt(p[i + 1]);
                    }
                    words.put(p[0], c);
                }
            }
        } catch (RuntimeException e) {
            // a damaged file starts that part afresh
        }
    }

    private String read(String name, String fallback) {
        File f = new File(dir, name);
        try {
            return f.isFile() ? ai.ibiko.learn.CodeFile.read(f) : fallback;
        } catch (IOException e) {
            return fallback;
        }
    }
}
