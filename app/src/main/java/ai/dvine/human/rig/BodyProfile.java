package ai.dvine.human.rig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.dvine.brain.MiniJson;
import ai.dvine.face.nano.DotBody;

/**
 * The authoritative body-dimension profile (ANRE_BODY_DIMENSIONS_V1, shipped
 * as the resource {@code ai/dvine/human/body_dimensions.json}): global ratios,
 * normalised landmarks (floor 0, crown 1), blockout parts and joint limits.
 * Every body measurement the builder fits to comes from here; nothing else
 * carries joint heights.
 *
 * Profile space is metres for a nominal 1.8 m adult, Y up, front toward −Z,
 * anatomical left at −X. Body space (the meshes and the rig) has the floor at
 * {@link #FLOOR}, front toward +Z and the persona's left at +X, so both X and
 * Z flip: {@code x_body = −x·H}, {@code y_body = FLOOR + y·H},
 * {@code z_body = −z·H}, with H the body's height in body units. One unit is
 * {@code heightM / H} metres.
 *
 * The profile is an art/rigging baseline, not clinical anthropometry. Where
 * it contradicts itself, {@link #consistency()} says so and {@link #applied}
 * names the value the body uses instead and why.
 */
public final class BodyProfile {
    public static final String SCHEMA = "ANRE_BODY_DIMENSIONS_V1";
    public static final String RESOURCE = "/ai/dvine/human/body_dimensions.json";
    /** Where the soles stand in body units. */
    public static final float FLOOR = -4.2f;
    /** The smallest gap between the shoulder joint and the chin a natural neck keeps (fraction of height). */
    public static final float MIN_NECK_GAP = 0.045f;

    public final float heightM;
    public final float headRatio, shoulderRatio, hipRatio, spanRatio, legRatio;
    /** Normalised landmark positions (profile axes), by id. */
    public final Map<String, float[]> landmarks = new LinkedHashMap<String, float[]>();
    /** Blockout part centres (or limb start/end), normalised profile axes, by id. */
    public final Map<String, float[]> parts = new LinkedHashMap<String, float[]>();
    /** Joint limits in degrees: id → {min, max}. */
    public final Map<String, float[]> limits = new LinkedHashMap<String, float[]>();
    /** The landmark heights the body is fitted to (normalised), after resolving contradictions. */
    public final Map<String, Float> applied = new LinkedHashMap<String, Float>();
    /** Why an applied value differs from the profile, by landmark id. */
    public final Map<String, String> deviations = new LinkedHashMap<String, String>();

    private static BodyProfile standard;

    private BodyProfile(Map<String, Object> root) {
        if (!SCHEMA.equals(MiniJson.str(root.get("schema"), ""))) {
            throw new IllegalArgumentException("not an " + SCHEMA + " profile");
        }
        if (!"meters".equals(MiniJson.str(root.get("units"), ""))) {
            throw new IllegalArgumentException("profile units must be meters");
        }
        Map<String, Object> g = MiniJson.obj(root.get("global_calculations"));
        heightM = positive(g, "height_m");
        headRatio = positive(g, "head_height_ratio");
        shoulderRatio = positive(g, "shoulder_width_ratio");
        hipRatio = positive(g, "hip_width_ratio");
        spanRatio = positive(g, "arm_span_ratio");
        legRatio = positive(g, "leg_length_ratio");
        for (Object o : MiniJson.list(root.get("landmarks"))) {
            Map<String, Object> l = MiniJson.obj(o);
            landmarks.put(MiniJson.str(l.get("id"), "?"), vec(l.get("position")));
        }
        for (Object o : MiniJson.list(root.get("parts"))) {
            Map<String, Object> p = MiniJson.obj(o);
            String id = MiniJson.str(p.get("id"), "?");
            if (p.containsKey("center")) {
                parts.put(id, vec(p.get("center")));
            } else {
                parts.put(id + ".start", vec(p.get("start")));
                parts.put(id + ".end", vec(p.get("end")));
            }
        }
        for (Object o : MiniJson.list(root.get("joint_limits_degrees"))) {
            Map<String, Object> j = MiniJson.obj(o);
            float lo = (float) MiniJson.num(j.get("min"), Double.NaN), hi = (float) MiniJson.num(j.get("max"), Double.NaN);
            if (!(lo <= hi)) {
                throw new IllegalArgumentException("bad joint limit " + j.get("joint"));
            }
            limits.put(MiniJson.str(j.get("joint"), "?"), new float[]{lo, hi});
        }
        for (String id : new String[]{"ankle_center", "knee_center", "hip_joint_center", "pelvis_center", "navel",
                "sternum_center", "shoulder_joint_center", "crown"}) {
            applied.put(id, y(id));
        }
        // the profile's chin (0.942) contradicts its own head ratio (crown − chin = 0.135 puts the chin at 0.865);
        // the head keeps the ratio, so the shoulder may not rise into the neck
        float chin = y("crown") - headRatio;
        float shoulder = y("shoulder_joint_center");
        if (chin - shoulder < MIN_NECK_GAP) {
            float use = chin - MIN_NECK_GAP;
            applied.put("shoulder_joint_center", use);
            deviations.put("shoulder_joint_center", String.format(Locale.US,
                    "%.3f leaves %.3f between the shoulder joint and the chin the head ratio implies (%.3f); using %.3f",
                    shoulder, chin - shoulder, chin, use));
        }
    }

    /** Parses a profile; throws IllegalArgumentException when it is malformed. */
    public static BodyProfile parse(String json) {
        return new BodyProfile(MiniJson.obj(MiniJson.parse(json)));
    }

    /** The shipped profile. */
    public static synchronized BodyProfile standard() {
        if (standard == null) {
            InputStream in = BodyProfile.class.getResourceAsStream(RESOURCE);
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                }
                standard = parse(out.toString("UTF-8"));
            } catch (IOException e) {
                throw new IllegalStateException("unreadable " + RESOURCE, e);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        return standard;
    }

    /**
     * The character profile over the neutral baseline (the profile allows
     * per-character customisation): Obi keeps the athletic V-taper of the
     * reference, about 10 % broader across the shoulders; Ivie's frame is
     * slightly narrower than the neutral one. Hips, span and joint heights
     * are the baseline for both.
     */
    public static float shoulderFactor(boolean female) {
        return female ? 0.98f : 1.1f;
    }

    /** A landmark's normalised height. */
    public float y(String landmark) {
        float[] p = landmarks.get(landmark);
        if (p == null) {
            throw new IllegalArgumentException("no landmark " + landmark);
        }
        return p[1];
    }

    /** The applied (fitted) normalised height of a landmark. */
    public float fit(String landmark) {
        Float v = applied.get(landmark);
        return v != null ? v : y(landmark);
    }

    /** Profile point (normalised) → body units for a body {@code heightUnits} tall. */
    public static float[] toBody(float[] p, float heightUnits) {
        return new float[]{-p[0] * heightUnits, FLOOR + p[1] * heightUnits, -p[2] * heightUnits};
    }

    /** {@code dimension_m = normalised · height_m}. */
    public float metres(float normalised) {
        if (Float.isNaN(normalised) || Float.isInfinite(normalised)) {
            throw new IllegalArgumentException("dimension must be finite");
        }
        return normalised * heightM;
    }

    /** A tapered limb's radius a fraction {@code t} along it, clamped to the limb. */
    public static float radiusAt(float proximal, float distal, float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return proximal + (distal - proximal) * c;
    }

    /**
     * Everything in the profile that disagrees with itself by more than 2 %
     * (or a left/right pair that is not mirrored): the stated metres against
     * ratio × height, the landmarks against the ratios, the landmarks against
     * the parts. Empty when the profile is consistent.
     */
    public List<String> consistency() {
        List<String> out = new ArrayList<String>();
        float chinGap = y("crown") - y("chin");
        compare(out, "head ratio vs crown − chin landmarks", headRatio, chinGap);
        compare(out, "leg ratio vs hip joint − ankle landmarks", legRatio, y("hip_joint_center") - y("ankle_center"));
        if (parts.containsKey("left_knee")) {
            compare(out, "knee landmark vs knee part", y("knee_center"), parts.get("left_knee")[1]);
        }
        if (parts.containsKey("left_ankle")) {
            compare(out, "ankle landmark vs ankle part", y("ankle_center"), parts.get("left_ankle")[1]);
        }
        if (parts.containsKey("left_upper_arm.start")) {
            compare(out, "shoulder landmark vs upper arm start", y("shoulder_joint_center"), parts.get("left_upper_arm.start")[1]);
        }
        if (y("eye_line") > y("crown") - headRatio * 0.3f) {
            out.add(String.format(Locale.US, "eye line %.3f sits %.3f below the crown; a head %.3f tall puts it near %.3f",
                    y("eye_line"), y("crown") - y("eye_line"), headRatio, y("crown") - headRatio * 0.5f));
        }
        for (Map.Entry<String, float[]> e : parts.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("left_")) {
                float[] r = parts.get("right_" + k.substring(5));
                float[] l = e.getValue();
                if (r == null || Math.abs(l[0] + r[0]) > 1e-4f || Math.abs(l[1] - r[1]) > 1e-4f || Math.abs(l[2] - r[2]) > 1e-4f) {
                    out.add(k + " is not mirrored by its right side");
                }
            }
        }
        return out;
    }

    private static void compare(List<String> out, String what, float a, float b) {
        if (Math.abs(a - b) > 0.02f * Math.max(Math.abs(a), Math.abs(b))) {
            out.add(String.format(Locale.US, "%s: %.3f vs %.3f", what, a, b));
        }
    }

    /**
     * Writes the profile's joint limits into the rig's per-bone Euler limits
     * (degrees; x, y, z min/max per bone) where the rig's axes map onto the
     * profile's motions: neck flexion (x) and rotation (y), elbow flexion
     * (forearm −y), forearm pronation (hand x), wrist flexion (hand z), hip
     * flexion (thigh −x) and abduction (thigh z) and knee flexion (shin x).
     * Shoulder, ankle and wrist deviation are left to the rig: their axes do
     * not map one-to-one (see the handbook).
     */
    public void applyLimits(float[][] rig) {
        for (int head : new int[]{DotBody.NECK, DotBody.HEAD}) {
            set(rig[head], 0, limits.get("neck_flexion_extension"), false);
            set(rig[head], 1, limits.get("neck_rotation"), false);
        }
        for (int o = 0; o <= 3; o += 3) {
            set(rig[DotBody.FORE_L + o], 1, limits.get("elbow_flexion"), true);
            set(rig[DotBody.HAND_L + o], 0, limits.get("forearm_pronation_supination"), false);
            set(rig[DotBody.HAND_L + o], 2, limits.get("wrist_flexion_extension"), false);
            set(rig[DotBody.THIGH_L + o], 0, limits.get("hip_flexion_extension"), true);
            set(rig[DotBody.THIGH_L + o], 2, limits.get("hip_abduction_adduction"), false);
            set(rig[DotBody.SHIN_L + o], 0, limits.get("knee_flexion"), false);
        }
    }

    private static void set(float[] bone, int axis, float[] lim, boolean negative) {
        if (bone == null || lim == null) {
            return;
        }
        bone[axis * 2] = negative ? -lim[1] : lim[0];
        bone[axis * 2 + 1] = negative ? -lim[0] : lim[1];
    }

    private static float positive(Map<String, Object> m, String key) {
        float v = (float) MiniJson.num(m.get(key), Double.NaN);
        if (!(v > 0f) || Float.isInfinite(v)) {
            throw new IllegalArgumentException(key + " must be a positive number");
        }
        return v;
    }

    private static float[] vec(Object o) {
        List<Object> l = MiniJson.list(o);
        if (l == null || l.size() != 3) {
            throw new IllegalArgumentException("a position needs three numbers");
        }
        float[] v = new float[3];
        for (int i = 0; i < 3; i++) {
            v[i] = (float) MiniJson.num(l.get(i), Double.NaN);
            if (Float.isNaN(v[i]) || Float.isInfinite(v[i])) {
                throw new IllegalArgumentException("a position must be finite");
            }
        }
        return v;
    }
}
