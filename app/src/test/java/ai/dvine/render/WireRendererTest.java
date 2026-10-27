package ai.dvine.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.Buffer;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;

/** The renderer's resource handling and drawing, against a fake GL that tracks every object. */
public class WireRendererTest {
    /** Counts live objects and draw calls; never reports an error. */
    static final class FakeGl implements GlApi {
        final Set<Integer> live = new HashSet<Integer>();
        int next = 1;
        int draws;
        int leaksDeleted;

        int make() {
            live.add(next);
            return next++;
        }

        void drop(int id) {
            if (!live.remove(id)) {
                leaksDeleted++; // deleting something that was not alive
            }
        }

        public int program(String v, String f) {
            assertTrue(v.startsWith("#version 300 es") && f.startsWith("#version 300 es"));
            return make();
        }
        public void deleteProgram(int p) { drop(p); }
        public void useProgram(int p) { }
        public int uniform(int program, String name) { return 1; }
        public void uniform1f(int loc, float v) { }
        public void uniform1i(int loc, int v) { }
        public void uniform2f(int loc, float a, float b) { }
        public void uniform4f(int loc, float a, float b, float c, float d) { }
        public void uniform3fv(int loc, float[] v) { assertEquals(0, v.length % 3); }
        public void uniform1fv(int loc, float[] v) { assertTrue(v.length > 0); }
        public void uniformMatrix4(int loc, float[] m) { assertEquals(16, m.length); }
        public void uniformMatrix3(int loc, float[] m) { assertEquals(9, m.length); }
        public int buffer() { return make(); }
        public void deleteBuffer(int b) { drop(b); }
        public void bindBuffer(int target, int b) { }
        public void bufferData(int target, int bytes, Buffer data, int usage) { }
        public void bufferSubData(int target, int offset, int bytes, Buffer data) { assertTrue(data.capacity() * 4 >= bytes); }
        public void vertexAttrib(int index, int size, int stride, int offset) { }
        public void disableAttrib(int index) { }
        public int floatTexture(int width, int height) { return make(); }
        public void updateFloatTexture(int tex, int width, int height, Buffer data) { assertEquals(width * height * 3, data.limit()); }
        public void deleteTexture(int t) { drop(t); }
        public int imageTexture(int width, int height, Buffer rgba) { assertEquals(width * height * 4, rgba.limit()); return make(); }
        public void bindTexture(int unit, int tex) { }
        public void enable(int cap) { }
        public void disable(int cap) { }
        public void depthMask(boolean on) { }
        public void depthFunc(int f) { }
        public void blendFunc(int src, int dst) { }
        public void polygonOffset(float factor, float units) { }
        public void viewport(int w, int h) { }
        public void clear(float r, float g, float b) { }
        public void drawArrays(int mode, int first, int count) { draws++; }
        public void drawElements(int mode, int count, int offsetBytes) { draws++; }
        public int error() { return NO_ERROR; }
    }

    @Test
    public void createsDrawsSurvivesContextLossAndFreesEverything() throws Exception {
        HumanAsset obi, ivie;
        try (FileInputStream a = new FileInputStream(new File("src/main/assets/human/obi.dvhm"));
             FileInputStream b = new FileInputStream(new File("src/main/assets/human/ivie.dvhm"))) {
            obi = HumanAsset.read(a);
            ivie = HumanAsset.read(b);
        }
        FakeGl gl = new FakeGl();
        WireRenderer r = new WireRenderer(gl);
        r.setAsset(0, obi, ai.dvine.anre.HumanParticles.build(obi, 4000));
        r.setAsset(1, ivie, ai.dvine.anre.HumanParticles.build(ivie, 4000));
        r.onCreated();
        r.onSize(1080, 2000);
        HumanInstance h = new HumanInstance(obi);
        Camera c = new Camera();
        c.cx = 540;
        c.cy = 1000;
        c.unit = 700;
        c.distance = 4.4f;
        for (int f = 0; f < 120; f++) {
            h.update(f / 60f);
            r.setCamera(c, -4.22f);
            r.submit(0, h.pos, h.nor, 0f, 0.9f, 1f, 1f, 1f, true);
            r.onDraw(f * 16_000_000L);
        }
        assertTrue("draws happened", gl.draws > 100);
        assertEquals(0, r.glErrors);
        assertEquals("the renderer's own count matches the GL's", gl.live.size(), r.liveObjects);

        // the GL context is lost: a new one gets fresh objects and drawing continues
        FakeGl gl2 = new FakeGl();
        WireRenderer r2 = new WireRenderer(gl2);
        r2.setAsset(0, obi, ai.dvine.anre.HumanParticles.build(obi, 4000));
        r2.onCreated();
        r2.onSize(1080, 2000);
        r2.submit(0, h.pos, h.nor, 0f, 0.9f, 1f, 1f, 1f, true);
        r2.onDraw(0L);
        r2.onCreated(); // same renderer, new context
        r2.submit(0, h.pos, h.nor, 0f, 0.9f, 1f, 1f, 1f, true);
        r2.onDraw(16_000_000L);
        assertTrue(gl2.draws > 4);

        // detaching frees every object, and a second dispose does no harm
        r.dispose();
        assertEquals(0, gl.live.size());
        assertEquals(0, gl.leaksDeleted);
        assertEquals(0, r.liveObjects);
        r.dispose();
        assertEquals(0, gl.leaksDeleted);
        assertTrue(r.stats.averageMs() > 0f);
    }

    @Test
    public void cameraRaysPassThroughWhatTheyProject() {
        Camera c = new Camera();
        c.width = 1080;
        c.height = 2000;
        c.cx = 500;
        c.cy = 900;
        c.unit = 650;
        c.distance = 2.5f;
        c.pivot = 0.4f;
        c.yawDeg = 30f;
        c.pitchDeg = 10f;
        c.update();
        float[] p = {0.3f, -1.2f, 0.2f};
        float[] s = c.project(p[0], p[1], p[2]);
        float[] ray = c.ray(s[0], s[1]);
        // distance from the point to the ray
        float vx = p[0] - ray[0], vy = p[1] - ray[1], vz = p[2] - ray[2];
        float t = vx * ray[3] + vy * ray[4] + vz * ray[5];
        float dx = vx - t * ray[3], dy = vy - t * ray[4], dz = vz - t * ray[5];
        assertTrue(t > 0f);
        assertEquals(0f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-3f);
    }
}
