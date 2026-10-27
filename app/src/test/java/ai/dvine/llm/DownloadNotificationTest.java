package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The download notifications build on Android 14: a progress bar with the percent, size, speed and time, and a Pause button. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DownloadNotificationTest {
    @Test
    public void showsProgressAndResult() {
        Context c = RuntimeEnvironment.getApplication();
        ModelDownloadService.channel(c);
        DownloadStatus s = new DownloadStatus();
        s.begin(1_000_000_000L, 0, 0);
        s.update(250_000_000L, 50_000L);
        Notification n = ModelDownloadService.progress(c, s);
        assertEquals(100, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX));
        assertEquals(25, n.extras.getInt(Notification.EXTRA_PROGRESS));
        assertTrue(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)).endsWith("25%"));
        assertTrue(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)).startsWith("250 MB of 1.0 GB"));
        assertEquals("Pause", String.valueOf(n.actions[0].title));
        assertTrue((n.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        s.finish(DownloadStatus.Phase.DONE);
        Notification done = ModelDownloadService.result(c, s);
        assertEquals("DVINE's language model is ready", String.valueOf(done.extras.getCharSequence(Notification.EXTRA_TITLE)));
    }
}
