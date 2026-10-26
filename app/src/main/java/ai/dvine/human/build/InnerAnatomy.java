package ai.dvine.human.build;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.create.Geo;
import ai.dvine.face.nano.DotBody;
import ai.dvine.human.mesh.TriMesh;
import ai.dvine.human.rig.Skeleton;

/**
 * The inside of the body, for the anatomical view (ANRE Mode B): the
 * skeleton, the major muscles, the organs and the great vessels, as closed
 * meshes in the same body coordinates as the skin, each riding one bone of
 * the same rig, so they stay in place inside the body whatever it does.
 * These are visual representations only: nothing here functions.
 */
public final class InnerAnatomy {
    public static final int SKELETON = 0, MUSCLE = 1, ORGAN = 2, VESSEL = 3;
    public static final String[] LAYERS = {"skeleton", "muscles", "organs", "vessels"};

    /** One structure: its name, layer and the bone it rides. */
    public static final class Part {
        public final int id;
        public final String name;
        public final int layer;
        public final int bone;

        Part(int id, String name, int layer, int bone) {
            this.id = id;
            this.name = name;
            this.layer = layer;
            this.bone = bone;
        }
    }

    /** The result: one mesh (region = part id per vertex, bone0 = its bone), and the part table. */
    public static final class Result {
        public final TriMesh mesh;
        public final List<Part> parts;

        Result(TriMesh mesh, List<Part> parts) {
            this.mesh = mesh;
            this.parts = parts;
        }
    }

    private final Skeleton sk;
    private final float sh, hx, a;
    private final List<Part> parts = new ArrayList<Part>();
    private final List<TriMesh> meshes = new ArrayList<TriMesh>();
    private final List<Integer> owner = new ArrayList<Integer>();

    private InnerAnatomy(Skeleton sk) {
        this.sk = sk;
        this.sh = sk.sh;
        this.hx = sk.hx;
        this.a = sk.female ? 0.9f : 1f;
    }

    public static Result build(Skeleton sk) {
        InnerAnatomy b = new InnerAnatomy(sk);
        b.skeleton();
        b.muscles();
        b.organs();
        b.vessels();
        return b.result();
    }

    // ------------------------------------------------------------ skeleton

    private void skeleton() {
        float[] skull = AssetBuilder.toBody(0f, 0.12f, -0.1f);
        add("skull", SKELETON, DotBody.HEAD, Geo.sphere(skull[0], skull[1], skull[2], 0.265f, 0.285f, 10));
        float[] jaw = AssetBuilder.toBody(0f, -0.7f, 0.28f);
        add("jaw", SKELETON, DotBody.HEAD, Geo.frustum(jaw[0], jaw[1], jaw[2], 0.2f, 0.17f, 0.24f, 0.19f, 0.05f, 0f));
        int part = newPart("spine", SKELETON, DotBody.CHEST);
        float[][] spine = {{0f, 0.26f, -0.07f, DotBody.NECK}, {0f, -0.02f, -0.1f, DotBody.CHEST}, {0f, -0.6f, -0.16f, DotBody.CHEST},
                {0f, -0.98f, -0.13f, DotBody.SPINE}, {0f, -1.36f, -0.1f, DotBody.SPINE}};
        for (int k = 0; k + 1 < spine.length; k++) {
            int n = 6;
            for (int j = 0; j < n; j++) {
                float t0 = (j + 0.1f) / n, t1 = (j + 0.85f) / n;
                float[] p = spine[k], q = spine[k + 1];
                TriMesh v = Geo.cylinder(lerp(p[0], q[0], t0), lerp(p[1], q[1], t0), lerp(p[2], q[2], t0),
                        lerp(p[0], q[0], t1), lerp(p[1], q[1], t1), lerp(p[2], q[2], t1), 0.034f + 0.006f * k, 8);
                addTo(part, v, (int) p[3]);
            }
        }
        int ribs = newPart("ribs", SKELETON, DotBody.CHEST);
        for (int r = 0; r < 10; r++) {
            float y = -0.12f - r * 0.068f, ra = (0.27f + 0.09f * (float) Math.sin(Math.PI * (r + 1) / 11.0)) * sh / 0.55f, rb = 0.19f;
            for (int side = -1; side <= 1; side += 2) {
                float prevX = 0f, prevY = 0f, prevZ = 0f;
                for (int j = 0; j <= 7; j++) {
                    double ang = Math.PI * (0.55 + 0.9 * j / 7.0); // from the back round to the front
                    float x = side * (float) Math.sin(ang) * ra, z = -(float) Math.cos(ang) * rb - 0.02f, yy = y - 0.05f * j / 7f;
                    if (j > 0) {
                        addTo(ribs, Geo.cylinder(prevX, prevY, prevZ, x, yy, z, 0.012f, 6), DotBody.CHEST);
                    }
                    prevX = x;
                    prevY = yy;
                    prevZ = z;
                }
            }
        }
        add("sternum", SKELETON, DotBody.CHEST, Geo.box(0f, -0.42f, 0.2f, 0.05f, 0.42f, 0.025f));
        for (int side = -1; side <= 1; side += 2) {
            String s = side > 0 ? " left" : " right";
            add("clavicle" + s, SKELETON, DotBody.CHEST, Geo.cylinder(side * 0.05f, -0.05f, 0.12f, side * sh * 0.84f, -0.01f, 0.01f, 0.017f, 8));
            add("scapula" + s, SKELETON, DotBody.CHEST, Geo.box(side * 0.22f * sh / 0.55f, -0.3f, -0.17f, 0.15f, 0.24f, 0.025f, side * 18f));
        }
        int pelvis = newPart("pelvis", SKELETON, DotBody.PELVIS);
        for (int side = -1; side <= 1; side += 2) {
            addTo(pelvis, Geo.sphere(side * 0.16f, -1.43f, -0.02f, 0.13f, 0.1f, 8), DotBody.PELVIS);
            addTo(pelvis, Geo.cylinder(side * 0.15f, -1.5f, 0.02f, side * 0.04f, -1.64f, 0.12f, 0.025f, 8), DotBody.PELVIS);
        }
        addTo(pelvis, Geo.box(0f, -1.5f, -0.12f, 0.12f, 0.16f, 0.06f), DotBody.PELVIS);
        for (int side = -1; side <= 1; side += 2) {
            String s = side > 0 ? " left" : " right";
            int o = side > 0 ? 0 : 3;
            add("humerus" + s, SKELETON, DotBody.ARM_L + o, arm(side, 0.04f, 0f, 0f, 0.78f, 0f, 0f, 0.032f * a));
            add("radius" + s, SKELETON, DotBody.FORE_L + o, arm(side, 0.86f, 0.005f, 0.02f, Skeleton.WRIST - 0.04f, 0.005f, 0.02f, 0.017f * a));
            add("ulna" + s, SKELETON, DotBody.FORE_L + o, arm(side, 0.84f, -0.01f, -0.02f, Skeleton.WRIST - 0.04f, -0.01f, -0.02f, 0.016f * a));
            int hand = newPart("hand bones" + s, SKELETON, DotBody.HAND_L + o);
            int fingers = newPart("finger bones" + s, SKELETON, (side > 0 ? Skeleton.FINGER_L : Skeleton.FINGER_R));
            int base = side > 0 ? Skeleton.FINGER_L : Skeleton.FINGER_R;
            for (int f = 1; f < 5; f++) {
                float z = Skeleton.FINGER_Z[f] * a;
                addTo(hand, arm(side, Skeleton.WRIST + 0.03f, -0.012f, z * 0.7f, Skeleton.KNUCKLE - 0.01f, -0.012f, z, 0.008f), DotBody.HAND_L + o);
                float len = Skeleton.FINGER_LEN[f] * a, mid = Skeleton.KNUCKLE + len * Skeleton.SPLIT;
                addTo(fingers, arm(side, Skeleton.KNUCKLE + 0.006f, -0.013f, z, mid - 0.006f, -0.016f, z * Skeleton.SPREAD_MID, 0.0075f), base + f * 2);
                addTo(fingers, arm(side, mid + 0.006f, -0.016f, z * Skeleton.SPREAD_MID, Skeleton.KNUCKLE + len - 0.012f, -0.02f,
                        z * Skeleton.SPREAD_TIP, 0.006f), base + f * 2 + 1);
            }
            add("femur" + s, SKELETON, DotBody.THIGH_L + o, Geo.cylinder(side * hx, -1.68f, 0f, side * (hx + 0.02f), -2.8f, 0.02f, 0.036f, 10));
            add("patella" + s, SKELETON, DotBody.THIGH_L + o, Geo.sphere(side * (hx + 0.02f), -2.86f, 0.085f, 0.035f, 0.04f, 6));
            add("tibia" + s, SKELETON, DotBody.SHIN_L + o, Geo.cylinder(side * (hx + 0.02f), -2.93f, 0.03f, side * (hx + 0.03f), -3.94f, -0.01f, 0.028f, 10));
            add("fibula" + s, SKELETON, DotBody.SHIN_L + o, Geo.cylinder(side * (hx + 0.07f), -2.96f, -0.01f, side * (hx + 0.07f), -3.93f, -0.02f, 0.013f, 8));
            add("foot bones" + s, SKELETON, DotBody.FOOT_L + o, Geo.box(side * (hx + 0.04f), -4.12f, 0.1f, 0.06f, 0.025f, 0.3f));
        }
    }

    // ------------------------------------------------------------ muscles

    private void muscles() {
        float k = sh / 0.55f;
        add("trapezius", MUSCLE, DotBody.CHEST, Geo.box(0f, -0.07f, -0.08f, 0.5f * k, 0.09f, 0.08f));
        add("abdominals", MUSCLE, DotBody.SPINE, Geo.box(0f, -0.95f, 0.13f, 0.2f, 0.48f, 0.05f));
        for (int side = -1; side <= 1; side += 2) {
            String s = side > 0 ? " left" : " right";
            int o = side > 0 ? 0 : 3;
            add("pectoral" + s, MUSCLE, DotBody.CHEST, Geo.sphere(side * 0.18f * k, -0.3f, 0.1f, 0.15f * k, 0.1f, 8));
            float[] d = Skeleton.armToBody(side, sh, 0.02f, -0.03f, 0f);
            add("deltoid" + s, MUSCLE, DotBody.ARM_L + o, Geo.sphere(d[0], d[1], d[2], 0.11f * a, 0.12f * a, 8));
            add("biceps" + s, MUSCLE, DotBody.ARM_L + o, arm(side, 0.24f, 0.02f, 0.03f, 0.62f, 0.01f, 0.03f, 0.055f * a));
            add("triceps" + s, MUSCLE, DotBody.ARM_L + o, arm(side, 0.28f, -0.01f, -0.03f, 0.7f, 0f, -0.03f, 0.05f * a));
            add("forearm muscles" + s, MUSCLE, DotBody.FORE_L + o, armCone(side, 0.9f, 1.28f, 0.058f * a, 0.035f * a));
            add("gluteus" + s, MUSCLE, DotBody.PELVIS, Geo.sphere(side * 0.15f, -1.6f, -0.09f, 0.12f, 0.13f, 8));
            add("quadriceps" + s, MUSCLE, DotBody.THIGH_L + o, Geo.cone(side * (hx + 0.01f), -1.75f, 0.05f, side * (hx + 0.02f), -2.72f, 0.07f, 0.11f, 0.07f, 12));
            add("hamstrings" + s, MUSCLE, DotBody.THIGH_L + o, Geo.cone(side * (hx + 0.01f), -1.8f, -0.06f, side * (hx + 0.01f), -2.65f, -0.05f, 0.09f, 0.06f, 12));
            add("calf" + s, MUSCLE, DotBody.SHIN_L + o, Geo.sphere(side * (hx + 0.03f), -3.22f, -0.06f, 0.07f, 0.24f, 8));
        }
    }

    // ------------------------------------------------------------ organs

    private void organs() {
        float[] brain = AssetBuilder.toBody(0f, 0.18f, -0.08f);
        add("brain", ORGAN, DotBody.HEAD, Geo.sphere(brain[0], brain[1], brain[2], 0.24f, 0.2f, 10));
        float[] stemTop = AssetBuilder.toBody(0f, -0.3f, -0.22f), stemEnd = AssetBuilder.toBody(0f, -0.95f, -0.28f);
        add("brainstem", ORGAN, DotBody.HEAD, Geo.cone(stemTop[0], stemTop[1], stemTop[2], stemEnd[0], stemEnd[1], stemEnd[2], 0.035f, 0.025f, 8));
        float k = sh / 0.55f;
        add("heart", ORGAN, DotBody.CHEST, Geo.sphere(0.05f, -0.46f, 0.06f, 0.085f, 0.1f, 8));
        add("lung left", ORGAN, DotBody.CHEST, Geo.sphere(0.17f * k, -0.43f, -0.01f, 0.13f * k, 0.26f, 10));
        add("lung right", ORGAN, DotBody.CHEST, Geo.sphere(-0.17f * k, -0.43f, -0.01f, 0.14f * k, 0.26f, 10));
        add("liver", ORGAN, DotBody.CHEST, Geo.sphere(-0.1f, -0.8f, 0.04f, 0.19f, 0.08f, 8));
        add("stomach", ORGAN, DotBody.CHEST, Geo.sphere(0.12f, -0.83f, 0.07f, 0.09f, 0.075f, 8));
        add("kidney left", ORGAN, DotBody.SPINE, Geo.sphere(0.11f, -1.0f, -0.09f, 0.045f, 0.075f, 6));
        add("kidney right", ORGAN, DotBody.SPINE, Geo.sphere(-0.11f, -1.02f, -0.09f, 0.045f, 0.075f, 6));
        int gut = newPart("intestines", ORGAN, DotBody.SPINE);
        for (int r = 0; r < 3; r++) {
            addTo(gut, Geo.torus(0f, -1.1f - r * 0.09f, 0.06f, 0.12f - r * 0.01f, 0.035f, "y", 16, 6), DotBody.SPINE);
        }
    }

    // ------------------------------------------------------------ vessels

    private void vessels() {
        int aorta = newPart("aorta", VESSEL, DotBody.CHEST);
        float[][] arch = {{0.04f, -0.38f, 0.06f}, {0.02f, -0.26f, 0.03f}, {-0.01f, -0.28f, -0.07f}, {-0.01f, -0.6f, -0.09f}};
        for (int i = 0; i + 1 < arch.length; i++) {
            addTo(aorta, Geo.cylinder(arch[i][0], arch[i][1], arch[i][2], arch[i + 1][0], arch[i + 1][1], arch[i + 1][2], 0.02f, 8), DotBody.CHEST);
        }
        addTo(aorta, Geo.cylinder(-0.01f, -0.6f, -0.09f, -0.01f, -1.36f, -0.06f, 0.018f, 8), DotBody.SPINE);
        add("vena cava", VESSEL, DotBody.SPINE, Geo.cylinder(-0.05f, -0.42f, 0.0f, -0.05f, -1.36f, -0.05f, 0.02f, 8));
        for (int side = -1; side <= 1; side += 2) {
            String s = side > 0 ? " left" : " right";
            int o = side > 0 ? 0 : 3;
            add("carotid" + s, VESSEL, DotBody.NECK, Geo.cylinder(side * 0.03f, -0.22f, 0.03f, side * 0.06f, 0.3f, 0.05f, 0.012f, 6));
            add("iliac artery" + s, VESSEL, DotBody.PELVIS, Geo.cylinder(-0.01f, -1.36f, -0.06f, side * (hx - 0.02f), -1.62f, 0.04f, 0.013f, 6));
            add("femoral artery" + s, VESSEL, DotBody.THIGH_L + o, Geo.cylinder(side * (hx - 0.02f), -1.66f, 0.05f, side * (hx + 0.01f), -2.75f, 0.0f, 0.012f, 6));
        }
    }

    // ------------------------------------------------------------ helpers

    private TriMesh arm(int side, float x0, float y0, float z0, float x1, float y1, float z1, float r) {
        float[] p = Skeleton.armToBody(side, sh, x0, y0, z0), q = Skeleton.armToBody(side, sh, x1, y1, z1);
        return Geo.cylinder(p[0], p[1], p[2], q[0], q[1], q[2], r, 8);
    }

    private TriMesh armCone(int side, float x0, float x1, float r0, float r1) {
        float[] p = Skeleton.armToBody(side, sh, x0, 0f, 0f), q = Skeleton.armToBody(side, sh, x1, 0f, 0f);
        return Geo.cone(p[0], p[1], p[2], q[0], q[1], q[2], r0, r1, 10);
    }

    private int newPart(String name, int layer, int bone) {
        Part p = new Part(parts.size(), name, layer, bone);
        parts.add(p);
        return p.id;
    }

    private void add(String name, int layer, int bone, TriMesh m) {
        addTo(newPart(name, layer, bone), m, bone);
    }

    private void addTo(int part, TriMesh m, int bone) {
        m.bone0 = new byte[m.pos.length / 3];
        java.util.Arrays.fill(m.bone0, (byte) bone);
        meshes.add(m);
        owner.add(part);
    }

    private Result result() {
        TriMesh all = Geo.merge(meshes.toArray(new TriMesh[0]));
        int n = all.pos.length / 3;
        all.region = new byte[n];
        all.bone0 = new byte[n];
        all.bone1 = new byte[n];
        all.weight0 = new float[n];
        int v = 0;
        for (int i = 0; i < meshes.size(); i++) {
            TriMesh m = meshes.get(i);
            for (int k = 0; k < m.pos.length / 3; k++, v++) {
                all.region[v] = (byte) (int) owner.get(i);
                all.bone0[v] = m.bone0[k];
                all.bone1[v] = m.bone0[k];
                all.weight0[v] = 1f;
            }
        }
        all.computeNormals();
        return new Result(all, parts);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
