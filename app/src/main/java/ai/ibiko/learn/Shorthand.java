package ai.ibiko.learn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The interpreter between people's words and the brain's codes. Every word (and punctuation mark) becomes its id
 * ({@link Symbols}: an English word's fixed dictionary id, another word its own id) written short, in base 36:
 * "telephone" is word 136,258 → {@code 2x9m}; "the" is {@code 1}. A capital is marked {@code ^}, all capitals
 * {@code !}. The space between words has its own code too (a double space one code, a new line and a tab theirs), so a text comes back exactly
 * as it was written. A number is {@code #} and its value in base 36 (1969 → {@code #1ip}), never stored as a word.
 * "Abuja is the capital of Nigeria." is 12 codes (6 words, 5 spaces, the stop): what the
 * brain stores, files and compares; {@link #words} turns it back into the same sentence for people.
 * Pure Java.
 */
public final class Shorthand {
    private Shorthand() {
    }

    /**
     * Words, a double space (one code), and every other character on its own (a mark, a space, a new line, a tab,
     * anything else): every character of the text is in exactly one token, so nothing is ever lost.
     */
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*|  |(?s:.)");

    /** One id written short: 136258 → "2x9m". */
    public static String code(int id) {
        return Integer.toString(id, 36);
    }

    /** "2x9m" → 136258; -1 when it isn't a code. */
    public static int id(String code) {
        try {
            return Integer.parseInt(code, 36);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The tokens of a text: words (with inner apostrophes and hyphens) and single punctuation marks. */
    public static List<String> tokens(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<String>();
        }
        return simple(text) ? split(text) : graphemes(text);
    }

    /** No emoji and no combining mark: the old split, so a sentence's codes do not move. */
    private static boolean simple(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int type = Character.getType(cp);
            if (Character.charCount(cp) > 1 || type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    private static List<String> split(String text) {
        List<String> out = new ArrayList<String>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** One token per grapheme, then the same word and double-space rules. A joined emoji stays one token. */
    private static List<String> graphemes(String text) {
        java.text.BreakIterator breaks = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT);
        breaks.setText(text);
        List<String> g = new ArrayList<String>();
        int start = breaks.first();
        for (int end = breaks.next(); end != java.text.BreakIterator.DONE; start = end, end = breaks.next()) {
            String piece = text.substring(start, end);
            if (!g.isEmpty() && joins(g.get(g.size() - 1), piece)) {
                g.set(g.size() - 1, g.get(g.size() - 1) + piece);
            } else {
                g.add(piece);
            }
        }
        List<String> out = new ArrayList<String>();
        int i = 0;
        while (i < g.size()) {
            if (i + 1 < g.size() && " ".equals(g.get(i)) && " ".equals(g.get(i + 1))) {
                out.add("  ");
                i += 2;
                continue;
            }
            if (wordPiece(g.get(i))) {
                StringBuilder w = new StringBuilder();
                while (i < g.size()) {
                    String cur = g.get(i);
                    if (wordPiece(cur)) {
                        w.append(cur);
                        i++;
                    } else if ((cur.equals("'") || cur.equals("\u2019") || cur.equals("-")) && i + 1 < g.size()
                            && wordPiece(g.get(i + 1)) && w.length() > 0) {
                        w.append(cur).append(g.get(i + 1));
                        i += 2;
                    } else {
                        break;
                    }
                }
                out.add(w.toString());
                continue;
            }
            out.add(g.get(i));
            i++;
        }
        return out;
    }

    /** A joiner, a variation selector or a skin tone stays on the character it belongs to. */
    private static boolean joins(String prev, String next) {
        if (prev.endsWith("\u200D") || next.startsWith("\u200D")) {
            return true;
        }
        int cp = next.codePointAt(0);
        return cp == 0xFE0E || cp == 0xFE0F || (cp >= 0x1F3FB && cp <= 0x1F3FF);
    }

    /** A letter or digit, plus any combining marks hanging off it. */
    private static boolean wordPiece(String g) {
        if (g == null || g.isEmpty()) {
            return false;
        }
        int cp = g.codePointAt(0);
        if (!Character.isLetterOrDigit(cp)) {
            return false;
        }
        for (int i = Character.charCount(cp); i < g.length(); ) {
            int n = g.codePointAt(i);
            int type = Character.getType(n);
            // Letters and digits are allowed after the first too: Java 17's BreakIterator keeps an Indic conjunct
            // (न्दी: letter, virama, letter, sign) as one cluster where Java 20+ splits it. Refusing it would cut the
            // word in two, and Pack would read the two halves back with a space between them.
            if (type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK && type != Character.COMBINING_SPACING_MARK
                    && !Character.isLetterOrDigit(n) && n != 0x200C && n != 0x200D) {
                return false;
            }
            i += Character.charCount(n);
        }
        return true;
    }

    /** Words → codes, one code a token, separated by spaces. */
    public static String codes(String text) {
        StringBuilder b = new StringBuilder();
        for (String t : tokens(text)) {
            String lower = t.toLowerCase(Locale.US);
            String mark = "";
            String filed = lower;
            if (!t.equals(lower)) {
                if (t.length() > 1 && t.equals(t.toUpperCase(Locale.US))) {
                    mark = "!"; // NASA
                } else if (t.equals(Character.toUpperCase(lower.charAt(0)) + lower.substring(1))) {
                    mark = "^"; // Abuja
                } else {
                    filed = t; // iPhone: filed as it is written
                }
            }
            long n = Symbols.number(t);
            if (n >= 0) {
                b.append(b.length() == 0 ? "" : " ").append('#').append(Long.toString(n, 36)); // 1969 → #1ip
                continue;
            }
            b.append(b.length() == 0 ? "" : " ").append(mark).append(code(Symbols.id(filed)));
        }
        return b.toString();
    }

    /** Codes → the words again, exactly as written: the spaces have their own codes. A code it doesn't know reads as "?". */
    public static String words(String codes) {
        StringBuilder b = new StringBuilder();
        for (String c : codes.trim().split(" ")) {
            if (c.isEmpty()) {
                continue;
            }
            char mark = c.charAt(0);
            if (mark == '#') {
                try {
                    b.append(Long.parseLong(c.substring(1), 36)); // a number's code is its value
                } catch (NumberFormatException e) {
                    b.append('?');
                }
                continue;
            }
            String bare = mark == '^' || mark == '!' ? c.substring(1) : c;
            String w = Symbols.word(id(bare));
            if (w == null) {
                w = "?";
            } else if (mark == '^') {
                w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            } else if (mark == '!') {
                w = w.toUpperCase(Locale.US);
            }
            b.append(w);
        }
        return b.toString();
    }
}
