package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The floating player: a ring turns where play or pause is while the voice gets ready; its buttons are spaced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlayerBarTest {
    private static PlayerBar bar() {
        return new PlayerBar(RuntimeEnvironment.getApplication(), new PlayerBar.Control() {
            @Override
            public void playPause() {
            }

            @Override
            public boolean skip(int seconds) {
                return true;
            }

            @Override
            public void speed(float s) {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    public void ringWhileTheVoiceGetsReady() {
        PlayerBar b = bar();
        assertEquals(View.GONE, b.getVisibility());
        b.loading(true);
        assertEquals("shown at once, before the first sound", View.VISIBLE, b.getVisibility());
        ViewGroup first = (ViewGroup) b.getChildAt(0);
        assertEquals("play or pause hidden", View.INVISIBLE, first.getChildAt(0).getVisibility());
        assertEquals("the ring turns", View.VISIBLE, first.getChildAt(1).getVisibility());
        b.state(true, false, true);
        b.loading(false);
        assertEquals(View.VISIBLE, first.getChildAt(0).getVisibility());
        assertEquals(View.GONE, first.getChildAt(1).getVisibility());
        assertTrue(b.getVisibility() == View.VISIBLE);
        b.state(false, false, false);
        assertEquals(View.GONE, b.getVisibility());
        b.loading(true);
        b.loading(false);
        assertFalse(b.isLoading());
        assertEquals("a ring that ends without sound hides the bar", View.GONE, b.getVisibility());
    }

    @Test
    public void buttonsAreSpaced() {
        PlayerBar b = bar();
        for (int i = 1; i < b.getChildCount(); i++) {
            assertTrue(((ViewGroup.MarginLayoutParams) b.getChildAt(i).getLayoutParams()).leftMargin > 0);
        }
    }
}
