package ai.dvine.dvine1;

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
 */
public final class Facts {
    public static final int MOST = 20000;

    private final File file;
    private final List<Grammar.Triple> all = new ArrayList<Grammar.Triple>();

    public Facts(File file) {
        this.file = file;
        load();
    }

    public synchronized int size() {
        return all.size();
    }

    /** Reads a text for facts and keeps the new ones; how many were new. */
    public synchronized int learn(String text, String topic) {
        int added = 0;
        for (Grammar.Triple t : Grammar.facts(text, topic)) {
            if (t.subject.length() < 2 || t.object.length() < 2 || t.subject.length() > 80 || known(t)) {
                continue;
            }
            all.add(t);
            added++;
        }
        while (all.size() > MOST) {
            all.remove(0);
        }
        if (added > 0) {
            save();
        }
        return added;
    }

    private boolean known(Grammar.Triple t) {
        for (Grammar.Triple k : all) {
            if (same(k.subject, t.subject) && k.relation.equals(t.relation) && same(k.object, t.object)) {
                return true;
            }
        }
        return false;
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
        for (int i = all.size() - 1; i >= 0; i--) {
            String said = match(p, all.get(i));
            if (said != null) {
                return said;
            }
        }
        return null;
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
        return new ArrayList<Grammar.Triple>(all);
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
                if (p.length == 4) {
                    all.add(new Grammar.Triple(p[0], p[1], p[2], p[3]));
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
        StringBuilder b = new StringBuilder();
        for (Grammar.Triple t : all) {
            b.append(t.subject.replace('\t', ' ')).append('\t').append(t.relation).append('\t').append(t.object.replace('\t', ' '))
                    .append('\t').append(t.surface).append('\n');
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
