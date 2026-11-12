package ai.ibiko.learn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The order of the words by how often English uses them, so the commonest take the fewest bytes in a pack (format 2):
 * a word's code is its place in this order, not its place in the alphabet. "the", "of", "a", the space and the stop
 * take one byte; the next few thousand words two; the rest three.
 * <p>
 * The order is counted once from the dictionary's own definitions (their 1.8 million words) and shipped with the app as
 * {@code knowledge/wordrank.bin}, so every device has the very same order: counting it on a device would take seconds.
 * The small words (and spaces and marks) come first, then the dictionary's words, each group by count, a tie by id.
 * <p>
 * File: {@code IBKR}, version 1, the dictionary's size (varint), how many ids (varint), then the ids in order (varints).
 * Its fingerprint (the first 8 bytes of the file's SHA-256) goes into every pack made with it, and a pack made with
 * another order is refused, never misread. Pure Java.
 */
public final class WordRank {
    private WordRank() {
    }

    static final byte[] MAGIC = {'I', 'B', 'K', 'R', 1};
    /** The file it is kept in, beside the dictionary. */
    public static final String FILE = "wordrank.bin";

    private static volatile int[] codeOf, idOf;
    private static volatile byte[] print;

    /** Whether the order is loaded (and matches the dictionary that is open). */
    public static boolean ready() {
        return codeOf != null;
    }

    /** The 8-byte fingerprint of the order in use, or null. */
    public static byte[] fingerprint() {
        byte[] p = print;
        return p == null ? null : p.clone();
    }

    /** A word id's code (1…), or 0 when it has none. */
    public static int code(int id) {
        int[] c = codeOf;
        return c == null || id <= 0 || id >= c.length ? 0 : c[id];
    }

    /** The word id a code stands for, or 0. */
    public static int id(int code) {
        int[] i = idOf;
        return i == null || code <= 0 || code >= i.length ? 0 : i[code];
    }

    /** Reads the order beside an open dictionary. False (and nothing loaded) when it is missing or for another dictionary. */
    static synchronized boolean load(File lexicon) {
        if (lexicon == null) {
            return false;
        }
        File f = new File(lexicon.getParentFile(), FILE);
        if (!f.isFile()) {
            return false;
        }
        try {
            return use(Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            return false;
        }
    }

    /** Takes an order from its file's bytes; false when they are not one, or not for the dictionary that is open. */
    static synchronized boolean use(byte[] b) {
        try {
            for (int i = 0; i < MAGIC.length; i++) {
                if (b.length <= i || b[i] != MAGIC[i]) {
                    return false;
                }
            }
            int[] at = {MAGIC.length};
            long dictionary = varint(b, at);
            if (dictionary != WordBook.size()) {
                return false; // made for another dictionary: packs stay in format 1
            }
            int n = (int) varint(b, at);
            int max = WordBook.FIRST + WordBook.size();
            if (n <= 0 || n > max) {
                return false;
            }
            int[] code = new int[max];
            int[] id = new int[n + 1];
            for (int k = 1; k <= n; k++) {
                int w = (int) varint(b, at);
                if (w <= 0 || w >= max || code[w] != 0) {
                    return false;
                }
                code[w] = k;
                id[k] = w;
            }
            print = Arrays.copyOf(java.security.MessageDigest.getInstance("SHA-256").digest(b), 8);
            idOf = id;
            codeOf = code;
            return true;
        } catch (IOException | java.security.NoSuchAlgorithmException | RuntimeException e) {
            return false;
        }
    }

    /** Forgets the order (tests). */
    static synchronized void drop() {
        codeOf = null;
        idOf = null;
        print = null;
    }

    private static long varint(byte[] b, int[] at) throws IOException {
        long v = 0;
        for (int shift = 0; shift < 63; shift += 7) {
            if (at[0] >= b.length) {
                throw new IOException("cut short");
            }
            int x = b[at[0]++] & 0xFF;
            v |= (long) (x & 0x7F) << shift;
            if (x < 0x80) {
                return v;
            }
        }
        throw new IOException("damaged");
    }

    private static void varint(ByteArrayOutputStream o, long v) {
        while (v >= 0x80) {
            o.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write((int) v);
    }

    /**
     * The words of a definition, counted for the order. Fixed here, apart from {@link Shorthand}'s tokenizer, so the
     * shipped order can always be made again exactly.
     */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*| |[^\\s\\p{L}\\p{N}]");

    /**
     * Counts the order from the open dictionary's definitions (the third column of {@code lexicon}) and returns its
     * file's bytes. Run when the dictionary changes (WordRankTest checks the shipped file is what this makes).
     */
    public static byte[] build(File lexicon) throws IOException {
        int max = WordBook.FIRST + WordBook.size();
        final long[] count = new long[max];
        for (String line : Files.readAllLines(lexicon.toPath(), StandardCharsets.UTF_8)) {
            int a = line.indexOf('\t');
            int b = a < 0 ? -1 : line.indexOf('\t', a + 1);
            if (b < 0) {
                continue;
            }
            int c = line.indexOf('\t', b + 1);
            Matcher m = WORD.matcher(c < 0 ? line.substring(b + 1) : line.substring(b + 1, c));
            while (m.find()) {
                int id = WordBook.id(m.group().toLowerCase(Locale.US));
                if (id > 0 && id < max) {
                    count[id]++;
                }
            }
        }
        Integer[] ids = new Integer[max - 1];
        int n = 0;
        for (int i = 1; i < max; i++) {
            if (WordBook.word(i) != null) {
                ids[n++] = i;
            }
        }
        Integer[] order = Arrays.copyOf(ids, n);
        Arrays.sort(order, (x, y) -> {
            boolean sx = x < WordBook.FIRST, sy = y < WordBook.FIRST;
            if (sx != sy) {
                return sx ? -1 : 1; // the small words, spaces and marks first
            }
            return count[x] != count[y] ? Long.compare(count[y], count[x]) : Integer.compare(x, y);
        });
        ByteArrayOutputStream o = new ByteArrayOutputStream(n * 3);
        o.write(MAGIC, 0, MAGIC.length);
        varint(o, WordBook.size());
        varint(o, n);
        for (Integer id : order) {
            varint(o, id);
        }
        return o.toByteArray();
    }
}
