package ai.dvine.render;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

import ai.dvine.face.FaceRig;
import ai.dvine.face.nano.DotBody;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;
import ai.dvine.human.touch.BodyTouch;

/** A tap on the chest: the hand comes to the chest (not the chin), and the head only nods, so the face stays in view. */
public class TouchReactionTest {
    @Test
    public void aChestTapBringsTheHandToTheChestAndTheFaceStaysUp() throws Exception {
        HumanAsset obi;
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/obi.dvhm"))) {
            obi = HumanAsset.read(in);
        }
        WireRenderer r = new WireRenderer(new WireRendererTest.FakeGl());
        r.onCreated();
        r.onSize(1080, 2000);
        HumanStage stage = new HumanStage(r);
        stage.install(0, obi, true);
        FaceRig rig = new FaceRig(false);
        Camera c = stage.camera;
        c.width = 1080;
        c.height = 2000;
        c.cx = 540;
        c.unit = 300;
        c.distance = 4.4f;
        c.pivot = 0f;
        c.cy = 640;
        c.update();
        float t = 0f;
        for (; t < 5f; t += 1 / 30f) {
            stage.frame(t, false, rig, true, false, 1f);
        }
        HumanInstance h = stage.instance(false);
        int chest = obi.landmark("chest");
        float[] s = c.project(h.pos[chest * 3], h.pos[chest * 3 + 1], h.pos[chest * 3 + 2]);
        BodyTouch touch = stage.pick(s[0], s[1], false, BodyTouch.Kind.TAP, 1, 0L, 80L);
        assertNotNull(touch);
        float chinY = h.pos[obi.landmark("chin") * 3 + 1];
        stage.react(touch, false);
        for (float end = t + 1.2f; t < end; t += 1 / 30f) {
            stage.frame(t, false, rig, true, false, 1f);
        }
        // the reaching hand (whichever came): its wrist is below the chin and near the tapped point
        float[] p = touch.world;
        float d = Float.MAX_VALUE, hy = 0f;
        for (int hb : new int[]{DotBody.HAND_L, DotBody.HAND_R}) {
            float hx = h.rig.joint[hb * 3], y = h.rig.joint[hb * 3 + 1], hz = h.rig.joint[hb * 3 + 2];
            float dd = (float) Math.sqrt((hx - p[0]) * (hx - p[0]) + (y - p[1]) * (y - p[1]) + (hz - p[2]) * (hz - p[2]));
            if (dd < d) {
                d = dd;
                hy = y;
            }
        }
        assertTrue("wrist below the chin: " + hy + " vs chin " + chinY, hy < chinY - 0.25f);
        assertTrue("wrist near the tapped chest: " + d, d < 0.75f);
        // the face still looks mostly forward: the eyes stay in front of the chin's height band
        float eyeY = (h.pos[obi.landmark("eye left") * 3 + 1] + h.pos[obi.landmark("eye right") * 3 + 1]) * 0.5f;
        assertTrue("the head only nods: eyes stay well above the chin", eyeY - chinY > 0.25f);
    }
}
