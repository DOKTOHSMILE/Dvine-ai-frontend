package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Reading aloud shows in the panel with Pause (Resume when paused) and Stop, and goes when reading ends. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReadAloudNoticeTest {
    @Test
    public void showsPauseAndStopThenGoes() {
        Context c = RuntimeEnvironment.getApplication();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ReadAloudNotice.show(c, true, false, "Abuja is the capital of Nigeria.", "Obi", ai.ibiko.MainActivity.class);
        Notification n = Shadows.shadowOf(nm).getNotification(ReadAloudNotice.ID);
        assertNotNull(n);
        assertEquals("Obi is reading aloud", n.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Abuja is the capital of Nigeria.", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals("Pause", n.actions[0].title.toString());
        assertEquals("Stop", n.actions[1].title.toString());
        assertTrue("can't be swiped away while reading", (n.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertNotNull("swiping it away stops the reading", n.deleteIntent);
        ReadAloudNotice.show(c, true, true, "Abuja is the capital of Nigeria.", "Ivie", ai.ibiko.MainActivity.class);
        n = Shadows.shadowOf(nm).getNotification(ReadAloudNotice.ID);
        assertEquals("Ivie · paused", n.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Resume", n.actions[0].title.toString());
        ReadAloudNotice.show(c, false, false, null, "", ai.ibiko.MainActivity.class);
        assertNull(Shadows.shadowOf(nm).getNotification(ReadAloudNotice.ID));
    }
}
