package ai.dvine.ops.termux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One file per Termux command. A file left RUNNING means the process died or the app closed, and the command is not
 * verified. A timeout is recorded as TIMED_OUT, not as success. The command text is clipped and secrets are stripped.
 */
public final class TermuxSessionLog {
    public static final class Session {
        public final String id;
        public final String purpose;
        public final String command;
        public final String state;
        public final int exit;

        Session(String id, String purpose, String command, String state, int exit) {
            this.id = id;
            this.purpose = purpose;
            this.command = command;
            this.state = state;
            this.exit = exit;
        }

        public boolean unfinished() {
            return "RUNNING".equals(state) || "TIMED_OUT".equals(state) || "DISCONNECTED".equals(state) || "STOPPED".equals(state);
        }
    }

    private final File dir;

    public TermuxSessionLog(File dir) {
        this.dir = dir;
        if (dir != null && !dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    /** Writes RUNNING before the command is started. */
    public String open(String purpose, String command) {
        String id = "cmd-" + System.currentTimeMillis();
        write(id, purpose, command, "RUNNING", -1);
        return id;
    }

    public void close(String id, String state, int exit) {
        if (id == null || dir == null) {
            return;
        }
        Session prior = read(new File(dir, safe(id) + ".session"));
        String purpose = prior == null ? "" : prior.purpose;
        String command = prior == null ? "" : prior.command;
        write(id, purpose, command, state == null ? "DISCONNECTED" : state, exit);
    }

    public List<Session> unfinished() {
        List<Session> out = new ArrayList<Session>();
        File[] files = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".session"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            Session s = read(f);
            if (s != null && s.unfinished()) {
                out.add(s);
            }
        }
        return out;
    }

    /** A line for the workload sheet. Empty when every recorded command has finished. */
    public static String pending(File dir) {
        List<Session> open = new TermuxSessionLog(dir).unfinished();
        if (open.isEmpty()) {
            return "";
        }
        Session s = open.get(0);
        String what = s.purpose == null || s.purpose.isEmpty() ? "a command" : s.purpose;
        return open.size() + " unverified command" + (open.size() == 1 ? "" : "s") + ". Latest: " + what
                + " (" + s.state.toLowerCase(Locale.US).replace('_', ' ') + "). It was not resumed mid-way.";
    }

    private void write(String id, String purpose, String command, String state, int exit) {
        if (dir == null || id == null) {
            return;
        }
        String body = "id=" + id + "\npurpose=" + esc(clip(purpose)) + "\ncommand=" + esc(redact(clip(command)))
                + "\nstate=" + state + "\nexit=" + exit + "\n";
        File f = new File(dir, safe(id) + ".session");
        File tmp = new File(dir, safe(id) + ".session.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (f.exists() && !f.delete()) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            tmp.delete();
        }
    }

    private static Session read(File f) {
        if (f == null || !f.isFile()) {
            return null;
        }
        try {
            String id = "", purpose = "", command = "", state = "";
            int exit = -1;
            for (String raw : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                int c = raw.indexOf('=');
                if (c <= 0) {
                    continue;
                }
                String k = raw.substring(0, c);
                String v = raw.substring(c + 1).replace("\\n", "\n");
                switch (k) {
                    case "id": id = v; break;
                    case "purpose": purpose = v; break;
                    case "command": command = v; break;
                    case "state": state = v; break;
                    case "exit":
                        try {
                            exit = Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                            exit = -1;
                        }
                        break;
                    default:
                        break;
                }
            }
            return id.isEmpty() ? null : new Session(id, purpose, command, state, exit);
        } catch (IOException e) {
            return null;
        }
    }

    static String redact(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("ghp_[A-Za-z0-9]+", "ghp_…").replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-…");
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 180 ? s : s.substring(0, 180);
    }

    private static String esc(String s) {
        return s.replace("\n", "\\n");
    }

    private static String safe(String id) {
        return id.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
