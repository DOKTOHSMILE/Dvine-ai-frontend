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
    public void itStartsWithTheAppAndStopsOnlyWhenClosed() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("dvine", 0).edit().clear().apply();
        ActivityController<ai.ibiko.MainActivity> c = Robolectric.buildActivity(ai.ibiko.MainActivity.class).create();
        Intent started = null;
        Intent i;
        while ((i = Shadows.shadowOf(app).getNextStartedService()) != null) {
            if (i.getComponent() != null && i.getComponent().getClassName().equals(KeepAlive.class.getName())) {
                started = i;
            }
        }
        assertNotNull("the keep-running service starts with Ibiko", started);
        // Close from the notification: remembered, so nothing starts it again until Ibiko is opened
        KeepAlive s = Robolectric.buildService(KeepAlive.class, new Intent(app, KeepAlive.class).setAction(KeepAlive.ACTION_CLOSE)).create().get();
        s.onStartCommand(new Intent(app, KeepAlive.class).setAction(KeepAlive.ACTION_CLOSE), 0, 1);
        assertTrue(app.getSharedPreferences("dvine", 0).getBoolean("closed_by_pilot", false));
        KeepAlive.opened(app);
        assertFalse("opening Ibiko again clears the Close", app.getSharedPreferences("dvine", 0).getBoolean("closed_by_pilot", false));
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
        c.destroy();
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
}
