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
        float accel = omega * omega * (target - value) - 2f * zeta * omega * velocity;
        velocity += accel * dt;
        value += velocity * dt;
        if (Float.isNaN(value) || Float.isNaN(velocity)) {
            value = target;
            velocity = 0f;
        }
    }
}
