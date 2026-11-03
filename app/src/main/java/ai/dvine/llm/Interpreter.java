package ai.dvine.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The interpreter: a message in Nigerian Pidgin, Yoruba or French is put into English for the model (models reason
 * best in English), and the English reply is put back into the user's language. English goes straight through. The
 * same model does the translating, online (the API) or offline (the phone's model); for Pidgin the words it may not
 * know are given to it from a small glossary. Code, links and numbers are kept as they are. Pure Java.
 */
public final class Interpreter {
    /** A language the interpreter handles: its name as shown, and how the model is told to write it. */
    public enum Language {
        PIDGIN("Pidgin", "Nigerian Pidgin English, the way people in Lagos or Warri say it"),
        YORUBA("Yoruba", "Yoruba, with the proper tone marks and dots (ẹ, ọ, ṣ)"),
        FRENCH("French", "natural French");

        public final String shown;
        final String write;

        Language(String shown, String write) {
            this.shown = shown;
            this.write = write;
        }
    }

    /** The model: instructions and text in, its reply out (null when it failed). */
    public interface Brain {
        String ask(String system, String text, int maxTokens);
    }

    private Interpreter() {
    }

    /** Pidgin words that English doesn't use, and what they mean (for the model, and to spot Pidgin). */
    static final Map<String, String> PIDGIN = new LinkedHashMap<String, String>();
    /** Pidgin words that are also English words: they count only beside a sure one. */
    static final String[] PIDGIN_WEAK = {"na", "don", "go", "fit", "wan", "make", "e", "dem", "am", "una", "wey", "sef",
            "small", "abi", "o", "jare"};

    static {
        String[][] g = {
                {"wetin", "what"}, {"dey", "is / are / am (doing)"}, {"abeg", "please"}, {"wahala", "trouble, problem"},
                {"comot", "leave, go out, remove"}, {"sabi", "know, know how"}, {"oya", "come on, let's go"},
                {"shey", "is it that, right?"}, {"abi", "isn't it, or"}, {"pikin", "child"}, {"chop", "eat, spend"},
                {"waka", "walk, go about"}, {"wey", "who, which, that"}, {"una", "you (plural)"}, {"naim", "that is (what)"},
                {"na im", "that is (what)"}, {"howfar", "how are you, what's up"}, {"how far", "how are you, what's up"},
                {"dem", "they, them"}, {"kuku", "just, simply"}, {"sha", "anyway, though"},
                {"sef", "even, too"}, {"wahala dey", "there's a problem"}, {"no wahala", "no problem"},
                {"e don do", "that's enough"}, {"e no easy", "it's not easy"}, {"gist", "story, chat, news"},
                {"palava", "trouble, dispute"}, {"ginger", "motivate, excite"}, {"yarn", "talk, say"},
                {"jollof", "jollof rice"}, {"belle", "belly, stomach"}, {"ehn", "(emphasis)"}, {"abi na", "or is it"},
                {"wan", "want to"}, {"fit", "can"}, {"don", "have (done), already"}, {"go", "will"},
                {"make", "let, so that"}, {"na", "it is, is"}, {"e", "it, he, she"}, {"am", "him, her, it"},
                {"wetin dey", "what's happening"}, {"no be", "isn't, it's not"}, {"sotey", "until"},
                {"tey", "for a long time"}, {"kpatakpata", "completely"}, {"wayo", "trickery, scam"},
                {"mumu", "fool"}, {"ode", "fool"}, {"japa", "emigrate, run away"}, {"jare", "(emphasis), please"},
                {"o", "(emphasis)"}, {"dey kampe", "is fine, is strong"}, {"pesin", "person"}, {"plenty", "a lot of"},
        };
        for (String[] p : g) {
            PIDGIN.put(p[0], p[1]);
        }
    }

    /** Pidgin words that only Pidgin uses: one of these makes a message Pidgin. */
    static final String[] PIDGIN_SURE = {"wetin", "dey", "abeg", "wahala", "comot", "sabi", "oya", "shey", "pikin", "naim",
            "howfar", "kuku", "palava", "sotey", "kpatakpata", "wayo", "japa", "pesin", "una", "wey",
            "e don", "make we", "no be", "e no", "na im", "una go"};

    /** Yoruba letters with a dot below (ẹ ọ ṣ, or the combining dot): no other language here uses them. */
    static final Pattern YORUBA_LETTERS = Pattern.compile("[ẸẹỌọṢṣ̣]");
    static final String[] YORUBA_WORDS = {"bawo", "kilode", "kini", "jowo", "jọwọ", "ẹ", "ṣe", "se", "mo", "ni", "ati",
            "awọn", "awon", "ọmọ", "omo", "nibo", "nigba", "daadaa", "dáadáa", "ẹ̀kú", "eku", "kaaro", "káàrọ̀", "ẹ ku",
            "ese", "ẹ ṣé", "o dabo", "wa", "mi", "lati", "ti", "si", "naa", "náà", "ko", "kò", "rara", "bẹẹni", "oruko", "orukọ", "rẹ", "re",
            "beeni", "olorun", "ọlọrun", "oluwa", "owó", "ilé", "ounje", "oúnjẹ", "fẹ", "lọ"};
    static final String[] YORUBA_SURE = {"bawo", "kilode", "kini", "jowo", "jọwọ", "awọn", "awon", "nibo", "daadaa", "dáadáa",
            "kaaro", "káàrọ̀", "ẹ ṣé", "bẹẹni", "beeni", "ọlọrun", "olorun", "oúnjẹ", "ounje", "náà"};

    static final Pattern FRENCH_LETTERS = Pattern.compile("[éèêëàâçùûôîïœ]", Pattern.CASE_INSENSITIVE);
    static final String[] FRENCH_WORDS = {"je", "tu", "il", "elle", "nous", "vous", "ils", "est", "suis", "sont", "pas",
            "le", "la", "les", "des", "une", "un", "du", "pour", "avec", "que", "qui", "quoi", "comment", "pourquoi",
            "bonjour", "salut", "merci", "oui", "non", "mais", "et", "dans", "sur", "mon", "ma", "mes", "ton", "votre",
            "c'est", "j'ai", "veux", "peux", "faire", "fait", "très", "bien", "aussi", "combien", "où", "quand", "ce",
            "cette", "au", "aux", "en", "être", "avoir", "s'il", "plaît", "aide", "aidez", "moi", "toi"};
    static final String[] FRENCH_SURE = {"je", "nous", "vous", "suis", "sont", "c'est", "j'ai", "bonjour", "merci",
            "pourquoi", "avec", "dans", "veux", "peux", "très", "s'il", "plaît", "qu'est-ce", "aujourd'hui", "est-ce"};

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}']+");
    private static final Pattern CODE = Pattern.compile("(?s)```.*?(```|$)|`[^`\\n]+`|https?://\\S+");

    /**
     * The language of the message, or null for English (or anything too short or too mixed to tell). Code blocks and
     * links aren't counted.
     */
    public static Language language(String message) {
        if (message == null) {
            return null;
        }
        String text = CODE.matcher(message).replaceAll(" ").toLowerCase(Locale.ROOT);
        List<String> words = new ArrayList<String>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            words.add(m.group());
        }
        if (words.isEmpty()) {
            return null;
        }
        String joined = " " + String.join(" ", words) + " ";
        int n = words.size();

        // Yoruba: its dotted letters are certain; otherwise its words
        int yoSure = count(joined, YORUBA_SURE), yo = count(joined, YORUBA_WORDS);
        if (YORUBA_LETTERS.matcher(text).find() && yo >= 1 || yoSure >= 2 || yoSure >= 1 && (n <= 6 || yo * 1f / n >= 0.4f)) {
            return Language.YORUBA;
        }
        // Pidgin
        int pSure = count(joined, PIDGIN_SURE), pWeak = count(joined, PIDGIN_WEAK);
        if (pSure >= 2 || pSure >= 1 && (n <= 12 || (pSure + pWeak) * 1f / n >= 0.15f)) {
            return Language.PIDGIN;
        }
        // French: its words, helped by its accents
        int frSure = count(joined, FRENCH_SURE), fr = count(joined, FRENCH_WORDS);
        boolean accents = FRENCH_LETTERS.matcher(text).find();
        if (frSure >= 1 && fr * 1f / n >= 0.3f || accents && fr >= 2 && fr * 1f / n >= 0.25f || frSure >= 2) {
            return Language.FRENCH;
        }
        return null;
    }

    private static int count(String joined, String[] list) {
        int c = 0;
        for (String w : list) {
            int at = 0;
            String k = " " + w + " ";
            while ((at = joined.indexOf(k, at)) >= 0) {
                c++;
                at += k.length() - 1;
            }
        }
        return c;
    }

    /** The Pidgin words in the message with their meanings, one a line ("wetin = what"); empty when there are none. */
    public static String glossary(String message) {
        if (message == null) {
            return "";
        }
        String joined = " " + String.join(" ", words(message.toLowerCase(Locale.ROOT))) + " ";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : PIDGIN.entrySet()) {
            if (joined.contains(" " + e.getKey() + " ") && b.indexOf(e.getKey() + " = ") < 0) {
                b.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
        }
        return b.toString().trim();
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<String>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    static String toEnglishPrompt(Language lang, String message) {
        String hints = lang == Language.PIDGIN ? glossary(message) : "";
        return "Translate the user's message from " + lang.shown + " into clear, natural English. Keep its meaning and tone;"
                + " keep names, numbers, code and links exactly as they are. Write only the English, nothing else: no"
                + " notes, no quotes, no answer to the message." + (hints.isEmpty() ? "" : "\nWord meanings that may help:\n" + hints)
                + " /no_think";
    }

    /** A few lines of Pidgin, so a small model sees how it differs from English (otherwise it gives the English back). */
    static final String PIDGIN_EXAMPLES = "\nExamples:\nEnglish: I don't know what is happening. -> Pidgin: I no know wetin dey happen."
            + "\nEnglish: Please come and eat, the food is ready. -> Pidgin: Abeg come chop, food don ready."
            + "\nEnglish: It is not easy, but you can do it. -> Pidgin: E no easy, but you fit do am."
            + "\nEnglish: Lagos is the biggest city and it has many people. -> Pidgin: Lagos na the biggest city and plenty people dey there.";

    static String fromEnglishPrompt(Language lang, boolean placeholders) {
        return "Translate this reply into " + lang.write + ". Do not leave it in English. Keep its meaning, warmth and length."
                + (placeholders ? " Keep every placeholder in double square brackets exactly as it is." : "")
                + " Keep names and numbers as they are. Write only the translation, nothing else." + (lang == Language.PIDGIN ? PIDGIN_EXAMPLES : "") + " /no_think";
    }

    /** The message in English, or null when the model couldn't (the caller then uses the message as it is). */
    public static String toEnglish(Brain brain, Language lang, String message) {
        if (lang == null || message == null || message.trim().isEmpty()) {
            return null;
        }
        String out = clean(brain.ask(toEnglishPrompt(lang, message), message.trim(), budget(message)));
        return sane(message, out) ? out : null;
    }

    /**
     * The English reply in the user's language, or null when the model couldn't (the caller then shows the English).
     * Code blocks and links are lifted out first and put back after, so they come through unchanged.
     */
    public static String fromEnglish(Brain brain, Language lang, String reply) {
        if (lang == null || reply == null || reply.trim().isEmpty()) {
            return null;
        }
        List<String> kept = new ArrayList<String>();
        String plain = protect(reply, kept);
        String out = clean(brain.ask(fromEnglishPrompt(lang, !kept.isEmpty()), plain, budget(plain)));
        if (!sane(plain, out) || same(plain, out) || kept.isEmpty() && out.matches("(?s).*\\bK\\d\\b.*")) {
            return null; // nothing came back, or the English came back unchanged
        }
        String back = restore(out, kept);
        for (int i = 0; i < kept.size(); i++) {
            if (!back.contains(kept.get(i))) {
                return null; // the model lost a code block or link: the English is safer
            }
        }
        return back;
    }

    /** Code blocks, inline code and links replaced by [[K1]], [[K2]]…; {@code kept} holds them in order. */
    static String protect(String text, List<String> kept) {
        Matcher m = CODE.matcher(text);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            kept.add(m.group());
            m.appendReplacement(b, Matcher.quoteReplacement("[[K" + kept.size() + "]]"));
        }
        m.appendTail(b);
        return b.toString();
    }

    static String restore(String text, List<String> kept) {
        String out = text;
        for (int i = kept.size(); i >= 1; i--) {
            out = out.replace("[[K" + i + "]]", kept.get(i - 1));
        }
        return out;
    }

    /** Room to write the translation: about the original's length again, with some slack. */
    static int budget(String text) {
        return Math.min(2400, Math.max(96, UsageLog.estimate(text) * 2 + 64));
    }

    /** Thinking, labels and wrapping quotes taken off what the model wrote. */
    static String clean(String said) {
        if (said == null) {
            return "";
        }
        String s = said.replaceAll("(?s)<think>.*?(</think>|$)", "").replaceAll("</?final>", "").trim();
        s = s.replaceFirst("(?i)^(here is|here's)?\\s*(the )?(english |french |yoruba |pidgin )?(translation|version)\\s*(is)?\\s*:\\s*", "");
        s = s.replaceFirst("(?i)^(english|french|yoruba|pidgin|traduction)\\s*:\\s*", "").trim();
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("“") && s.endsWith("”"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /** The same words, give or take case and punctuation: not translated. */
    static boolean same(String a, String b) {
        return a.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().equalsIgnoreCase(b.replaceAll("[^\\p{L}\\p{N}]+", " ").trim());
    }

    /** Not empty, and not wildly longer or shorter than the original (a model that answered instead of translating). */
    static boolean sane(String original, String translated) {
        if (translated == null || translated.trim().isEmpty()) {
            return false;
        }
        int a = original.trim().length(), b = translated.trim().length();
        return b <= a * 3 + 120 && b * 4 + 40 >= a;
    }
}
