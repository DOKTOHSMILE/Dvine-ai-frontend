package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The download's progress, speed and time left, as shown on screen, in the notification and when asked. */
public class DownloadStatusTest {
    @Test
    public void tracksProgressSpeedAndTimeLeft() {
        DownloadStatus s = new DownloadStatus();
        assertEquals("There's no download running.", s.spoken());
        long total = 1_107_409_472L;
        s.begin(total, 0, 0);
        assertTrue(s.active() && s.detail().equals("connecting…"));
        // 2 MB/s for 100 seconds
        for (int t = 1; t <= 100; t++) {
            s.update(t * 2_000_000L, t * 1000L);
        }
        assertEquals(18, s.percent());
        assertTrue(s.detail(), s.detail().startsWith("200 MB of 1.1 GB · 2.0 MB/s · "));
        long eta = s.etaSeconds();
        assertTrue("eta " + eta, eta > 440 && eta < 470); // about 907 MB left at 2 MB/s
        assertEquals("It's 18 percent done, about 8 minutes to go.", s.spoken());
        s.finish(DownloadStatus.Phase.PAUSED);
        assertFalse(s.active());
        assertTrue(s.spoken().startsWith("It's paused at 18 percent"));
        s.finish(DownloadStatus.Phase.DONE);
        assertEquals(100, s.percent());
        assertEquals("downloaded and verified", s.detail());
        // a resume starts from what is on disk
        DownloadStatus r = new DownloadStatus();
        r.begin(total, 500_000_000L, 0);
        assertEquals("resuming at 500 MB…", r.detail());
        assertEquals(45, r.percent());
    }

    @Test
    public void timesReadNaturally() {
        assertEquals("less than a minute", DownloadStatus.etaWords(40));
        assertEquals("a minute", DownloadStatus.etaWords(70));
        assertEquals("12 minutes", DownloadStatus.etaWords(12 * 60));
        assertEquals("an hour and 20 minutes", DownloadStatus.etaWords(80 * 60));
        assertEquals("2 hours", DownloadStatus.etaWords(121 * 60));
        assertEquals("3 min", DownloadStatus.etaShort(170));
        assertEquals("1.10 GB", DownloadStatus.mb(1_100_000_000L));
    }
}
