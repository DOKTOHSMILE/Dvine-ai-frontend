package ai.dvine.ops.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** File-backed task records. One file per task so a process death can be recovered. */
public final class TaskStateRepository {
    private final File dir;

    public TaskStateRepository(File dir) {
        this.dir = dir;
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            // the caller sees empty reads; nothing is invented
        }
    }

    public synchronized void save(TaskRecord task) {
        if (task == null || task.id == null || dir == null) {
            return;
        }
        File f = new File(dir, safe(task.id) + ".task");
        byte[] bytes = encode(task).getBytes(StandardCharsets.UTF_8);
        File tmp = new File(dir, safe(task.id) + ".task.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        } catch (IOException e) {
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

    public synchronized TaskRecord load(String id) {
        if (id == null || dir == null) {
            return null;
        }
        File f = new File(dir, safe(id) + ".task");
        if (!f.isFile()) {
            return null;
        }
        try {
            return decode(Files.readString(f.toPath()));
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized List<TaskRecord> all() {
        List<TaskRecord> out = new ArrayList<TaskRecord>();
        File[] files = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".task"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            try {
                TaskRecord r = decode(Files.readString(f.toPath()));
                if (r != null && r.id != null) {
                    out.add(r);
                }
            } catch (IOException ignored) {
                // skip a damaged record rather than crash startup
            }
        }
        out.sort(Comparator.comparingLong((TaskRecord r) -> r.updatedAt).reversed());
        return out;
    }

    public static String encode(TaskRecord t) {
        StringBuilder b = new StringBuilder();
        line(b, "id", t.id);
        line(b, "instruction", t.instruction);
        line(b, "description", t.description);
        line(b, "state", t.state.name());
        line(b, "completed", join(t.completed));
        line(b, "remaining", join(t.remaining));
        line(b, "checkpoint", t.checkpoint);
        line(b, "assets", join(t.assets));
        line(b, "permission", t.permissionNote);
        line(b, "retries", Integer.toString(t.retries));
        line(b, "error", t.lastError);
        line(b, "cancel", t.cancelStatus);
        line(b, "created", Long.toString(t.createdAt));
        line(b, "updated", Long.toString(t.updatedAt));
        line(b, "destructive", Boolean.toString(t.destructive));
        line(b, "authorized", Boolean.toString(t.destructiveAuthorized));
        line(b, "idem", t.idempotencyKey);
        line(b, "kind", t.kind);
        for (var e : t.facts.entrySet()) {
            line(b, "fact." + e.getKey(), e.getValue());
        }
        return b.toString();
    }

    public static TaskRecord decode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        TaskRecord t = new TaskRecord();
        for (String raw : text.split("\n")) {
            int c = raw.indexOf('=');
            if (c <= 0) {
                continue;
            }
            String k = raw.substring(0, c);
            String v = unescape(raw.substring(c + 1));
            switch (k) {
                case "id": t.id = v; break;
                case "instruction": t.instruction = v; break;
                case "description": t.description = v; break;
                case "state":
                    try {
                        t.state = TaskState.valueOf(v);
                    } catch (IllegalArgumentException e) {
                        t.state = TaskState.FAILED;
                        t.lastError = "unknown state";
                    }
                    break;
                case "completed": splitInto(v, t.completed); break;
                case "remaining": splitInto(v, t.remaining); break;
                case "checkpoint": t.checkpoint = v; break;
                case "assets": splitInto(v, t.assets); break;
                case "permission": t.permissionNote = v; break;
                case "retries": t.retries = parseInt(v); break;
                case "error": t.lastError = v; break;
                case "cancel": t.cancelStatus = v; break;
                case "created": t.createdAt = parseLong(v); break;
                case "updated": t.updatedAt = parseLong(v); break;
                case "destructive": t.destructive = Boolean.parseBoolean(v); break;
                case "authorized": t.destructiveAuthorized = Boolean.parseBoolean(v); break;
                case "idem": t.idempotencyKey = v; break;
                case "kind": t.kind = v; break;
                default:
                    if (k.startsWith("fact.")) {
                        t.facts.put(k.substring(5), v);
                    }
                    break;
            }
        }
        return t.id == null ? null : t;
    }

    private static void splitInto(String v, List<String> into) {
        into.clear();
        if (v == null || v.isEmpty()) {
            return;
        }
        for (String p : v.split("%%")) {
            if (!p.isEmpty()) {
                into.add(p);
            }
        }
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append("%%");
            }
            b.append(p.replace("%%", " "));
        }
        return b.toString();
    }

    private static void line(StringBuilder b, String k, String v) {
        b.append(k).append('=').append(escape(v)).append('\n');
    }

    static String escape(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    static String unescape(String v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' && i + 1 < v.length()) {
                char n = v.charAt(++i);
                b.append(n == 'n' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String safe(String id) {
        return id.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public File directory() {
        return dir;
    }

    public static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
    }
}
