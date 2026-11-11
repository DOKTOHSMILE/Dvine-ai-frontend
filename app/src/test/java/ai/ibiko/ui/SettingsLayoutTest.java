package ai.ibiko.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.chat.Tuning;
import ai.ibiko.chat.ui.SettingsActivity;

/** Settings: every section closed until tapped, and no slider in the scrolling list for a finger to move. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsLayoutTest {
    private static int seekBars(View v) {
        int n = v instanceof android.widget.SeekBar ? 1 : 0;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                n += seekBars(((ViewGroup) v).getChildAt(i));
            }
        }
        return n;
    }

    @Test
    public void sectionsAreClosedAndTheListHasNoSliders() {
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View root = c.get().getWindow().getDecorView();
        java.util.ArrayList<View> found = new java.util.ArrayList<View>();
        root.findViewsWithText(found, "Back to the defaults", View.FIND_VIEWS_WITH_TEXT);
        assertTrue("Advanced is closed until opened", found.isEmpty());
        root.findViewsWithText(found, "Advanced", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertFalse(found.isEmpty());
        assertEquals("the list builds no section's controls", 0, seekBars(root));
        found.get(0).performClick();
        // the section opens on its own page, built only then
        android.content.Intent page = org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertEquals("Advanced", page.getStringExtra(SettingsActivity.EXTRA_SECTION));
        ActivityController<SettingsActivity> p = Robolectric.buildActivity(SettingsActivity.screen(page), page).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View proot = p.get().getWindow().getDecorView();
        found.clear();
        proot.findViewsWithText(found, "Back to the defaults", View.FIND_VIEWS_WITH_TEXT);
        assertFalse("its settings are on its page", found.isEmpty());
        found.clear();
        proot.findViewsWithText(found, "Change the offline model", View.FIND_VIEWS_WITH_TEXT);
        assertTrue("only this section is built, not the others", found.isEmpty());
        assertEquals("the numbers are rows; a slider only inside a number's own editor", 0, seekBars(proot));
        p.pause().stop().destroy();
        c.pause().stop().destroy();
    }

    @Test
    public void numbersAnAccidentalSliderMovedGoBackOnce() {
        SharedPreferences p = org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("t", 0);
        p.edit().putInt(Tuning.CHAT_TOKENS.key, 64).apply();
        assertTrue(Tuning.resetOnce(p));
        assertEquals(Tuning.CHAT_TOKENS.def, Tuning.CHAT_TOKENS.get(p));
        p.edit().putInt(Tuning.CHAT_TOKENS.key, 300).apply();
        assertFalse("only once: a value set after the reset stays", Tuning.resetOnce(p));
        assertEquals(300, Tuning.CHAT_TOKENS.get(p));
    }
}
