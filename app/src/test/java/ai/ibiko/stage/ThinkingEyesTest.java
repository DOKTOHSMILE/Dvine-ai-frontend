package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** While Ibiko works, the chat shows its live face (the icon's face, blinking and glancing), not the old spark. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThinkingEyesTest {
    @Test
    public void theWorkingLineShowsTheEyes() throws Exception {
        ActivityController<ai.ibiko.MainActivity> c = Robolectric.buildActivity(ai.ibiko.MainActivity.class).create().start().resume().visible();
        java.lang.reflect.Field f = ai.ibiko.MainActivity.class.getDeclaredField("chatPane");
        f.setAccessible(true);
        ChatPane pane = (ChatPane) f.get(c.get());
        android.view.View mark = pane.thinkingEyes();
        assertTrue("Ibiko's live face, drawn as it moves, not a short GIF: " + mark.getClass(), mark instanceof LiveFace);
        assertFalse(mark instanceof ThinkingMark);
        assertEquals(LiveFace.Mood.THINKING, ((LiveFace) mark).mood());
        c.pause().stop().destroy();
    }
}
