package ai.dvine.suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DVINE 1's formulas for language: how to read any sentence into its parts, and what a question wants. Pure Java,
 * rules anyone can read, no model.
 *
 * <pre>
 * INTENT   greeting | thanks | question (ends "?", opens with a question word or an auxiliary) | command (opens with a
 *          verb: tell, show, find, make, explain…) | statement (everything else)
 * QUESTION who → a person · what/which → a thing · when → a time · where → a place · why → a reason · how → a way ·
 *          how many/much → a number · is/are/did/can… → yes or no
 * FOCUS    F1  wh + be + X                      "who is Ada Lovelace"            → about X
 *          F2  wh + do/did/does + X + V (+ Y)   "when did Nigeria gain independence" → X, V, Y
 *          F3  who/what + V-ed + Y              "who invented the telephone"     → ?, V, Y
 *          F4  wh + was/were + X + V-ed (+ by)  "when was Microsoft founded"     → X, V (passive)
 * FACT     T1  X was/were V-ed by Y (in Z)      → (X, V by, Y) and (X, V in, Z)
 *          T2  X was born (on D) (in P)         → (X, born on, D), (X, born in, P)
 *          T3  X is the capital/… of Y          → (X, capital of, Y)
 *          T4  X is/was (a|an|the) Y            → (X, is, Y)
 *          T5  X V-ed Y (in Z)                  → (X, V, Y), (Y, V by, X)   (V a known action verb)
 * </pre>
 */
public final class Grammar {
    private Grammar() {
    }

    public enum Intent { GREETING, THANKS, QUESTION, COMMAND, STATEMENT }

    public enum Wants { PERSON, THING, TIME, PLACE, REASON, WAY, NUMBER, YESNO, NONE }

    /** A sentence read by the formulas. */
    public static final class Parse {
        public final Intent intent;
        public final Wants wants;
        /** What it is about, the action (as its base form: "found", "invent"), and what the action is done to. */
        public final String subject, verb, object;
        public final boolean passive, negated;

        Parse(Intent intent, Wants wants, String subject, String verb, String object, boolean passive, boolean negated) {
            this.intent = intent;
            this.wants = wants;
            this.subject = subject == null ? "" : subject;
            this.verb = verb == null ? "" : verb;
            this.object = object == null ? "" : object;
            this.passive = passive;
            this.negated = negated;
        }

        @Override
        public String toString() {
            return intent + "/" + wants + " [" + subject + " | " + verb + " | " + object + "]" + (passive ? " passive" : "");
        }
    }

    /** One fact read from a sentence: subject, relation ("founded by", "is", "born in"), object; the verb as written. */
    public static final class Triple {
        public final String subject, relation, object, surface;

        public Triple(String subject, String relation, String object, String surface) {
            this.subject = subject;
            this.relation = relation;
            this.object = object;
            this.surface = surface;
        }

        @Override
        public String toString() {
            return "(" + subject + " | " + relation + " | " + object + ")";
        }
    }

    static final Set<String> AUX = new HashSet<String>(Arrays.asList("is", "are", "was", "were", "am", "do", "does", "did", "can", "could",
            "will", "would", "should", "has", "have", "had", "may", "might", "must"));
    static final Set<String> COMMANDS = new HashSet<String>(Arrays.asList("tell", "show", "give", "find", "make", "write", "explain",
            "list", "define", "describe", "search", "look", "send", "create", "open", "help", "teach", "summarise", "summarize", "compare"));
    /** Irregular past forms to their base. */
    static final Map<String, String> IRREGULAR = new HashMap<String, String>();

    static {
        String[] pairs = {"wrote", "write", "written", "write", "built", "build", "made", "make", "led", "lead", "won", "win", "began", "begin",
                "begun", "begin", "found", "find", "bought", "buy", "sold", "sell", "taught", "teach", "thought", "think", "drew", "draw",
                "drawn", "draw", "sang", "sing", "sung", "sing", "gave", "give", "given", "give", "took", "take", "taken", "take", "ran", "run",
                "became", "become", "came", "come", "saw", "see", "seen", "see", "knew", "know", "known", "know", "told", "tell", "held", "hold",
                "brought", "bring", "fought", "fight", "spoke", "speak", "spoken", "speak", "chose", "choose", "chosen", "choose", "flew", "fly",
                "grew", "grow", "grown", "grow", "left", "leave", "met", "meet", "paid", "pay", "rose", "rise", "set", "set", "shot", "shoot",
                "won", "win", "invented", "invent", "born", "bear"};
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            IRREGULAR.put(pairs[i], pairs[i + 1]);
        }
        IRREGULAR.put("founded", "found"); // founded a company, not "find"
    }

    /** Verbs that make a fact when they join two names ("Bell invented the telephone"). */
    static final Set<String> ACTIONS = new HashSet<String>(Arrays.asList("invent", "found", "create", "write", "discover", "build", "design",
            "develop", "direct", "paint", "compose", "establish", "lead", "own", "win", "marry", "produce", "publish", "launch", "start",
            "acquire", "buy", "sell", "rule", "govern", "play", "sing", "star", "coach", "manage", "make", "draw", "record", "release",
            "conquer", "defeat", "kill", "assassinate", "succeed", "replace", "name", "design", "patent", "introduce", "propose"));

    /** A verb's base form: "founded" → "found", "invented" → "invent", "wrote" → "write", "studies" → "study". */
    public static String base(String verb) {
        String v = verb == null ? "" : verb.toLowerCase(Locale.US).trim();
        if (IRREGULAR.containsKey(v)) {
            return IRREGULAR.get(v);
        }
        if (v.endsWith("ied") && v.length() > 4) {
            return v.substring(0, v.length() - 3) + "y";
        }
        if (v.endsWith("ed") && v.length() > 4) {
            String b = v.substring(0, v.length() - 2);
            if (b.length() > 2 && b.charAt(b.length() - 1) == b.charAt(b.length() - 2) && "lsz".indexOf(b.charAt(b.length() - 1)) < 0) {
                return b.substring(0, b.length() - 1); // "planned" → "plan"
            }
            if (ACTIONS.contains(b)) {
                return b;
            }
            if (ACTIONS.contains(b + "e") || b.matches(".*(at|iz|is|ov|ur|ag|uc|ir|ag|in)$")) {
                return b + "e"; // "created" → "create", "produced" → "produce"
            }
            return b;
        }
        if (v.endsWith("ies") && v.length() > 4) {
            return v.substring(0, v.length() - 3) + "y";
        }
        if (v.endsWith("es") && v.matches(".*(ss|sh|ch|x|z)es$")) {
            return v.substring(0, v.length() - 2);
        }
        if (v.endsWith("s") && !v.endsWith("ss") && v.length() > 3) {
            return v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static final Pattern GREET = Pattern.compile("(?i)^(hi|hello|hey|yo|good (morning|afternoon|evening))\\b.{0,20}$");
    private static final Pattern THANK = Pattern.compile("(?i)^(thanks|thank you|cheers|nice one)\\b.{0,30}$");
    private static final Pattern NEG = Pattern.compile("(?i)\\b(not|n't|never|no)\\b");

    /** F1–F4: what a question is about. */
    private static final Pattern F4 = Pattern.compile("(?i)^(who|what|when|where|why|how|which)\\s+(?:was|were|is|are)\\s+(.+?)\\s+(\\w+ed|\\w+en|born|built|made|written|won|led|founded)(?:\\s+by)?\\s*\\??$");
    private static final Pattern F2 = Pattern.compile("(?i)^(who|what|when|where|why|how|which)\\s+(?:did|does|do)\\s+(.+?)\\s+(\\w+)(?:\\s+(.+?))?\\s*\\??$");
    private static final Pattern F3 = Pattern.compile("(?i)^(who|what|which \\w+)\\s+(\\w+(?:ed|s)|wrote|built|made|led|won|founded|invented|discovered|painted|composed|founded)\\s+(.+?)\\s*\\??$");
    private static final Pattern F1 = Pattern.compile("(?i)^(who|what|when|where|why|how|which)(?:'s|\\s+is|\\s+are|\\s+was|\\s+were)\\s+(.+?)\\s*\\??$");

    /** Reads any sentence: its intent, what a question wants, and its subject, action and object. */
    public static Parse parse(String sentence) {
        String s = sentence == null ? "" : sentence.trim().replaceAll("\\s+", " ");
        String low = s.toLowerCase(Locale.US);
        boolean negated = NEG.matcher(s).find();
        if (GREET.matcher(s).matches()) {
            return new Parse(Intent.GREETING, Wants.NONE, "", "", "", false, false);
        }
        if (THANK.matcher(s).matches()) {
            return new Parse(Intent.THANKS, Wants.NONE, "", "", "", false, false);
        }
        String first = low.split("[^a-z']+").length == 0 ? "" : low.replaceAll("^[^a-z']+", "").split("[^a-z']+")[0];
        Wants wants = wants(low);
        boolean question = s.endsWith("?") || wants != Wants.NONE && !"how".equals(first) || AUX.contains(first)
                || low.matches("^how\\b.*");
        if (!question && (COMMANDS.contains(first) || first.equals("please"))) {
            String rest = s.replaceFirst("(?i)^(please\\s+)?\\w+\\s+(me\\s+)?(about\\s+|what\\s+|the\\s+meaning\\s+of\\s+)?", "");
            return new Parse(Intent.COMMAND, Wants.THING, clean(rest), first, "", false, negated);
        }
        if (!question) {
            return new Parse(Intent.STATEMENT, Wants.NONE, "", "", "", false, negated);
        }
        Matcher m = F4.matcher(s);
        if (m.matches()) {
            return new Parse(Intent.QUESTION, wants, clean(m.group(2)), base(m.group(3)), "", true, negated);
        }
        m = F2.matcher(s);
        if (m.matches()) {
            return new Parse(Intent.QUESTION, wants, clean(m.group(2)), base(m.group(3)), clean(m.group(4)), false, negated);
        }
        m = F3.matcher(s);
        if (m.matches() && !AUX.contains(m.group(2).toLowerCase(Locale.US))) {
            return new Parse(Intent.QUESTION, wants, "", base(m.group(2)), clean(m.group(3)), false, negated);
        }
        m = F1.matcher(s);
        if (m.matches()) {
            return new Parse(Intent.QUESTION, wants, clean(m.group(2)), "be", "", false, negated);
        }
        return new Parse(Intent.QUESTION, wants, clean(s.replaceFirst("(?i)^(\\w+)\\s+", "")), "", "", false, negated);
    }

    /** What kind of answer a question wants, from its opening words. */
    public static Wants wants(String low) {
        String t = " " + low.replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim() + " ";
        if (t.matches(" how (many|much|old|long|far|tall|big|high) .*")) {
            return Wants.NUMBER;
        }
        if (t.startsWith(" who ") || t.startsWith(" whom ") || t.startsWith(" whose ")) {
            return Wants.PERSON;
        }
        if (t.startsWith(" when ") || t.matches(" (what|which) (year|date|time|day|month|century) .*")) {
            return Wants.TIME;
        }
        if (t.startsWith(" where ") || t.matches(" (what|which) (country|city|place|state|continent) .*")) {
            return Wants.PLACE;
        }
        if (t.startsWith(" why ")) {
            return Wants.REASON;
        }
        if (t.startsWith(" how ")) {
            return Wants.WAY;
        }
        if (t.startsWith(" what ") || t.startsWith(" which ") || t.startsWith(" what's ")) {
            return Wants.THING;
        }
        String first = t.trim().split(" ")[0];
        return AUX.contains(first) ? Wants.YESNO : Wants.NONE;
    }

    /** A noun phrase without its leading article and trailing punctuation. */
    static String clean(String phrase) {
        if (phrase == null) {
            return "";
        }
        return phrase.trim().replaceAll("[?.!,;]+$", "").replaceFirst("(?i)^(the|a|an)\\s+", "").trim();
    }

    // ------------------------------------------------------------ facts from text

    private static final String NP = "([A-Z0-9][^,;:()]{0,80}?)";
    private static final Pattern T1 = Pattern.compile("^(?:The |A |An )?" + NP + "(?:\\s*\\([^)]*\\))?\\s+(?:was|were|is|are|has been)\\s+(?:\\w+ly\\s+)?(\\w+ed|\\w+en|built|made|led|won|written|found)\\s+by\\s+([^,.;]+?)(?:\\s+(?:in|on)\\s+([^,.;]+?))?(?:[,.;]|$)");
    private static final Pattern T2 = Pattern.compile("^(?:The |A |An )?" + NP + "(?:\\s*\\([^)]*\\))?\\s+(?:was|were)\\s+born\\s+(?:on\\s+([^,.;]+?\\d{4}|[^,.;]*?\\d{4}))?\\s*(?:,\\s*)?(?:in\\s+([^,.;]+?))?(?:[,.;]|$)");
    private static final Pattern T3 = Pattern.compile("^(?:The |A |An )?" + NP + "(?:\\s*\\([^)]*\\))?\\s+(?:is|was|are|were)\\s+the\\s+(capital|largest city|currency|official language|president|founder|ceo|author|leader)\\s+of\\s+([^,.;]+?)(?:[,.;]|$)");
    private static final Pattern T4 = Pattern.compile("^(?:The |A |An )?" + NP + "(?:\\s*\\([^)]*\\))?(?:,[^,]{0,80},)?\\s+(?:is|are|was|were)\\s+((?:a|an|the)\\s+[^.;]{3,200}?)(?:[.;]|$)");
    /**
     * T5 by hand: the first action verb in the past ("invented", "wrote") splits the sentence into who did it and what
     * they did it to (and when, after "in"). Null when there is none.
     */
    static Triple[] action(String s) {
        String[] w = s.replaceAll("[.;]+$", "").split("\\s+");
        for (int i = 1; i < w.length - 1 && i < 12; i++) {
            String v = w[i].replaceAll("[^A-Za-z]", "");
            if (!v.matches("(?i)\\w+ed|wrote|built|made|led|won|drew|sang|founded") || !ACTIONS.contains(base(v))
                    || AUX.contains(w[i - 1].toLowerCase(Locale.US))) {
                continue;
            }
            String who = clean(String.join(" ", Arrays.copyOfRange(w, 0, i)).replaceAll("\\s*\\([^)]*\\)", ""));
            String rest = String.join(" ", Arrays.copyOfRange(w, i + 1, w.length));
            String when = null;
            Matcher in = Pattern.compile("^(.+?)\\s+in\\s+(\\d{4}|[A-Z][^,]+)$").matcher(rest);
            if (in.matches()) {
                rest = in.group(1);
                when = in.group(2);
            }
            String what = clean(rest.split(",")[0]);
            if (who.isEmpty() || what.isEmpty() || !Character.isUpperCase(who.charAt(0)) || what.split(" ").length > 8) {
                return null;
            }
            return new Triple[] { new Triple(who, base(v), what, v), new Triple(what, base(v) + " by", who, v),
                    when == null ? null : new Triple(what, base(v) + " in", clean(when), v) };
        }
        return null;
    }

    private static final Pattern T5 = Pattern.compile("^(?:The |A |An )?" + NP + "(?:\\s*\\([^)]*\\))?\\s+(\\w+)\\s+(?:the\\s+|a\\s+|an\\s+)?([^,.;]{2,80}?)(?:\\s+in\\s+(\\d{4}|[A-Z][^,.;]+))?(?:[,.;]|$)");

    /**
     * The facts in a text, by the T1–T5 formulas. {@code topic}: what the text is about, so "It was founded by…" and
     * "He was born…" are about it.
     */
    public static List<Triple> facts(String text, String topic) {
        List<Triple> out = new ArrayList<Triple>();
        if (text == null) {
            return out;
        }
        for (String raw : text.split("(?<=[.!?])\\s+(?=[A-Z])")) {
            String s = raw.trim();
            if (topic != null && !topic.isEmpty()) {
                s = s.replaceFirst("^(It|He|She|They|This)\\s+", topic + " ");
            }
            Matcher m = T1.matcher(s);
            if (m.find()) {
                String v = m.group(2);
                out.add(new Triple(clean(m.group(1)), base(v) + " by", clean(m.group(3)), v));
                if (m.group(4) != null) {
                    out.add(new Triple(clean(m.group(1)), base(v) + " in", clean(m.group(4)), v));
                }
                continue;
            }
            m = T2.matcher(s);
            if (m.find() && (m.group(2) != null || m.group(3) != null)) {
                if (m.group(2) != null) {
                    out.add(new Triple(clean(m.group(1)), "born on", clean(m.group(2)), "born"));
                }
                if (m.group(3) != null) {
                    out.add(new Triple(clean(m.group(1)), "born in", clean(m.group(3)), "born"));
                }
                continue;
            }
            m = T3.matcher(s);
            if (m.find()) {
                out.add(new Triple(clean(m.group(1)), m.group(2).toLowerCase(Locale.US) + " of", clean(m.group(3)), "is"));
                continue;
            }
            Triple[] action = action(s);
            if (action != null) {
                for (Triple a : action) {
                    if (a != null) {
                        out.add(a);
                    }
                }
                continue;
            }
            m = T4.matcher(s);
            if (m.find()) {
                out.add(new Triple(clean(m.group(1)), "is", m.group(2).trim(), m.group(0).matches("(?s).*\\bwas\\b.*") ? "was" : "is"));
            }
        }
        return out;
    }
}
