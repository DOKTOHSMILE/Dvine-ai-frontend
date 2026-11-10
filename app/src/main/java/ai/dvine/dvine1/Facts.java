package ai.dvine.dvine1;

import ai.dvine.dvine1.formulas.Confidence;
import ai.dvine.dvine1.formulas.Grammar;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * Each fact has a strength and the time it was last confirmed ({@link Confidence}): seen again it grows, contradicted
 * or corrected it falls and is dropped at 0, unconfirmed for 30 days it fades. Only a fact of strength 2 or more is
 * given as an answer; when two different answers are equally strong, DVINE asks which is right. The file has six
 * columns (subject, relation, object, surface, strength, confirmed); an older four-column line loads as strength 1.
 */
public final class Facts {
    public static final int MOST = 20000;

    private final File file;
    private final List<Row> all = new ArrayList<Row>();
    /** The fact behind the last answer given, for the pilot's check or cancel. */
    private Row last;

    /** A fact with its strength and when it was last confirmed. */
    public static final class Row {
        public final Grammar.Triple fact;
        public int n;
        public long confirmed;

        Row(Grammar.Triple fact, int n, long confirmed) {
            this.fact = fact;
            this.n = n;
            this.confirmed = confirmed;
        }
    }

    public Facts(File file) {
        this.file = file;
        load();
    }

    /** Facts strong enough to answer from (strength 2 or more). */
    public synchronized int strong() {
        int c = 0;
        for (Row r : all) {
            if (Confidence.answers(r.n)) {
                c++;
            }
        }
        return c;
    }

    public synchronized int size() {
        return all.size();
    }

    /** Reads a text for facts, seen once each; how many were new. */
    public synchronized int learn(String text, String topic) {
        return learn(text, topic, 1, System.currentTimeMillis());
    }

    /**
     * Reads a text for facts: a new one starts at {@code sightings} (2 for an encyclopedia or the pilot's own words), one
     * seen again grows, one it contradicts falls. How many were new.
     */
    public synchronized int learn(String text, String topic, int sightings, long now) {
        int added = 0;
        boolean changed = false;
        for (Grammar.Triple t : Grammar.facts(text, topic)) {
            if (t.subject.length() < 2 || t.object.length() < 2 || t.subject.length() > 80) {
                continue;
            }
            int was = all.size();
            changed |= see(t, sightings, now);
            added += all.size() > was ? 1 : 0;
        }
        while (all.size() > MOST) {
            all.remove(weakest());
        }
        if (changed) {
            save();
        }
        return added;
    }

    /** One fact seen: C1 first sight, C2 seen again, C3 what it contradicts falls. True when anything changed. */
    public synchronized boolean see(Grammar.Triple t, int sightings, long now) {
        Row same = null;
        for (int i = all.size() - 1; i >= 0; i--) {
            Row k = all.get(i);
            if (same(k.fact.subject, t.subject) && k.fact.relation.equals(t.relation) && same(k.fact.object, t.object)) {
                same = k;
            } else if (opposes(k.fact, t)) {
                k.n = Confidence.down(k.n);
                if (k.n == 0) {
                    all.remove(i);
                }
            }
        }
        if (same != null) {
            same.n = Confidence.up(same.n);
            same.confirmed = now;
        } else {
            all.add(new Row(t, Confidence.first(sightings), now));
        }
        return true;
    }

    /**
     * Two facts that cannot both be true: the same subject and relation with a different object, for a relation that
     * has one answer (born on/in, founded in, invented by, capital of).
     */
    public static boolean opposes(Grammar.Triple a, Grammar.Triple b) {
        if (!same(a.subject, b.subject) || !a.relation.equals(b.relation) || same(a.object, b.object)) {
            return false;
        }
        String r = a.relation;
        return r.startsWith("born ") || r.endsWith(" in") && a.object.matches(".*\\d{3,4}.*") && b.object.matches(".*\\d{3,4}.*")
                || r.endsWith(" by") || r.endsWith(" of") && !r.equals("part of") && !r.equals("one of");
    }

    private int weakest() {
        int w = 0;
        for (int i = 1; i < all.size(); i++) {
            if (all.get(i).n < all.get(w).n) {
                w = i;
            }
        }
        return w;
    }

    public static boolean same(String a, String b) {
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

    /**
     * The answer to a question as a sentence, from the strongest fact that answers it (C7), or null when no fact of
     * strength 2 answers it. Two different answers within 1 of each other: both are given and DVINE asks which is
     * right (C8).
     */
    public synchronized String answer(String question) {
        Grammar.Parse p = Grammar.parse(question);
        last = null;
        if (p.intent != Grammar.Intent.QUESTION && p.intent != Grammar.Intent.COMMAND) {
            return null;
        }
        Row best = null, second = null;
        String bestSaid = null, secondSaid = null;
        for (int i = all.size() - 1; i >= 0; i--) {
            Row r = all.get(i);
            String said = match(p, r.fact);
            if (said == null) {
                continue;
            }
            if (best == null || r.n > best.n) {
                if (best != null && !said.equals(bestSaid)) {
                    second = best;
                    secondSaid = bestSaid;
                }
                best = r;
                bestSaid = said;
            } else if (!said.equals(bestSaid) && (second == null || r.n > second.n)) {
                second = r;
                secondSaid = said;
            }
        }
        if (best == null || !Confidence.answers(best.n)) {
            return null;
        }
        if (second != null && Confidence.tie(best.n, second.n)) {
            return "I've read two answers: " + bestSaid + " Or: " + secondSaid + " Which is right?";
        }
        last = best;
        return bestSaid;
    }

    /** True when a fact of strength 2 or more answers {@code question} plainly (the last answer is kept). */
    public synchronized boolean knows(String question) {
        Row keep = last;
        String a = answer(question);
        last = keep;
        return a != null && !a.startsWith("I've read two answers");
    }

    /** The fact behind the last answer, or null. */
    public synchronized Grammar.Triple lastAnswered() {
        return last == null ? null : last.fact;
    }

    /** C4: the pilot accepted the last answer. */
    public synchronized void accepted() {
        if (last != null) {
            last.n = Confidence.up(last.n);
            last.confirmed = System.currentTimeMillis();
            save();
        }
    }

    /** C5: the pilot corrected the last answer; it is dropped at 0. */
    public synchronized void corrected() {
        if (last != null) {
            last.n = Confidence.down(last.n);
            if (last.n == 0) {
                all.remove(last);
            }
            last = null;
            save();
        }
    }

    /**
     * The review pass: C6 decay of facts unconfirmed for 30 days (their clock restarts), and duplicates merged (the
     * same subject, relation and object written differently: strengths added, capped). How many changed.
     */
    public synchronized int review(long now) {
        int changed = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            Row r = all.get(i);
            int n = Confidence.decayed(r.n, r.confirmed, now);
            if (n != r.n) {
                changed++;
                r.n = n;
                r.confirmed = now;
                if (n == 0) {
                    all.remove(i);
                }
            }
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            Row r = all.get(i);
            for (int j = 0; j < i; j++) {
                Row k = all.get(j);
                if (same(k.fact.subject, r.fact.subject) && k.fact.relation.equals(r.fact.relation) && same(k.fact.object, r.fact.object)) {
                    k.n = Math.min(Confidence.CAP, k.n + r.n);
                    k.confirmed = Math.max(k.confirmed, r.confirmed);
                    all.remove(i);
                    changed++;
                    break;
                }
            }
        }
        if (changed > 0) {
            save();
        }
        return changed;
    }

    /** The strength of a fact, 0 when not known. */
    public synchronized int strength(String subject, String relation, String object) {
        for (Row r : all) {
            if (same(r.fact.subject, subject) && r.fact.relation.equals(relation) && same(r.fact.object, object)) {
                return r.n;
            }
        }
        return 0;
    }

    /** Every fact with its strength, oldest first (copies). */
    public synchronized List<Row> rows() {
        List<Row> out = new ArrayList<Row>();
        for (Row r : all) {
            out.add(new Row(r.fact, r.n, r.confirmed));
        }
        return out;
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

    /** The answer to {@code question} from one fact, said as a sentence, or null. */
    public String answerFrom(String question, Grammar.Triple t) {
        return match(Grammar.parse(question), t);
    }

    /** Every fact, oldest first. */
    public synchronized List<Grammar.Triple> all() {
        List<Grammar.Triple> out = new ArrayList<Grammar.Triple>();
        for (Row r : all) {
            out.add(r.fact);
        }
        return out;
    }

    /** A question each fact answers, by the focus formulas in reverse ("Who invented the telephone?"), or null. */
    public static String question(Grammar.Triple t) {
        String r = t.relation;
        if (r.equals("is") || r.endsWith(" of")) {
            return "What is " + noun(t.subject) + "?";
        }
        if (r.equals("born in")) {
            return "Where was " + t.subject + " born?";
        }
        if (r.equals("born on")) {
            return "When was " + t.subject + " born?";
        }
        if (r.endsWith(" by")) {
            return "Who " + t.surface + " " + (Character.isUpperCase(t.subject.charAt(0)) ? t.subject : "the " + t.subject) + "?";
        }
        if (r.endsWith(" in")) {
            return (t.object.matches(".*\\d{4}.*") ? "When" : "Where") + " was " + t.subject + " " + t.surface + "?";
        }
        return null;
    }

    /** Forgets one fact. */
    public synchronized void forget(Grammar.Triple t) {
        for (int i = all.size() - 1; i >= 0; i--) {
            Grammar.Triple k = all.get(i).fact;
            if (same(k.subject, t.subject) && k.relation.equals(t.relation) && same(k.object, t.object)) {
                all.remove(i);
            }
        }
        last = null;
        save();
    }

    public synchronized void clear() {
        all.clear();
        last = null;
        save();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            long then = file.lastModified();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 3) {
                    continue;
                }
                // older lines (3 or 4 columns) load as strength 1, confirmed when the file was last written
                int n = 1;
                long at = then;
                if (p.length >= 6) {
                    try {
                        n = Math.max(1, Math.min(Confidence.CAP, Integer.parseInt(p[4].trim())));
                        at = Long.parseLong(p[5].trim());
                    } catch (NumberFormatException e) {
                        n = 1;
                    }
                }
                all.add(new Row(new Grammar.Triple(p[0], p[1], p[2], p.length >= 4 ? p[3] : p[1]), n, at));
            }
        } catch (IOException e) {
            all.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Row r : all) {
            Grammar.Triple t = r.fact;
            b.append(t.subject.replace('\t', ' ')).append('\t').append(t.relation).append('\t').append(t.object.replace('\t', ' '))
                    .append('\t').append(t.surface).append('\t').append(r.n).append('\t').append(r.confirmed).append('\n');
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
