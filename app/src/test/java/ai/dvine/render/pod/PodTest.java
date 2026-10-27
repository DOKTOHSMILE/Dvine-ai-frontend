package ai.dvine.render.pod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import ai.dvine.stage.StageMode;

/** The stage: the pod's place on screen, the sky, how the pod shows what DVINE does, GL clean-up, and no face anywhere. */
public class PodTest {
    @Test
    public void composition() {
        PodLayout l = PodLayout.compute(1080, 1920, 0, 0);
        float[] c = l.project(0f, 0f, 0f);
        assertEquals(540f, c[0], 1f);
        assertEquals(0.40f * 1920, c[1], 2f);
        assertEquals(0.76f * 1920, l.project(0f, l.podY, 0f)[1], 2f);
        assertEquals(0.76f * 1080, l.podDiameter * l.unit, 2f);
        for (float[] wh : new float[][]{{1080, 2400}, {2400, 1080}, {1600, 2560}}) {
            PodLayout q = PodLayout.compute(wh[0], wh[1], 60, 80);
            assertTrue(q.project(0f, q.podY, 0f)[1] + 0.15f * q.unit < wh[1] - 80f);
            assertTrue(q.podDiameter * q.unit <= wh[0]);
        }
    }

    @Test
    public void aCalmSky() {
        Space a = new Space(), b = new Space();
        assertTrue(java.util.Arrays.equals(a.stars, b.stars));
        double tw = a.twinkling() / (double) Space.STARS;
        assertTrue(tw >= 0.08 && tw <= 0.12);
        int meteors = 0;
        boolean flying = false;
        float last = -100f;
        for (float t = 0f; t < 180f; t += 1f / 30f) {
            a.step(t);
            if (a.meteorLife >= 0f && !flying) {
                meteors++;
                assertTrue(last < 0f || t - last >= 8f);
                last = t;
            }
            flying = a.meteorLife >= 0f;
        }
        assertTrue(meteors >= 9 && meteors <= 23);
    }

    @Test
    public void thePodShowsWhatDvineDoes() {
        PodMotion m = new PodMotion();
        for (int i = 0; i < 60; i++) {
            m.step(1f / 30f, StageMode.CONTENT);
        }
        assertEquals("no speech, no beat", 0f, m.voice, 0.01f);
        m.word("debate");
        float most = 0f;
        for (int i = 0; i < 15; i++) {
            m.step(1f / 30f, StageMode.SPEAKING);
            most = Math.max(most, m.voice);
        }
        assertTrue(most > 0.3f);
        for (int i = 0; i < 60; i++) {
            m.step(1f / 30f, StageMode.LISTENING);
        }
        assertTrue(m.listen > 0.8f);
        for (int i = 0; i < 60; i++) {
            m.step(1f / 30f, StageMode.THINKING);
        }
        assertTrue(m.think > 0.8f && m.listen < 0.3f);
        m.speaker("ivie");
        for (int i = 0; i < 60; i++) {
            m.step(1f / 30f, StageMode.CONTENT);
        }
        assertTrue("Ivie's light is violet", m.tint[0] > 0.7f);
        m.setDim(true);
        for (int i = 0; i < 120; i++) {
            m.step(1f / 30f, StageMode.CONTENT);
        }
        assertTrue(m.dim > 0.9f);
    }

    /** A GL that counts what is made and freed. */
    static final class Counter implements java.lang.reflect.InvocationHandler {
        int next = 1, buffers, programs;

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "buffer": buffers++; return next++;
                case "program": programs++; return next++;
                case "deleteBuffer": buffers--; return null;
                case "deleteProgram": programs--; return null;
                case "uniform": case "floatTexture": case "imageTexture": return next++;
                case "error": return 0;
                default: return null;
            }
        }
    }

    @Test
    public void glObjectsAreFreed() {
        Counter c = new Counter();
        ai.dvine.render.GlApi gl = (ai.dvine.render.GlApi) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ai.dvine.render.GlApi.class}, c);
        ai.dvine.render.StageRenderer r = new ai.dvine.render.StageRenderer(gl);
        PodMotion m = new PodMotion();
        PodLayout l = PodLayout.compute(1080, 2400, 0, 0);
        for (int pass = 0; pass < 2; pass++) {
            r.onCreated();
            r.onSize(1080, 2400);
            for (int i = 0; i < 5; i++) {
                m.step(0.03f, StageMode.SPEAKING);
                r.pod.submit(l, m, i > 2, 2.75f);
                r.onDraw(i * 33_000_000L);
            }
            assertTrue(c.buffers > 8 && c.programs >= 8);
            r.dispose();
            assertEquals(0, c.buffers);
            assertEquals(0, c.programs);
        }
    }

    @Test
    public void noFaceAnywhere() throws Exception {
        assertFalse(new File("src/main/assets/holo").exists());
        assertFalse(new File("src/main/assets/human").exists());
        assertFalse(new File("src/main/java/ai/dvine/face").exists());
        assertFalse(new File("src/main/java/ai/dvine/human").exists());
        for (String f : new String[]{"src/main/java/ai/dvine/stage/StageView.java", "src/main/java/ai/dvine/MainActivity.java",
                "src/main/java/ai/dvine/render/pod/PodRenderer.java"}) {
            String src = new String(Files.readAllBytes(new File(f).toPath()), StandardCharsets.UTF_8);
            for (String old : new String[]{"HoloHead", "HumanStage", "WireRenderer", "DotHead", "WireHead", "eyeBloom", "HEAD_FS"}) {
                assertFalse(f + " still uses " + old, src.contains(old));
            }
        }
    }
}
