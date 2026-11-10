package ai.ibiko.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What lies behind a step the user can open: a file's changes (green and red lines) or what a command printed. Kept
 * as a small file under files/steps; the step's line carries its name after a mark ("Edited app.py ⟦a1b2…⟧"), so the
 * kept "Worked for 6s" list opens it too. Pure Java.
 */
public final class StepDetail {
    private static final Pattern MARK = Pattern.compile("\\s*\u27E6([0-9a-f-]{36})\u27E7\\s*$");
    static final int MAX_LINES = 4000;

    /** The kept detail: a title, the counts (-1 when it isn't a diff) and the lines. */
    public static final class Detail {
        public final String title;
        public final int added, removed;
        public final List<String> lines;

        Detail(String title, int added, int removed, List<String> lines) {
            this.title = title;
            this.added = added;
            this.removed = removed;
            this.lines = lines;
        }

        public boolean diff() {
            return added >= 0;
        }
    }

    private StepDetail() {
    }

    public static String mark(String id) {
        return " \u27E6" + id + "\u27E7";
    }

    /** The step's words without the mark. */
    public static String words(String line) {
        return line == null ? "" : MARK.matcher(line).replaceAll("");
    }

    /** The detail's name in a step line, or null when it has none. */
    public static String id(String line) {
        Matcher m = MARK.matcher(line == null ? "" : line);
        return m.find() ? m.group(1) : null;
    }

    /** Keeps the detail; its name, or null when it couldn't be kept. */
    public static String save(File filesDir, String title, List<String> lines, int added, int removed) {
        File dir = new File(filesDir, "steps");
        dir.mkdirs();
        String id = UUID.randomUUID().toString();
        StringBuilder b = new StringBuilder();
        b.append(added).append(' ').append(removed).append(' ').append(title == null ? "" : title.replace('\n', ' ')).append('\n');
        int n = 0;
        for (String l : lines) {
            if (n++ >= MAX_LINES) {
                b.append("@ \u2026the rest is left out\n");
                break;
            }
            b.append(l.replace('\n', ' ')).append('\n');
        }
        try {
            Files.write(new File(dir, id).toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (IOException e) {
            return null;
        }
    }

    public static Detail load(File filesDir, String id) {
        try {
            String all = new String(Files.readAllBytes(new File(new File(filesDir, "steps"), id).toPath()), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<String>(Arrays.asList(all.split("\n", -1)));
            String[] head = lines.remove(0).split(" ", 3);
            if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            return new Detail(head.length > 2 ? head[2] : "", Integer.parseInt(head[0]), Integer.parseInt(head[1]), lines);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
