package ai.dvine.face;

import java.util.Random;

/**
 * One persona's muscles. Targets are tiny. The springs make them buttery.
 * Ivie rests happier. Obi rests content, not stern.
 */
public final class FaceRig {
    public final boolean ivie;
    public final Spring1D yaw = new Spring1D(7.2f, 0.96f, 0f);
    public final Spring1D pitch = new Spring1D(7.2f, 0.96f, 0f);
    public final Spring1D roll = new Spring1D(8f, 0.96f, 0f);
    public final Spring1D jaw = new Spring1D(16f, 0.84f, 0.02f);
    public final Spring1D smile;
    public final Spring1D browL = new Spring1D(9f, 0.90f, 0f);
    public final Spring1D browR = new Spring1D(9f, 0.90f, 0f);
    public final Spring1D blink = new Spring1D(22f, 0.80f, 0f);
    public final Spring1D lookX = new Spring1D(12f, 0.88f, 0f);
    public final Spring1D lookY = new Spring1D(12f, 0.88f, 0f);
    public final Spring1D glow;
    public final Spring1D wide = new Spring1D(12f, 0.86f, 0.34f);

    private final Random random;
    private FaceMode mode = FaceMode.CONTENT;
    private float time;
    private float nextBlink = 1.4f;
    private float nextSaccade = 0.9f;
    private float nextBrow = 2.1f;
    private float nextGlance = 5.5f;
    private float nextSmilePulse;
    private float blinkUntil = -1f;
    private float browUntil = -1f;
    private float glanceUntil = -1f;
    private float smilePulseUntil = -1f;
    private float modeUntil = -1f;
    private float lookTargetX;
    private float lookTargetY;
    private float browKick;
    private boolean kickLeft = true;
    private boolean dragging;
    private float dragYaw;
    private float dragPitch;

    public FaceRig(boolean ivie) {
        this.ivie = ivie;
        float restSmile = ivie ? 0.74f : 0.40f;
        smile = new Spring1D(5.2f, 0.95f, restSmile);
        glow = new Spring1D(3.4f, 0.95f, 0.62f);
        random = new Random(ivie ? 11L : 3L);
        nextSmilePulse = ivie ? 4.8f : 8.5f;
        nextGlance = ivie ? 4.2f : 6.4f;
    }

    public FaceMode mode() {
        return mode;
    }

    public float time() {
        return time;
    }

    public void setMode(FaceMode next) {
        if (next == null || next == mode) {
            return;
        }
        mode = next;
        if (next == FaceMode.DONE) {
            modeUntil = time + 0.55f;
        } else if (next == FaceMode.ERROR) {
            modeUntil = time + 1.7f;
        } else {
            modeUntil = -1f;
        }
    }

    public void setDrag(float yawDeg, float pitchDeg, boolean active) {
        dragging = active;
        dragYaw = clamp(yawDeg, -28f, 28f);
        dragPitch = clamp(pitchDeg, -16f, 16f);
    }

    public void tick(float dt) {
        if (dt < 0f) {
            return;
        }
        time += dt;
        if (mode == FaceMode.DONE && modeUntil > 0f && time > modeUntil) {
            mode = FaceMode.CONTENT;
        }
        if (mode == FaceMode.ERROR && modeUntil > 0f && time > modeUntil) {
            mode = FaceMode.CONTENT;
        }

        float phase = ivie ? 0.6f : 0f;
        float microYaw = (float) Math.sin(time * 0.37f + phase) * 1.05f;
        float microPitch = (float) Math.sin(time * 0.23f + phase) * 0.65f;
        float microRoll = (float) Math.sin(time * 0.19f + phase) * 0.40f;

        if (time > nextBlink) {
            blinkUntil = time + 0.10f;
            float gap = mode == FaceMode.LISTENING ? 2.0f : 3.2f;
            nextBlink = time + gap + random.nextFloat() * 2.6f;
        }
        float blinkTarget = time < blinkUntil ? 1f : 0f;
        if (ivie && mode == FaceMode.THINKING) {
            blinkTarget = Math.max(blinkTarget, 0.72f);
        }

        if (time > nextSaccade) {
            lookTargetX = (random.nextFloat() - 0.5f) * (ivie ? 0.42f : 0.30f);
            lookTargetY = (random.nextFloat() - 0.5f) * 0.20f;
            nextSaccade = time + 2.2f + random.nextFloat() * 2.4f;
        }
        float aimX = lookTargetX;
        float aimY = lookTargetY;
        if (mode == FaceMode.SPEAKING) {
            aimX *= 0.15f;
            aimY *= 0.15f;
        }

        if (time > nextBrow && mode != FaceMode.SPEAKING) {
            browKick = ivie ? 0.20f + random.nextFloat() * 0.10f : 0.10f + random.nextFloat() * 0.06f;
            kickLeft = random.nextBoolean();
            browUntil = time + 0.38f;
            nextBrow = time + 3.4f + random.nextFloat() * 3.2f;
        }
        if (time > browUntil) {
            browKick = 0f;
        }

        float glance = 0f;
        if (mode == FaceMode.CONTENT && time > nextGlance) {
            glanceUntil = time + 1.05f;
            nextGlance = time + 7f + random.nextFloat() * 4f;
        }
        if (time < glanceUntil && mode == FaceMode.CONTENT) {
            glance = ivie ? -8f : 8f;
            lookTargetX = ivie ? -0.35f : 0.35f;
        }

        float smileTarget = ivie ? 0.74f : 0.40f;
        if (time > nextSmilePulse) {
            smilePulseUntil = time + (ivie ? 1.15f : 0.7f);
            nextSmilePulse = time + (ivie ? 6f : 10f) + random.nextFloat() * 3f;
        }
        if (time < smilePulseUntil) {
            smileTarget += ivie ? 0.14f : 0.07f;
        }
        if (mode == FaceMode.SPEAKING) {
            smileTarget += ivie ? 0.08f : 0.04f;
        }
        if (mode == FaceMode.LISTENING) {
            smileTarget += 0.04f;
        }
        smileTarget = clamp(smileTarget, 0f, 1f);

        float jawTarget = 0.02f + (float) Math.sin(time * 1.25f + phase) * 0.008f;
        float wideTarget = 0.34f + smile.value * 0.06f;
        float speechBrow = 0f;
        if (mode == FaceMode.SPEAKING) {
            float w = (float) Math.sin(time * 26.0 + (ivie ? 0.5 : 0.0));
            float open = w > 0f ? (float) Math.pow(w, 1.65) : 0f;
            jawTarget = 0.03f + open * (ivie ? 0.24f : 0.20f);
            wideTarget = 0.26f + (1f - open) * 0.24f;
            speechBrow = open * (ivie ? 0.16f : 0.08f);
        }

        float yawTarget;
        float pitchTarget = microPitch;
        float rollTarget = microRoll;
        if (dragging) {
            yawTarget = dragYaw;
            pitchTarget = dragPitch;
            rollTarget = 0f;
        } else if (mode == FaceMode.THINKING) {
            yawTarget = (float) Math.sin(time * Math.PI / 2.0) * 12f;
            pitchTarget = ivie ? 4f : 1.5f;
        } else if (mode == FaceMode.LISTENING) {
            yawTarget = (ivie ? -4f : 4f) + microYaw;
            rollTarget = ivie ? 6f : -6f;
        } else if (mode == FaceMode.SPEAKING) {
            yawTarget = microYaw * 0.4f;
        } else if (mode == FaceMode.ERROR) {
            yawTarget = (float) Math.sin(time * 5.0) * 1.4f;
        } else {
            yawTarget = microYaw + glance;
        }

        float glowTarget = 0.62f;
        if (mode == FaceMode.LISTENING || mode == FaceMode.SPEAKING || mode == FaceMode.DONE) {
            glowTarget = 1f;
        } else if (mode == FaceMode.THINKING) {
            glowTarget = 0.82f;
        } else if (mode == FaceMode.ERROR) {
            glowTarget = 0.34f + 0.12f * (float) (0.5 + 0.5 * Math.sin(time * 5.5));
        } else if (mode == FaceMode.APPROVAL) {
            glowTarget = ivie ? 0.78f + 0.18f * (float) Math.sin(time * 2.6) : 0.42f;
        }
        glowTarget += (float) Math.sin(time * 1.6f + phase) * 0.025f;

        float left = browKick * (kickLeft || mode == FaceMode.SPEAKING ? 1f : 0.25f) + speechBrow;
        float right = browKick * (!kickLeft || mode == FaceMode.SPEAKING ? 1f : 0.25f) + speechBrow;
        if (mode == FaceMode.LISTENING) {
            left += ivie ? 0.18f : 0.10f;
            right += ivie ? 0.18f : 0.10f;
        }

        yaw.step(yawTarget, dt);
        pitch.step(pitchTarget, dt);
        roll.step(rollTarget, dt);
        jaw.step(jawTarget, dt);
        smile.step(smileTarget, dt);
        browL.step(left, dt);
        browR.step(right, dt);
        blink.step(blinkTarget, dt);
        lookX.step(aimX, dt);
        lookY.step(aimY, dt);
        glow.step(glowTarget, dt);
        wide.step(wideTarget, dt);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
