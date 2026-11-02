package ai.dvine.connect;

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

import ai.dvine.brain.MiniJson;

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
                Map<String, Object> t = new LinkedHashMap<String, Object>();
                t.put("sha", commits.get(path.substring(13))[0]);
                out.put("tree", t);
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
        assertTrue(said, said.contains("2 files") && said.contains("on the phone"));
        assertTrue(asked.get(0), asked.get(0).endsWith("/repos/me/app/zipball/" + hub.refs.get("main")));
        assertFalse(hub.calls.toString(), hub.calls.toString().contains("/git/blobs/")); // no file-by-file download
        assertTrue(git.status(dir).contains("nothing changed"));
        write(new File(dir, "src/main.py"), "print('hello')\n");
        git.push(dir, "Say hello", "");
        assertEquals(files("README.md", "# App\n", "src/main.py", "print('hello')\n"), hub.filesAt("main"));
        assertFalse(new File(dir.getParentFile(), ".app.zip").exists());
    }
}
