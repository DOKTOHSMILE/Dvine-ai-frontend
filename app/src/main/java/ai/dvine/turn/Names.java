package ai.dvine.turn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The personas' names as speech recognition hears them: "Evie", "Ivy", "Eevee" are Ivie; "Obie", "Obee" (and "obey"
 * where it can only be a name) are Obi. Applied to what was heard before anything reads it. Pure Java.
 */
public final class Names {
    private Names() {
    }

    private static final Pattern IVIE = Pattern.compile("\\b(eva|evie|evey|evee|eevee|eevie|ivee|ivey|ivy|ivi|iffy|evi)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IBIKO = Pattern.compile("\\b(ibiko|ibeko|ebiko|ebeko|ibico|ibeco|ibeeko|ebeeko|i\\.?\\s?b\\.?\\s?ko|eboko|ibuko|abiko)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OBI = Pattern.compile("\\b(obie|obee|oby|obbie|obi's|o\\.b\\.)\\b", Pattern.CASE_INSENSITIVE);
    /** "obey" only where a name is: at the start, after a greeting or a switch, or alone at the end after a comma. */
    private static final Pattern OBEY = Pattern.compile(
            "(^|\\b(?:switch to|switch back to|talk to|speak to|speak with|hey|hi|hello|ask|tell|with|call|bring|and)\\s+)obey\\b|,\\s*obey$",
            Pattern.CASE_INSENSITIVE);

    public static String normalize(String heard) {
        if (heard == null) {
            return null;
        }
        String s = heard;
        synchronized (LEARNED) {
            for (Map.Entry<String, String> e : LEARNED.entrySet()) {
                s = s.replaceAll("(?i)\\b" + Pattern.quote(e.getKey()) + "\\b", e.getValue());
            }
        }
        s = IBIKO.matcher(s).replaceAll("Ibiko");
        s = IVIE.matcher(s).replaceAll("Ivie");
        s = OBI.matcher(s).replaceAll("Obi");
        java.util.regex.Matcher m = OBEY.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String lead = m.group(1) != null ? m.group(1) : ", ";
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(m.group(1) != null ? lead + "Obi" : ", Obi"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern SWITCH = Pattern.compile(
            "^(?:(?:can you |could you |please |let me |i want to |i'd like to |i would like to )?"
                    + "(?:switch(?: back| over)? to|change to|go to|talk to|speak to|speak with|chat with|communicate with|bring(?: in| out| me)?|"
                    + "call|get|show me|i want|give me|where is|where's)\\s+)(ivie|obi|ibiko)\\b(.*)$");

    /**
     * The persona a request asks to bring forward ("switch to Ivie", "communicate with Ivie your other half", "let me
     * talk to Obi"), or null. A request with more to say ("talk to Ivie about the weather") isn't only a switch.
     */
    public static String switchTarget(String said) {
        if (said == null) {
            return null;
        }
        String t = said.toLowerCase(Locale.US).replaceAll("[.!?,]+", " ").replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher m = SWITCH.matcher(t);
        if (!m.matches()) {
            return null;
        }
        String rest = m.group(2).trim();
        if (rest.split(" ").length > 4 || rest.matches("^(about|and|to|that|if|what|how|why|when) .*")) {
            return null;
        }
        return m.group(1);
    }

    // ------------------------------------------------------------ sound-alikes: ask, don't guess

    /** What was heard, with the clear names fixed and, when a word only sounds like a name, the guess to offer. */
    public static final class Hearing {
        /** The words with the clear spellings fixed ("Evie" → "Ivie"). */
        public final String text;
        /** The word that may be a name ("Eva"), or null when nothing is unsure. */
        public final String word;
        /** The name it sounds like ("Ivie"), or null. */
        public final String guess;
        /** {@link #text} with the word replaced by the guess, or null. */
        public final String suggested;

        Hearing(String text, String word, String guess, String suggested) {
            this.text = text;
            this.word = word;
            this.guess = guess;
            this.suggested = suggested;
        }

        /** "Did you mean Ivie? I heard \"Eva\"." */
        public String question() {
            return word == null ? null : "Did you mean " + guess + "? I heard \"" + word + "\".";
        }
    }

    /** Spellings the user confirmed as a name ("eva" → "Ivie"), and words they said weren't one. */
    private static final Map<String, String> LEARNED = new HashMap<String, String>();
    private static final Set<String> NOT_NAMES = new HashSet<String>();

    /** Real words close in sound that are almost never a name; never asked about. */
    private static final Set<String> COMMON = new HashSet<String>(java.util.Arrays.asList(
            "obey", "evil", "even", "ever", "eves", "ivy", "ape", "eva", "apex", "obese", "oboe", "ova", "avid", "ebay",
            "ovi", "abide", "obit", "orbit", "okay", "obs", "aye", "eye", "ive", "ivan", "ibis", "obvi"));

    /** Words after which a name sits: a greeting, a switch, "and", "thanks". */
    private static final Pattern BEFORE_NAME = Pattern.compile(
            ".*\\b(?:switch to|switch back to|talk to|speak to|speak with|chat with|communicate with|hey|hi|hello|ask|tell|"
                    + "with|call|bring|and|thanks|thank you|bye|okay|ok)\\s*$");
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z']{1,7}\\b");

    /**
     * Reads what was heard: the clear spellings fixed as {@link #normalize} does; a word in a name's place (the
     * start, after a greeting or a switch, after a comma) that only sounds like one ("Eva", "Ava", "Abi", "Ovie")
     * comes back as a guess to confirm, not a silent change.
     */
    public static Hearing hear(String said) {
        String text = normalize(said);
        if (text == null) {
            return new Hearing(null, null, null, null);
        }
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            String before = text.substring(0, m.start()).toLowerCase(Locale.US);
            boolean place = before.trim().isEmpty() || before.trim().endsWith(",") || BEFORE_NAME.matcher(before).matches();
            if (!place || m.start() > 0 && Character.isLetter(text.charAt(m.start() - 1))) {
                continue;
            }
            String word = m.group();
            String low = word.toLowerCase(Locale.US);
            String guess = soundsLike(low);
            if (guess == null) {
                continue;
            }
            synchronized (LEARNED) {
                if (NOT_NAMES.contains(low)) {
                    continue;
                }
            }
            String suggested = text.substring(0, m.start()) + guess + text.substring(m.end());
            if (switchTarget(suggested) != null) {
                return new Hearing(suggested, null, null, null); // "switch to Ava": clearly a name, no need to ask
            }
            return new Hearing(text, word, guess, suggested);
        }
        return new Hearing(text, null, null, null);
    }

    /** "Ivie" or "Obi" when the word sounds like one of them but isn't spelled as one; null otherwise. */
    static String soundsLike(String low) {
        if (low.equals("ivie") || low.equals("obi") || COMMON.contains(low) || low.length() < 2) {
            return null;
        }
        String key = key(low);
        if (key.equals("*V*")) {
            return "Ivie";
        }
        if (key.equals("*B*")) {
            return "Obi";
        }
        if (low.length() >= 3) {
            if (distance(low, "ivie") <= 1 || distance(low, "evie") <= 1) {
                return "Ivie";
            }
            if (distance(low, "obi") <= 1 || distance(low, "obie") <= 1) {
                return "Obi";
            }
        }
        return null;
    }

    /** A rough sound key: vowels (and y) collapse to "*", f/v to "V", b/p to "B"; anything else keeps its letter. */
    static String key(String low) {
        StringBuilder k = new StringBuilder();
        String w = low.replace("ph", "f").replace("'", "");
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            char out = "aeiouy".indexOf(c) >= 0 ? '*' : c == 'f' || c == 'v' ? 'V' : c == 'b' || c == 'p' ? 'B'
                    : Character.toUpperCase(c);
            if (k.length() == 0 || k.charAt(k.length() - 1) != out) {
                k.append(out);
            }
        }
        return k.toString();
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** The user confirmed: from now on this spelling is the name. */
    public static void learn(String word, String name) {
        synchronized (LEARNED) {
            LEARNED.put(word.toLowerCase(Locale.US), name);
            NOT_NAMES.remove(word.toLowerCase(Locale.US));
        }
    }

    /** The user said no: this word isn't a name, don't ask about it again. */
    public static void notAName(String word) {
        synchronized (LEARNED) {
            NOT_NAMES.add(word.toLowerCase(Locale.US));
            LEARNED.remove(word.toLowerCase(Locale.US));
        }
    }

    /** What was learned, for saving: "eva=Ivie;abi=Obi;!ava" ("!" marks a word that isn't a name). */
    public static String saved() {
        StringBuilder b = new StringBuilder();
        synchronized (LEARNED) {
            for (Map.Entry<String, String> e : LEARNED.entrySet()) {
                b.append(b.length() == 0 ? "" : ";").append(e.getKey()).append('=').append(e.getValue());
            }
            for (String w : NOT_NAMES) {
                b.append(b.length() == 0 ? "" : ";").append('!').append(w);
            }
        }
        return b.toString();
    }

    /** Restores {@link #saved()}. */
    public static void restore(String saved) {
        synchronized (LEARNED) {
            LEARNED.clear();
            NOT_NAMES.clear();
            if (saved == null) {
                return;
            }
            for (String item : saved.split(";")) {
                if (item.startsWith("!") && item.length() > 1) {
                    NOT_NAMES.add(item.substring(1));
                } else if (item.matches("[a-z']+=(Ivie|Obi)")) {
                    String[] kv = item.split("=");
                    LEARNED.put(kv[0], kv[1]);
                }
            }
        }
    }

    /** TRUE for a yes ("yes", "yeah, Ivie", "that's right"), FALSE for a no, null for anything else. */
    public static Boolean yesNo(String said) {
        String t = said == null ? "" : said.toLowerCase(Locale.US).replaceAll("[.!?,]", " ").replaceAll("\\s+", " ").trim();
        if (t.matches("^(yes|yeah|yea|yep|yup|ya|correct|right|exactly|sure|i did|that's right|that is right|"
                + "that's it|uh huh)( .*)?$|^(ok|okay)$")) {
            return Boolean.TRUE;
        }
        if (t.matches("^(no|nope|nah|not really|i didn't|i did not|wrong|neither)( .*)?$")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
