package ai.dvine.connect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ai.dvine.brain.MiniJson;

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
        if (zips != null) {
            File zip = new File(dir.getParentFile(), "." + dir.getName() + ".zip");
            try {
                if (zips.download(API + "/repos/" + repo + "/zipball/" + head, headers(), zip)) {
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
        Record r = Record.read(dir);
        Changes c = changes(dir, r);
        if (c.isEmpty()) {
            return "On " + r.branch + " of " + r.repo + ": nothing changed.";
        }
        return "On " + r.branch + " of " + r.repo + ":\n" + c.describe();
    }

    /** Brings in the branch's newer commit; files the user changed are kept and listed (the other side is saved as "name.theirs"). */
    public String pull(File dir) throws IOException {
        Record r = Record.read(dir);
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
        String branch = toBranch == null || toBranch.trim().isEmpty() ? r.branch : toBranch.trim();
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
        cm.put("message", message == null || message.trim().isEmpty() ? "Update from DVINE" : message.trim());
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
        pr.put("title", title == null || title.trim().isEmpty() ? "Changes from DVINE" : title.trim());
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
            if (k.getName().equals(RECORD) || k.getName().equals(".git")) {
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
            if (!"blob".equals(MiniJson.str(e.get("type"), "")) || MiniJson.num(e.get("size"), 0) > MAX_FILE_BYTES) {
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
        h.put("User-Agent", "DVINE");
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
