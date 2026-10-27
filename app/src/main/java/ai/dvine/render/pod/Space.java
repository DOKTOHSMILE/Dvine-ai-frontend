package ai.dvine.render.pod;

import java.util.Random;

/**
 * The deep space behind the pod: a fixed star field (the same stars every time: a fixed seed, so switching
 * faces never regenerates it), drifting dust in front, and now and then a shooting star. Stars are 75% small and
 * distant, 20% medium, 5% bright; about one in ten twinkles on its own phase and speed. Shooting stars come one at a
 * time, every 8 to 18 seconds, crossing the sky away from the head. Pure Java: the renderer draws what this holds.
 */
public final class Space {
    /** Per star: x, y (−1..1 of the screen, a little beyond), size (px at 1×), brightness, twinkle speed (0: steady), phase, warmth, depth. */
    public static final int STAR_STRIDE = 8;
    public static final int STARS = 1200, DUST = 150;
    public final float[] stars = new float[STARS * STAR_STRIDE];
    /** Per dust mote: x, y, size, brightness, drift x, drift y, phase, depth. */
    public final float[] dust = new float[DUST * STAR_STRIDE];

    /** The shooting star in flight: start x, y, direction x, y, length, width, colour index; {@link #meteorLife} < 0 when none. */
    public final float[] meteor = new float[7];
    public float meteorLife = -1f, meteorDuration = 1f;
    private float nextMeteor;
    private final Random events;

    public Space() {
        this(20261027L);
    }

    public Space(long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < STARS; i++) {
            float kind = r.nextFloat();
            int o = i * STAR_STRIDE;
            stars[o] = r.nextFloat() * 2.2f - 1.1f;
            stars[o + 1] = r.nextFloat() * 2.2f - 1.1f;
            if (kind < 0.75f) {
                stars[o + 2] = 0.9f + r.nextFloat() * 0.8f;
                stars[o + 3] = 0.25f + r.nextFloat() * 0.35f;
            } else if (kind < 0.95f) {
                stars[o + 2] = 1.7f + r.nextFloat() * 1.0f;
                stars[o + 3] = 0.5f + r.nextFloat() * 0.3f;
            } else {
                stars[o + 2] = 2.6f + r.nextFloat() * 1.6f;
                stars[o + 3] = 0.8f + r.nextFloat() * 0.2f;
            }
            boolean twinkles = r.nextFloat() < 0.10f;
            // a twinkle lasts 1.5 to 5 seconds: speed in radians per second
            stars[o + 4] = twinkles ? (float) (2 * Math.PI / (1.5f + r.nextFloat() * 3.5f)) : 0f;
            stars[o + 5] = r.nextFloat() * 6.2832f;
            stars[o + 6] = r.nextFloat();
            stars[o + 7] = 0.2f + r.nextFloat() * 0.8f;
        }
        // shuffle so any first n stars are an even sample (fewer are drawn on a slower phone)
        for (int i = STARS - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            for (int k = 0; k < STAR_STRIDE; k++) {
                float t = stars[i * STAR_STRIDE + k];
                stars[i * STAR_STRIDE + k] = stars[j * STAR_STRIDE + k];
                stars[j * STAR_STRIDE + k] = t;
            }
        }
        for (int i = 0; i < DUST; i++) {
            int o = i * STAR_STRIDE;
            dust[o] = r.nextFloat() * 2f - 1f;
            dust[o + 1] = r.nextFloat() * 2f - 1f;
            dust[o + 2] = 1.5f + r.nextFloat() * 2.5f;
            dust[o + 3] = 0.12f + r.nextFloat() * 0.25f;
            dust[o + 4] = (r.nextFloat() - 0.5f) * 0.012f;
            dust[o + 5] = 0.004f + r.nextFloat() * 0.01f;
            dust[o + 6] = r.nextFloat() * 6.2832f;
            dust[o + 7] = r.nextFloat();
        }
        events = new Random(seed ^ 0x5DEECE66DL);
        nextMeteor = 4f + events.nextFloat() * 6f;
    }

    /** Advances to {@code time} (seconds): starts a shooting star when one is due, and ends the one in flight. */
    public void step(float time) {
        if (meteorLife >= 0f) {
            meteorLife = (time - startedAt) / meteorDuration;
            if (meteorLife > 1f) {
                meteorLife = -1f;
                nextMeteor = time + 8f + events.nextFloat() * 10f;
            }
            return;
        }
        if (time >= nextMeteor) {
            launch(time);
        }
    }

    private float startedAt;

    /** A shooting star from the upper sky or a side, heading diagonally down and away from the head. */
    void launch(float time) {
        boolean left = events.nextBoolean();
        float sx = left ? -0.95f + events.nextFloat() * 0.5f : 0.45f + events.nextFloat() * 0.5f;
        float sy = 0.55f + events.nextFloat() * 0.4f;
        float ang = (float) Math.toRadians(20 + events.nextFloat() * 25);
        float dx = (left ? -1f : 1f) * (float) Math.cos(ang), dy = -(float) Math.sin(ang);
        meteor[0] = sx;
        meteor[1] = sy;
        meteor[2] = dx;
        meteor[3] = dy;
        meteor[4] = 0.3f + events.nextFloat() * 0.3f;
        meteor[5] = 0.006f + events.nextFloat() * 0.004f;
        float c = events.nextFloat();
        meteor[6] = c < 0.55f ? 0f : c < 0.85f ? 1f : 2f; // white, pale cyan, violet
        meteorDuration = 0.6f + events.nextFloat() * 0.8f;
        startedAt = time;
        meteorLife = 0f;
    }

    /** Seconds until the next shooting star may start (for tests). */
    public float nextMeteorAt() {
        return nextMeteor;
    }

    /** How many stars twinkle. */
    public int twinkling() {
        int n = 0;
        for (int i = 0; i < STARS; i++) {
            if (stars[i * STAR_STRIDE + 4] > 0f) {
                n++;
            }
        }
        return n;
    }
}
