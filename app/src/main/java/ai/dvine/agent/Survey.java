package ai.dvine.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The Blueprint's first phase, done by several workers at once: the files the task touches (and the ones that do
 * something similar) are found, each worker reads its files line by line with their line numbers, and writes down
 * the key points: what the file is for, its parts with their lines, what it connects to, what of it the task needs
 * and what can be reused. The notes are kept in {@code .blueprint/notes/}, joined into {@code .blueprint/MAP.md},
 * and handed to the lead, which writes the plan from the full picture instead of guessing. Pure Java.
 */
public final class Survey {
    /** A model call that is not shown in the chat (a worker's own reading). */
    public interface Reader {
        String read(String system, String text);
    }

    /** The most files read, the longest piece one worker reads at a time, and the most pieces in all. */
    static final int MOST_FILES = 24, PIECE_LINES = 400, MOST_PIECES = 36;
    static final long FILE_BYTES = 400_000;

    static final String SYSTEM = "You are one of several readers surveying a codebase before a change is planned. Read"
            + " the file below line by line (each line starts with its number). Write the key points, short and exact, in"
            + " this form and nothing else:\n"
            + "Purpose: one line.\n"
            + "Parts: one line each, 'name (line N): what it does'.\n"
            + "Connects to: the files, classes or screens it uses or is used by.\n"
            + "For the task: what of this file the task needs, changes or must not break; 'nothing' if it doesn't.\n"
            + "Reuse: what already here could be used instead of writing new code; 'nothing' if nothing.\n"
            + "Do not invent anything that is not in the lines.";

    private static final Set<String> SOURCE = new HashSet<String>(java.util.Arrays.asList("java", "kt", "kts", "gradle", "xml",
            "py", "js", "ts", "tsx", "jsx", "go", "rs", "c", "cc", "cpp", "h", "hpp", "swift", "rb", "php", "cs", "sh", "md",
            "json", "yml", "yaml", "toml", "html", "css", "sql", "dart", "vue", "svelte"));
    private static final Set<String> SKIP_DIRS = new HashSet<String>(java.util.Arrays.asList(".git", "build", "node_modules", ".gradle",
            ".idea", "dist", "out", "target", "__pycache__", ".blueprint", "third_party", "vendor", "llama.cpp"));
    private static final Set<String> COMMON = new HashSet<String>(java.util.Arrays.asList("the", "and", "for", "with", "that",
            "this", "from", "into", "make", "build", "create", "add", "fix", "please", "should", "would", "could", "when",
            "then", "also", "have", "what", "need", "want", "like", "app", "code", "file", "files", "using", "use", "new"));

    /** The Scribe's own files in a project folder: never surveyed. */
    private static final Set<String> NOTES = new HashSet<String>(java.util.Arrays.asList("NEXT_ACTIONS.md", "DECISIONS.md",
            "ERRORS.md", "EXECUTION.json", "PROJECT_MANIFEST.json", "PROGRESS.md", "PROJECT_MAP.md", "PROJECT_PLAN.md", "FILES.md",
            "RECOVERY.md", "RECORD.md", "PLAN.md", "MAP.md"));

    private Survey() {
    }

    /** One piece of a file for one worker: the file, its first and last line, and the lines numbered. */
    static final class Piece {
        final String path;
        final int from, to;
        final String text;

        Piece(String path, int from, int to, String text) {
            this.path = path;
            this.from = from;
            this.to = to;
            this.text = text;
        }
    }

    /** The words of a task worth searching for: names and long words, not common ones. */
    static List<String> keywords(String task) {
        List<String> out = new ArrayList<String>();
        for (String w : (task == null ? "" : task).split("[^A-Za-z0-9_]+")) {
            String l = w.toLowerCase(Locale.US);
            if (l.length() >= 4 && !COMMON.contains(l) && !out.contains(l) && !l.matches("https?|github|gitlab|clone|pull|repo|repository")) {
                out.add(l);
            }
        }
        return out;
    }

    /**
     * The files worth reading for this task, most relevant first: the ones whose path or text names the task's words,
     * then the project's own notes (README, PLAN, docs), at most {@link #MOST_FILES}.
     */
    static List<File> pick(File root, String task) {
        List<String> words = keywords(task);
        List<File> all = new ArrayList<File>();
        walk(root, all, 0);
        final Map<File, Integer> score = new LinkedHashMap<File, Integer>();
        for (File f : all) {
            if (NOTES.contains(f.getName())) {
                continue; // DVINE's own bookkeeping of the work, not the project's code
            }
            // the path inside the project: the project folder is named after the task, so its own name says nothing
            String path = root.toPath().relativize(f.toPath()).toString().toLowerCase(Locale.US);
            int s = 0;
            for (String w : words) {
                if (path.contains(w)) {
                    s += 5;
                }
            }
            if (f.getName().matches("(?i)README(\\..*)?|PLAN\\.md|ARCHITECTURE\\.md|CLAUDE\\.md")) {
                s += 3;
            }
            if (!words.isEmpty() && f.length() < FILE_BYTES) {
                try {
                    String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).toLowerCase(Locale.US);
                    for (String w : words) {
                        int at = 0, hits = 0;
                        while ((at = text.indexOf(w, at)) >= 0 && hits < 20) {
                            hits++;
                            at += w.length();
                        }
                        s += hits;
                    }
                } catch (IOException ignored) {
                    // unreadable: by its name only
                }
            }
            if (s > 0) {
                score.put(f, s);
            }
        }
        boolean matched = false;
        for (Map.Entry<File, Integer> e : score.entrySet()) {
            matched |= e.getValue() > 3; // more than a README's own boost: the task's words were found
        }
        if (!matched) {
            return new ArrayList<File>(); // nothing here is about the task: no survey
        }
        List<File> ranked = new ArrayList<File>(score.keySet());
        Collections.sort(ranked, (a, b) -> score.get(b) - score.get(a));
        return ranked.size() > MOST_FILES ? new ArrayList<File>(ranked.subList(0, MOST_FILES)) : ranked;
    }

    private static void walk(File dir, List<File> out, int depth) {
        File[] in = dir.listFiles();
        if (in == null || depth > 12 || out.size() > 20000) {
            return;
        }
        for (File f : in) {
            if (f.isDirectory()) {
                if (!SKIP_DIRS.contains(f.getName()) && !f.getName().startsWith(".")) {
                    walk(f, out, depth + 1);
                }
            } else {
                String n = f.getName();
                int dot = n.lastIndexOf('.');
                if (dot > 0 && SOURCE.contains(n.substring(dot + 1).toLowerCase(Locale.US)) && f.length() < FILE_BYTES) {
                    out.add(f);
                }
            }
        }
    }

    /** The files cut into pieces a worker reads whole, each line with its number. */
    static List<Piece> pieces(File root, List<File> files) {
        List<Piece> out = new ArrayList<Piece>();
        for (File f : files) {
            List<String> lines;
            try {
                lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                continue;
            }
            String rel = root.toPath().relativize(f.toPath()).toString();
            for (int from = 0; from < Math.max(1, lines.size()) && out.size() < MOST_PIECES; from += PIECE_LINES) {
                int to = Math.min(lines.size(), from + PIECE_LINES);
                StringBuilder b = new StringBuilder();
                for (int i = from; i < to; i++) {
                    b.append(i + 1).append(": ").append(lines.get(i)).append('\n');
                }
                out.add(new Piece(rel, from + 1, to, b.toString()));
            }
        }
        return out;
    }

    /**
     * Surveys the code under {@code root} for {@code task}: {@code workers} read at once. The notes are written under
     * {@code root/.blueprint}; the map (every file's key points) is returned, or null when nothing was relevant.
     * {@code step} hears each file as it is read.
     */
    public static String run(File root, String task, Reader reader, int workers, Consumer<String> step) {
        return run(root, new File(root, ".blueprint"), task, reader, workers, step);
    }

    /** As {@link #run(File, String, Reader, int, Consumer)}, the notes kept in {@code keep} (outside a clone, so never pushed). */
    public static String run(File root, File keep, String task, Reader reader, int workers, Consumer<String> step) {
        List<File> files = pick(root, task);
        if (files.isEmpty()) {
            return null;
        }
        List<Piece> pieces = pieces(root, files);
        if (pieces.isEmpty()) {
            return null;
        }
        int n = Math.max(1, Math.min(workers, pieces.size()));
        if (step != null) {
            step.accept("Surveying the codebase: " + files.size() + (files.size() == 1 ? " file" : " files") + ", " + n
                    + (n == 1 ? " reader" : " readers at once"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "dvine-survey");
            t.setDaemon(true);
            return t;
        });
        Map<Piece, Future<String>> jobs = new LinkedHashMap<Piece, Future<String>>();
        for (final Piece p : pieces) {
            jobs.put(p, pool.submit(() -> {
                String code = AgentBoard.start("Reader", p.path + " (lines " + p.from + "–" + p.to + ")");
                try {
                    AgentBoard.step(code, "Reading " + p.path);
                    String said = reader.read(SYSTEM, "Task: " + task.trim() + "\n\nFile: " + p.path + " (lines " + p.from
                            + "–" + p.to + ")\n" + p.text);
                    AgentBoard.end(code, said == null || said.trim().isEmpty() ? AgentBoard.Outcome.FAILED : AgentBoard.Outcome.DONE);
                    if (step != null) {
                        step.accept("Read " + p.path + (p.from > 1 || p.to > PIECE_LINES ? " (lines " + p.from + "–" + p.to + ")" : ""));
                    }
                    return said == null ? "" : said.trim();
                } catch (RuntimeException e) {
                    AgentBoard.end(code, AgentBoard.Outcome.FAILED);
                    return "";
                }
            }));
        }
        pool.shutdown();
        StringBuilder map = new StringBuilder("# Codebase map\n\nTask: ").append(task.trim()).append("\n\n");
        Map<String, StringBuilder> byFile = new LinkedHashMap<String, StringBuilder>();
        int read = 0;
        for (Map.Entry<Piece, Future<String>> e : jobs.entrySet()) {
            String said;
            try {
                said = e.getValue().get(10, TimeUnit.MINUTES);
            } catch (Exception failed) {
                said = "";
            }
            if (said.isEmpty()) {
                continue;
            }
            read++;
            Piece p = e.getKey();
            StringBuilder b = byFile.get(p.path);
            if (b == null) {
                b = new StringBuilder();
                byFile.put(p.path, b);
            }
            b.append(p.from > 1 || p.to > PIECE_LINES ? "(lines " + p.from + "–" + p.to + ")\n" : "").append(said).append("\n");
        }
        pool.shutdownNow();
        if (read == 0) {
            return null;
        }
        File notes = new File(keep, "notes");
        notes.mkdirs();
        for (Map.Entry<String, StringBuilder> e : byFile.entrySet()) {
            map.append("## ").append(e.getKey()).append("\n").append(e.getValue()).append("\n");
            try {
                Files.write(new File(notes, e.getKey().replaceAll("[\\\\/]", "__") + ".md").toPath(),
                        ("# " + e.getKey() + "\n\n" + e.getValue()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the map still has it
            }
        }
        try {
            Files.write(new File(keep, "MAP.md").toPath(), map.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // returned all the same
        }
        if (step != null) {
            step.accept("Codebase map ready: " + byFile.size() + (byFile.size() == 1 ? " file" : " files") + " documented");
        }
        return map.toString();
    }
}
