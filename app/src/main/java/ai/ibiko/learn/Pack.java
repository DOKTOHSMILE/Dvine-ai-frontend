package ai.ibiko.learn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * The brain's codes written as small as they go: text packed into bytes by word id, and unpacked into exactly the same
 * text. This is the form the brain's files are kept in ({@link CodeFile}).
 * <ul>
 * <li>Each word, mark or space is one number, written in as few bytes as it needs (7 bits a byte): the commonest small
 * words and marks take 1 byte, a dictionary word 3.</li>
 * <li>The single space between two words is not written at all: two words in a row mean a space was there.</li>
 * <li>A capital or all capitals is two bits of the same number, not a mark of its own.</li>
 * <li>A dictionary word with an ending ("changes", "appended", "walking") is the word's id and the ending's number.</li>
 * <li>A number is its value. A word the dictionary doesn't have is spelled out, so it reads back the same on every
 * device, whatever that device has learned: nothing in a pack depends on one device's own ids.</li>
 * </ul>
 * Every engine with the same dictionary packs the same text into the same bytes: the header carries the dictionary's
 * size, and a pack made with another dictionary is refused instead of being read wrongly. Pure Java.
 */
public final class Pack {
    private Pack() {
    }

    static final byte[] MAGIC = {'I', 'B', 'K', 1};
    static final String[] ENDINGS = {"s", "es", "ed", "d", "ing", "ly", "er", "est"};

    /** Whether these bytes are a pack. */
    public static boolean is(byte[] b) {
        return b != null && b.length >= 5 && b[0] == 'I' && b[1] == 'B' && b[2] == 'K' && b[3] == 1;
    }

    private static void varint(ByteArrayOutputStream o, long v) {
        while (v >= 0x80) {
            o.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write((int) v);
    }

    private static boolean wordLike(String t) {
        return !t.isEmpty() && Character.isLetterOrDigit(t.codePointAt(0));
    }

    /** Text → bytes. */
    public static byte[] pack(String text) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(text == null ? 16 : text.length() / 2 + 16);
        o.write(MAGIC, 0, MAGIC.length);
        varint(o, WordBook.size());
        List<String> tokens = Shorthand.tokens(text == null ? "" : text);
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals(" ") && i > 0 && i + 1 < tokens.size() && wordLike(tokens.get(i - 1)) && wordLike(tokens.get(i + 1))) {
                continue; // the space between two words: implied
            }
            long n = Symbols.number(t);
            if (n >= 0) {
                varint(o, 3); // extended: a number
                varint(o, n);
                continue;
            }
            String lower = t.toLowerCase(Locale.US);
            int caps = t.equals(lower) ? 0 : t.length() > 1 && t.equals(t.toUpperCase(Locale.US)) ? 2
                    : t.equals(Character.toUpperCase(lower.charAt(0)) + lower.substring(1)) ? 1 : -1;
            int id = caps < 0 ? 0 : WordBook.id(lower);
            if (id > 0) {
                varint(o, ((long) id << 2) | caps);
                continue;
            }
            int ending = -1, base = 0;
            for (int e = 0; caps == 0 && e < ENDINGS.length && base == 0; e++) {
                if (lower.length() > ENDINGS[e].length() + 1 && lower.endsWith(ENDINGS[e])) {
                    int b = WordBook.id(lower.substring(0, lower.length() - ENDINGS[e].length()));
                    if (b >= WordBook.FIRST) {
                        base = b;
                        ending = e;
                    }
                }
            }
            if (base > 0) {
                varint(o, 3 | (2 << 2)); // extended: a dictionary word and an ending
                varint(o, ((long) base << 3) | ending);
                continue;
            }
            byte[] spelled = t.getBytes(StandardCharsets.UTF_8);
            varint(o, 3 | (1 << 2)); // extended: spelled out
            varint(o, spelled.length);
            o.write(spelled, 0, spelled.length);
        }
        return o.toByteArray();
    }

    private static final class In {
        final byte[] b;
        int at;

        In(byte[] b, int at) {
            this.b = b;
            this.at = at;
        }

        long varint() throws IOException {
            long v = 0;
            for (int shift = 0; shift < 63; shift += 7) {
                if (at >= b.length) {
                    throw new IOException("the pack is cut short");
                }
                int x = b[at++] & 0xFF;
                v |= (long) (x & 0x7F) << shift;
                if (x < 0x80) {
                    return v;
                }
            }
            throw new IOException("the pack is damaged");
        }
    }

    /** Bytes → the text, exactly. Refused when it was packed with another dictionary. */
    public static String unpack(byte[] bytes) throws IOException {
        if (!is(bytes)) {
            throw new IOException("not a pack");
        }
        In in = new In(bytes, MAGIC.length);
        long dictionary = in.varint();
        if (dictionary != WordBook.size()) {
            throw new IOException("packed with another dictionary (" + dictionary + " words, this one " + WordBook.size() + ")");
        }
        StringBuilder out = new StringBuilder(bytes.length * 2);
        boolean lastWord = false;
        while (in.at < bytes.length) {
            long v = in.varint();
            int type = (int) (v & 3);
            String w;
            if (type == 3) {
                int sub = (int) ((v >> 2) & 3);
                if (sub == 0) {
                    w = String.valueOf(in.varint());
                } else if (sub == 1) {
                    int len = (int) in.varint();
                    if (len < 0 || in.at + len > bytes.length) {
                        throw new IOException("the pack is cut short");
                    }
                    w = new String(bytes, in.at, len, StandardCharsets.UTF_8);
                    in.at += len;
                } else {
                    long x = in.varint();
                    String base = WordBook.word((int) (x >> 3));
                    if (base == null) {
                        throw new IOException("a word this dictionary doesn't have");
                    }
                    w = base + ENDINGS[(int) (x & 7)];
                }
            } else {
                w = WordBook.word((int) (v >> 2));
                if (w == null) {
                    throw new IOException("a word this dictionary doesn't have");
                }
                if (type == 1) {
                    w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
                } else if (type == 2) {
                    w = w.toUpperCase(Locale.US);
                }
            }
            boolean word = wordLike(w);
            if (word && lastWord) {
                out.append(' ');
            }
            out.append(w);
            lastWord = word;
        }
        return out.toString();
    }
}
