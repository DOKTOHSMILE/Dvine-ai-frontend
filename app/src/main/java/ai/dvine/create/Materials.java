package ai.dvine.create;

import java.util.Locale;

/**
 * The palette particles are tinted with on an object (a glowing drawing keeps the
 * persona's colour and leans toward the material's). Sixteen entries, so it
 * fits one shader uniform array.
 */
public final class Materials {
    public static final String[] NAMES = {"default", "white", "black", "grey", "silver", "red", "blue", "green", "yellow",
            "wood", "glass", "metal", "rubber", "light", "water", "leaves"};
    public static final float[][] RGB = {
            {0.7f, 0.9f, 1f}, {1f, 1f, 1f}, {0.25f, 0.25f, 0.3f}, {0.6f, 0.62f, 0.66f}, {0.82f, 0.85f, 0.9f}, {1f, 0.25f, 0.2f},
            {0.25f, 0.45f, 1f}, {0.3f, 0.9f, 0.4f}, {1f, 0.85f, 0.2f}, {0.85f, 0.6f, 0.35f}, {0.6f, 0.95f, 1f}, {0.75f, 0.78f, 0.82f},
            {0.3f, 0.3f, 0.32f}, {1f, 0.95f, 0.7f}, {0.2f, 0.7f, 1f}, {0.35f, 0.8f, 0.35f}};

    private Materials() {
    }

    /** Palette index for a material or colour name ("oak" is wood, "green glass" is glass…). */
    public static int index(String material) {
        String m = material == null ? "" : material.toLowerCase(Locale.US);
        if (m.contains("glass")) return 10;
        if (m.contains("water")) return 14;
        if (m.contains("leaves") || m.contains("leaf")) return 15;
        if (m.contains("light")) return 13;
        if (m.contains("rubber") || m.contains("tyre")) return 12;
        if (m.contains("oak") || m.contains("pine") || m.contains("walnut") || m.contains("wood") || m.contains("bark")) return 9;
        if (m.contains("metal") || m.contains("alumin") || m.contains("steel")) return 11;
        for (int i = 1; i < 9; i++) {
            if (m.contains(NAMES[i])) {
                return i;
            }
        }
        return 0;
    }
}
