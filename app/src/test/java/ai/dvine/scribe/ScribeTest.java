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

/** Scribe keeps a project record that survives the chat, and does not treat an unverified claim as completion. */
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
    public void aSecondBeginResumesTheOpenJobAndAnAndroidTreeIsRecognised() throws Exception {
        File ws = Files.createTempDirectory("dvine-survey").toFile();
        File app = new File(ws, "app");
        assertTrue(app.mkdirs());
        Files.write(new File(app, "build.gradle").toPath(), "apply plugin: 'com.android.application'\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(app, "Main.java").toPath(), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        ProjectSurvey survey = ProjectSurvey.of(ws);
        assertTrue(survey.languages.contains("Java"));
        assertTrue(survey.markers.contains("build.gradle"));
        assertTrue(survey.plan("Build the screen").contains("Android"));
        Scribe scribe = new Scribe(ws);
        String instruction = "Build the settings screen for this application";
        scribe.begin("ibiko", instruction);
        File plan = new File(ws, "memory/projects");
        File[] kids = plan.listFiles();
        assertTrue(kids != null && kids.length == 1);
        String written = new String(Files.readAllBytes(new File(kids[0], "PROJECT_PLAN.md").toPath()), StandardCharsets.UTF_8);
        assertTrue(written.contains("Android"));
        assertEquals(1, Scribe.jobs(ws).size());
        assertTrue(Scribe.jobs(ws).get(0).open);
        scribe.begin("ibiko", instruction);
        assertEquals(1, Scribe.jobs(ws).size());
        String progress = new String(Files.readAllBytes(new File(kids[0], "PROGRESS.md").toPath()), StandardCharsets.UTF_8);
        assertTrue(progress.contains("Resumed"));
        String kept = new String(Files.readAllBytes(new File(kids[0], "PROJECT_PLAN.md").toPath()), StandardCharsets.UTF_8);
        assertEquals(written, kept);
    }

    @Test
    public void filePresenceAloneDoesNotCompleteAndVerificationMustSucceed() throws Exception {
        File ws = Files.createTempDirectory("dvine-reconcile").toFile();
        Scribe scribe = new Scribe(ws);
        String instruction = "Build the notes screen for the application";
        scribe.begin("ibiko", instruction);
        File target = new File(ws, "app/Notes.java");
        assertTrue(target.getParentFile().mkdirs());
        Files.write(target.toPath(), "class Notes {}".getBytes(StandardCharsets.UTF_8));

        scribe.finish("The notes screen is finished.", false, Collections.singletonList("app/Notes.java"));
        assertTrue("No independent verification must leave the task open", Scribe.jobs(ws).get(0).open);

        scribe.begin("ibiko", instruction);
        scribe.finish("The notes screen is finished.", false, Collections.singletonList("app/Notes.java"),
                Scribe.VerificationEvidence.commandResult(1, "tests failed"));
        assertTrue("A failed check must leave the task open", Scribe.jobs(ws).get(0).open);

        scribe.begin("ibiko", instruction);
        scribe.finish("The notes screen is finished.", false, Collections.singletonList("app/Notes.java"),
                Scribe.VerificationEvidence.commandResult(0, "test suite: 14 passed"));
        assertFalse(Scribe.jobs(ws).get(0).open);
        File project = new File(ws, "memory/projects").listFiles()[0];
        String report = new String(Files.readAllBytes(new File(project, "TEST_RESULTS.md").toPath()), StandardCharsets.UTF_8);
        assertTrue(report.contains("test suite: 14 passed"));
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
