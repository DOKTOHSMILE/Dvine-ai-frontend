package ai.ibiko.ops.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/** Approved commands stay an argument list. A session left open is not a success. */
public class TermuxPlanTest {
    @Test
    public void installAndCloneStayAllowListedAndNeedConfirmation() {
        TermuxCommandEngine engine = engine(new TermuxExecutionService.Result(0, "ok", "", false, false), new AtomicInteger());
        TermuxCommandEngine.Planned python = engine.plan("Install Python.");
        assertEquals("pkg", python.argv.get(0));
        assertEquals("python", python.argv.get(3));
        AtomicInteger runs = new AtomicInteger();
        TermuxCommandEngine gated = engine(new TermuxExecutionService.Result(0, "ok", "", false, false), runs);
        var refused = gated.execute(gated.plan("Install Python."), false);
        assertEquals(0, runs.get());
        assertTrue(refused.speech.toLowerCase().contains("confirm") || refused.speech.toLowerCase().contains("change"));
        TermuxCommandEngine.Planned clone = engine.plan("Clone this GitHub repository https://github.com/example/app.git");
        assertEquals("git", clone.argv.get(0));
        assertEquals("https://github.com/example/app.git", clone.argv.get(4));
        assertNull(engine.plan("Clone https://user:token@github.com/example/app.git").argv);
        assertNull(TermuxCommandEngine.zipName("unzip ../secret.zip"));
        assertEquals("notes.zip", TermuxCommandEngine.zipName("Extract the downloaded notes.zip"));
        TermuxCommandEngine.Planned deleted = engine.plan("please delete the project folder");
        assertNull(deleted.argv);
        assertTrue(deleted.refusal.toLowerCase().contains("won't") || deleted.refusal.toLowerCase().contains("destructive"));
    }

    @Test
    public void aDeadSessionStaysUnverified() throws Exception {
        File dir = Files.createTempDirectory("dvine-termux").toFile();
        TermuxSessionLog log = new TermuxSessionLog(dir);
        String id = log.open("python install", "pkg install python ghp_abcdefghijklmnopqrstuvwxyz");
        assertTrue(TermuxSessionLog.pending(dir).contains("unverified"));
        assertFalse(new String(Files.readAllBytes(new File(dir, id + ".session").toPath())).contains("ghp_abcdefghijklmnopqrstuvwxyz"));
        log.close(id, "TIMED_OUT", -1);
        assertTrue(TermuxSessionLog.pending(dir).toLowerCase().contains("timed out"));
        log.close(id, "FINISHED", 0);
        assertEquals("", TermuxSessionLog.pending(dir));
    }

    private static TermuxCommandEngine engine(TermuxExecutionService.Result result, AtomicInteger runs) {
        return new TermuxCommandEngine(new TermuxCapabilityDetector(new TermuxCapabilityDetector.Probe() {
            public boolean installed() { return true; }
            public boolean permissionGranted() { return true; }
            public boolean resultBridgeConnected() { return true; }
        }), new TermuxExecutionService((argv, timeout) -> {
            runs.incrementAndGet();
            return result;
        }));
    }
}
