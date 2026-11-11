package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** While Ibiko works, the chat shows the pilot's own animation: its eyes glancing about, not the old spark. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThinkingEyesTest {
    @Test
    public void theAnimationShipsWithAllItsFrames() throws Exception {
        byte[] gif = Files.readAllBytes(new File("src/main/res/raw/thinking_eyes.gif").toPath());
        assertEquals("GIF89a", new String(gif, 0, 6, "US-ASCII"));
        int frames = 0;
        for (int i = 0; i + 1 < gif.length; i++) {
            if ((gif[i] & 0xff) == 0x21 && (gif[i + 1] & 0xff) == 0xF9) {
                frames++; // one graphic-control block before each frame
            }
        }
        assertEquals(18, frames);
        assertTrue("small enough for the chat", gif.length < 200_000);
    }

    @Test
    public void theWorkingLineShowsTheEyes() throws Exception {
        ActivityController<ai.ibiko.MainActivity> c = Robolectric.buildActivity(ai.ibiko.MainActivity.class).create().start().resume().visible();
        java.lang.reflect.Field f = ai.ibiko.MainActivity.class.getDeclaredField("chatPane");
        f.setAccessible(true);
        ChatPane pane = (ChatPane) f.get(c.get());
        android.view.View mark = pane.thinkingEyes();
        assertTrue("an animated picture, not the drawn spark: " + mark.getClass(), mark instanceof android.widget.ImageView);
        assertFalse(mark instanceof ThinkingMark);
        assertTrue(((android.widget.ImageView) mark).getDrawable() != null);
        c.pause().stop().destroy();
    }
}
