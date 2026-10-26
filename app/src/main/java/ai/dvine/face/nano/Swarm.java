package ai.dvine.face.nano;

/**
 * Nano-bot particles. Each particle springs toward a target point that the
 * current {@link Form} supplies, so changing the form makes the swarm fly
 * apart and reassemble into the new shape (face, body, drawing, object).
 * Pure Java: no Android imports.
 */
public final class Swarm {
    public final int capacity;
    public final float[] pos;
    public final float[] vel;
    public final float[] target;
    /** Target brightness 0..1 (0 = parked, fades out). */
    public final float[] targetLight;
    /** Current brightness, eased toward the target. */
    public final float[] light;
    private int active;
    /** Brightness of particles the form does not use yet (0: they fade out; above 0: they glow as drifting dust). */
    public float parkedLight;
    private float omega = 9f;
    private float zeta = 0.82f;

    public Swarm(int capacity) {
        this.capacity = capacity;
        pos = new float[capacity * 3];
        vel = new float[capacity * 3];
        target = new float[capacity * 3];
        targetLight = new float[capacity];
        light = new float[capacity];
        for (int i = 0; i < capacity; i++) {
            // start scattered in a loose cloud so the first form assembles from nothing
            pos[i * 3] = noise(i * 3) * 4f;
            pos[i * 3 + 1] = noise(i * 3 + 1) * 4f;
            pos[i * 3 + 2] = noise(i * 3 + 2) * 4f;
        }
    }

    /** Points the current form uses; the rest are parked (dark). */
    public int active() {
        return active;
    }

    /** A shape the swarm can take. Writes xyz targets and 0..1 brightness; returns how many points. */
    public interface Form {
        int fill(float[] xyz, float[] lit, int max, float time);
    }

    /** Asks the form for this frame's targets. Unused particles drift and fade. */
    public void shape(Form form, float time) {
        int n = form == null ? 0 : Math.min(capacity, form.fill(target, targetLight, capacity, time));
        for (int i = n; i < capacity; i++) {
            targetLight[i] = parkedLight;
            // parked particles hover around where they are
            target[i * 3] = pos[i * 3];
            target[i * 3 + 1] = pos[i * 3 + 1] + 0.002f;
            target[i * 3 + 2] = pos[i * 3 + 2];
        }
        active = n;
    }

    /** Softness of the assembly: higher omega snaps faster. */
    public void setSpring(float omega, float zeta) {
        this.omega = omega;
        this.zeta = zeta;
    }

    public void step(float dt) {
        if (dt <= 0f) {
            return;
        }
        if (dt > 0.05f) {
            dt = 0.05f;
        }
        float k = omega * omega;
        float c = 2f * zeta * omega;
        for (int i = 0; i < capacity * 3; i++) {
            float a = k * (target[i] - pos[i]) - c * vel[i];
            vel[i] += a * dt;
            pos[i] += vel[i] * dt;
        }
        float ease = Math.min(1f, dt * 6f);
        for (int i = 0; i < capacity; i++) {
            light[i] += (targetLight[i] - light[i]) * ease;
        }
    }

    /** Moves every particle into another frame: p * scale + (0, dy, 0). Used when the camera zooms between face and body. */
    public void remap(float scale, float dy) {
        for (int i = 0; i < capacity; i++) {
            pos[i * 3] *= scale;
            pos[i * 3 + 1] = pos[i * 3 + 1] * scale + dy;
            pos[i * 3 + 2] *= scale;
            vel[i * 3] *= scale;
            vel[i * 3 + 1] *= scale;
            vel[i * 3 + 2] *= scale;
        }
    }

    /** Throws every particle outward; the springs bring them back into the form ("redraw yourself"). */
    public void scatter(float strength) {
        for (int i = 0; i < capacity; i++) {
            vel[i * 3] += noise(i * 7 + 1) * strength;
            vel[i * 3 + 1] += noise(i * 7 + 2) * strength;
            vel[i * 3 + 2] += noise(i * 7 + 3) * strength;
        }
    }

    /** Deterministic -1..1 noise. */
    public static float noise(int seed) {
        int h = seed * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return ((h & 0xffff) / 32767.5f) - 1f;
    }
}
