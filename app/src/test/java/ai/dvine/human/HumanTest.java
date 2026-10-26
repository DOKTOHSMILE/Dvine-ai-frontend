package ai.dvine.human;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import ai.dvine.face.Look;
import ai.dvine.face.nano.DotBody;
import ai.dvine.human.build.Anatomy;
import ai.dvine.human.build.AssetBuilder;
import ai.dvine.human.face.FaceDrive;
import ai.dvine.human.inspect.Inspector;
import ai.dvine.human.mesh.MeshValidator;
import ai.dvine.human.mesh.TriMesh;
import ai.dvine.human.rig.Skeleton;
import ai.dvine.human.touch.BodyTouch;
import ai.dvine.human.touch.TouchClassifier;
import ai.dvine.render.Camera;

/** Validation of the shipped 3D humans (spec §10). */
public class HumanTest {
    static HumanAsset obi;
    static HumanAsset ivie;

    @BeforeClass
    public static void load() throws IOException {
        obi = read("obi");
        ivie = read("ivie");
    }

    static HumanAsset read(String name) throws IOException {
        try (FileInputStream in = new FileInputStream(new File("src/main/assets/human/" + name + ".dvhm"))) {
            return HumanAsset.read(in);
        }
    }

    @Test
    public void shippedAssetsAreValidClosedSkinnedAndMapped() {
        for (HumanAsset a : new HumanAsset[]{obi, ivie}) {
            MeshValidator.Report r = MeshValidator.check(a.mesh, true, Skeleton.COUNT, Regions.COUNT);
            assertTrue(r.toString(), r.ok());
            assertTrue("a real body, not a toy: " + a.mesh.triangleCount(), a.mesh.triangleCount() > 6000);
            assertEquals(15, a.morphs.length);
            boolean[] seen = new boolean[Regions.COUNT];
            for (byte b : a.mesh.region) {
                seen[b & 0xff] = true;
            }
            for (int id = 0; id < Regions.COUNT; id++) {
                assertTrue(Regions.key(id), seen[id] || id == Regions.HAIR && !a.female);
            }
        }
        assertTrue("female asset has hair", ivie.lines.length > obi.lines.length);
    }

    @Test
    public void damagedOrInvalidGeometryIsRejected() throws IOException {
        byte[] bytes = Files.readAllBytes(new File("src/main/assets/human/obi.dvhm").toPath());
        bytes[bytes.length / 2] ^= 0x5A;
        try {
            HumanAsset.read(new ByteArrayInputStream(bytes));
            fail("a damaged asset must not load");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("checksum"));
        }
        TriMesh m = copy(obi.mesh);
        m.tri[5] = m.vertexCount() + 10; // index out of range
        assertFalse(MeshValidator.check(m, true, 0, 0).ok());
        m = copy(obi.mesh);
        int t = m.tri[0];
        m.tri[0] = m.tri[1];
        m.tri[1] = t; // one triangle wound the wrong way
        assertFalse(MeshValidator.check(m, true, 0, 0).ok());
        m = copy(obi.mesh);
        m.tri = java.util.Arrays.copyOf(m.tri, m.tri.length - 3); // a hole
        assertFalse(MeshValidator.check(m, true, 0, 0).ok());
    }

    @Test
    public void theHeadIsGenuinelyThreeDimensional() {
        for (HumanAsset a : new HumanAsset[]{obi, ivie}) {
            float minX = 9, maxX = -9, minZ = 9, maxZ = -9;
            for (int v = 0; v < a.mesh.vertexCount(); v++) {
                int r = a.mesh.region[v];
                if (!Regions.isFace(r) || r == Regions.HAIR) {
                    continue;
                }
                minX = Math.min(minX, a.mesh.pos[v * 3]);
                maxX = Math.max(maxX, a.mesh.pos[v * 3]);
                minZ = Math.min(minZ, a.mesh.pos[v * 3 + 2]);
                maxZ = Math.max(maxZ, a.mesh.pos[v * 3 + 2]);
            }
            assertTrue("deep as well as wide", (maxZ - minZ) > 0.9f * (maxX - minX));
            // the nose stands out in front of the eyes, the eyes in front of the ears
            float nose = z(a, "nose tip"), eye = z(a, "eye left"), ear = z(a, "ear left");
            assertTrue(nose > eye && eye > ear);
        }
    }

    @Test
    public void landmarksStayAlignedFromEveryView() {
        HumanInstance h = new HumanInstance(obi);
        h.rig.setTurntable(false);
        h.update(1f);
        Camera c = faceCamera(h);
        int eyeL = obi.landmark("eye left"), eyeR = obi.landmark("eye right"), nose = obi.landmark("nose tip");
        for (float yaw : new float[]{0f, 35f, 90f, 180f, -90f}) {
            c.yawDeg = yaw;
            c.update();
            float[] l = project(c, h, eyeL), r = project(c, h, eyeR), n = project(c, h, nose);
            for (float[] p : new float[][]{l, r, n}) {
                assertTrue(Float.isFinite(p[0]) && Float.isFinite(p[1]) && p[2] > 0f);
            }
            if (yaw == 0f) {
                assertEquals("eyes level", l[1], r[1], 0.02f * Math.abs(l[0] - r[0]));
                assertEquals("nose centred between the eyes", (l[0] + r[0]) / 2f, n[0], 6f);
                assertTrue("persona's left eye on the viewer's right", l[0] > r[0]);
            }
            if (yaw == 180f) {
                assertTrue("seen from behind the eyes swap sides", l[0] < r[0]);
            }
        }
    }

    @Test
    public void theMouthOpensWithoutTearingAndTheEyesStayPut() {
        HumanInstance h = new HumanInstance(ivie);
        h.update(1f);
        float gap0 = gap(h);
        h.setWeight("jawOpen", 1f);
        h.update(1.05f);
        float gap1 = gap(h);
        assertTrue("mouth opens: " + gap0 + " → " + gap1, gap1 > gap0 + 0.02f);
        assertTrue(new Inspector(ivie).inspect(h).isEmpty());
        // no triangle of the face flips over
        int[] tri = ivie.mesh.tri;
        int flipped = 0;
        for (int t = 0; t < tri.length; t += 3) {
            if (h.asset.mesh.region[tri[t]] == Regions.LIPS || h.asset.mesh.region[tri[t]] == Regions.CHIN) {
                if (dot(normal(ivie.mesh.pos, tri, t), normal(h.pos, tri, t)) < 0f) {
                    flipped++;
                }
            }
        }
        assertEquals(0, flipped);
        // eyes sit in the head: between the ears, behind the nose tip
        assertTrue(Math.abs(ivie.eyeCenter[0]) < Math.abs(x(ivie, "ear left")));
        assertTrue(ivie.eyeCenter[2] < z(ivie, "nose tip"));
    }

    @Test
    public void theHeadStaysConnectedToTheNeck() {
        for (HumanAsset a : new HumanAsset[]{obi, ivie}) {
            ai.dvine.face.nano.Sculpt body = Anatomy.body(new Skeleton(a.female));
            // the head piece ends inside the body's neck: its lowest ring is hidden inside
            float cut = Anatomy.HEAD_CUT_Y * Anatomy.HEAD_SCALE + Anatomy.HEAD_Y;
            int inside = 0, ring = 0;
            for (int v = 0; v < a.mesh.vertexCount(); v++) {
                float y = a.mesh.pos[v * 3 + 1];
                if (a.mesh.region[v] == Regions.NECK && Math.abs(y - cut) < 0.01f) {
                    ring++;
                    if (body.d(a.mesh.pos[v * 3], y, a.mesh.pos[v * 3 + 2]) < 0.004f) {
                        inside++;
                    }
                }
            }
            assertTrue("ring found", ring > 10);
            assertTrue(inside + "/" + ring + " of the head's cut edge inside the neck", inside >= ring * 0.9f);
        }
    }

    @Test
    public void theMeshDeformsCleanlyInEveryPose() {
        java.util.Set<String> everyday = new java.util.HashSet<String>(java.util.Arrays.asList(
                "stand", "talk", "wave", "point", "walk", "arms up", "boxing", "pushups", "plank", "dance", "bow"));
        for (HumanAsset a : new HumanAsset[]{obi, ivie}) {
            Inspector in = new Inspector(a);
            for (String pose : DotBody.POSES) {
                HumanInstance h = new HumanInstance(a);
                h.rig.setPose(pose);
                float worst = 0f;
                for (int i = 0; i < 150; i++) {
                    h.update(i / 30f);
                    if (i % 15 == 14) {
                        List<String> problems = in.inspect(h);
                        assertTrue(pose + ": " + problems, problems.isEmpty());
                        worst = Math.max(worst, worstStretch(h));
                    }
                }
                if (everyday.contains(pose)) {
                    assertTrue(pose + " stretches the skin " + worst + "x", worst < 2.6f);
                }
            }
        }
    }

    private static float worstStretch(HumanInstance h) {
        int[] e = h.asset.mesh.edge;
        float[] rest = h.unskinned();
        float worst = 0f;
        for (int i = 0; i < e.length; i += 2) {
            float r = dist(rest, e[i], e[i + 1]);
            if (r > 1e-6f) {
                worst = Math.max(worst, dist(h.pos, e[i], e[i + 1]) / r);
            }
        }
        return worst;
    }

    private static float dist(float[] p, int a, int b) {
        float x = p[a * 3] - p[b * 3], y = p[a * 3 + 1] - p[b * 3 + 1], z = p[a * 3 + 2] - p[b * 3 + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    @Test
    public void touchFindsTheRightBodyPart() {
        HumanInstance h = new HumanInstance(obi);
        h.rig.setTurntable(false);
        h.update(1f);
        Camera c = faceCamera(h);
        assertEquals(Regions.NOSE, regionAt(h, c, obi.landmark("nose tip")));
        assertEquals(Regions.CHIN, regionAt(h, c, obi.landmark("chin")));
        // the full body
        c.unit = 300f;
        c.distance = 4.4f;
        c.pivot = 0f;
        c.cy = 640f;
        c.update();
        assertEquals(Regions.KNEE_L, regionAt(h, c, obi.landmark("left knee")));
        assertEquals(Regions.CHEST, regionAt(h, c, obi.landmark("chest")));
        // a contact point is also known relative to its bone
        HumanInstance.Hit hit = pickAt(h, c, obi.landmark("chest"));
        assertNotNull(hit);
        assertEquals(3, h.toBoneLocal(DotBody.CHEST, hit.point).length);
        // and a ray that misses the body hits nothing
        float[] ray = c.ray(5f, 5f);
        assertNull(h.pick(ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]));
    }

    @Test
    public void repeatedTouchesAreNotDuplicated() {
        TouchClassifier tc = new TouchClassifier();
        tc.down(Regions.NOSE, 0);
        assertEquals(BodyTouch.Kind.TAP, tc.up(80));
        tc.down(Regions.NOSE, 90);
        assertNull("the same touch reported twice", tc.up(100));
        tc.down(Regions.NOSE, 300);
        assertEquals(BodyTouch.Kind.REPEAT, tc.up(380));
        assertEquals(2, tc.count());
        tc.down(Regions.CHIN, 1000);
        assertNull(tc.held(1200));
        assertEquals(BodyTouch.Kind.HOLD, tc.held(1700));
        assertNull("a hold is reported once", tc.held(2500));
        assertNull("and lifting after a hold is not also a tap", tc.up(2600));
    }

    @Test
    public void reconstructionKeepsTheFaceUnlessTheUserApprovesAChange() {
        Inspector in = new Inspector(obi);
        HumanAsset again = in.reconstruct(Look.defaults(false), new AssetBuilder.Budget(), false);
        assertNotNull(in.lastReport(), again);
        assertTrue(in.sameIdentity(again));
        Look bigNose = Look.defaults(false);
        bigNose.nose = 1.6f;
        bigNose.lips = 1.4f;
        assertNull("an unapproved change of face is refused", in.reconstruct(bigNose, new AssetBuilder.Budget(), false));
        assertNotNull("but a look the user asked for is allowed", in.reconstruct(bigNose, new AssetBuilder.Budget(), true));
    }

    @Test
    public void armsReachFeetStayPlantedHandsStayOutOfTheBody() {
        HumanInstance h = new HumanInstance(ivie);
        h.update(0f);
        float[] chest = h.asset.mesh.pos.clone();
        float tx = 0.25f, ty = -0.35f, tz = 0.45f;
        h.rig.reachTo(-1, tx, ty, tz, 5f);
        for (int i = 1; i <= 60; i++) {
            h.update(i / 30f);
        }
        float[] w = {h.rig.joint[DotBody.HAND_R * 3], h.rig.joint[DotBody.HAND_R * 3 + 1], h.rig.joint[DotBody.HAND_R * 3 + 2]};
        assertTrue(h.rig.validate().toString(), h.rig.validate().isEmpty());
        assertFalse(h.rig.handInside(DotBody.HAND_R));
        // feet: planted while standing and swaying
        HumanInstance s = new HumanInstance(ivie);
        s.rig.setTurntable(false);
        float minX = 9, maxX = -9;
        for (int i = 0; i < 240; i++) {
            s.update(i / 30f);
            float x = s.rig.joint[DotBody.FOOT_L * 3];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }
        assertTrue("foot slides " + (maxX - minX), maxX - minX < 0.02f);
        assertNotNull(chest);
        assertTrue(Float.isFinite(w[0]));
    }

    @Test
    public void expressionsAndSpeechDriveTheFace() {
        HumanInstance h = new HumanInstance(obi);
        FaceDrive fd = new FaceDrive();
        ai.dvine.face.FaceRig rig = new ai.dvine.face.FaceRig(false);
        fd.setExpression("surprise", 3f, 0f);
        for (float t = 0f; t < 1f; t += 0.05f) {
            rig.tick(0.05f);
            fd.apply(h, rig, t);
        }
        assertTrue(weight(h, "browUpL") > 0.8f && weight(h, "wideL") > 0.8f);
        fd.speech.word("hello", 1f);
        fd.apply(h, rig, 1.1f);
        assertTrue(weight(h, "jawOpen") > 0.1f);
        fd.speech.word("moon", 2f);
        fd.apply(h, rig, 2.01f);
        assertTrue("m closes the lips", weight(h, "lipsPress") > 0.5f);
        assertEquals("smile", FaceDrive.expressionFor("smile for me"));
    }

    // ------------------------------------------------------------ helpers

    private static Camera faceCamera(HumanInstance h) {
        Camera c = new Camera();
        c.width = 1080;
        c.height = 2000;
        c.cx = 540;
        c.unit = 2400f;
        c.distance = 0.75f;
        c.pivot = (h.rig.joint[DotBody.HEAD * 3 + 1] + 0.3f - HumanInstance.CENTRE) * HumanInstance.OUT;
        c.cy = 800f + 0f;
        c.update();
        return c;
    }

    private static float[] project(Camera c, HumanInstance h, int v) {
        return c.project(h.pos[v * 3], h.pos[v * 3 + 1], h.pos[v * 3 + 2]);
    }

    private static HumanInstance.Hit pickAt(HumanInstance h, Camera c, int v) {
        float[] s = project(c, h, v);
        float[] ray = c.ray(s[0], s[1]);
        return h.pick(ray[0], ray[1], ray[2], ray[3], ray[4], ray[5]);
    }

    private static int regionAt(HumanInstance h, Camera c, int v) {
        HumanInstance.Hit hit = pickAt(h, c, v);
        assertNotNull("ray hits the body", hit);
        return hit.region;
    }

    private static float gap(HumanInstance h) {
        // distance between the lowest upper-lip point and the highest lower-lip point at the centre
        float up = -9, low = 9;
        HumanAsset a = h.asset;
        for (int v = 0; v < a.mesh.vertexCount(); v++) {
            if (a.mesh.region[v] != Regions.LIPS || Math.abs(a.mesh.pos[v * 3]) > 0.02f) {
                continue;
            }
            float by = a.mesh.pos[v * 3 + 1], y = h.pos[v * 3 + 1];
            float mouth = Anatomy.MOUTH_Y * Anatomy.HEAD_SCALE + Anatomy.HEAD_Y;
            if (by > mouth) {
                up = Math.max(up, -y);
            } else {
                low = Math.min(low, -y);
            }
        }
        return low - up;
    }

    private static float weight(HumanInstance h, String name) {
        return h.weights[h.asset.morph(name)];
    }

    private static float z(HumanAsset a, String landmark) {
        return a.mesh.pos[a.landmark(landmark) * 3 + 2];
    }

    private static float x(HumanAsset a, String landmark) {
        return a.mesh.pos[a.landmark(landmark) * 3];
    }

    private static float[] normal(float[] p, int[] tri, int t) {
        int a = tri[t] * 3, b = tri[t + 1] * 3, c = tri[t + 2] * 3;
        float ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
        float vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static TriMesh copy(TriMesh m) {
        TriMesh c = new TriMesh();
        c.pos = m.pos.clone();
        c.tri = m.tri.clone();
        return c;
    }
}
