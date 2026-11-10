package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import ai.ibiko.brain.MiniJson;

/** Clone, status, pull, commit and push, and a pull request, against a pretend GitHub that keeps a real git model. */
public class GitHubGitTest {
    /** A tiny GitHub: blobs, flat trees (path → blob), commits and branches. */
    public static final class FakeHub implements Connectors.Http {
        final Map<String, byte[]> blobs = new HashMap<String, byte[]>();
        final Map<String, Map<String, String>> trees = new HashMap<String, Map<String, String>>();
        final Map<String, String[]> commits = new HashMap<String, String[]>(); // sha → {tree, parent, message}
        public final Map<String, String> refs = new TreeMap<String, String>();
        final List<String> calls = new ArrayList<String>();
        int n;

        public String commit(Map<String, String> files, String parent, String message) {
            Map<String, String> tree = new TreeMap<String, String>();
            for (Map.Entry<String, String> e : files.entrySet()) {
                byte[] b = e.getValue().getBytes(StandardCharsets.UTF_8);
                String sha = GitHubGit.blobSha(b);
                blobs.put(sha, b);
                tree.put(e.getKey(), sha);
            }
            return commitTree(tree, parent, message);
        }

        String commitTree(Map<String, String> tree, String parent, String message) {
            String t = "t" + (++n);
            trees.put(t, tree);
            String c = "c" + (++n);
            commits.put(c, new String[]{t, parent, message});
            return c;
        }

        @Override
        public Connectors.Reply send(String method, String url, Map<String, String> headers, byte[] body) {
            assertEquals("Bearer tok", headers.get("Authorization"));
            String path = url.replace("https://api.github.com/repos/me/app", "");
            calls.add(method + " " + path);
            Map<String, Object> in = body == null ? null : MiniJson.obj(MiniJson.parse(new String(body, StandardCharsets.UTF_8)));
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            if (method.equals("GET") && path.isEmpty()) {
                out.put("default_branch", "main");
            } else if (method.equals("GET") && path.startsWith("/git/ref/heads/")) {
                String sha = refs.get(path.substring(15));
                if (sha == null) {
                    return new Connectors.Reply(404, "{\"message\":\"Not Found\"}", null);
                }
                Map<String, Object> o = new LinkedHashMap<String, Object>();
                o.put("sha", sha);
                out.put("object", o);
            } else if (method.equals("GET") && path.startsWith("/git/commits/")) {
                String[] c = commits.get(path.substring(13));
                if (c == null) {
                    return new Connectors.Reply(404, "{\"message\":\"Not Found\"}", null);
                }
                Map<String, Object> t = new LinkedHashMap<String, Object>();
                t.put("sha", c[0]);
                out.put("tree", t);
                List<Object> parents = new ArrayList<Object>();
                if (c[1] != null && !c[1].isEmpty() && !"null".equals(c[1])) {
                    Map<String, Object> p = new LinkedHashMap<String, Object>();
                    p.put("sha", c[1]);
                    parents.add(p);
                }
                out.put("parents", parents);
            } else if (method.equals("GET") && path.startsWith("/git/trees/")) {
                List<Object> list = new ArrayList<Object>();
                for (Map.Entry<String, String> e : trees.get(path.substring(11).replace("?recursive=1", "")).entrySet()) {
                    Map<String, Object> en = new LinkedHashMap<String, Object>();
                    en.put("path", e.getKey());
                    en.put("type", "blob");
                    en.put("sha", e.getValue());
                    en.put("size", (double) blobs.get(e.getValue()).length);
                    list.add(en);
                }
                out.put("tree", list);
            } else if (method.equals("GET") && path.startsWith("/git/blobs/")) {
                out.put("content", Base64.getMimeEncoder().encodeToString(blobs.get(path.substring(11))));
                out.put("encoding", "base64");
            } else if (method.equals("POST") && path.equals("/git/blobs")) {
                byte[] b = Base64.getDecoder().decode(MiniJson.str(in.get("content"), ""));
                String sha = GitHubGit.blobSha(b);
                blobs.put(sha, b);
                out.put("sha", sha);
            } else if (method.equals("POST") && path.equals("/git/trees")) {
                Map<String, String> tree = new TreeMap<String, String>(trees.get(MiniJson.str(in.get("base_tree"), "")));
                for (Object o : MiniJson.list(in.get("tree"))) {
                    Map<String, Object> e = MiniJson.obj(o);
                    String sha = MiniJson.str(e.get("sha"), null);
                    if (sha == null) {
                        tree.remove(MiniJson.str(e.get("path"), ""));
                    } else {
                        tree.put(MiniJson.str(e.get("path"), ""), sha);
                    }
                }
                String t = "t" + (++n);
                trees.put(t, tree);
                out.put("sha", t);
            } else if (method.equals("POST") && path.equals("/git/commits")) {
                String c = "c" + (++n);
                commits.put(c, new String[]{MiniJson.str(in.get("tree"), ""), MiniJson.strings(in.get("parents")).get(0),
                        MiniJson.str(in.get("message"), "")});
                out.put("sha", c);
            } else if (method.equals("PATCH") && path.startsWith("/git/refs/heads/")) {
                String b = path.substring(16);
                String sha = MiniJson.str(in.get("sha"), "");
                if (!commits.get(sha)[1].equals(refs.get(b))) {
                    return new Connectors.Reply(422, "{\"message\":\"Update is not a fast forward\"}", null);
                }
                refs.put(b, sha);
            } else if (method.equals("POST") && path.equals("/git/refs")) {
                refs.put(MiniJson.str(in.get("ref"), "").replace("refs/heads/", ""), MiniJson.str(in.get("sha"), ""));
            } else if (method.equals("POST") && path.equals("/pulls")) {
                out.put("number", 7.0);
                out.put("html_url", "https://github.com/me/app/pull/7");
                out.put("head", in.get("head"));
            } else if (method.equals("GET") && path.startsWith("/commits")) {
                String sha = q(path, "sha");
                if (refs.containsKey(sha)) {
                    sha = refs.get(sha);
                }
                List<Object> arr = new ArrayList<Object>();
                int guard = 0;
                while (sha != null && commits.containsKey(sha) && guard++ < 20) {
                    Map<String, Object> one = new LinkedHashMap<String, Object>();
                    one.put("sha", sha);
                    Map<String, Object> cm = new LinkedHashMap<String, Object>();
                    cm.put("message", commits.get(sha)[2] == null ? "" : commits.get(sha)[2]);
                    one.put("commit", cm);
                    arr.add(one);
                    String parent = commits.get(sha)[1];
                    sha = parent == null || parent.isEmpty() || "null".equals(parent) ? null : parent;
                }
                return new Connectors.Reply(200, MiniJson.write(arr), null);
            } else if (method.equals("GET") && path.startsWith("/branches")) {
                List<Object> arr = new ArrayList<Object>();
                for (String name : refs.keySet()) {
                    Map<String, Object> one = new LinkedHashMap<String, Object>();
                    one.put("name", name);
                    arr.add(one);
                }
                return new Connectors.Reply(200, MiniJson.write(arr), null);
            } else {
                return new Connectors.Reply(404, "{}", null);
            }
            return new Connectors.Reply(200, MiniJson.write(out), null);
        }

        public Map<String, String> filesAt(String branch) {
            Map<String, String> out = new TreeMap<String, String>();
            for (Map.Entry<String, String> e : trees.get(commits.get(refs.get(branch))[0]).entrySet()) {
                out.put(e.getKey(), new String(blobs.get(e.getValue()), StandardCharsets.UTF_8));
            }
            return out;
        }

        static String q(String path, String key) {
            int at = path.indexOf(key + "=");
            if (at < 0) {
                return "";
            }
            String v = path.substring(at + key.length() + 1);
            int amp = v.indexOf('&');
            return amp < 0 ? v : v.substring(0, amp);
        }
    }

    private static Map<String, String> files(String... kv) {
        Map<String, String> m = new TreeMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void blobIdsMatchGit() {
        // `printf 'hello\n' | git hash-object --stdin`
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GitHubGit.blobSha("hello\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void cloneEditCommitPushAndOpenAPullRequest() throws Exception {
        FakeHub hub = new FakeHub();
        hub.refs.put("main", hub.commit(files("README.md", "# App\n", "src/main.py", "print('hi')\n"), null, "first"));
        GitHubGit git = new GitHubGit(hub, "tok");
        File dir = new File(Files.createTempDirectory("git").toFile(), "app");

        assertTrue(git.clone("https://github.com/me/app.git", "", dir).contains("2 files"));
        assertEquals("print('hi')\n", read(new File(dir, "src/main.py")));
        assertTrue(git.status(dir).contains("nothing changed"));

        write(new File(dir, "src/main.py"), "print('hello')\n");
        write(new File(dir, "docs/notes.md"), "notes\n");
        new File(dir, "README.md").delete();
        String st = git.status(dir);
        assertTrue(st, st.contains("modified: src/main.py") && st.contains("added: docs/notes.md") && st.contains("deleted: README.md"));

        String pushed = git.push(dir, "Say hello", "feature");
        assertTrue(pushed, pushed.contains("3 file changes") && pushed.contains("to feature"));
        assertEquals(files("docs/notes.md", "notes\n", "src/main.py", "print('hello')\n"), hub.filesAt("feature"));
        assertEquals("first", hub.commits.get(hub.commits.get(hub.refs.get("feature"))[1])[2]); // parent is main's commit
        assertTrue(git.status(dir).contains("nothing changed"));
        assertTrue(git.push(dir, "again", "").startsWith("Nothing to commit"));

        String pr = git.pullRequest(dir, "Say hello", "Changes", "");
        assertTrue(pr, pr.contains("#7"));
    }

    @Test
    public void pullUpdatesCleanFilesKeepsEditsAndPushRefusesAStaleBranch() throws Exception {
        FakeHub hub = new FakeHub();
        String first = hub.commit(files("a.txt", "a1\n", "b.txt", "b1\n", "c.txt", "c1\n"), null, "first");
        hub.refs.put("main", first);
        GitHubGit git = new GitHubGit(hub, "tok");
        File dir = new File(Files.createTempDirectory("git").toFile(), "app");
        git.clone("me/app", "main", dir);
        write(new File(dir, "b.txt"), "mine\n");

        // someone else pushes: a and b change, c is deleted
        hub.refs.put("main", hub.commit(files("a.txt", "a2\n", "b.txt", "theirs\n"), first, "second"));
        try {
            git.push(dir, "mine", "");
            fail("a stale branch must not be pushed over");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("pull first"));
        }

        String pulled = git.pull(dir);
        assertTrue(pulled, pulled.contains("Conflicts") && pulled.contains("b.txt"));
        assertEquals("a2\n", read(new File(dir, "a.txt")));
        assertEquals("mine\n", read(new File(dir, "b.txt")));
        assertEquals("theirs\n", read(new File(dir, "b.txt.theirs")));
        assertFalse(new File(dir, "c.txt").exists());
        assertTrue(git.pull(dir).startsWith("Already up to date"));

        try {
            git.push(dir, "mine", "");
            fail("an unmerged .theirs file must stop the push");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(".theirs"));
        }
        new File(dir, "b.txt.theirs").delete();
        git.push(dir, "Keep mine", "");
        assertEquals(files("a.txt", "a2\n", "b.txt", "mine\n"), hub.filesAt("main"));
        String history = git.log(dir);
        assertTrue(history, history.contains("Keep mine") && history.contains("Recent commits"));
        String branches = git.branches(dir);
        assertTrue(branches, branches.contains("* main"));
        GitOp.open(dir, "push to main");
        assertTrue(git.status(dir).contains("never confirmed"));
        GitOp.close(dir, "FAILED", "stopped");
        assertFalse(git.status(dir).contains("never confirmed"));
    }

    @Test
    public void refusesPathsThatClimbOutAndBadRepoNames() throws Exception {
        FakeHub hub = new FakeHub();
        hub.refs.put("main", hub.commit(files("../evil.sh", "rm -rf /\n"), null, "bad"));
        File dir = new File(Files.createTempDirectory("git").toFile(), "app");
        try {
            new GitHubGit(hub, "tok").clone("me/app", "main", dir);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("climbs out"));
        }
        try {
            GitHubGit.repoName("not a repo");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("owner/name"));
        }
    }

    @Test
    public void clonesFromOneZipThenPushesOnlyWhatChanged() throws Exception {
        FakeHub hub = new FakeHub();
        hub.refs.put("main", hub.commit(files("README.md", "# App\n", "src/main.py", "print('hi')\n"), null, "first"));
        final java.util.List<String> asked = new java.util.ArrayList<String>();
        GitHubGit git = new GitHubGit(hub, "tok").useZips((url, headers, to) -> {
            asked.add(url);
            try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(to))) {
                for (String[] f : new String[][]{{"me-app-c1/README.md", "# App\n"}, {"me-app-c1/src/main.py", "print('hi')\n"}}) {
                    z.putNextEntry(new java.util.zip.ZipEntry(f[0]));
                    z.write(f[1].getBytes(StandardCharsets.UTF_8));
                    z.closeEntry();
                }
            }
            return true;
        });
        File dir = new File(Files.createTempDirectory("git").toFile(), "app");
        String said = git.clone("me/app", "", dir);
        assertTrue(said, said.contains("2 files") && said.contains("on the device"));
        assertTrue(asked.get(0), asked.get(0).endsWith("/repos/me/app/zipball/" + hub.refs.get("main")));
        assertFalse(hub.calls.toString(), hub.calls.toString().contains("/git/blobs/")); // no file-by-file download
        assertTrue(git.status(dir).contains("nothing changed"));
        assertTrue(GitHubGit.diff(dir).isEmpty());
        write(new File(dir, "src/main.py"), "print('hello')\n");
        write(new File(dir, "NOTES.md"), "one\ntwo\n");
        java.util.List<GitHubGit.FileDiff> d = GitHubGit.diff(dir);
        assertEquals(2, d.size());
        GitHubGit.FileDiff added = d.get(0), changed = d.get(1);
        assertEquals("NOTES.md", added.path);
        assertEquals("added", added.kind);
        assertEquals(2, added.diff.added);
        assertEquals("src/main.py", changed.path);
        assertEquals(1, changed.diff.added);
        assertEquals(1, changed.diff.removed);
        new File(dir, "NOTES.md").delete();
        git.push(dir, "Say hello", "");
        assertTrue(GitHubGit.diff(dir).isEmpty()); // pushed: nothing left to show
        assertEquals(files("README.md", "# App\n", "src/main.py", "print('hello')\n"), hub.filesAt("main"));
        assertFalse(new File(dir.getParentFile(), ".app.zip").exists());
    }

    @Test
    public void fetchMainThenMergeCombinesEditsAndLeavesARealConflict() throws Exception {
        FakeHub hub = new FakeHub();
        String base = "line1\nline2\nline3\nline4\n";
        String first = hub.commit(files("a.txt", base, "keep.txt", "same\n"), null, "base");
        hub.refs.put("main", first);
        GitHubGit git = new GitHubGit(hub, "tok");
        File dir = new File(Files.createTempDirectory("git").toFile(), "app");
        git.clone("me/app", "main", dir);
        write(new File(dir, "a.txt"), "line1\nline2-mine\nline3\nline4\n");
        git.push(dir, "mine", "feature");
        hub.refs.put("main", hub.commit(files("a.txt", "line1\nline2\nline3\nline4-main\n", "keep.txt", "same\n"), first, "main moved"));

        int before = hub.calls.size();
        String fetched = git.fetch(dir, "main");
        assertTrue(fetched, fetched.contains("folder was not changed") && fetched.contains("No files were downloaded") == false);
        assertTrue(fetched, fetched.contains("files listed"));
        for (int i = before; i < hub.calls.size(); i++) {
            assertFalse(hub.calls.get(i), hub.calls.get(i).contains("/git/blobs/"));
        }
        assertEquals("line1\nline2-mine\nline3\nline4\n", read(new File(dir, "a.txt")));

        String merged = git.merge(dir, "origin/main");
        assertTrue(merged, merged.contains("on the phone") && merged.contains("stayed on the device"));
        assertEquals("line1\nline2-mine\nline3\nline4-main\n", read(new File(dir, "a.txt")));
        assertFalse(new File(dir, "a.txt.theirs").exists());
        assertTrue(git.status(dir), git.status(dir).contains("modified: a.txt"));
        assertEquals("same\n", read(new File(dir, "keep.txt")));
        String pushed = git.push(dir, "Bring in main", "");
        assertTrue(pushed, pushed.contains("to feature"));
        assertEquals("line1\nline2-mine\nline3\nline4-main\n", hub.filesAt("feature").get("a.txt"));

        // overlapping edit: markers and .theirs block the push until they are gone
        hub.refs.put("main", hub.commit(files("a.txt", "line1\nline2-theirs\nline3\nline4-main\n", "keep.txt", "same\n"),
                hub.refs.get("main"), "overlap"));
        String clash = git.merge(dir, "main");
        assertTrue(clash, clash.contains("1 conflict") && clash.contains("<<<<<<< yours"));
        assertTrue(read(new File(dir, "a.txt.theirs")), read(new File(dir, "a.txt.theirs")).contains("line2-theirs"));
        assertTrue(read(new File(dir, "a.txt")).contains("<<<<<<< yours"));
        try {
            git.push(dir, "too soon", "");
            fail("a .theirs file must stop the push");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(".theirs"));
        }
        new File(dir, "a.txt.theirs").delete();
        try {
            git.push(dir, "still marked", "");
            fail("conflict markers must stop the push");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("conflict markers"));
        }
        write(new File(dir, "a.txt"), "line1\nline2-both\nline3\nline4-main\n");
        assertTrue(git.push(dir, "Resolved", "").contains("to feature"));
    }
}
