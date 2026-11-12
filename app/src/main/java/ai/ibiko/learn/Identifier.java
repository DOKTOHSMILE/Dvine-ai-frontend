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
        for (int i = 0; i < saved.length(); i++) {
            if (Character.getType(saved.charAt(i)) == Character.PRIVATE_USE || Character.isSurrogate(saved.charAt(i))) {
                return Glyphs.text(saved);
            }
        }
        return Shorthand.words(saved);
    }

    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ibiko-conversation-ids");
        t.setDaemon(true);
        return t;
    });

    /** The file a chat's conversation is saved in as ids. */
    public static File file(File filesDir, String chat) {
        return new File(ai.ibiko.main.ChatSpace.of(filesDir, chat), ai.ibiko.main.ChatSpace.MEMORY + "/codes.tsv");
    }

    /** Saves a message of a chat as ids (off the caller's thread): one line a sentence. */
    public static void save(final File filesDir, final String chat, final String role, final String messageId, final String text) {
        if (filesDir == null || chat == null || text == null || text.trim().isEmpty()) {
            return;
        }
        final List<Sentence> said = identify(messageId, text);
        WRITER.execute(() -> {
            StringBuilder b = new StringBuilder();
            for (Sentence s : said) {
                b.append(role).append('\t').append(s.id).append('\t').append(s.script).append('\n'); // one character a word
            }
            try {
                File f = file(filesDir, chat);
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // the chat itself is kept in chats.json; only its id copy missed this line
            }
        });
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
