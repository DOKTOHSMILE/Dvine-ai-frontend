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
        long now = System.currentTimeMillis();
        current = new TaskRecord();
        current.id = "job-" + now;
        current.instruction = redact(instruction);
        current.description = clip(current.instruction, 160);
        current.kind = persona == null ? "ibiko" : persona;
        current.state = TaskState.QUEUED;
        current.createdAt = now;
        current.updatedAt = now;
        current.remaining.add("Carry out the instruction");
        current.remaining.add("Verify the result against the files");
        tasks.save(current);
        projectDir = new File(workspace, "memory/projects/" + slug(current.description));
        new File(projectDir, "checkpoints").mkdirs();
        write("PROJECT_MANIFEST.json", manifest(persona));
        write("PROJECT_PLAN.md", plan());
        write("PROJECT_MAP.md", map());
        write("PROGRESS.md", "# Progress\n\n- Planning. The instruction is recorded. Nothing is marked done yet.\n");
        write("DECISIONS.md", "# Decisions\n\n- The job is tracked by Scribe, apart from the chat.\n");
        write("ERRORS.md", "# Errors\n\n");
        write("NEXT_ACTIONS.md", "# Next\n\n1. Carry out the instruction.\n2. Check the files that changed.\n");
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
     * Closes the round. Completed only when the run was not stopped and the reply does not ask to continue.
     * A reply that merely says the work is done is not enough on its own when nothing changed and it asks to go on.
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
        if (userStopped || asksContinue || failed) {
            current.state = TaskState.PAUSED;
            if (!current.remaining.contains("Resume from the last checkpoint")) {
                current.remaining.add("Resume from the last checkpoint");
            }
        } else if (!files.isEmpty() || shown.length() > 200) {
            current.state = TaskState.VERIFYING;
            current.completed.add("A result was written");
            current.remaining.remove("Carry out the instruction");
            current.state = TaskState.COMPLETED;
            current.completed.add("Files or a full result were checked in");
        } else {
            current.state = TaskState.PAUSED;
            current.lastError = "The reply was too thin to count as finished.";
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

    private String manifest(String persona) {
        return "{\n  \"id\": \"" + current.id + "\",\n  \"persona\": \"" + json(current.kind) + "\",\n  \"agent\": \"scribe\",\n"
                + "  \"instruction\": \"" + json(clip(current.instruction, 500)) + "\",\n  \"state\": \"" + current.state + "\"\n}\n";
    }

    private String plan() {
        return "# Plan\n\n## Instruction\n\n" + current.instruction + "\n\n## Steps\n\n"
                + "1. Read what is already in the workspace. Do not overwrite a project blindly.\n"
                + "2. Do the instruction in small steps.\n"
                + "3. Check the result (files changed, or a command that exited cleanly).\n"
                + "4. Scribe records each checkpoint. A claim in chat is not completion.\n";
    }

    private String map() {
        StringBuilder b = new StringBuilder("# Workspace map\n\n");
        File[] kids = workspace.listFiles();
        if (kids == null || kids.length == 0) {
            b.append("(empty)\n");
            return b.toString();
        }
        java.util.Arrays.sort(kids);
        int n = 0;
        for (File k : kids) {
            if (k.getName().equals("memory") || n >= 80) {
                continue;
            }
            b.append("- ").append(k.getName()).append(k.isDirectory() ? "/" : "").append('\n');
            n++;
        }
        return b.toString();
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
