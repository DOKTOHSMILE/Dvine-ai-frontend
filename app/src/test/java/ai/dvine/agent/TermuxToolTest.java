package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TermuxToolTest {
    @Test
    public void aProjectIsPackedAsTarGzThatTarUnpacks() throws Exception {
        File p = Files.createTempDirectory("proj").toFile();
        new File(p, "src/app").mkdirs();
        Files.write(new File(p, "main.py").toPath(), "print('hello from termux')\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(p, "src/app/util.py").toPath(), "X = 1\n".getBytes(StandardCharsets.UTF_8));
        new File(p, "node_modules/big").mkdirs();
        Files.write(new File(p, "node_modules/big/index.js").toPath(), new byte[5000]);
        String deep = "a/" + "b".repeat(80) + "/" + "c".repeat(60) + ".txt"; // over 100 characters: the prefix field
        new File(p, deep).getParentFile().mkdirs();
        Files.write(new File(p, deep).toPath(), "deep".getBytes(StandardCharsets.UTF_8));
        byte[] tgz = TarGz.pack(p, 600_000);
        assertNotNull(tgz);
        assertNull("too big: not packed", TarGz.pack(p, 50));
        File out = Files.createTempDirectory("unpacked").toFile();
        File archive = new File(out, "p.tgz");
        Files.write(archive.toPath(), tgz);
        Process tar;
        try {
            tar = new ProcessBuilder("tar", "xzf", archive.getPath(), "-C", out.getPath()).redirectErrorStream(true).start();
        } catch (java.io.IOException noTar) {
            return; // no tar on this machine: the packing itself was checked above
        }
        String said = new String(tar.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(said, 0, tar.waitFor());
        assertEquals("print('hello from termux')\n", new String(Files.readAllBytes(new File(out, "main.py").toPath()), StandardCharsets.UTF_8));
        assertTrue(new File(out, "src/app/util.py").isFile());
        assertEquals("deep", new String(Files.readAllBytes(new File(out, deep).toPath()), StandardCharsets.UTF_8));
        assertFalse("rebuilt folders aren't copied", new File(out, "node_modules").exists());
    }

    @Test
    public void ibikoRunsCodeInTermuxWhenItIsThere() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        new File(ws, "sandbox/hello").mkdirs();
        final List<String> ran = new ArrayList<String>();
        CodeAgent.Host host = new CodeAgent.Host() {
            public boolean allowRun(String command) { ran.add("asked " + command); return true; }
            public String run(String command, File workspace) { return ""; }
            public String fetch(String url) { return null; }
            public String connector(String call) { return ""; }
            public void step(String line) { }
            public boolean stopped() { return false; }
            public boolean termuxReady() { return true; }
            public String termux(String command, File folder) {
                ran.add(folder.getName() + ": " + command);
                return "hello\n[exit code 0: it worked]";
            }
        };
        final List<String> replies = new ArrayList<String>(Arrays.asList(
                "<tool name=\"termux\" path=\"sandbox/hello\" command=\"python3 main.py\"/>",
                "<final>It prints hello.</final>"));
        CodeAgent agent = new CodeAgent(ws, (system, transcript) -> replies.isEmpty() ? null : replies.remove(0), host, "", 20_000);
        assertTrue(agent.system().contains("<tool name=\"termux\""));
        assertEquals("It prints hello.", agent.run("run my hello script", ""));
        assertEquals(Arrays.asList("asked termux: python3 main.py", "hello: python3 main.py"), ran);
    }
}
