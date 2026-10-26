package ai.dvine.human.rig;

import ai.dvine.face.nano.DotBody;

/**
 * The human skeleton: the 17 body bones the pose library drives (pelvis,
 * spine, chest, neck, head, both arms and legs) plus two segments for each
 * finger and thumb. Bind positions are in body units (feet at y = -4.2,
 * crown near +1), arms resting in an A-pose 40° below level. Persona's left
 * is +x.
 */
public final class Skeleton {
    public static final int BODY_BONES = DotBody.BONES;
    /** Finger order per hand: thumb, index, middle, ring, little; two segments each. */
    public static final String[] FINGERS = {"thumb", "index", "middle", "ring", "little"};
    public static final int FINGER_L = BODY_BONES;          // 17..26
    public static final int FINGER_R = BODY_BONES + 10;     // 27..36
    public static final int COUNT = BODY_BONES + 20;

    public static final String[] NAMES = new String[COUNT];
    public static final int[] PARENT = new int[COUNT];

    static {
        String[] body = {"pelvis", "spine", "chest", "neck", "head", "upper arm L", "forearm L", "hand L",
                "upper arm R", "forearm R", "hand R", "thigh L", "shin L", "foot L", "thigh R", "shin R", "foot R"};
        for (int b = 0; b < BODY_BONES; b++) {
            NAMES[b] = body[b];
            PARENT[b] = DotBody.PARENT[b];
        }
        for (int side = 0; side < 2; side++) {
            int base = side == 0 ? FINGER_L : FINGER_R;
            int hand = side == 0 ? DotBody.HAND_L : DotBody.HAND_R;
            for (int f = 0; f < 5; f++) {
                NAMES[base + f * 2] = FINGERS[f] + " 1 " + (side == 0 ? "L" : "R");
                NAMES[base + f * 2 + 1] = FINGERS[f] + " 2 " + (side == 0 ? "L" : "R");
                PARENT[base + f * 2] = hand;
                PARENT[base + f * 2 + 1] = base + f * 2;
            }
        }
    }

    /** Arm-local layout (x along the arm from the shoulder, y up, z forward), shared by sculpt and rig. */
    public static final float WRIST = 1.52f;
    public static final float KNUCKLE = 1.76f;
    /** Finger knuckle z offsets (thumb unused here), lengths and the split between the two segments. */
    public static final float[] FINGER_Z = {0f, 0.056f, 0.019f, -0.019f, -0.054f};
    public static final float[] FINGER_LEN = {0.2f, 0.21f, 0.245f, 0.225f, 0.17f};
    public static final float[] FINGER_R0 = {0.026f, 0.021f, 0.022f, 0.021f, 0.018f};
    /** How far the fingers fan out at the middle joint and at the tip (multiples of FINGER_Z), so they stay separate. */
    public static final float SPREAD_MID = 1.15f, SPREAD_TIP = 1.25f;
    public static final float SPLIT = 0.48f;
    /** Thumb base and direction in arm-local coordinates. */
    public static final float[] THUMB_BASE = {1.6f, -0.025f, 0.06f};
    public static final float[] THUMB_DIR = {0.62f, -0.18f, 0.76f};

    public final boolean female;
    /** Shoulder half-width, shoulder joint height and hip half-width. */
    public final float sh;
    public final float shy;
    public final float hx;
    /** Bind joint positions, xyz per bone. */
    public final float[] joint = new float[COUNT * 3];

    /** A skeleton with the given bind joints (an asset's, fitted to the body-dimension profile). */
    public Skeleton(boolean female, float[] joints) {
        this.female = female;
        sh = Math.abs(joints[DotBody.ARM_L * 3]);
        shy = joints[DotBody.ARM_L * 3 + 1];
        hx = Math.abs(joints[DotBody.THIGH_L * 3]);
        System.arraycopy(joints, 0, joint, 0, COUNT * 3);
    }

    /**
     * The blockout layout the sculpts are authored in. Built assets carry
     * joints fitted to {@link BodyProfile}; rig an asset with those.
     */
    public Skeleton(boolean female) {
        this.female = female;
        // shoulder and hip joints where the standard body (StandardBody) has them; the shoulder width is
        // trimmed toward the body-dimension profile (Obi with his athletic character factor)
        sh = female ? 0.444f : 0.528f;
        shy = female ? -0.18f : -0.2f;
        hx = female ? 0.256f : 0.344f;
        set(DotBody.PELVIS, 0f, -1.5f, 0f);
        set(DotBody.SPINE, 0f, -1.15f, 0f);
        set(DotBody.CHEST, 0f, -0.6f, 0f);
        set(DotBody.NECK, 0f, 0.1f, -0.02f);
        set(DotBody.HEAD, 0f, 0.33f, 0f);
        for (int s = -1; s <= 1; s += 2) {
            int o = s > 0 ? 0 : 3;
            setArm(DotBody.ARM_L + o, s, 0f, 0f, 0f);
            setArm(DotBody.FORE_L + o, s, 0.82f, 0f, 0f);
            setArm(DotBody.HAND_L + o, s, WRIST, 0f, 0f);
            set(DotBody.THIGH_L + o, s * hx, -1.62f, 0f);
            set(DotBody.SHIN_L + o, s * (hx + 0.02f), -2.88f, 0.03f);
            set(DotBody.FOOT_L + o, s * (hx + 0.03f), -4.0f, -0.02f);
            int base = s > 0 ? FINGER_L : FINGER_R;
            // thumb: base and middle
            float tl = FINGER_LEN[0];
            setArm(base, s, THUMB_BASE[0], THUMB_BASE[1], THUMB_BASE[2]);
            setArm(base + 1, s, THUMB_BASE[0] + THUMB_DIR[0] * tl * SPLIT, THUMB_BASE[1] + THUMB_DIR[1] * tl * SPLIT,
                    THUMB_BASE[2] + THUMB_DIR[2] * tl * SPLIT);
            for (int f = 1; f < 5; f++) {
                setArm(base + f * 2, s, KNUCKLE, -0.012f, FINGER_Z[f]);
                setArm(base + f * 2 + 1, s, KNUCKLE + FINGER_LEN[f] * SPLIT, -0.016f, FINGER_Z[f] * SPREAD_MID);
            }
        }
    }

    private void set(int b, float x, float y, float z) {
        joint[b * 3] = x;
        joint[b * 3 + 1] = y;
        joint[b * 3 + 2] = z;
    }

    private void setArm(int b, int side, float x, float y, float z) {
        float[] p = arm(side, x, y, z);
        set(b, p[0], p[1], p[2]);
    }

    /** Arm-local → body bind coordinates for one side, in the A-pose, the shoulder joint at (±sh, shy). */
    public static float[] armToBody(int side, float sh, float shy, float x, float y, float z) {
        float c = (float) Math.cos(Math.toRadians(DotBody.BIND_ARM)), s = (float) Math.sin(Math.toRadians(DotBody.BIND_ARM));
        return new float[]{side * (sh + x * c + y * s), shy - x * s + y * c, z};
    }

    /** Arm-local → body bind coordinates on this skeleton. */
    public float[] arm(int side, float x, float y, float z) {
        return armToBody(side, sh, shy, x, y, z);
    }

    /** Body → arm-local coordinates on this skeleton (the inverse of {@link #arm}); {@code side} picks the arm. */
    public float[] local(int side, float x, float y, float z) {
        float c = (float) Math.cos(Math.toRadians(DotBody.BIND_ARM)), s = (float) Math.sin(Math.toRadians(DotBody.BIND_ARM));
        float u = side * x - sh, w = y - shy;
        return new float[]{u * c - w * s, u * s + w * c, z};
    }

    /** True for bones on the persona's right side (stored mirrored in the pose library). */
    public static boolean rightSide(int b) {
        return b >= DotBody.ARM_R && b <= DotBody.HAND_R || b >= DotBody.THIGH_R && b < BODY_BONES || b >= FINGER_R;
    }

    public static boolean isFinger(int b) {
        return b >= FINGER_L;
    }
}
