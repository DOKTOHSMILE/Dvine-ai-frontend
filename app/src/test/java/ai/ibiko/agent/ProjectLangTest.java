package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/** A Java tree is not sent looking for Kotlin files that are not there. */
public class ProjectLangTest {
    @Test
    public void aJavaTreeRefusesKotlinAndAKotlinTreeDoesNot() throws Exception {
        File root = Files.createTempDirectory("lang").toFile();
        new File(root, "app/src").mkdirs();
        Files.write(new File(root, "app/src/Main.java").toPath(), "class Main {}".getBytes("UTF-8"));
        Files.write(new File(root, "app/src/Other.java").toPath(), "class Other {}".getBytes("UTF-8"));
        new File(root, "build").mkdirs();
        Files.write(new File(root, "build/Gen.kt").toPath(), "class Gen".getBytes("UTF-8"));
        ProjectLang java = ProjectLang.of(root);
        assertEquals(2, java.javaFiles);
        assertEquals("build output is not the language", 0, java.kotlinFiles);
        assertTrue(java.rule().contains("no Kotlin") && java.rule().contains("2"));
        assertTrue(java.blocked("app/src/Main.kt", "read_file").contains("no Kotlin"));
        assertTrue(java.blocked("app/src/Main.kt", "write_file").contains("Not written"));
        assertNull(java.blocked("app/src/Main.java", "write_file"));
        assertTrue(java.searchNote("Models.kt").contains("not a file"));
        assertNull(java.searchNote("login"));

        Files.write(new File(root, "app/src/Screen.kt").toPath(), "class Screen".getBytes("UTF-8"));
        ProjectLang both = ProjectLang.of(root);
        assertEquals(1, both.kotlinFiles);
        assertEquals("", both.rule());
        assertNull(both.blocked("app/src/Screen.kt", "read_file"));
    }
}
