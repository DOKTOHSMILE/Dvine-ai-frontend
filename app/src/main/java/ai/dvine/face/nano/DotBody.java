package ai.dvine.face.nano;

import java.util.Locale;

import ai.dvine.face.WireHead;

/**
 * A full standing being drawn as nano dots, like a holographic anatomy model:
 * a smooth sculpted skin with a bright silhouette, a faint skeleton inside
 * (spine, ribs, pelvis, long bones) and the persona's living head on top.
 * The body is rigged on 17 bones, so it can take poses (T-pose, arms up,
 * wave, point, squat, sit, kneel, run, walk, bow, dance, handstand) and
 * blends smoothly between them. The rig is built in body units (feet at
 * y = -4.2, crown near +1) and handed to the camera scaled by {@link #OUT}
 * around the hips, so it fits the same view as the head: feet rest on
 * {@link #FLOOR_OUT}.
 */
public final class DotBody implements Swarm.Form {
    static final int PELVIS = 0, SPINE = 1, CHEST = 2, NECK = 3, HEAD = 4;
    static final int ARM_L = 5, FORE_L = 6, HAND_L = 7, ARM_R = 8, FORE_R = 9, HAND_R = 10;
    static final int THIGH_L = 11, SHIN_L = 12, FOOT_L = 13, THIGH_R = 14, SHIN_R = 15, FOOT_R = 16;
    static final int BONES = 17;
    static final int[] PARENT = {-1, 0, 1, 2, 3, 2, 5, 6, 2, 8, 9, 0, 11, 12, 0, 14, 15};

    public static final String[] POSES = {"stand", "tpose", "arms up", "wave", "point", "squat", "sit", "kneel",
            "run", "walk", "bow", "dance", "handstand"};

    private static final float FLOOR = -4.2f;
    /** Output scale and centre: body units to camera units. */
    public static final float OUT = 0.4f;
    private static final float CENTRE = -1.6f;
    /** Where the feet land in camera units. */
    public static final float FLOOR_OUT = (FLOOR - CENTRE) * OUT;
    private static final float HEAD_SCALE = 0.34f;
    private static final float HEAD_Y = 0.66f;

    private final DotHead head;
    public final WireHead.Pose face = new WireHead.Pose();
    /** Bind-pose joint positions. */
    final float[] joint = new float[BONES * 3];
    // body dots in bind pose
    private final float[] bind;
    private final float[] bindNormal;
    private final int[] bone;
    private final float[] parentWeight;
    /** Brightness for skeleton dots; negative means "shade the skin from its normal". */
    private final float[] fixedLit;
    private final int count;
    // head dots, posed each frame in head space
    private final float[] headXyz;
    private final float[] headLit;

    // pose state
    private String pose = "stand";
    private final float[] from = new float[BONES * 3];
    private final float[] to = new float[BONES * 3];
    private final float[] now = new float[BONES * 3];
    private float blendStart = -10f;
    private float lastTime;
    private float ground;
    private float spinFrom;
    private float spinStart = -100f;
    // per-frame bone transforms: 3x3 rotation + translation
    private final float[] rot = new float[BONES * 9];
    private final float[] move = new float[BONES * 3];
    /** Posed joint positions (before the floor shift), for tests. */
    final float[] jointWorld = new float[BONES * 3];

    public DotBody(DotHead head) {
        this.head = head;
        boolean ivie = head.ivie;
        float sh = ivie ? 0.47f : 0.55f;
        float hx = ivie ? 0.27f : 0.25f;
        setJoint(PELVIS, 0f, -1.5f, 0f);
        setJoint(SPINE, 0f, -1.15f, 0f);
        setJoint(CHEST, 0f, -0.6f, 0f);
        setJoint(NECK, 0f, 0.1f, -0.02f);
        setJoint(HEAD, 0f, 0.33f, 0f);
        for (int s = -1; s <= 1; s += 2) {
            int o = s > 0 ? 0 : 3;
            // arms rest in a relaxed A-pose (BIND_ARM degrees below level), so shoulders bend less
            arm(s, sh, 0f, ARM_L + o);
            arm(s, sh, 0.82f, FORE_L + o);
            arm(s, sh, 1.52f, HAND_L + o);
            setJoint(THIGH_L + o, s * hx, -1.62f, 0f);
            setJoint(SHIN_L + o, s * (hx + 0.02f), -2.88f, 0.03f);
            setJoint(FOOT_L + o, s * (hx + 0.03f), -4.0f, -0.02f);
        }

        Sculpt skin = new Sculpt();
        skin.blend(0.16f).tag(CHEST)
                .ellipsoid(0f, -0.42f, 0f, sh * 0.84f, 0.5f, 0.26f)
                .ellipsoid(0f, 0.0f, -0.04f, sh * 0.72f, 0.1f, 0.15f);                               // trapezius
        if (ivie) {
            skin.blend(0.1f).ellipsoid(0.16f, -0.42f, 0.16f, 0.14f, 0.13f, 0.13f).ellipsoid(-0.16f, -0.42f, 0.16f, 0.14f, 0.13f, 0.13f);
        } else {
            skin.blend(0.12f).ellipsoid(0.19f, -0.3f, 0.13f, 0.2f, 0.13f, 0.12f).ellipsoid(-0.19f, -0.3f, 0.13f, 0.2f, 0.13f, 0.12f);
        }
        skin.blend(0.2f).tag(SPINE).ellipsoid(0f, -1.0f, 0.02f, ivie ? 0.28f : 0.34f, 0.4f, 0.21f);
        skin.blend(0.18f).tag(PELVIS).ellipsoid(0f, -1.48f, 0f, ivie ? 0.41f : 0.35f, 0.26f, 0.24f)
                .ellipsoid(0.15f, -1.55f, -0.1f, 0.17f, 0.18f, 0.15f).ellipsoid(-0.15f, -1.55f, -0.1f, 0.17f, 0.18f, 0.15f);
        skin.blend(0.12f).tag(NECK).capsule(0f, -0.05f, -0.02f, ivie ? 0.11f : 0.135f, 0f, 0.42f, 0f, ivie ? 0.1f : 0.12f);
        for (int s = -1; s <= 1; s += 2) {
            int o = s > 0 ? 0 : 3;
            float a = ivie ? 0.86f : 1f;
            Arm m = new Arm(s, sh);
            skin.blend(0.1f).tag(ARM_L + o);
            m.ball(skin, 0.02f, -0.03f, 0f, 0.14f * a);                                                   // deltoid
            m.capsule(skin, 0f, 0f, 0f, 0.12f * a, 0.82f, 0f, 0f, 0.075f * a);
            m.capsule(skin, 0.2f, 0.02f, 0.03f, 0.1f * a, 0.62f, 0.01f, 0.03f, 0.085f * a);                // biceps
            skin.blend(0.08f).tag(FORE_L + o);
            m.capsule(skin, 0.82f, 0f, 0f, 0.075f * a, 1.52f, 0f, 0f, 0.048f * a);
            m.capsule(skin, 0.9f, 0.01f, 0f, 0.085f * a, 1.25f, 0.005f, 0f, 0.065f * a);                    // forearm muscle
            skin.blend(0.03f).tag(HAND_L + o);
            m.capsule(skin, 1.56f, -0.01f, 0f, 0.05f, 1.7f, -0.01f, 0f, 0.055f);                           // palm
            for (int f = 0; f < 4; f++) {
                float z = -0.054f + f * 0.036f;
                float len = f == 0 || f == 3 ? 0.17f : 0.21f;
                m.capsule(skin, 1.73f, -0.01f, z, 0.019f, 1.73f + len, -0.025f, z * 1.15f, 0.014f);
            }
            m.capsule(skin, 1.6f, -0.02f, 0.07f, 0.022f, 1.73f, -0.03f, 0.14f, 0.016f);                    // thumb
            skin.blend(0.12f).tag(THIGH_L + o)
                    .capsule(s * hx, -1.62f, 0f, ivie ? 0.21f : 0.2f, s * (hx + 0.02f), -2.88f, 0.03f, 0.115f)
                    .ellipsoid(s * (hx + 0.02f), -2.12f, 0.05f, 0.17f, 0.5f, 0.17f);
            skin.blend(0.1f).tag(SHIN_L + o)
                    .capsule(s * (hx + 0.02f), -2.88f, 0.03f, 0.11f, s * (hx + 0.03f), -4.0f, -0.02f, 0.058f)
                    .ellipsoid(s * (hx + 0.03f), -3.25f, -0.06f, 0.1f, 0.34f, 0.11f);
            skin.blend(0.06f).tag(FOOT_L + o)
                    .ellipsoid(s * (hx + 0.04f), -4.12f, 0.12f, 0.08f, 0.055f, 0.2f)
                    .sphere(s * (hx + 0.03f), -4.1f, -0.04f, 0.065f);
        }
        Sculpt.Cloud c = skin.sample(ivie ? 5000 : 5200, ivie ? 3 : 4, (x, y, z) -> y < 0.36f);

        Skeleton bones = new Skeleton();
        bones.build(sh, hx, ivie);
        count = c.size + bones.n;
        bind = new float[count * 3];
        bindNormal = new float[count * 3];
        bone = new int[count];
        parentWeight = new float[count];
        fixedLit = new float[count];
        for (int i = 0; i < c.size; i++) {
            System.arraycopy(c.xyz, i * 3, bind, i * 3, 3);
            System.arraycopy(c.normal, i * 3, bindNormal, i * 3, 3);
            bone[i] = c.tag[i];
            fixedLit[i] = -1f;
        }
        for (int i = 0; i < bones.n; i++) {
            int k = c.size + i;
            System.arraycopy(bones.xyz, i * 3, bind, k * 3, 3);
            bindNormal[k * 3 + 2] = 1f;
            bone[k] = bones.bone[i];
            fixedLit[k] = bones.lit[i];
        }
        for (int i = 0; i < count; i++) {
            int b = bone[i];
            if (PARENT[b] < 0) {
                continue;
            }
            float dx = bind[i * 3] - joint[b * 3], dy = bind[i * 3 + 1] - joint[b * 3 + 1], dz = bind[i * 3 + 2] - joint[b * 3 + 2];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float r = b <= HEAD ? 0.32f : b == ARM_L || b == ARM_R ? 0.3f : b == HAND_L || b == HAND_R || b == FOOT_L || b == FOOT_R ? 0.1f : 0.2f;
            float t = Math.min(1f, d / r);
            parentWeight[i] = 0.5f * (1f - t * t * (3f - 2f * t));
        }
        headXyz = new float[head.size * 3];
        headLit = new float[head.size];
        poseAngles("stand", 0f, to);
        System.arraycopy(to, 0, from, 0, to.length);
    }

    /** Arms rest this many degrees below level. */
    static final float BIND_ARM = 40f;

    /** Joint of an arm bone, {@code along} units from the shoulder in the A-pose. */
    private void arm(int side, float sh, float along, int b) {
        float[] p = new float[]{along, 0f, 0f};
        new Arm(side, sh).place(p, 0);
        setJoint(b, p[0], p[1], p[2]);
    }

    /**
     * Arm-local coordinates: x runs from the shoulder to the fingertips, y up,
     * z forward. {@link #place} turns them into body space for one side.
     */
    static final class Arm {
        final float side;
        final float sh;
        final float c = (float) Math.cos(Math.toRadians(BIND_ARM));
        final float s = (float) Math.sin(Math.toRadians(BIND_ARM));

        Arm(int side, float sh) {
            this.side = side;
            this.sh = sh;
        }

        void place(float[] p, int o) {
            float x = p[o], y = p[o + 1];
            p[o] = side * (sh + x * c + y * s);
            p[o + 1] = -0.02f - x * s + y * c;
        }

        void capsule(Sculpt k, float ax, float ay, float az, float ra, float bx, float by, float bz, float rb) {
            float[] a = {ax, ay, az, bx, by, bz};
            place(a, 0);
            place(a, 3);
            k.capsule(a[0], a[1], a[2], ra, a[3], a[4], a[5], rb);
        }

        void ball(Sculpt k, float x, float y, float z, float r) {
            float[] a = {x, y, z};
            place(a, 0);
            k.sphere(a[0], a[1], a[2], r);
        }
    }

    private void setJoint(int b, float x, float y, float z) {
        joint[b * 3] = x;
        joint[b * 3 + 1] = y;
        joint[b * 3 + 2] = z;
    }

    public WireHead.Pose pose() {
        return face;
    }

    /** Points this body produces at most. */
    public int size() {
        return count + head.size;
    }

    /** Which pose a spoken request names ("do a t-pose", "squat", "sit down"…), or null. */
    public static String poseFor(String lower) {
        String t = lower.toLowerCase(Locale.US);
        if (t.contains("t-pose") || t.contains("t pose") || t.contains("arms out") || t.contains("spread your arms")) return "tpose";
        if (t.contains("handstand") || t.contains("upside down")) return "handstand";
        if (t.contains("raise your hands") || t.contains("raise your arms") || t.contains("hands up") || t.contains("arms up")) return "arms up";
        if (t.contains("wave")) return "wave";
        if (t.contains("point")) return "point";
        if (t.contains("squat") || t.contains("crouch")) return "squat";
        if (t.contains("sit") || t.contains("meditate") || t.contains("cross leg")) return "sit";
        if (t.contains("kneel")) return "kneel";
        if (t.contains("run") || t.contains("jog")) return "run";
        if (t.contains("walk")) return "walk";
        if (t.contains("bow")) return "bow";
        if (t.contains("dance")) return "dance";
        if (t.contains("stand") || t.contains("relax") || t.contains("normal pose")) return "stand";
        return null;
    }

    /** Moves into a pose over about half a second. Unknown names are ignored. */
    public void setPose(String name) {
        boolean known = false;
        for (String p : POSES) {
            known |= p.equals(name);
        }
        if (!known || name.equals(pose)) {
            return;
        }
        System.arraycopy(now, 0, from, 0, now.length);
        pose = name;
        blendStart = lastTime;
    }

    public String currentPose() {
        return pose;
    }

    /** One full turn so the user sees every side, like orbiting the model in Blender. */
    public void turnAround() {
        spinStart = lastTime;
        spinFrom = 0f;
    }

    @Override
    public int fill(float[] xyz, float[] lit, int max, float time) {
        lastTime = time;
        poseAngles(pose, time, to);
        float b = Math.min(1f, (time - blendStart) / 0.6f);
        b = b * b * (3f - 2f * b);
        for (int i = 0; i < now.length; i++) {
            now[i] = from[i] + (to[i] - from[i]) * b;
        }
        solve();
        float turn = 22f * (float) Math.sin(time * 0.35f);
        float spin = time - spinStart;
        if (spin < 8f) {
            float s = spin / 8f;
            turn += 360f * s * s * (3f - 2f * s);
        }
        float ty = (float) Math.toRadians(turn);
        float cy = (float) Math.cos(ty), sy = (float) Math.sin(ty);

        int n = 0;
        float low = 1e9f;
        for (int i = 0; i < count && n < max; i++) {
            int bi = bone[i];
            int p = PARENT[bi];
            float w = parentWeight[i];
            float x = bind[i * 3], y = bind[i * 3 + 1], z = bind[i * 3 + 2];
            float ox = tx(bi, x, y, z), oy = tyy(bi, x, y, z), oz = tz(bi, x, y, z);
            if (w > 0f && p >= 0) {
                ox += (tx(p, x, y, z) - ox) * w;
                oy += (tyy(p, x, y, z) - oy) * w;
                oz += (tz(p, x, y, z) - oz) * w;
            }
            xyz[n * 3] = ox * cy + oz * sy;
            xyz[n * 3 + 1] = oy;
            xyz[n * 3 + 2] = -ox * sy + oz * cy;
            low = Math.min(low, oy);
            float f = fixedLit[i];
            if (f >= 0f) {
                lit[n] = f;
            } else {
                int r = bi * 9;
                float nx = bindNormal[i * 3], ny = bindNormal[i * 3 + 1], nz = bindNormal[i * 3 + 2];
                float mx = rot[r] * nx + rot[r + 1] * ny + rot[r + 2] * nz;
                float my = rot[r + 3] * nx + rot[r + 4] * ny + rot[r + 5] * nz;
                float mz = rot[r + 6] * nx + rot[r + 7] * ny + rot[r + 8] * nz;
                lit[n] = Sculpt.holo(mx * cy + mz * sy, my, -mx * sy + mz * cy);
            }
            n++;
        }
        // the living head: posed in its own space, then carried by the head bone
        face.time = time;
        int h = head.pose(face, headXyz, headLit, head.size, 3, -1.05f);
        for (int i = 0; i < h && n < max; i++) {
            float x = headXyz[i * 3] * HEAD_SCALE, y = headXyz[i * 3 + 1] * HEAD_SCALE + HEAD_Y, z = headXyz[i * 3 + 2] * HEAD_SCALE;
            float ox = tx(HEAD, x, y, z), oy = tyy(HEAD, x, y, z), oz = tz(HEAD, x, y, z);
            xyz[n * 3] = ox * cy + oz * sy;
            xyz[n * 3 + 1] = oy;
            xyz[n * 3 + 2] = -ox * sy + oz * cy;
            low = Math.min(low, oy);
            lit[n] = headLit[i];
            n++;
        }
        // keep the lowest point on the floor (feet, knees, hands in a handstand)
        float want = FLOOR - low;
        ground += (want - ground) * (time - blendStart < 0.05f ? 1f : 0.6f);
        if (Math.abs(want - ground) > 1.5f) {
            ground = want;
        }
        for (int i = 0; i < n * 3; i += 3) {
            xyz[i] *= OUT;
            xyz[i + 1] = (xyz[i + 1] + ground - CENTRE) * OUT;
            xyz[i + 2] *= OUT;
        }
        return n;
    }

    private float tx(int b, float x, float y, float z) {
        int r = b * 9;
        return rot[r] * x + rot[r + 1] * y + rot[r + 2] * z + move[b * 3];
    }

    private float tyy(int b, float x, float y, float z) {
        int r = b * 9;
        return rot[r + 3] * x + rot[r + 4] * y + rot[r + 5] * z + move[b * 3 + 1];
    }

    private float tz(int b, float x, float y, float z) {
        int r = b * 9;
        return rot[r + 6] * x + rot[r + 7] * y + rot[r + 8] * z + move[b * 3 + 2];
    }

    /** Forward kinematics: world rotation and offset per bone from the blended angles. */
    private void solve() {
        float[] local = new float[9];
        float[] world = jointWorld;
        for (int b = 0; b < BONES; b++) {
            euler(now[b * 3], now[b * 3 + 1], now[b * 3 + 2], local);
            if (b >= ARM_L && b <= HAND_R) {
                // poses are written for arms held level; the rig rests in an A-pose
                fromLevel(local, b >= ARM_R ? -BIND_ARM : BIND_ARM, b == ARM_L || b == ARM_R);
            }
            int p = PARENT[b];
            int r = b * 9;
            if (p < 0) {
                System.arraycopy(local, 0, rot, r, 9);
                world[0] = joint[0];
                world[1] = joint[1];
                world[2] = joint[2];
            } else {
                int q = p * 9;
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        rot[r + i * 3 + j] = rot[q + i * 3] * local[j] + rot[q + i * 3 + 1] * local[3 + j] + rot[q + i * 3 + 2] * local[6 + j];
                    }
                }
                float dx = joint[b * 3] - joint[p * 3], dy = joint[b * 3 + 1] - joint[p * 3 + 1], dz = joint[b * 3 + 2] - joint[p * 3 + 2];
                world[b * 3] = world[p * 3] + rot[q] * dx + rot[q + 1] * dy + rot[q + 2] * dz;
                world[b * 3 + 1] = world[p * 3 + 1] + rot[q + 3] * dx + rot[q + 4] * dy + rot[q + 5] * dz;
                world[b * 3 + 2] = world[p * 3 + 2] + rot[q + 6] * dx + rot[q + 7] * dy + rot[q + 8] * dz;
            }
            // p' = R (p - J) + W
            float jx = joint[b * 3], jy = joint[b * 3 + 1], jz = joint[b * 3 + 2];
            move[b * 3] = world[b * 3] - (rot[r] * jx + rot[r + 1] * jy + rot[r + 2] * jz);
            move[b * 3 + 1] = world[b * 3 + 1] - (rot[r + 3] * jx + rot[r + 4] * jy + rot[r + 5] * jz);
            move[b * 3 + 2] = world[b * 3 + 2] - (rot[r + 6] * jx + rot[r + 7] * jy + rot[r + 8] * jz);
        }
    }

    /**
     * Turns a rotation written for a level arm into one for the A-pose rig:
     * the upper arm gets R * Rz(+bind) (it first lifts to level), the forearm
     * and hand get Rz(-bind) * R * Rz(+bind) (the same hinge, seen from the rig).
     */
    private static void fromLevel(float[] m, float bindDeg, boolean upper) {
        float[] z = new float[9];
        float[] t = new float[9];
        euler(0f, 0f, bindDeg, z);
        mul(m, z, t);
        if (upper) {
            System.arraycopy(t, 0, m, 0, 9);
            return;
        }
        euler(0f, 0f, -bindDeg, z);
        mul(z, t, m);
    }

    private static void mul(float[] a, float[] b, float[] out) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
    }

    /** Rotation Ry * Rx * Rz from degrees, row-major. */
    static void euler(float xd, float yd, float zd, float[] m) {
        double x = Math.toRadians(xd), y = Math.toRadians(yd), z = Math.toRadians(zd);
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x);
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        // Rx * Rz
        float a00 = cz, a01 = -sz, a02 = 0f;
        float a10 = cx * sz, a11 = cx * cz, a12 = -sx;
        float a20 = sx * sz, a21 = sx * cz, a22 = cx;
        // Ry * (Rx * Rz)
        m[0] = cy * a00 + sy * a20;
        m[1] = cy * a01 + sy * a21;
        m[2] = cy * a02 + sy * a22;
        m[3] = a10;
        m[4] = a11;
        m[5] = a12;
        m[6] = -sy * a00 + cy * a20;
        m[7] = -sy * a01 + cy * a21;
        m[8] = -sy * a02 + cy * a22;
    }

    // ------------------------------------------------------------ poses

    /**
     * Joint angles (degrees, x/y/z per bone) for a pose at a time. Angles are
     * written for the left side (+x); right-side bones mirror y and z.
     * Signs: z+ lifts a left limb, x- swings a leg forward, x+ bends a knee or
     * leans the chest forward, y- swings a left arm or forearm forward.
     */
    static void poseAngles(String name, float t, float[] a) {
        java.util.Arrays.fill(a, 0f);
        float breathe = (float) Math.sin(t * 1.3f);
        switch (name) {
            case "tpose":
                break;
            case "arms up":
                both(a, ARM_L, 0f, 0f, 72f);
                both(a, FORE_L, 0f, 0f, 6f);
                break;
            case "wave":
                both(a, ARM_L, 0f, -6f, -78f);
                both(a, FORE_L, 0f, -12f, 0f);
                set(a, ARM_R, 0f, 0f, 12f, true);
                set(a, FORE_R, 0f, 0f, 100f + 22f * (float) Math.sin(t * 7f), true);
                set(a, HAND_R, 0f, 0f, 10f * (float) Math.sin(t * 7f + 0.6f), true);
                set(a, ARM_L, 0f, -6f, -78f, false);
                set(a, FORE_L, 0f, -12f, 0f, false);
                break;
            case "point":
                both(a, ARM_L, 0f, -6f, -78f);
                both(a, FORE_L, 0f, -12f, 0f);
                set(a, ARM_R, 0f, -84f, -4f, true);
                set(a, FORE_R, 0f, 0f, 0f, true);
                a[NECK * 3 + 1] = -8f;
                break;
            case "squat":
                both(a, THIGH_L, -100f, 0f, 16f);
                both(a, SHIN_L, 125f, 0f, 0f);
                both(a, FOOT_L, -25f, 0f, 0f);
                a[SPINE * 3] = 18f;
                a[CHEST * 3] = 12f;
                a[NECK * 3] = -20f;
                both(a, ARM_L, 0f, -38f, -62f);
                both(a, FORE_L, 0f, -30f, 0f);
                break;
            case "sit":
                both(a, THIGH_L, -90f, 50f, 0f);
                set(a, SHIN_L, 0f, 0f, -123f, false);
                set(a, SHIN_R, 0f, 0f, -118f, true);
                both(a, FOOT_L, 0f, 0f, 30f);
                both(a, ARM_L, 0f, -30f, -66f);
                both(a, FORE_L, 0f, -40f, 0f);
                a[CHEST * 3] = 2f * breathe;
                break;
            case "kneel":
                set(a, THIGH_L, -90f, 0f, 4f, false);
                set(a, SHIN_L, 90f, 0f, 0f, false);
                set(a, THIGH_R, 0f, 0f, 4f, true);
                set(a, SHIN_R, 90f, 0f, 0f, true);
                set(a, FOOT_R, 40f, 0f, 0f, true);
                both(a, ARM_L, 0f, -6f, -76f);
                set(a, ARM_R, 0f, -84f, -4f, true);
                break;
            case "run":
            case "walk": {
                boolean run = name.equals("run");
                float ph = t * (run ? 7.5f : 4.2f);
                float amp = run ? 1f : 0.55f;
                for (int s = 0; s < 2; s++) {
                    boolean right = s == 1;
                    float p = ph + (right ? (float) Math.PI : 0f);
                    float sin = (float) Math.sin(p);
                    set(a, THIGH_L + s * 3, -10f * amp - 42f * amp * sin, 0f, 2f, right);
                    set(a, SHIN_L + s * 3, (run ? 25f : 8f) + 70f * amp * Math.max(0f, (float) Math.sin(p - 1.3f)), 0f, 0f, right);
                    set(a, FOOT_L + s * 3, -10f * sin * amp, 0f, 0f, right);
                    set(a, ARM_L + s * 3, 0f, 40f * amp * sin, -76f, right);
                    set(a, FORE_L + s * 3, 0f, run ? -85f : -25f, 0f, right);
                }
                a[CHEST * 3] = run ? 10f : 3f;
                a[CHEST * 3 + 1] = 6f * (float) Math.sin(ph);
                break;
            }
            case "bow":
                both(a, ARM_L, 0f, -4f, -82f);
                a[SPINE * 3] = 20f;
                a[CHEST * 3] = 26f;
                a[NECK * 3] = 15f;
                break;
            case "dance": {
                float s = (float) Math.sin(t * 3.2f);
                a[PELVIS * 3 + 2] = 6f * s;
                a[CHEST * 3 + 2] = -9f * s;
                a[NECK * 3 + 2] = 5f * s;
                set(a, ARM_L, 0f, 0f, 55f + 25f * s, false);
                set(a, FORE_L, 0f, 0f, 40f, false);
                set(a, ARM_R, 0f, 0f, 55f - 25f * s, true);
                set(a, FORE_R, 0f, 0f, 40f, true);
                set(a, THIGH_L, -14f * Math.max(0f, s), 0f, 6f, false);
                set(a, SHIN_L, 24f * Math.max(0f, s), 0f, 0f, false);
                set(a, THIGH_R, -14f * Math.max(0f, -s), 0f, 6f, true);
                set(a, SHIN_R, 24f * Math.max(0f, -s), 0f, 0f, true);
                break;
            }
            case "handstand":
                a[PELVIS * 3 + 2] = 180f;
                both(a, ARM_L, 0f, 0f, 84f);
                both(a, THIGH_L, 0f, 0f, 6f + 2f * breathe);
                break;
            default: // stand: relaxed, arms down, a little breath and weight shift
                both(a, ARM_L, 0f, -6f, -77f + 1.5f * breathe);
                both(a, FORE_L, 0f, -14f, 0f);
                both(a, HAND_L, 0f, 0f, -4f);
                a[PELVIS * 3 + 2] = 1.2f * (float) Math.sin(t * 0.5f);
                a[CHEST * 3] = -0.8f * breathe;
                both(a, THIGH_L, 0f, 0f, 1.5f);
                break;
        }
    }

    private static void both(float[] a, int leftBone, float x, float y, float z) {
        set(a, leftBone, x, y, z, false);
        set(a, leftBone + 3, x, y, z, true);
    }

    private static void set(float[] a, int bone, float x, float y, float z, boolean right) {
        a[bone * 3] = x;
        a[bone * 3 + 1] = right ? -y : y;
        a[bone * 3 + 2] = right ? -z : z;
    }

    // ------------------------------------------------------------ skeleton

    /** Faint inner bones, built from curves (no sampling needed). */
    static final class Skeleton {
        float[] xyz = new float[2400 * 3];
        float[] lit = new float[2400];
        int[] bone = new int[2400];
        int n;

        void add(float x, float y, float z, float l, int b) {
            if (n == lit.length) {
                return;
            }
            xyz[n * 3] = x;
            xyz[n * 3 + 1] = y;
            xyz[n * 3 + 2] = z;
            lit[n] = l;
            bone[n] = b;
            n++;
        }

        void line(float ax, float ay, float az, float bx, float by, float bz, float step, float l, int b) {
            float len = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
            int k = Math.max(2, (int) (len / step));
            for (int i = 0; i <= k; i++) {
                float t = i / (float) k;
                add(ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t, l, b);
            }
        }

        void knob(float x, float y, float z, float r, int count, float l, int b) {
            for (int i = 0; i < count; i++) {
                float v = 1f - 2f * (i + 0.5f) / count;
                float rr = (float) Math.sqrt(1f - v * v);
                double a = i * 2.399963;
                add(x + (float) Math.cos(a) * rr * r, y + v * r, z + (float) Math.sin(a) * rr * r, l, b);
            }
        }

        void build(float sh, float hx, boolean ivie) {
            float dim = 0.26f;
            // spine: a column of small vertebrae following the back's curve
            for (int i = 0; i < 22; i++) {
                float y = 0.28f - i * 0.085f;
                float z = -0.12f - 0.035f * (float) Math.sin((y + 1.6f) * 1.9f);
                int b = y > 0.08f ? NECK : y > -0.62f ? CHEST : y > -1.2f ? SPINE : PELVIS;
                float r = 0.03f + 0.012f * (1f - Math.abs(y + 0.6f));
                for (int j = 0; j < 9; j++) {
                    double a = j * Math.PI * 2 / 9;
                    add((float) Math.cos(a) * r, y, z + (float) Math.sin(a) * r * 0.8f, dim + 0.1f, b);
                }
                add(0f, y - 0.02f, z - r - 0.04f, dim + 0.12f, b);
            }
            // ribs: arcs from the spine round to the breastbone
            for (int i = 0; i < 9; i++) {
                float y = -0.1f - i * 0.075f;
                float rx = (sh * 0.62f) * (0.72f + 0.28f * (float) Math.sin(Math.PI * (i + 2) / 12f));
                float rz = 0.21f;
                for (int s = -1; s <= 1; s += 2) {
                    for (int j = 0; j <= 26; j++) {
                        double a = Math.PI * j / 26;
                        float x = s * (float) Math.sin(a) * rx;
                        float z = -0.06f - (float) Math.cos(a) * rz * 0.55f + (float) (1 - Math.cos(a)) * 0.06f;
                        add(x, y - 0.09f * (float) (1 - Math.cos(a)) * 0.5f - 0.05f * (float) Math.sin(a), z + 0.03f, dim, CHEST);
                    }
                }
            }
            line(0f, -0.08f, 0.2f, 0f, -0.68f, 0.21f, 0.025f, dim + 0.08f, CHEST);                       // breastbone
            for (int s = -1; s <= 1; s += 2) {
                int o = s > 0 ? 0 : 3;
                line(s * 0.04f, -0.03f, 0.14f, s * sh, 0.0f, 0.02f, 0.025f, dim + 0.05f, CHEST);           // collarbone
                Arm m = new Arm(s, sh);
                int from = n;
                knob(0f, 0f, 0f, 0.055f, 16, dim + 0.05f, ARM_L + o);                                     // shoulder joint
                line(0f, 0f, 0f, 0.8f, 0f, 0f, 0.03f, dim, ARM_L + o);                                     // upper arm
                knob(0.82f, 0f, 0f, 0.04f, 12, dim + 0.05f, FORE_L + o);                                  // elbow
                line(0.84f, 0f, 0.02f, 1.5f, 0f, 0.025f, 0.03f, dim, FORE_L + o);
                line(0.84f, 0f, -0.02f, 1.5f, 0f, -0.025f, 0.03f, dim, FORE_L + o);
                for (int f = 0; f < 4; f++) {
                    float z = -0.054f + f * 0.036f;
                    line(1.56f, -0.01f, z * 0.6f, 1.92f, -0.02f, z * 1.15f, 0.03f, dim, HAND_L + o);
                }
                for (int i = from; i < n; i++) {
                    m.place(xyz, i * 3);
                }
                knob(s * hx, -1.62f, 0f, 0.07f, 18, dim + 0.05f, THIGH_L + o);                             // hip joint
                line(s * hx, -1.66f, 0f, s * (hx + 0.02f), -2.84f, 0.03f, 0.03f, dim, THIGH_L + o);          // thigh bone
                knob(s * (hx + 0.02f), -2.88f, 0.1f, 0.05f, 14, dim + 0.08f, SHIN_L + o);                  // kneecap
                line(s * (hx + 0.01f), -2.92f, 0.03f, s * (hx + 0.03f), -3.97f, 0f, 0.03f, dim, SHIN_L + o);
                line(s * (hx + 0.08f), -2.95f, -0.02f, s * (hx + 0.07f), -3.95f, -0.03f, 0.04f, dim * 0.8f, SHIN_L + o);
                line(s * (hx + 0.03f), -4.08f, -0.05f, s * (hx + 0.05f), -4.14f, 0.28f, 0.03f, dim, FOOT_L + o);
                // pelvis wing
                for (int j = 0; j <= 18; j++) {
                    double a = Math.PI * j / 18;
                    add(s * (0.06f + (ivie ? 0.3f : 0.25f) * (float) Math.sin(a)), -1.32f - 0.16f * (float) Math.sin(a * 0.5),
                            -0.02f + 0.12f * (float) Math.cos(a), dim, PELVIS);
                }
            }
            // pelvis bowl and the heart, glowing a little brighter
            for (int j = 0; j < 30; j++) {
                double a = j * Math.PI * 2 / 30;
                add((ivie ? 0.2f : 0.17f) * (float) Math.cos(a), -1.55f, 0.02f + 0.1f * (float) Math.sin(a), dim, PELVIS);
            }
            knob(0.07f, -0.44f, 0.06f, 0.075f, 40, 0.42f, CHEST);
        }
    }
}
