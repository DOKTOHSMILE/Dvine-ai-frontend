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
 * {@code !}. "Abuja is the capital of Nigeria." becomes something like {@code ^pc 9g 1 3fy1 3n ^lez5 9z}: what the
 * brain stores, files and compares; {@link #words} turns it back into the same sentence for people.
 * Pure Java.
 */
public final class Shorthand {
    private Shorthand() {
    }

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*|[^\\s\\p{L}\\p{N}]");

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
    static List<String> tokens(String text) {
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
            b.append(b.length() == 0 ? "" : " ").append(mark).append(code(Symbols.id(filed)));
        }
        return b.toString();
    }

    /** Codes → the words again, spaced as people write them. A code it doesn't know reads as "?". */
    public static String words(String codes) {
        StringBuilder b = new StringBuilder();
        String prev = null;
        for (String c : codes.trim().split(" ")) {
            if (c.isEmpty()) {
                continue;
            }
            char mark = c.charAt(0);
            String bare = mark == '^' || mark == '!' ? c.substring(1) : c;
            String w = Symbols.word(id(bare));
            if (w == null) {
                w = "?";
            } else if (mark == '^') {
                w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            } else if (mark == '!') {
                w = w.toUpperCase(Locale.US);
            }
            boolean glued = w.matches("[.,!?;:)\\]}%…’”]") || prev != null && prev.matches("[(\\[{$#@‘“₦]");
            if (b.length() > 0 && !glued) {
                b.append(' ');
            }
            b.append(w);
            prev = w;
        }
        return b.toString();
    }
}
