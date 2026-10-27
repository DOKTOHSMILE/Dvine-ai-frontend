package ai.dvine.motion;

import java.util.Locale;

/**
 * The movement engine: a 17-bone body (hips, spine, chest, neck, head, two arms, forearms, hands, thighs, shins and
 * feet), the poses and moves it knows (standing, waving, walking, running, dancing, push-ups, cartwheels, flips and
 * more, each as joint angles over time) and the joint limits no move may break. It has no look of its own: it is
 * kept to move a body the user has DVINE design in 3D. Pure Java.
 */
public final class Motions {
    private Motions() {
    }

    public static final int PELVIS = 0, SPINE = 1, CHEST = 2, NECK = 3, HEAD = 4;
    public static final int ARM_L = 5, FORE_L = 6, HAND_L = 7, ARM_R = 8, FORE_R = 9, HAND_R = 10;
    public static final int THIGH_L = 11, SHIN_L = 12, FOOT_L = 13, THIGH_R = 14, SHIN_R = 15, FOOT_R = 16;
    public static final int BONES = 17;
    public static final int[] PARENT = {-1, 0, 1, 2, 3, 2, 5, 6, 2, 8, 9, 0, 11, 12, 0, 14, 15};

    public static final String[] POSES = {"stand", "tpose", "arms up", "wave", "point", "squat", "sit", "kneel",
            "run", "walk", "bow", "dance", "handstand",
            // exercises and stunts (they loop until another pose is asked for)
            "pushups", "jumping jacks", "squats", "lunges", "plank", "stretch", "situps", "boxing", "kick",
            "cartwheel", "backflip", "spin", "jump", "roll", "lie",
            // the body talks with its hands while the persona explains
            "talk"};
    /** Extra pose channels after the bone angles: sideways travel and lift (body units). */
    public static final int TRAVEL = BONES * 3, LIFT = BONES * 3 + 1, CHANNELS = BONES * 3 + 2;

    /** Which pose a spoken request names ("do a t-pose", "squat", "sit down"…), or null. */
    public static String poseFor(String lower) {
        String t = lower.toLowerCase(Locale.US);
        if (t.matches(".*\\bpush[- ]?ups?\\b.*") || t.contains("press up") || t.contains("press-up")) return "pushups";
        if (t.contains("jumping jack") || t.contains("star jump")) return "jumping jacks";
        if (t.matches(".*\\b(sit[- ]?ups?|crunch(es)?)\\b.*")) return "situps";
        if (t.matches(".*\\bsquats\\b.*")) return "squats";
        if (t.contains("lunge")) return "lunges";
        if (t.contains("plank")) return "plank";
        if (t.contains("stretch")) return "stretch";
        if (t.contains("cartwheel")) return "cartwheel";
        if (t.matches(".*\\broll(ing)?\\b.*")) return "roll";
        if (t.matches(".*\\b(lie|lay|laying|lying)( down| on (your|the) back| on the floor)?\\b.*")) return "lie";
        if (t.matches(".*\\b(jump(ing)?|hop|leap)\\b.*") && !t.contains("jumping jack")) return "jump";
        if (t.contains("backflip") || t.contains("back flip") || t.matches(".*\\bflip\\b.*") || t.contains("somersault")) return "backflip";
        if (t.contains("spin") || t.contains("pirouette")) return "spin";
        if (t.matches(".*\\b(box|boxing|punch(es)?|fight)\\b.*")) return "boxing";
        if (t.contains("kick")) return "kick";
        if (t.contains("t-pose") || t.contains("t pose") || t.contains("arms out") || t.contains("spread your arms")) return "tpose";
        if (t.contains("handstand") || t.contains("upside down")) return "handstand";
        if (t.contains("raise your hands") || t.contains("raise your arms") || t.contains("hands up") || t.contains("arms up")) return "arms up";
        if (t.contains("wave")) return "wave";
        if (t.contains("point")) return "point";
        if (t.contains("squat") || t.contains("crouch")) return "squat";
        if (t.contains("sit") || t.contains("meditat") || t.contains("cross leg")) return "sit";
        if (t.contains("kneel")) return "kneel";
        if (t.contains("run") || t.contains("jog")) return "run";
        if (t.contains("walk")) return "walk";
        if (t.contains("bow")) return "bow";
        if (t.matches(".*\\bdanc(e|ing)\\b.*")) return "dance";
        if (t.contains("stand") || t.contains("relax") || t.contains("normal pose")) return "stand";
        return null;
    }

    /**
     * Turns a rotation written for a level arm into one for the A-pose rig:
     * the upper arm gets R * Rz(+bind) (it first lifts to level), the forearm
     * and hand get Rz(-bind) * R * Rz(+bind) (the same hinge, seen from the rig).
     */
    public static void fromLevel(float[] m, float bindDeg, boolean upper) {
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

    public static void mul(float[] a, float[] b, float[] out) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
    }

    /** Rotation Ry * Rx * Rz from degrees, row-major. */
    public static void euler(float xd, float yd, float zd, float[] m) {
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
    public static void poseAngles(String name, float t, float[] a) {
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
                    set(a, THIGH_L + s * 3, -10f * amp - 40f * amp * sin, 0f, 2f, right); // back leg ends at the 30° hip extension limit
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
            case "jump": { // crouch, leap with the arms swinging up, land soft
                float f = (t % 1.6f) / 1.6f;
                if (f < 0.3f) {
                    float u = f / 0.3f;
                    u = u * u * (3f - 2f * u);
                    both(a, THIGH_L, -55f * u, 0f, 3f);
                    both(a, SHIN_L, 80f * u, 0f, 0f);
                    a[CHEST * 3] = 20f * u;
                    both(a, ARM_L, 0f, 40f * u, -72f);
                } else if (f < 0.75f) {
                    float s = (float) Math.sin((f - 0.3f) / 0.45f * Math.PI);
                    a[LIFT] = 0.9f * s;
                    both(a, THIGH_L, -20f * s, 0f, 3f);
                    both(a, SHIN_L, 30f * s, 0f, 0f);
                    both(a, ARM_L, 0f, -20f * s, -72f + 150f * s);
                    both(a, FOOT_L, 25f * s, 0f, 0f);
                } else {
                    float u = 1f - (f - 0.75f) / 0.25f;
                    both(a, THIGH_L, -45f * u, 0f, 3f);
                    both(a, SHIN_L, 65f * u, 0f, 0f);
                    a[CHEST * 3] = 15f * u;
                    both(a, ARM_L, 0f, 15f * u, -72f);
                }
                break;
            }
            case "roll": { // roll over on the floor, arms stretched past the head, along the body's length
                a[PELVIS * 3 + 2] = -90f;
                a[PELVIS * 3] = -360f * ((t % 2.6f) / 2.6f);
                both(a, ARM_L, 0f, 0f, 82f);
                both(a, THIGH_L, 0f, 0f, 2f);
                both(a, FOOT_L, 30f, 0f, 0f);
                break;
            }
            case "lie": // on the back, face up, one knee raised, breathing
                a[PELVIS * 3 + 2] = -90f;
                a[PELVIS * 3] = -90f;
                both(a, ARM_L, 0f, 0f, -70f);
                set(a, THIGH_L, -40f, 0f, 2f, false);
                set(a, SHIN_L, 75f, 0f, 0f, false);
                a[CHEST * 3] = 2f * breathe;
                break;
            case "handstand":
                a[PELVIS * 3 + 2] = 180f;
                both(a, ARM_L, 0f, 0f, 84f);
                both(a, THIGH_L, 0f, 0f, 6f + 2f * breathe);
                break;
            case "pushups":
            case "plank": {
                float d = name.equals("plank") ? 0f : wave(t, 2.2f);
                float tilt = 12f - 9f * d;
                a[PELVIS * 3] = 90f - tilt;
                a[PELVIS * 3 + 1] = 90f; // lie sideways to the viewer, head to the right
                both(a, ARM_L, 0f, -90f, tilt - 55f * d);
                both(a, FORE_L, 0f, 0f, 55f * d);
                both(a, FOOT_L, -35f, 0f, 0f);
                a[NECK * 3] = -tilt - 10f;
                break;
            }
            case "situps": {
                float u = wave(t, 2.6f);
                a[PELVIS * 3] = -90f;
                a[PELVIS * 3 + 1] = 90f;
                both(a, THIGH_L, -50f, 0f, 3f);
                both(a, SHIN_L, 95f, 0f, 0f);
                a[SPINE * 3] = 30f * u;
                a[CHEST * 3] = 55f * u;
                a[NECK * 3] = 10f + 15f * u;
                both(a, ARM_L, 0f, -80f, -20f + 10f * u);
                break;
            }
            case "jumping jacks": {
                float u = wave(t, 1.1f);
                both(a, ARM_L, 0f, 0f, -75f + 155f * u);
                both(a, FORE_L, 0f, 0f, 8f * u);
                both(a, THIGH_L, 0f, 0f, 2f + 16f * u);
                a[LIFT] = 0.22f * Math.abs((float) Math.sin(t * Math.PI * 2 / 1.1f));
                break;
            }
            case "squats": {
                float[] up = new float[CHANNELS];
                poseAngles("stand", t, up);
                poseAngles("squat", t, a);
                both(a, ARM_L, 0f, -80f, -8f); // arms forward for balance
                float u = wave(t, 2.4f);
                for (int i = 0; i < CHANNELS; i++) {
                    a[i] = up[i] + (a[i] - up[i]) * u;
                }
                break;
            }
            case "lunges": {
                float u = wave(t, 3f);
                boolean right = ((int) (t / 3f)) % 2 == 1;
                int front = right ? THIGH_R : THIGH_L, back = right ? THIGH_L : THIGH_R;
                set(a, front, -82f * u, 0f, 3f, right);
                set(a, front + 1, 88f * u, 0f, 0f, right);
                set(a, back, 18f * u, 0f, 3f, !right);
                set(a, back + 1, 95f * u, 0f, 0f, !right);
                set(a, back + 2, 35f * u, 0f, 0f, !right);
                both(a, ARM_L, 0f, -25f, -55f);
                both(a, FORE_L, 0f, -105f, 0f); // hands on hips
                break;
            }
            case "stretch": {
                float sway = (float) Math.sin(t * 1.1f);
                both(a, ARM_L, 0f, 0f, 82f);
                both(a, FORE_L, 0f, 0f, 4f);
                a[SPINE * 3 + 2] = 9f * sway;
                a[CHEST * 3 + 2] = 20f * sway;
                a[NECK * 3 + 2] = 8f * sway;
                break;
            }
            case "boxing": {
                float hop = Math.abs((float) Math.sin(t * 5f));
                both(a, THIGH_L, -14f, 0f, 6f);
                both(a, SHIN_L, 26f, 0f, 0f);
                both(a, ARM_L, 0f, -62f, -55f);
                both(a, FORE_L, 0f, -118f, 0f); // fists up, guarding the face
                int k = (int) (t / 0.6f);
                float jab = Math.max(0f, (float) Math.sin((t % 0.6f) / 0.6f * Math.PI));
                boolean right = k % 2 == 1;
                set(a, right ? ARM_R : ARM_L, 0f, -62f - 26f * jab, -55f + 52f * jab, right);
                set(a, right ? FORE_R : FORE_L, 0f, -118f * (1f - jab), 0f, right);
                a[CHEST * 3 + 1] = (right ? 14f : -14f) * jab;
                a[LIFT] = 0.05f * hop;
                break;
            }
            case "kick": {
                float k = Math.max(0f, (float) Math.sin((t % 2f) / 2f * Math.PI * 2));
                both(a, ARM_L, 0f, -62f, -55f);
                both(a, FORE_L, 0f, -118f, 0f);
                set(a, THIGH_R, -95f * k, 0f, 4f, true);
                set(a, SHIN_R, 95f * (1f - k) * (k > 0.05f ? 1f : 0f), 0f, 0f, true);
                set(a, FOOT_R, -30f * k, 0f, 0f, true);
                a[CHEST * 3] = -12f * k;
                break;
            }
            case "cartwheel": {
                float f = (t % 2.4f) / 2.4f;
                float dir = ((int) (t / 2.4f)) % 2 == 0 ? 1f : -1f;
                a[PELVIS * 3 + 2] = -360f * f * dir;
                both(a, ARM_L, 0f, 0f, 80f);
                both(a, THIGH_L, 0f, 0f, 30f);
                a[TRAVEL] = dir * (-1.1f + 2.2f * f);
                break;
            }
            case "backflip": {
                float f = (t % 2.8f) / 2.8f;
                if (f < 0.25f) { // crouch and swing the arms back
                    float u = f / 0.25f;
                    both(a, THIGH_L, -70f * u, 0f, 4f);
                    both(a, SHIN_L, 95f * u, 0f, 0f);
                    a[CHEST * 3] = 25f * u;
                    both(a, ARM_L, 0f, 35f * u, -70f);
                } else if (f < 0.75f) { // in the air, tucked, turning over backwards
                    float u = (f - 0.25f) / 0.5f;
                    float tuck = (float) Math.sin(u * Math.PI);
                    a[PELVIS * 3] = -360f * u;
                    both(a, THIGH_L, -115f * tuck, 0f, 4f);
                    both(a, SHIN_L, 125f * tuck, 0f, 0f);
                    both(a, ARM_L, 0f, -40f * tuck, -70f + 150f * (1f - tuck));
                    a[LIFT] = 1.1f * tuck;
                } else { // land soft, knees bent, then stand tall
                    float u = 1f - (f - 0.75f) / 0.25f;
                    both(a, THIGH_L, -60f * u, 0f, 4f);
                    both(a, SHIN_L, 85f * u, 0f, 0f);
                    a[CHEST * 3] = 18f * u;
                    both(a, ARM_L, 0f, -60f * u, -70f + 60f * u);
                }
                break;
            }
            case "spin":
                a[PELVIS * 3 + 1] = (t * 300f) % 360f;
                both(a, ARM_L, 0f, -20f, 70f);
                both(a, FORE_L, 0f, 0f, 30f);
                set(a, THIGH_L, -35f, 0f, 30f, false);
                set(a, SHIN_L, 85f, 0f, 0f, false);
                break;
            case "talk": {
                // conversational gestures: hands open and turn, a shrug, a nod, a little weight shift
                float l = (float) Math.sin(t * 1.7f), r = (float) Math.sin(t * 1.3f + 1.5f);
                set(a, ARM_L, 0f, -18f - 12f * l, -68f + 9f * l, false);
                set(a, FORE_L, 0f, -62f - 28f * l, 0f, false);
                set(a, HAND_L, 0f, 0f, 18f * l, false);
                set(a, ARM_R, 0f, -18f - 12f * r, -68f + 9f * r, true);
                set(a, FORE_R, 0f, -62f - 28f * r, 0f, true);
                set(a, HAND_R, 0f, 0f, 18f * r, true);
                a[CHEST * 3 + 1] = 6f * (float) Math.sin(t * 0.9f);
                a[NECK * 3] = 4f * (float) Math.sin(t * 2.1f);
                a[PELVIS * 3 + 2] = 1.5f * (float) Math.sin(t * 0.6f);
                both(a, THIGH_L, 0f, 0f, 1.5f);
                break;
            }
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

    /** 0 → 1 → 0 once per period, smooth at both ends. */
    private static float wave(float t, float period) {
        return 0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2 / period);
    }

    /**
     * Joint limits (degrees, left-side convention: x min, x max, y min, y max,
     * z min, z max) so no request can bend a knee backwards, twist a neck
     * round or fold an elbow the wrong way. The hips (root) turn freely so
     * the whole body can flip, lie down or cartwheel. Arms use the level-arm
     * frame the poses are written in.
     */
    static final float[][] LIMITS = new float[BONES][];

    static {
        LIMITS[SPINE] = new float[]{-30f, 60f, -40f, 40f, -35f, 35f};
        LIMITS[CHEST] = new float[]{-30f, 70f, -45f, 45f, -35f, 35f};
        LIMITS[NECK] = new float[]{-45f, 50f, -75f, 75f, -40f, 40f};
        LIMITS[HEAD] = new float[]{-45f, 50f, -75f, 75f, -40f, 40f};
        for (int o = 0; o <= 3; o += 3) {
            LIMITS[ARM_L + o] = new float[]{-100f, 100f, -135f, 70f, -90f, 185f};
            LIMITS[FORE_L + o] = new float[]{-30f, 30f, -155f, 10f, -20f, 160f};
            LIMITS[HAND_L + o] = new float[]{-80f, 80f, -45f, 45f, -70f, 70f};
            LIMITS[THIGH_L + o] = new float[]{-130f, 45f, -60f, 75f, -35f, 75f};
            LIMITS[SHIN_L + o] = new float[]{-5f, 160f, -15f, 15f, -130f, 15f};
            LIMITS[FOOT_L + o] = new float[]{-60f, 60f, -30f, 30f, -35f, 35f};
        }
    }

    /** Clamps every joint into its limits. Returns how many angles had to move. */
    public static int limit(float[] a) {
        int moved = 0;
        for (int b = 0; b < BONES; b++) {
            float[] l = LIMITS[b];
            if (l == null) {
                continue;
            }
            boolean right = b >= ARM_R && b <= HAND_R || b >= THIGH_R;
            for (int k = 0; k < 3; k++) {
                float sign = right && k > 0 ? -1f : 1f; // right side stores y and z mirrored
                float v = a[b * 3 + k] * sign;
                float c = Math.max(l[k * 2], Math.min(l[k * 2 + 1], v));
                if (c != v) {
                    moved++;
                    a[b * 3 + k] = c * sign;
                }
            }
        }
        return moved;
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
}
