package ai.ibiko.agent;

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

    @Test
    public void theScribesOwnFilesAndTheFolderNameAreNotTheCode() throws Exception {
        java.io.File root = java.nio.file.Files.createTempDirectory("clone-https-github-com-me-app").toFile();
        java.nio.file.Files.write(new java.io.File(root, "NEXT_ACTIONS.md").toPath(), "clone the github repo app".getBytes("UTF-8"));
        java.nio.file.Files.write(new java.io.File(root, "EXECUTION.json").toPath(), "{\"task\":\"clone app\"}".getBytes("UTF-8"));
        assertTrue("nothing but Ibiko's bookkeeping: no survey", Survey.pick(root, "Clone https://github.com/me/app.git").isEmpty());
    }

    @Test
    public void aBrokenOffSurveyCarriesOnWithoutReadingAgainAndTheMapIsInOrderWithTimes() throws Exception {
        File root = project();
        File keep = Files.createTempDirectory("keep").toFile();
        String task = "Fix the dictation so pauses keep the words";
        // the first run breaks off: one reader fails, as when the connection drops
        Survey.run(root, keep, task, (system, text) -> text.contains("Voice.java") ? null : "Purpose: read once", 1, null);
        String progress = new String(Files.readAllBytes(new File(keep, "SURVEY.md").toPath()), "UTF-8");
        assertTrue(progress, progress.contains("Still to read") && progress.contains("Voice.java") && progress.contains("Last read:"));
        // "continue": only what was not read is read now
        AtomicInteger asked = new AtomicInteger();
        StringBuilder steps = new StringBuilder();
        String map = Survey.run(root, keep, task, (system, text) -> {
            asked.incrementAndGet();
            assertTrue("only the part not read yet", text.contains("Voice.java"));
            return "Purpose: a helper";
        }, 2, line -> steps.append(line).append('\n'));
        assertEquals(1, asked.get());
        assertTrue(steps.toString(), steps.toString().contains("already read, carrying on from there"));
        assertTrue(map, map.contains("Made: ") && map.contains("## Contents") && map.contains("### Lines 1") && map.contains("Read 20"));
        assertTrue("files in path order", map.indexOf("## README.md") < map.indexOf("## app/src/Dictation.java")
                || !map.contains("## README.md"));
        assertTrue(map.indexOf("## app/src/Dictation.java") < map.indexOf("## app/src/Voice.java"));
        // a changed file is read again
        Files.write(new File(root, "app/src/Dictation.java").toPath(), "class Dictation {\n  void put(String s) {}\n}\n".getBytes("UTF-8"));
        AtomicInteger again = new AtomicInteger();
        Survey.run(root, keep, task, (system, text) -> {
            again.incrementAndGet();
            return "Purpose: changed";
        }, 2, null);
        assertEquals("only the changed file", 1, again.get());
    }

    @Test
    public void afterABreakTheBlueprintFolderSaysWhereTheWorkStood() throws Exception {
        File root = project();
        File keep = Files.createTempDirectory("keep").toFile();
        String task = "Fix the dictation so pauses keep the words";
        assertTrue("nothing kept yet", Survey.whereItStood(keep).isEmpty() && !Survey.unfinished(keep));
        // the connection breaks while one part is being read
        Survey.run(root, keep, task, (system, text) -> text.contains("Voice.java") ? null : "Purpose: writes speech", 1, null);
        assertTrue("the break is seen", Survey.unfinished(keep));
        String stood = Survey.whereItStood(keep);
        assertTrue(stood, stood.contains("[SURVEY.md]") && stood.contains("Still to read") && stood.contains("Voice.java")
                && stood.contains("Last read:"));
        // carried on: only what was left is read, and then nothing is unfinished
        Survey.run(root, keep, task, (system, text) -> "Purpose: a helper", 1, null);
        assertTrue("finished", !Survey.unfinished(keep));
        assertTrue(Survey.whereItStood(keep).contains("[MAP.md]"));
    }

    @Test
    public void theWholeTreeIsReadFromTheTopOneBadgeAFile() throws Exception {
        File root = project();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            big.append("line ").append(i).append(" dictation\n");
        }
        Files.write(new File(root, "app/src/Dictation.java").toPath(), big.toString().getBytes("UTF-8"));
        java.util.List<Survey.Piece> parts = Survey.piecesOf(root, new File(root, "app/src/Dictation.java"));
        assertEquals(2, parts.size());
        assertEquals(1, parts.get(0).from);
        assertEquals(400, parts.get(0).to);
        assertEquals(401, parts.get(1).from);
        assertEquals(500, parts.get(1).to);
        assertTrue("the rest of the tree is not dropped", Survey.tree(root, "Fix the dictation so pauses keep the words").size() >= 4);
        File keep = Files.createTempDirectory("keep").toFile();
        StringBuilder steps = new StringBuilder();
        java.util.List<String> asked = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        String map = Survey.run(root, keep, "Fix the dictation so pauses keep the words", (system, text) -> {
            int at = text.indexOf("File: ");
            asked.add(text.substring(at, text.indexOf('\n', at)));
            return "Purpose: a file";
        }, 4, line -> {
            synchronized (steps) {
                steps.append(line).append('\n');
            }
        });
        String shown = steps.toString();
        assertTrue(shown, shown.contains("Mapped the tree:"));
        assertTrue(shown, shown.contains("4 of 4 files documented"));
        int dict = shown.indexOf("Read app/src/Dictation.java (lines 1–500)");
        int voice = shown.indexOf("Read app/src/Voice.java (lines 1–");
        assertTrue(shown, dict > 0 && voice > dict);
        assertTrue("one badge, not a slice per 400 lines", shown.indexOf("Read app/src/Dictation.java") == dict);
        assertTrue(map, map.contains("## Tree") && map.contains("app/src/Other.java"));
        String first = null, second = null;
        for (String a : asked) {
            if (!a.contains("Dictation.java")) {
                continue;
            }
            if (a.contains("lines 1–")) {
                first = a;
            } else if (a.contains("lines 401–")) {
                second = a;
            }
        }
        assertTrue(asked.toString(), first != null && second != null && asked.indexOf(first) < asked.indexOf(second));
    }
}
