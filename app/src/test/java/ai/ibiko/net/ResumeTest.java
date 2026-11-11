package ai.ibiko.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/** A broken download carries on where it stopped, not from the start. */
public class ResumeTest {
    static byte[] data(int n) {
        byte[] b = new byte[n];
        new Random(3).nextBytes(b);
        return b;
    }

    /** A server that honours Range and whose connection drops after {@code breakAfter} bytes, {@code breaks} times. */
    static Resume.Source server(byte[] all, int breakAfter, int[] breaks, List<Long> asked, boolean ranges) {
        return (url, from, info) -> {
            asked.add(from);
            int start = ranges ? (int) from : 0;
            info[0] = all.length - start;
            info[1] = ranges && from > 0 ? 1 : 0;
            final boolean drop = breaks[0]-- > 0;
            return new InputStream() {
                int at = start, sent = 0;

                @Override
                public int read() throws IOException {
                    if (drop && sent >= breakAfter) {
                        throw new IOException("Connection reset");
                    }
                    if (at >= all.length) {
                        return -1;
                    }
                    sent++;
                    return all[at++] & 0xff;
                }
            };
        };
    }

    @Test
    public void aBrokenDownloadCarriesOnFromWhereItStopped() throws Exception {
        byte[] all = data(300_000);
        File part = File.createTempFile("download", ".part");
        part.delete();
        List<Long> asked = new ArrayList<Long>();
        List<Long> waits = new ArrayList<Long>();
        Resume.fetch("https://x/file", part, server(all, 100_000, new int[] {2}, asked, true), null, Resume.TRIES, waits::add);
        assertArrayEquals(all, Files.readAllBytes(part.toPath()));
        assertEquals("from the start, then from where each break left it", List.of(0L, 100_000L, 200_000L), asked);
        assertEquals("2 s, then 4 s before carrying on", List.of(2000L, 4000L), waits);
    }

    @Test
    public void tryAgainLaterCarriesOnFromThePartKept() throws Exception {
        byte[] all = data(200_000);
        File part = File.createTempFile("download", ".part");
        part.delete();
        List<Long> asked = new ArrayList<Long>();
        try {
            Resume.fetch("https://x/file", part, server(all, 50_000, new int[] {99}, asked, true), null, 2, ms -> { });
            fail("the network keeps breaking");
        } catch (IOException expected) {
            assertEquals("what arrived is kept", 100_000, part.length());
        }
        asked.clear();
        Resume.fetch("https://x/file", part, server(all, 0, new int[] {0}, asked, true), null, 1, ms -> { });
        assertEquals("Try again asks only for the rest", List.of(100_000L), asked);
        assertArrayEquals(all, Files.readAllBytes(part.toPath()));
    }

    @Test
    public void aServerThatWontSendPartsStartsOver() throws Exception {
        byte[] all = data(50_000);
        File part = File.createTempFile("download", ".part");
        Files.write(part.toPath(), new byte[] {9, 9, 9});
        Resume.fetch("https://x/file", part, server(all, 0, new int[] {0}, new ArrayList<Long>(), false), null, 1, ms -> { });
        assertArrayEquals("not appended to the old part", all, Files.readAllBytes(part.toPath()));
    }

    @Test
    public void theWholeFileAlreadyThereIsDone() throws Exception {
        byte[] all = data(1000);
        File part = File.createTempFile("download", ".part");
        Files.write(part.toPath(), all);
        Resume.fetch("https://x/file", part, (url, from, info) -> {
            info[0] = 0;
            info[1] = 1; // 416: nothing left to send
            return new ByteArrayInputStream(new byte[0]);
        }, null, 1, ms -> { });
        assertArrayEquals(all, Files.readAllBytes(part.toPath()));
        assertTrue(Resume.sha256(part).length() == 64);
    }
}
