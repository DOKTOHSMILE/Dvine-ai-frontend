package ai.ibiko.act;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Every action Ibiko carried out on the user's behalf (a post handed to an app, a copy, later a social action), with
 * when, where, whether the user approved it or auto-approve did, and what happened. A plain text file on the phone,
 * newest last, so the user can always see what was done in their name.
 */
public final class ActionLog {
    private ActionLog() {
    }

    static File file(File filesDir) {
        return new File(filesDir, "actions.log");
    }

    public static synchronized void add(File filesDir, String kind, String where, String how, String outcome, String text) {
        String line = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date())
                + "\t" + kind + "\t" + where + "\t" + how + "\t" + outcome + "\t" + (text == null ? "" : text.replace('\n', ' ').replace('\t', ' '))
                + "\n";
        try (FileWriter w = new FileWriter(file(filesDir), StandardCharsets.UTF_8, true)) {
            w.write(line);
        } catch (IOException ignored) {
            // the action already happened; a log that can't be written mustn't undo it
        }
    }

    /** The log's lines, newest first. */
    public static synchronized List<String> read(File filesDir) {
        List<String> out = new ArrayList<String>();
        try {
            File f = file(filesDir);
            if (f.isFile()) {
                for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    if (!l.trim().isEmpty()) {
                        out.add(0, l);
                    }
                }
            }
        } catch (IOException ignored) {
            // nothing to show
        }
        return out;
    }
}
