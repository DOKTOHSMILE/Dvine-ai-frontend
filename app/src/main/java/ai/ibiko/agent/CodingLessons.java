package ai.ibiko.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What fixed an error before, kept by the error's signature ({@link ErrorReader#signature}): when a failed check is
 * followed by a passing one, the edits between them are the fix. The next time the same error comes up, in any chat or
 * project, the agent is told what fixed it, so it learns from its own work instead of starting again each time. Kept
 * in one small file on the device, the most recent {@link #MOST}. Pure Java.
 */
public final class CodingLessons {
    static final int MOST = 500;

    private final File file;
    /** signature → {times it worked, the fix}; oldest first. */
    private final LinkedHashMap<String, String[]> lessons = new LinkedHashMap<String, String[]>(16, 0.75f, true);

    public CodingLessons(File file) {
        this.file = file;
        load();
    }

    /** "fixed by … (2 times)", or null when this error hasn't been fixed before. */
    public synchronized String recall(String signature) {
        String[] l = signature == null ? null : lessons.get(signature);
        if (l == null) {
            return null;
        }
        int n = Integer.parseInt(l[0]);
        return l[1] + (n > 1 ? " (it worked " + n + " times)" : "");
    }

    /** The fix for this error: kept, or counted again when it is the same fix. */
    public synchronized void learn(String signature, String fix) {
        if (signature == null || fix == null || fix.trim().isEmpty()) {
            return;
        }
        String f = fix.replaceAll("[\\t\\n\\r]+", " ").trim();
        if (f.length() > 300) {
            f = f.substring(0, 297) + "…";
        }
        String[] had = lessons.get(signature);
        int n = had != null && had[1].equals(f) ? Integer.parseInt(had[0]) + 1 : 1;
        lessons.put(signature, new String[]{Integer.toString(n), f});
        while (lessons.size() > MOST) {
            lessons.remove(lessons.keySet().iterator().next());
        }
        save();
    }

    public synchronized int size() {
        return lessons.size();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.split("\t", 3);
                if (p.length == 3 && p[1].matches("\\d+")) {
                    lessons.put(p[0], new String[]{p[1], p[2]});
                }
            }
        } catch (IOException ignored) {
            // nothing learned yet, or unreadable: starts empty
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String[]> e : lessons.entrySet()) {
            b.append(e.getKey().replace('\t', ' ')).append('\t').append(e.getValue()[0]).append('\t').append(e.getValue()[1]).append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) {
                Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }

    /** The lessons for one kind of error, newest last (for a report or a test). */
    synchronized List<String> all() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String[]> e : lessons.entrySet()) {
            out.add(e.getKey() + " → " + e.getValue()[1]);
        }
        return out;
    }
}
