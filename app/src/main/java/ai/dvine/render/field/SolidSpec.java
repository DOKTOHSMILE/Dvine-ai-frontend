package ai.dvine.render.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A thing built in 3D from its parts in words, the way a small language model describes it well:
 * <pre>
 * body | box | red | middle centre | large wide
 * cabin | box | sky | above body | medium
 * wheel | cylinder | grey | below body left, sideways | small
 * light | sphere | yellow | in front of body | tiny
 * </pre>
 * Each line: a part, its solid (sphere, oval, box, cylinder, cone, pyramid, ring, disc, line), its colour, where it
 * is (a place for the first; then on / above / below / left of / right of / in front of / behind / around an
 * earlier part, optionally "left" or "right", "top" or "bottom", and "near" or "far" side within it) and its size (tiny … huge; wide, tall, deep, long, flat,
 * thin; sideways or forward for a cylinder's axis). The layout places the solids in a 100-unit cube and fills their
 * surfaces with lit points: a {@link Shape} about 2 units across, centred, y up. Pure Java.
 */
public final class SolidSpec {
    public enum Solid { SPHERE, ELLIPSOID, BOX, CYLINDER, CONE, PYRAMID, RING, DISC }

    public static final class Part {
        public final String name;
        public final Solid solid;
        public final int colour;
        /** Centre and full size (canvas units, y down, z toward the viewer); axis 0 x, 1 y, 2 z. */
        public final float x, y, z, w, h, d;
        public final int axis;
        /** The part it is placed against ("above body": the body), which it hangs from as a joint; null at the top. */
        public final Part parent;

        Part(String name, Solid solid, int colour, float x, float y, float z, float w, float h, float d, int axis, Part parent) {
            this.parent = parent;
            this.name = name;
            this.solid = solid;
            this.colour = colour;
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
            this.h = h;
            this.d = d;
            this.axis = axis;
        }
    }

    public final List<Part> parts = new ArrayList<Part>();
    /**
     * The box it is fitted to (centre x, y, z and the longest side), fixed when it is first drawn so taking parts out
     * or adding them doesn't make the rest jump or grow; null fits it to its own parts.
     */
    private float[] frame;

    /** Fixes the frame to the parts as they are now (or keeps one already fixed); returns this. */
    public SolidSpec fixFrame() {
        if (frame == null && !parts.isEmpty()) {
            float[] b = bounds();
            frame = new float[]{b[0], b[1], b[2], b[3]};
        }
        return this;
    }

    /** This thing in the same frame as {@code other}, so the two line up. */
    public SolidSpec inFrameOf(SolidSpec other) {
        frame = other.frame == null ? null : other.frame.clone();
        return this;
    }

    private float[] bounds() {
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (Part p : parts) {
            minX = Math.min(minX, p.x - p.w / 2);
            maxX = Math.max(maxX, p.x + p.w / 2);
            minY = Math.min(minY, p.y - p.h / 2);
            maxY = Math.max(maxY, p.y + p.h / 2);
            minZ = Math.min(minZ, p.z - p.d / 2);
            maxZ = Math.max(maxZ, p.z + p.d / 2);
        }
        return new float[]{(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ))};
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    private static final String[] RELATIONS = {"left of", "right of", "in front of", "behind", "above", "below", "under",
            "on top of", "around", "on", "inside", "in"};

    public static SolidSpec parse(String text) {
        return parse(text, null);
    }

    /**
     * As {@link #parse(String)}; with the {@code subject} asked for, a stand, base or ground the model added on its
     * own (the subject doesn't have one) is left out.
     */
    public static SolidSpec parse(String text, String subject) {
        return parseOnto(new SolidSpec(), text, subject);
    }

    /**
     * This thing with more parts added: {@code text} in the same line format, its places relative to the parts
     * already here ("spoiler | box | black | above body | small wide"). The parts already here don't move.
     */
    public SolidSpec extend(String text) {
        SolidSpec out = copy();
        return parseOnto(out, text, null);
    }

    /** A copy (the parts themselves don't change, so they are shared). */
    public SolidSpec copy() {
        SolidSpec c = new SolidSpec();
        c.parts.addAll(parts);
        c.frame = frame == null ? null : frame.clone();
        return c;
    }

    /** This part with a new colour or a new size (a factor across, up and deep), the same place. */
    public static Part with(Part p, Integer colour, float kw, float kh, float kd) {
        return new Part(p.name, p.solid, colour != null ? colour : p.colour, p.x, p.y, p.z, p.w * kw, p.h * kh, p.d * kd, p.axis, p.parent);
    }

    private static SolidSpec parseOnto(SolidSpec out, String text, String subject) {
        if (text == null) {
            return out;
        }
        Map<String, Part> placed = new HashMap<String, Part>();
        for (Part p : out.parts) {
            placed.putIfAbsent(p.name, p);
        }
        for (String raw : text.split("\\r?\\n")) {
            String[] f = raw.replaceAll("^[-*\\d.)\\s]+", "").split("\\s*\\|\\s*");
            if (f.length < 4) {
                continue;
            }
            String name = f[0].trim().toLowerCase(Locale.US);
            Solid solid = solid(f[1].trim().toLowerCase(Locale.US));
            Integer c = DrawSpec.Palette.colour(f[2].trim().replace(" ", ""));
            String where = f[3].trim().toLowerCase(Locale.US);
            String size = f.length > 4 ? f[4].trim().toLowerCase(Locale.US) : "medium";
            if (solid == null || name.isEmpty()) {
                continue;
            }
            if (subject != null && name.matches("^(base|stand|ground|platform|floor|plinth|pedestal)$")
                    && !subject.toLowerCase(Locale.US).matches(".*\\b(lamp|statue|trophy|stand|tower|lighthouse|table|vase|globe)\\b.*")) {
                continue; // a base the model added out of habit
            }
            if (f[1].toLowerCase(Locale.US).contains("line") || f[1].toLowerCase(Locale.US).contains("pole")
                    || f[1].toLowerCase(Locale.US).contains("stick")) {
                size += " thin"; // a line is a thin rod
            }
            int axis = size.contains("sideways") || size.contains("horizontal") || size.contains("lying") || where.contains("sideways") ? 0
                    : size.contains("forward") || size.contains("pointing out") ? 2 : 1;
            float[] s = size(size, solid, axis);
            float w = s[0], h = s[1], d = s[2];
            Part ref = null;
            String rel = null;
            for (String r : RELATIONS) {
                if (where.startsWith(r + " ")) {
                    rel = r;
                    String other = where.substring(r.length() + 1).replaceAll("^(the|a|an) ", "").replaceAll(",.*$", "")
                            .replaceAll(" (left|right|top|bottom|front|back)$", "").trim();
                    ref = find(placed, other);
                    break;
                }
            }
            float x, y, z;
            if (ref != null) {
                x = ref.x;
                y = ref.y;
                z = ref.z;
                switch (rel) {
                    case "left of": x = ref.x - ref.w / 2 - w / 2 + 1; break;
                    case "right of": x = ref.x + ref.w / 2 + w / 2 - 1; break;
                    case "in front of": z = ref.z + ref.d / 2 + d / 2 - 1; break;
                    case "behind": z = ref.z - ref.d / 2 - d / 2 + 1; break;
                    case "above":
                    case "on top of": y = ref.y - ref.h / 2 - h / 2 + 1; break;
                    case "below":
                    case "under": y = ref.y + ref.h / 2 + h / 2 - 1; break;
                    case "around": // a ring or a band around the other part
                        w = Math.max(w, ref.w * 1.15f);
                        d = Math.max(d, ref.d * 1.15f);
                        break;
                    default: // on, inside, in: on its front surface, no bigger than it
                        w = Math.min(w, ref.w * 0.8f);
                        h = Math.min(h, ref.h * 0.8f);
                        z = ref.z + ref.d / 2;
                        d = Math.min(d, Math.max(2f, ref.d * 0.3f));
                        break;
                }
                if (where.matches(".*\\b(left|right)$") || where.matches(".*,\\s*(left|right).*")) {
                    float k = where.contains("left") ? -1f : 1f;
                    if (rel.equals("below") || rel.equals("under") || rel.equals("above") || rel.equals("on top of")
                            || rel.equals("on") || rel.equals("in") || rel.equals("inside") || rel.equals("in front of") || rel.equals("behind")) {
                        x += k * ref.w * 0.3f;
                    }
                }
                if (where.matches(".*,\\s*(top|bottom)\\b.*") && !rel.equals("above") && !rel.equals("below") && !rel.equals("under")
                        && !rel.equals("on top of")) {
                    y += (where.matches(".*,\\s*top\\b.*") ? -1f : 1f) * ref.h * 0.3f;
                }
                if (where.matches(".*,\\s*(near|far)\\b.*")) {
                    // the near or far side of the other part: the legs and wheels you see, and the ones behind
                    z = ref.z + (where.matches(".*,\\s*near\\b.*") ? 1f : -1f) * Math.max(0f, ref.d / 2 - d / 2 - 1);
                }
                if (ref.solid == Solid.SPHERE || ref.solid == Solid.ELLIPSOID) {
                    // on a round part, off its middle the surface curves in: an eye high on the side of a head, or an
                    // ear toward the near side of it, sits on the surface instead of floating beside it
                    float ry = (y - ref.y) / Math.max(1f, ref.h / 2), rz = (z - ref.z) / Math.max(1f, ref.d / 2),
                            rx = (x - ref.x) / Math.max(1f, ref.w / 2);
                    if (rel.equals("left of") || rel.equals("right of")) {
                        float curve = (float) Math.sqrt(Math.max(0.15f, 1f - ry * ry - rz * rz));
                        x = ref.x + (rel.equals("left of") ? -1f : 1f) * (ref.w / 2 * curve + w / 2 - 1);
                    } else if (rel.equals("above") || rel.equals("on top of") || rel.equals("below") || rel.equals("under")) {
                        float curve = (float) Math.sqrt(Math.max(0.15f, 1f - rx * rx - rz * rz));
                        y = ref.y + (rel.equals("below") || rel.equals("under") ? 1f : -1f) * (ref.h / 2 * curve + h / 2 - 1);
                    }
                }
            } else {
                y = where.contains("top") ? 20 : where.contains("upper") ? 35 : where.contains("lower") ? 65 : where.contains("bottom") ? 80 : 50;
                x = where.contains("left") ? 30 : where.contains("right") ? 70 : 50;
                z = where.contains("front") ? 20 : where.contains("back") ? -20 : 0;
            }
            Part p = new Part(name, solid, c != null ? c : 0xFFFFFFFF, x, y, z, w, h, d, axis, ref);
            out.parts.add(p);
            if (!placed.containsKey(name)) {
                placed.put(name, p);
            }
            if (where.contains("both sides") || size.contains("both sides") || size.contains("pair")) {
                // "wheels … both sides": the same part mirrored across the middle of the first part
                Part first = out.parts.get(0);
                out.parts.add(new Part(name, solid, p.colour, 2 * first.x - x, y, z, w, h, d, axis, ref));
            }
        }
        return out;
    }

    private static Part find(Map<String, Part> placed, String name) {
        Part b = placed.get(name);
        if (b != null) {
            return b;
        }
        String single = name.replaceAll("s$", "");
        for (Map.Entry<String, Part> e : placed.entrySet()) {
            String k = e.getKey();
            if (k.contains(single) || single.contains(k.replaceAll("s$", ""))) {
                return e.getValue();
            }
        }
        return null;
    }

    static Solid solid(String s) {
        if (s.matches("^(sphere|ball|circle|dot|globe|round).*")) return Solid.SPHERE;
        if (s.matches("^(oval|ellipsoid|ellipse|egg).*")) return Solid.ELLIPSOID;
        if (s.matches("^(box|cube|rect|block|square|brick|slab).*")) return Solid.BOX;
        if (s.matches("^(cylinder|tube|pipe|pole|stick|rod|line|post|column|can|log).*")) return Solid.CYLINDER;
        if (s.matches("^(cone|spike|triangle|horn|point).*")) return Solid.CONE;
        if (s.matches("^(pyramid|wedge|roof).*")) return Solid.PYRAMID;
        if (s.matches("^(ring|torus|donut|doughnut|hoop|loop).*")) return Solid.RING;
        if (s.matches("^(disc|disk|plate|wheel|coin).*")) return Solid.DISC;
        return null;
    }

    private static final java.util.regex.Pattern EXACT = java.util.regex.Pattern.compile("\\b([whd])(\\d+(?:\\.\\d+)?)\\b");

    /** Width, height and depth (canvas units) from the size words. */
    private static float[] size(String s, Solid k, int axis) {
        float base = s.contains("tiny") ? 7 : s.contains("small") ? 15 : s.contains("huge") ? 70 : s.contains("large") || s.contains("big") ? 44 : 26;
        float w = base, h = base, d = base;
        if (k == Solid.ELLIPSOID) {
            h = base * 0.62f;
        }
        if (k == Solid.DISC || k == Solid.RING) {
            d = base * 0.22f; // faces the viewer; a wheel "sideways" turns to face the side
        }
        if (s.contains("wide") || s.contains("long")) {
            w *= 1.7f;
            h *= 0.6f;
        }
        if (s.contains("tall")) {
            h *= 1.7f;
            w *= 0.6f;
            d *= 0.6f;
        }
        if (s.contains("deep")) {
            d *= 1.7f;
        }
        if (s.contains("flat")) {
            h *= 0.25f;
        }
        if (s.contains("thin")) {
            if (k == Solid.CYLINDER) {
                w = d = Math.max(2.5f, base * 0.12f);
            } else {
                d *= 0.4f;
            }
        }
        if (k == Solid.CYLINDER && axis != 1) {
            // lying along x or z: its length goes that way
            float len = Math.max(w, h), r = Math.min(w, Math.min(h, d));
            if (axis == 0) {
                w = len;
                h = d = r;
            } else {
                d = len;
                w = h = r;
            }
        }
        if ((k == Solid.DISC || k == Solid.RING) && s.contains("sideways")) {
            float t = d;
            d = w;
            w = t;
        }
        // exact sizes when given ("w46 h20 d20", canvas units): the built-in plans use them
        java.util.regex.Matcher m = EXACT.matcher(s);
        while (m.find()) {
            float v = Float.parseFloat(m.group(2));
            switch (m.group(1)) {
                case "w": w = v; break;
                case "h": h = v; break;
                default: d = v; break;
            }
        }
        return new float[]{Math.min(96, w), Math.min(96, h), Math.min(96, d)};
    }

    // ------------------------------------------------------------ the points

    /** The solids' surfaces as lit points, at most {@code maxPoints}, the whole thing {@code width} units across. */
    public Shape points(int maxPoints, float width) {
        if (parts.isEmpty()) {
            return Shape.EMPTY;
        }
        // the bounds, so the thing is centred and fits (or the frame it was first drawn in)
        double area = 0;
        double[] areas = new double[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            areas[i] = Math.max(20, surface(parts.get(i)));
            area += areas[i];
        }
        float[] fr = frame != null ? frame : bounds();
        float k = width / Math.max(1f, fr[3]);
        float cx = fr[0], cy = fr[1], cz = fr[2];
        float[] xyz = new float[maxPoints * 3], rgb = new float[maxPoints * 3];
        Random rnd = new Random(11);
        float[] pt = new float[6];
        int n = 0;
        for (int i = 0; i < parts.size() && n < maxPoints; i++) {
            Part p = parts.get(i);
            int count = (int) Math.round(maxPoints * areas[i] / area);
            float r = ((p.colour >> 16) & 255) / 255f, g = ((p.colour >> 8) & 255) / 255f, b = (p.colour & 255) / 255f;
            for (int j = 0; j < count && n < maxPoints; j++) {
                sample(p, rnd, pt);
                // light from the upper front left; a little rim light keeps dark colours visible
                float lit = 0.5f + 0.5f * Math.max(0f, -0.4f * pt[3] + 0.7f * -pt[4] + 0.6f * pt[5]);
                float rim = 0.12f * (1f - Math.abs(pt[5]));
                xyz[n * 3] = (pt[0] - cx) * k;
                xyz[n * 3 + 1] = (cy - pt[1]) * k;
                xyz[n * 3 + 2] = (pt[2] - cz) * k;
                rgb[n * 3] = Math.min(1f, r * lit + rim + 0.06f);
                rgb[n * 3 + 1] = Math.min(1f, g * lit + rim + 0.06f);
                rgb[n * 3 + 2] = Math.min(1f, b * lit + rim + 0.06f);
                n++;
            }
        }
        return new Shape(xyz, rgb, n);
    }

    private static double surface(Part p) {
        double a = p.w / 2, b = p.h / 2, c = p.d / 2;
        switch (p.solid) {
            case SPHERE:
            case ELLIPSOID:
                return 4 * Math.PI * Math.pow((Math.pow(a * b, 1.6) + Math.pow(a * c, 1.6) + Math.pow(b * c, 1.6)) / 3, 1 / 1.6);
            case BOX:
                return 2 * (p.w * p.h + p.w * p.d + p.h * p.d);
            default:
                return Math.PI * (a + c) * (p.h + (a + c) / 2) + 2 * Math.PI * a * c;
        }
    }

    /** A random point on the part's surface and its normal: out = x, y, z, nx, ny, nz (y down). */
    private static void sample(Part p, Random rnd, float[] out) {
        float a = p.w / 2, b = p.h / 2, c = p.d / 2;
        switch (p.solid) {
            case SPHERE:
            case ELLIPSOID: {
                double u = rnd.nextDouble() * 2 - 1, t = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
                float nx = (float) (s * Math.cos(t)), ny = (float) u, nz = (float) (s * Math.sin(t));
                set(out, p.x + a * nx, p.y + b * ny, p.z + c * nz, nx / a, ny / b, nz / c);
                return;
            }
            case BOX: {
                double wa = p.h * p.d, wb = p.w * p.d, wc = p.w * p.h, sum = wa + wb + wc, pick = rnd.nextDouble() * sum;
                float u = rnd.nextFloat() * 2 - 1, v = rnd.nextFloat() * 2 - 1, side = rnd.nextBoolean() ? 1 : -1;
                if (pick < wa) {
                    set(out, p.x + side * a, p.y + u * b, p.z + v * c, side, 0, 0);
                } else if (pick < wa + wb) {
                    set(out, p.x + u * a, p.y + side * b, p.z + v * c, 0, side, 0);
                } else {
                    set(out, p.x + u * a, p.y + v * b, p.z + side * c, 0, 0, side);
                }
                return;
            }
            case RING: {
                // a torus around the axis that faces the viewer (z) unless turned sideways (then x)
                double t = rnd.nextDouble() * Math.PI * 2, q = rnd.nextDouble() * Math.PI * 2;
                boolean side = p.w < p.d;
                float big = (side ? Math.max(b, c) : Math.max(a, b)) * 0.8f, tube = Math.max(1.2f, (side ? a : c) * 0.9f + big * 0.12f);
                float rx = (float) Math.cos(t), ry = (float) Math.sin(t), cq = (float) Math.cos(q), sq = (float) Math.sin(q);
                if (side) {
                    set(out, p.x + tube * sq, p.y + (big + tube * cq) * ry, p.z + (big + tube * cq) * rx, sq, cq * ry, cq * rx);
                } else {
                    set(out, p.x + (big + tube * cq) * rx, p.y + (big + tube * cq) * ry, p.z + tube * sq, cq * rx, cq * ry, sq);
                }
                return;
            }
            default: { // cylinder, disc, cone, pyramid: round (or square) along an axis
                int axis = p.solid == Solid.DISC ? (p.w < p.d ? 0 : 2) : p.axis;
                float len = axis == 0 ? p.w : axis == 1 ? p.h : p.d;
                float r1 = axis == 0 ? Math.min(b, c) : axis == 1 ? Math.min(a, c) : Math.min(a, b);
                boolean pointed = p.solid == Solid.CONE || p.solid == Solid.PYRAMID;
                float sideArea = (float) (Math.PI * r1 * len * (pointed ? 0.6 : 1)), capArea = (float) (Math.PI * r1 * r1);
                float pick = rnd.nextFloat() * (sideArea + capArea * (pointed ? 1 : 2));
                double t = rnd.nextDouble() * Math.PI * 2;
                float along, rr, nr, na;
                if (pick < sideArea) {
                    along = rnd.nextFloat() * 2 - 1; // -1 base .. 1 tip
                    rr = pointed ? r1 * (1 - (along + 1) / 2) : r1;
                    nr = 1;
                    na = pointed ? r1 / len : 0;
                } else {
                    along = pointed || rnd.nextBoolean() ? -1 : 1;
                    rr = r1 * (float) Math.sqrt(rnd.nextFloat());
                    nr = 0;
                    na = along;
                }
                float cx = (float) Math.cos(t), cz = (float) Math.sin(t);
                if (p.solid == Solid.PYRAMID) {
                    // square cross-section
                    float m = Math.max(Math.abs(cx), Math.abs(cz));
                    cx /= m;
                    cz /= m;
                }
                float off = along * len / 2;
                // the tip points up (y down: negative), forward or right
                if (axis == 1) {
                    set(out, p.x + rr * cx, p.y - off, p.z + rr * cz, nr * cx, -na, nr * cz);
                } else if (axis == 0) {
                    set(out, p.x + off, p.y + rr * cx, p.z + rr * cz, na, nr * cx, nr * cz);
                } else {
                    set(out, p.x + rr * cx, p.y + rr * cz, p.z + off, nr * cx, nr * cz, na);
                }
            }
        }
    }

    private static void set(float[] o, float x, float y, float z, float nx, float ny, float nz) {
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) {
            l = 1;
        }
        o[0] = x;
        o[1] = y;
        o[2] = z;
        o[3] = nx / l;
        o[4] = ny / l;
        o[5] = nz / l;
    }
}
