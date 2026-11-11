package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every English word Ibiko knows has a fixed id, the same on every device and every run:
 * <ul>
 * <li>{@code 1}–{@code 999}: the small words the dictionary leaves out (the, is, of, and, I, you…) and punctuation, in a
 * fixed list ({@link #SMALL}).</li>
 * <li>{@code 1000 + n}: the dictionary's words in its sorted order (Open English WordNet: 152,137 words, nouns,
 * verbs, adjectives and adverbs), {@code n} the word's place in {@code knowledge/lexicon.tsv}.</li>
 * <li>Words in neither (names, slang, new words) get ids from {@link Symbols}, from {@link Symbols#CUSTOM} up.</li>
 * </ul>
 * The dictionary is read in place (the file mapped into memory, only where each word starts kept, 600 kB), so a word's
 * id is found by a binary search over bytes, not by loading 150,000 words. Pure Java.
 */
public final class WordBook {
    private WordBook() {
    }

    /** The first id of the dictionary's words; the small words are below it. */
    public static final int FIRST = 1000;

    /** The small words and punctuation, each with its part of speech; their id is their place here, from 1. */
    static String[][] SMALL = parse("the:determiner a:determiner an:determiner this:determiner that:determiner"
            + " these:determiner those:determiner my:determiner your:determiner his:determiner her:determiner its:determiner"
            + " our:determiner their:determiner some:determiner any:determiner no:determiner every:determiner each:determiner"
            + " either:determiner neither:determiner such:determiner what:pronoun which:pronoun who:pronoun whom:pronoun"
            + " whose:pronoun i:pronoun me:pronoun you:pronoun he:pronoun him:pronoun she:pronoun it:pronoun we:pronoun us:pronoun"
            + " they:pronoun them:pronoun myself:pronoun yourself:pronoun himself:pronoun herself:pronoun itself:pronoun"
            + " ourselves:pronoun themselves:pronoun mine:pronoun yours:pronoun hers:pronoun ours:pronoun theirs:pronoun"
            + " someone:pronoun something:pronoun anyone:pronoun anything:pronoun everyone:pronoun everything:pronoun"
            + " nobody:pronoun nothing:pronoun of:preposition in:preposition on:preposition at:preposition by:preposition"
            + " for:preposition with:preposition from:preposition to:preposition into:preposition onto:preposition"
            + " about:preposition over:preposition under:preposition between:preposition among:preposition through:preposition"
            + " during:preposition before:preposition after:preposition above:preposition below:preposition against:preposition"
            + " without:preposition within:preposition along:preposition across:preposition behind:preposition beyond:preposition"
            + " near:preposition since:preposition until:preposition upon:preposition via:preposition per:preposition"
            + " and:conjunction or:conjunction but:conjunction nor:conjunction so:conjunction yet:conjunction if:conjunction"
            + " because:conjunction although:conjunction though:conjunction while:conjunction whereas:conjunction unless:conjunction"
            + " than:conjunction whether:conjunction when:adverb where:adverb why:adverb how:adverb not:adverb n't:adverb"
            + " is:verb are:verb was:verb were:verb be:verb been:verb being:verb am:verb do:verb does:verb did:verb have:verb"
            + " has:verb had:verb will:verb would:verb shall:verb should:verb can:verb could:verb may:verb might:verb must:verb"
            + " 's:particle 're:particle 've:particle 'll:particle 'd:particle 'm:particle"
            + " .:punctuation ,:punctuation !:punctuation ?:punctuation ;:punctuation ::punctuation (:punctuation"
            + " ):punctuation \":punctuation ':punctuation -:punctuation —:punctuation …:punctuation /:punctuation"
            + " &:punctuation %:punctuation $:punctuation #:punctuation @:punctuation *:punctuation +:punctuation =:punctuation"
            + " [:punctuation ]:punctuation {:punctuation }:punctuation ‘:punctuation ’:punctuation “:punctuation"
            + " ”:punctuation ₦:punctuation");

    /**
     * The spaces between words have codes too, after the list (so no earlier id moves): a space, a new line, a tab, and
     * a double space as one code of its own.
     */
    static {
        String[][] more = Arrays.copyOf(SMALL, SMALL.length + 4);
        more[SMALL.length] = new String[]{" ", "space"};
        more[SMALL.length + 1] = new String[]{"\n", "space"};
        more[SMALL.length + 2] = new String[]{"\t", "space"};
        more[SMALL.length + 3] = new String[]{"  ", "space"}; // a double space: one code of its own
        SMALL = more;
    }

    private static final Map<String, Integer> SMALL_ID = new HashMap<String, Integer>();

    static {
        for (int i = 0; i < SMALL.length; i++) {
            SMALL_ID.putIfAbsent(SMALL[i][0], i + 1);
        }
    }

    private static String[][] parse(String s) {
        String[] parts = s.split(" ");
        String[][] out = new String[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            int c = parts[i].lastIndexOf(':');
            out[i] = new String[]{parts[i].substring(0, c), parts[i].substring(c + 1)};
        }
        return out;
    }

    private static volatile MappedByteBuffer map;
    /** Where each different word's first line starts, in the file's (sorted) order. */
    private static volatile int[] starts;

    /** Reads the dictionary in place (once, off the main thread: about a tenth of a second). */
    public static synchronized boolean open(File lexicon) {
        if (starts != null) {
            return true;
        }
        if (lexicon == null || !lexicon.isFile() || lexicon.length() > Integer.MAX_VALUE) {
            return false;
        }
        try (RandomAccessFile f = new RandomAccessFile(lexicon, "r"); FileChannel ch = f.getChannel()) {
            MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
            int size = (int) ch.size();
            int[] at = new int[200_000];
            int n = 0, lineStart = 0;
            int prevStart = -1, prevLen = 0;
            while (lineStart < size) {
                int tab = lineStart;
                while (tab < size && m.get(tab) != '\t' && m.get(tab) != '\n') {
                    tab++;
                }
                int len = tab - lineStart;
                if (len > 0 && (prevStart < 0 || !same(m, prevStart, prevLen, lineStart, len))) {
                    if (n == at.length) {
                        at = Arrays.copyOf(at, n * 2);
                    }
                    at[n++] = lineStart;
                    prevStart = lineStart;
                    prevLen = len;
                }
                int nl = tab;
                while (nl < size && m.get(nl) != '\n') {
                    nl++;
                }
                lineStart = nl + 1;
            }
            map = m;
            starts = Arrays.copyOf(at, n);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static boolean ready() {
        return starts != null;
    }

    /** How many dictionary words have ids. */
    public static int size() {
        int[] s = starts;
        return s == null ? 0 : s.length;
    }

    private static boolean same(MappedByteBuffer m, int a, int alen, int b, int blen) {
        if (alen != blen) {
            return false;
        }
        for (int i = 0; i < alen; i++) {
            if (m.get(a + i) != m.get(b + i)) {
                return false;
            }
        }
        return true;
    }

    /** The key (the word) of the dictionary line starting at {@code start}. */
    private static int keyEnd(MappedByteBuffer m, int start) {
        int e = start;
        int size = m.limit();
        while (e < size && m.get(e) != '\t' && m.get(e) != '\n') {
            e++;
        }
        return e;
    }

    private static int compare(MappedByteBuffer m, int start, byte[] want) {
        int end = keyEnd(m, start);
        int n = Math.min(end - start, want.length);
        for (int i = 0; i < n; i++) {
            int x = m.get(start + i) & 0xff, y = want[i] & 0xff;
            if (x != y) {
                return x - y;
            }
        }
        return (end - start) - want.length;
    }

    /** The fixed id of a word (lower case): a small word, a dictionary word, or 0 when it is in neither. */
    public static int id(String word) {
        if (word == null || word.isEmpty()) {
            return 0;
        }
        Integer small = SMALL_ID.get(word);
        if (small != null) {
            return small;
        }
        int[] s = starts;
        MappedByteBuffer m = map;
        if (s == null || m == null) {
            return 0;
        }
        byte[] want = word.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = s.length - 1;
        synchronized (WordBook.class) { // the buffer's reads are absolute: no shared position, but kept simple and safe
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int c = compare(m, s[mid], want);
                if (c == 0) {
                    return FIRST + mid;
                }
                if (c < 0) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
        }
        return 0;
    }

    /** The word a fixed id stands for, or null. */
    public static String word(int id) {
        if (id >= 1 && id <= SMALL.length) {
            return SMALL[id - 1][0];
        }
        int[] s = starts;
        MappedByteBuffer m = map;
        int n = id - FIRST;
        if (s == null || m == null || n < 0 || n >= s.length) {
            return null;
        }
        synchronized (WordBook.class) {
            int end = keyEnd(m, s[n]);
            byte[] b = new byte[end - s[n]];
            for (int i = 0; i < b.length; i++) {
                b[i] = m.get(s[n] + i);
            }
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    /** The part of speech of a word with a fixed id (its first sense in the dictionary), or null. */
    public static String pos(int id) {
        if (id >= 1 && id <= SMALL.length) {
            return SMALL[id - 1][1];
        }
        int[] s = starts;
        MappedByteBuffer m = map;
        int n = id - FIRST;
        if (s == null || m == null || n < 0 || n >= s.length) {
            return null;
        }
        synchronized (WordBook.class) {
            int a = keyEnd(m, s[n]) + 1, b = a;
            while (b < m.limit() && m.get(b) != '\t' && m.get(b) != '\n') {
                b++;
            }
            byte[] out = new byte[Math.max(0, b - a)];
            for (int i = 0; i < out.length; i++) {
                out[i] = m.get(a + i);
            }
            return new String(out, StandardCharsets.UTF_8).toLowerCase(Locale.US);
        }
    }
}
