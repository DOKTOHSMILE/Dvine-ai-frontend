package ai.dvine.render.field;

/**
 * A pool of nano particles that can become any picture: each particle springs toward its place in the current
 * {@link Shape} (and its colour), so a new picture forms out of the old one instead of popping in. Particles the
 * picture doesn't need fade out where they are. A faint per-particle shimmer keeps the picture alive. Positions are
 * in the layer's own units (the stage places and scales them). Pure Java; one step per frame.
 */
public final class ParticleField {
    /** Floats per particle in {@link #pack}: x, y, z, r, g, b, a. */
    public static final int STRIDE = 7;

    private final int capacity;
    private final float[] pos, vel, target, color, targetColor, phase;
    private int used; // particles that belong to the current picture
    private boolean dirty = true;
    private float time;
    private final java.util.Random random = new java.util.Random(7);

    public ParticleField(int capacity) {
        this.capacity = capacity;
        pos = new float[capacity * 3];
        vel = new float[capacity * 3];
        target = new float[capacity * 3];
        color = new float[capacity * 4];
        targetColor = new float[capacity * 4];
        phase = new float[capacity];
        for (int i = 0; i < capacity; i++) {
            phase[i] = random.nextFloat() * 6.2832f;
            // start as a faint cloud, so the first picture forms from somewhere
            pos[i * 3] = (random.nextFloat() - 0.5f) * 2f;
            pos[i * 3 + 1] = (random.nextFloat() - 0.5f) * 1f;
            pos[i * 3 + 2] = (random.nextFloat() - 0.5f) * 0.6f;
        }
    }

    private boolean calm;

    /** Calm particles hold still and steady (for words, which must read crisply); the default shimmers. */
    public void setCalm(boolean on) {
        calm = on;
    }

    public int capacity() {
        return capacity;
    }

    /** The particles now drawn (those of the picture, and those still fading out). */
    public int visible() {
        int n = 0;
        for (int i = 0; i < capacity; i++) {
            if (color[i * 4 + 3] > 0.01f || targetColor[i * 4 + 3] > 0.01f) {
                n++;
            }
        }
        return n;
    }

    /** Forms this picture (null or empty: the particles fade away). Points beyond the capacity are thinned evenly. */
    public void show(Shape s) {
        int n = s == null ? 0 : Math.min(capacity, s.count);
        for (int i = 0; i < capacity; i++) {
            if (i < n) {
                // spread the picture's points evenly over its whole list when it has more than the pool holds
                int k = s.count <= capacity ? i : (int) ((long) i * s.count / n);
                target[i * 3] = s.xyz[k * 3];
                target[i * 3 + 1] = s.xyz[k * 3 + 1];
                target[i * 3 + 2] = s.xyz[k * 3 + 2];
                targetColor[i * 4] = s.rgb[k * 3];
                targetColor[i * 4 + 1] = s.rgb[k * 3 + 1];
                targetColor[i * 4 + 2] = s.rgb[k * 3 + 2];
                targetColor[i * 4 + 3] = 1f;
                if (calm) {
                    // words switch in place (a quick fade), they don't fly: a moving letter can't be read
                    pos[i * 3] = target[i * 3];
                    pos[i * 3 + 1] = target[i * 3 + 1];
                    pos[i * 3 + 2] = target[i * 3 + 2];
                    vel[i * 3] = vel[i * 3 + 1] = vel[i * 3 + 2] = 0f;
                    color[i * 4] = targetColor[i * 4];
                    color[i * 4 + 1] = targetColor[i * 4 + 1];
                    color[i * 4 + 2] = targetColor[i * 4 + 2];
                    if (color[i * 4 + 3] < 0.02f) {
                        color[i * 4 + 3] = 0.35f;
                    }
                } else if (color[i * 4 + 3] < 0.02f) {
                    // a particle joining the picture starts from a random point of it, a little out of place
                    int r = random.nextInt(Math.max(1, n));
                    int kr = s.count <= capacity ? r : (int) ((long) r * s.count / n);
                    pos[i * 3] = s.xyz[kr * 3] + (random.nextFloat() - 0.5f) * 0.6f;
                    pos[i * 3 + 1] = s.xyz[kr * 3 + 1] + (random.nextFloat() - 0.5f) * 0.6f;
                    pos[i * 3 + 2] = s.xyz[kr * 3 + 2] + (random.nextFloat() - 0.5f) * 0.6f;
                }
            } else {
                targetColor[i * 4 + 3] = 0f;
            }
        }
        used = n;
        dirty = true;
    }

    public int used() {
        return used;
    }

    /** Moves every particle toward its place (a damped spring) and its colour; dt in seconds. */
    public void step(float dt) {
        dt = Math.max(0f, Math.min(dt, 0.05f));
        time += dt;
        final float k = 38f, damping = 9.5f, fade = Math.min(1f, dt * (calm ? 14f : 5f));
        for (int i = 0; i < capacity; i++) {
            boolean live = targetColor[i * 4 + 3] > 0.01f;
            for (int a = 0; a < 3; a++) {
                int j = i * 3 + a;
                if (live) {
                    float acc = (target[j] - pos[j]) * k - vel[j] * damping;
                    vel[j] += acc * dt;
                } else {
                    vel[j] *= 1f - Math.min(1f, dt * 2f); // leaving particles drift to a stop
                }
                pos[j] += vel[j] * dt;
            }
            for (int c = 0; c < 4; c++) {
                int j = i * 4 + c;
                color[j] += (targetColor[j] - color[j]) * fade;
            }
        }
        dirty = true;
    }

    /**
     * The particles to draw into {@code out} (x, y, z, r, g, b, a each, {@link #STRIDE} floats), with the shimmer
     * added; returns how many. {@code out} must hold capacity × STRIDE floats.
     */
    public int pack(float[] out) {
        int n = 0;
        for (int i = 0; i < capacity; i++) {
            float a = color[i * 4 + 3];
            if (a < 0.01f) {
                continue;
            }
            float ph = phase[i];
            float tw = calm ? 0.95f : 0.75f + 0.25f * (float) Math.sin(time * 2.3f + ph * 3f);
            float jitter = calm ? 0f : 0.004f;
            int o = n * STRIDE;
            out[o] = pos[i * 3] + jitter * (float) Math.sin(time * 1.7f + ph);
            out[o + 1] = pos[i * 3 + 1] + jitter * (float) Math.cos(time * 1.3f + ph * 1.7f);
            out[o + 2] = pos[i * 3 + 2];
            out[o + 3] = color[i * 4];
            out[o + 4] = color[i * 4 + 1];
            out[o + 5] = color[i * 4 + 2];
            out[o + 6] = a * tw;
            n++;
        }
        dirty = false;
        return n;
    }

    /** Whether anything moved or changed since the last {@link #pack}. */
    public boolean dirty() {
        return dirty;
    }

    /** The mean distance of the picture's particles from their places (0 when the picture has formed). */
    public float settle() {
        if (used == 0) {
            return 0f;
        }
        double d = 0;
        for (int i = 0; i < used; i++) {
            float dx = target[i * 3] - pos[i * 3], dy = target[i * 3 + 1] - pos[i * 3 + 1], dz = target[i * 3 + 2] - pos[i * 3 + 2];
            d += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return (float) (d / used);
    }
}
