package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** A deep diagnosis claims nothing that isn't so: every file and line it names is checked against the files. */
public class DiagnosisTest {
    private static File workspace() throws Exception {
        File ws = Files.createTempDirectory("ws").toFile();
        File f = new File(ws, "app/src/Main.java");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), ("class Main {\n    int count = 0;\n    void add() {\n        count++;\n    }\n}\n").getBytes(StandardCharsets.UTF_8));
        File g = new File(ws, "app/src/Other.java");
        Files.write(g.toPath(), "class Other {}\n".getBytes(StandardCharsets.UTF_8));
        return ws;
    }

    @Test
    public void auditsAndDiagnosesGetTheMap() {
        assertTrue(Diagnosis.applies("Audit the login flow"));
        assertTrue(Diagnosis.applies("why does the app crash on start"));
        assertTrue(Diagnosis.applies("find the bugs in the upload code"));
        assertFalse(Diagnosis.applies("add a dark mode switch"));
        assertTrue(Diagnosis.MAP.contains("D2 Evidence first"));
        assertTrue(Diagnosis.MAP.contains("## Not checked"));
    }

    @Test
    public void trueClaimsHoldAndFalseOnesAreCaught() throws Exception {
        File ws = workspace();
        Set<String> read = new HashSet<String>(Collections.singleton("app/src/Main.java"));
        String report = "## Scope\nThe counter.\n## Findings\n"
                + "1. **[MEDIUM] Not thread-safe** - OBSERVED - `app/src/Main.java:4` - `count++;` - lost updates - use AtomicInteger.\n"
                + "2. **[HIGH] Overflow** - OBSERVED - `app/src/Main.java:40` - `count += 2;` - made up.\n"
                + "3. **[LOW] Wrong quote** - OBSERVED - `Main.java:2` - `int count = 5;` - not what the file says.\n"
                + "4. **[LOW] Other is empty** - OBSERVED - `app/src/Other.java:1` - `class Other {}` - never opened.\n"
                + "5. **[HIGH] Leaks memory** - OBSERVED - no file named.\n"
                + "## Not checked\nNothing else.\n## Summary\nDone.";
        Diagnosis.Check c = Diagnosis.verify(report, ws, read);
        assertEquals(4, c.cited);
        assertEquals("only the first holds", 1, c.verified);
        assertEquals(1, c.unsupported);
        String all = String.join("\n", c.problems);
        assertTrue(all, all.contains("Main.java:40 - the file has only 6 lines"));
        assertTrue(all, all.contains("is not at that line"));
        assertTrue(all, all.contains("Other.java:1 - this file was not opened"));
        assertTrue(Diagnosis.correction(c).contains("mark it SUSPECTED"));
        assertTrue(Diagnosis.note(c).contains("1 of 4"));
    }

    @Test
    public void aCleanReportSaysWhatWasChecked() throws Exception {
        File ws = workspace();
        Diagnosis.Check c = Diagnosis.verify("## Findings\n1. **[LOW] x** - OBSERVED - `app/src/Main.java:2` - `int count = 0;` - ok",
                ws, new HashSet<String>(Collections.singleton("app/src/Main.java")));
        assertTrue(c.clean());
        assertTrue(Diagnosis.note(c).contains("1 of 1"));
    }
}
