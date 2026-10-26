package ai.dvine.human.face;

import java.util.Locale;

/**
 * Speech mouth shapes from the word being spoken. The speech engine reports
 * each word as it starts; the word's letters become a short sequence of
 * visemes (jaw opening, lip rounding, widening, pressing) spread over the
 * word's expected duration. Approximate by design: real phoneme timing
 * would need the voice engine to report it.
 */
public final class Visemes {
    /** jaw, round, wide, press for one viseme. */
    static float[] of(char c) {
        switch (Character.toLowerCase(c)) {
            case 'a':
                return new float[]{0.62f, 0f, 0.25f, 0f};
            case 'e':
                return new float[]{0.28f, 0f, 0.6f, 0f};
            case 'i':
            case 'y':
                return new float[]{0.3f, 0f, 0.45f, 0f};
            case 'o':
                return new float[]{0.46f, 0.7f, 0f, 0f};
            case 'u':
            case 'w':
            case 'q':
                return new float[]{0.16f, 1f, 0f, 0f};
            case 'm':
            case 'b':
            case 'p':
                return new float[]{0f, 0f, 0f, 1f};
            case 'f':
            case 'v':
                return new float[]{0.1f, 0f, 0.1f, 0.45f};
            case 'l':
                return new float[]{0.32f, 0f, 0.1f, 0f};
            default:
                return Character.isLetter(c) ? new float[]{0.14f, 0f, 0.33f, 0f} : new float[]{0f, 0f, 0f, 0f};
        }
    }

    private String word = "";
    private float start = -1f;
    private float duration;
    private final float[] out = new float[4];

    /** A new word starts being spoken at {@code time} seconds. */
    public void word(String w, float time) {
        word = w == null ? "" : w.toLowerCase(Locale.US).replaceAll("[^a-z']", "");
        start = time;
        duration = Math.max(0.18f, word.length() * 0.075f);
    }

    public void stop() {
        start = -1f;
    }

    public boolean active(float time) {
        return start >= 0f && time - start < duration + 0.12f && word.length() > 0;
    }

    /** The mouth targets at {@code time}: jaw, round, wide, press (blended between letters). */
    public float[] at(float time) {
        java.util.Arrays.fill(out, 0f);
        if (!active(time)) {
            return out;
        }
        float u = Math.max(0f, Math.min(0.999f, (time - start) / duration)) * word.length();
        int i = (int) u;
        float f = u - i;
        float[] a = of(word.charAt(i)), b = of(word.charAt(Math.min(word.length() - 1, i + 1)));
        for (int k = 0; k < 4; k++) {
            out[k] = a[k] + (b[k] - a[k]) * f * f * (3f - 2f * f);
        }
        if (time - start > duration) {
            float fade = 1f - (time - start - duration) / 0.12f;
            for (int k = 0; k < 4; k++) {
                out[k] *= Math.max(0f, fade);
            }
        }
        return out;
    }
}
