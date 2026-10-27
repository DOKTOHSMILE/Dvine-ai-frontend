package ai.dvine.render;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

import ai.dvine.face.FaceRig;
import ai.dvine.face.Gestures;
import ai.dvine.face.nano.DotBody;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;

/** Every gesture is made with the body's own hands, up beside the face, and leaves the body valid. */
public class GestureHandsTest {
    @Test
    public void everyGestureUsesTheRealHandsAndKeepsTheBodyValid() throws Exception {
        for (String who : new String[]{"obi", "ivie"}) {
            HumanAsset a;
            try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/" + who + ".dvhm"))) {
                a = HumanAsset.read(in);
            }
            boolean ivie = who.equals("ivie");
            WireRenderer r = new WireRenderer(new WireRendererTest.FakeGl());
            r.onCreated();
            r.onSize(1080, 2000);
            HumanStage stage = new HumanStage(r);
            stage.install(ivie ? 1 : 0, a, true);
            FaceRig rig = new FaceRig(ivie);
            float t = 0f;
            for (; t < 1f; t += 1 / 30f) {
                stage.frame(t, ivie, rig, false, false, 1f);
            }
            HumanInstance h = stage.instance(ivie);
            for (String key : Gestures.keys()) {
                assertTrue(key, stage.gesture(ivie, key, 1.2f));
                float lowest = Float.MAX_VALUE;
                for (float end = t + 1.1f; t < end; t += 1 / 30f) {
                    stage.frame(t, ivie, rig, false, false, 1f);
                }
                for (int hand : new int[]{DotBody.HAND_L, DotBody.HAND_R}) {
                    lowest = Math.min(lowest, h.rig.joint[hand * 3 + 1]);
                }
                for (float v : h.pos) {
                    assertTrue(who + " " + key + " finite", !Float.isNaN(v) && !Float.isInfinite(v));
                }
                assertTrue(who + " " + key + " the hands stay out of the body", !h.rig.handInside(DotBody.HAND_L) && !h.rig.handInside(DotBody.HAND_R));
                // a gesturing wrist is raised well above where a hanging hand would be (about hip height)
                float highest = Math.max(h.rig.joint[DotBody.HAND_L * 3 + 1], h.rig.joint[DotBody.HAND_R * 3 + 1]);
                assertTrue(who + " " + key + " a hand comes up: " + highest, highest > h.rig.joint[DotBody.CHEST * 3 + 1]);
                for (; h.rig.gesturing() && t < 60f; t += 1 / 30f) {
                    stage.frame(t, ivie, rig, false, false, 1f);
                }
            }
        }
    }
}
