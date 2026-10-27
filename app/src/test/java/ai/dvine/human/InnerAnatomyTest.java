package ai.dvine.human;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.dvine.human.bake.InnerAnatomy;

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
            // the skin as built (bind pose): every inner structure must be inside it
            MeshInside skin = new MeshInside(a.mesh.pos, a.mesh.tri);
            List<String> outside = new ArrayList<String>();
            int[] bad = new int[a.innerNames.length], total = new int[a.innerNames.length];
            for (int v = 0; v < a.inner.pos.length / 3; v++) {
                int part = a.inner.region[v] & 0xff;
                total[part]++;
                if (!skin.inside(a.inner.pos[v * 3], a.inner.pos[v * 3 + 1], a.inner.pos[v * 3 + 2])) {
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
}
