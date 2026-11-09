package ai.dvine.scribe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.dvine.ops.task.TaskRecord;
import ai.dvine.ops.task.TaskState;
import ai.dvine.ops.task.TaskStateRepository;

/**
 * DVINE Scribe: the project-memory agent. It writes the plan, the map and the checkpoint itself. It does not ask the
 * conversation model what happened, and it does not mark a job finished because a reply said it was.
 */
public final class Scribe {
    private final File workspace;
    private final TaskStateRepository tasks;
    private TaskRecord current;
    private File projectDir;
    private boolean active;

    public Scribe(File workspace) {
        this.workspace = workspace == null ? new File("ibiko") : workspace;
        this.tasks = new TaskStateRepository(new File(this.workspace, "memory/tasks"));
    }

    /** A short chat is not a project. A build, a fix, or a long instruction is. */
    public static boolean worth(String instruction) {
        if (instruction == null) {
            return false;
        }
        String t = instruction.trim();
        if (t.length() > 120) {
            return true;
        }
        return t.toLowerCase(Locale.US).matches("(?s).*\\b(build|implement|audit|refactor|project|repository|repo|fix|write|create|clone|deploy)\\b.*");
    }

    /** Opens a record for this instruction. No-op when {@link #worth} is false. */
    public void begin(String persona, String instruction) {
        if (!worth(instruction)) {
            active = false;
            return;
        }
        active = true;
        String key = slug(clip(instruction, 160));
        TaskRecord prior = tasks.byIdempotency(key);
        if (prior != null && prior.state != null && prior.state.open()) {
            current = prior;
            projectDir = new File(workspace, "memory/projects/" + key);
            new File(projectDir, "checkpoints").mkdirs();
            current.state = TaskState.RESUMING;
            current.updatedAt = System.currentTimeMillis();
            tasks.save(current);
            ProjectSurvey survey = ProjectSurvey.of(workspace);
            write("PROJECT_MAP.md", survey.map());
            Reconcile recovered = Reconcile.check(workspace, current.assets);
            write("RECOVERY.md", recovered.report());
            noteMissing(recovered);
            append("PROGRESS.md", "\n- Resumed " + current.id + ". The plan was kept. The map was read again ("
                    + survey.files + " files). Claimed files on disk: " + recovered.present.size()
                    + ". Missing: " + recovered.missing.size() + ".\n");
            current.state = TaskState.RUNNING;
            current.updatedAt = System.currentTimeMillis();
            tasks.save(current);
            write("EXECUTION.json", execution());
            return;
        }
        long now = System.currentTimeMillis();
        current = new TaskRecord();
        current.id = "job-" + now;
        current.instruction = redact(instruction);
        current.description = clip(current.instruction, 160);
        current.kind = persona == null ? "ibiko" : persona;
        current.idempotencyKey = key;
        current.state = TaskState.QUEUED;
        current.createdAt = now;
        current.updatedAt = now;
        ProjectSurvey survey = ProjectSurvey.of(workspace);
        current.remaining.addAll(survey.steps);
        tasks.save(current);
        projectDir = new File(workspace, "memory/projects/" + key);
        new File(projectDir, "checkpoints").mkdirs();
        write("PROJECT_MANIFEST.json", manifest(persona));
        write("PROJECT_PLAN.md", survey.plan(current.instruction));
        write("PROJECT_MAP.md", survey.map());
        write("PROGRESS.md", "# Progress\n\n- Surveyed the workspace (" + survey.files
                + " files). Nothing is marked done yet.\n");
        write("DECISIONS.md", "# Decisions\n\n- The job is tracked by Scribe, apart from the chat.\n");
        write("ERRORS.md", "# Errors\n\n");
        write("NEXT_ACTIONS.md", next());
        write("CHANGELOG.md", "# Changelog\n\n");
        current.state = TaskState.RUNNING;
        current.updatedAt = System.currentTimeMillis();
        tasks.save(current);
        write("EXECUTION.json", execution());
    }

    /** A checkpoint after a round of work. The text is the work log, not a claim that the job is finished. */
    public void checkpoint(String work) {
        if (!active || current == null) {
            return;
        }
        current.state = TaskState.RUNNING;
        current.checkpoint = redact(clip(work, 4000));
        current.updatedAt = System.currentTimeMillis();
        tasks.save(current);
        write("checkpoints/latest.md", "# Checkpoint " + current.updatedAt + "\n\n" + current.checkpoint + "\n");
        append("PROGRESS.md", "\n- Checkpoint at " + current.updatedAt + " (work saved, not verified).\n");
        write("EXECUTION.json", execution());
    }

    /**
     * Closes the round. Completed only when every claimed file is on disk. A long reply, or a reply that says the
     * work is done, is not enough. A missing file stays on the list.
     */
    public void finish(String result, boolean userStopped, List<String> changed) {
        if (!active || current == null) {
            return;
        }
        List<String> files = changed == null ? new ArrayList<String>() : changed;
        current.assets.clear();
        current.assets.addAll(files);
        String shown = redact(result == null ? "" : result);
        boolean asksContinue = shown.toLowerCase(Locale.US).contains("continue");
        boolean failed = shown.contains("didn't answer") || shown.startsWith("Stopped.");
        Reconcile check = Reconcile.check(workspace, files);
        write("RECOVERY.md", check.report());
        if (userStopped || asksContinue || failed) {
            current.state = TaskState.PAUSED;
            noteMissing(check);
            if (!current.remaining.contains("Resume from the last checkpoint")) {
                current.remaining.add("Resume from the last checkpoint");
            }
        } else if (check.verified()) {
            current.state = TaskState.VERIFYING;
            current.completed.add("Verified " + check.present.size() + " files on disk");
            current.remaining.remove("Verify from the files and command results. A sentence in chat is not completion.");
            current.state = TaskState.COMPLETED;
        } else {
            current.state = TaskState.PAUSED;
            current.lastError = check.missing.isEmpty()
                    ? "No files were written, so the job is not finished."
                    : "Claimed files are not on disk.";
            noteMissing(check);
        }
        current.updatedAt = System.currentTimeMillis();
        tasks.save(current);
        append("CHANGELOG.md", "\n## " + current.updatedAt + "\n\nState: " + current.state + "\n\n" + clip(shown, 2000) + "\n");
        write("NEXT_ACTIONS.md", next());
        write("EXECUTION.json", execution());
        if (!files.isEmpty()) {
            StringBuilder b = new StringBuilder("# Files touched\n\n");
            for (String f : files) {
                b.append("- ").append(f).append('\n');
            }
            write("FILES.md", b.toString());
        }
    }

    /** The newest job that is still open, for the carry-on prompt. Empty when there is none. */
    public static String pending(File workspace) {
        if (workspace == null) {
            return "";
        }
        TaskStateRepository tasks = new TaskStateRepository(new File(workspace, "memory/tasks"));
        for (TaskRecord r : tasks.all()) {
            if (r.state != null && r.state.open()) {
                return r.oneLine();
            }
        }
        return "";
    }

    /** A missing claim goes back on the list. It is not ticked off. */
    private void noteMissing(Reconcile check) {
        if (current == null || check == null) {
            return;
        }
        for (String f : check.missing) {
            String step = "Write " + f + " — it was claimed but it is not on disk";
            if (!current.remaining.contains(step)) {
                current.remaining.add(step);
            }
        }
    }

    private String manifest(String persona) {
        return "{\n  \"id\": \"" + current.id + "\",\n  \"persona\": \"" + json(current.kind) + "\",\n  \"agent\": \"scribe\",\n"
                + "  \"instruction\": \"" + json(clip(current.instruction, 500)) + "\",\n  \"state\": \"" + current.state + "\"\n}\n";
    }

    /** One line of the project list. {@code next} is the first step still outstanding. */
    public static final class Job {
        public final String id;
        public final String title;
        public final String state;
        public final String next;
        public final boolean open;
        /** Steps done and still to do, and when it last changed. */
        public final int done, left;
        public final long updated;

        Job(String id, String title, String state, String next, boolean open, int done, int left, long updated) {
            this.id = id;
            this.title = title;
            this.state = state;
            this.next = next;
            this.open = open;
            this.done = done;
            this.left = left;
            this.updated = updated;
        }
    }

    /** A title for people: pasted attachments and tags out, one line. */
    public static String readable(String text) {
        String t = text == null ? "" : text.replaceAll("(?is)<attachment[^>]*>.*?(</attachment>|$)", " ")
                .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? "Untitled task" : t;
    }

    /** The pilot closed a task that won't be resumed: it is kept as cancelled. True when it was there and open. */
    public static boolean close(File workspace, String id) {
        TaskStateRepository tasks = new TaskStateRepository(new File(workspace, "memory/tasks"));
        TaskRecord r = tasks.load(id);
        if (r == null || r.state == null || !r.state.open()) {
            return false;
        }
        r.state = TaskState.CANCELLED;
        r.cancelStatus = "closed by you";
        r.updatedAt = System.currentTimeMillis();
        tasks.save(r);
        return true;
    }

    /** Open jobs first, then the latest closed ones. At most eight. */
    public static List<Job> jobs(File workspace) {
        List<Job> out = new ArrayList<Job>();
        if (workspace == null) {
            return out;
        }
        TaskStateRepository tasks = new TaskStateRepository(new File(workspace, "memory/tasks"));
        List<TaskRecord> open = new ArrayList<TaskRecord>();
        List<TaskRecord> closed = new ArrayList<TaskRecord>();
        for (TaskRecord r : tasks.all()) {
            if (r.state != null && r.state.open()) {
                open.add(r);
            } else {
                closed.add(r);
            }
        }
        for (TaskRecord r : open) {
            out.add(job(r));
        }
        for (TaskRecord r : closed) {
            if (out.size() >= 8) {
                break;
            }
            out.add(job(r));
        }
        return out;
    }

    private static Job job(TaskRecord r) {
        String title = readable(r.description == null || r.description.isEmpty() ? r.instruction : r.description);
        String state = r.state == null ? "unknown" : r.state.name().toLowerCase(Locale.US).replace('_', ' ');
        String next = r.remaining.isEmpty() ? "Nothing outstanding in the record." : readable(r.remaining.get(0));
        return new Job(r.id, clip(title, 120), state, clip(next, 160), r.state != null && r.state.open(), r.completed.size(),
                r.remaining.size(), r.updatedAt > 0 ? r.updatedAt : r.createdAt);
    }

    private String execution() {
        return "{\n  \"id\": \"" + current.id + "\",\n  \"state\": \"" + current.state + "\",\n  \"updated\": " + current.updatedAt + "\n}\n";
    }

    private String next() {
        StringBuilder b = new StringBuilder("# Next\n\n");
        if (current.remaining.isEmpty()) {
            b.append("Nothing outstanding in the record.\n");
        }
        int i = 1;
        for (String step : current.remaining) {
            b.append(i++).append(". ").append(step).append('\n');
        }
        b.append("\nState: ").append(current.state).append('\n');
        return b.toString();
    }

    private void append(String name, String text) {
        File f = new File(projectDir, name);
        String prior = "";
        if (f.isFile()) {
            try {
                prior = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                prior = "";
            }
        }
        write(name, prior + text);
    }

    private void write(String name, String text) {
        if (projectDir == null) {
            return;
        }
        File f = new File(projectDir, name);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File tmp = new File(parent, f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
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

    private static String slug(String text) {
        String s = text == null ? "job" : text.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            s = "job";
        }
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    private static String clip(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    static String redact(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("ghp_[A-Za-z0-9]+", "ghp_…")
                .replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-…")
                .replaceAll("(?i)bearer [A-Za-z0-9._\\-]{8,}", "Bearer …");
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
