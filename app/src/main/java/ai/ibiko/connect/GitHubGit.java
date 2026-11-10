package ai.ibiko.connect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import ai.ibiko.brain.MiniJson;

/**
 * Git with GitHub, without git: the phone has no git program, so the repository is worked through GitHub's Git Data
 * API with the GitHub connector's token. Clone writes a branch's files into a folder and keeps a small record of what
 * it got (".dvine-git.json": the repository, branch, commit, tree and each file's blob id). Status compares the
 * folder with that record the way git does (the blob id of the bytes). Pull brings in the branch's newer commit,
 * leaving files the user changed alone and reporting them. Commit and push are one step here: the changed files go
 * up as blobs, a tree and a commit, and the branch moves to it (or a new branch is made). A pull request can then
 * be opened. Pure Java; the network is behind {@link Connectors.Http}.
 */
public final class GitHubGit {
    public static final String RECORD = ".dvine-git.json";
    /** The most files a clone or pull takes (Settings › Advanced › Most files in a clone). */
    static final int MAX_FILES = 5000;
    private int maxFiles = MAX_FILES;

    /** The most files a repository may have to be cloned or pulled here. */
    public GitHubGit maxFiles(int most) {
        maxFiles = Math.max(1, most);
        return this;
    }
    static final long MAX_FILE_BYTES = 1_000_000;
    private long maxFileBytes = MAX_FILE_BYTES;

    /** The largest file a clone will copy. Larger files are skipped and named, not loaded whole. */
    public GitHubGit maxFileBytes(long most) {
        maxFileBytes = Math.max(1, most);
        return this;
    }

    private final Connectors.Http http;
    private final String token;
    private Zips zips;
    private java.util.function.Consumer<String[]> onPullRequest;

    /** Told {number, title, url, repo} of each pull request opened (for the Pull requests list). */
    public GitHubGit onPullRequest(java.util.function.Consumer<String[]> listener) {
        this.onPullRequest = listener;
        return this;
    }

    /** Downloads a file (a branch as a zip) to disk; false when it can't. */
    public interface Zips {
        boolean download(String url, Map<String, String> headers, File to) throws IOException;
    }

    /** Clone by downloading the branch as one zip (fast, any size up to 300 MB) instead of file by file. */
    public GitHubGit useZips(Zips zips) {
        this.zips = zips;
        return this;
    }
    private static final String API = "https://api.github.com";

    public GitHubGit(Connectors.Http http, String token) {
        this.http = http;
        this.token = token;
    }

    // ------------------------------------------------------------ the record kept beside the files

    static final class Record {
        String repo = "", branch = "", head = "", tree = "";
        Map<String, String> files = new TreeMap<String, String>();

        static Record read(File dir) throws IOException {
            File f = new File(dir, RECORD);
            if (!f.isFile()) {
                throw new IOException(dir.getName() + " isn't a cloned repository (clone it first)");
            }
            Map<String, Object> m = MiniJson.obj(MiniJson.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            Record r = new Record();
            r.repo = MiniJson.str(m.get("repo"), "");
            r.branch = MiniJson.str(m.get("branch"), "");
            r.head = MiniJson.str(m.get("head"), "");
            r.tree = MiniJson.str(m.get("tree"), "");
            for (Map.Entry<String, Object> e : MiniJson.obj(m.get("files")).entrySet()) {
                r.files.put(e.getKey(), MiniJson.str(e.getValue(), ""));
            }
            return r;
        }

        void write(File dir) throws IOException {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("repo", repo);
            m.put("branch", branch);
            m.put("head", head);
            m.put("tree", tree);
            m.put("files", new LinkedHashMap<String, Object>(files));
            Files.write(new File(dir, RECORD).toPath(), MiniJson.write(m).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** A branch listed from GitHub and not written into the working folder. File ids only, no file bodies. */
    static final class Fetched {
        String branch = "", head = "", tree = "";
        Map<String, String> files = new TreeMap<String, String>();

        static File file(File dir, String branch) {
            String safe = branch.replace("/", "__");
            return new File(new File(new File(dir, ".git"), "dvine-fetched"), safe + ".json");
        }

        static Fetched read(File dir, String branch) throws IOException {
            File f = file(dir, branch);
            if (!f.isFile()) {
                return null;
            }
            Map<String, Object> m = MiniJson.obj(MiniJson.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            Fetched got = new Fetched();
            got.branch = MiniJson.str(m.get("branch"), branch);
            got.head = MiniJson.str(m.get("head"), "");
            got.tree = MiniJson.str(m.get("tree"), "");
            for (Map.Entry<String, Object> e : MiniJson.obj(m.get("files")).entrySet()) {
                got.files.put(e.getKey(), MiniJson.str(e.getValue(), ""));
            }
            return got;
        }

        void write(File dir) throws IOException {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("branch", branch);
            m.put("head", head);
            m.put("tree", tree);
            m.put("files", new LinkedHashMap<String, Object>(files));
            File f = file(dir, branch);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), MiniJson.write(m).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------ commands

    /** Clones "owner/repo" (its default branch when {@code branch} is empty) into {@code dir}. */
    public String clone(String repo, String branch, File dir) throws IOException {
        repo = repoName(repo);
        if (branch == null || branch.trim().isEmpty()) {
            branch = MiniJson.str(get("/repos/" + repo).get("default_branch"), "main");
        }
        if (dir.exists() && new File(dir, RECORD).isFile()) {
            throw new IOException(dir.getName() + " is already cloned; pull to update it");
        }
        String head = headOf(repo, branch);
        if (head == null) {
            throw new IOException("the branch " + branch + " isn't in " + repo);
        }
        Record r = new Record();
        r.repo = repo;
        r.branch = branch;
        r.head = head;
        r.tree = treeOf(repo, head);
        dir.mkdirs();
        GitOp.open(dir, "clone of " + repo);
        try {
            String done = cloneInto(repo, branch, dir, r);
            GitOp.close(dir, "FINISHED", done);
            return done;
        } catch (IOException | RuntimeException e) {
            GitOp.close(dir, "FAILED", e.getMessage());
            throw e;
        }
    }

    private String cloneInto(String repo, String branch, File dir, Record r) throws IOException {
        if (zips != null) {
            File zip = new File(dir.getParentFile(), "." + dir.getName() + ".zip");
            try {
                if (zips.download(API + "/repos/" + repo + "/zipball/" + r.head, headers(), zip)) {
                    int n = unzip(zip, dir, r);
                    r.write(dir);
                    return "Cloned " + repo + " (" + branch + ", " + n + " files) into " + dir.getName()
                            + "/. It is on the device now: read, edit and push it any time.";
                }
            } finally {
                zip.delete();
            }
        }
        Map<String, String> remote = files(repo, r.tree);
        int skipped = 0;
        for (Map.Entry<String, String> e : remote.entrySet()) {
            byte[] bytes = blob(repo, e.getValue());
            if (bytes == null) {
                skipped++;
                continue;
            }
            File f = inside(dir, e.getKey());
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), bytes);
            base(dir, e.getKey(), bytes);
            r.files.put(e.getKey(), e.getValue());
        }
        r.write(dir);
        return "Cloned " + repo + " (" + branch + ", " + r.files.size() + " files" + (skipped > 0 ? ", " + skipped + " too big to fetch" : "")
                + ") into " + dir.getName() + "/.";
    }

    /** What changed in the folder since the clone or the last pull or push. */
    public String status(File dir) throws IOException {
        String warn = GitOp.pending(dir);
        Record r = Record.read(dir);
        Changes c = changes(dir, r);
        String body = c.isEmpty()
                ? "On " + r.branch + " of " + r.repo + ": nothing changed."
                : "On " + r.branch + " of " + r.repo + ":\n" + c.describe();
        return warn == null ? body : warn + "\n" + body;
    }

    /** The changed lines, capped, so a push can be checked before it is sent. */
    public static String diffText(File dir) {
        List<FileDiff> diffs = diff(dir);
        if (diffs.isEmpty()) {
            return "No line changes.";
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (FileDiff f : diffs) {
            if (shown >= 20) {
                out.append("…and ").append(diffs.size() - 20).append(" more files\n");
                break;
            }
            shown++;
            out.append(f.kind).append(" ").append(f.path);
            if (f.diff != null) {
                out.append(" (+").append(f.diff.added).append(" -").append(f.diff.removed).append(")");
            }
            out.append("\n");
            int lines = 0;
            if (f.diff != null && f.diff.lines != null) {
                for (String line : f.diff.lines) {
                    if (lines++ >= 30) {
                        out.append("  …\n");
                        break;
                    }
                    out.append(line).append("\n");
                }
            }
        }
        return out.toString().trim();
    }

    /** Recent commits on the folder's branch. This does not download the files. */
    public String log(File dir) throws IOException {
        Record r = Record.read(dir);
        Connectors.Reply reply = check(http.send("GET",
                API + "/repos/" + r.repo + "/commits?sha=" + r.branch + "&per_page=15", headers(), null));
        List<Object> list = MiniJson.list(MiniJson.parse(reply.body));
        if (list.isEmpty()) {
            return "No commits on " + r.branch + ".";
        }
        StringBuilder out = new StringBuilder("Recent commits on " + r.branch + " of " + r.repo + ":");
        int n = 0;
        for (Object o : list) {
            if (n++ >= 15) {
                break;
            }
            Map<String, Object> c = MiniJson.obj(o);
            String sha = MiniJson.str(c.get("sha"), "");
            String msg = MiniJson.str(MiniJson.obj(c.get("commit")).get("message"), "");
            String first = msg.split("\n", 2)[0].trim();
            if (first.length() > 120) {
                first = first.substring(0, 120);
            }
            out.append("\n").append(sha.length() > 7 ? sha.substring(0, 7) : sha).append(" ").append(first);
        }
        return out.toString();
    }

    /** Branch names in the repository. The folder's current branch is marked. */
    public String branches(File dir) throws IOException {
        Record r = Record.read(dir);
        Connectors.Reply reply = check(http.send("GET",
                API + "/repos/" + r.repo + "/branches?per_page=100", headers(), null));
        List<Object> list = MiniJson.list(MiniJson.parse(reply.body));
        if (list.isEmpty()) {
            return "No branches listed for " + r.repo + ".";
        }
        StringBuilder out = new StringBuilder("Branches in " + r.repo + ":");
        int n = 0;
        for (Object o : list) {
            if (n++ >= 100) {
                break;
            }
            String name = MiniJson.str(MiniJson.obj(o).get("name"), "");
            if (name.isEmpty()) {
                continue;
            }
            out.append("\n").append(name.equals(r.branch) ? "* " : "  ").append(name);
        }
        return out.toString();
    }

    /** Brings in the branch's newer commit; files the user changed are kept and listed (the other side is saved as "name.theirs"). */
    public String pull(File dir) throws IOException {
        Record r = Record.read(dir);
        GitOp.open(dir, "pull of " + r.branch);
        try {
            String done = pullInto(dir, r);
            GitOp.close(dir, "FINISHED", done);
            return done;
        } catch (IOException | RuntimeException e) {
            GitOp.close(dir, "FAILED", e.getMessage());
            throw e;
        }
    }

    private String pullInto(File dir, Record r) throws IOException {
        String head = headOf(r.repo, r.branch);
        if (head == null) {
            throw new IOException("the branch " + r.branch + " is gone from " + r.repo);
        }
        if (head.equals(r.head)) {
            return "Already up to date with " + r.branch + ".";
        }
        Changes mine = changes(dir, r);
        String tree = treeOf(r.repo, head);
        Map<String, String> remote = files(r.repo, tree);
        List<String> updated = new ArrayList<String>(), conflicts = new ArrayList<String>();
        Map<String, String> next = new TreeMap<String, String>();
        for (Map.Entry<String, String> e : remote.entrySet()) {
            String path = e.getKey(), sha = e.getValue();
            String was = r.files.get(path);
            if (sha.equals(was)) {
                next.put(path, sha); // unchanged upstream
                continue;
            }
            byte[] bytes = blob(r.repo, sha);
            if (bytes == null) {
                continue;
            }
            File f = inside(dir, path);
            base(dir, path, bytes); // the diff is now against GitHub's version
            if (mine.touched(path)) {
                Files.write(inside(dir, path + ".theirs").toPath(), bytes);
                conflicts.add(path);
                next.put(path, sha); // the user's version stays, and will replace theirs on push
                continue;
            }
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), bytes);
            next.put(path, sha);
            updated.add(path);
        }
        for (String path : r.files.keySet()) {
            if (!remote.containsKey(path)) {
                if (mine.touched(path)) {
                    conflicts.add(path + " (deleted upstream)");
                } else {
                    inside(dir, path).delete();
                    base(dir, path, null);
                    updated.add(path + " (deleted)");
                }
            }
        }
        r.head = head;
        r.tree = tree;
        r.files = next;
        r.write(dir);
        StringBuilder out = new StringBuilder("Pulled " + r.branch + ": " + updated.size() + " files updated.");
        if (!updated.isEmpty()) {
            out.append("\n").append(String.join("\n", cap(updated)));
        }
        if (!conflicts.isEmpty()) {
            out.append("\nConflicts (your version kept, theirs saved beside it as .theirs; merge them, delete the .theirs files, then push):\n")
                    .append(String.join("\n", conflicts));
        }
        return out.toString();
    }

    /**
     * Lists a branch (main when {@code branch} is empty) without writing those files into the folder. The phone
     * keeps the file ids under .git and downloads a body only later, if a merge needs that one file.
     */
    public String fetch(File dir, String branch) throws IOException {
        Record r = Record.read(dir);
        String name = cleanBranch(branch);
        GitOp.open(dir, "fetch of " + name);
        try {
            Fetched got = fetchInto(dir, r, name);
            String done = describeFetch(r, got);
            GitOp.close(dir, "FINISHED", done);
            return done;
        } catch (IOException | RuntimeException e) {
            GitOp.close(dir, "FAILED", e.getMessage());
            throw e;
        }
    }

    /**
     * Merges a branch into the folder on the phone. The same branch is a pull. Another branch (main, into a feature
     * branch) is a three-way merge: edits that do not overlap are combined, and overlapping edits stay in the file
     * as conflict markers with a .theirs copy. The branch pointer is not moved, so the result is a local change to
     * push. File bodies are downloaded only for files the merge has to write.
     */
    public String merge(File dir, String branch) throws IOException {
        Record r = Record.read(dir);
        String name = cleanBranch(branch);
        GitOp.open(dir, "merge of " + name);
        try {
            String done = mergeInto(dir, r, name);
            GitOp.close(dir, "FINISHED", done);
            return done;
        } catch (IOException | RuntimeException e) {
            GitOp.close(dir, "FAILED", e.getMessage());
            throw e;
        }
    }

    private Fetched fetchInto(File dir, Record r, String name) throws IOException {
        String head = headOf(r.repo, name);
        if (head == null && "main".equals(name)) {
            String def = MiniJson.str(get("/repos/" + r.repo).get("default_branch"), "");
            if (!def.isEmpty() && !def.equals(name)) {
                String alt = headOf(r.repo, def);
                if (alt != null) {
                    name = def;
                    head = alt;
                }
            }
        }
        if (head == null) {
            throw new IOException("the branch " + name + " isn't in " + r.repo);
        }
        Fetched f = new Fetched();
        f.branch = name;
        f.head = head;
        f.tree = treeOf(r.repo, head);
        f.files = files(r.repo, f.tree);
        f.write(dir);
        return f;
    }

    private static String describeFetch(Record r, Fetched got) {
        String where = shortSha(got.head);
        if (got.branch.equals(r.branch) && got.head.equals(r.head)) {
            return "Fetched " + got.branch + " (" + where + "). Already the commit in this folder. No files were downloaded.";
        }
        if (got.head.equals(r.head)) {
            return "Fetched " + got.branch + " (" + where + "). It is the same commit as " + r.branch
                    + ". No files were downloaded.";
        }
        return "Fetched " + got.branch + " (" + where + ", " + got.files.size() + " files listed) into the phone's index."
                + " The folder was not changed. Merge to bring " + got.branch + " into " + r.branch + ".";
    }

    private String mergeInto(File dir, Record r, String requested) throws IOException {
        Fetched got = fetchInto(dir, r, requested);
        if (got.branch.equals(r.branch)) {
            return pullInto(dir, r);
        }
        if (got.head.equals(r.head)) {
            return "Fetched " + got.branch + " (" + shortSha(got.head) + "). It is the same commit as " + r.branch
                    + ". Nothing to merge.";
        }
        String baseCommit = mergeBase(r.repo, r.head, got.head);
        Map<String, String> baseFiles;
        if (baseCommit == null) {
            baseFiles = new TreeMap<String, String>();
        } else if (baseCommit.equals(r.head)) {
            baseFiles = r.files;
        } else if (baseCommit.equals(got.head)) {
            baseFiles = got.files;
        } else {
            baseFiles = files(r.repo, treeOf(r.repo, baseCommit));
        }
        Changes mine = changes(dir, r);
        Set<String> paths = new java.util.TreeSet<String>();
        paths.addAll(r.files.keySet());
        paths.addAll(got.files.keySet());
        List<String> took = new ArrayList<String>();
        List<String> merged = new ArrayList<String>();
        List<String> conflicts = new ArrayList<String>();
        StringBuilder pieces = new StringBuilder();
        Map<String, byte[]> bodies = new HashMap<String, byte[]>();
        for (String path : paths) {
            if (path.endsWith(".theirs")) {
                continue;
            }
            String baseSha = baseFiles.get(path);
            String ourSha = r.files.get(path);
            String theirSha = got.files.get(path);
            boolean gone = mine.deleted.contains(path);
            byte[] working = null;
            if (!gone) {
                File onDisk = inside(dir, path);
                if (onDisk.isFile()) {
                    working = Files.readAllBytes(onDisk.toPath());
                }
            }
            String eff = gone || working == null ? null : mine.touched(path) ? blobSha(working) : ourSha;
            if (sameSha(eff, theirSha)) {
                quietTheirs(dir, path);
                continue;
            }
            if (sameSha(theirSha, baseSha)) {
                continue;
            }
            if (sameSha(eff, baseSha)) {
                if (theirSha == null) {
                    if (ourSha != null && !gone) {
                        inside(dir, path).delete();
                        quietTheirs(dir, path);
                        took.add(path + " (deleted)");
                    }
                } else {
                    byte[] bytes = body(r.repo, theirSha, bodies);
                    if (bytes == null) {
                        continue;
                    }
                    writeFile(dir, path, bytes);
                    quietTheirs(dir, path);
                    took.add(path);
                }
                continue;
            }
            byte[] ourBytes = working;
            byte[] theirBytes = theirSha == null ? null : body(r.repo, theirSha, bodies);
            byte[] baseBytes = baseSha == null ? new byte[0] : body(r.repo, baseSha, bodies);
            if (theirBytes == null) {
                conflicts.add(path + " (deleted on " + got.branch + ")");
                continue;
            }
            if (ourBytes == null) {
                writeFile(dir, path + ".theirs", theirBytes);
                conflicts.add(path + " (you deleted it, " + got.branch + " changed it)");
                continue;
            }
            if (baseBytes == null || binary(ourBytes) || binary(theirBytes) || binary(baseBytes)
                    || ourBytes.length > 200_000 || theirBytes.length > 200_000) {
                writeFile(dir, path + ".theirs", theirBytes);
                conflicts.add(path + " (not merged as text)");
                continue;
            }
            LineMerge.Result m = LineMerge.of(new String(baseBytes, StandardCharsets.UTF_8),
                    new String(ourBytes, StandardCharsets.UTF_8), new String(theirBytes, StandardCharsets.UTF_8),
                    "yours", got.branch);
            writeFile(dir, path, m.text.getBytes(StandardCharsets.UTF_8));
            if (m.clean()) {
                quietTheirs(dir, path);
                merged.add(path);
            } else {
                writeFile(dir, path + ".theirs", theirBytes);
                conflicts.add(path);
                if (pieces.length() < 1800) {
                    pieces.append("\n").append(path).append(":\n").append(excerpt(m.text));
                }
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("Merged ").append(got.branch).append(" (").append(shortSha(got.head)).append(") into ")
                .append(r.branch).append(" on the phone. ");
        out.append(took.size()).append(" file").append(took.size() == 1 ? "" : "s").append(" taken from ").append(got.branch);
        out.append(", ").append(merged.size()).append(" merged");
        out.append(", ").append(conflicts.size()).append(" conflict").append(conflicts.size() == 1 ? "" : "s").append(".");
        if (baseCommit == null) {
            out.append(" No shared commit was found in the recent history, so every disagreement is a conflict.");
        }
        out.append(" The repository stayed on the device.");
        if (!took.isEmpty()) {
            out.append("\nFrom ").append(got.branch).append(":\n").append(String.join("\n", cap(took)));
        }
        if (!merged.isEmpty()) {
            out.append("\nMerged:\n").append(String.join("\n", cap(merged)));
        }
        if (!conflicts.isEmpty()) {
            out.append("\nConflicts (your version is under <<<<<<< yours, theirs under >>>>>>> ")
                    .append(got.branch).append("; a .theirs copy sits beside the file). Edit the file, delete the .theirs file, then push:\n")
                    .append(String.join("\n", conflicts));
            if (pieces.length() > 0) {
                out.append("\n").append(pieces);
            }
        } else {
            out.append("\nNothing overlapped. Check git status, then push.");
        }
        return out.toString();
    }

    private static void quietTheirs(File dir, String path) throws IOException {
        File side = inside(dir, path + ".theirs");
        if (side.isFile()) {
            side.delete();
        }
    }

    private static void writeFile(File dir, String path, byte[] bytes) throws IOException {
        File f = inside(dir, path);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(f.toPath(), bytes);
    }

    private byte[] body(String repo, String sha, Map<String, byte[]> cache) throws IOException {
        if (cache.containsKey(sha)) {
            return cache.get(sha);
        }
        byte[] got = blob(repo, sha);
        cache.put(sha, got);
        return got;
    }

    private static boolean sameSha(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean binary(byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        int n = Math.min(bytes.length, 8000);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** The conflict regions only, so a model is not handed the whole file. */
    static String excerpt(String marked) {
        if (marked == null || marked.isEmpty()) {
            return "";
        }
        String[] lines = marked.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean on = false;
        int n = 0;
        for (String line : lines) {
            if (line.startsWith("<<<<<<< ")) {
                on = true;
            }
            if (!on) {
                continue;
            }
            if (n == 60) {
                out.append("…\n");
                break;
            }
            out.append(line).append('\n');
            n++;
            if (line.startsWith(">>>>>>> ")) {
                on = false;
            }
        }
        return out.toString();
    }

    private String mergeBase(String repo, String left, String right) throws IOException {
        if (left == null || right == null) {
            return null;
        }
        if (left.equals(right)) {
            return left;
        }
        Map<String, List<String>> cache = new HashMap<String, List<String>>();
        Set<String> seenLeft = new HashSet<String>();
        Set<String> seenRight = new HashSet<String>();
        List<String> leftWave = new ArrayList<String>();
        List<String> rightWave = new ArrayList<String>();
        leftWave.add(left);
        rightWave.add(right);
        for (int depth = 0; depth < 40; depth++) {
            if (leftWave.isEmpty() && rightWave.isEmpty()) {
                break;
            }
            String hit = expand(repo, leftWave, seenLeft, seenRight, cache);
            if (hit != null) {
                return hit;
            }
            hit = expand(repo, rightWave, seenRight, seenLeft, cache);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** Puts this wave into {@code seen} and replaces it with the parents. A sha already seen from the other side is the base. */
    private String expand(String repo, List<String> wave, Set<String> seen, Set<String> other, Map<String, List<String>> cache)
            throws IOException {
        List<String> next = new ArrayList<String>();
        for (String sha : new ArrayList<String>(wave)) {
            if (sha == null || sha.isEmpty() || !seen.add(sha)) {
                continue;
            }
            if (other.contains(sha)) {
                return sha;
            }
            next.addAll(parentsOf(repo, sha, cache));
        }
        wave.clear();
        wave.addAll(next);
        return null;
    }

    private List<String> parentsOf(String repo, String commit, Map<String, List<String>> cache) throws IOException {
        List<String> got = cache.get(commit);
        if (got != null) {
            return got;
        }
        List<String> out = new ArrayList<String>();
        for (Object o : MiniJson.list(get("/repos/" + repo + "/git/commits/" + commit).get("parents"))) {
            String sha = MiniJson.str(MiniJson.obj(o).get("sha"), "");
            if (!sha.isEmpty()) {
                out.add(sha);
            }
        }
        cache.put(commit, out);
        return out;
    }

    private static String shortSha(String sha) {
        if (sha == null) {
            return "";
        }
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private static String cleanBranch(String branch) throws IOException {
        String name = branch == null ? "" : branch.trim();
        if (name.startsWith("origin/")) {
            name = name.substring("origin/".length());
        }
        if (name.equals("origin") || name.equals("HEAD")) {
            name = "";
        }
        if (name.isEmpty()) {
            return "main";
        }
        if (!name.matches("[\\w.-]+(?:/[\\w.-]+)*")) {
            throw new IOException("that branch name isn't one I can fetch");
        }
        return name;
    }

    static boolean conflictMarkers(byte[] bytes) {
        if (bytes == null || bytes.length > 2_000_000) {
            return false;
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        return s.contains("<<<<<<< ") && s.contains("\n=======\n") && s.contains(">>>>>>> ");
    }

    /**
     * Commits the folder's changes and pushes them: to its branch, or to {@code toBranch} (made from this commit
     * when it doesn't exist yet). Refused when the branch has moved on (pull first).
     */
    public String push(File dir, String message, String toBranch) throws IOException {
        Record r = Record.read(dir);
        Changes c = changes(dir, r);
        if (c.isEmpty()) {
            return "Nothing to commit: no files changed.";
        }
        for (String p : c.added) {
            if (p.endsWith(".theirs")) {
                throw new IOException("merge and delete " + p + " before pushing");
            }
        }
        List<String> marked = new ArrayList<String>(c.added);
        marked.addAll(c.modified);
        for (String p : marked) {
            if (conflictMarkers(Files.readAllBytes(inside(dir, p).toPath()))) {
                throw new IOException("resolve the conflict markers in " + p + " and delete its .theirs file before pushing");
            }
        }
        String branch = toBranch == null || toBranch.trim().isEmpty() ? r.branch : toBranch.trim();
        GitOp.open(dir, "push to " + branch);
        try {
            String done = pushOnto(dir, r, c, message, branch);
            GitOp.close(dir, "FINISHED", done);
            return done;
        } catch (IOException | RuntimeException e) {
            GitOp.close(dir, "FAILED", e.getMessage());
            throw e;
        }
    }

    private String pushOnto(File dir, Record r, Changes c, String message, String branch) throws IOException {
        String there = headOf(r.repo, branch);
        if (there != null && !there.equals(r.head)) {
            throw new IOException(branch.equals(r.branch) ? r.branch + " has newer commits on GitHub; pull first"
                    : "the branch " + branch + " already exists with other commits; pick another name");
        }
        List<Object> entries = new ArrayList<Object>();
        Map<String, String> next = new TreeMap<String, String>(r.files);
        List<String> up = new ArrayList<String>(c.added);
        up.addAll(c.modified);
        for (String path : up) {
            byte[] bytes = Files.readAllBytes(inside(dir, path).toPath());
            Map<String, Object> b = new LinkedHashMap<String, Object>();
            b.put("content", Base64.getEncoder().encodeToString(bytes));
            b.put("encoding", "base64");
            String sha = MiniJson.str(post("/repos/" + r.repo + "/git/blobs", b).get("sha"), "");
            entries.add(entry(path, sha));
            next.put(path, sha);
            base(dir, path, bytes); // pushed: the diff starts again from here
        }
        for (String path : c.deleted) {
            entries.add(entry(path, null));
            next.remove(path);
            base(dir, path, null);
        }
        Map<String, Object> t = new LinkedHashMap<String, Object>();
        t.put("base_tree", r.tree);
        t.put("tree", entries);
        String tree = MiniJson.str(post("/repos/" + r.repo + "/git/trees", t).get("sha"), "");
        Map<String, Object> cm = new LinkedHashMap<String, Object>();
        cm.put("message", message == null || message.trim().isEmpty() ? "Update from Ibiko" : message.trim());
        cm.put("tree", tree);
        List<Object> parents = new ArrayList<Object>();
        parents.add(r.head);
        cm.put("parents", parents);
        String commit = MiniJson.str(post("/repos/" + r.repo + "/git/commits", cm).get("sha"), "");
        if (there == null) {
            Map<String, Object> ref = new LinkedHashMap<String, Object>();
            ref.put("ref", "refs/heads/" + branch);
            ref.put("sha", commit);
            post("/repos/" + r.repo + "/git/refs", ref);
        } else {
            Map<String, Object> ref = new LinkedHashMap<String, Object>();
            ref.put("sha", commit);
            ref.put("force", Boolean.FALSE);
            send("PATCH", "/repos/" + r.repo + "/git/refs/heads/" + branch, ref);
        }
        r.branch = branch;
        r.head = commit;
        r.tree = tree;
        r.files = next;
        r.write(dir);
        return "Committed and pushed " + c.count() + " file change" + (c.count() == 1 ? "" : "s") + " to " + branch + " of " + r.repo
                + " (" + commit.substring(0, Math.min(7, commit.length())) + "):\n" + c.describe();
    }

    /** Opens a pull request from the folder's branch into {@code base} (the repository's default when empty). */
    public String pullRequest(File dir, String title, String body, String base) throws IOException {
        Record r = Record.read(dir);
        if (base == null || base.trim().isEmpty()) {
            base = MiniJson.str(get("/repos/" + r.repo).get("default_branch"), "main");
        }
        if (base.equals(r.branch)) {
            throw new IOException("push to a new branch first (a pull request goes from one branch into another)");
        }
        Map<String, Object> pr = new LinkedHashMap<String, Object>();
        pr.put("title", title == null || title.trim().isEmpty() ? "Changes from Ibiko" : title.trim());
        pr.put("head", r.branch);
        pr.put("base", base.trim());
        pr.put("body", body == null ? "" : body);
        Map<String, Object> got = post("/repos/" + r.repo + "/pulls", pr);
        if (onPullRequest != null) {
            onPullRequest.accept(new String[]{String.valueOf((int) MiniJson.num(got.get("number"), 0)), MiniJson.str(pr.get("title"), ""),
                    MiniJson.str(got.get("html_url"), ""), r.repo});
        }
        return "Opened pull request #" + (int) MiniJson.num(got.get("number"), 0) + ": " + MiniJson.str(got.get("html_url"), "");
    }

    /** Unpacks GitHub's zip (its one top folder dropped) into the folder, recording each file's blob id. */
    static int unzip(File zip, File dir, Record r) throws IOException {
        int n = 0;
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(zip)))) {
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = in.getNextEntry()) != null) {
                String name = e.getName();
                int slash = name.indexOf('/');
                String path = slash < 0 ? "" : name.substring(slash + 1);
                if (e.isDirectory() || path.isEmpty()) {
                    continue;
                }
                File f = inside(dir, path);
                f.getParentFile().mkdirs();
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                int got;
                while ((got = in.read(buf)) > 0) {
                    bytes.write(buf, 0, got);
                }
                byte[] all = bytes.toByteArray();
                Files.write(f.toPath(), all);
                base(dir, path, all);
                r.files.put(path, blobSha(all));
                n++;
            }
        }
        return n;
    }

    /** The real download: https, the token, redirects followed (GitHub hands zips to codeload), 300 MB at most. */
    public static Zips httpsZips() {
        return (url, headers, to) -> {
            javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) new java.net.URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20_000);
            c.setReadTimeout(60_000);
            for (Map.Entry<String, String> h : headers.entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
            int status = c.getResponseCode();
            if (status == 401 || status == 403 || status == 404) {
                throw new IOException("GitHub said " + status + " (the token may lack access to this repository)");
            }
            if (status < 200 || status >= 300) {
                return false;
            }
            long total = 0;
            try (java.io.InputStream in = c.getInputStream(); java.io.OutputStream out = new java.io.FileOutputStream(to)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) {
                    total += n;
                    if (total > 300L * 1024 * 1024) {
                        throw new IOException("the repository is over 300 MB, too big for the device");
                    }
                    out.write(b, 0, n);
                }
            } finally {
                c.disconnect();
            }
            return true;
        };
    }

    // ------------------------------------------------------------ the diff

    /** Where the version last cloned, pulled or pushed is kept, so the changes can be shown line by line. */
    static File baseDir(File dir) {
        return new File(dir, ".git/dvine-base");
    }

    private static void base(File dir, String path, byte[] bytes) throws IOException {
        File f = inside(baseDir(dir), path);
        if (bytes == null) {
            f.delete();
            return;
        }
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), bytes);
    }

    /** One file's changes since the clone, pull or push. */
    public static final class FileDiff {
        public final String path, kind;
        public final LineDiff.Result diff;

        public FileDiff(String path, String kind, LineDiff.Result diff) {
            this.path = path;
            this.kind = kind;
            this.diff = diff;
        }
    }

    /**
     * Every changed file in a cloned folder, with its lines added and removed (a binary file counts as changed,
     * without lines). Empty when nothing changed or the folder isn't a clone.
     */
    public static List<FileDiff> diff(File dir) {
        List<FileDiff> out = new ArrayList<FileDiff>();
        Record r;
        try {
            r = Record.read(dir);
        } catch (IOException | RuntimeException e) {
            return out;
        }
        Changes c;
        try {
            c = changes(dir, r);
        } catch (IOException e) {
            return out;
        }
        for (String p : c.added) {
            out.add(new FileDiff(p, "added", LineDiff.of("", text(new File(dir, p)))));
        }
        for (String p : c.modified) {
            out.add(new FileDiff(p, "modified", LineDiff.of(text(new File(baseDir(dir), p)), text(new File(dir, p)))));
        }
        for (String p : c.deleted) {
            out.add(new FileDiff(p, "deleted", LineDiff.of(text(new File(baseDir(dir), p)), "")));
        }
        return out;
    }

    /** A file's text, or "" when it is missing, binary or over 2 MB. */
    private static String text(File f) {
        try {
            if (!f.isFile() || f.length() > 2_000_000) {
                return "";
            }
            byte[] b = Files.readAllBytes(f.toPath());
            for (int i = 0; i < Math.min(b.length, 8000); i++) {
                if (b[i] == 0) {
                    return ""; // binary
                }
            }
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------ changes

    static final class Changes {
        final List<String> added = new ArrayList<String>(), modified = new ArrayList<String>(), deleted = new ArrayList<String>();

        boolean isEmpty() {
            return count() == 0;
        }

        int count() {
            return added.size() + modified.size() + deleted.size();
        }

        boolean touched(String path) {
            return added.contains(path) || modified.contains(path) || deleted.contains(path);
        }

        String describe() {
            List<String> lines = new ArrayList<String>();
            for (String p : added) {
                lines.add("  added: " + p);
            }
            for (String p : modified) {
                lines.add("  modified: " + p);
            }
            for (String p : deleted) {
                lines.add("  deleted: " + p);
            }
            return String.join("\n", cap(lines));
        }
    }

    static Changes changes(File dir, Record r) throws IOException {
        Changes c = new Changes();
        Map<String, File> local = new TreeMap<String, File>();
        walk(dir, dir, local);
        for (Map.Entry<String, File> e : local.entrySet()) {
            String was = r.files.get(e.getKey());
            if (was == null) {
                c.added.add(e.getKey());
            } else if (!was.equals(blobSha(Files.readAllBytes(e.getValue().toPath())))) {
                c.modified.add(e.getKey());
            }
        }
        for (String path : r.files.keySet()) {
            if (!local.containsKey(path)) {
                c.deleted.add(path);
            }
        }
        return c;
    }

    private static void walk(File base, File f, Map<String, File> out) {
        File[] kids = f.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.getName().equals(RECORD) || k.getName().equals(".git") || k.getName().equals(GitOp.FILE)
                    || k.getName().equals(GitOp.FILE + ".tmp")) {
                continue;
            }
            if (k.isDirectory()) {
                walk(base, k, out);
            } else {
                out.put(base.toPath().relativize(k.toPath()).toString().replace(File.separatorChar, '/'), k);
            }
        }
    }

    /** Git's id for a file's bytes: sha1 of "blob <length>\0" and the bytes. */
    public static String blobSha(byte[] bytes) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-1");
            d.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
            d.update(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : d.digest()) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ GitHub

    private String headOf(String repo, String branch) throws IOException {
        Connectors.Reply r = http.send("GET", API + "/repos/" + repo + "/git/ref/heads/" + branch, headers(), null);
        if (r.status == 404) {
            return null;
        }
        Map<String, Object> m = MiniJson.obj(MiniJson.parse(check(r).body));
        return MiniJson.str(MiniJson.obj(m.get("object")).get("sha"), null);
    }

    private String treeOf(String repo, String commit) throws IOException {
        return MiniJson.str(MiniJson.obj(get("/repos/" + repo + "/git/commits/" + commit).get("tree")).get("sha"), "");
    }

    /** Every file (path → blob id) in a tree, up to {@link #maxFiles}. */
    private Map<String, String> files(String repo, String tree) throws IOException {
        Map<String, Object> m = get("/repos/" + repo + "/git/trees/" + tree + "?recursive=1");
        Map<String, String> out = new TreeMap<String, String>();
        for (Object o : MiniJson.list(m.get("tree"))) {
            Map<String, Object> e = MiniJson.obj(o);
            if (!"blob".equals(MiniJson.str(e.get("type"), "")) || MiniJson.num(e.get("size"), 0) > maxFileBytes) {
                continue;
            }
            if (out.size() >= maxFiles) {
                throw new IOException(repo + " has more than " + maxFiles + " files, the most set for a clone"
                        + " (Settings \u203A Advanced \u203A Most files in a clone)");
            }
            out.put(MiniJson.str(e.get("path"), ""), MiniJson.str(e.get("sha"), ""));
        }
        return out;
    }

    private byte[] blob(String repo, String sha) throws IOException {
        Map<String, Object> m = get("/repos/" + repo + "/git/blobs/" + sha);
        String content = MiniJson.str(m.get("content"), null);
        if (content == null) {
            return null;
        }
        return Base64.getMimeDecoder().decode(content);
    }

    private static Map<String, Object> entry(String path, String sha) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("path", path);
        e.put("mode", "100644");
        e.put("type", "blob");
        e.put("sha", sha); // null deletes it
        return e;
    }

    private Map<String, Object> get(String path) throws IOException {
        return MiniJson.obj(MiniJson.parse(check(http.send("GET", API + path, headers(), null)).body));
    }

    private Map<String, Object> post(String path, Map<String, Object> body) throws IOException {
        return send("POST", path, body);
    }

    private Map<String, Object> send(String method, String path, Map<String, Object> body) throws IOException {
        Map<String, String> h = headers();
        h.put("Content-Type", "application/json");
        Connectors.Reply r = http.send(method, API + path, h, MiniJson.write(body).getBytes(StandardCharsets.UTF_8));
        return MiniJson.obj(MiniJson.parse(check(r).body));
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Accept", "application/vnd.github+json");
        h.put("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.trim().isEmpty()) {
            h.put("Authorization", "Bearer " + token.trim()); // without one, public repositories still clone and pull
        }
        h.put("User-Agent", "Ibiko");
        return h;
    }

    private static Connectors.Reply check(Connectors.Reply r) throws IOException {
        if (r.status >= 200 && r.status < 300) {
            return r;
        }
        String why = "";
        try {
            why = MiniJson.str(MiniJson.obj(MiniJson.parse(r.body)).get("message"), "");
        } catch (RuntimeException e) {
            // not JSON
        }
        if (r.status == 401) {
            throw new IOException("GitHub needs you signed in for that (Connectors \u203A GitHub)");
        }
        if (r.status == 403 || r.status == 404) {
            throw new IOException("GitHub said " + r.status + (why.isEmpty() ? "" : ": " + why)
                    + " (the token may lack access to this repository, or the Contents permission)");
        }
        throw new IOException("GitHub said " + r.status + (why.isEmpty() ? "" : ": " + why));
    }

    static String repoName(String repo) throws IOException {
        String r = repo == null ? "" : repo.trim().replaceAll("^https?://github\\.com/", "").replaceAll("(\\.git)?/*$", "");
        if (!r.matches("[\\w.-]+/[\\w.-]+")) {
            throw new IOException("give the repository as owner/name");
        }
        return r;
    }

    private static File inside(File dir, String path) throws IOException {
        File f = new File(dir, path).getCanonicalFile();
        String root = dir.getCanonicalPath();
        if (!f.getPath().startsWith(root + File.separator)) {
            throw new IOException("a path in the repository climbs out of its folder: " + path);
        }
        return f;
    }

    private static List<String> cap(List<String> lines) {
        if (lines.size() <= 40) {
            return lines;
        }
        List<String> out = new ArrayList<String>(lines.subList(0, 40));
        out.add("  …and " + (lines.size() - 40) + " more");
        return out;
    }
}
