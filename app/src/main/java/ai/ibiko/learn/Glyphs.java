package ai.ibiko.learn;

import java.util.Locale;

/**
 * Ibiko's own script: one character a word. Unicode keeps about 137,000 code points for private use, assigned to
 * nothing in any language; Ibiko takes them as its own letters. Each word's id ({@link Symbols}) becomes one of these
 * characters, so a sentence is as many characters as it has words, spaces and marks, with no separators:
 * "Abuja is the capital of Nigeria." is 12 characters, the same as its 12 codes. People never see them; {@link #text}
 * turns them back into the words.
 * <ul>
 * <li>The small words, spaces, marks and the first dictionary words (letters below 6,000): one character in
 * U+E000–U+F76F.</li>
 * <li>Every other single dictionary word (84,706 in all) and up to 47,000 words of Ibiko's own: one character in the
 * supplementary private planes (U+F0000–U+FFFFD, U+100000–U+10FFFD).</li>
 * <li>Past those 137,068 letters (rare): {@link #MORE} and two letters.</li>
 * <li>A number is written as its digits (they are already one character each); a capital is {@link #CAP} before the
 * word's letter, all capitals {@link #CAPS}.</li>
 * </ul>
 * Inside the brain the id itself (an int) is the fastest form; this is its smallest written form, for keeping and
 * passing sentences. Pure Java.
 */
public final class Glyphs {
    private Glyphs() {
    }

    private static final int BMP = 0xE000, BMP_SIZE = 6000;
    private static final int P15 = 0xF0000, P16 = 0x100000, PLANE = 65534;
    /** Marks: a capital follows, all capitals follow, a letter past the first 137,068 follows. */
    public static final int CAP = 0xF8FD, CAPS = 0xF8FC, MORE = 0xF8FF;
    /** The letters there are: 137,068. */
    private static final int ALL = BMP_SIZE + 2 * PLANE;
    /** Ibiko's own words (names, new words) start at this letter: room for 47,000 of them in one character each. */
    private static final int OWN = 90_000;

    /**
     * The letter number of an id. The small words, spaces and marks keep their ids (under 1000); a dictionary word is
     * 1000 + its place among the single words (84,706: all in one character); Ibiko's own words follow from
     * {@link #OWN}. Anything else (a dictionary phrase, an own word past the room) is its id past the last letter,
     * written with {@link #MORE}.
     */
    static int letter(int id) {
        if (id < WordBook.FIRST) {
            return id;
        }
        if (id < Symbols.CUSTOM) {
            int r = WordBook.rank(id);
            return r >= 0 ? WordBook.FIRST + r : ALL + id;
        }
        int own = OWN + (id - Symbols.CUSTOM);
        return own < ALL ? own : ALL + id;
    }

    static int idOf(int letter) {
        if (letter >= ALL) {
            return letter - ALL;
        }
        if (letter >= OWN) {
            return Symbols.CUSTOM + (letter - OWN);
        }
        return letter < WordBook.FIRST ? letter : WordBook.idAtRank(letter - WordBook.FIRST);
    }

    /** The character(s) for one letter number. */
    static void put(StringBuilder b, int n) {
        if (n < BMP_SIZE) {
            b.append((char) (BMP + n));
        } else if (n < BMP_SIZE + PLANE) {
            b.appendCodePoint(P15 + n - BMP_SIZE);
        } else if (n < BMP_SIZE + 2 * PLANE) {
            b.appendCodePoint(P16 + n - BMP_SIZE - PLANE);
        } else {
            // past the last letter: MORE, then the number in two letters (high part, low part)
            int rest = n - ALL;
            b.appendCodePoint(MORE);
            put(b, rest / 60_000);
            put(b, rest % 60_000);
        }
    }

    /** Words → Ibiko's script. */
    public static String script(String text) {
        StringBuilder b = new StringBuilder();
        for (String t : Shorthand.tokens(text)) {
            if (Symbols.number(t) >= 0) {
                b.append(t); // digits stay digits: already one character each
                continue;
            }
            String lower = t.toLowerCase(Locale.US);
            String filed = lower;
            if (!t.equals(lower)) {
                if (t.length() > 1 && t.equals(t.toUpperCase(Locale.US))) {
                    b.appendCodePoint(CAPS);
                } else if (t.equals(Character.toUpperCase(lower.charAt(0)) + lower.substring(1))) {
                    b.appendCodePoint(CAP);
                } else {
                    filed = t;
                }
            }
            put(b, letter(Symbols.id(filed)));
        }
        return b.toString();
    }

    /** Ibiko's script → the words, exactly as written. */
    public static String text(String script) {
        StringBuilder out = new StringBuilder();
        int mark = 0, more = 0, high = 0;
        for (int i = 0; i < script.length(); ) {
            int cp = script.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= '0' && cp <= '9') {
                out.appendCodePoint(cp);
                continue;
            }
            if (cp == CAP || cp == CAPS) {
                mark = cp;
                continue;
            }
            if (cp == MORE) {
                more = 2; // the next two letters are one number past the last letter
                continue;
            }
            int n;
            if (cp >= BMP && cp < BMP + BMP_SIZE) {
                n = cp - BMP;
            } else if (cp >= P15 && cp < P15 + PLANE) {
                n = BMP_SIZE + cp - P15;
            } else if (cp >= P16 && cp < P16 + PLANE) {
                n = BMP_SIZE + PLANE + cp - P16;
            } else {
                out.appendCodePoint(cp); // not a letter of the script: kept as it is
                mark = 0;
                more = 0;
                continue;
            }
            if (more == 2) {
                high = n;
                more = 1;
                continue;
            }
            if (more == 1) {
                n = ALL + high * 60_000 + n;
                more = 0;
            }
            String w = Symbols.word(idOf(n));
            if (w == null) {
                w = "?";
            } else if (mark == CAP) {
                w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            } else if (mark == CAPS) {
                w = w.toUpperCase(Locale.US);
            }
            mark = 0;
            out.append(w);
        }
        return out.toString();
    }

    /** How many characters (code points) a text takes in the script. */
    public static int length(String script) {
        return script.codePointCount(0, script.length());
    }
}
