package ai.dvine.human;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.dvine.face.Look;
import ai.dvine.face.nano.Sculpt;
import ai.dvine.human.build.Anatomy;
import ai.dvine.human.build.BodyFit;
import ai.dvine.human.build.InnerAnatomy;
import ai.dvine.human.rig.Skeleton;

/** The inside of the body: every structure inside the skin, and riding its bone when the body moves. */
public class InnerAnatomyTest {
    @Test
    public void everyStructureIsInsideTheBody() throws Exception {
        for (String who : new String[]{"obi", "ivie"}) {
            HumanAsset a;
            try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/" + who + ".dvhm"))) {
                a = HumanAsset.read(in);
            }
            assertTrue(a.inner != null && a.innerNames.length > 40);
            // the sculpts are in the blockout layout: take each point back through the profile fit
            Skeleton sk = new Skeleton(a.female);
            BodyFit fit = BodyFit.of(a);
            Sculpt body = Anatomy.body(sk), head = Anatomy.head(a.female, Look.defaults(a.female));
            Sculpt hand = Anatomy.hand(a.female);
            List<String> outside = new ArrayList<String>();
            int[] bad = new int[a.innerNames.length], total = new int[a.innerNames.length];
            for (int v = 0; v < a.inner.pos.length / 3; v++) {
                int part = a.inner.region[v] & 0xff;
                float[] p = fit.unfit(a.inner.bone0[v], a.inner.pos[v * 3], a.inner.pos[v * 3 + 1], a.inner.pos[v * 3 + 2]);
                float x = p[0], y = p[1], z = p[2];
                total[part]++;
                float d = Math.min(body.d(x, y, z), head.d(x / Anatomy.HEAD_SCALE, (y - Anatomy.HEAD_Y) / Anatomy.HEAD_SCALE,
                        z / Anatomy.HEAD_SCALE) * Anatomy.HEAD_SCALE);
                if (a.innerNames[part].contains("hand") || a.innerNames[part].contains("finger")) {
                    d = Math.min(d, handDistance(hand, sk, x, y, z));
                }
                if (d > 0.004f) {
                    bad[part]++;
                }
            }
            for (int p = 0; p < bad.length; p++) {
                if (bad[p] > total[p] * 0.02f) {
                    outside.add(a.innerNames[p] + " " + bad[p] + "/" + total[p]);
                }
            }
            assertTrue(who + ": outside the skin: " + outside, outside.isEmpty());
        }
    }

    @Test
    public void theInsideMovesWithTheBody() throws Exception {
        HumanAsset a;
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/obi.dvhm"))) {
            a = HumanAsset.read(in);
        }
        HumanInstance h = new HumanInstance(a);
        h.rig.setPose("wave");
        for (int i = 0; i < 90; i++) {
            h.update(i / 30f);
        }
        // the right humerus stays between the right shoulder and elbow joints, wherever the arm is
        int humerus = -1;
        for (int p = 0; p < a.innerNames.length; p++) {
            if (a.innerNames[p].equals("humerus right")) humerus = p;
        }
        float[] sj = joint(h, ai.dvine.face.nano.DotBody.ARM_R), ej = joint(h, ai.dvine.face.nano.DotBody.FORE_R);
        for (int v = 0; v < a.inner.pos.length / 3; v++) {
            if ((a.inner.region[v] & 0xff) != humerus) continue;
            float d = segmentDistance(h.innerPos, v, sj, ej);
            assertTrue("humerus " + d + " from the arm", d < 0.08f);
        }
    }

    private static float[] joint(HumanInstance h, int b) {
        return new float[]{h.rig.joint[b * 3], h.rig.joint[b * 3 + 1], h.rig.joint[b * 3 + 2]};
    }

    private static float segmentDistance(float[] p, int v, float[] a, float[] b) {
        float px = p[v * 3] - a[0], py = p[v * 3 + 1] - a[1], pz = p[v * 3 + 2] - a[2];
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float t = Math.max(0f, Math.min(1f, (px * dx + py * dy + pz * dz) / (dx * dx + dy * dy + dz * dz)));
        float x = px - t * dx, y = py - t * dy, z = pz - t * dz;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /** Distance to the hand sculpt (built in arm-local coordinates) for a body point on either side. */
    private static float handDistance(Sculpt hand, Skeleton sk, float x, float y, float z) {
        float best = Float.MAX_VALUE;
        for (int side = -1; side <= 1; side += 2) {
            // invert Skeleton.armToBody: x_b = side*(sh + lx*c + ly*s), y_b = -0.02 - lx*s + ly*c
            double ang = Math.toRadians(ai.dvine.face.nano.DotBody.BIND_ARM);
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            float u = side * x - sk.sh, w = y + 0.02f;
            float lx = u * c - w * s, ly = u * s + w * c;
            best = Math.min(best, hand.d(lx, ly, z));
        }
        return best;
    }
}
