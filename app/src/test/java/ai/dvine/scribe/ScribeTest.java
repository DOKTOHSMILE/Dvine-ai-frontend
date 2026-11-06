package ai.dvine.scribe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.Test;

import ai.dvine.ops.task.TaskState;

/** Scribe keeps a project record that survives the chat, and does not treat a thin reply as finished. */
public class ScribeTest {
    @Test
    public void aProjectIsRecordedAndAStopDoesNotFinishIt() throws Exception {
        File ws = Files.createTempDirectory("dvine-scribe").toFile();
        Scribe scribe = new Scribe(ws);
        assertFalse(Scribe.worth("hi"));
        assertTrue(Scribe.worth("Build me an application that tracks tasks"));
        scribe.begin("ibiko", "Build me an application that tracks tasks");
        File plan = new File(ws, "memory/projects");
        File[] kids = plan.listFiles();
        assertTrue(kids != null && kids.length == 1);
        assertTrue(new File(kids[0], "PROJECT_PLAN.md").isFile());
        assertTrue(new File(kids[0], "PROJECT_MAP.md").isFile());
        scribe.checkpoint("read the workspace\nUser token ghp_abcdefghijklmnopqrstuvwxyz");
        String checkpoint = new String(Files.readAllBytes(new File(kids[0], "checkpoints/latest.md").toPath()), StandardCharsets.UTF_8);
        assertFalse(checkpoint.contains("ghp_abcdefghijklmnopqrstuvwxyz"));
        assertTrue(checkpoint.contains("ghp_"));
        scribe.finish("I'll continue after this.", false, Collections.<String>emptyList());
        assertTrue(Scribe.pending(ws).toLowerCase().contains("paused") || Scribe.pending(ws).contains("pause"));
    }

    @Test
    public void deepModeDoesNotInheritTheShortReplyCap() {
        assertEquals(180_000L, RunMode.BALANCED.replyCapMs);
        assertTrue(RunMode.DEEP.replyCapMs > RunMode.BALANCED.replyCapMs);
        assertTrue(RunMode.AUTONOMOUS.termuxMs > RunMode.BALANCED.termuxMs);
        assertEquals(RunMode.BALANCED, RunMode.of(1));
        assertEquals(RunMode.AUTONOMOUS, RunMode.of(4));
        assertEquals(RunMode.BALANCED, RunMode.of(99));
        assertTrue(TaskState.PAUSED.open());
        assertFalse(TaskState.COMPLETED.open());
    }
}
