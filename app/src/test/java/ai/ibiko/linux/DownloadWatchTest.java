package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Whatever the terminal downloads (apk, wget, curl, git, pip), the notification bar is told its progress. */
public class DownloadWatchTest {
    final List<String> shown = new ArrayList<String>();

    @Before
    public void listen() {
        ai.ibiko.net.Transfers.use(new ai.ibiko.net.Transfers.Sink() {
            @Override
            public void progress(String key, String title, String line, int percent) {
                shown.add(title + "|" + percent);
            }

            @Override
            public void end(String key, String title, String line, boolean ok) {
                shown.add("END " + title + "|" + ok);
            }
        });
    }

    @After
    public void quiet() {
        ai.ibiko.net.Transfers.use(null);
    }

    private String last() {
        return shown.isEmpty() ? null : shown.get(shown.size() - 1);
    }

    @Test
    public void apkInstallingPackagesCountsThemOff() {
        DownloadWatch w = new DownloadWatch();
        w.feed("fetch https://dl-cdn.alpinelinux.org/alpine/v3.22/main/aarch64/APKINDEX.tar.gz\r\n", 0);
        assertEquals("Updating the package list|-1", last());
        w.feed("(1/4) Installing ca-certificates (20250619-r0)\r\n(2/4) Installing python3 (3.12.11-r0)\r\n", 100);
        assertEquals("Installing packages|50", last());
        w.feed("(4/4) Installing py3-pip (25.1.1-r0)\r\nOK: 120 MiB in 40 packages\r\n", 200);
        assertEquals("Installing packages|100", last());
        assertTrue("not over while it still talks", !w.tick(300));
        assertTrue("over once it goes quiet", w.tick(200 + DownloadWatch.QUIET_MS + 1));
        assertEquals("END Installing packages|true", last());
        assertNull(w.current());
    }

    @Test
    public void wgetsBarRedrawnInPlace() {
        DownloadWatch w = new DownloadWatch();
        w.feed("Connecting to example.org (93.184.216.34:443)\r\n", 0);
        w.feed("saving to 'model.bin'\r\nmodel.bin             12% |***      |  1.2M  0:00:09 ETA\r", 10);
        assertEquals("Downloading model.bin|12", last());
        w.feed("model.bin             64% |*******  |  6.4M  0:00:02 ETA\r", 20);
        assertEquals("Downloading model.bin|64", last());
    }

    @Test
    public void curlsProgressTable() {
        DownloadWatch w = new DownloadWatch();
        w.feed("  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current\r\n", 0);
        w.feed(" 37 12.3M   37 4660k    0     0  1820k      0  0:00:06  0:00:02  0:00:04 1820k\r", 10);
        assertEquals("Downloading|37", last());
    }

    @Test
    public void aGitClone() {
        DownloadWatch w = new DownloadWatch();
        w.feed("Cloning into 'shop-app'...\r\nremote: Counting objects: 100% (52/52), done.\r\n", 0);
        w.feed("Receiving objects:  45% (230/511), 1.20 MiB | 2.10 MiB/s\r", 10);
        assertEquals("Cloning shop-app|45", last());
    }

    @Test
    public void pipCollectingAndItsBar() {
        DownloadWatch w = new DownloadWatch();
        w.feed("Collecting numpy\r\n  Downloading numpy-2.1.0-cp312.whl (13.6 MB)\r\n", 0);
        w.feed("     ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 6.8/13.6 MB 4.1 MB/s eta 0:00:02\r", 10);
        assertEquals("Installing Python packages|50", last());
    }

    @Test
    public void ordinaryOutputIsNotADownload() {
        DownloadWatch w = new DownloadWatch();
        w.feed("~/workspace $ ls\r\nREADME.md  app  build.gradle\r\n~/workspace $ echo 50% done\r\n50% done\r\n", 0);
        assertTrue(shown.toString(), shown.isEmpty());
        assertNull(w.current());
    }

    @Test
    public void aSilentCloneStillHasATitle() {
        assertEquals("Cloning Dvine-ai-frontend", DownloadWatch.began(
                "git clone https://github.com/DOKTOHSMILE/Dvine-ai-frontend.git"));
        assertNull(DownloadWatch.began("ls /workspace"));
    }
}
