package ai.ibiko.ibiko1.comprehend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Universal Comprehension Layer, first version: one message turned into a structured meaning before any brain
 * answers or acts. What the person wants (the intent), each step they ask for (the action, its target with its
 * attributes, the thing it belongs to, a position or direction), whether it is negated, what "it", "its" and "that
 * thing" point to in the conversation, which sense of a word the sentence uses (WordNet), whether a part belongs to
 * its whole (ConceptNet), and whether to ask before acting. The same reading serves chat, voice, 3D editing, files
 * and code. Rules and lookups, no model: it runs on any device in a few milliseconds. Pure Java.
 */
public final class Comprehension {
    /** What the person wants, as the blueprint names it. */
    public enum Intent { QUESTION, EXPLANATION, CREATION, MODIFICATION, DELETION, NAVIGATION, CALCULATION, RESEARCH, CONVERSATION, INSTRUCTION }

    /** One step asked for. */
    public static final class Operation {
        public String action, target, parent, position, direction;
        public final Map<String, String> attributes = new LinkedHashMap<String, String>();
        /** True when ConceptNet knows the target as a part of the parent, false when not, null when nothing to check. */
        public Boolean partKnown;
    }

    /** The whole reading of one message. */
    public static final class Meaning {
        public Intent intent = Intent.CONVERSATION;
        public Intent secondary;
        public boolean negated;
        public final List<Operation> operations = new ArrayList<Operation>();
        public final Map<String, String> references = new LinkedHashMap<String, String>();
        /** The sense the sentence uses, for words with more than one: word -> definition. */
        public final Map<String, String> senses = new LinkedHashMap<String, String>();
        /** Why to ask before acting; null when the meaning is clear enough. */
        public String clarify;

        public Operation first() {
            return operations.isEmpty() ? null : operations.get(0);
        }

        /** As JSON, the blueprint's shape. */
        public String toJson() {
            StringBuilder b = new StringBuilder("{\"intent\":\"").append(intent.name().toLowerCase(Locale.US)).append('"');
            if (secondary != null) {
                b.append(",\"secondary_intent\":\"").append(secondary.name().toLowerCase(Locale.US)).append('"');
            }
            if (negated) {
                b.append(",\"negated\":true");
            }
            if (!references.isEmpty()) {
                b.append(",\"references\":").append(map(references));
            }
            b.append(",\"operations\":[");
            for (int i = 0; i < operations.size(); i++) {
                Operation o = operations.get(i);
                b.append(i == 0 ? "" : ",").append("{\"action\":").append(q(o.action)).append(",\"target\":").append(q(o.target));
                if (!o.attributes.isEmpty()) {
                    b.append(",\"attributes\":").append(map(o.attributes));
                }
                if (o.parent != null) {
                    b.append(",\"source\":").append(q(o.parent));
                }
                if (o.position != null) {
                    b.append(",\"position\":").append(q(o.position));
                }
                if (o.direction != null) {
                    b.append(",\"direction\":").append(q(o.direction));
                }
                if (o.partKnown != null) {
                    b.append(",\"part_known\":").append(o.partKnown);
                }
                b.append('}');
            }
            b.append(']');
            if (!senses.isEmpty()) {
                b.append(",\"senses\":").append(map(senses));
            }
            b.append(",\"requires_confirmation\":").append(clarify != null);
            if (clarify != null) {
                b.append(",\"clarify\":").append(q(clarify));
            }
            return b.append('}').toString();
        }

        /** One short line for a brain: what the message means, so it does not have to work it out. */
        public String brief() {
            StringBuilder b = new StringBuilder("Understood: ").append(intent.name().toLowerCase(Locale.US));
            if (negated) {
                b.append(" (negated: the user does NOT want this)");
            }
            for (Operation o : operations) {
                b.append("; ").append(o.action == null ? "?" : o.action);
                if (o.direction != null) {
                    b.append(' ').append(o.direction);
                }
                if (o.target != null) {
                    b.append(' ').append(o.target);
                }
                if (!o.attributes.isEmpty()) {
                    b.append(" (").append(String.join(", ", o.attributes.values())).append(')');
                }
                if (o.position != null) {
                    b.append(" on the ").append(o.position);
                }
                if (o.parent != null) {
                    b.append(" of ").append(o.parent);
                }
            }
            for (Map.Entry<String, String> r : references.entrySet()) {
                b.append("; \"").append(r.getKey()).append("\" = ").append(r.getValue());
            }
            for (Map.Entry<String, String> s : senses.entrySet()) {
                b.append("; ").append(s.getKey()).append(" here means: ").append(s.getValue());
            }
            if (clarify != null) {
                b.append("; ask first: ").append(clarify);
            }
            String t = b.append('.').toString();
            return t.length() > 400 ? t.substring(0, 397) + "…" : t;
        }

        private static String q(String s) {
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }

        private static String map(Map<String, String> m) {
            StringBuilder b = new StringBuilder("{");
            for (Map.Entry<String, String> e : m.entrySet()) {
                b.append(b.length() == 1 ? "" : ",").append(q(e.getKey())).append(':').append(q(e.getValue()));
            }
            return b.append('}').toString();
        }
    }

    // ------------------------------------------------------------ the words that carry the meaning

    /** Actions and their canonical name; the intent each one serves. */
    private static final String[][] ACTIONS = {
            {"remove|delete|erase|discard|eliminate|get rid of|take away|take off|cut off|pull off|clear", "remove", "DELETION"},
            {"add|insert|attach|include|append|put", "add", "MODIFICATION"},
            {"enlarge|grow|scale up|increase|expand|zoom in", "resize", "MODIFICATION"},
            {"shrink|reduce|scale down|decrease|zoom out", "resize", "MODIFICATION"},
            {"replace|swap|substitute", "replace", "MODIFICATION"},
            {"move|drag|shift|place|position", "move", "MODIFICATION"},
            {"rotate|turn|spin|flip", "rotate", "MODIFICATION"},
            {"change|modify|edit|alter|paint|colour|color|recolour|recolor|rename|set|update|fix|adjust", "change", "MODIFICATION"},
            {"create|design|draw|build|generate|compose|write|make|produce|sketch", "create", "CREATION"},
            {"open|launch|go to|navigate to|show me|take me to|switch to", "open", "NAVIGATION"},
            {"calculate|compute|work out|add up|multiply|divide|subtract|convert", "calculate", "CALCULATION"},
            {"find|search|look up|research|google|browse|check", "research", "RESEARCH"},
            {"explain|describe|tell me about|teach me|clarify", "explain", "EXPLANATION"},
            {"save|send|share|copy|download|install|run|play|stop|start|call|print|upload|export|close|cancel|undo|redo", "", "INSTRUCTION"},
    };
    private static final Pattern COLOUR = Pattern.compile("^(red|orange|yellow|green|blue|purple|violet|pink|brown|black|white|grey|gray|"
            + "silver|gold|golden|cyan|magenta|beige|navy|maroon|teal|turquoise|crimson|dark|light)$");
    private static final Pattern SIZE = Pattern.compile("^(small|smaller|smallest|little|tiny|big|bigger|biggest|large|larger|largest|huge|"
            + "tall|short|long|wide|narrow|thick|thin)$");
    private static final Pattern SIDE = Pattern.compile("^(left|right|top|bottom|front|back|rear|middle|centre|center|upper|lower|inner|outer|"
            + "first|second|third|last)$");
    private static final Pattern DET = Pattern.compile("^(the|a|an|this|that|these|those|my|your|our|their|his|her|its|some|all|every|each|both)$");
    private static final Pattern PREP = Pattern.compile("^(of|off|from|on|in|into|to|with|at|inside|beneath|under|above|over|behind|near|beside|onto|for|by)$");
    private static final Pattern PRONOUN = Pattern.compile("^(it|its|it's|this|that|them|they|their|these|those)$");
    private static final Pattern QUESTION = Pattern.compile("^(what|who|whom|whose|which|where|when|why|how|is|are|was|were|do|does|did|can|could|"
            + "will|would|should|shall|may|might|has|have|had)\\b");
    private static final Pattern FEELING = Pattern.compile("^(i am|i'm|im|i feel|i felt|i was|we are|i love|i hate|i like|thank|thanks|hello|hi|hey|good morning|good evening|good night)\\b");

    private final Dictionary dictionary;
    private final Concepts concepts;
    /** The conversation's recent things, newest first: {word, turn}. */
    private final Deque<Object[]> recent = new ArrayDeque<Object[]>();
    private int turn;
    private long lastAt;
    static final int KEEP_TURNS = 12;
    static final long KEEP_MS = 30 * 60_000L;

    public Comprehension(Dictionary dictionary, Concepts concepts) {
        this.dictionary = dictionary;
        this.concepts = concepts;
    }

    /** A new conversation: nothing earlier is referred to any more. */
    public synchronized void forget() {
        recent.clear();
    }

    /** The thing most recently talked about, or null. */
    public synchronized String lastThing() {
        return recent.isEmpty() ? null : (String) recent.peekFirst()[0];
    }

    public Meaning read(String message) {
        return read(message, System.currentTimeMillis());
    }

    /** Reads one message of the conversation, at {@code now}; what it mentions becomes context for the next. */
    public synchronized Meaning read(String message, long now) {
        if (lastAt > 0 && now - lastAt > KEEP_MS) {
            recent.clear(); // an old conversation's "it" no longer means anything
        }
        lastAt = now;
        turn++;
        while (!recent.isEmpty() && turn - (Integer) recent.peekLast()[1] > KEEP_TURNS) {
            recent.removeLast();
        }
        Meaning m = new Meaning();
        String raw = message == null ? "" : message.trim();
        String t = normal(raw);
        m.negated = t.matches("(?s).*\\b(do not|don't|dont|never|no need to|stop)\\b.*") && !t.matches("^stop\\b.*");
        String body = t.replaceFirst("^(please |can you |could you |would you |will you |kindly |i want you to |i need you to |"
                + "help me |let's |lets |do not |don't |dont |never )+", "");
        // the steps: clauses joined by "and", "then", commas, each starting with an action
        List<String> clauses = new ArrayList<String>();
        for (String c : body.split("\\s*(?:,\\s*(?:and\\s+)?(?:then\\s+)?|\\band then\\b|\\bthen\\b|;|\\band\\b(?=\\s+(?:" + allVerbs() + ")\\b))\\s*")) {
            if (!c.trim().isEmpty()) {
                clauses.add(c.trim());
            }
        }
        Intent first = null;
        for (String c : clauses) {
            Object[] act = action(c);
            if (act == null) {
                continue;
            }
            Operation o = new Operation();
            o.action = (String) act[0];
            String rest = (String) act[2];
            if ("resize".equals(o.action) || rest.matches("(?s).*\\b(bigger|larger|smaller|wider|taller|shorter)\\b.*")) {
                o.action = "resize";
                o.direction = rest.matches("(?s).*\\b(smaller|shorter|narrower|thinner|shrink|reduce|decrease|down|out)\\b.*")
                        || ((String) act[3]).matches("shrink|reduce|scale down|decrease|zoom out") ? "decrease" : "increase";
            }
            object(rest, o, m);
            m.operations.add(o);
            Intent i = Intent.valueOf((String) act[1]);
            if ("resize".equals(o.action)) {
                i = Intent.MODIFICATION;
            }
            if (first == null) {
                first = i;
            } else if (i != first && m.secondary == null) {
                m.secondary = i;
            }
        }
        if (first != null) {
            m.intent = first;
        } else if (raw.endsWith("?") || QUESTION.matcher(t).find()) {
            m.intent = t.matches("^(why|how)\\b.*") ? Intent.EXPLANATION : Intent.QUESTION;
            question(t, m);
        } else if (t.matches(".*\\d\\s*[-+*/x×÷%^]\\s*\\d.*") || t.matches(".*\\b\\d+(\\.\\d+)?%\\s*of\\b.*")) {
            m.intent = Intent.CALCULATION;
        } else if (FEELING.matcher(t).find()) {
            m.intent = Intent.CONVERSATION;
            statement(t, m);
        } else {
            m.intent = Intent.CONVERSATION;
            statement(t, m);
        }
        senses(raw, m);
        return m;
    }

    private static String normal(String s) {
        return s.toLowerCase(Locale.US).replace('’', '\'').replaceAll("\\bcan't\\b", "can not").replaceAll("\\bwon't\\b", "will not")
                .replaceAll("[\"“”]", "").replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
    }

    private static String allVerbs() {
        StringBuilder b = new StringBuilder();
        for (String[] a : ACTIONS) {
            b.append(b.length() == 0 ? "" : "|").append(a[0]);
        }
        return b.toString();
    }

    /** The action a clause opens with (a phrasal "take X off" too): {canonical, intent, rest, the verb as said}. */
    private static Object[] action(String clause) {
        String c = clause;
        Matcher phrasal = Pattern.compile("^(take|get|cut|pull|rip|knock|strip)\\s+(.+?)\\s+(off|away|out)(?:\\s+(?:of\\s+)?(.*))?$").matcher(c);
        if (phrasal.find()) {
            String rest = phrasal.group(2) + (phrasal.group(4) == null ? "" : " off " + phrasal.group(4));
            return new Object[] {"remove", "DELETION", rest.trim(), phrasal.group(1) + " " + phrasal.group(3)};
        }
        for (String[] a : ACTIONS) {
            Matcher m = Pattern.compile("^(" + a[0] + ")\\b\\s*(.*)$").matcher(c);
            if (m.find()) {
                String said = m.group(1);
                String canonical = a[1].isEmpty() ? said : a[1];
                if ("make".equals(said) && m.group(2).matches("(?s).*\\b(bigger|larger|smaller|wider|taller|shorter|thinner|thicker)\\b.*")) {
                    return new Object[] {"resize", "MODIFICATION", m.group(2), said}; // "make it bigger" changes it
                }
                if ("make".equals(said) && m.group(2).matches("^(it|its|this|that|them|the \\w+)\\s+\\w+.*")) {
                    return new Object[] {"change", "MODIFICATION", m.group(2), said}; // "make it red"
                }
                return new Object[] {canonical, a[2], m.group(2), said};
            }
        }
        return null;
    }

    /** The target of a step: the noun after the determiners and describing words, the thing it belongs to, a side. */
    private void object(String rest, Operation o, Meaning m) {
        String[] w = rest.replaceAll("[,.;:!?]", " ").trim().split("\\s+");
        String possessor = null;
        int i = 0;
        List<String> pre = new ArrayList<String>();
        // the noun phrase: up to the first preposition
        for (; i < w.length; i++) {
            String x = w[i];
            if (x.isEmpty()) {
                continue;
            }
            if (PREP.matcher(x).matches() && !pre.isEmpty()) {
                break;
            }
            if (x.matches("its|their|his|her|it's")) {
                String ref = resolve(x);
                if (ref != null) {
                    m.references.put(x, ref);
                    possessor = ref;
                }
                continue;
            }
            if (x.endsWith("'s") && x.length() > 2) {
                possessor = x.substring(0, x.length() - 2); // "the car's door"
                remember(possessor);
                continue;
            }
            if (DET.matcher(x).matches()) {
                continue;
            }
            pre.add(x);
        }
        String head = null;
        for (int k = pre.size() - 1; k >= 0; k--) {
            String x = pre.get(k);
            if (COLOUR.matcher(x).matches()) {
                o.attributes.put("colour", x);
            } else if (SIZE.matcher(x).matches()) {
                if (!x.matches("bigger|larger|smaller|wider|taller|shorter")) {
                    o.attributes.put("size", x);
                }
            } else if (SIDE.matcher(x).matches()) {
                o.position = x;
            } else if (head == null && !x.matches("again|now|please|too|also|more|less|up|down|a bit|bit|slightly|much")) {
                head = x;
            } else if (head != null && !x.matches("again|now|please")) {
                head = x + " " + head; // "steering wheel"
            }
        }
        if (head == null || PRONOUN.matcher(head).matches()) {
            String said = head == null ? (rest.matches("^(it|this|that|them)\\b.*") ? rest.split("\\s+")[0] : null) : head;
            if (said != null) {
                String ref = resolve(said);
                if (ref != null) {
                    m.references.put(said, ref);
                    head = ref;
                } else {
                    m.clarify = "what \"" + said + "\" refers to";
                    head = said;
                }
            }
        }
        if (rest.matches("^(that|the) thing( i showed you| i sent| from before| earlier)?.*")) {
            String ref = lastThing();
            m.references.put("that thing", ref == null ? "unknown" : ref);
            if (ref == null) {
                m.clarify = "which thing was meant";
            } else {
                head = ref;
            }
        }
        o.target = head;
        // what it belongs to: "off the car", "of the car", "from the car", "on the car", or a possessive
        String tail = String.join(" ", java.util.Arrays.copyOfRange(w, Math.min(i, w.length), w.length));
        Matcher from = Pattern.compile("^(?:off|of|from|on|in|inside|out of)\\s+(?:(?:the|a|an|my|your|its|this|that|their)\\s+)?([\\w'-]+(?:\\s[\\w'-]+)?)").matcher(tail);
        if (from.find()) {
            String p = from.group(1);
            if (SIDE.matcher(p.split(" ")[0]).matches()) {
                o.position = p.split(" ")[0]; // "on its right", "on the left"
                if (tail.matches("^(?:on|in)\\s+its\\b.*")) {
                    String ref = resolve("its");
                    if (ref != null) {
                        m.references.put("its", ref);
                        possessor = possessor == null ? ref : possessor;
                    }
                }
            } else if (PRONOUN.matcher(p.split(" ")[0]).matches()) {
                String ref = resolve(p.split(" ")[0]);
                if (ref != null) {
                    m.references.put(p.split(" ")[0], ref);
                    o.parent = ref;
                }
            } else {
                o.parent = p.split(" ").length > 1 && PREP.matcher(p.split(" ")[1]).matches() ? p.split(" ")[0] : p;
            }
        }
        if (o.parent == null && possessor != null && !possessor.equals(o.target)) {
            o.parent = possessor;
        }
        if (o.parent != null) {
            remember(o.parent);
        }
        if (o.target != null && !PRONOUN.matcher(o.target).matches()) {
            remember(o.target);
        }
        if (o.target != null && o.parent != null && concepts != null && concepts.present()) {
            o.partKnown = concepts.partOf(o.target, o.parent);
        }
    }

    /** A question: what it is about becomes the conversation's last thing. */
    private void question(String t, Meaning m) {
        Matcher about = Pattern.compile("\\b(?:a|an|the|is|are|of|about)\\s+([a-z][a-z-]+)\\s*$").matcher(t);
        if (about.find() && !PRONOUN.matcher(about.group(1)).matches()) {
            remember(about.group(1));
        }
        Matcher pron = Pattern.compile("\\b(it|its|that|this|they|them)\\b").matcher(t);
        if (pron.find()) {
            String ref = resolve(pron.group(1));
            if (ref != null) {
                m.references.put(pron.group(1), ref);
            }
        }
    }

    /** A statement ("I bought a new car", "it is red"): its things become context; its pronouns are resolved. */
    private void statement(String t, Meaning m) {
        Matcher pron = Pattern.compile("^(it|its|they|this|that)\\b").matcher(t);
        if (pron.find()) {
            String ref = resolve(pron.group(1));
            if (ref != null) {
                m.references.put(pron.group(1), ref);
            }
        }
        Matcher thing = Pattern.compile("\\b(?:a|an|the|my|your|our|new|old)\\s+(?:(?:new|old|big|small|little|red|blue|green|black|white)\\s+)*([a-z][a-z-]{2,})\\b").matcher(t);
        String last = null;
        while (thing.find()) {
            if (!PRONOUN.matcher(thing.group(1)).matches() && isNoun(thing.group(1))) {
                last = thing.group(1);
            }
        }
        if (last != null) {
            remember(last);
        }
    }

    private boolean isNoun(String w) {
        return dictionary == null || !dictionary.present() || !dictionary.senses(w, "noun").isEmpty();
    }

    private void remember(String thing) {
        if (thing == null || thing.isEmpty() || PRONOUN.matcher(thing).matches()) {
            return;
        }
        recent.removeIf(r -> r[0].equals(thing));
        recent.addFirst(new Object[] {thing, turn});
    }

    /** What a pronoun points to: the newest thing in the conversation (a part's whole for "its" when it was the last). */
    private String resolve(String pronoun) {
        return recent.isEmpty() ? null : (String) recent.peekFirst()[0];
    }

    /** For each noun with more than one meaning, the one this sentence uses. */
    private void senses(String raw, Meaning m) {
        if (dictionary == null || !dictionary.present()) {
            return;
        }
        int n = 0;
        for (String w : Dictionary.contentWords(raw)) {
            if (n >= 3) {
                break;
            }
            List<Dictionary.Sense> nouns = dictionary.senses(w, "noun");
            if (nouns.size() < 2) {
                continue;
            }
            Dictionary.Sense s = dictionary.meant(w, raw);
            if (s != null && s.pos.equals("noun")) {
                m.senses.put(w, s.definition.length() > 90 ? s.definition.substring(0, 87) + "…" : s.definition);
                n++;
            }
        }
    }
}
