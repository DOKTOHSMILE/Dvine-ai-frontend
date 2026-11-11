package ai.ibiko.connect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * One record beside a cloned repository. It is written RUNNING before clone, pull or push talks to GitHub, and
 * rewritten only when that call returns. A record left RUNNING means the app stopped in the middle: the push is
 * not done.
 */
public final class GitOp {
    public static final String FILE = ".ibiko-git-op.json";

    private GitOp() {
    }

    public static void open(File dir, String action) {
        write(dir, action, "RUNNING", "");
    }

    public static void close(File dir, String state, String note) {
        String action = field(dir, "action");
        write(dir, action.isEmpty() ? "git" : action, state == null ? "FAILED" : state, note);
    }

    /** A warning when a git call started and never came back. Null when the last call finished or failed in the open. */
    public static String pending(File dir) {
        if (!"RUNNING".equals(field(dir, "state"))) {
            return null;
        }
        String action = field(dir, "action");
        if (action.isEmpty()) {
            action = "git call";
        }
        return "A " + action + " was started and never confirmed. It is not done. Check git log before pushing again.";
    }

    private static void write(File dir, String action, String state, String note) {
        if (dir == null) {
            return;
        }
        dir.mkdirs();
        String body = "action=" + one(action) + "\nstate=" + one(state) + "\nnote=" + one(note) + "\n";
        File dest = new File(dir, FILE);
        File tmp = new File(dir, FILE + ".tmp");
        try {
            Files.write(tmp.toPath(), body.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.write(dest.toPath(), body.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the call still proceeds; a missing record is not a successful push
            }
        }
    }

    private static String field(File dir, String key) {
        File f = dir == null ? null : new File(dir, FILE);
        if (f == null || !f.isFile()) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith(key + "=")) {
                    return line.substring(key.length() + 1).trim();
                }
            }
        } catch (IOException e) {
            return "";
        }
        return "";
    }

    private static String one(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').replace('\r', ' ').replaceAll("ghp_[A-Za-z0-9]+", "ghp_…").trim();
        return t.length() > 240 ? t.substring(0, 240) : t;
    }
}
