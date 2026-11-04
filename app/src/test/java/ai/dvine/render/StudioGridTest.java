package ai.dvine.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StudioGridTest {
    @Test
    public void theFloorLiesUnderTheDesignAndCarriesTheAxes() {
        int s = ai.dvine.stage.SolidSurface.STRIDE;
        // a box from (-2, 1, -1) to (2, 3, 1)
        float[] verts = new float[2 * s];
        verts[0] = -2; verts[1] = 1; verts[2] = -1;
        verts[s] = 2; verts[s + 1] = 3; verts[s + 2] = 1;
        float[] g = StageRenderer.gridLines(verts);
        int n = g.length / StageRenderer.GRID_STRIDE;
        assertTrue(n > 40);
        boolean red = false, green = false;
        for (int i = 0; i < n; i++) {
            int o = i * StageRenderer.GRID_STRIDE;
            assertTrue("on the design's lowest point", g[o + 1] <= 1f && g[o + 1] > 0.99f);
            red |= g[o + 3] > 0.8f && g[o + 4] < 0.4f && g[o + 2] == 0f; // the X axis at z = 0
            green |= g[o + 4] > 0.7f && g[o + 3] < 0.6f && g[o] == 0f; // the Z axis at x = 0
        }
        assertTrue(red && green);
        assertTrue("reaches well past the design", StageRenderer.reachOf(g) >= 9f);
        assertEquals(0.5f, StageRenderer.niceStep(0.34f), 1e-6);
        assertEquals(2f, StageRenderer.niceStep(1.3f), 1e-6);
        assertEquals(10f, StageRenderer.niceStep(7f), 1e-6);
        assertEquals(0, StageRenderer.gridLines(new float[0]).length);
    }
}
