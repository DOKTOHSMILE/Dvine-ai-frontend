package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.junit.Test;

/** The human voices: bzip2 and tar read in pure Java, a pack checked and unpacked, Kokoro's men and women. */
public class VoicePacksTest {
    static final File RES = new File("src/test/resources/voice");
    /** A pack laid out as the real ones (tools: Python's bz2 and tarfile, GNU format, a 130-letter name). */
    public static final VoicePacks.Pack TINY = new VoicePacks.Pack("tiny", "Tiny", "test", "https://example.invalid/tiny-voice.tar.bz2",
            "e1ef572a40b17ae9c325b94986986cda43d3adeeea80eded2a8bc624aa296f85", 273, "tiny-voice", "model.onnx", "vits",
            new VoicePacks.Speaker(0, "tiny", "Tiny (test)", true));

    static String sha(byte[] b) throws Exception {
        StringBuilder h = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) {
            h.append(String.format("%02x", x));
        }
        return h.toString();
    }

    @Test
    public void bzip2ManyBlocksRunsAndTwoStreams() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (InputStream in = new Bzip2InputStream(new FileInputStream(new File(RES, "sample.bz2")))) {
            byte[] buf = new byte[777];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        assertEquals(431598 + "second stream\n".length(), out.size());
        assertEquals("the same bytes as Python's bz2 made it from",
                "941e0a29b99c9fe27fd0128387dfe19911b51210402e219dcfb6440542a6b281", sha(out.toByteArray()));
    }

    @Test
    public void damagedBzip2IsRefused() throws Exception {
        byte[] b = Files.readAllBytes(new File(RES, "sample.bz2").toPath());
        b[5000] ^= 0x10;
        try (InputStream in = new Bzip2InputStream(new ByteArrayInputStream(b))) {
            byte[] buf = new byte[65536];
            while (in.read(buf) > 0) {
                // reading until the damage is found
            }
            fail("a damaged block is refused");
        } catch (IOException expected) {
            // the checksum or the block's coding gives it away
        }
    }

    @Test
    public void aPackIsCheckedAndUnpacked() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        assertFalse(VoicePacks.installed(files, TINY));
        int[] last = {-1};
        VoicePacks.install(files, TINY, (url, len) -> new FileInputStream(new File(RES, "tiny-voice.tar.bz2")),
                (stage, pct) -> last[0] = pct);
        assertTrue(VoicePacks.installed(files, TINY));
        assertEquals(100, last[0]);
        File d = VoicePacks.folder(files, TINY);
        assertTrue(new File(d, "model.onnx").isFile());
        assertTrue("a GNU long name", new File(d, "espeak-ng-data/" + "x".repeat(120)).isFile());
        assertFalse("nothing left over", new File(VoicePacks.root(files), "tiny.tar.bz2.part").exists());
        VoicePacks.remove(files, TINY);
        assertFalse(VoicePacks.installed(files, TINY));
    }

    @Test
    public void aWrongDownloadIsNotInstalled() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        try {
            VoicePacks.install(files, TINY, (url, len) -> new ByteArrayInputStream("nope".getBytes("UTF-8")), null);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not the expected file"));
        }
        assertFalse(VoicePacks.installed(files, TINY));
    }

    @Test
    public void kokoroHasMenAndWomenAndBothAreChosen() {
        VoicePacks.Pack k = VoicePacks.KOKORO;
        assertEquals(11, k.speakers.size());
        for (int id : new int[] {5, 6, 9, 10}) {
            boolean found = false;
            for (VoicePacks.Speaker s : k.speakers) {
                if (s.id == id) {
                    assertTrue("speaker " + id + " is a man's voice (98 to 145 Hz)", s.male);
                    found = true;
                }
            }
            assertTrue(found);
        }
        assertEquals("am_michael", k.speaker(true, "").name);
        assertEquals("af_bella", k.speaker(false, "").name);
        assertEquals("bf_emma", k.speaker(false, "bf_emma").name);
        assertNotNull(VoicePacks.byKey("piper-ryan"));
        assertTrue(VoicePacks.PIPER_RYAN.speakers.get(0).male);
        assertFalse(VoicePacks.PIPER_AMY.speakers.get(0).male);
    }

    @Test
    public void aTarNameOutsideTheFolderIsRefused() throws Exception {
        byte[] h = new byte[1024 + 512];
        byte[] name = "../evil.txt".getBytes("UTF-8");
        System.arraycopy(name, 0, h, 0, name.length);
        byte[] size = "00000000004\0".getBytes("UTF-8");
        System.arraycopy(size, 0, h, 124, size.length);
        h[156] = '0';
        try {
            TarUnpack.unpack(new ByteArrayInputStream(h), Files.createTempDirectory("t").toFile(), null);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("outside"));
        }
    }
}
