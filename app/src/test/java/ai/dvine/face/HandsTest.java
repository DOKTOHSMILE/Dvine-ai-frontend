package ai.dvine.face;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HandsTest {
    @Test
    public void everyGestureProducesAFiniteHand() {
        assertTrue(Gestures.count() >= 40);
        WireHand hand = new WireHand();
        float[] out = new float[hand.vertexCount() * 3];
        String[] keys = {"👍", "👎", "🫶", "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊", "🫳", "🫴", "🫱", "🫲", "🫸", "🫷",
                "👋", "🤚", "🖐", "✋", "🖖", "🤟", "🤘", "✌", "🤞", "🫰", "🤙", "🤌", "🤏", "👌", "🫵", "👉", "👈",
                "☝", "👆", "👇", "✍", "🤳", "🙏", "💅", "🤝"};
        for (String k : keys) {
            Gestures.Gesture g = Gestures.get(k + "🏽");
            assertNotNull(k, g);
            hand.pose(g.pose, false, 0.6f, g.x, g.y, 0f, out);
            for (float v : out) {
                assertFalse(k, Float.isNaN(v) || Float.isInfinite(v));
            }
        }
        for (int e : hand.edges) {
            assertTrue(e >= 0 && e < hand.vertexCount());
        }
    }

    @Test
    public void sketchesHaveStrokes() {
        String[] asks = {"draw a circle", "draw a square", "explain a half", "explain the solar system", "draw steps",
                "draw a heart", "draw a star", "explain the water cycle", "explain 2 plus 5"};
        for (String a : asks) {
            Sketch s = Sketch.forRequest(a);
            assertNotNull(a, s);
            assertTrue(a, s.strokes.size() > 0 && s.length > 0f);
        }
    }
}
