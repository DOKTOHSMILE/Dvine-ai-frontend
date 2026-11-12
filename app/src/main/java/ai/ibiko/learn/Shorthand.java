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
        List<String> out = new ArrayList<String>();
        Matcher m = TOKEN.matcher(text == null ? "" : text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
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
