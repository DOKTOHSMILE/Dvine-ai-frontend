package ai.dvine.create;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.geom.TriMesh;

/**
 * Builds real geometry from an {@link ObjectSpec}: every object is a set of
 * named, closed parts with dimensions in metres and a build order, so it can
 * be inspected, edited, validated, saved and exported, whatever the particles
 * do with it. New categories are added as generators; anything without one
 * can still be composed from basic shapes ("a stack of three spheres").
 */
public final class ProceduralModelingEngine {
    /** Categories with a dedicated generator. */
    public static final String[] CATEGORIES = {"house", "car", "table", "chair", "sofa", "bed", "bookshelf", "lamp", "mug",
            "bottle", "laptop", "phone", "tree", "bicycle", "motorcycle", "person", "shapes", "progress"};

    private ProceduralModelingEngine() {
    }

    public static boolean supports(String category) {
        for (String c : CATEGORIES) {
            if (c.equals(category)) {
                return true;
            }
        }
        return false;
    }

    public static ObjectModel build(ObjectSpec s, int version, List<Provenance> provenance) {
        Parts p = new Parts();
        String title;
        switch (s.category) {
            case "house": title = house(s, p); break;
            case "car": title = car(s, p); break;
            case "table": title = table(s, p); break;
            case "chair": title = chair(s, p); break;
            case "sofa": title = sofa(s, p); break;
            case "bed": title = bed(s, p); break;
            case "bookshelf": title = bookshelf(s, p); break;
            case "lamp": title = lamp(s, p); break;
            case "mug": title = mug(s, p); break;
            case "bottle": title = bottle(s, p); break;
            case "laptop": title = laptop(s, p); break;
            case "phone": title = phone(s, p); break;
            case "tree": title = tree(s, p); break;
            case "bicycle": title = bicycle(s, p); break;
            case "motorcycle": title = motorcycle(s, p); break;
            case "person": title = BodyRig.assemble(s, p); break;
            case "progress": title = progress(s, p); break;
            default: title = shapes(s, p);
        }
        List<Provenance> prov = new ArrayList<Provenance>(provenance);
        if (prov.isEmpty()) {
            prov.add(new Provenance("default", "DVINE procedural generator: " + s.category, System.currentTimeMillis(), "own work",
                    "dimensions are typical defaults unless you gave them"));
        }
        ObjectModel m = new ObjectModel(s, version, title, p.list, prov);
        if ("person".equals(s.category)) {
            BodyRig.attach(m);
        }
        m.problems.addAll(GeometryValidator.validate(m));
        return m;
    }

    /** Collects parts with increasing ids. */
    static final class Parts {
        final List<Component> list = new ArrayList<Component>();

        Component add(String name, String kind, String material, int step, TriMesh mesh) {
            Component c = new Component(list.size(), name, kind, material, step, mesh);
            list.add(c);
            return c;
        }
    }

    // ------------------------------------------------------------ house

    private static String house(ObjectSpec s, Parts p) {
        int floors = Math.max(1, Math.min(4, s.count("floors", 2)));
        float w = clamp(s.num("width", 12f), 5f, 40f), d = clamp(s.num("depth", 9f), 5f, 30f), fh = 3f;
        String roof = s.str("roof", "modern".equals(s.str("style", "modern")) ? "flat" : "gable");
        String wall = s.str("color", "white");
        p.add("foundation", "foundation", "concrete", 0, Geo.box(0f, 0.15f, 0f, w + 0.6f, 0.3f, d + 0.6f));
        for (int f = 0; f < floors; f++) {
            float y0 = 0.3f + f * fh;
            int step = 1 + f * 3;
            p.add(floorName(f) + " slab", "floor", "concrete", step, Geo.box(0f, y0 + 0.1f, 0f, w, 0.2f, d));
            float t = 0.25f, wy = y0 + 0.2f + (fh - 0.2f) / 2, wh = fh - 0.2f;
            p.add(floorName(f) + " front wall", "wall", wall, step + 1, Geo.box(0f, wy, d / 2 - t / 2, w, wh, t));
            p.add(floorName(f) + " back wall", "wall", wall, step + 1, Geo.box(0f, wy, -d / 2 + t / 2, w, wh, t));
            p.add(floorName(f) + " left wall", "wall", wall, step + 1, Geo.box(-w / 2 + t / 2, wy, 0f, t, wh, d - 2 * t));
            p.add(floorName(f) + " right wall", "wall", wall, step + 1, Geo.box(w / 2 - t / 2, wy, 0f, t, wh, d - 2 * t));
            p.add(floorName(f) + " inner wall", "interior wall", wall, step + 1, Geo.box(w * 0.1f, wy, 0f, 0.12f, wh, d - 2 * t));
            // windows: glass panes standing just proud of the front and back walls
            int across = Math.max(2, (int) (w / 3f));
            for (int k = 0; k < across; k++) {
                float x = -w / 2 + w * (k + 0.5f) / across;
                if (f == 0 && Math.abs(x) < w / (2f * across)) {
                    continue; // the front door goes here
                }
                p.add(floorName(f) + " front window " + (k + 1), "window", "glass", step + 2,
                        Geo.box(x, y0 + 1.6f, d / 2 + 0.03f, Math.min(1.6f, w / across * 0.6f), 1.4f, 0.08f));
                p.add(floorName(f) + " back window " + (k + 1), "window", "glass", step + 2,
                        Geo.box(x, y0 + 1.6f, -d / 2 - 0.03f, Math.min(1.6f, w / across * 0.6f), 1.4f, 0.08f));
            }
            if (f == 0) {
                p.add("front door", "door", "wood", step + 2, Geo.box(0f, y0 + 0.2f + 1.05f, d / 2 + 0.04f, 1.1f, 2.1f, 0.08f));
            }
            if (f < floors - 1) {
                // a straight flight of stairs up to the next floor, inside by the left wall
                for (int k = 0; k < 14; k++) {
                    float sy = y0 + 0.2f + (k + 0.5f) * (fh / 14f);
                    p.add("stairs " + floorName(f) + " step " + (k + 1), "stairs", "wood", step + 1,
                            Geo.box(-w / 2 + 1.0f, sy - fh / 28f, -d / 2 + 1.0f + k * 0.27f, 1.2f, fh / 14f, 0.28f));
                }
            }
        }
        float top = 0.3f + floors * fh;
        switch (roof) {
            case "gable":
                p.add("roof", "roof", "tiles", 3 * floors + 2, Geo.gable(0f, top, 0f, w + 0.8f, d + 0.8f, d * 0.35f));
                break;
            case "hip":
                p.add("roof", "roof", "tiles", 3 * floors + 2, Geo.hip(0f, top, 0f, w + 0.8f, d + 0.8f, d * 0.3f));
                break;
            default:
                p.add("roof", "roof", "concrete", 3 * floors + 2, Geo.box(0f, top + 0.15f, 0f, w + 0.6f, 0.3f, d + 0.6f));
        }
        int extra = 3 * floors + 3;
        if (s.flag("balcony") && floors > 1) {
            float by = 0.3f + fh + 0.1f;
            p.add("balcony", "balcony", "concrete", extra, Geo.box(0f, by, d / 2 + 0.75f, w * 0.4f, 0.2f, 1.5f));
            p.add("balcony railing", "railing", "glass", extra + 1, Geo.box(0f, by + 0.6f, d / 2 + 1.48f, w * 0.4f, 1.0f, 0.05f));
        }
        if (s.flag("garage")) {
            float gw = 4.2f, gd = Math.min(7f, d);
            p.add("garage", "garage", wall, extra, Geo.box(w / 2 + gw / 2, 0.3f + 1.4f, (d - gd) / 2, gw, 2.8f, gd));
            p.add("garage door", "door", "metal", extra + 1, Geo.box(w / 2 + gw / 2, 0.3f + 1.15f, d / 2 + 0.04f, 3.2f, 2.3f, 0.08f));
        }
        if (s.flag("pool")) {
            float pl = Math.min(10f, w * 0.8f), pw = 4f, pz = d / 2 + 4.5f;
            p.add("pool deck", "pool", "stone", extra + 2, Geo.box(0f, 0.05f, pz, pl + 1.6f, 0.1f, pw + 1.6f));
            p.add("pool water", "water", "water", extra + 3, Geo.box(0f, 0.12f, pz, pl, 0.05f, pw));
        }
        if (s.flag("fence")) {
            float fx = w / 2 + (s.flag("garage") ? 6f : 2f), fz = d / 2 + (s.flag("pool") ? 8f : 3f), bz = -d / 2 - 2f;
            float[][] runs = {{-w / 2 - 2f, fz, fx, fz}, {-w / 2 - 2f, bz, fx, bz}, {-w / 2 - 2f, bz, -w / 2 - 2f, fz}, {fx, bz, fx, fz}};
            int k = 0;
            for (float[] r : runs) {
                float len = (float) Math.hypot(r[2] - r[0], r[3] - r[1]);
                float yaw = (float) Math.toDegrees(Math.atan2(-(r[3] - r[1]), r[2] - r[0]));
                p.add("fence " + (++k), "fence", "wood", extra + 4, Geo.box((r[0] + r[2]) / 2, 0.6f, (r[1] + r[3]) / 2, len, 1.2f, 0.06f, yaw));
            }
        }
        if (s.flag("garden")) {
            for (int k = 0; k < 4; k++) {
                float x = -w / 2 - 1.2f, z = -d / 2 + d * (k + 0.5f) / 4;
                p.add("garden tree " + (k + 1) + " trunk", "tree", "wood", extra + 5, Geo.cylinder(x, 0f, z, x, 1.8f, z, 0.12f, 8));
                p.add("garden tree " + (k + 1) + " crown", "tree", "leaves", extra + 6, Geo.sphere(x, 2.5f, z, 0.9f, 1.0f, 6));
            }
        }
        return (floors == 1 ? "single-storey" : floors + "-storey") + " " + s.str("style", "modern") + " house";
    }

    private static String floorName(int f) {
        return f == 0 ? "ground floor" : f == 1 ? "first floor" : f == 2 ? "second floor" : "third floor";
    }

    // ------------------------------------------------------------ car

    private static String car(ObjectSpec s, Parts p) {
        String style = s.str("style", "sedan");
        float L = clamp(s.num("length", style.equals("suv") ? 4.7f : style.equals("sports") ? 4.4f : 4.6f), 3f, 6f);
        float W = clamp(s.num("width", 1.85f), 1.4f, 2.3f);
        float H = clamp(s.num("height", style.equals("suv") ? 1.75f : style.equals("sports") ? 1.25f : 1.45f), 1f, 2.2f);
        float wheel = clamp(s.num("wheel", style.equals("suv") ? 0.75f : 0.66f), 0.4f, 1.1f), r = wheel / 2;
        String paint = s.str("color", "silver");
        float clear = r * 0.55f, bodyH = (H - clear) * 0.5f, bodyY = clear + bodyH / 2;
        p.add("chassis", "chassis", "metal", 0, Geo.box(0f, clear * 0.8f, 0f, L * 0.86f, clear * 0.5f, W * 0.8f));
        p.add("body", "body", paint, 2, Geo.box(0f, bodyY, 0f, L, bodyH, W));
        float cabL = L * (style.equals("sports") ? 0.42f : 0.5f), cabH = H - clear - bodyH;
        p.add("cabin", "cabin", paint, 3, Geo.frustum(-L * 0.04f, bodyY + bodyH / 2 + cabH / 2, 0f, cabL, W * 0.92f, cabL * 0.62f,
                W * 0.82f, cabH, 0f));
        float gy = bodyY + bodyH / 2 + cabH * 0.55f;
        p.add("windshield", "window", "glass", 4, Geo.box(-L * 0.04f + cabL * 0.4f, gy, 0f, 0.06f, cabH * 0.7f, W * 0.78f));
        p.add("rear window", "window", "glass", 4, Geo.box(-L * 0.04f - cabL * 0.4f, gy, 0f, 0.06f, cabH * 0.7f, W * 0.76f));
        p.add("left windows", "window", "glass", 4, Geo.box(-L * 0.04f, gy, W * 0.44f, cabL * 0.7f, cabH * 0.55f, 0.05f));
        p.add("right windows", "window", "glass", 4, Geo.box(-L * 0.04f, gy, -W * 0.44f, cabL * 0.7f, cabH * 0.55f, 0.05f));
        float wx = L * 0.33f, wz = W / 2 - 0.12f;
        String[] names = {"front left wheel", "front right wheel", "rear left wheel", "rear right wheel"};
        float[][] at = {{wx, wz}, {wx, -wz}, {-wx, wz}, {-wx, -wz}};
        for (int k = 0; k < 4; k++) {
            float side = at[k][1] > 0 ? 1f : -1f;
            p.add(names[k], "wheel", "rubber", 1, Geo.cylinder(at[k][0], r, at[k][1] - side * 0.11f, at[k][0], r, at[k][1] + side * 0.11f, r, 18));
            p.add(names[k].replace("wheel", "hub"), "hub", "metal", 1,
                    Geo.cylinder(at[k][0], r, at[k][1] + side * 0.1f, at[k][0], r, at[k][1] + side * 0.13f, r * 0.55f, 12));
        }
        p.add("front bumper", "bumper", "plastic", 5, Geo.box(L / 2 + 0.05f, clear + 0.12f, 0f, 0.12f, 0.22f, W * 0.96f));
        p.add("rear bumper", "bumper", "plastic", 5, Geo.box(-L / 2 - 0.05f, clear + 0.12f, 0f, 0.12f, 0.22f, W * 0.96f));
        for (int k = -1; k <= 1; k += 2) {
            p.add(k < 0 ? "left headlight" : "right headlight", "light", "light", 5, Geo.box(L / 2 + 0.01f, bodyY + bodyH * 0.2f, k * -W * 0.36f, 0.06f, 0.12f, 0.3f));
            p.add(k < 0 ? "left taillight" : "right taillight", "light", "red light", 5, Geo.box(-L / 2 - 0.01f, bodyY + bodyH * 0.2f, k * -W * 0.36f, 0.06f, 0.12f, 0.3f));
            p.add(k < 0 ? "left mirror" : "right mirror", "mirror", paint, 5, Geo.box(L * 0.12f, bodyY + bodyH / 2 + 0.02f, k * -(W / 2 + 0.07f), 0.12f, 0.1f, 0.16f));
        }
        return style + " car";
    }

    // ------------------------------------------------------------ furniture and objects

    private static String table(ObjectSpec s, Parts p) {
        float w = clamp(s.num("width", 1.6f), 0.4f, 4f), d = clamp(s.num("depth", 0.9f), 0.4f, 2.5f), h = clamp(s.num("height", 0.75f), 0.3f, 1.2f);
        String mat = s.str("color", "oak");
        boolean round = "round".equals(s.str("shape", "rectangular"));
        float top = 0.04f;
        if (round) {
            p.add("pedestal", "leg", mat, 0, Geo.cylinder(0f, 0f, 0f, 0f, h - top, 0f, 0.07f, 12));
            p.add("base", "leg", mat, 0, Geo.cylinder(0f, 0f, 0f, 0f, 0.04f, 0f, Math.min(w, d) * 0.3f, 16));
            p.add("table top", "top", mat, 1, Geo.cylinder(0f, h - top, 0f, 0f, h, 0f, w / 2, 32));
        } else {
            int k = 0;
            for (int x = -1; x <= 1; x += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    p.add("leg " + (++k), "leg", mat, 0, Geo.box(x * (w / 2 - 0.06f), (h - top) / 2, z * (d / 2 - 0.06f), 0.06f, h - top, 0.06f));
                }
            }
            p.add("apron", "frame", mat, 1, Geo.box(0f, h - top - 0.05f, 0f, w - 0.2f, 0.08f, d - 0.2f));
            p.add("table top", "top", mat, 2, Geo.box(0f, h - top / 2, 0f, w, top, d));
        }
        return (round ? "round" : "") + " table";
    }

    private static String chair(ObjectSpec s, Parts p) {
        float seat = clamp(s.num("height", 0.46f), 0.3f, 0.8f), w = clamp(s.num("width", 0.46f), 0.35f, 0.9f);
        String mat = s.str("color", "walnut");
        int k = 0;
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                p.add("leg " + (++k), "leg", mat, 0, Geo.box(x * (w / 2 - 0.03f), (seat - 0.04f) / 2, z * (w / 2 - 0.03f), 0.04f, seat - 0.04f, 0.04f));
            }
        }
        p.add("seat", "seat", mat, 1, Geo.box(0f, seat - 0.02f, 0f, w, 0.04f, w));
        p.add("backrest", "back", mat, 2, Geo.box(0f, seat + 0.24f, -w / 2 + 0.02f, w, 0.45f, 0.04f));
        if (s.flag("arms")) {
            p.add("left arm", "arm", mat, 3, Geo.box(-w / 2 + 0.02f, seat + 0.2f, 0f, 0.04f, 0.04f, w));
            p.add("right arm", "arm", mat, 3, Geo.box(w / 2 - 0.02f, seat + 0.2f, 0f, 0.04f, 0.04f, w));
        }
        return "chair";
    }

    private static String sofa(ObjectSpec s, Parts p) {
        float w = clamp(s.num("width", 2.1f), 1f, 4f), d = 0.9f;
        String mat = s.str("color", "grey fabric");
        for (int k = 0; k < 4; k++) {
            float fx = (k % 2 == 0 ? -1 : 1) * (w / 2 - 0.08f), fz = (k < 2 ? -1 : 1) * (d / 2 - 0.08f);
            p.add("foot " + (k + 1), "foot", "wood", 0, Geo.box(fx, 0.04f, fz, 0.06f, 0.08f, 0.06f));
        }
        p.add("base", "base", mat, 0, Geo.box(0f, 0.22f, 0f, w, 0.3f, d));
        int seats = Math.max(1, Math.round(w / 0.7f));
        for (int k = 0; k < seats; k++) {
            float x = -w / 2 + 0.18f + (w - 0.36f) * (k + 0.5f) / seats;
            p.add("seat cushion " + (k + 1), "cushion", mat, 1, Geo.box(x, 0.45f, 0.05f, (w - 0.36f) / seats - 0.02f, 0.16f, d - 0.25f));
            p.add("back cushion " + (k + 1), "cushion", mat, 2, Geo.box(x, 0.68f, -d / 2 + 0.17f, (w - 0.36f) / seats - 0.02f, 0.42f, 0.18f));
        }
        p.add("left arm", "arm", mat, 1, Geo.box(-w / 2 + 0.09f, 0.42f, 0f, 0.18f, 0.5f, d));
        p.add("right arm", "arm", mat, 1, Geo.box(w / 2 - 0.09f, 0.42f, 0f, 0.18f, 0.5f, d));
        p.add("back", "back", mat, 1, Geo.box(0f, 0.55f, -d / 2 + 0.05f, w, 0.75f, 0.1f));
        return "sofa";
    }

    private static String bed(ObjectSpec s, Parts p) {
        float w = clamp(s.num("width", 1.6f), 0.9f, 2.2f), l = 2.1f;
        for (int k = 0; k < 4; k++) {
            float lx = (k % 2 == 0 ? -1 : 1) * (w / 2 - 0.02f), lz = (k < 2 ? -1 : 1) * (l / 2 - 0.02f);
            p.add("leg " + (k + 1), "leg", s.str("color", "oak"), 0, Geo.box(lx, 0.045f, lz, 0.07f, 0.09f, 0.07f));
        }
        p.add("frame", "frame", s.str("color", "oak"), 0, Geo.box(0f, 0.2f, 0f, w + 0.1f, 0.25f, l + 0.1f));
        p.add("mattress", "mattress", "fabric", 1, Geo.box(0f, 0.43f, 0.03f, w, 0.22f, l - 0.06f));
        p.add("headboard", "headboard", s.str("color", "oak"), 2, Geo.box(0f, 0.65f, -l / 2 - 0.03f, w + 0.1f, 0.9f, 0.06f));
        p.add("left pillow", "pillow", "fabric", 3, Geo.box(-w / 4, 0.59f, -l / 2 + 0.3f, w / 2 - 0.1f, 0.12f, 0.4f));
        p.add("right pillow", "pillow", "fabric", 3, Geo.box(w / 4, 0.59f, -l / 2 + 0.3f, w / 2 - 0.1f, 0.12f, 0.4f));
        return "bed";
    }

    private static String bookshelf(ObjectSpec s, Parts p) {
        float w = clamp(s.num("width", 0.9f), 0.4f, 3f), h = clamp(s.num("height", 1.9f), 0.6f, 3f), d = 0.32f;
        int shelves = Math.max(2, Math.min(8, s.count("shelves", 5)));
        String mat = s.str("color", "pine");
        p.add("left side", "side", mat, 0, Geo.box(-w / 2 + 0.01f, h / 2, 0f, 0.02f, h, d));
        p.add("right side", "side", mat, 0, Geo.box(w / 2 - 0.01f, h / 2, 0f, 0.02f, h, d));
        p.add("back panel", "back", mat, 1, Geo.box(0f, h / 2, -d / 2 + 0.005f, w - 0.04f, h, 0.01f));
        for (int k = 0; k <= shelves; k++) {
            float y = 0.01f + (h - 0.02f) * k / shelves;
            p.add("shelf " + (k + 1), "shelf", mat, 2, Geo.box(0f, y, 0f, w - 0.04f, 0.02f, d));
        }
        return "bookshelf";
    }

    private static String lamp(ObjectSpec s, Parts p) {
        float h = clamp(s.num("height", 0.55f), 0.2f, 2f);
        p.add("base", "base", "metal", 0, Geo.cylinder(0f, 0f, 0f, 0f, 0.03f, 0f, 0.1f, 20));
        p.add("stem", "stem", "metal", 1, Geo.cylinder(0f, 0.03f, 0f, 0f, h * 0.75f, 0f, 0.012f, 10));
        p.add("bulb", "bulb", "light", 2, Geo.sphere(0f, h * 0.72f, 0f, 0.04f, 0.05f, 6));
        p.add("shade", "shade", s.str("color", "linen"), 3, Geo.cone(0f, h * 0.62f, 0f, 0f, h, 0f, 0.18f, 0.11f, 24));
        return "lamp";
    }

    private static String mug(ObjectSpec s, Parts p) {
        p.add("cup", "cup", s.str("color", "white ceramic"), 0, Geo.cylinder(0f, 0f, 0f, 0f, 0.1f, 0f, 0.042f, 24));
        p.add("handle", "handle", s.str("color", "white ceramic"), 1, Geo.torus(0.052f, 0.05f, 0f, 0.025f, 0.007f, "z", 16, 8));
        return "mug";
    }

    private static String bottle(ObjectSpec s, Parts p) {
        float h = clamp(s.num("height", 0.3f), 0.1f, 1f);
        p.add("body", "body", s.str("color", "green glass"), 0, Geo.cylinder(0f, 0f, 0f, 0f, h * 0.62f, 0f, h * 0.12f, 20));
        p.add("shoulder", "neck", s.str("color", "green glass"), 1, Geo.cone(0f, h * 0.62f, 0f, 0f, h * 0.8f, 0f, h * 0.12f, h * 0.045f, 20));
        p.add("neck", "neck", s.str("color", "green glass"), 2, Geo.cylinder(0f, h * 0.8f, 0f, 0f, h * 0.96f, 0f, h * 0.045f, 14));
        p.add("cap", "cap", "metal", 3, Geo.cylinder(0f, h * 0.96f, 0f, 0f, h, 0f, h * 0.05f, 14));
        return "bottle";
    }

    private static String laptop(ObjectSpec s, Parts p) {
        float w = clamp(s.num("width", 0.33f), 0.2f, 0.5f), d = w * 0.68f;
        String mat = s.str("color", "aluminium");
        p.add("base", "base", mat, 0, Geo.box(0f, 0.009f, 0f, w, 0.018f, d));
        p.add("keyboard", "keyboard", "plastic", 1, Geo.box(0f, 0.0195f, 0.02f, w * 0.85f, 0.003f, d * 0.45f));
        // the lid stands on the base's back edge, leaning back 15 degrees
        TriMesh lid = Geo.box(0f, 0.018f + d / 2, -d / 2 + 0.004f, w, d, 0.008f);
        tiltX(lid, -15f, 0f, 0.018f, -d / 2 + 0.004f);
        p.add("screen", "screen", mat, 2, lid);
        return "laptop";
    }

    private static String phone(ObjectSpec s, Parts p) {
        p.add("body", "body", s.str("color", "black"), 0, Geo.box(0f, 0.075f, 0f, 0.072f, 0.15f, 0.008f));
        p.add("screen", "screen", "glass", 1, Geo.box(0f, 0.075f, 0.0045f, 0.066f, 0.142f, 0.001f));
        p.add("camera", "camera", "glass", 2, Geo.cylinder(-0.02f, 0.13f, -0.004f, -0.02f, 0.13f, -0.007f, 0.007f, 12));
        return "phone";
    }

    private static String tree(ObjectSpec s, Parts p) {
        float h = clamp(s.num("height", 6f), 1f, 30f);
        p.add("trunk", "trunk", "bark", 0, Geo.cone(0f, 0f, 0f, 0f, h * 0.55f, 0f, h * 0.04f, h * 0.025f, 10));
        p.add("lower crown", "crown", "leaves", 1, Geo.sphere(0f, h * 0.58f, 0f, h * 0.3f, h * 0.22f, 8));
        p.add("upper crown", "crown", "leaves", 2, Geo.sphere(h * 0.04f, h * 0.8f, 0f, h * 0.2f, h * 0.18f, 8));
        return "tree";
    }

    private static String bicycle(ObjectSpec s, Parts p) {
        float r = clamp(s.num("wheel", 0.68f), 0.3f, 0.9f) / 2, base = r * 3f;
        String paint = s.str("color", "red");
        p.add("rear wheel", "wheel", "rubber", 0, Geo.torus(-base / 2, r, 0f, r - 0.02f, 0.02f, "z", 32, 8));
        p.add("front wheel", "wheel", "rubber", 0, Geo.torus(base / 2, r, 0f, r - 0.02f, 0.02f, "z", 32, 8));
        float bx = -base * 0.05f, by = r + 0.05f, sx = -base * 0.18f, sy = r + 0.55f, hx = base * 0.32f, hy = r + 0.6f;
        p.add("down tube", "frame", paint, 1, Geo.cylinder(bx, by, 0f, hx, hy - 0.05f, 0f, 0.018f, 8));
        p.add("seat tube", "frame", paint, 1, Geo.cylinder(bx, by, 0f, sx, sy, 0f, 0.018f, 8));
        p.add("top tube", "frame", paint, 1, Geo.cylinder(sx, sy, 0f, hx, hy, 0f, 0.016f, 8));
        p.add("chain stay", "frame", paint, 1, Geo.cylinder(bx, by, 0f, -base / 2, r, 0f, 0.012f, 8));
        p.add("seat stay", "frame", paint, 1, Geo.cylinder(sx, sy, 0f, -base / 2, r, 0f, 0.012f, 8));
        p.add("fork", "frame", paint, 1, Geo.cylinder(hx, hy, 0f, base / 2, r, 0f, 0.016f, 8));
        p.add("saddle", "saddle", "leather", 2, Geo.box(sx - 0.02f, sy + 0.06f, 0f, 0.24f, 0.05f, 0.12f));
        p.add("handlebar", "handlebar", "metal", 2, Geo.cylinder(hx + 0.02f, hy + 0.12f, -0.25f, hx + 0.02f, hy + 0.12f, 0.25f, 0.014f, 8));
        p.add("stem", "frame", "metal", 2, Geo.cylinder(hx, hy, 0f, hx + 0.02f, hy + 0.12f, 0f, 0.014f, 8));
        return "bicycle";
    }

    /**
     * A concept motorcycle: each tyre and rim is its own closed mesh.
     * This is not a measurement and it has no skeleton.
     */
    private static String motorcycle(ObjectSpec s, Parts p) {
        float radius = 0.335f, half = 0.72f;
        String paint = s.str("color", "black");
        p.add("rear tyre", "wheel", "rubber", 0, Geo.torus(-half, radius, 0f, radius - 0.035f, 0.035f, "z", 28, 8));
        p.add("front tyre", "wheel", "rubber", 0, Geo.torus(half, radius, 0f, radius - 0.035f, 0.035f, "z", 28, 8));
        p.add("rear rim", "rim", "metal", 0, Geo.torus(-half, radius, 0f, radius * 0.55f, 0.014f, "z", 24, 8));
        p.add("front rim", "rim", "metal", 0, Geo.torus(half, radius, 0f, radius * 0.55f, 0.014f, "z", 24, 8));
        float bx = -0.05f, by = radius + 0.08f, sx = -0.22f, sy = radius + 0.52f, hx = half - 0.08f, hy = radius + 0.62f;
        p.add("down tube", "frame", paint, 1, Geo.cylinder(bx, by, 0f, hx, hy - 0.08f, 0f, 0.02f, 8));
        p.add("seat tube", "frame", paint, 1, Geo.cylinder(bx, by, 0f, sx, sy, 0f, 0.02f, 8));
        p.add("top tube", "frame", paint, 1, Geo.cylinder(sx, sy, 0f, hx, hy, 0f, 0.018f, 8));
        p.add("chain stay", "frame", paint, 1, Geo.cylinder(bx, by, 0f, -half, radius, 0f, 0.014f, 8));
        p.add("seat stay", "frame", paint, 1, Geo.cylinder(sx, sy, 0f, -half, radius, 0f, 0.014f, 8));
        p.add("fork", "frame", paint, 1, Geo.cylinder(hx, hy, 0f, half, radius, 0f, 0.018f, 8));
        p.add("engine", "engine", "metal", 1, Geo.box(0.02f, radius + 0.02f, 0f, 0.36f, 0.28f, 0.22f));
        p.add("seat", "seat", "leather", 2, Geo.box(sx, sy + 0.06f, 0f, 0.42f, 0.06f, 0.16f));
        p.add("handlebar", "handlebar", "metal", 2, Geo.cylinder(hx, hy + 0.14f, -0.28f, hx, hy + 0.14f, 0.28f, 0.014f, 8));
        p.add("stem", "frame", "metal", 2, Geo.cylinder(hx, hy, 0f, hx, hy + 0.14f, 0f, 0.014f, 8));
        return "motorcycle";
    }

    /** A download bar: a frame and a fill that grows with "progress" (0..1). */
    private static String progress(ObjectSpec s, Parts p) {
        float f = clamp(s.num("progress", 0f), 0f, 1f), w = 3.2f, h = 0.32f;
        p.add("track", "track", "frame", 0, Geo.merge(
                Geo.box(0f, 0.02f, 0f, w + 0.08f, 0.04f, 0.08f), Geo.box(0f, h + 0.06f, 0f, w + 0.08f, 0.04f, 0.08f),
                Geo.box(-w / 2 - 0.02f, h / 2 + 0.04f, 0f, 0.04f, h + 0.08f, 0.08f), Geo.box(w / 2 + 0.02f, h / 2 + 0.04f, 0f, 0.04f, h + 0.08f, 0.08f)));
        float fw = Math.max(0.02f, w * f);
        p.add("fill", "fill", "light", 1, Geo.box(-w / 2 + fw / 2, h / 2 + 0.04f, 0f, fw, h, 0.06f));
        return Math.round(f * 100) + " percent";
    }

    /** Free composition: "count" shapes of "shape" stacked up (sphere, box, cylinder, cone, torus). */
    private static String shapes(ObjectSpec s, Parts p) {
        String[] list = s.str("shapes", "box").split(",");
        float y = 0f;
        int k = 0;
        for (String raw : list) {
            String shape = raw.trim();
            float size = Math.max(0.3f, 1f - k * 0.18f);
            TriMesh m;
            switch (shape) {
                case "sphere": m = Geo.sphere(0f, y + size / 2, 0f, size / 2, size / 2, 8); break;
                case "cylinder": m = Geo.cylinder(0f, y, 0f, 0f, y + size, 0f, size / 2, 20); break;
                case "cone": m = Geo.cone(0f, y, 0f, 0f, y + size, 0f, size / 2, 0f, 20); break;
                case "torus": m = Geo.torus(0f, y + size * 0.15f, 0f, size * 0.35f, size * 0.15f, "y", 24, 10); size = size * 0.3f; break;
                default: m = Geo.box(0f, y + size / 2, 0f, size, size, size);
            }
            p.add(shape + " " + (k + 1), shape, s.str("color", "white"), k, m);
            y += size * 0.98f;
            k++;
        }
        return s.str("title", list.length + " shapes");
    }

    static void tiltX(TriMesh m, float deg, float pivotX, float pivotY, float pivotZ) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), sn = (float) Math.sin(a);
        for (int i = 0; i < m.pos.length; i += 3) {
            float y = m.pos[i + 1] - pivotY, z = m.pos[i + 2] - pivotZ;
            m.pos[i + 1] = pivotY + y * c - z * sn;
            m.pos[i + 2] = pivotZ + y * sn + z * c;
        }
        m.computeNormals();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
