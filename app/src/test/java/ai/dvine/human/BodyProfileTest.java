package ai.dvine.human;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.dvine.brain.MiniJson;
import ai.dvine.face.nano.DotBody;
import ai.dvine.human.bake.BodyFit;
import ai.dvine.human.mesh.MeshValidator;
import ai.dvine.human.rig.BodyProfile;
import ai.dvine.human.rig.Skeleton;

/** The body-dimension profile, and the shipped bodies measured against it. */
public class BodyProfileTest {
    private static final BodyProfile P = BodyProfile.standard();

    static HumanAsset read(String name) throws IOException {
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/" + name + ".dvhm"))) {
            return HumanAsset.read(in);
        }
    }

    @Test
    public void theShippedProfileParsesAndItsMetresAgreeWithItsRatios() throws IOException {
        assertEquals(1.8f, P.heightM, 1e-6f);
        assertEquals(15, P.landmarks.size());
        assertEquals(13, P.limits.size());
        String json = new String(java.nio.file.Files.readAllBytes(
                new File("src/main/resources/ai/dvine/human/body_dimensions.json").toPath()), "UTF-8");
        Map<String, Object> g = MiniJson.obj(MiniJson.obj(MiniJson.parse(json)).get("global_calculations"));
        assertEquals(MiniJson.num(g.get("head_height_m"), 0), P.metres(P.headRatio), 0.001);
        assertEquals(MiniJson.num(g.get("shoulder_width_m"), 0), P.metres(P.shoulderRatio), 0.001);
        assertEquals(MiniJson.num(g.get("hip_width_m"), 0), P.metres(P.hipRatio), 0.001);
        assertEquals(MiniJson.num(g.get("arm_span_m"), 0), P.metres(P.spanRatio), 0.001);
        assertEquals(MiniJson.num(g.get("leg_length_m"), 0), P.metres(P.legRatio), 0.001);
        assertEquals(0.0315f, BodyProfile.radiusAt(0.04f, 0.023f, 0.5f), 1e-6f);
        assertEquals(0.023f, BodyProfile.radiusAt(0.04f, 0.023f, 3f), 1e-6f);
    }

    @Test
    public void theProfilesContradictionsAreReportedAndResolved() {
        List<String> found = P.consistency();
        String all = found.toString();
        assertTrue(all, all.contains("head ratio vs crown − chin"));
        assertTrue(all, all.contains("knee landmark vs knee part"));
        assertTrue(all, all.contains("eye line"));
        assertTrue("left/right pairs mirror: " + all, !all.contains("not mirrored"));
        // the shoulder may not rise into the neck the head ratio leaves
        assertTrue(P.deviations.containsKey("shoulder_joint_center"));
        assertEquals(1f - P.headRatio - BodyProfile.MIN_NECK_GAP, P.fit("shoulder_joint_center"), 1e-5f);
        // everything else is applied as given
        assertEquals(P.y("hip_joint_center"), P.fit("hip_joint_center"), 0f);
        assertEquals(P.y("knee_center"), P.fit("knee_center"), 0f);
        assertEquals(P.y("ankle_center"), P.fit("ankle_center"), 0f);
    }

    @Test
    public void malformedProfilesAreRefused() {
        String good = "{\"schema\":\"ANRE_BODY_DIMENSIONS_V1\",\"units\":\"meters\",\"global_calculations\":{"
                + "\"height_m\":1.8,\"head_height_ratio\":0.135,\"shoulder_width_ratio\":0.259,\"hip_width_ratio\":0.192,"
                + "\"arm_span_ratio\":1,\"leg_length_ratio\":0.49},\"landmarks\":[],\"parts\":[],\"joint_limits_degrees\":[]}";
        String[] bad = {good.replace("V1", "V0"), good.replace("meters", "inches"), good.replace("1.8", "-1.8"),
                good.replace("\"landmarks\":[]", "\"landmarks\":[{\"id\":\"x\",\"position\":[0,1]}]"),
                good.replace("\"joint_limits_degrees\":[]", "\"joint_limits_degrees\":[{\"joint\":\"k\",\"min\":10,\"max\":0}]")};
        for (String b : bad) {
            try {
                BodyProfile.parse(b);
                fail("accepted " + b);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    @Test
    public void profileCoordinatesMapOntoTheBodyMirrored() {
        // anatomical left is −X and the front −Z in the profile; the persona's left is +X and the front +Z here
        float[] eye = BodyProfile.toBody(new float[]{-0.027f, 0.99f, -0.08f}, 5f);
        assertTrue(eye[0] > 0f && eye[2] > 0f);
        assertEquals(BodyProfile.FLOOR + 4.95f, eye[1], 1e-5f);
    }

    @Test
    public void theBodiesStandOnTheProfilesProportions() throws IOException {
        for (String who : new String[]{"obi", "ivie"}) {
            HumanAsset a = read(who);
            assertTrue(MeshValidator.check(a.mesh, true, Skeleton.COUNT, Regions.COUNT).ok());
            HumanInstance s = new HumanInstance(a);
            s.rig.setTurntable(false);
            s.rig.setPose("stand");
            HumanInstance t = new HumanInstance(a);
            t.rig.setTurntable(false);
            t.rig.setPose("tpose");
            for (int i = 0; i < 60; i++) {
                s.update(i / 30f);
                t.update(i / 30f);
            }
            int n = s.solidCount();
            float lo = 9f, hi = -9f, minX = 9f, maxX = -9f;
            for (int v = 0; v < n; v++) {
                float x = s.pos[v * 3], y = s.pos[v * 3 + 1], z = s.pos[v * 3 + 2];
                assertTrue("finite", !Float.isNaN(x + y + z) && !Float.isInfinite(x + y + z));
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                if (a.mesh.region[v] == Regions.HAIR) {
                    continue;
                }
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
            float h = hi - lo;
            // bounds: the soles on the floor, a symmetric body
            assertEquals(who, BodyProfile.FLOOR, lo, 0.03f);
            assertEquals(who + " symmetric", maxX, -minX, 0.02f * h);
            float[] j = s.rig.joint;
            assertEquals(who + " hip", P.fit("hip_joint_center"), (j[DotBody.THIGH_L * 3 + 1] - lo) / h, 0.005f);
            assertEquals(who + " knee", P.fit("knee_center"), (j[DotBody.SHIN_L * 3 + 1] - lo) / h, 0.01f);
            assertEquals(who + " ankle", P.fit("ankle_center"), (j[DotBody.FOOT_L * 3 + 1] - lo) / h, 0.01f);
            assertEquals(who + " shoulder", P.fit("shoulder_joint_center"), (j[DotBody.ARM_L * 3 + 1] - lo) / h, 0.005f);
            for (int b = 0; b < Skeleton.COUNT; b++) {
                int m = Skeleton.rightSide(b) ? mirror(b) : -1;
                if (m >= 0) {
                    assertEquals(Skeleton.NAMES[b], -a.joints[m * 3], a.joints[b * 3], 1e-4f);
                    assertEquals(Skeleton.NAMES[b], a.joints[m * 3 + 1], a.joints[b * 3 + 1], 1e-4f);
                }
                assertTrue("parents come first", Skeleton.PARENT[b] < b);
            }
            float sw = 0f, hw = 0f, span = 0f;
            float shY = j[DotBody.ARM_L * 3 + 1], hipY = j[DotBody.THIGH_L * 3 + 1];
            for (int v = 0; v < n; v++) {
                float y = s.pos[v * 3 + 1];
                String k = Regions.key(a.mesh.region[v]);
                if (Math.abs(y - shY) < 0.15f && !k.startsWith("hand")) {
                    sw = Math.max(sw, Math.abs(s.pos[v * 3]));
                }
                if (Math.abs(y - hipY) < 0.12f && (k.startsWith("body") || k.startsWith("leg"))) {
                    hw = Math.max(hw, Math.abs(s.pos[v * 3]));
                }
                span = Math.max(span, Math.abs(t.pos[v * 3]));
            }
            float crown = s.pos[a.landmark("crown") * 3 + 1], chin = s.pos[a.landmark("chin") * 3 + 1];
            assertEquals(who + " head", P.headRatio, (crown - chin) / h, P.headRatio * 0.06f);
            assertEquals(who + " shoulders", P.shoulderRatio * BodyProfile.shoulderFactor(a.female), 2f * sw / h, 0.03f * P.shoulderRatio);
            assertEquals(who + " hips", P.hipRatio * BodyProfile.hipFactor(a.female), 2f * hw / h, 0.04f * P.hipRatio);
            assertEquals(who + " span", P.spanRatio, 2f * span / h, 0.02f);
            assertEquals(who + " leg", P.legRatio, (hipY - j[DotBody.FOOT_L * 3 + 1]) / h, 0.03f * P.legRatio);
        }
    }

    @Test
    public void theFitInvertsAndTheRigUsesTheProfilesJointLimits() throws IOException {
        HumanAsset a = read("obi");
        BodyFit fit = BodyFit.of(a);
        float[] d = new float[3];
        for (int bone : new int[]{DotBody.SPINE, DotBody.THIGH_L, DotBody.FORE_R, Skeleton.FINGER_L + 3}) {
            float[] p = {bone == DotBody.FORE_R ? -0.9f : 0.3f, -1.1f, 0.05f};
            fit.offset(bone, p[0], p[1], p[2], d);
            float[] back = fit.unfit(bone, p[0] + d[0], p[1] + d[1], p[2] + d[2]);
            for (int k = 0; k < 3; k++) {
                assertEquals(Skeleton.NAMES[bone], p[k], back[k], 1e-4f);
            }
        }
        float[][] limits = new float[DotBody.BONES][];
        for (int b = 0; b < DotBody.BONES; b++) {
            limits[b] = new float[6];
        }
        P.applyLimits(limits);
        assertEquals(140f, limits[DotBody.SHIN_L][1], 0f);
        assertEquals(-145f, limits[DotBody.FORE_L][2], 0f);
        assertEquals(65f, limits[DotBody.NECK][3], 0f);
        assertEquals(-120f, limits[DotBody.THIGH_R][0], 0f);
    }

    private static int mirror(int b) {
        if (b >= Skeleton.FINGER_R) {
            return b - 10;
        }
        return b - 3;
    }
}
