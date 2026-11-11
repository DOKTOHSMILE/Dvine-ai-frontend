package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class LinuxEngineTest {
    @Test
    public void theShellKnowsItsOwnCommandsOnly() {
        assertTrue(LinuxEngine.knows("unzip report.zip"));
        assertTrue(LinuxEngine.knows("unzip -n report.zip -d out"));
        assertTrue(LinuxEngine.knows("ls"));
        assertTrue(LinuxEngine.knows("pwd && ls"));
        assertTrue(LinuxEngine.knows("echo hello > notes.txt"));
        assertFalse(LinuxEngine.knows("unzip ../report.zip"));
        assertFalse(LinuxEngine.knows("unzip report.zip && rm -rf x"));
        assertFalse(LinuxEngine.knows("python3 demo.py"));
        assertFalse(LinuxEngine.knows("echo hi &"));
        assertFalse(LinuxEngine.updatesEnvironment("pkg update && pkg upgrade -y"));
    }

    @Test
    public void unzipRunsInsideTheApp() throws Exception {
        File root = Files.createTempDirectory("ibiko-linux").toFile();
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
        File root = Files.createTempDirectory("ibiko-linux-bad").toFile();
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

    @Test
    public void aShellChainPipesAndRedirects() throws Exception {
        File root = Files.createTempDirectory("ibiko-shell").toFile();
        LinuxEngine.Result wrote = LinuxEngine.run("mkdir box && echo hello > box/note.txt && cat box/note.txt", root, null);
        assertTrue(wrote.text, wrote.ok);
        assertEquals("hello", new String(Files.readAllBytes(new File(root, "box/note.txt").toPath())).trim());
        LinuxEngine.Result piped = LinuxEngine.run("printf 'b\\na\\n' | sort", root, null);
        assertTrue(piped.text, piped.ok);
        assertTrue(piped.text.startsWith("a\nb\n"));
        LinuxEngine.Result recovered = LinuxEngine.run("rm missing.txt || echo recovered", root, null);
        assertTrue(recovered.ok);
        assertTrue(recovered.text.contains("recovered"));
        LinuxEngine.Result home = LinuxEngine.run("echo $HOME", root, null);
        assertTrue(home.text.contains(root.getAbsolutePath()));
        LinuxEngine.Result missing = LinuxEngine.run("python3 --version", root, null);
        assertFalse(missing.ok);
        assertTrue(missing.text.contains("[exit 127]"));
        assertTrue(missing.text.contains("not bundled"));
        LinuxEngine.Result background = LinuxEngine.run("echo hi &", root, null);
        assertFalse(background.ok);
        assertTrue(background.text, background.text.contains("Background"));
        LinuxEngine.Result url = LinuxEngine.run("echo http://example.com?a=1&b=2", root, null);
        assertFalse(url.ok);
        assertTrue(url.text.contains("Background"));
        assertTrue(new File(root, "workspace/projects").isDirectory());
    }
}
