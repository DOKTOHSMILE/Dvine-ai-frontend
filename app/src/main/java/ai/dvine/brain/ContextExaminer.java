package ai.dvine.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a whole sentence before anything acts on it, so the personas
 * understand what was meant rather than reacting to single words:
 * <ul>
 *   <li>what kind of sentence it is (question, request, statement, greeting, thanks, a correction, never mind);</li>
 *   <li>who it is for ("Ivie, …", "… Obi", "both of you …");</li>
 *   <li>whether the request is negated ("don't open Telegram" must not open it);</li>
 *   <li>the core request without fillers ("could you please tell me what X is" → "what is x");</li>
 *   <li>what "it", "that", "this" or "them" refer to (the topic of the last turn);</li>
 *   <li>corrections ("no, I meant the river bank") applied to the last request;</li>
 *   <li>several requests in one sentence ("tell me the time and then a joke");</li>
 *   <li>when the sentence is incomplete or a reference can't be resolved: a question back instead of a guess.</li>
 * </ul>
 * Whole words only (never parts of words). Keeps the last topic between turns. Pure Java.
 */
public final class ContextExaminer {
    public enum Kind {
        QUESTION,
        REQUEST,
        STATEMENT,
        GREETING,
        THANKS,
        CORRECTION,
        NEVER_MIND
    }

    /** What a sentence means, as far as its words and the conversation show. */
    public static final class Reading {
        public final String raw;
        public Kind kind = Kind.STATEMENT;
        /** "obi", "ivie", "both", or null when not said. */
        public String addressee;
        /** The whole request is negated ("don't …", "do not …", "never …"). */
        public boolean negated;
        /** The sentence as said, with references and a correction resolved (lower case, contractions expanded). */
        public String resolved;
        /** True when a reference or a correction changed what the sentence asks (then {@link #resolved} should be answered). */
        public boolean rewritten;
        /** The core of the request without fillers or the persona's name. */
        public String core;
        /** Separate requests in one sentence, in order (one entry when there is just one). */
        public final List<String> parts = new ArrayList<String>();
        /** What the sentence is about (a few content words), or null. */
        public String topic;
        /** A question to ask back instead of answering (incomplete sentence, unresolved reference), or null. */
        public String clarify;

        Reading(String raw) {
            this.raw = raw;
        }

        /** A short line for the live activity panel. */
        public String summary() {
            StringBuilder b = new StringBuilder(kind.name().toLowerCase(Locale.US).replace('_', ' '));
            if (negated) {
                b.append(", negated");
            }
            if (addressee != null) {
                b.append(" for ").append(addressee);
            }
            if (parts.size() > 1) {
                b.append(", ").append(parts.size()).append(" requests");
            }
            if (topic != null) {
                b.append(" · ").append(topic);
            }
            if (clarify != null) {
                b.append(" · asking back");
            }
            return b.toString();
        }
    }

    private String lastTopic;
    private String lastCore;

    /** Examines one utterance in the context of the previous ones. */
    public Reading examine(String text) {
        Reading r = new Reading(text == null ? "" : text);
        String t = expand(r.raw.toLowerCase(Locale.US).trim());
        boolean questionMark = t.endsWith("?");
        t = t.replaceAll("[\"“”]", "").replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        // who it is for: a name at the start or end ("ivie, …", "…, obi"), "both of you", "you two"
        String[] voc = vocative(t);
        r.addressee = voc[0];
        t = voc[1];
        // never mind / corrections
        if (t.matches("^(never ?mind|forget it|forget that|nothing|cancel that|ignore that)$")) {
            r.kind = Kind.NEVER_MIND;
            r.resolved = r.core = t;
            r.parts.add(t);
            return r;
        }
        String corrected = correction(t);
        if (corrected != null) {
            r.kind = Kind.CORRECTION;
            t = corrected;
        }
        String core = strip(t);
        // a negated request: "do not open telegram", "never send that" (but not "do not you think", "i do not know")
        if (core.matches("^(do not|never|please do not|stop) .+") && !core.matches("^do not you .*")
                && !core.matches("^stop (it|that|talking|speaking|listening)$")) {
            r.negated = core.startsWith("do not") || core.startsWith("never") || core.startsWith("please do not");
        }
        // what it refers to: the last topic (only when this sentence names nothing else)
        String resolved = resolveReferences(t);
        String coreResolved = resolveReferences(core);
        if (resolved == null || coreResolved == null) {
            r.clarify = "What do you mean by \"" + firstReference(core) + "\"? Tell me what you're referring to.";
            resolved = t;
            coreResolved = core;
        }
        r.rewritten = r.kind == Kind.CORRECTION || !resolved.equals(t);
        r.resolved = resolved;
        r.core = coreResolved;
        if (r.kind != Kind.CORRECTION) {
            r.kind = kindOf(r.core, questionMark);
        }
        // an incomplete request: "can you", "tell me", "what about"
        if (r.clarify == null && r.core.matches("^(can you|could you|would you|will you|tell me|show me|what about|how about|i want|i need|please)?$")) {
            r.clarify = r.core.isEmpty() ? "I'm listening. What would you like?" : "Go on, finish the sentence and I'll do it.";
        }
        // several requests in one sentence
        for (String part : split(r.core)) {
            r.parts.add(part);
        }
        if (r.parts.isEmpty()) {
            r.parts.add(r.core);
        }
        r.topic = topicOf(r.core);
        if (r.topic != null) {
            lastTopic = r.topic;
        }
        if (r.kind == Kind.QUESTION || r.kind == Kind.REQUEST || r.kind == Kind.CORRECTION) {
            lastCore = r.core;
        }
        return r;
    }

    /** What was last talked about (for tests and the brain). */
    public String lastTopic() {
        return lastTopic;
    }

    // ------------------------------------------------------------ steps

    private static final String[][] CONTRACTIONS = {
            {"won't", "will not"}, {"can't", "cannot"}, {"shan't", "shall not"}, {"let's", "let us"}, {"i'm", "i am"},
            {"what's", "what is"}, {"who's", "who is"}, {"where's", "where is"}, {"when's", "when is"}, {"how's", "how is"},
            {"that's", "that is"}, {"there's", "there is"}, {"it's", "it is"}, {"he's", "he is"}, {"she's", "she is"},
            {"n't", " not"}, {"'re", " are"}, {"'ll", " will"}, {"'ve", " have"}, {"'d", " would"},
    };

    /** Contractions written out, curly apostrophes straightened ("don't" → "do not"). */
    static String expand(String t) {
        t = t.replace('’', '\'').replace('‘', '\'');
        for (String[] c : CONTRACTIONS) {
            t = t.replace(c[0], c[1]);
        }
        return t.replace("cannot", "can not").replace("can not", "cannot");
    }

    private static final Set<String> NAMES = new HashSet<String>(Arrays.asList("obi", "ivie", "evie", "ivy"));

    /** {addressee or null, the sentence without the vocative}. */
    static String[] vocative(String t) {
        String who = null;
        String s = t;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(hey |hi |ok |okay )?(obi|ivie|evie|ivy)\\b[,:]?\\s*(.*)$").matcher(s);
        if (m.matches() && !m.group(3).isEmpty()) {
            who = m.group(2);
            s = m.group(3);
        } else {
            m = java.util.regex.Pattern.compile("^(.*?)[,]?\\s+(obi|ivie|evie|ivy)$").matcher(s);
            if (m.matches() && !m.group(1).isEmpty() && !m.group(1).matches(".*\\b(ask|tell|call|is|was|like|switch to|to|and|with)$")) {
                who = m.group(2);
                s = m.group(1);
            }
        }
        if (who == null && s.matches("^(both of you|you both|you two|both)\\b.*")) {
            who = "both";
        }
        if (who != null && !"both".equals(who)) {
            who = "obi".equals(who) ? "obi" : "ivie";
        }
        return new String[]{who, s.trim()};
    }

    /** A correction rewritten as the corrected request, or null. */
    private String correction(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(no|nope|not that|wrong|actually|sorry)[,]?\\s+(i meant|i mean|i said|i was asking about|i asked about|meant)\\s+(.+)$")
                .matcher(t);
        if (!m.matches()) {
            m = java.util.regex.Pattern.compile("^(i meant|i mean)\\s+(.+)$").matcher(t);
            if (!m.matches() || lastCore == null) {
                return null;
            }
            return replaceObject(lastCore, m.group(2));
        }
        String fix = m.group(3);
        if (lastCore == null || startsLikeRequest(fix)) {
            return fix; // a whole new request
        }
        return replaceObject(lastCore, fix);
    }

    /** The last request with its object replaced: "what is a bank" + "the river bank" → "what is the river bank". */
    private static String replaceObject(String last, String object) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^((?:what|who|where|when|how|why) (?:is|are|was|were|does|do|did)|define|explain|tell me about|show me|"
                        + "open|play|draw|search for|find|translate|spell|meaning of|what does) .+$")
                .matcher(last);
        if (m.matches()) {
            return m.group(1) + " " + object;
        }
        return object;
    }

    private static final String[] LEAD_FILLERS = {
            "please", "kindly", "hey", "hi", "okay", "ok", "so", "um", "uh", "erm", "well", "alright", "right", "now",
            "can you", "could you", "would you", "will you", "would you mind", "do you mind",
            "i want you to", "i need you to", "i would like you to", "i would love you to", "go ahead and", "just",
            "help me", "can you help me", "i was wondering", "i wonder", "do you know",
    };

    /** The core request: leading fillers and politeness and trailing "please" / "for me" removed. */
    static String strip(String t) {
        String s = t;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String f : LEAD_FILLERS) {
                if (s.equals(f)) {
                    break;
                }
                if (s.startsWith(f + " ")) {
                    String rest = s.substring(f.length() + 1).trim();
                    // keep "can you hear me" / "do you know me": the filler is the question itself when only a pronoun follows
                    if (rest.matches("^(me|us|it|that|this|him|her|them)$") || f.matches("can you|could you|do you know")
                            && rest.matches("^(hear|see|understand|speak|talk|read|feel|think|remember|learn|sing)( \\w+)?$")) {
                        continue;
                    }
                    s = rest;
                    changed = true;
                    break;
                }
            }
            if (s.startsWith(", ")) {
                s = s.substring(2);
                changed = true;
            }
        }
        s = s.replaceAll("[,]?\\s+(please|for me|thanks|thank you)$", "").replaceAll("^(to|if|whether) ", "").trim();
        // "tell me what x is" → "what is x"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:tell me |say |explain )?(what|who|where) (.+) (is|are)$").matcher(s);
        if (m.matches()) {
            s = m.group(1) + " " + m.group(3) + " " + m.group(2);
        }
        return s;
    }

    private static final Set<String> REFERENCES = new HashSet<String>(Arrays.asList("it", "that", "this", "them", "those", "these"));

    /** The sentence with "it" / "that" / "this" / "them" replaced by the last topic; null when one can't be resolved. */
    private String resolveReferences(String t) {
        String[] w = t.split(" ");
        boolean has = false;
        for (int i = 0; i < w.length; i++) {
            if (REFERENCES.contains(w[i]) && isObjectReference(w, i)) {
                has = true;
            }
        }
        if (!has) {
            return t;
        }
        if (lastTopic == null) {
            // a reference with nothing before it: only a question when the sentence is just a verb and the reference
            return w.length <= 3 && startsLikeRequest(t) ? null : t;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            b.append(i == 0 ? "" : " ").append(REFERENCES.contains(w[i]) && isObjectReference(w, i) ? lastTopic : w[i]);
        }
        return b.toString();
    }

    /** "explain it", "tell me more about that": the reference is the object, not part of "is that so" or "that is …". */
    private static boolean isObjectReference(String[] w, int i) {
        if (i == 0) {
            return false; // "that is right", "this is great": a demonstrative at the start
        }
        String before = w[i - 1];
        if (i + 1 < w.length && w[i + 1].matches("is|was|are|were|will|would|means|sounds|makes|works")) {
            return false; // "what is that is" … the subject of a clause
        }
        return before.matches("explain|define|spell|repeat|translate|about|of|on|with|for|open|play|draw|show|send|copy|share|"
                + "describe|summarise|summarize|say|do|use|try|find|save|delete|remove|change|is|was|mean|meant|in");
    }

    private static String firstReference(String t) {
        for (String w : t.split(" ")) {
            if (REFERENCES.contains(w)) {
                return w;
            }
        }
        return t;
    }

    private static final Set<String> QUESTION_WORDS = new HashSet<String>(Arrays.asList(
            "what", "who", "whom", "whose", "where", "when", "why", "how", "which", "is", "are", "was", "were", "do", "does",
            "did", "can", "could", "would", "will", "should", "shall", "may", "might", "have", "has", "am"));
    private static final Set<String> REQUEST_VERBS = new HashSet<String>(Arrays.asList(
            "tell", "show", "explain", "define", "open", "play", "draw", "make", "set", "turn", "switch", "start", "stop",
            "send", "share", "copy", "find", "search", "look", "give", "say", "read", "write", "remind", "call", "help",
            "teach", "translate", "spell", "calculate", "convert", "count", "describe", "list", "create", "build", "change",
            "put", "take", "let", "go", "come", "wave", "dance", "walk", "stand", "sit", "smile", "remember", "forget",
            "save", "delete", "remove", "add", "check", "compare", "summarise", "summarize", "repeat", "sing", "do", "use",
            "try", "pause", "resume", "debate", "discuss", "ask", "grow", "shave", "give"));

    private static Kind kindOf(String core, boolean questionMark) {
        String first = core.split(" ")[0];
        if (core.matches("^(hi|hello|hey|good (morning|afternoon|evening)|howdy|yo|greetings)\\b.*") && core.split(" ").length <= 4) {
            return Kind.GREETING;
        }
        if (core.matches("^(thanks|thank you|cheers|much appreciated|great job|well done)\\b.*")) {
            return Kind.THANKS;
        }
        if (questionMark || QUESTION_WORDS.contains(first) && !core.matches("^(do not|do it|do that|do the|do a|do some) ?.*")) {
            return Kind.QUESTION;
        }
        if (REQUEST_VERBS.contains(first) || core.startsWith("do not ") || core.startsWith("never ") || core.startsWith("please ")) {
            return Kind.REQUEST;
        }
        return Kind.STATEMENT;
    }

    private static boolean startsLikeRequest(String t) {
        String first = t.split(" ")[0];
        return REQUEST_VERBS.contains(first) || QUESTION_WORDS.contains(first);
    }

    /**
     * Separate requests: split on "and then", "then", "; " and "also", and on "and" when both sides start with a
     * request verb ("tell me a joke and set a timer"), not inside a phrase ("salt and pepper").
     */
    static List<String> split(String core) {
        List<String> out = new ArrayList<String>();
        for (String piece : core.split("\\s*(;|, and then|, then| and then| then| also,| after that)\\s+")) {
            String[] w = piece.split(" and ");
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < w.length; i++) {
                if (i > 0 && startsLikeRequest(w[i]) && cur.length() > 0 && startsLikeRequest(cur.toString())) {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                } else if (i > 0) {
                    cur.append(" and ");
                }
                cur.append(w[i]);
            }
            if (cur.toString().trim().length() > 0) {
                out.add(cur.toString().trim());
            }
        }
        return out;
    }

    private static final Set<String> STOP = new HashSet<String>(Arrays.asList(
            "a", "an", "the", "is", "are", "was", "were", "be", "to", "of", "in", "on", "at", "for", "with", "about", "me",
            "my", "you", "your", "i", "it", "that", "this", "what", "who", "where", "when", "why", "how", "which", "do",
            "does", "did", "can", "could", "would", "will", "should", "tell", "show", "explain", "define", "please", "and",
            "or", "not", "no", "yes", "some", "more", "them", "those", "these", "there", "here", "mean", "means", "meaning",
            "word", "give", "say", "let", "us", "we", "our", "so", "very", "really", "just", "now", "then", "am", "have",
            "has", "had", "know", "think", "like", "want", "need", "get", "make", "go", "his", "her", "him", "she", "he",
            "their", "they", "its", "by", "from", "as", "if", "but", "up", "out", "again", "also", "too", "much", "many",
            "obi", "ivie", "both", "hi", "hello", "hey", "thanks", "thank", "okay", "ok", "all", "any", "one", "something"));

    /** A few content words naming what the sentence is about ("the river bank", "black holes"), or null. */
    static String topicOf(String core) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(?:what|who|where) (?:is|are|was|were) (?:a |an |the )?(.+)$|^(?:define|explain|tell me about|describe|meaning of|what does) (?:a |an |the )?(.+?)(?: mean)?$")
                .matcher(core);
        if (m.matches()) {
            String obj = m.group(1) != null ? m.group(1) : m.group(2);
            if (obj != null && !REFERENCES.contains(obj) && obj.split(" ").length <= 5) {
                return obj;
            }
        }
        List<String> content = new ArrayList<String>();
        for (String w : core.split(" ")) {
            if (w.length() > 2 && !STOP.contains(w) && !REQUEST_VERBS.contains(w) && !NAMES.contains(w)) {
                content.add(w);
            }
        }
        if (content.isEmpty()) {
            return null;
        }
        return String.join(" ", content.subList(Math.max(0, content.size() - 3), content.size()));
    }
}
