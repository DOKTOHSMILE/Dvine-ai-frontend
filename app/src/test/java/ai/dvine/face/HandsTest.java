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

    @Test
    public void skinTrianglesAreValidAndOrdered() {
        WireHand hand = new WireHand();
        for (int t : hand.tris) {
            assertTrue(t >= 0 && t < hand.vertexCount());
        }
        WireHead head = WireHead.create(true);
        float[] d = new float[head.rest.length];
        float[] s = new float[head.rest.length];
        head.deform(new WireHead.Pose(), d);
        WireHead.project(d, head.vertexCount(), -30f, 0f, 0f, 300f, 300f, 150f, s);
        long[] keys = new long[head.tris.length / 3];
        short[] out = new short[head.tris.length];
        int n = Shade.order(s, head.tris, -0.05f, keys, out);
        assertTrue(n > 0 && n % 3 == 0);
        float prev = -Float.MAX_VALUE;
        for (int i = 0; i < n; i += 3) {
            float z = (s[out[i] * 3 + 2] + s[out[i + 1] * 3 + 2] + s[out[i + 2] * 3 + 2]) / 3f;
            assertTrue(z >= prev - 1e-4f);
            prev = z;
        }
    }
}
