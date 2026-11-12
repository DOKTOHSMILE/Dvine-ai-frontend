package ai.ibiko.learn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * The brain's codes written as small as they go: text packed into bytes by word, and unpacked into exactly the same
 * text. This is the form the brain's files and conversations are kept in ({@link CodeFile}, {@link Identifier}).
 * <ul>
 * <li>Each word, mark or space is one number, written in as few bytes as it needs (7 bits a byte).</li>
 * <li>Format 2: the number is the word's place among the words by use ({@link WordRank}): "the", "of", the space and
 * the stop take 1 byte, the next few thousand words 2, the rest 3. Format 1 used the place in the alphabet (3 bytes
 * for nearly every word) and is still read.</li>
 * <li>The single space between two words is not written at all: two words in a row mean a space was there. Any other
 * space (a double space, a new line, a tab) has its own code, so the text comes back exactly.</li>
 * <li>A capital or all capitals is two bits of the same number, not a mark of its own.</li>
 * <li>A dictionary word with an ending ("changes", "appended", "walking") is the word's code and the ending's number.</li>
 * <li>A number is its value. A word the dictionary doesn't have is spelled out, so it reads back the same on every
 * device, whatever that device has learned: nothing in a pack depends on one device's own ids.</li>
 * </ul>
 * Format 2 starts {@code IBK 2} and the order's 8-byte fingerprint, and ends with a CRC-32 of the text: a pack made with
 * another order is refused, and a damaged one is caught, never read wrongly. Pure Java.
 */
public final class Pack {
    private Pack() {
    }

    static final byte[] MAGIC = {'I', 'B', 'K', 1};
    static final byte[] MAGIC2 = {'I', 'B', 'K', 2};
    static final String[] ENDINGS = {"s", "es", "ed", "d", "ing", "ly", "er", "est"};

    /** Whether these bytes are a pack (either format). */
    public static boolean is(byte[] b) {
        return b != null && b.length >= 5 && b[0] == 'I' && b[1] == 'B' && b[2] == 'K' && (b[3] == 1 || b[3] == 2);
    }

    /** Which format these bytes are packed in: 1, 2, or 0 for none. */
    public static int format(byte[] b) {
        return is(b) ? b[3] : 0;
    }

    static void varint(ByteArrayOutputStream o, long v) {
        while (v >= 0x80) {
            o.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write((int) v);
    }

    private static boolean wordLike(String t) {
        return !t.isEmpty() && Character.isLetterOrDigit(t.codePointAt(0));
    }

    /** Text → bytes: format 2 when the words' order is loaded, else format 1. */
    public static byte[] pack(String text) {
        String t = text == null ? "" : text;
        if (WordRank.ready()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(t.length() / 3 + 20);
            o.write(MAGIC2, 0, MAGIC2.length);
            byte[] fp = WordRank.fingerprint();
            o.write(fp, 0, fp.length);
            body(o, t, true);
            int crc = crc(t);
            o.write(crc >>> 24);
            o.write(crc >>> 16);
            o.write(crc >>> 8);
            o.write(crc);
            return o.toByteArray();
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream(t.length() / 2 + 16);
        o.write(MAGIC, 0, MAGIC.length);
        varint(o, WordBook.size());
        body(o, t, false);
        return o.toByteArray();
    }

    /** Text → its codes alone, in format 2, with no header (for files that carry one header for many texts). */
    static byte[] body(String text) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(text.length() / 3 + 4);
        body(o, text, true);
        return o.toByteArray();
    }

    static int crc(String text) {
        CRC32 c = new CRC32();
        c.update(text.getBytes(StandardCharsets.UTF_8));
        return (int) c.getValue();
    }

    /** A word id as it is written: its place by use (format 2) or the id itself (format 1). */
    private static long write(int id, boolean ranked) {
        return ranked ? WordRank.code(id) : id;
    }

    private static int read(long code, boolean ranked) {
        return ranked ? WordRank.id((int) code) : (int) code;
    }

    private static void body(ByteArrayOutputStream o, String text, boolean ranked) {
        List<String> tokens = Shorthand.tokens(text);
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
            long code = id > 0 ? write(id, ranked) : 0;
            if (code > 0) {
                varint(o, (code << 2) | caps);
                continue;
            }
            int ending = -1;
            long base = 0;
            for (int e = 0; caps == 0 && e < ENDINGS.length && base == 0; e++) {
                if (lower.length() > ENDINGS[e].length() + 1 && lower.endsWith(ENDINGS[e])) {
                    int b = WordBook.id(lower.substring(0, lower.length() - ENDINGS[e].length()));
                    if (b >= WordBook.FIRST) {
                        base = write(b, ranked);
                        ending = e;
                    }
                }
            }
            if (base > 0) {
                varint(o, 3 | (2 << 2)); // extended: a dictionary word and an ending
                varint(o, (base << 3) | ending);
                continue;
            }
            byte[] spelled = t.getBytes(StandardCharsets.UTF_8);
            varint(o, 3 | (1 << 2)); // extended: spelled out
            varint(o, spelled.length);
            o.write(spelled, 0, spelled.length);
        }
    }

    private static final class In {
        final byte[] b;
        final int end;
        int at;

        In(byte[] b, int at, int end) {
            this.b = b;
            this.at = at;
            this.end = end;
        }

        long varint() throws IOException {
            long v = 0;
            for (int shift = 0; shift < 63; shift += 7) {
                if (at >= end) {
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

    /** Bytes → the text, exactly. Refused when packed with another dictionary or order, or damaged. */
    public static String unpack(byte[] bytes) throws IOException {
        int f = format(bytes);
        if (f == 2) {
            if (bytes.length < MAGIC2.length + 8 + 4) {
                throw new IOException("the pack is cut short");
            }
            byte[] fp = WordRank.fingerprint();
            if (fp == null) {
                throw new IOException("this pack needs the words' order, and it isn't loaded");
            }
            if (!Arrays.equals(fp, Arrays.copyOfRange(bytes, MAGIC2.length, MAGIC2.length + 8))) {
                throw new IOException("packed with another order of words");
            }
            int end = bytes.length - 4;
            String text = unbody(bytes, MAGIC2.length + 8, end);
            int crc = (bytes[end] & 0xFF) << 24 | (bytes[end + 1] & 0xFF) << 16 | (bytes[end + 2] & 0xFF) << 8 | (bytes[end + 3] & 0xFF);
            if (crc != crc(text)) {
                throw new IOException("the pack is damaged");
            }
            return text;
        }
        if (f != 1) {
            throw new IOException("not a pack");
        }
        In in = new In(bytes, MAGIC.length, bytes.length);
        long dictionary = in.varint();
        if (dictionary != WordBook.size()) {
            throw new IOException("packed with another dictionary (" + dictionary + " words, this one " + WordBook.size() + ")");
        }
        return read(in, false);
    }

    /** Codes alone (format 2, no header) → the text. */
    static String unbody(byte[] b, int from, int to) throws IOException {
        if (!WordRank.ready()) {
            throw new IOException("this pack needs the words' order, and it isn't loaded");
        }
        return read(new In(b, from, to), true);
    }

    private static String read(In in, boolean ranked) throws IOException {
        StringBuilder out = new StringBuilder(Math.max(16, (in.end - in.at) * 3));
        boolean lastWord = false;
        while (in.at < in.end) {
            long v = in.varint();
            int type = (int) (v & 3);
            String w;
            if (type == 3) {
                int sub = (int) ((v >> 2) & 3);
                if (sub == 0) {
                    w = String.valueOf(in.varint());
                } else if (sub == 1) {
                    int len = (int) in.varint();
                    if (len < 0 || in.at + len > in.end) {
                        throw new IOException("the pack is cut short");
                    }
                    w = new String(in.b, in.at, len, StandardCharsets.UTF_8);
                    in.at += len;
                } else if (sub == 2) {
                    long x = in.varint();
                    String base = WordBook.word(read(x >> 3, ranked));
                    if (base == null) {
                        throw new IOException("a word this dictionary doesn't have");
                    }
                    w = base + ENDINGS[(int) (x & 7)];
                } else {
                    throw new IOException("the pack is damaged");
                }
            } else {
                w = WordBook.word(read(v >> 2, ranked));
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
