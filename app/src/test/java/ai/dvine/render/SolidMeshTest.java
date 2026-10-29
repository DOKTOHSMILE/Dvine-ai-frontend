package ai.dvine.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import ai.dvine.create.Component;
import ai.dvine.create.Geo;
import ai.dvine.create.ObjectModel;
import ai.dvine.create.ObjectSpec;
import ai.dvine.geom.TriMesh;
import ai.dvine.stage.SolidSurface;

/** A validated design is triangles on the board. This is not a measurement from a photograph. */
public class SolidMeshTest {
    private static ObjectModel model(Component... parts) {
        return new ObjectModel(ObjectSpec.of("block"), 1, "block", Arrays.asList(parts), Collections.emptyList());
    }

    @Test
    public void aBoxPacksFiniteTrianglesAboutTwoUnitsAcross() {
        Component hidden = new Component(2, "ghost", "body", "glass", 0, Geo.box(0f, 0f, 0f, 8f, 8f, 8f));
        hidden.visible = false;
        SolidSurface s = SolidSurface.pack(model(
                hidden,
                new Component(1, "body", "body", "steel", 0, Geo.box(1f, 2f, 3f, 2f, 1f, 0.5f))));
        assertNotNull(s);
        assertEquals(12, s.triangles);
        assertEquals(s.triangles * 3, s.indices.length);
        assertEquals(0, s.verts.length % SolidSurface.STRIDE);
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < s.verts.length; i += SolidSurface.STRIDE) {
            float x = s.verts[i], y = s.verts[i + 1], z = s.verts[i + 2];
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            float nx = s.verts[i + 3], ny = s.verts[i + 4], nz = s.verts[i + 5];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            assertEquals(1f, len, 0.05f);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            assertEquals(0.75f, s.verts[i + 6], 0.001f);
            assertEquals(0.82f, s.verts[i + 7], 0.001f);
            assertEquals(0.95f, s.verts[i + 8], 0.001f);
        }
        float span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        assertEquals(2f, span, 0.02f);
        assertEquals(0f, (minX + maxX) / 2f, 0.02f);
        assertEquals(0f, (minY + maxY) / 2f, 0.02f);
        assertEquals(0f, (minZ + maxZ) / 2f, 0.02f);
    }

    @Test
    public void aBadMeshIsRejectedAndNotPacked() {
        assertEquals("missing mesh", SolidSurface.reject(new TriMesh()));
        TriMesh badIndex = new TriMesh();
        badIndex.pos = new float[]{0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f};
        badIndex.tri = new int[]{0, 1, 9};
        assertEquals("index out of range", SolidSurface.reject(badIndex));
        TriMesh infinite = new TriMesh();
        infinite.pos = new float[]{0f, 0f, 0f, 1f, 0f, Float.NaN, 0f, 1f, 0f};
        infinite.tri = new int[]{0, 1, 2};
        assertEquals("non-finite coordinates", SolidSurface.reject(infinite));
        assertNull(SolidSurface.pack(model(new Component(1, "bad", "body", "metal", 0, badIndex))));
        assertNull(SolidSurface.pack(null));
    }

    @Test
    public void theBoardDrawsTheTrianglesAndFreesThem() {
        SolidSurface s = SolidSurface.pack(model(new Component(1, "body", "body", "wood", 0, Geo.box(0f, 0f, 0f, 1f, 1f, 1f))));
        assertNotNull(s);
        RecordingGl gl = new RecordingGl();
        StageRenderer r = new StageRenderer(gl);
        float[] eye = new float[16];
        eye[0] = eye[5] = eye[10] = eye[15] = 1f;
        r.onCreated();
        r.onSize(1080, 2400);
        r.setProjection(eye);
        r.setSolidPose(0f, 0f, 1f, 0.2f, 0f);
        r.setSolid(s);
        r.onDraw(1_000_000L);
        r.setSolid(s);
        r.onDraw(2_000_000L);
        String log = gl.cmds.toString();
        assertTrue(log.contains("[\"drawElements\"," + GlApi.TRIANGLES + "," + (s.triangles * 3) + ",0]"));
        assertTrue(log.contains("[\"enable\"," + GlApi.DEPTH_TEST + "]"));
        assertTrue(log.contains("[\"enable\"," + GlApi.CULL_FACE + "]"));
        assertTrue(log.contains("bufSub"));
        float[] model = StageRenderer.solidModel(0f, 0f, 1f, 0.2f, 0f);
        assertEquals(1f, model[0], 0.001f);
        assertEquals(1f, model[5], 0.001f);
        assertEquals(0.2f, model[12], 0.001f);
        assertEquals(0.05f, model[13], 0.001f);
        float[] turned = StageRenderer.solidModel((float) (Math.PI / 2.0), 0f, 1f, 0f, 0f);
        assertEquals(0f, turned[0], 0.02f);
        assertEquals(1f, turned[8], 0.02f);
        String meshDraw = "[\"drawElements\"," + GlApi.TRIANGLES + "," + (s.triangles * 3) + ",0]";
        assertEquals(2, count(log, meshDraw));
        r.setSolid(null);
        r.onDraw(3_000_000L);
        assertEquals(2, count(gl.cmds.toString(), meshDraw));
        r.dispose();
        r.dispose();
        assertEquals(0, r.liveObjects);
    }

    private static int count(String log, String token) {
        int n = 0, from = 0;
        while (true) {
            int i = log.indexOf(token, from);
            if (i < 0) {
                return n;
            }
            n++;
            from = i + token.length();
        }
    }
}
