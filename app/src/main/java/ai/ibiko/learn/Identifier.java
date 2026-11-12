package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The identifier: any text into ids, and ids back into the same text. A message's sentences get their ids
 * ({@code <message id>#<n>}, as {@link Codes} files them) and each word its id ({@link Symbols}, kept for good), so a
 * sentence is a short list of numbers: "Abuja is the capital of Nigeria." is {@code m1k2#0 → 812 9 3 1544 7 2201}.
 * <p>
 * Every conversation is saved this way as it goes, in its own chat's folder
 * ({@code files/ibiko/chats/<chat>/memory/codes.tsv}), one line a sentence: "role, sentence id, word codes". The codes read
 * back to the exact words ({@link #read}). Deleting the chat deletes its file. Pure Java.
 */
public final class Identifier {
    private Identifier() {
    }

    /** One sentence, identified: its id, and its words as the brain's short codes ({@link Shorthand}). */
    public static final class Sentence {
        public final String id;
        public final String codes;
        /** The same sentence in Ibiko's own script ({@link Glyphs}): one character a word, space or mark. */
        public final String script;

        Sentence(String id, String codes, String script) {
            this.id = id;
            this.codes = codes;
            this.script = script;
        }

        /** How many words (and marks) it has. */
        public int size() {
            return codes.isEmpty() ? 0 : codes.split(" ").length;
        }
    }

    /** Identifies every sentence and word of a message. */
    public static List<Sentence> identify(String messageId, String text) {
        List<Sentence> out = new ArrayList<Sentence>();
        for (String[] s : Codes.sentences(messageId, text)) {
            out.add(new Sentence(s[0], Shorthand.codes(s[1]), Glyphs.script(s[1])));
        }
        return out;
    }

    /** The words a saved sentence stands for, read back for people: from Ibiko's script, or from codes. */
    public static String read(String saved) {
        return script(saved) ? Glyphs.text(saved) : Shorthand.words(saved);
    }

    private static boolean script(String saved) {
        for (int i = 0; i < saved.length(); i++) {
            if (Character.getType(saved.charAt(i)) == Character.PRIVATE_USE || Character.isSurrogate(saved.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ibiko-conversation-ids");
        t.setDaemon(true);
        return t;
    });

    /** The file a chat's conversation was saved in by older versions: one line a sentence, in Ibiko's script. */
    public static File file(File filesDir, String chat) {
        return new File(ai.ibiko.main.ChatSpace.of(filesDir, chat), ai.ibiko.main.ChatSpace.MEMORY + "/codes.tsv");
    }

    /**
     * The file a chat's conversation is saved in now: packed codes (format 2, {@link Pack}). It starts {@code IBKC},
     * version 2 and the words' order fingerprint; then one record a message: its length (varint), the role in one byte
     * (0 the user, 1 Ibiko, 2 another, then its name), the message id (a base-36 id as its number, else spelled), how
     * many sentences, each sentence's number, length and codes, and a CRC-32 of the sentences. Plain bytes: nothing a
     * copy or a keyboard can drop, as it dropped the script's characters.
     */
    public static File packed(File filesDir, String chat) {
        return new File(ai.ibiko.main.ChatSpace.of(filesDir, chat), ai.ibiko.main.ChatSpace.MEMORY + "/codes.ibk");
    }

    static final byte[] HEAD = {'I', 'B', 'K', 'C', 2};

    /** Saves a message of a chat as ids (off the caller's thread): one record (or line) a sentence. */
    public static void save(final File filesDir, final String chat, final String role, final String messageId, final String text) {
        if (filesDir == null || chat == null || text == null || text.trim().isEmpty()) {
            return;
        }
        final List<String[]> said = Codes.sentences(messageId, text);
        WRITER.execute(() -> {
            synchronized (Identifier.class) { // one writer at a time with a conversion of the same file
                write(filesDir, chat, role, said);
            }
        });
    }

    private static void write(File filesDir, String chat, String role, List<String[]> said) {
        try {
            if (WordRank.ready()) {
                convert(filesDir, chat); // what an older version kept for this chat comes first, in the same file
                append(packed(filesDir, chat), role, said);
                return;
            }
            // no words' order yet (the dictionary isn't there): the script, as before; converted once it is
            StringBuilder b = new StringBuilder();
            for (String[] s : said) {
                b.append(role).append('\t').append(s[0]).append('\t').append(Glyphs.script(s[1])).append('\n');
            }
            File f = file(filesDir, chat);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // the chat itself is kept in chats.json; only its id copy missed this
        }
    }

    /** Adds a message's sentences ({"<id>#<n>", text}) to a chat's packed file as one record. */
    private static void append(File f, String role, List<String[]> sentences) throws IOException {
        if (sentences.isEmpty()) {
            return;
        }
        byte[] fp = WordRank.fingerprint();
        if (f.isFile() && !sameHead(f, fp)) {
            File aside = new File(f.getPath() + ".old");
            Files.move(f.toPath(), aside.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        if (!f.isFile()) {
            f.getParentFile().mkdirs();
            o.write(HEAD, 0, HEAD.length);
            o.write(fp, 0, fp.length);
        }
        // one record a message: sentences of the same message id together, in order
        String msg = null;
        List<String[]> run = new ArrayList<String[]>();
        for (String[] s : sentences) {
            int hash = s[0].lastIndexOf('#');
            String m = hash < 0 ? s[0] : s[0].substring(0, hash);
            if (msg != null && !msg.equals(m)) {
                record(o, role, msg, run);
                run = new ArrayList<String[]>();
            }
            msg = m;
            run.add(s);
        }
        record(o, role, msg, run);
        Files.write(f.toPath(), o.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void record(java.io.ByteArrayOutputStream o, String role, String message, List<String[]> sentences) {
        java.io.ByteArrayOutputStream r = new java.io.ByteArrayOutputStream();
        if ("user".equals(role)) {
            r.write(0);
        } else if ("assistant".equals(role)) {
            r.write(1);
        } else {
            r.write(2);
            bytes(r, role);
        }
        if (message.matches("[1-9a-z][0-9a-z]{0,11}")) {
            r.write(0);
            Pack.varint(r, Long.parseLong(message, 36)); // "mmwdkml0ab2": 8 bytes, not 11
        } else {
            r.write(1);
            bytes(r, message);
        }
        Pack.varint(r, sentences.size());
        StringBuilder all = new StringBuilder();
        for (String[] s : sentences) {
            int hash = s[0].lastIndexOf('#');
            long n = 0;
            try {
                n = hash < 0 ? 0 : Long.parseLong(s[0].substring(hash + 1));
            } catch (NumberFormatException ignored) {
                // numbered 0
            }
            Pack.varint(r, n);
            byte[] body = Pack.body(s[1]);
            Pack.varint(r, body.length);
            r.write(body, 0, body.length);
            all.append(s[1]).append('\n');
        }
        int crc = Pack.crc(all.toString());
        r.write(crc >>> 24);
        r.write(crc >>> 16);
        r.write(crc >>> 8);
        r.write(crc);
        Pack.varint(o, r.size());
        byte[] rec = r.toByteArray();
        o.write(rec, 0, rec.length);
    }

    private static boolean sameHead(File f, byte[] fp) throws IOException {
        byte[] head = new byte[HEAD.length + 8];
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            if (in.read(head) != head.length) {
                return false;
            }
        }
        for (int i = 0; i < HEAD.length; i++) {
            if (head[i] != HEAD[i]) {
                return false;
            }
        }
        return java.util.Arrays.equals(fp, java.util.Arrays.copyOfRange(head, HEAD.length, head.length));
    }

    private static void bytes(java.io.ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        Pack.varint(o, b.length);
        o.write(b, 0, b.length);
    }

    /**
     * A chat's saved conversation, read back: {role, sentence id, text} a sentence, oldest first, from the packed file
     * and from an older script file not yet converted. A damaged record ends the reading there; what came before is
     * kept.
     */
    public static List<String[]> lines(File filesDir, String chat) {
        List<String[]> out = new ArrayList<String[]>();
        File old = file(filesDir, chat);
        if (old.isFile()) {
            try {
                for (String l : Files.readAllLines(old.toPath(), StandardCharsets.UTF_8)) {
                    String[] p = l.split("\t", 3);
                    if (p.length == 3) {
                        out.add(new String[]{p[0], p[1], read(p[2])});
                    }
                }
            } catch (IOException ignored) {
                // nothing from it
            }
        }
        File f = packed(filesDir, chat);
        if (!f.isFile()) {
            return out;
        }
        try {
            byte[] b = Files.readAllBytes(f.toPath());
            if (b.length < HEAD.length + 8 || !java.util.Arrays.equals(WordRank.fingerprint(),
                    java.util.Arrays.copyOfRange(b, HEAD.length, HEAD.length + 8))) {
                return out; // made with another order of words: kept as it is, not read wrongly
            }
            if (b[HEAD.length - 1] != HEAD[HEAD.length - 1]) {
                return out; // another version of this file: kept as it is
            }
            int[] at = {HEAD.length + 8};
            while (at[0] < b.length) {
                int len = (int) varint(b, at);
                int end = at[0] + len;
                if (len < 7 || end > b.length) {
                    break;
                }
                int r = b[at[0]++];
                String role = r == 0 ? "user" : r == 1 ? "assistant" : string(b, at);
                String message = b[at[0]++] == 0 ? Long.toString(varint(b, at), 36) : string(b, at);
                int count = (int) varint(b, at);
                List<String[]> got = new ArrayList<String[]>();
                StringBuilder all = new StringBuilder();
                for (int k = 0; k < count; k++) {
                    long n = varint(b, at);
                    int bl = (int) varint(b, at);
                    if (bl < 0 || at[0] + bl > end - 4) {
                        throw new IOException("damaged");
                    }
                    String text = Pack.unbody(b, at[0], at[0] + bl);
                    at[0] += bl;
                    got.add(new String[]{role, message + "#" + n, text});
                    all.append(text).append('\n');
                }
                int crc = (b[end - 4] & 0xFF) << 24 | (b[end - 3] & 0xFF) << 16 | (b[end - 2] & 0xFF) << 8 | (b[end - 1] & 0xFF);
                if (at[0] != end - 4 || crc != Pack.crc(all.toString())) {
                    break;
                }
                out.addAll(got);
                at[0] = end;
            }
        } catch (IOException | RuntimeException ignored) {
            // what was read is kept
        }
        return out;
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

    private static String string(byte[] b, int[] at) throws IOException {
        int n = (int) varint(b, at);
        if (n < 0 || at[0] + n > b.length) {
            throw new IOException("cut short");
        }
        String s = new String(b, at[0], n, StandardCharsets.UTF_8);
        at[0] += n;
        return s;
    }

    /**
     * Converts a chat's older script file into the packed file, sentence by sentence. Every line is checked first:
     * its words, written in the script again, must give back the very same characters (a word whose id this device no
     * longer knows would not). When all do, the old file is removed; when one doesn't, it is kept as
     * {@code codes.tsv.old} beside the packed copy. True when it converted.
     */
    public static synchronized boolean convert(File filesDir, String chat) {
        File old = file(filesDir, chat);
        if (!old.isFile() || !WordRank.ready()) {
            return false;
        }
        try {
            List<String> raw = Files.readAllLines(old.toPath(), StandardCharsets.UTF_8);
            boolean exact = true;
            String role = null;
            List<String[]> run = new ArrayList<String[]>();
            File to = packed(filesDir, chat);
            for (String l : raw) {
                String[] p = l.split("\t", 3);
                if (p.length < 3) {
                    continue;
                }
                String text = read(p[2]);
                exact &= script(p[2]) ? Glyphs.script(text).equals(p[2]) : Shorthand.codes(text).equals(p[2]);
                if (role != null && !role.equals(p[0])) {
                    append(to, role, run);
                    run = new ArrayList<String[]>();
                }
                role = p[0];
                run.add(new String[]{p[1], text});
            }
            if (role != null) {
                append(to, role, run);
            }
            if (exact) {
                Files.delete(old.toPath());
            } else {
                Files.move(old.toPath(), new File(old.getPath() + ".old").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Converts every chat's older script file (once, at start, off the main thread). How many were converted. */
    public static int convertAll(File filesDir) {
        File[] chats = new File(ai.ibiko.main.ChatSpace.root(filesDir), ai.ibiko.main.ChatSpace.CHATS).listFiles();
        int n = 0;
        for (File c : chats == null ? new File[0] : chats) {
            if (new File(c, ai.ibiko.main.ChatSpace.MEMORY + "/codes.tsv").isFile() && convert(filesDir, c.getName())) {
                n++;
            }
        }
        return n;
    }

    /** Waits until every save asked for so far is written (for tests and before the app is closed). */
    public static void flush() {
        try {
            WRITER.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // nothing more to wait for
        }
    }
}
