package ai.dvine.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.dvine.suit.Grammar;

/**
 * What DVINE 1 has read, kept as facts (subject, relation, object) by the {@link Grammar} formulas, and answered from
 * by the same formulas: a question is parsed into what it wants and what it is about, matched to a fact, and the
 * answer is formulated as a sentence of its own (the sentence formula below). Kept in a file on the device.
 *
 * <pre>
 * SAY  person / thing asked for an action:  "{who} {verb as written} {object}."     Bell invented the telephone.
 *      time or place of an action:          "{subject} was {verb} in {when}."       Microsoft was founded in 1975.
 *      birth:                               "{subject} was born on {date} / in {place}."
 *      what something is:                   "{subject} {is|was} {description}."     A quasar is an active galactic nucleus.
 *      a role:                              "{subject} is the {role} of {object}."  Abuja is the capital of Nigeria.
 * </pre>
 *
 * Every fact also carries a strength (0–5) and the time it was last confirmed (T7): a fact said again rises, a
 * contradiction drops it two, and once it reaches zero it is dropped for good, so "Nigeria's capital is Lagos" cannot
 * survive "Nigeria's capital is Abuja" forever.
 */
public final class Facts {
    public static final int MOST = 20000;
    /** Below this a fact is not worth answering from: one sighting is a guess, three is something. */
    public static final int STRONG = 2;
    public static final int MAX_STRENGTH = 5;
    /** A contradiction lowers the older fact by this much. */
    public static final int CONTRADICTION_COST = 2;
    /** The most facts kept with the same subject+relation competing for one answer. */
    private static final int AMBIGUOUS_LIMIT = 3;
    /** Facts whose strength is 0 are dropped on the next save; a fact is dropped once it has not been touched in a year. */
    private static final long STALE_SECONDS = 365L * 24 * 3600;

    private final File file;
    private final List<Row> all = new ArrayList<Row>();

    /** A fact with its confidence: strength 0–5 and when it was last confirmed. */
    public static final class Row {
        public final Grammar.Triple triple;
        public int strength;
        public long confirmed;

        Row(Grammar.Triple triple, int strength, long confirmed) {
            this.triple = triple;
            this.strength = strength;
            this.confirmed = confirmed;
        }
    }

    /** What to do with a message that states a fact already known. */
    public enum Verdict {
        NEW, REPEATED, CONTRADICTED
    }

    /** The result of reading one message: how many facts, whether it repeated one, and whether it denied one. */
    public static final class Learn {
        public int added;
        public Verdict verdict = Verdict.NEW;
        public Grammar.Triple against;

        public boolean stated() {
            return added > 0 || verdict != Verdict.NEW;
        }
    }

    public Facts(File file) {
        this.file = file;
        load();
    }

    public synchronized int size() {
        return all.size();
    }

    /** A snapshot of the facts, for the tests and for anything that wants to read them. */
    public synchronized List<Row> rows() {
        return new ArrayList<Row>(all);
    }

    /** The facts with their strengths, in file order. */
    public synchronized List<Row> strongest() {
        List<Row> out = new ArrayList<Row>(all);
        out.sort((a, b) -> b.strength - a.strength);
        return out;
    }

    /**
     * Reads a text for facts and keeps the new ones; how many were new. A statement of something already known raises
     * its strength (a repeated name, "as usual"), and a denial lowers the older fact — {@link Learn#stated()} says
     * whether the message said anything at all, so the caller can tell "I don't know" from "I already told you".
     */
    public synchronized Learn learn(String text, String topic) {
        Learn out = new Learn();
        for (Grammar.Triple t : Grammar.facts(text, topic)) {
            if (t.subject.length() < 2 || t.object.length() < 2 || t.subject.length() > 80) {
                continue;
            }
            Row same = findSame(t);
            if (same != null) {
                if (same.triple.relation.equals(t.relation) && Facts.opposes(same.triple.object, t.object)) {
                    same.strength = Math.max(0, same.strength - CONTRADICTION_COST);
                    same.confirmed = now();
                    out.verdict = Verdict.CONTRADICTED;
                    out.against = same.triple;
                    if (same.strength == 0) {
                        all.remove(same);
                    }
                } else {
                    same.strength = Math.min(MAX_STRENGTH, same.strength + 1);
                    same.confirmed = now();
                    out.verdict = Verdict.REPEATED;
                }
                continue;
            }
            Row denied = findDenied(t);
            if (denied != null) {
                denied.strength = Math.max(0, denied.strength - CONTRADICTION_COST);
                denied.confirmed = now();
                out.verdict = Verdict.CONTRADICTED;
                out.against = denied.triple;
                if (denied.strength == 0) {
                    all.remove(denied);
                }
                continue;
            }
            all.add(new Row(t, 1, now()));
            out.added++;
        }
        while (all.size() > MOST) {
            all.remove(0);
        }
        if (out.stated()) {
            save();
        }
        return out;
    }

    private Row findSame(Grammar.Triple t) {
        for (Row r : all) {
            if (same(r.triple.subject, t.subject) && r.triple.relation.equals(t.relation) && same(r.triple.object, t.object)) {
                return r;
            }
        }
        return null;
    }

    /** The fact this statement denies: the same subject and relation, a different object that opposes the new one. */
    private Row findDenied(Grammar.Triple t) {
        for (Row r : all) {
            if (same(r.triple.subject, t.subject) && r.triple.relation.equals(t.relation) && !same(r.triple.object, t.object)
                    && opposes(r.triple.object, t.object)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Whether one value denies another: "not X", or a plain different value where at least one side is an article, a
     * year, a month or a word that negates ("nobody"). Two different free-text values ("a quasar", "a pulsar") are not
     * a contradiction — only a denial is — so learning does not treat every correction as a fight.
     */
    static boolean opposes(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty() || x.equals(y)) {
            return false;
        }
        if (x.startsWith("not ") || y.startsWith("not ") || x.startsWith("no ") || y.startsWith("no ")) {
            return true;
        }
        if (isNegationWord(x) || isNegationWord(y)) {
            return true;
        }
        return (isArticle(x) || isArticle(y) || isYear(x) || isYear(y) || isMonth(x) || isMonth(y)) && x.length() <= 24 && y.length() <= 24;
    }

    private static boolean isNegationWord(String s) {
        return s.equals("nobody") || s.equals("nothing") || s.equals("none") || s.equals("never") || s.equals("no one");
    }

    private static boolean isArticle(String s) {
        return s.equals("a") || s.equals("an") || s.equals("the");
    }

    private static boolean isYear(String s) {
        return s.matches("\\d{4}");
    }

    private static boolean isMonth(String s) {
        return s.matches("(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)");
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    private boolean known(Grammar.Triple t) {
        return findSame(t) != null;
    }

    static boolean same(String a, String b) {
        return norm(a).equals(norm(b));
    }

    static String norm(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.US).replaceFirst("^(the|a|an)\\s+", "").replaceAll("[^\\p{L}\\p{N} ]", "")
                .replaceAll("s\\b", "").replaceAll("\\s+", " ").trim();
    }

    /** True when a fact's subject names what was asked about ("Microsoft" for "Microsoft Corporation"). */
    static boolean names(String fact, String asked) {
        String f = norm(fact), a = norm(asked);
        return !a.isEmpty() && (f.equals(a) || f.startsWith(a + " ") || a.startsWith(f + " ") && f.length() > 3);
    }

    /** The answer to a question as a sentence, from what was read, or null when nothing read answers it. */
    public synchronized String answer(String question) {
        Grammar.Parse p = Grammar.parse(question);
        if (p.intent != Grammar.Intent.QUESTION && p.intent != Grammar.Intent.COMMAND) {
            return null;
        }
        List<Row> strong = new ArrayList<Row>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Row r = all.get(i);
            if (r.strength >= STRONG && match(p, r.triple) != null) {
                strong.add(r);
            }
        }
        if (strong.isEmpty()) {
            return null;
        }
        strong.sort((a, b) -> b.strength - a.strength);
        if (strong.size() > 1 && strong.get(0).strength == strong.get(1).strength && strong.size() >= AMBIGUOUS_LIMIT) {
            // Two strong facts answer differently and neither is better: say so rather than pick one at random.
            return null;
        }
        return match(p, strong.get(0).triple);
    }

    /** The sentence formula: a fact that answers the parse, said as a sentence; or null. */
    static String match(Grammar.Parse p, Grammar.Triple t) {
        String rel = t.relation;
        switch (p.wants) {
            case PERSON:
            case THING:
                // who invented the telephone: (telephone, invent by, Bell) → "Bell invented the telephone."
                if (!p.verb.isEmpty() && !p.verb.equals("be") && p.subject.isEmpty() && rel.equals(p.verb + " by") && names(t.subject, p.object)) {
                    return sentence(t.object + " " + t.surface + " " + article(p.object, t.subject) + ".");
                }
                // who founded X (F2 reads "who did X found"? no: F3) / what is X
                if (p.verb.equals("be") && rel.equals("is") && names(t.subject, p.subject)) {
                    return sentence(noun(t.subject) + " " + t.surface + " " + t.object + ".");
                }
                if (p.verb.equals("be") && rel.endsWith(" of") && names(t.subject, p.subject)) {
                    return sentence(t.subject + " is the " + rel.substring(0, rel.length() - 3) + " of " + t.object + ".");
                }
                if (p.verb.equals("be") && rel.endsWith(" of") && p.subject.toLowerCase(Locale.US).startsWith(rel.substring(0, rel.length() - 3) + " of ")
                        && names(t.object, p.subject.substring(rel.length()))) {
                    return sentence(t.subject + " is the " + rel.substring(0, rel.length() - 3) + " of " + t.object + ".");
                }
                return null;
            case TIME:
                if (!p.verb.isEmpty() && names(t.subject, p.subject.isEmpty() ? p.object : p.subject)
                        && (rel.equals(p.verb + " in") || p.verb.equals("bear") && rel.equals("born on") || p.verb.equals("born") && rel.equals("born on"))) {
                    return sentence(t.subject + " was " + t.surface + (rel.startsWith("born") ? " on " : " in ") + t.object + ".");
                }
                return null;
            case PLACE:
                if (names(t.subject, p.subject) && (rel.equals("born in") && (p.verb.equals("bear") || p.verb.equals("born"))
                        || !p.verb.isEmpty() && rel.equals(p.verb + " in") && !t.object.matches("\\d{4}"))) {
                    return sentence(t.subject + " was " + t.surface + " in " + t.object + ".");
                }
                return null;
            default:
                return null;
        }
    }

    /** "the telephone" as the question had it, else the fact's own words. */
    private static String article(String asked, String fact) {
        String a = asked == null ? "" : asked.trim();
        return a.isEmpty() ? fact : (Character.isUpperCase(a.charAt(0)) ? a : "the " + a);
    }

    /** A common noun opening a sentence takes its article: "quasar" → "a quasar"; a name stays as it is. */
    static String noun(String subject) {
        String n = subject.trim();
        if (n.isEmpty() || Character.isUpperCase(n.charAt(0)) || Character.isDigit(n.charAt(0))) {
            return n;
        }
        return ("aeiou".indexOf(Character.toLowerCase(n.charAt(0))) >= 0 ? "an " : "a ") + n;
    }

    static String sentence(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
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
            long now = now();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length == 6) {
                    try {
                        int strength = Integer.parseInt(p[4]);
                        long confirmed = Long.parseLong(p[5]);
                        if (strength > 0 && now - confirmed <= STALE_SECONDS) {
                            all.add(new Row(new Grammar.Triple(p[0], p[1], p[2], p[3]), Math.min(MAX_STRENGTH, strength), confirmed));
                        }
                    } catch (NumberFormatException ignored) {
                        // a damaged line: skip it, keep the rest
                    }
                } else if (p.length == 4) {
                    // written before strengths existed: one sighting, confirmed now, so it can still be confirmed again.
                    all.add(new Row(new Grammar.Triple(p[0], p[1], p[2], p[3]), 1, now));
                }
            }
        } catch (IOException e) {
            all.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        long now = now();
        StringBuilder b = new StringBuilder();
        for (Row r : all) {
            if (r.strength <= 0 || now - r.confirmed > STALE_SECONDS) {
                continue;
            }
            b.append(r.triple.subject.replace('\t', ' ')).append('\t').append(r.triple.relation).append('\t')
                    .append(r.triple.object.replace('\t', ' ')).append('\t').append(r.triple.surface).append('\t')
                    .append(r.strength).append('\t').append(r.confirmed).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }
}