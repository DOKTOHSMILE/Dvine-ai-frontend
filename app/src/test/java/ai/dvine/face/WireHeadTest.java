package ai.dvine.face;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WireHeadTest {
    @Test
    public void meshIsValid() {
        for (boolean ivie : new boolean[]{false, true}) {
            WireHead h = WireHead.create(ivie);
            assertTrue(h.vertexCount() > 300);
            assertTrue(h.edgeCount() < 6000);
            assertEquals(h.edgeCount(), h.edgeGroup.length);
            for (int e : h.edges) {
                assertTrue(e >= 0 && e < h.vertexCount());
            }
            for (float v : h.rest) {
                assertFalse(Float.isNaN(v));
            }
        }
    }

    @Test
    public void hairFollowsTheLook() {
        // the sheets' looks are bald on the dot head (Ivie bald, Obi's hair is painted); a look with hair gets it
        assertFalse(hasGroup(WireHead.create(true), WireHead.HAIR));
        ai.dvine.face.Look longHair = ai.dvine.face.Look.defaults(true);
        longHair.hair = 1f;
        longHair.hairStyle = ai.dvine.face.Look.PUFFS;
        assertTrue(hasGroup(WireHead.create(true, longHair), WireHead.HAIR));
    }

    @Test
    public void jawOpensDownward() {
        WireHead h = WireHead.create(false);
        WireHead.Pose shut = new WireHead.Pose();
        WireHead.Pose open = new WireHead.Pose();
        open.jaw = 1f;
        float[] a = new float[h.rest.length];
        float[] b = new float[h.rest.length];
        h.deform(shut, a);
        h.deform(open, b);
        float lowA = 0f;
        float lowB = 0f;
        for (int i = 0; i < h.vertexCount(); i++) {
            if (h.tag[i] == WireHead.T_LIP_LO) {
                lowA += a[i * 3 + 1];
                lowB += b[i * 3 + 1];
            }
        }
        assertTrue(lowB < lowA);
    }

    @Test
    public void faceIsRoughlySymmetric() {
        WireHead h = WireHead.create(false);
        float sum = 0f;
        for (int i = 0; i < h.vertexCount(); i++) {
            if (h.tag[i] != WireHead.T_SKIN) {
                sum += h.rest[i * 3];
            }
        }
        assertEquals(0f, sum, 0.05f);
    }

    @Test
    public void projectionIsFinite() {
        WireHead h = WireHead.create(true);
        WireHead.Pose p = new WireHead.Pose();
        p.time = 3f;
        p.jaw = 0.5f;
        p.smile = 0.8f;
        p.blink = 1f;
        float[] d = new float[h.rest.length];
        float[] s = new float[h.rest.length];
        h.deform(p, d);
        WireHead.project(d, h.vertexCount(), 25f, -8f, 4f, 500f, 600f, 200f, s);
        for (float v : s) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v));
        }
    }

    private static boolean hasGroup(WireHead h, int g) {
        for (int x : h.edgeGroup) {
            if (x == g) {
                return true;
            }
        }
        return false;
    }
}
