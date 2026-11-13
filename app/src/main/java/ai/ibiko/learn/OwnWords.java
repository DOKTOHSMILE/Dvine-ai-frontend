package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The person's own words, which {@link TypoFix} never changes: a word the dictionary lacks and has no clear fix for
 * that they used in {@link #OWN_AFTER} messages or more (a name, slang, a word from their language, a term from their
 * work), and any word they said to keep (a tap on "Read … as …"). Kept on the device, the most recent {@link #MOST}.
 * Pure Java.
 */
public final class OwnWords {
    static final int OWN_AFTER = 2, MOST = 3000;
    private static final Pattern WORD = Pattern.compile("[A-Za-z][a-z]{4,}");

    private final File file;
    /** word → messages it was in (a kept word: {@link #KEPT}). */
    private final LinkedHashMap<String, Integer> seen = new LinkedHashMap<String, Integer>(16, 0.75f, true);
    static final int KEPT = 1_000_000;

    public OwnWords(File file) {
        this.file = file;
        load();
    }

    /** The words that are the person's own now. */
    public synchronized Set<String> own() {
        Set<String> out = new HashSet<String>();
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            if (e.getValue() >= OWN_AFTER) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** A message as the person wrote it: each word the dictionary lacks is counted once. */
    public synchronized void heard(String text, TypoFix.Dict d) {
        if (text == null || TypoFix.leaveAlone(text)) {
            return;
        }
        Set<String> once = new HashSet<String>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            String w = m.group().toLowerCase(Locale.ROOT);
            // only a word with no clear fix (a name, slang, a word from their language) becomes theirs by use; a habitual
            // misspelling ("recieve") keeps being fixed unless they say to keep it
            if (once.add(w) && !TypoFix.known(w, d) && TypoFix.fixWord(text, m.start(), m.end(), m.group(), d, null) == null) {
                Integer n = seen.get(w);
                seen.put(w, n == null ? 1 : n >= KEPT ? n : n + 1);
            }
        }
        while (seen.size() > MOST) {
            seen.remove(seen.keySet().iterator().next());
        }
        save();
    }

    /** "Keep my word": never changed again. */
    public synchronized void keep(String word) {
        if (word != null && !word.trim().isEmpty()) {
            seen.put(word.trim().toLowerCase(Locale.ROOT), KEPT);
            save();
        }
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.split("\t");
                if (p.length == 2 && p[1].matches("\\d+")) {
                    seen.put(p[0], Integer.parseInt(p[1]));
                }
            }
        } catch (IOException ignored) {
            // starts empty
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            b.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }
}
