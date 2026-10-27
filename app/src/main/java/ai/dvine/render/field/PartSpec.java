package ai.dvine.render.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A drawing as its parts in words, which a small language model gets right far more often than coordinates:
 * <pre>
 * body | oval | grey | middle centre | large wide
 * rotor | line | silver | above body | huge
 * tail | box | grey | right of body | medium wide
 * window | circle | sky | on body | small
 * </pre>
 * Each line is a part, its shape (circle, oval, box, triangle, line), its colour, where it sits (top / upper /
 * middle / lower / bottom with left / centre / right, or on / above / below / left of / right of an earlier part) and
 * its size (tiny, small, medium, large, huge; wide, tall, thin; a triangle can point down, left or right). The
 * layout turns that into a {@link DrawSpec} on the 100 × 100 canvas. Pure Java.
 */
public final class PartSpec {
    private PartSpec() {
    }

    private static final class Box {
        final float x, y, w, h;

        Box(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /** The parts laid out as shapes; empty when no line reads as a part. */
    public static DrawSpec layout(String text) {
        DrawSpec d = new DrawSpec();
        if (text == null) {
            return d;
        }
        Map<String, Box> placed = new HashMap<String, Box>();
        Box last = null;
        for (String raw : text.split("\\r?\\n")) {
            String[] f = raw.replaceAll("^[-*\\d.)\\s]+", "").split("\\s*\\|\\s*");
            if (f.length < 4) {
                continue;
            }
            String name = f[0].trim().toLowerCase(Locale.US), shape = f[1].trim().toLowerCase(Locale.US);
            Integer colour = DrawSpec.Palette.colour(f[2].trim().replace(" ", ""));
            String where = f[3].trim().toLowerCase(Locale.US), size = f.length > 4 ? f[4].trim().toLowerCase(Locale.US) : "medium";
            DrawSpec.Kind kind = kind(shape);
            if (kind == null || name.isEmpty()) {
                continue;
            }
            float[] wh = size(size, kind);
            Box ref = null;
            String rel = null;
            for (String r : new String[]{"left of", "right of", "above", "below", "under", "on top of", "on", "inside", "in"}) {
                if (where.startsWith(r + " ")) {
                    rel = r;
                    ref = find(placed, where.substring(r.length() + 1).replaceAll("^(the|a|an) ", "").trim());
                    break;
                }
            }
            float cx, cy;
            if (ref != null) {
                switch (rel) {
                    case "left of":
                        cx = ref.x - ref.w / 2 - wh[0] / 2 + 1;
                        cy = ref.y;
                        break;
                    case "right of":
                        cx = ref.x + ref.w / 2 + wh[0] / 2 - 1;
                        cy = ref.y;
                        break;
                    case "above":
                    case "on top of":
                        cx = ref.x;
                        cy = ref.y - ref.h / 2 - wh[1] / 2 + 1;
                        break;
                    case "below":
                    case "under":
                        cx = ref.x;
                        cy = ref.y + ref.h / 2 + wh[1] / 2 - 1;
                        break;
                    default: // on, inside, in: within the other part, no bigger than it
                        cx = ref.x;
                        cy = ref.y;
                        wh[0] = Math.min(wh[0], ref.w * 0.8f);
                        wh[1] = Math.min(wh[1], ref.h * 0.8f);
                        break;
                }
                // "on the body, left" nudges within it
                if (where.contains(" left") && !"left of".equals(rel)) {
                    cx -= ref.w * 0.25f;
                } else if (where.contains(" right") && !"right of".equals(rel)) {
                    cx += ref.w * 0.25f;
                }
            } else {
                cy = where.contains("top") ? 18 : where.contains("upper") ? 34 : where.contains("lower") ? 66 : where.contains("bottom") ? 82 : 50;
                cx = where.contains("left") ? 28 : where.contains("right") ? 72 : 50;
            }
            cx = Math.max(wh[0] / 2, Math.min(100 - wh[0] / 2, cx));
            cy = Math.max(wh[1] / 2, Math.min(100 - wh[1] / 2, cy));
            last = new Box(cx, cy, wh[0], wh[1]);
            placed.put(name, last);
            int c = colour != null ? colour : 0xFFFFFFFF;
            boolean fill = !size.contains("outline");
            add(d, kind, last, c, fill, size);
        }
        return d;
    }

    private static Box find(Map<String, Box> placed, String name) {
        Box b = placed.get(name);
        if (b != null) {
            return b;
        }
        for (Map.Entry<String, Box> e : placed.entrySet()) {
            if (e.getKey().contains(name) || name.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static DrawSpec.Kind kind(String s) {
        if (s.startsWith("circle") || s.startsWith("ball") || s.startsWith("dot") || s.startsWith("round")) {
            return DrawSpec.Kind.CIRCLE;
        }
        if (s.startsWith("oval") || s.startsWith("ellipse")) {
            return DrawSpec.Kind.ELLIPSE;
        }
        if (s.startsWith("box") || s.startsWith("rect") || s.startsWith("square")) {
            return DrawSpec.Kind.RECT;
        }
        if (s.startsWith("triangle") || s.startsWith("cone") || s.startsWith("spike")) {
            return DrawSpec.Kind.POLYGON;
        }
        if (s.startsWith("line") || s.startsWith("stick") || s.startsWith("pole") || s.startsWith("bar")) {
            return DrawSpec.Kind.LINE;
        }
        return null;
    }

    /** Width and height on the canvas from the size words. */
    private static float[] size(String s, DrawSpec.Kind k) {
        float base = s.contains("tiny") ? 6 : s.contains("small") ? 14 : s.contains("huge") ? 72 : s.contains("large") || s.contains("big") ? 44 : 26;
        float w = base, h = base;
        if (k == DrawSpec.Kind.ELLIPSE) {
            h = base * 0.6f;
        }
        if (s.contains("wide") || s.contains("long")) {
            w *= 1.6f;
            h *= 0.5f;
        }
        if (s.contains("tall")) {
            h *= 1.6f;
            w *= 0.5f;
        }
        if (s.contains("thin")) {
            w *= k == DrawSpec.Kind.LINE ? 1f : 0.5f;
        }
        if (k == DrawSpec.Kind.LINE && !s.contains("vertical") && !s.contains("tall")) {
            h = 2;
        }
        if (k == DrawSpec.Kind.LINE && (s.contains("vertical") || s.contains("tall"))) {
            h = Math.max(h, base);
            w = 2;
        }
        return new float[]{Math.min(96, w), Math.min(96, h)};
    }

    private static void add(DrawSpec d, DrawSpec.Kind k, Box b, int colour, boolean fill, String size) {
        float l = b.x - b.w / 2, r = b.x + b.w / 2, t = b.y - b.h / 2, bo = b.y + b.h / 2;
        switch (k) {
            case CIRCLE:
                d.items.add(new DrawSpec.Item(k, new float[]{b.x, b.y, Math.min(b.w, b.h) / 2}, colour, fill, null));
                break;
            case ELLIPSE:
                d.items.add(new DrawSpec.Item(k, new float[]{b.x, b.y, b.w / 2, b.h / 2}, colour, fill, null));
                break;
            case RECT:
                d.items.add(new DrawSpec.Item(k, new float[]{l, t, b.w, b.h}, colour, fill, null));
                break;
            case LINE: {
                boolean vertical = b.h > b.w;
                float width = size.contains("thick") ? 4f : size.contains("thin") ? 1f : 2f;
                d.items.add(new DrawSpec.Item(k, vertical ? new float[]{b.x, t, b.x, bo, width} : new float[]{l, b.y, r, b.y, width},
                        colour, false, null));
                break;
            }
            default: { // a triangle pointing up unless told otherwise
                float[] v = size.contains("down") ? new float[]{l, t, r, t, b.x, bo}
                        : size.contains("left") ? new float[]{r, t, r, bo, l, b.y}
                        : size.contains("right") ? new float[]{l, t, l, bo, r, b.y}
                        : new float[]{l, bo, r, bo, b.x, t};
                d.items.add(new DrawSpec.Item(DrawSpec.Kind.POLYGON, v, colour, fill, null));
                break;
            }
        }
    }
}
