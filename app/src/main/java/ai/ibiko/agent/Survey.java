package ai.ibiko.agent;

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

    /** The most files the keyword picker returns on its own. A survey that runs reads the whole tree, not this many. */
    static final int MOST_FILES = 24, PIECE_LINES = 400;
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
                continue; // Ibiko's own bookkeeping of the work, not the project's code
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

    /** "2026-11-10 21:14:03": when a part was read, so the report is in time order. */
    static String stamp(long ms) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date(ms));
    }

    /** A part's kept reading is named for its file, its lines and a hash of those lines: changed lines are read again. */
    static File keptFile(File dir, Piece p) {
        return new File(dir, p.path.replaceAll("[\\\\/]", "__") + "@" + p.from + "-" + p.to + "-"
                + Integer.toHexString(p.text.hashCode()) + ".md");
    }

    static String readKept(File dir, Piece p) {
        File f = keptFile(dir, p);
        try {
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void writeKept(File dir, Piece p, String note) {
        try {
            Files.write(keptFile(dir, p).toPath(), note.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // read again next time
        }
    }

    /** Whether a reading kept in {@code keep} broke off with parts still to read. */
    public static boolean unfinished(File keep) {
        File f = new File(keep, "SURVEY.md");
        try {
            String s = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
            return s.contains("## Still to read") && !s.contains("- nothing: every part is read");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Where a task stood, from its blueprint folder: SURVEY.md (what was read, the last part and when, what is still to
     * read) and the start of MAP.md (what was found). Empty when the folder holds neither.
     */
    public static String whereItStood(File keep) {
        StringBuilder b = new StringBuilder();
        for (String name : new String[] {"SURVEY.md", "MAP.md"}) {
            File f = new File(keep, name);
            if (!f.isFile()) {
                continue;
            }
            try {
                String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                if ("MAP.md".equals(name) && s.length() > 6000) {
                    s = s.substring(0, 6000) + "\n… (the rest is in " + name + ")";
                }
                b.append(b.length() == 0 ? "" : "\n\n").append("[").append(name).append("]\n").append(s);
            } catch (IOException ignored) {
                // that one can't be read: the other still says where things stood
            }
        }
        return b.toString();
    }

    /**
     * SURVEY.md: where the reading stands, rewritten as each part finishes: how many are read, the last one and when,
     * and the parts still to read in order. A run that breaks off carries on from here.
     */
    static synchronized void progress(File keep, String task, List<Piece> all, java.util.Set<Piece> done, Piece last) {
        StringBuilder b = new StringBuilder("# Survey progress\n\nTask: ").append(task.trim()).append("\nUpdated: ")
                .append(stamp(System.currentTimeMillis())).append("\nRead: ").append(done.size()).append(" of ").append(all.size())
                .append(" parts\n");
        if (last != null) {
            b.append("Last read: ").append(last.path).append(" (lines ").append(last.from).append("–").append(last.to).append(")\n");
        }
        b.append("\n## Still to read\n\n");
        int left = 0;
        for (Piece p : all) {
            if (!done.contains(p)) {
                b.append("- ").append(p.path).append(" (lines ").append(p.from).append("–").append(p.to).append(")\n");
                left++;
            }
        }
        if (left == 0) {
            b.append("- nothing: every part is read\n");
        }
        try {
            keep.mkdirs();
            Files.write(new File(keep, "SURVEY.md").toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the kept parts still say what was read
        }
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

    /** The files cut into pieces a worker reads whole, from the top of each file, each line with its number. */
    static List<Piece> pieces(File root, List<File> files) {
        List<Piece> out = new ArrayList<Piece>();
        for (File f : files) {
            out.addAll(piecesOf(root, f));
        }
        return out;
    }

    /** One file, from line 1 to the end, in order. Never a slice taken from the middle first. */
    static List<Piece> piecesOf(File root, File f) {
        List<Piece> out = new ArrayList<Piece>();
        List<String> lines;
        try {
            lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return out;
        }
        String rel = root.toPath().relativize(f.toPath()).toString();
        for (int from = 0; from < Math.max(1, lines.size()); from += PIECE_LINES) {
            int to = Math.min(lines.size(), from + PIECE_LINES);
            StringBuilder b = new StringBuilder();
            for (int i = from; i < to; i++) {
                b.append(i + 1).append(": ").append(lines.get(i)).append('\n');
            }
            out.add(new Piece(rel, from + 1, to, b.toString()));
        }
        return out;
    }

    /**
     * Every source file, in path order, when the task matches something here. Nothing when it matches nothing
     * (a survey is not started). The keyword picker still names what to read first; the rest of the tree follows.
     */
    static List<File> tree(File root, String task) {
        List<File> first = pick(root, task);
        if (first.isEmpty()) {
            return first;
        }
        java.util.Set<File> seen = new java.util.LinkedHashSet<File>(first);
        List<File> all = new ArrayList<File>();
        walk(root, all, 0);
        for (File f : all) {
            if (!NOTES.contains(f.getName())) {
                seen.add(f);
            }
        }
        List<File> out = new ArrayList<File>(seen);
        Collections.sort(out, (a, b) -> root.toPath().relativize(a.toPath()).toString()
                .compareTo(root.toPath().relativize(b.toPath()).toString()));
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
        List<File> files = tree(root, task);
        if (files.isEmpty()) {
            return null;
        }
        List<Piece> pieces = pieces(root, files);
        if (pieces.isEmpty()) {
            return null;
        }
        final List<String> paths = new ArrayList<String>();
        final Map<String, List<Piece>> byPath = new LinkedHashMap<String, List<Piece>>();
        for (Piece p : pieces) {
            List<Piece> mine = byPath.get(p.path);
            if (mine == null) {
                mine = new ArrayList<Piece>();
                byPath.put(p.path, mine);
                paths.add(p.path);
            }
            mine.add(p);
        }
        // what was already read (an earlier run that broke off, a "continue"): kept, never read again while the lines
        // it covers are unchanged
        final File done = new File(keep, "pieces");
        done.mkdirs();
        final Map<Piece, String> already = new LinkedHashMap<Piece, String>();
        for (Piece p : pieces) {
            String kept = readKept(done, p);
            if (kept != null) {
                already.put(p, kept);
            }
        }
        int left = pieces.size() - already.size();
        int n = Math.max(1, Math.min(workers, Math.max(1, left == 0 ? 1 : paths.size())));
        if (step != null) {
            step.accept("Mapped the tree: " + files.size() + (files.size() == 1 ? " file" : " files"));
            step.accept("Surveying the codebase: " + files.size() + (files.size() == 1 ? " file" : " files") + ", " + n
                    + (n == 1 ? " reader" : " readers at once") + ", one file each from the top"
                    + (already.isEmpty() ? "" : "; " + already.size() + " of " + pieces.size() + " parts already read, carrying on from there"));
        }
        progress(keep, task, pieces, already.keySet(), null);
        final Map<Piece, String> notes = java.util.Collections.synchronizedMap(new LinkedHashMap<Piece, String>());
        notes.putAll(already);
        final java.util.Set<Piece> finished = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<Piece>(already.keySet()));
        final java.util.concurrent.atomic.AtomicInteger cursor = new java.util.concurrent.atomic.AtomicInteger();
        final Map<String, String> ready = java.util.Collections.synchronizedMap(new LinkedHashMap<String, String>());
        final int[] announced = {0};
        final Runnable flush = () -> {
            if (step == null) {
                return;
            }
            synchronized (ready) {
                while (announced[0] < paths.size() && ready.containsKey(paths.get(announced[0]))) {
                    String line = ready.get(paths.get(announced[0]));
                    announced[0]++;
                    if (line != null && !line.isEmpty()) {
                        step.accept(line);
                    }
                }
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "ibiko-survey");
            t.setDaemon(true);
            return t;
        });
        for (int w = 0; w < n; w++) {
            pool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    int i = cursor.getAndIncrement();
                    if (i >= paths.size()) {
                        return;
                    }
                    String path = paths.get(i);
                    List<Piece> mine = byPath.get(path);
                    int lastLine = mine.get(mine.size() - 1).to;
                    // Claimed in path order. Post the one badge now, in that same order, before the read — a slow
                    // file must not hide every file after it, and a finished slice must not appear as its own row.
                    synchronized (ready) {
                        ready.put(path, "Read " + path + " (lines 1–" + lastLine + ")");
                    }
                    flush.run();
                    String code = AgentBoard.start("Reader", path);
                    boolean whole = true;
                    try {
                        AgentBoard.step(code, "Reading " + path + " from the top");
                        for (Piece p : mine) {
                            String note = notes.get(p);
                            if (note == null) {
                                String said;
                                try {
                                    said = reader.read(SYSTEM, "Task: " + task.trim() + "\n\nFile: " + p.path + " (lines " + p.from
                                            + "–" + p.to + " of " + mine.get(mine.size() - 1).to + ", from the top)\n" + p.text);
                                } catch (RuntimeException e) {
                                    said = "";
                                }
                                note = said == null ? "" : said.trim();
                                if (!note.isEmpty()) {
                                    note = "Read " + stamp(System.currentTimeMillis()) + "\n" + note;
                                    writeKept(done, p, note);
                                    finished.add(p);
                                    notes.put(p, note);
                                    progress(keep, task, pieces, finished, p);
                                } else {
                                    whole = false;
                                }
                            }
                            if (note == null || note.isEmpty()) {
                                whole = false;
                            }
                        }
                        AgentBoard.end(code, whole ? AgentBoard.Outcome.DONE : AgentBoard.Outcome.FAILED);
                    } catch (RuntimeException e) {
                        AgentBoard.end(code, AgentBoard.Outcome.FAILED);
                        whole = false;
                    }
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(6, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        flush.run();
        StringBuilder map = new StringBuilder("# Codebase map\n\nTask: ").append(task.trim()).append("\nMade: ")
                .append(stamp(System.currentTimeMillis())).append("\nFiles: ").append(files.size()).append(", parts: ")
                .append(pieces.size()).append("\n\n## Tree\n\n");
        for (String path : paths) {
            map.append("- ").append(path).append(" (").append(byPath.get(path).get(byPath.get(path).size() - 1).to).append(" lines)\n");
        }
        map.append("\n");
        Map<String, StringBuilder> byFile = new LinkedHashMap<String, StringBuilder>();
        int documented = 0;
        for (String path : paths) {
            List<Piece> mine = byPath.get(path);
            boolean whole = true;
            StringBuilder b = new StringBuilder();
            for (Piece p : mine) {
                String said = notes.get(p);
                if (said == null || said.isEmpty()) {
                    whole = false;
                    continue;
                }
                b.append("### Lines " + p.from + "–" + p.to + "\n").append(said).append("\n\n");
            }
            if (b.length() > 0) {
                byFile.put(path, b);
            }
            if (whole) {
                documented++;
            }
        }
        if (byFile.isEmpty()) {
            return null;
        }
        File notesDir = new File(keep, "notes");
        notesDir.mkdirs();
        map.append("## Contents\n\n");
        for (String f : byFile.keySet()) {
            map.append("- ").append(f).append('\n');
        }
        map.append('\n');
        for (Map.Entry<String, StringBuilder> e : byFile.entrySet()) {
            map.append("## ").append(e.getKey()).append("\n").append(e.getValue()).append("\n");
            try {
                Files.write(new File(notesDir, e.getKey().replaceAll("[\\\\/]", "__") + ".md").toPath(),
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
            step.accept("Codebase map ready: " + documented + " of " + files.size()
                    + (files.size() == 1 ? " file" : " files") + " documented");
        }
        return map.toString();
    }
}
