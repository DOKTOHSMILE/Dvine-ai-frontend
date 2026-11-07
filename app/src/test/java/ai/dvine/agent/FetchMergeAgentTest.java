package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TreeMap;

import org.junit.Test;

import ai.dvine.connect.GitHubGit;
import ai.dvine.connect.GitHubGitTest;

/** "fetch main and merge" is done on the phone. The model is not asked. */
public class FetchMergeAgentTest {
    @Test
    public void fetchMainAndMergeRunsOnThePhoneWithoutAModel() throws Exception {
        GitHubGitTest.FakeHub hub = new GitHubGitTest.FakeHub();
        TreeMap<String, String> f = new TreeMap<String, String>();
        f.put("a.txt", "one\ntwo\n");
        String first = hub.commit(f, null, "base");
        hub.refs.put("main", first);
        File ws = Files.createTempDirectory("ibiko").toFile();
        GitHubGit git = new GitHubGit(hub, "tok");
        File app = new File(ws, "app");
        git.clone("me/app", "main", app);
        Files.write(new File(app, "a.txt").toPath(), "one\nmine\n".getBytes(StandardCharsets.UTF_8));
        git.push(app, "mine", "feature");
        TreeMap<String, String> moved = new TreeMap<String, String>();
        moved.put("a.txt", "one\ntwo\nmain\n");
        hub.refs.put("main", hub.commit(moved, first, "main moved"));
        CodeAgent agent = new CodeAgent(ws, new CodeAgent.Brain() {
            public String ask(String system, String transcript) {
                throw new AssertionError("a fetch and merge must not ask a model");
            }
        }, new Host(), "", 20_000).useGit(git);
        String out = agent.run("fetch main and merge in app", "");
        assertTrue(out, out.contains("on the phone") && out.contains("stayed on the device"));
        assertEquals("one\nmine\nmain\n", new String(Files.readAllBytes(new File(app, "a.txt").toPath()), StandardCharsets.UTF_8));
        assertTrue(agent.system().contains("action=\"fetch\"") && agent.system().contains("action=\"merge\"")
                && agent.system().contains("git pull, then push again"));
    }

    private static final class Host implements CodeAgent.Host {
        public boolean allowRun(String command) {
            return false;
        }

        public String run(String command, File workspace) {
            return "";
        }

        public String fetch(String url) {
            return null;
        }

        public String connector(String call) {
            return "";
        }

        public void step(String line) {
        }

        public boolean stopped() {
            return false;
        }
    }
}
