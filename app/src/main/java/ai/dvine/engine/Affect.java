package ai.dvine.engine;

/**
 * A small appraisal and affect model (engineering variables, not feelings).
 * Events nudge valence, arousal, curiosity and frustration; everything drifts
 * back to a calm baseline; a regulation step keeps the resulting expression
 * small and proportionate. Criticism is read as feedback, never as hostility.
 */
public final class Affect {
    public float valence;
    public float arousal = 0.3f;
    public float curiosity = 0.2f;
    public float frustration;
    public float engagement = 0.4f;
    private long last = -1L;

    /** Appraises an event. Kinds: praise, criticism, question, unknown, learned, touch, exercise, stop. */
    public void appraise(String kind) {
        switch (kind) {
            case "praise":
                valence += 0.35f;
                engagement += 0.15f;
                break;
            case "criticism":
                valence -= 0.15f; // feedback: attentive and concerned, not hurt
                curiosity += 0.2f;
                break;
            case "question":
                curiosity += 0.15f;
                engagement += 0.05f;
                break;
            case "unknown":
                frustration += 0.25f;
                curiosity += 0.1f;
                break;
            case "learned":
                valence += 0.2f;
                frustration -= 0.3f;
                break;
            case "touch":
                arousal += 0.2f;
                engagement += 0.1f;
                break;
            case "exercise":
                arousal += 0.3f;
                valence += 0.1f;
                break;
            case "stop":
                arousal -= 0.2f;
                break;
            default:
                break;
        }
        clampAll();
    }

    /** Drifts back toward the baseline (half-life about 20 s). */
    public void decay(long now) {
        if (last < 0) {
            last = now;
            return;
        }
        float dt = Math.min(5f, (now - last) / 1000f);
        last = now;
        float k = (float) Math.exp(-dt * 0.035f);
        valence *= k;
        frustration *= k;
        arousal = 0.3f + (arousal - 0.3f) * k;
        curiosity = 0.2f + (curiosity - 0.2f) * k;
        engagement = 0.4f + (engagement - 0.4f) * k;
    }

    /** The regulated expression bias: {smile, brow, tilt in degrees}. */
    public float[] expression() {
        float smile = 0.18f * valence - 0.08f * frustration;
        float brow = 0.12f * curiosity + 0.08f * frustration - 0.05f * Math.max(0f, valence);
        float tilt = 6f * Math.max(0f, curiosity - 0.25f) + 3f * frustration;
        return new float[]{smile, brow, tilt};
    }

    /** One word for the state snapshot. */
    public String label() {
        if (frustration > 0.35f) {
            return "puzzled";
        }
        if (valence > 0.25f) {
            return "warm";
        }
        if (curiosity > 0.4f) {
            return "curious";
        }
        if (arousal > 0.55f) {
            return "energetic";
        }
        if (valence < -0.1f) {
            return "concerned";
        }
        return "calm";
    }

    private void clampAll() {
        valence = clamp(valence, -1f, 1f);
        arousal = clamp(arousal, 0f, 1f);
        curiosity = clamp(curiosity, 0f, 1f);
        frustration = clamp(frustration, 0f, 1f);
        engagement = clamp(engagement, 0f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
