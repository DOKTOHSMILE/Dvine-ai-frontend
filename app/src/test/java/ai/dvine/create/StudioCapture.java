package ai.dvine.create;

import java.io.File;

import org.junit.Assume;
import org.junit.Test;

import ai.dvine.render.RecordingGl;
import ai.dvine.render.StageRenderer;
import ai.dvine.render.pod.PodLayout;
import ai.dvine.render.pod.PodMotion;
import ai.dvine.stage.SolidSurface;
import ai.dvine.stage.StageMode;

/**
 * Records what the full-screen design space draws (the studio backdrop and a measured house, 1080 x 2400 portrait,
 * as on a phone in full screen) for tools/gl_replay to play back in WebGL2. Runs only with GLCAP_OUT set:
 * {@code GLCAP_OUT=/tmp/cap/studio gradle :app:testDebugUnitTest --tests ai.dvine.create.StudioCapture}.
 */
public class StudioCapture {
    @Test
    public void recordsTheStudioWithAHouse() throws Exception {
        String out = System.getenv("GLCAP_OUT");
        Assume.assumeTrue(out != null && !out.isEmpty());
        float yaw = Float.parseFloat(System.getenv().getOrDefault("GLCAP_YAW", "0.6"));
        float pitch = Float.parseFloat(System.getenv().getOrDefault("GLCAP_PITCH", "0.35"));
        int w = 1080, h = 2400;
        RecordingGl gl = new RecordingGl();
        StageRenderer r = new StageRenderer(gl);
        r.onCreated();
        r.onSize(w, h);
        PodLayout l = PodLayout.compute(w, h, 0, 0);
        PodMotion m = new PodMotion();
        ObjectModel house = "bike".equals(System.getenv("GLCAP_WHAT")) ? Designs.build(Designs.spec("bicycle", "UNITS: m\n"
                + "rear tyre | torus-x | rubber | 0.05 x 0.68 x 0.68 | at 0, 0, -0.52\n"
                + "front tyre | torus-x | rubber | 0.05 x 0.68 x 0.68 | at 0, 0, 0.52\n"
                + "down tube | box | steel | 0.04 x 0.04 x 0.62 | at 0, 0.42, 0.05 | turn 0\n"
                + "seat tube | box | steel | 0.04 x 0.55 x 0.04 | at 0, 0.32, -0.15\n"
                + "top tube | box | steel | 0.035 x 0.035 x 0.55 | at 0, 0.82, 0.1\n"
                + "fork | box | steel | 0.04 x 0.5 x 0.04 | at 0, 0.34, 0.52\n"
                + "handlebar | cylinder-x | steel | 0.5 x 0.03 x 0.03 | at 0, 0.82, 0.52\n"
                + "seat | box | leather | 0.14 x 0.05 x 0.26 | at 0, 0.88, -0.17\n"
                + "chainring | cylinder-x | steel | 0.01 x 0.2 x 0.2 | at 0.04, 0.24, -0.05\n"))
                : Designs.build(Designs.house());
        r.setProjection(l.proj);
        r.setSolid(SolidSurface.pack(house));
        r.setSolidPose(yaw, pitch, 1f, 0f, 0f);
        for (int i = 0; i < 3; i++) {
            m.step(0.03f, StageMode.CONTENT);
            r.pod.submit(l, m, true, 2.75f, false);
            r.onDraw(i * 33_000_000L);
        }
        gl.write(new File(out));
    }
}
