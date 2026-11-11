package ai.ibiko.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;

/** The first-start download: the right file is unpacked and the app may open; a wrong or broken one leaves it closed. */
public class AppFilesTest {
    public static final String VERSION = AppFiles.CONCEPTS_VERSION;
    static final File GZ = new File("../downloads/knowledge/concepts-" + VERSION + ".tsv.gz");

    @Test
    public void theRightFileIsUnpackedOnceAndTheAppOpens() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        assertFalse(AppFiles.ready(files));
        int[] opened = {0};
        int[] last = {-1};
        AppFiles.Source repo = (url, length) -> {
            opened[0]++;
            assertTrue(url, url.startsWith("https://raw.githubusercontent.com/") && url.endsWith(GZ.getName()));
            length[0] = GZ.length();
            return new FileInputStream(GZ);
        };
        java.util.List<String> lines = new java.util.ArrayList<String>();
        AppFiles.Source both = (url, length) -> url.endsWith("tiny-voice.tar.bz2")
                ? new FileInputStream(new File("src/test/resources/voice/tiny-voice.tar.bz2")) : repo.open(url, length);
        AppFiles.fetch(files, both, (pct, line) -> {
            last[0] = Math.max(last[0], pct);
            lines.add(line);
        }, ai.ibiko.voice.VoicePacksTest.TINY);
        assertTrue(AppFiles.ready(files, ai.ibiko.voice.VoicePacksTest.TINY));
        assertFalse("the real voice is still needed to open", AppFiles.ready(files));
        assertEquals("one bar over every file reached the end", 100, last[0]);
        for (String l : lines) {
            assertFalse("no file is named: " + l, l.contains("concepts") || l.contains("tiny") || l.contains(".tar"));
        }
        File tsv = new File(files, "brain/knowledge/concepts.tsv");
        assertTrue(new String(Files.readAllBytes(tsv.toPath()), "UTF-8").contains("\tUsedFor\t"));
        AppFiles.fetch(files, both, null, ai.ibiko.voice.VoicePacksTest.TINY);
        assertEquals("not downloaded again", 1, opened[0]);
        assertFalse("nothing half-made left", new File(files, "brain/knowledge/concepts.tsv.gz.part").exists());
    }

    @Test
    public void aWrongOrBrokenDownloadKeepsTheAppClosed() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        try {
            AppFiles.fetch(files, (url, length) -> new ByteArrayInputStream("not the file".getBytes("UTF-8")), null,
                    ai.ibiko.voice.VoicePacksTest.TINY);
            fail("a wrong file is refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not the expected file"));
        }
        assertFalse(AppFiles.ready(files));
        try {
            AppFiles.fetch(files, (url, length) -> {
                throw new IOException("no network");
            }, null, ai.ibiko.voice.VoicePacksTest.TINY);
            fail("no network");
        } catch (IOException expected) {
            // the screen offers Try again or Close
        }
        assertFalse(AppFiles.ready(files));
        assertEquals("the pilot is never told the file's name", "Downloading app files", AppFiles.TITLE);
    }
}
