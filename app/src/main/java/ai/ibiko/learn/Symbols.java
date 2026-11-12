package ai.ibiko.learn;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Ibiko's word ids: every word it meets gets one number, the same for good (an English word its fixed dictionary id
 * from {@link WordBook}; any other word an id from {@link #CUSTOM} up, kept on the device), so its mind can
 * file, find and compare by numbers instead of by spelling. A word is turned into its id once, at the edge (when a
 * question comes in or a fact is learned); after that the indexes ({@link Codes}, the facts, the brain box) work on
 * ints: an int key is hashed and compared in one step, where a word is hashed and compared letter by letter. Pure
 * Java, thread-safe.
 */
public final class Symbols {
    private static final ConcurrentHashMap<String, Integer> IDS = new ConcurrentHashMap<String, Integer>();
    /** Where the ids of words outside the dictionary start ({@link WordBook} has 1 to about 153,000). */
    public static final int CUSTOM = 1_000_000;
    /**
     * Numbers have ids made from their value, nothing stored: 1969 is {@code NUMBER + 1969}. Whole numbers from 0 to
     * 600,000,000 written without a leading zero; anything else ("007", "2nd") is a word of its own.
     */
    public static final int NUMBER = 1_500_000_000;

    /** A whole number written plainly ("0", "1969", "600000000"), or -1. */
    public static long number(String word) {
        if (word == null || word.isEmpty() || word.length() > 9 || !word.matches("0|[1-9][0-9]*")) {
            return -1;
        }
        long v = Long.parseLong(word);
        return v <= 600_000_000L ? v : -1;
    }

    /** The id a word outside the dictionary gets: worked out from its letters (the same everywhere), in CUSTOM…NUMBER. */
    static int hashed(String word) {
        long h = 1125899906842597L;
        for (int i = 0; i < word.length(); i++) {
            h = 31 * h + word.charAt(i);
        }
        return CUSTOM + (int) Math.floorMod(h, (long) (NUMBER - CUSTOM));
    }

    private Symbols() {
    }

    /** The id of a word (made the first time it is seen, and saved when the words are kept on the device). 0 for nothing. */
    public static int id(String word) {
        if (word == null || word.isEmpty()) {
            return 0;
        }
        long n = number(word);
        if (n >= 0) {
            return (int) (NUMBER + n); // a number: its id is its value, never stored
        }
        Integer have = IDS.get(word);
        if (have != null) {
            return have;
        }
        int fixed = WordBook.id(word); // an English word: its fixed dictionary id, the same everywhere
        if (fixed > 0) {
            IDS.putIfAbsent(word, fixed);
            return IDS.get(word);
        }
        int id;
        boolean made = false;
        synchronized (WORDS) {
            Integer again = IDS.get(word);
            if (again != null) {
                return again;
            }
            // the id comes from the word itself, so every device gives the same word the same id; two different words
            // landing on one id (rare) take the next free one
            id = hashed(word);
            while (WORDS.containsKey(id) && !word.equals(WORDS.get(id))) {
                id = id + 1 >= NUMBER ? CUSTOM : id + 1;
            }
            IDS.put(word, id);
            WORDS.put(id, word);
            made = true;
        }
        if (made && WordBook.ready()) {
            keep(word, id); // kept for good only once the dictionary is open: a dictionary word never gets an id of its own for good
        }
        return id;
    }

    /** id → the word, to read ids back as words. */
    private static final ConcurrentHashMap<Integer, String> WORDS = new ConcurrentHashMap<Integer, String>();

    /** The word an id stands for, or null. */
    public static String word(int id) {
        if (id >= NUMBER) {
            return String.valueOf(id - NUMBER); // a number reads back as itself
        }
        return id < CUSTOM ? WordBook.word(id) : WORDS.get(id);
    }

    /**
     * Where the ids of words outside the dictionary are kept: {@code files/brain/words3.tsv}, one "word<TAB>id" a line,
     * only ever added to (dictionary words need no file: their ids are fixed by {@link WordBook}).
     */
    private static volatile java.io.File kept;
    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ibiko-word-ids");
        t.setDaemon(true);
        return t;
    });

    /**
     * Keeps the ids on the device from now on, and reads back those kept before, so a word has the same id for good
     * (a conversation saved as ids reads back the same next week). Once; later calls do nothing.
     */
    public static synchronized void open(java.io.File filesDir) {
        if (kept != null || filesDir == null) {
            return;
        }
        java.io.File f = new java.io.File(filesDir, "brain/words3.tsv"); // only words outside the dictionary
        boolean fresh = WORDS.isEmpty(); // opened first thing at start: no word outside the dictionary has an id yet
        if (fresh && f.isFile()) {
            try {
                for (String line : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    int tab = line.lastIndexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }
                    String w = line.substring(0, tab);
                    int id = Integer.parseInt(line.substring(tab + 1).trim());
                    if (IDS.putIfAbsent(w, id) == null) {
                        WORDS.put(id, w);
                    }
                }
            } catch (java.io.IOException | NumberFormatException ignored) {
                // what could be read is kept; the rest is made again
            }
        } else if (!fresh) {
            // ids were already made in this run before the file was opened: they become the kept ones
            f.delete();
        }
        kept = f;
        if (!fresh) {
            for (java.util.Map.Entry<String, Integer> e : IDS.entrySet()) {
                if (e.getValue() >= CUSTOM) {
                    keep(e.getKey(), e.getValue());
                }
            }
        }
    }

    private static void keep(final String word, final int id) {
        final java.io.File f = kept;
        if (f == null || word.indexOf('\n') >= 0 || word.indexOf('\t') >= 0) {
            return;
        }
        WRITER.execute(() -> {
            try {
                f.getParentFile().mkdirs();
                java.nio.file.Files.write(f.toPath(), (word + "\t" + id + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
                // the id still works for this run
            }
        });
    }

    /** The id of a word only if it has one already: -1 when this word was never seen (nothing is filed under it). */
    public static int known(String word) {
        long n = number(word);
        if (n >= 0) {
            return (int) (NUMBER + n);
        }
        Integer have = word == null ? null : IDS.get(word);
        if (have != null) {
            return have;
        }
        int fixed = WordBook.id(word); // a dictionary word's id is fixed: known without having been filed first
        return fixed > 0 ? fixed : -1;
    }

    /** How many words have ids. */
    public static int size() {
        return IDS.size();
    }
}
