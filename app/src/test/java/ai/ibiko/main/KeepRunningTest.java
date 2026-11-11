package ai.ibiko.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Ibiko keeps running until the pilot closes it; Back doesn't quit; the download screen gives something to read. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class KeepRunningTest {
    @Test
    public void backGoesToTheBackgroundInsteadOfQuitting() {
        ActivityController<ai.ibiko.MainActivity> c = Robolectric.buildActivity(ai.ibiko.MainActivity.class).create().start().resume().visible();
        c.get().onBackPressed();
        assertFalse("Back doesn't close Ibiko", c.get().isFinishing());
        c.pause().stop().destroy();
    }

    @Test
    public void nothingRunsIdleAndCloseIsRemembered() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("ibiko", 0).edit().clear().apply();
        ActivityController<ai.ibiko.MainActivity> c = Robolectric.buildActivity(ai.ibiko.MainActivity.class).create();
        Intent i;
        while ((i = Shadows.shadowOf(app).getNextStartedService()) != null) {
            boolean keep = i.getComponent() != null && i.getComponent().getClassName().equals(KeepAlive.class.getName());
            assertFalse("idle, no background service and no notification", keep);
        }
        // Close from the notification: remembered, so no work starts it again until Ibiko is opened
        KeepAlive s = Robolectric.buildService(KeepAlive.class, new Intent(app, KeepAlive.class).setAction(KeepAlive.ACTION_CLOSE)).create().get();
        s.onStartCommand(new Intent(app, KeepAlive.class).setAction(KeepAlive.ACTION_CLOSE), 0, 1);
        assertTrue(app.getSharedPreferences("ibiko", 0).getBoolean("closed_by_pilot", false));
        assertFalse("closed: work shows its own notification", KeepAlive.status(app, "dl:y", "Cloning", "", 10));
        KeepAlive.opened(app);
        assertFalse("opening Ibiko again clears the Close", app.getSharedPreferences("ibiko", 0).getBoolean("closed_by_pilot", false));
        c.destroy();
    }

    @Test
    public void theLinesRunThroughAllBeforeRepeating() {
        WaitLines w = new WaitLines(new Random(5));
        Set<String> seen = new HashSet<String>();
        String prev = null;
        for (int i = 0; i < w.size(); i++) {
            WaitLines.Line l = w.next();
            assertTrue("no repeat in a round: " + l.text, seen.add(l.text));
            prev = l.text;
        }
        assertEquals(WaitLines.ALL.length, seen.size());
        assertNotEquals("never the same line twice in a row", prev, w.next().text);
    }

    @Test
    public void theDownloadScreenGivesSomethingToRead() {
        ActivityController<ai.ibiko.chat.ui.AppFilesActivity> c = Robolectric.buildActivity(ai.ibiko.chat.ui.AppFilesActivity.class).setup();
        Shadows.shadowOf(c.get().getMainLooper()).idle();
        Set<String> shown = new HashSet<String>();
        collect(c.get().getWindow().getDecorView(), shown);
        boolean line = false;
        for (String[] l : WaitLines.ALL) {
            line |= shown.contains(l[1]);
        }
        assertTrue("a line is on screen: " + shown, line);
        assertTrue("facts about the app roll underneath", shown.contains("\u2022  " + WaitLines.APP_FACTS[0]));
        android.widget.ScrollView reel = find(c.get().getWindow().getDecorView());
        assertNotNull(reel);
        for (int k = 0; k < 120; k++) {
            Shadows.shadowOf(c.get().getMainLooper()).idleFor(java.time.Duration.ofMillis(17));
        }
        assertTrue("it rolls by itself: " + reel.getScrollY(), reel.getScrollY() > 0);
        c.destroy();
    }

    private static android.widget.ScrollView find(android.view.View v) {
        if (v instanceof android.widget.ScrollView) {
            return (android.widget.ScrollView) v;
        }
        if (v instanceof android.view.ViewGroup) {
            for (int k = 0; k < ((android.view.ViewGroup) v).getChildCount(); k++) {
                android.widget.ScrollView f = find(((android.view.ViewGroup) v).getChildAt(k));
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static void collect(android.view.View v, Set<String> out) {
        if (v instanceof TextView) {
            out.add(((TextView) v).getText().toString());
        }
        if (v instanceof android.view.ViewGroup) {
            for (int k = 0; k < ((android.view.ViewGroup) v).getChildCount(); k++) {
                collect(((android.view.ViewGroup) v).getChildAt(k), out);
            }
        }
    }

    /** One notification while Ibiko runs: work shows in it, not in notifications of its own. */
    @Test
    public void workShowsInTheOneNotification() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("ibiko", 0).edit().clear().apply();
        KeepAlive.running = false;
        assertTrue("work starts the service", KeepAlive.status(app, "dl:x", "Cloning a/b", "Downloading main", 40));
        Intent started = Shadows.shadowOf(app).getNextStartedService();
        assertNotNull(started);
        assertEquals(KeepAlive.class.getName(), started.getComponent().getClassName());
        KeepAlive.running = true;
        try {
            assertTrue(KeepAlive.status(app, "reply", "Ibiko is thinking", "The reply will be in the chat.", -2));
            assertTrue(KeepAlive.status(app, "dl:x", "Cloning a/b", "Downloading main", 40));
            android.app.Notification n = KeepAlive.note(app);
            assertEquals("Cloning a/b \u00B7 40%", n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString());
            assertEquals("1 more running", n.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT).toString());
            new ai.ibiko.chat.ui.DownloadNotice(app).end("x", "Cloning a/b", "done", true);
            n = KeepAlive.note(app);
            assertEquals("Ibiko is thinking", n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString());
            KeepAlive.done(app, "reply");
            assertTrue("no work left: nothing to keep", KeepAlive.idle());
            android.app.NotificationManager nm = app.getSystemService(android.app.NotificationManager.class);
            assertEquals("no download notification of its own", 0,
                    Shadows.shadowOf(nm).getAllNotifications().stream().filter(x -> "downloads".equals(x.getChannelId())).count());
        } finally {
            KeepAlive.running = false;
        }
    }
}
