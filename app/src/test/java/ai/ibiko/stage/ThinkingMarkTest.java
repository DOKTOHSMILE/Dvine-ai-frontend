package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThinkingMarkTest {
    @Test
    public void theStarLooksLeftThenRightAndBack() {
        assertEquals(0f, ThinkingMark.look(0f), 1e-6);
        assertEquals("rests on the left", -1f, ThinkingMark.look(0.25f), 1e-6);
        assertEquals("rests on the right", 1f, ThinkingMark.look(0.7f), 1e-6);
        assertEquals("back in the middle", 0f, ThinkingMark.look(0.97f), 1e-6);
        // smooth all the way round: no jumps between frames
        float last = ThinkingMark.look(0f);
        for (int i = 1; i <= 1000; i++) {
            float now = ThinkingMark.look(i / 1000f);
            assertTrue("jump at " + i, Math.abs(now - last) < 0.05f);
            assertTrue(now >= -1f && now <= 1f);
            last = now;
        }
    }
}
