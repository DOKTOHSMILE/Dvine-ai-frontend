package ai.ibiko.scribe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.Test;

import ai.ibiko.ops.task.TaskState;

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
        assertTrue(new File(kids[0], Scribe.RECORD).isFile());
        assertTrue(new File(kids[0], Scribe.RECORD).isFile());
        scribe.checkpoint("read the workspace\nUser token ghp_abcdefghijklmnopqrstuvwxyz");
        String checkpoint = new String(Files.readAllBytes(new File(kids[0], Scribe.RECORD).toPath()), StandardCharsets.UTF_8);
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
        String written = new String(Files.readAllBytes(new File(kids[0], Scribe.RECORD).toPath()), StandardCharsets.UTF_8);
        assertTrue(written.contains("Android"));
        assertEquals(1, Scribe.jobs(ws).size());
        assertTrue(Scribe.jobs(ws).get(0).open);
        scribe.begin("ibiko", instruction);
        assertEquals(1, Scribe.jobs(ws).size());
        String progress = new String(Files.readAllBytes(new File(kids[0], Scribe.RECORD).toPath()), StandardCharsets.UTF_8);
        assertTrue(progress.contains("Resumed"));
        String kept = new String(Files.readAllBytes(new File(kids[0], Scribe.RECORD).toPath()), StandardCharsets.UTF_8);
        assertTrue("the plan is kept", kept.contains("Android"));
    }

    @Test
    public void aJobIsFinishedOnlyWhenTheClaimedFileIsOnDisk() throws Exception {
        File ws = Files.createTempDirectory("dvine-reconcile").toFile();
        Scribe scribe = new Scribe(ws);
        String instruction = "Build the notes screen for the application";
        scribe.begin("ibiko", instruction);
        scribe.finish("The notes screen is finished and the reply is long enough to look done.", false,
                Collections.singletonList("app/Notes.java"));
        assertTrue(Scribe.jobs(ws).get(0).open);
        File project = new File(ws, "memory/projects").listFiles()[0];
        String recovery = new String(Files.readAllBytes(new File(project, Scribe.RECORD).toPath()), StandardCharsets.UTF_8);
        assertTrue(recovery.contains("Missing: 1"));
        assertTrue(recovery.contains("app/Notes.java"));
        assertEquals(1, Reconcile.check(ws, Collections.singletonList("../secret.txt")).missing.size());

        assertTrue(new File(ws, "app").mkdirs());
        Files.write(new File(ws, "app/Notes.java").toPath(), "class Notes {}".getBytes(StandardCharsets.UTF_8));
        scribe.begin("ibiko", instruction);
        String progress = new String(Files.readAllBytes(new File(project, Scribe.RECORD).toPath()), StandardCharsets.UTF_8);
        assertTrue(progress.contains("Missing: 0"));
        scribe.finish("The notes screen is in app/Notes.java.", false, Collections.singletonList("app/Notes.java"));
        assertFalse(Scribe.jobs(ws).get(0).open);
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

    @Test
    public void oneFileForAProjectAndItSaysExactlyWhereTheWorkStands() throws Exception {
        File ws = Files.createTempDirectory("dvine-record").toFile();
        Scribe scribe = new Scribe(ws);
        scribe.begin("ibiko", "Build the notes screen for the application", "chat 7");
        scribe.step("Read app/Notes.java");
        scribe.step("Edited app/Notes.java (12 lines)");
        File project = new File(ws, "memory/projects/section-chat-7");
        String[] files = project.list();
        assertEquals("one record file, nothing else", 1, files.length);
        assertEquals(Scribe.RECORD, files[0]);
        String resume = scribe.resume();
        assertTrue(resume.contains("Last file: app/Notes.java"));
        assertTrue(resume.contains("Last change: Edited app/Notes.java"));
        assertTrue(resume.contains("Pending:"));
        assertTrue(resume.contains("Already done"));
        // a second instruction in the same chat adds to the same record, and what was done stays on it
        scribe.finish("Done.", false, Collections.<String>emptyList());
        Scribe again = new Scribe(ws);
        again.begin("ibiko", "Now add a delete button to the notes screen", "chat 7");
        assertEquals(1, new File(ws, "memory/projects").list().length);
        assertEquals(1, project.list().length);
        assertTrue(again.resume().contains("Edited app/Notes.java"));
    }
}
