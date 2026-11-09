package ai.dvine.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class LinuxEngineTest {
    @Test
    public void onlyUnzipIsItsFunction() {
        assertTrue(LinuxEngine.knows("unzip report.zip"));
        assertTrue(LinuxEngine.knows("unzip -n report.zip -d out"));
        assertFalse(LinuxEngine.knows("ls"));
        assertFalse(LinuxEngine.knows("unzip ../report.zip"));
        assertFalse(LinuxEngine.knows("unzip report.zip && rm -rf x"));
        assertNull(LinuxEngine.run("ls", new File("."), null));
    }

    @Test
    public void unzipRunsInsideTheApp() throws Exception {
        File root = Files.createTempDirectory("dvine-linux").toFile();
        File zip = new File(root, "report.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("note.txt"));
            out.write("hello".getBytes());
            out.closeEntry();
        }
        LinuxEngine.Result ran = LinuxEngine.run("unzip report.zip -d unpacked", root, null);
        assertTrue(ran.ok);
        assertTrue(ran.text.contains("Termux was not opened"));
        assertEquals("hello", new String(Files.readAllBytes(new File(root, "unpacked/note.txt").toPath())));
    }

    @Test
    public void aZipThatEscapesIsRefused() throws Exception {
        File root = Files.createTempDirectory("dvine-linux-bad").toFile();
        File zip = new File(root, "bad.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../outside.txt"));
            out.write("no".getBytes());
            out.closeEntry();
        }
        LinuxEngine.Result ran = LinuxEngine.run("unzip bad.zip", root, null);
        assertFalse(ran.ok);
        assertTrue(ran.text.contains("outside"));
        assertFalse(new File(root.getParentFile(), "outside.txt").exists());
    }
}
