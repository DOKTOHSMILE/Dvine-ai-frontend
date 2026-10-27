package ai.dvine.face;

/** Critically-near damped spring. Motion eases. It does not snap. */
public final class Spring1D {
    public float value;
    public float velocity;
    public final float omega;
    public final float zeta;

    public Spring1D(float omega, float zeta, float initial) {
        this.omega = omega;
        this.zeta = zeta;
        this.value = initial;
    }

    public void step(float target, float dt) {
        if (dt <= 0f) {
            return;
        }
        if (dt > 0.05f) {
            dt = 0.05f;
        }
        // small sub-steps: semi-implicit Euler is only stable for steps well under 1/omega, and
        // stiff springs (blinks, omega 22) blew up on slow frames when stepped once per frame
        int n = Math.max(1, (int) Math.ceil(dt * omega / 0.25f));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            float accel = omega * omega * (target - value) - 2f * zeta * omega * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
        if (Float.isNaN(value) || Float.isNaN(velocity)) {
            value = target;
            velocity = 0f;
        }
    }
}
