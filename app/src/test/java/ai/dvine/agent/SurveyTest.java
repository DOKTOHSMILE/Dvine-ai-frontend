package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** The Blueprint's survey: the relevant files found, read in numbered pieces by several readers, the notes kept. */
public class SurveyTest {
    private static File project() throws Exception {
        File root = Files.createTempDirectory("survey").toFile();
        new File(root, "app/src").mkdirs();
        Files.write(new File(root, "app/src/Dictation.java").toPath(), "class Dictation {\n  void put() {}\n}\n".getBytes("UTF-8"));
        Files.write(new File(root, "app/src/Voice.java").toPath(), "class Voice { // dictation helper\n}\n".getBytes("UTF-8"));
        Files.write(new File(root, "app/src/Other.java").toPath(), "class Other {}\n".getBytes("UTF-8"));
        Files.write(new File(root, "README.md").toPath(), "# App\n".getBytes("UTF-8"));
        new File(root, "build").mkdirs();
        Files.write(new File(root, "build/Dictation.java").toPath(), "generated".getBytes("UTF-8"));
        return root;
    }

    @Test
    public void theRelevantFilesAreFoundFirst() throws Exception {
        File root = project();
        List<File> picked = Survey.pick(root, "Fix the dictation so pauses keep the words");
        assertEquals("Dictation.java", picked.get(0).getName());
        assertTrue(picked.toString(), picked.toString().contains("Voice.java"));
        assertTrue("build output is not read", !picked.toString().contains("build"));
        assertTrue("an unrelated file is left out", !picked.toString().contains("Other.java"));
    }

    @Test
    public void readersReadNumberedLinesAtOnceAndTheMapIsKept() throws Exception {
        File root = project();
        File keep = Files.createTempDirectory("keep").toFile();
        AtomicInteger asked = new AtomicInteger();
        StringBuilder steps = new StringBuilder();
        String map = Survey.run(root, keep, "Fix the dictation so pauses keep the words", (system, text) -> {
            asked.incrementAndGet();
            assertTrue(text, text.contains("1: "));
            return "Purpose: " + (text.contains("Dictation.java") ? "writes speech into the box" : "a helper") + "\nReuse: put()";
        }, 3, line -> {
            synchronized (steps) {
                steps.append(line).append('\n');
            }
        });
        assertTrue(map, map.contains("## app/src/Dictation.java") && map.contains("writes speech into the box"));
        assertTrue(new File(keep, "MAP.md").isFile());
        assertTrue(new File(keep, "notes/app__src__Dictation.java.md").isFile());
        assertTrue(steps.toString(), steps.toString().contains("readers at once") && steps.toString().contains("Codebase map ready"));
        assertTrue(asked.get() >= 2);
    }

    @Test
    public void nothingRelevantGivesNoMap() throws Exception {
        assertNull(Survey.run(project(), "zzzz qqqq", (s, t) -> "x", 2, null));
    }
}
