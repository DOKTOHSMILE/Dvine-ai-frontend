package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

/** Commands said in words, and a step's detail kept and read back. */
public class CommandWordsTest {
    @Test
    public void commandsInWords() {
        assertEquals("Listed the files in the workspace, then listed the files in Dvine-ai-frontend", CommandWords.describe("ls -la; echo ---; ls Dvine-ai-frontend/"));
        assertEquals("Looked through the files in Dvine-ai-frontend/app/src/main/java (the first lines)",
                CommandWords.describe("find Dvine-ai-frontend/app/src/main/java -type f | head -n 1"));
        assertEquals("Searched for \\u201cTODO\\u201d in app".replace("\\u201c", "“").replace("\\u201d", "”"),
                CommandWords.describe("grep -rn TODO app"));
        assertEquals("Read app/Main.java", CommandWords.describe("cat app/Main.java"));
        assertEquals("Counted the lines in a.py and b.py", CommandWords.describe("wc -l a.py b.py"));
        assertEquals("Read a.py, b.py and c.py", CommandWords.describe("cat a.py b.py c.py"));
        assertEquals("Looked for files named “*.java” in src", CommandWords.describe("find src -name '*.java'"));
        assertEquals("Wrote to note.txt", CommandWords.describe("echo hello > note.txt"));
        assertEquals("Ran app.py", CommandWords.describe("cd app && python3 app.py"));
        assertEquals("Downloaded brewkeg.dev", CommandWords.describe("curl -fsSL https://brewkeg.dev/install.sh"));
        assertEquals("Ran a command", CommandWords.describe(""));
    }

    @Test
    public void aStepsDetailIsKeptAndOpened() throws Exception {
        File dir = Files.createTempDirectory("steps").toFile();
        String id = StepDetail.save(dir, "app/Main.java", Arrays.asList("@ line 1", " class Main {", "+    void hello() {}", "-    // old", " }"), 1, 1);
        String line = "Edited app/Main.java" + StepDetail.mark(id);
        assertEquals("Edited app/Main.java", StepDetail.words(line));
        assertEquals(id, StepDetail.id(line));
        StepDetail.Detail d = StepDetail.load(dir, id);
        assertTrue(d.diff());
        assertEquals(1, d.added);
        assertEquals("app/Main.java", d.title);
        assertEquals("+    void hello() {}", d.lines.get(2));
        assertNull(StepDetail.id("Read a file"));
    }
}
