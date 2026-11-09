package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.widget.SeekBar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/** Every Advanced number is a slider: round stops over wide ranges, every number over short ones, kept as it moves. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
public class TuningSliderTest {
    @Test
    public void stopsAreRoundAndIncludeTheEnds() {
        int[] tokens = Tuning.AGENT_TOKENS.stops(); // 256 to 32,000
        assertEquals(256, tokens[0]);
        assertEquals(32000, tokens[tokens.length - 1]);
        assertTrue(contains(tokens, 4000) && contains(tokens, 1000) && contains(tokens, 8000) && contains(tokens, 300));
        assertTrue(tokens.length < 40); // round steps, not 31,744 of them
        int[] steps = Tuning.AGENT_STEPS.stops(); // 3 to 100: every number
        assertEquals(98, steps.length);
        assertEquals("Automatic", Tuning.MODEL_THREADS.shown(0));
        assertEquals("8,192", Tuning.OFFLINE_CONTEXT.shown(8192));
        assertEquals(8192, Tuning.OFFLINE_CONTEXT.stops()[Tuning.OFFLINE_CONTEXT.stopFor(8192)]);
    }

    @Test
    public void aNumberChangesOnlyInItsEditorAndOnlyWithSave() throws Exception {
        ActivityController<ai.dvine.chat.ui.SettingsActivity> c = Robolectric.buildActivity(ai.dvine.chat.ui.SettingsActivity.class)
                .create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        SharedPreferences prefs = c.get().getSharedPreferences("dvine", 0);
        android.view.View root = c.get().getWindow().getDecorView();
        java.util.ArrayList<android.view.View> found = new java.util.ArrayList<android.view.View>();
        root.findViewsWithText(found, "Advanced", android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        found.get(0).performClick(); // Advanced is closed until opened
        found.clear();
        root.findViewsWithText(found, Tuning.AGENT_TOKENS.name, android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertEquals(1, found.size());
        assertTrue("a row, not a slider a finger can move while scrolling", !(found.get(0) instanceof SeekBar));
        found.get(0).performClick();
        ShadowLooper.idleMainLooper();
        androidx.appcompat.app.AlertDialog d = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        SeekBar bar = findBar(d.getWindow().getDecorView());
        int[] stops = Tuning.AGENT_TOKENS.stops();
        assertEquals(4000, stops[bar.getProgress()]); // the default
        bar.setProgress(Tuning.AGENT_TOKENS.stopFor(8000));
        org.robolectric.Shadows.shadowOf(bar).getOnSeekBarChangeListener().onProgressChanged(bar, bar.getProgress(), true);
        assertEquals("nothing is kept before Save", 4000, Tuning.AGENT_TOKENS.get(prefs));
        d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(8000, Tuning.AGENT_TOKENS.get(prefs));
        java.util.ArrayList<android.view.View> shown = new java.util.ArrayList<android.view.View>();
        c.get().getWindow().getDecorView().findViewsWithText(shown, "8,000", android.view.View.FIND_VIEWS_WITH_TEXT);
        assertTrue(!shown.isEmpty()); // the row shows the new value
        prefs.edit().remove(Tuning.AGENT_TOKENS.key).commit();
        c.pause().stop().destroy();
    }

    private static SeekBar findBar(android.view.View v) {
        if (v instanceof SeekBar) {
            return (SeekBar) v;
        }
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) {
                SeekBar s = findBar(((android.view.ViewGroup) v).getChildAt(i));
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    private static android.widget.ScrollView find(android.view.View v) {
        if (v instanceof android.widget.ScrollView) {
            return (android.widget.ScrollView) v;
        }
        if (v instanceof android.view.ViewGroup) {
            for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) {
                android.widget.ScrollView s = find(((android.view.ViewGroup) v).getChildAt(i));
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }
}
