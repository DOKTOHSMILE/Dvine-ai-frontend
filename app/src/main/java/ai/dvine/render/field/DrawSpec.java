package ai.dvine.render.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A drawing as simple shapes on a 100 × 100 canvas (x right, y down), one per line, the way the language model
 * writes it when asked to draw something:
 * <pre>
 * circle 50 40 20 yellow fill
 * ellipse 50 60 30 12 #8899aa
 * rect 20 70 60 10 brown fill
 * line 10 90 90 90 white 2
 * polygon red fill 30 60 50 40 70 60
 * text 50 95 6 white A helicopter
 * </pre>
 * Unknown or malformed lines are skipped; numbers are clamped to the canvas. Pure Java.
 */
public final class DrawSpec {
    public enum Kind { CIRCLE, ELLIPSE, RECT, LINE, POLYGON, TEXT }

    public static final class Item {
        public final Kind kind;
        public final float[] v;
        public final int color;
        public final boolean fill;
        public final String text;

        public Item(Kind kind, float[] v, int color, boolean fill, String text) {
            this.kind = kind;
            this.v = v;
            this.color = color;
            this.fill = fill;
            this.text = text;
        }
    }

    public final List<Item> items = new ArrayList<Item>();

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static DrawSpec parse(String text) {
        DrawSpec d = new DrawSpec();
        if (text == null) {
            return d;
        }
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim().replaceAll("^[-*\\d.)]+\\s+", "").replace(",", " ").replaceAll("\\s+", " ");
            if (line.isEmpty() || line.startsWith("```")) {
                continue;
            }
            String[] t = line.split(" ");
            String cmd = t[0].toLowerCase(Locale.US);
            try {
                switch (cmd) {
                    case "circle":
                        d.add(Kind.CIRCLE, nums(t, 1, 3), colour(t, 4), has(t, "fill"), null);
                        break;
                    case "ellipse":
                    case "oval":
                        d.add(Kind.ELLIPSE, nums(t, 1, 4), colour(t, 5), has(t, "fill"), null);
                        break;
                    case "rect":
                    case "rectangle":
                    case "box":
                        d.add(Kind.RECT, nums(t, 1, 4), colour(t, 5), has(t, "fill"), null);
                        break;
                    case "line": {
                        float[] v = nums(t, 1, 4);
                        float width = t.length > 6 ? number(t[6], 1.5f) : 1.5f;
                        d.add(Kind.LINE, new float[]{v[0], v[1], v[2], v[3], Math.max(0.5f, Math.min(8f, width))}, colour(t, 5), false, null);
                        break;
                    }
                    case "polygon":
                    case "poly":
                    case "triangle": {
                        int i = 1;
                        int color = 0xFFFFFFFF;
                        boolean fill = false;
                        List<Float> pts = new ArrayList<Float>();
                        for (; i < t.length; i++) {
                            if (t[i].equalsIgnoreCase("fill")) {
                                fill = true;
                            } else if (isNumber(t[i])) {
                                pts.add(clamp(Float.parseFloat(t[i])));
                            } else {
                                Integer c = Palette.colour(t[i]);
                                if (c != null) {
                                    color = c;
                                }
                            }
                        }
                        if (pts.size() >= 6) {
                            float[] v = new float[pts.size() / 2 * 2];
                            for (int k = 0; k < v.length; k++) {
                                v[k] = pts.get(k);
                            }
                            d.add(Kind.POLYGON, v, color, fill, null);
                        }
                        break;
                    }
                    case "text":
                    case "label": {
                        float[] v = nums(t, 1, 3);
                        Integer c = t.length > 4 ? Palette.colour(t[4]) : null;
                        int from = c != null ? 5 : 4;
                        StringBuilder words = new StringBuilder();
                        for (int k = from; k < t.length; k++) {
                            words.append(k > from ? " " : "").append(t[k]);
                        }
                        if (words.length() > 0) {
                            d.add(Kind.TEXT, new float[]{v[0], v[1], Math.max(2f, Math.min(20f, v[2]))}, c != null ? c : 0xFFFFFFFF, true,
                                    words.toString().replaceAll("^[\"']|[\"']$", ""));
                        }
                        break;
                    }
                    default:
                        break; // not a shape: the model said something else; skip it
                }
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException skip) {
                // a malformed line: drawn without it
            }
        }
        return d;
    }

    private void add(Kind k, float[] v, int color, boolean fill, String text) {
        items.add(new Item(k, v, color, fill, text));
    }

    private static float[] nums(String[] t, int from, int n) {
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            v[i] = clamp(Float.parseFloat(t[from + i]));
        }
        return v;
    }

    private static int colour(String[] t, int at) {
        for (int i = at; i < t.length; i++) {
            Integer c = Palette.colour(t[i]);
            if (c != null) {
                return c;
            }
        }
        return 0xFFFFFFFF;
    }

    private static boolean has(String[] t, String word) {
        for (String s : t) {
            if (s.equalsIgnoreCase(word) || s.equalsIgnoreCase(word + "ed")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumber(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    private static float number(String s, float fallback) {
        return isNumber(s) ? Float.parseFloat(s) : fallback;
    }

    private static float clamp(float v) {
        return Math.max(-20f, Math.min(120f, v));
    }

    /** Colour names the model may use, and #rgb / #rrggbb. */
    public static final class Palette {
        private static final String[][] NAMES = {
                {"white", "FFFFFF"}, {"black", "30343C"}, {"grey", "9AA0A6"}, {"gray", "9AA0A6"}, {"silver", "C8CDD2"},
                {"red", "F2453D"}, {"orange", "FF9A2E"}, {"yellow", "FFD23F"}, {"gold", "F5C451"}, {"green", "4CD964"},
                {"lime", "A8E05F"}, {"teal", "2EC4B6"}, {"cyan", "2EE6FF"}, {"blue", "3D8BFF"}, {"navy", "2A3F8F"},
                {"purple", "A06CFF"}, {"violet", "A06CFF"}, {"pink", "FF7FB6"}, {"brown", "A26B3F"}, {"tan", "D8B48A"},
                {"beige", "E8D8B8"}, {"skin", "C68A5C"}, {"sky", "8FD3FF"}, {"skyblue", "8FD3FF"}, {"darkgreen", "2E8B57"},
                {"darkblue", "24418F"}, {"lightblue", "9CC9FF"}, {"darkgrey", "5F6670"}, {"darkgray", "5F6670"}};

        private Palette() {
        }

        public static Integer colour(String s) {
            String t = s.toLowerCase(Locale.US).replaceAll("[^#a-z0-9]", "");
            if (t.matches("#[0-9a-f]{6}")) {
                return 0xFF000000 | Integer.parseInt(t.substring(1), 16);
            }
            if (t.matches("#[0-9a-f]{3}")) {
                int r = Integer.parseInt(t.substring(1, 2), 16), g = Integer.parseInt(t.substring(2, 3), 16), b = Integer.parseInt(t.substring(3, 4), 16);
                return 0xFF000000 | (r * 17 << 16) | (g * 17 << 8) | b * 17;
            }
            for (String[] n : NAMES) {
                if (n[0].equals(t)) {
                    return 0xFF000000 | Integer.parseInt(n[1], 16);
                }
            }
            return null;
        }
    }
}
