package ai.dvine.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What DVINE learned from its brain: each thing the user said that the brain turned into a command DVINE knows
 * ("could you sketch me a lion" → "draw a lion in 3d"). The next time the same words come, DVINE does it at once,
 * without asking the brain, even offline. Kept on the phone (a small text file), newest 400 only. Pure Java.
 */
public final class LearnedIntents {
    private static final int MAX = 400;
    private final File file;
    private final Map<String, String> learned = new LinkedHashMap<String, String>(16, 0.75f, true);

    public LearnedIntents(File file) {
        this.file = file;
        try {
            if (file != null && file.exists()) {
                for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        learned.put(line.substring(0, tab), line.substring(tab + 1));
                    }
                }
            }
        } catch (IOException ignored) {
            // start empty
        }
    }

    static String key(String said) {
        return said == null ? "" : said.toLowerCase(Locale.US).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** The command learned for these words, or null. */
    public synchronized String commandFor(String said) {
        return learned.get(key(said));
    }

    /** Remembers that these words meant this command. */
    public synchronized void learn(String said, String command) {
        String k = key(said);
        if (k.isEmpty() || command == null || k.equals(key(command))) {
            return;
        }
        learned.put(k, command);
        while (learned.size() > MAX) {
            learned.remove(learned.keySet().iterator().next());
        }
        save();
    }

    public synchronized int size() {
        return learned.size();
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : learned.entrySet()) {
            b.append(e.getKey()).append('\t').append(e.getValue().replace('\n', ' ')).append('\n');
        }
        try {
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // learning is a help, not a must
        }
    }
}
