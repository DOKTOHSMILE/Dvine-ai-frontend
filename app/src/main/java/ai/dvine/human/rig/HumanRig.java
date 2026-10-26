package ai.dvine.human.rig;

import ai.dvine.face.nano.DotBody;

/**
 * The anatomical controller for the 3D human. Every movement goes through
 * here: poses from the pose library are clamped to joint limits, blended,
 * rate-limited, solved with forward kinematics, then corrected by inverse
 * kinematics (gaze, a reaching hand, planted feet) and a collision pass that
 * keeps hands out of the torso. Nothing outside can set a bone directly.
 *
 * Output: a world rotation (3x3) and translation per bone, so a skinned
 * vertex is p' = R·p + t, in body units.
 */
public final class HumanRig {
    public static final int N = Skeleton.COUNT;
    private static final int CH = DotBody.CHANNELS;
    /** Fastest a joint may turn, degrees per second (the root may spin freely for stunts). */
    static final float MAX_RATE = 600f;
    /** Poses that stand on both feet, so the feet stay planted while the hips sway. */
    private static final String[] PLANTED = {"stand", "talk", "wave", "point", "arms up", "tpose", "stretch", "bow"};

    public final Skeleton skeleton;
    final float[] bind;
    /** World rotation (row-major 3x3) and translation per bone. */
    public final float[] rot = new float[N * 9];
    public final float[] move = new float[N * 3];
    /** Posed joint positions. */
    public final float[] joint = new float[N * 3];

    private String pose = "stand";
    private boolean talking;
    private final float[] from = new float[CH];
    private final float[] to = new float[CH];
    private final float[] now = new float[CH];
    private final float[] shown = new float[CH];
    private float blendStart = -10f;
    private float lastTime = -1f;
    private float[] hand = {0f, 0f}; // finger curl per hand (0 open .. 1 fist), smoothed
    private float[] handTarget = {0.25f, 0.25f};
    private boolean[] pointing = {false, false};

    // gaze (body units) and eye angles for the renderer
    private float[] gaze = {0f, 0.66f, 10f};
    private float gazeWeight = 1f;
    public float eyeYaw;
    public float eyePitch;

    // a hand reaching for a point on the body (touch reactions)
    private int reachSide;
    private final float[] reach = new float[3];
    private float reachWeight;
    private float reachUntil = -1f;

    public int collisionsFixed;
    public float turnDeg;
    private boolean turntable = true;
    private float spinStart = -100f;

    public HumanRig(Skeleton sk) {
        skeleton = sk;
        bind = sk.joint;
        DotBody.poseAngles("stand", 0f, to);
        System.arraycopy(to, 0, from, 0, CH);
        System.arraycopy(to, 0, now, 0, CH);
        System.arraycopy(to, 0, shown, 0, CH);
    }

    // ------------------------------------------------------------ requests

    /** Moves into a pose from the shared pose library (blends over about half a second). */
    public boolean setPose(String name) {
        boolean known = false;
        for (String p : DotBody.POSES) {
            known |= p.equals(name);
        }
        if (!known) {
            return false;
        }
        if (!name.equals(pose)) {
            System.arraycopy(now, 0, from, 0, CH);
            pose = name;
            blendStart = lastTime;
        }
        return true;
    }

    public String pose() {
        return pose;
    }

    public void setTalking(boolean on) {
        if (on != talking) {
            talking = on;
            if (pose.equals("stand")) {
                System.arraycopy(now, 0, from, 0, CH);
                blendStart = lastTime;
            }
        }
    }

    /** Where to look, in body units (the user sits far in front: z ≫ 0). Weight 0 lets the pose decide. */
    public void lookAt(float x, float y, float z, float weight) {
        gaze[0] = x;
        gaze[1] = y;
        gaze[2] = z;
        gazeWeight = clamp(weight, 0f, 1f);
    }

    /** One hand reaches toward a point on the body for a moment (side +1 left, -1 right). */
    public void reachTo(int side, float x, float y, float z, float seconds) {
        reachSide = side;
        reach[0] = x;
        reach[1] = y;
        reach[2] = z;
        reachUntil = lastTime + seconds;
    }

    public void turnAround() {
        spinStart = lastTime;
    }

    /** The slow show-all-sides sway of the full body; off in the face close-up, which always faces the user. */
    public void setTurntable(boolean on) {
        turntable = on;
    }

    // ------------------------------------------------------------ update

    /** Advances to {@code time} seconds and solves the whole skeleton. */
    public void update(float time) {
        float dt = lastTime < 0f ? 0f : Math.min(0.1f, time - lastTime);
        lastTime = time;
        DotBody.poseAngles(talking && pose.equals("stand") ? "talk" : pose, time, to);
        DotBody.limit(to);
        float b = clamp((time - blendStart) / 0.6f, 0f, 1f);
        b = b * b * (3f - 2f * b);
        for (int i = 0; i < CH; i++) {
            now[i] = from[i] + (to[i] - from[i]) * b;
        }
        // rate limit every joint except the root (stunts turn the whole body)
        for (int i = 0; i < CH; i++) {
            boolean root = i < 3 || i >= DotBody.BONES * 3;
            float step = MAX_RATE * dt;
            float d = now[i] - shown[i];
            shown[i] = root || dt == 0f || Math.abs(d) <= step ? now[i] : shown[i] + Math.signum(d) * step;
        }
        handShape(dt);
        turnDeg = turntable ? 22f * (float) Math.sin(time * 0.35f) : 0f;
        float spin = time - spinStart;
        if (spin < 8f) {
            float s = spin / 8f;
            turnDeg += 360f * s * s * (3f - 2f * s);
        }
        float[] angles = shown.clone();
        applyGaze(angles);
        solve(angles);
        reachWeight += ((time < reachUntil ? 1f : 0f) - reachWeight) * Math.min(1f, dt * 5f);
        if (reachWeight > 0.01f && reachSide != 0) {
            armIk(reachSide, reach, reachWeight);
        }
        if (planted()) {
            plantFeet();
        }
        collisionsFixed = resolveCollisions(angles);
    }

    private boolean planted() {
        String p = talking && pose.equals("stand") ? "talk" : pose;
        for (String s : PLANTED) {
            if (s.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public float travel() {
        return shown[DotBody.TRAVEL];
    }

    public float lift() {
        return shown[DotBody.LIFT];
    }

    // ------------------------------------------------------------ hands

    private void handShape(float dt) {
        String p = talking && pose.equals("stand") ? "talk" : pose;
        float l = 0.3f, r = 0.3f;
        boolean pl = false, pr = false;
        switch (p) {
            case "boxing":
            case "kick":
                l = r = 1f;
                break;
            case "point":
                r = 1f;
                pr = true;
                break;
            case "wave":
            case "tpose":
            case "arms up":
            case "jumping jacks":
            case "cartwheel":
            case "handstand":
            case "stretch":
            case "pushups":
            case "plank":
                l = r = 0.05f;
                break;
            case "run":
                l = r = 0.7f;
                break;
            case "talk":
                l = r = 0.15f;
                break;
            default:
                break;
        }
        handTarget[0] = l;
        handTarget[1] = r;
        pointing[0] = pl;
        pointing[1] = pr;
        for (int i = 0; i < 2; i++) {
            hand[i] += (handTarget[i] - hand[i]) * Math.min(1f, dt * 6f);
        }
    }

    // ------------------------------------------------------------ kinematics

    private final float[] local = new float[9];
    private final float[] tmp = new float[9];

    private void solve(float[] a) {
        for (int b = 0; b < N; b++) {
            localMatrix(b, a, local);
            int p = Skeleton.PARENT[b];
            int r = b * 9;
            if (p < 0) {
                // the root also carries the slow turn of the whole body
                float[] turn = new float[9];
                DotBody.euler(0f, turnDeg, 0f, turn);
                DotBody.mul(turn, local, tmp);
                System.arraycopy(tmp, 0, rot, r, 9);
                joint[0] = bind[0];
                joint[1] = bind[1];
                joint[2] = bind[2];
            } else {
                int q = p * 9;
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        rot[r + i * 3 + j] = rot[q + i * 3] * local[j] + rot[q + i * 3 + 1] * local[3 + j] + rot[q + i * 3 + 2] * local[6 + j];
                    }
                }
                float dx = bind[b * 3] - bind[p * 3], dy = bind[b * 3 + 1] - bind[p * 3 + 1], dz = bind[b * 3 + 2] - bind[p * 3 + 2];
                joint[b * 3] = joint[p * 3] + rot[q] * dx + rot[q + 1] * dy + rot[q + 2] * dz;
                joint[b * 3 + 1] = joint[p * 3 + 1] + rot[q + 3] * dx + rot[q + 4] * dy + rot[q + 5] * dz;
                joint[b * 3 + 2] = joint[p * 3 + 2] + rot[q + 6] * dx + rot[q + 7] * dy + rot[q + 8] * dz;
            }
            updateMove(b);
        }
    }

    /** Re-solves the children of one bone after its world rotation changed (used by IK). */
    private void solveChildren(int from) {
        for (int b = from + 1; b < N; b++) {
            int p = Skeleton.PARENT[b];
            if (!descends(b, from)) {
                continue;
            }
            int r = b * 9, q = p * 9;
            float[] l = boneLocal[b];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    rot[r + i * 3 + j] = rot[q + i * 3] * l[j] + rot[q + i * 3 + 1] * l[3 + j] + rot[q + i * 3 + 2] * l[6 + j];
                }
            }
            float dx = bind[b * 3] - bind[p * 3], dy = bind[b * 3 + 1] - bind[p * 3 + 1], dz = bind[b * 3 + 2] - bind[p * 3 + 2];
            joint[b * 3] = joint[p * 3] + rot[q] * dx + rot[q + 1] * dy + rot[q + 2] * dz;
            joint[b * 3 + 1] = joint[p * 3 + 1] + rot[q + 3] * dx + rot[q + 4] * dy + rot[q + 5] * dz;
            joint[b * 3 + 2] = joint[p * 3 + 2] + rot[q + 6] * dx + rot[q + 7] * dy + rot[q + 8] * dz;
            updateMove(b);
        }
    }

    private static boolean descends(int b, int ancestor) {
        for (int p = Skeleton.PARENT[b]; p >= 0; p = Skeleton.PARENT[p]) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    private void updateMove(int b) {
        int r = b * 9;
        float jx = bind[b * 3], jy = bind[b * 3 + 1], jz = bind[b * 3 + 2];
        move[b * 3] = joint[b * 3] - (rot[r] * jx + rot[r + 1] * jy + rot[r + 2] * jz);
        move[b * 3 + 1] = joint[b * 3 + 1] - (rot[r + 3] * jx + rot[r + 4] * jy + rot[r + 5] * jz);
        move[b * 3 + 2] = joint[b * 3 + 2] - (rot[r + 6] * jx + rot[r + 7] * jy + rot[r + 8] * jz);
    }

    private final float[][] boneLocal = new float[N][9];

    private void localMatrix(int b, float[] a, float[] out) {
        if (b < DotBody.BONES) {
            DotBody.euler(a[b * 3], a[b * 3 + 1], a[b * 3 + 2], out);
            if (b >= DotBody.ARM_L && b <= DotBody.HAND_R) {
                DotBody.fromLevel(out, b >= DotBody.ARM_R ? -DotBody.BIND_ARM : DotBody.BIND_ARM, b == DotBody.ARM_L || b == DotBody.ARM_R);
            }
        } else {
            fingerMatrix(b, out);
        }
        System.arraycopy(out, 0, boneLocal[b], 0, 9);
    }

    /** A finger segment curls toward the palm about its own knuckle axis (thumb across the palm). */
    private void fingerMatrix(int b, float[] out) {
        boolean right = b >= Skeleton.FINGER_R;
        int k = b - (right ? Skeleton.FINGER_R : Skeleton.FINGER_L);
        int finger = k / 2;
        boolean tip = (k & 1) == 1;
        float curl = hand[right ? 1 : 0];
        if (finger == 1 && pointing[right ? 1 : 0]) {
            curl = 0f; // the index finger points
        }
        float deg = finger == 0 ? (tip ? 35f : 25f) * curl : (tip ? 95f : 80f) * curl;
        // level-arm frame: finger along +x, palm facing -y; curling turns +x toward -y (about -z)
        float[] axis = finger == 0 ? new float[]{-0.55f, 0.2f, -0.81f} : new float[]{0f, 0f, -1f};
        axisAngle(axis, (float) Math.toRadians(deg), out);
        if (right) {
            // mirror: the right side stores rotations reflected through the x = 0 plane
            out[1] = -out[1];
            out[2] = -out[2];
            out[3] = -out[3];
            out[6] = -out[6];
        }
        DotBody.fromLevel(out, right ? -DotBody.BIND_ARM : DotBody.BIND_ARM, false);
    }

    // ------------------------------------------------------------ gaze

    private void applyGaze(float[] a) {
        if (gazeWeight <= 0f) {
            eyeYaw = eyePitch = 0f;
            return;
        }
        // direction from the head to the target, in the body's own (turned) frame, before the pose
        float turn = (float) Math.toRadians(turnDeg);
        float dx = gaze[0], dz = gaze[2];
        float lx = dx * (float) Math.cos(-turn) + dz * (float) Math.sin(-turn);
        float lz = -dx * (float) Math.sin(-turn) + dz * (float) Math.cos(-turn);
        float dy = gaze[1] - (bind[DotBody.HEAD * 3 + 1] + 0.3f);
        float yaw = (float) Math.toDegrees(Math.atan2(lx, lz)) * gazeWeight;
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(lx * lx + lz * lz))) * gazeWeight;
        // the eyes lead (a third of the turn, at most 12°); the neck and head share the rest within their limits
        eyeYaw = clamp(yaw / 3f, -12f, 12f);
        eyePitch = clamp(pitch / 3f, -10f, 10f);
        float restYaw = yaw - eyeYaw, restPitch = pitch - eyePitch;
        a[DotBody.NECK * 3 + 1] += restYaw * 0.55f;
        a[DotBody.HEAD * 3 + 1] += restYaw * 0.45f;
        a[DotBody.NECK * 3] += restPitch * 0.5f;
        a[DotBody.HEAD * 3] += restPitch * 0.5f;
        DotBody.limit(a);
    }

    // ------------------------------------------------------------ inverse kinematics

    /** Two-bone IK: places a hand (wrist) at a target, elbow bending down and back. */
    private void armIk(int side, float[] target, float weight) {
        int arm = side > 0 ? DotBody.ARM_L : DotBody.ARM_R, fore = arm + 1, handBone = arm + 2;
        float[] s = vec(joint, arm), e = vec(joint, fore), w = vec(joint, handBone);
        float[] t = lerp(w, target, weight);
        twoBone(arm, fore, handBone, s, e, w, t, new float[]{0f, -0.6f, -0.8f});
    }

    /** Keeps both feet where they stand in the bind pose while the hips sway. */
    private void plantFeet() {
        for (int side = -1; side <= 1; side += 2) {
            int thigh = side > 0 ? DotBody.THIGH_L : DotBody.THIGH_R, shin = thigh + 1, foot = thigh + 2;
            float[] h = vec(joint, thigh), k = vec(joint, shin), a = vec(joint, foot);
            // the ankle's place on the floor, turned with the body
            float turn = (float) Math.toRadians(turnDeg);
            float bx = bind[foot * 3], bz = bind[foot * 3 + 2];
            float[] target = {bx * (float) Math.cos(turn) + bz * (float) Math.sin(turn), bind[foot * 3 + 1],
                    -bx * (float) Math.sin(turn) + bz * (float) Math.cos(turn)};
            // only correct small drifts; a real step is the pose's job
            float dx = target[0] - a[0], dy = target[1] - a[1], dz = target[2] - a[2];
            if (dx * dx + dy * dy + dz * dz > 0.04f) {
                continue;
            }
            twoBone(thigh, shin, foot, h, k, a, target, new float[]{0f, 0f, 1f});
        }
    }

    /**
     * Analytic two-bone solve from root joint {@code s} (bone a), middle
     * joint {@code e} (bone b) and end {@code w} (bone c) to target {@code t},
     * bending toward {@code pole}. Rewrites the world rotations of a and b
     * (minimal rotation from their current direction) and re-solves children.
     */
    private void twoBone(int a, int b, int c, float[] s, float[] e, float[] w, float[] t, float[] pole) {
        float l1 = dist(s, e), l2 = dist(e, w);
        float[] st = sub(t, s);
        float d = clamp(len(st), Math.abs(l1 - l2) + 1e-4f, l1 + l2 - 1e-4f);
        float[] dir = norm(st);
        // elbow on the plane of the target direction and the pole
        float[] side = norm(cross(dir, pole));
        if (len(side) < 1e-4f) {
            side = norm(cross(dir, new float[]{1f, 0f, 0f}));
        }
        float[] up = cross(side, dir);
        float cosA = (l1 * l1 + d * d - l2 * l2) / (2f * l1 * d);
        float sinA = (float) Math.sqrt(Math.max(0f, 1f - cosA * cosA));
        float[] elbow = {s[0] + (dir[0] * cosA + up[0] * sinA) * l1, s[1] + (dir[1] * cosA + up[1] * sinA) * l1,
                s[2] + (dir[2] * cosA + up[2] * sinA) * l1};
        float[] end = {s[0] + dir[0] * d, s[1] + dir[1] * d, s[2] + dir[2] * d};
        rotateBone(a, sub(e, s), sub(elbow, s));
        solveChildren(a);
        rotateBone(b, sub(vec(joint, c), vec(joint, b)), sub(end, elbow));
        solveChildren(b);
    }

    /** Turns bone {@code b}'s world rotation by the minimal rotation taking {@code from} to {@code to}, keeping its joint. */
    private void rotateBone(int b, float[] from, float[] to) {
        float[] f = norm(from), t = norm(to);
        float[] axis = cross(f, t);
        float s = len(axis), c = f[0] * t[0] + f[1] * t[1] + f[2] * t[2];
        if (s < 1e-6f) {
            return;
        }
        float[] r = new float[9];
        axisAngle(new float[]{axis[0] / s, axis[1] / s, axis[2] / s}, (float) Math.atan2(s, c), r);
        float[] old = new float[9];
        System.arraycopy(rot, b * 9, old, 0, 9);
        float[] out = new float[9];
        DotBody.mul(r, old, out);
        System.arraycopy(out, 0, rot, b * 9, 9);
        // keep the bone's local matrix in step so children follow
        int p = Skeleton.PARENT[b];
        float[] pt = transpose(rot, p * 9);
        DotBody.mul(pt, out, boneLocal[b]);
        updateMove(b);
    }

    // ------------------------------------------------------------ collisions

    /**
     * Keeps the hands out of the torso and head: if a wrist or fingertip
     * ends inside the chest, belly or head volume, the upper arm opens out a
     * little at a time until it is clear (at most a few steps).
     */
    private int resolveCollisions(float[] a) {
        int fixes = 0;
        for (int side = -1; side <= 1; side += 2) {
            int arm = side > 0 ? DotBody.ARM_L : DotBody.ARM_R;
            for (int step = 0; step < 6; step++) {
                if (!handInside(arm + 2)) {
                    break;
                }
                // open the arm outward (z+ lifts a left arm; the right side stores it mirrored)
                float[] open = new float[9];
                DotBody.euler(0f, 0f, side * 5f, open);
                float[] out = new float[9];
                System.arraycopy(boneLocal[arm], 0, tmp, 0, 9);
                DotBody.mul(tmp, open, out);
                System.arraycopy(out, 0, boneLocal[arm], 0, 9);
                int p = Skeleton.PARENT[arm];
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        rot[arm * 9 + i * 3 + j] = rot[p * 9 + i * 3] * out[j] + rot[p * 9 + i * 3 + 1] * out[3 + j]
                                + rot[p * 9 + i * 3 + 2] * out[6 + j];
                    }
                }
                updateMove(arm);
                solveChildren(arm);
                fixes++;
            }
        }
        return fixes;
    }

    /** Whether a hand's wrist sits inside the torso or head volumes. */
    public boolean handInside(int handBone) {
        float[] w = vec(joint, handBone);
        float[] chestA = vec(joint, DotBody.CHEST), neck = vec(joint, DotBody.NECK), pelvis = vec(joint, DotBody.PELVIS);
        float r = skeleton.female ? 0.26f : 0.3f;
        return capsuleDist(w, pelvis, chestA) < r * 0.85f || capsuleDist(w, chestA, neck) < r
                || dist(w, vec(joint, DotBody.HEAD)) < 0.2f;
    }

    // ------------------------------------------------------------ validation

    /** Problems with the current pose: broken bone lengths, non-finite values, a hand left inside the body. */
    public java.util.List<String> validate() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (int b = 0; b < N; b++) {
            int p = Skeleton.PARENT[b];
            for (int k = 0; k < 3; k++) {
                if (!Float.isFinite(joint[b * 3 + k])) {
                    out.add("non-finite joint " + Skeleton.NAMES[b]);
                    return out;
                }
            }
            if (p >= 0) {
                float bl = dist(vec(bind, b), vec(bind, p)), pl = dist(vec(joint, b), vec(joint, p));
                if (Math.abs(bl - pl) > 1e-3f) {
                    out.add("bone " + Skeleton.NAMES[b] + " changed length (" + bl + " → " + pl + ")");
                }
            }
        }
        float[] check = shown.clone();
        if (DotBody.limit(check) > 0) {
            out.add("pose outside joint limits");
        }
        if (handInside(DotBody.HAND_L) || handInside(DotBody.HAND_R)) {
            out.add("a hand is inside the body");
        }
        return out;
    }

    // ------------------------------------------------------------ math

    static void axisAngle(float[] axis, float ang, float[] m) {
        float l = (float) Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        float x = axis[0] / l, y = axis[1] / l, z = axis[2] / l;
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang), t = 1f - c;
        m[0] = t * x * x + c;
        m[1] = t * x * y - s * z;
        m[2] = t * x * z + s * y;
        m[3] = t * x * y + s * z;
        m[4] = t * y * y + c;
        m[5] = t * y * z - s * x;
        m[6] = t * x * z - s * y;
        m[7] = t * y * z + s * x;
        m[8] = t * z * z + c;
    }

    private static float[] transpose(float[] m, int o) {
        return new float[]{m[o], m[o + 3], m[o + 6], m[o + 1], m[o + 4], m[o + 7], m[o + 2], m[o + 5], m[o + 8]};
    }

    static float[] vec(float[] a, int b) {
        return new float[]{a[b * 3], a[b * 3 + 1], a[b * 3 + 2]};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float len(float[] a) {
        return (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    private static float[] norm(float[] a) {
        float l = len(a);
        return l < 1e-9f ? new float[]{0f, 0f, 0f} : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static float dist(float[] a, float[] b) {
        return len(sub(a, b));
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float capsuleDist(float[] p, float[] a, float[] b) {
        float[] ab = sub(b, a), ap = sub(p, a);
        float t = clamp((ap[0] * ab[0] + ap[1] * ab[1] + ap[2] * ab[2]) / Math.max(1e-9f, ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2]), 0f, 1f);
        return dist(p, new float[]{a[0] + ab[0] * t, a[1] + ab[1] * t, a[2] + ab[2] * t});
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
