package ai.dvine.render.pod;

import ai.dvine.stage.StageMode;

/**
 * How the pod shows what DVINE is really doing; it has no face. Idle: a slow hover, a slow turn, a gentle pulse.
 * Listening (only while the microphone is open): the rim pulses. Thinking: light runs round the rings and rises
 * faster. Speaking: the emitter beats with the words as the voice reaches them (each word's syllables). Offline:
 * dimmed, still there. The light takes the colour of whoever is speaking (Obi cyan, Ivie violet, Ibiko green).
 * Pure Java; the renderer reads the fields.
 */
public final class PodMotion {
    /** The listening pulse, thinking and the voice's beat (0..1 each), a brief flash, and how dimmed (0..1). */
    public float listen, think, voice, flash, dim;
    /** Pod hover (-1..1), turn (degrees), its energy pulse (0..1). */
    public float hover, podTurn, podPulse;
    /** The light's colour now (linear RGB). */
    public final float[] tint = {0.55f, 0.92f, 1f};

    private float time;
    private final float[] tintWant = {0.55f, 0.92f, 1f};
    private final float[] start = new float[48], dur = new float[48], open = new float[48];
    private int head;
    private float lastWord = -100f;
    private boolean dimWanted;
    private float flashAt = -10f;

    /** The voice has reached a word: the emitter beats with its syllables. */
    public void word(String w) {
        String s = w == null ? "" : w.toLowerCase(java.util.Locale.US).replaceAll("[^a-z]", "");
        if (s.isEmpty()) {
            return;
        }
        lastWord = time;
        int groups = 0;
        boolean in = false;
        for (int i = 0; i < s.length(); i++) {
            boolean v = "aeiouy".indexOf(s.charAt(i)) >= 0;
            if (v && !in) {
                groups++;
            }
            in = v;
        }
        groups = Math.max(1, groups);
        float length = Math.max(0.18f, Math.min(0.9f, s.length() * 0.075f));
        for (int i = 0; i < groups; i++) {
            int k = head;
            head = (head + 1) % start.length;
            start[k] = time + length * i / groups;
            dur[k] = length / groups;
            open[k] = 0.6f + 0.4f * ((i + s.length()) % 3) / 2f;
        }
    }

    /** Who is speaking: the light takes their colour. */
    public void speaker(String agent) {
        float[] c = "ivie".equals(agent) ? new float[]{0.78f, 0.6f, 1f} : "ibiko".equals(agent) ? new float[]{0.5f, 1f, 0.72f}
                : new float[]{0.55f, 0.92f, 1f};
        System.arraycopy(c, 0, tintWant, 0, 3);
    }

    public void setDim(boolean on) {
        dimWanted = on;
    }

    /** A brief brightening: an answer ready, something done. */
    public void flash() {
        flashAt = time;
    }

    public void step(float dt, StageMode mode) {
        dt = Math.max(0f, Math.min(dt, 0.1f));
        time += dt;
        boolean listening = mode == StageMode.LISTENING, thinking = mode == StageMode.THINKING, speaking = mode == StageMode.SPEAKING;
        float k = 1f - (float) Math.exp(-dt * 3f);
        listen += ((listening ? 1f : 0f) - listen) * k;
        think += ((thinking ? 1f : 0f) - think) * k;
        float beat = 0f;
        for (int i = 0; i < start.length; i++) {
            float u = dur[i] <= 0f ? -1f : (time - start[i]) / dur[i];
            if (u >= 0f && u <= 1f) {
                beat = Math.max(beat, open[i] * (float) Math.sin(Math.PI * u));
            }
        }
        if (speaking && lastWord < time - 1.2f) {
            beat = 0.35f + 0.25f * (float) Math.max(0, Math.sin(time * 9.0)); // a voice without word timings
        }
        if (!speaking && lastWord < time - 0.3f) {
            beat = 0f;
        }
        voice += (beat - voice) * (1f - (float) Math.exp(-dt * 20f));
        float f = time - flashAt;
        flash = f >= 0f && f < 0.8f ? 1f - f / 0.8f : 0f;
        dim += ((dimWanted ? 1f : 0f) - dim) * (1f - (float) Math.exp(-dt * 1.5f));
        for (int i = 0; i < 3; i++) {
            tint[i] += (tintWant[i] - tint[i]) * k;
        }
        hover = (float) Math.sin(time * 2 * Math.PI / 5.5f);
        podTurn = (time * (1.2f + 6f * think)) % 360f;
        podPulse = 0.5f + 0.5f * (float) Math.sin(time * 2 * Math.PI / 4.5f);
    }

    public float time() {
        return time;
    }
}
