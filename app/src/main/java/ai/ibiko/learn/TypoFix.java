package ai.ibiko.learn;

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
 * Typos in what the person typed (or the speech recogniser misheard), fixed before a brain reads it: "recieve" →
 * "receive", "teh" → "the". Careful above all, since a wrong fix is worse than none:
 * <ul>
 * <li>a word is only changed to a dictionary word one letter away (a letter missing, extra, wrong or two swapped), and
 * only when one such word is clearly the commonest;</li>
 * <li>a word is known in any of its forms ("pushed", "children", "went"), so a word that is merely inflected is never
 * "fixed";</li>
 * <li>short words, names, capitals, contractions, numbers, links, paths and anything that looks like code are left alone,
 * and so are Pidgin and Yoruba words and the person's own words ({@link #own}): a word they keep using is theirs.</li>
 * </ul>
 * Pure Java: the dictionary is behind {@link Dict}.
 */
public final class TypoFix {
    private TypoFix() {
    }

    /** A dictionary: whether a word (a base form) is in it, and how common it is (0 commonest; -1 not known). */
    public interface Dict {
        boolean has(String word);

        int commonness(String word);
    }

    /** The fixes made: "recieve" → "receive". */
    public static final class Result {
        public final String text;
        public final List<String[]> fixes;

        Result(String text, List<String[]> fixes) {
            this.text = text;
            this.fixes = fixes;
        }

        /** "Read “recieve” as “receive”." for the person, or null when nothing was changed. */
        public String said() {
            if (fixes.isEmpty()) {
                return null;
            }
            List<String> parts = new ArrayList<String>();
            for (String[] f : fixes) {
                parts.add("“" + f[0] + "” as “" + f[1] + "”");
            }
            return "Read " + String.join(", ", parts) + ".";
        }
    }

    /** Words never changed: Pidgin, Yoruba, Igbo and Hausa words heard in Nigerian chat, and everyday tech and chat words. */
    static final Set<String> KEEP = new HashSet<String>(Arrays.asList(
            "wahala", "abeg", "dey", "wey", "sha", "oya", "biko", "abi", "abeg", "jare", "jor", "shey", "sabi", "pikin",
            "oga", "madam", "wetin", "nawa", "chai", "haba", "ehen", "una", "dem", "dis", "dat", "wan", "fit", "go", "na",
            "o", "ooo", "sef", "kuku", "gist", "japa", "naija", "danfo", "okada", "kobo", "naira", "jollof", "suya", "garri",
            "ibiko", "obi", "ivie", "lagos", "abuja", "yoruba", "igbo", "hausa", "pidgin",
            "app", "apps", "repo", "repos", "github", "gitlab", "login", "logout", "signup", "username", "email", "emails",
            "wifi", "online", "offline", "inbox", "selfie", "emoji", "emojis", "hashtag", "website", "webpage", "blog",
            "android", "iphone", "ios", "laptop", "smartphone", "chatbot", "chatgpt", "claude", "gemini", "qwen", "llama",
            "javascript", "typescript", "kotlin", "python", "java", "golang", "rust", "json", "yaml", "html", "css", "api",
            "apis", "sdk", "cli", "ui", "ux", "backend", "frontend", "fullstack", "devops", "localhost", "npm", "pip",
            "gradle", "dockerfile", "docker", "kubernetes", "linux", "ubuntu", "termux", "readme", "changelog", "config",
            "configs", "async", "await", "boolean", "bool", "int", "enum", "struct", "regex", "url", "urls", "dropdown",
            "checkbox", "toggle", "toggles", "lol", "lmao", "omg", "btw", "idk", "imo", "pls", "plz", "thx", "ok", "okay",
            "gonna", "wanna", "gotta", "kinda", "sorta", "yeah", "yep", "nope", "hmm", "uh", "um",
            "cannot", "whom", "whose", "onto", "into", "unto", "towards", "afterwards", "anyway", "anyways", "etc", "versus",
            "whilst", "amongst", "alright", "maybe", "ourselves", "themselves", "yourselves", "itself", "versa", "beside",
            "besides", "despite", "toward", "amid", "amidst", "throughout", "underneath", "beneath", "inside", "outside",
            "alongside", "upon", "within", "without", "whereas", "thereby", "therefore", "however", "although", "unless",
            "javac", "kotlinc", "vocab", "info", "admin", "auth", "repo", "dev", "devs", "prod", "env", "async", "sync",
            "params", "args", "config", "temp", "tmp", "init", "impl", "util", "utils", "lib", "libs", "src", "deps", "todo"));

    /** Common forms the suffix rules don't reach, to their base. */
    static final Map<String, String> IRREGULAR = new HashMap<String, String>();

    static {
        String[] p = {"went", "go", "gone", "go", "goes", "go", "did", "do", "done", "do", "does", "do", "said", "say",
                "got", "get", "gotten", "get", "made", "make", "came", "come", "saw", "see", "seen", "see", "knew", "know",
                "known", "know", "took", "take", "taken", "take", "gave", "give", "given", "give", "told", "tell", "thought",
                "think", "found", "find", "felt", "feel", "left", "leave", "kept", "keep", "meant", "mean", "sent", "send",
                "spent", "spend", "built", "build", "bought", "buy", "brought", "bring", "caught", "catch", "taught", "teach",
                "wrote", "write", "written", "write", "ran", "run", "began", "begin", "begun", "begin", "broke", "break",
                "broken", "break", "chose", "choose", "chosen", "choose", "spoke", "speak", "spoken", "speak", "stood", "stand",
                "understood", "understand", "heard", "hear", "held", "hold", "lost", "lose", "met", "meet", "paid", "pay",
                "sold", "sell", "slept", "sleep", "won", "win", "wore", "wear", "worn", "wear", "children", "child", "men",
                "man", "women", "woman", "people", "person", "feet", "foot", "teeth", "tooth", "mice", "mouse", "better",
                "good", "best", "good", "worse", "bad", "worst", "bad", "has", "have", "had", "have", "was", "be", "were", "be",
                "been", "be", "am", "be", "is", "be", "are", "be", "its", "it", "data", "datum", "media", "medium",
                "froze", "freeze", "frozen", "freeze", "drove", "drive", "driven", "drive", "rode", "ride", "ridden", "ride",
                "rose", "rise", "risen", "rise", "fell", "fall", "fallen", "fall", "flew", "fly", "flown", "fly", "drew", "draw",
                "drawn", "draw", "grew", "grow", "grown", "grow", "threw", "throw", "thrown", "throw", "blew", "blow", "blown",
                "blow", "drank", "drink", "drunk", "drink", "sang", "sing", "sung", "sing", "swam", "swim", "swum", "swim",
                "ate", "eat", "eaten", "eat", "forgot", "forget", "forgotten", "forget", "forgave", "forgive", "forgiven",
                "forgive", "hid", "hide", "hidden", "hide", "bit", "bite", "bitten", "bite", "shook", "shake", "shaken", "shake",
                "stole", "steal", "stolen", "steal", "woke", "wake", "woken", "wake", "fought", "fight", "sought", "seek",
                "led", "lead", "fed", "feed", "fled", "flee", "dealt", "deal", "dug", "dig", "hung", "hang", "lit", "light",
                "slid", "slide", "struck", "strike", "swore", "swear", "sworn", "swear", "tore", "tear", "torn", "tear",
                "bent", "bend", "lent", "lend", "built", "build", "burnt", "burn", "learnt", "learn", "spelt", "spell",
                "lay", "lie", "lain", "lie", "laid", "lay", "shone", "shine", "shot", "shoot", "spun", "spin", "stuck", "stick",
                "stung", "sting", "swung", "swing", "wept", "weep", "wound", "wind", "undid", "undo", "undone", "undo",
                "rewrote", "rewrite", "rewritten", "rewrite", "mice", "mouse", "geese", "goose", "lives", "life", "wives",
                "wife", "knives", "knife", "leaves", "leaf", "halves", "half", "analyses", "analysis", "criteria", "criterion",
                "became", "become", "become", "become", "overcame", "overcome", "understood", "understand", "withdrew",
                "withdraw", "withdrawn", "withdraw", "beat", "beat", "beaten", "beat", "bore", "bear", "borne", "bear"};
        for (int i = 0; i + 1 < p.length; i += 2) {
            IRREGULAR.put(p[i], p[i + 1]);
        }
    }

    /** Whether a word is known in this form or any form a suffix makes ("pushed", "cars", "running", "studies"). */
    public static boolean known(String w, Dict d) {
        if (d.has(w) || KEEP.contains(w)) {
            return true;
        }
        String base = IRREGULAR.get(w);
        if (base != null && d.has(base)) {
            return true;
        }
        for (String b : bases(w)) {
            if (d.has(b)) {
                return true;
            }
        }
        // a known word behind a common prefix ("unset", "rebuild", "preload", "nonstop", "misread", "overwrite")
        for (String pre : PREFIXES) {
            if (w.length() > pre.length() + 2 && w.startsWith(pre)) {
                String rest = w.substring(pre.length());
                if (d.has(rest) || IRREGULAR.containsKey(rest)) {
                    return true;
                }
                for (String b : bases(rest)) {
                    if (d.has(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static final String[] PREFIXES = {"un", "re", "pre", "non", "dis", "mis", "over", "under", "sub", "inter", "out", "co", "de",
            "anti", "auto", "multi", "self", "super", "micro", "semi"};

    /** The base forms a word may be ("studies" → "study", "stud"; "running" → "run", "runn", "running"). */
    static List<String> bases(String w) {
        List<String> out = new ArrayList<String>();
        String[][] ends = {{"ies", "y"}, {"ied", "y"}, {"ier", "y"}, {"iest", "y"}, {"es", ""}, {"s", ""}, {"ed", ""},
                {"ed", "e"}, {"d", ""}, {"ing", ""}, {"ing", "e"}, {"er", ""}, {"er", "e"}, {"est", ""}, {"est", "e"},
                {"ly", ""}, {"ily", "y"}, {"ness", ""}, {"ment", ""}, {"able", ""}, {"able", "e"}, {"ful", ""}, {"less", ""}};
        for (String[] e : ends) {
            if (w.length() > e[0].length() + 2 && w.endsWith(e[0])) {
                String b = w.substring(0, w.length() - e[0].length()) + e[1];
                out.add(b);
                int n = b.length();
                if (e[1].isEmpty() && n >= 3 && b.charAt(n - 1) == b.charAt(n - 2)) {
                    out.add(b.substring(0, n - 1)); // "running" → "runn" → "run"
                }
            }
        }
        return out;
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z][a-z]*(?:'[a-z]+)?");
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

    /** Text that is code, a command, a link or a path: left exactly as it is. */
    static boolean leaveAlone(String text) {
        String t = text.trim();
        return t.contains("```") || t.contains("`") || t.matches("(?s).*[{};=<>]{2,}.*") || t.matches("(?s).*\\n\\s{2,}\\S.*")
                || t.matches("(?is)^(git|ls|cd|cat|rm|mv|cp|npm|pip|python3?|node|gradle|./\\S+|sudo|apt|apk|pkg|curl|wget|make|bash|sh)\\b.*")
                || t.startsWith("§") || t.startsWith("/");
    }

    /** The text with its clear typos fixed. {@code own}: the person's own words, never changed. */
    public static Result fix(String text, Dict d, Set<String> own) {
        List<String[]> fixes = new ArrayList<String[]>();
        if (text == null || text.isEmpty() || leaveAlone(text)) {
            return new Result(text, fixes);
        }
        StringBuilder out = new StringBuilder();
        Matcher m = WORD.matcher(text);
        int last = 0;
        while (m.find()) {
            String w = m.group();
            String fixed = fixWord(text, m.start(), m.end(), w, d, own);
            out.append(text, last, m.start()).append(fixed == null ? w : fixed);
            if (fixed != null) {
                fixes.add(new String[]{w, fixed});
            }
            last = m.end();
        }
        out.append(text.substring(last));
        return new Result(out.toString(), fixes);
    }

    /** The fix for one word, or null to leave it. */
    static String fixWord(String text, int start, int end, String w, Dict d, Set<String> own) {
        String low = w.toLowerCase(Locale.ROOT);
        if (low.length() <= 4 || w.contains("'") || own != null && own.contains(low)) {
            return null;
        }
        // part of a link, an address, a path, a handle, a word with digits or underscores
        char before = start > 0 ? text.charAt(start - 1) : ' ', after = end < text.length() ? text.charAt(end) : ' ';
        if ("@#/\\._-:".indexOf(before) >= 0 || "@/\\_-:".indexOf(after) >= 0 || Character.isDigit(after)
                || (after == '.' && end + 1 < text.length() && Character.isLetter(text.charAt(end + 1)))) {
            return null;
        }
        // a capital not at a sentence's start is a name; capitals inside the word an acronym or a code name
        boolean capital = Character.isUpperCase(w.charAt(0));
        if (capital && !sentenceStart(text, start) || !w.substring(1).equals(w.substring(1).toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (known(low, d)) {
            return null;
        }
        // the dictionary words one slip away, weighed by how common the word is and how likely the slip (two letters
        // swapped or a neighbouring key are everyday slips; any other letter much less so)
        Map<String, Double> found = new HashMap<String, Double>();
        for (Map.Entry<String, Double> e : edits(low).entrySet()) {
            String c = e.getKey();
            if (c.length() >= 3 && e.getValue() <= MOST_UNLIKELY && d.has(c)) {
                int r = rankOf(d.commonness(c));
                if (r <= COMMON) { // only a fix to an everyday word: a rare one is more likely a word the dictionary lacks
                    found.put(c, (r + 200.0) * e.getValue());
                }
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, Double>> ranked = new ArrayList<Map.Entry<String, Double>>(found.entrySet());
        ranked.sort((x, y) -> Double.compare(x.getValue(), y.getValue()));
        if (ranked.size() > 1 && ranked.get(1).getValue() < ranked.get(0).getValue() * CLEAR) {
            return null; // two near-equal fixes: not sure which was meant
        }
        String to = ranked.get(0).getKey();
        return capital ? Character.toUpperCase(to.charAt(0)) + to.substring(1) : to;
    }

    /** How common a fix must be (its place among words by use). */
    static final int COMMON = 25_000;
    /** The unlikeliest slip still fixed: any other letter (4) is; a slip at the first letter mostly isn't. */
    static final double MOST_UNLIKELY = 4;
    /** How much likelier the best fix must be than the next. */
    static final double CLEAR = 3;

    private static int rankOf(int commonness) {
        return commonness < 0 ? Integer.MAX_VALUE / 8 : commonness;
    }

    private static boolean sentenceStart(String text, int start) {
        for (int i = start - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '“' || c == '(') {
                continue;
            }
            return c == '.' || c == '!' || c == '?' || c == '\n';
        }
        return true;
    }

    /** Keys next to each other on a phone's keyboard (QWERTY): a slip to one of them is common. */
    static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

    static boolean neighbours(char a, char b) {
        int ra = -1, ca = -1, rb = -1, cb = -1;
        for (int r = 0; r < ROWS.length; r++) {
            if (ROWS[r].indexOf(a) >= 0) {
                ra = r;
                ca = ROWS[r].indexOf(a);
            }
            if (ROWS[r].indexOf(b) >= 0) {
                rb = r;
                cb = ROWS[r].indexOf(b);
            }
        }
        return ra >= 0 && rb >= 0 && Math.abs(ra - rb) <= 1 && Math.abs(ca - cb) <= 1;
    }

    /**
     * Every string one slip away, with how unlikely that slip is (lower is likelier): two letters swapped 1, a letter
     * doubled or a double made single 1, a neighbouring key 1.5, a letter missing or extra 2, any other letter 4.
     */
    static Map<String, Double> edits(String w) {
        Map<String, Double> out = new HashMap<String, Double>();
        for (int i = 0; i <= w.length(); i++) {
            String a = w.substring(0, i), b = w.substring(i);
            double first = i == 0 ? 4 : 1; // the first letter is rarely the slip ("kawaii" is not "hawaii", "xterm" not "term")
            if (!b.isEmpty()) {
                boolean undouble = i > 0 && w.charAt(i - 1) == b.charAt(0) || b.length() > 1 && b.charAt(1) == b.charAt(0);
                cheapest(out, a + b.substring(1), (undouble ? 1 : 2) * first);
            }
            if (b.length() > 1 && b.charAt(0) != b.charAt(1)) {
                cheapest(out, a + b.charAt(1) + b.charAt(0) + b.substring(2), 1);
            }
            for (int k = 0; k < LETTERS.length(); k++) {
                char c = LETTERS.charAt(k);
                if (!b.isEmpty() && b.charAt(0) != c) {
                    cheapest(out, a + c + b.substring(1), (neighbours(b.charAt(0), c) ? 1.5 : 4) * first);
                }
                boolean doubled = i > 0 && w.charAt(i - 1) == c || !b.isEmpty() && b.charAt(0) == c;
                cheapest(out, a + c + b, (doubled ? 1 : 2) * first);
            }
        }
        out.remove(w);
        return out;
    }

    private static void cheapest(Map<String, Double> out, String s, double cost) {
        Double had = out.get(s);
        if (had == null || cost < had) {
            out.put(s, cost);
        }
    }

    /** The dictionary on the device: {@link WordBook} for what a word is, {@link WordRank} for how common. */
    public static final Dict DEVICE = new Dict() {
        @Override
        public boolean has(String word) {
            return WordBook.ready() && WordBook.id(word) > 0;
        }

        @Override
        public int commonness(String word) {
            int id = WordBook.id(word);
            return id <= 0 || !WordRank.ready() ? -1 : WordRank.code(id);
        }
    };
}
