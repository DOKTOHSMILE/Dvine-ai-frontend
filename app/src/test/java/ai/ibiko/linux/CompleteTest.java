package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

public class CompleteTest {
    @Test
    public void tabFillsTheOnlyMatchAndListsSeveral() throws Exception {
        File root = Files.createTempDirectory("dvine-complete").toFile();
        File bin = new File(root, "bin");
        bin.mkdirs();
        new File(bin, "echo").createNewFile();
        File home = new File(root, "root");
        home.mkdirs();
        new File(home, "notes.txt").createNewFile();
        new File(home, "nested").mkdirs();
        Complete.Hit one = Complete.tab(root, "/root", "ec", 2);
        assertEquals("echo ", one.line);
        assertEquals("", one.shown);
        Complete.Hit many = Complete.tab(root, "/root", "n", 1);
        assertTrue(many.shown.contains("notes.txt"));
        assertTrue(many.shown.contains("nested/"));
        Complete.Hit dir = Complete.tab(root, "/root", "nest", 4);
        assertEquals("nested/", dir.line);
    }
}
