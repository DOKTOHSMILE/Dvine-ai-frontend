package ai.dvine.face.nano;


import ai.dvine.face.Sketch;
import ai.dvine.face.WireHead;

/**
 * The shapes the nano swarm can take. Everything is a cloud of dots on curved
 * 3D surfaces (no line drawing): the living head, drawings that become tubes,
 * inflated solids and spheres, and turning 3D objects. Pure Java, y up.
 */
public final class Forms {
    private Forms() {
    }

    /** Stays inside the drawing swarm with room to spare. */
    static final int DRAW_BUDGET = 3900;

    // ------------------------------------------------------------ head

    /** The persona's sculpted head as nano dots, moving with speech, blinks and glances. */
    public static final class Head implements Swarm.Form {
        public final DotHead head;
        public final WireHead.Pose pose = new WireHead.Pose();

        public Head(DotHead head) {
            this.head = head;
        }

        public int size() {
            return head.size;
        }

        @Override
        public int fill(float[] xyz, float[] lit, int max, float time) {
            pose.time = time;
            return head.pose(pose, xyz, lit, max, 1, -9f);
        }
    }

    // ------------------------------------------------------------ dot lists

    /** Dots with a surface normal and the time each one appears. */
    static final class Dots {
        float[] xyz = new float[4096 * 3];
        float[] nor = new float[4096 * 3];
        float[] at = new float[4096];
        float[] glow = new float[4096];
        int n;

        void add(float x, float y, float z, float nx, float ny, float nz, float t, float g) {
            if (n == at.length) {
                int c = n * 2;
                xyz = java.util.Arrays.copyOf(xyz, c * 3);
                nor = java.util.Arrays.copyOf(nor, c * 3);
                at = java.util.Arrays.copyOf(at, c);
                glow = java.util.Arrays.copyOf(glow, c);
            }
            xyz[n * 3] = x;
            xyz[n * 3 + 1] = y;
            xyz[n * 3 + 2] = z;
            nor[n * 3] = nx;
            nor[n * 3 + 1] = ny;
            nor[n * 3 + 2] = nz;
            at[n] = t;
            glow[n] = g;
            n++;
        }

        /** A round tube of dots along a polyline in the z = 0 plane. */
        void tube(float[] pts, float radius, float start, float duration, float g) {
            float total = 0f;
            for (int i = 2; i + 1 < pts.length; i += 2) {
                total += (float) Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
            }
            float step = 0.03f;
            int ring = 6;
            float walked = 0f;
            int k = 0;
            for (int i = 2; i + 1 < pts.length; i += 2) {
                float x0 = pts[i - 2], y0 = pts[i - 1], x1 = pts[i], y1 = pts[i + 1];
                float seg = (float) Math.hypot(x1 - x0, y1 - y0);
                if (seg < 1e-5f) {
                    continue;
                }
                float tx = (x1 - x0) / seg, ty = (y1 - y0) / seg;
                int m = Math.max(1, (int) (seg / step));
                for (int j = 0; j < m; j++) {
                    float q = j / (float) m;
                    float cx = x0 + (x1 - x0) * q, cy = y0 + (y1 - y0) * q;
                    float p = total <= 0f ? 1f : (walked + seg * q) / total;
                    for (int r = 0; r < ring; r++) {
                        double a = (r + (k & 1) * 0.5) * Math.PI * 2 / ring;
                        float c = (float) Math.cos(a), s = (float) Math.sin(a);
                        // ring axis is perpendicular to the path, in the plane (-ty, tx, 0) and (0, 0, 1)
                        float nx = -ty * c, ny = tx * c, nz = s;
                        add(cx + nx * radius, cy + ny * radius, nz * radius, nx, ny, nz, start + duration * p, g);
                    }
                    k++;
                }
                walked += seg;
            }
        }

        /** A ball of dots, appearing from the top down. */
        void ball(float cx, float cy, float cz, float r, float spacing, float start, float duration, float g) {
            int count = Math.max(24, (int) (4 * Math.PI * r * r / (spacing * spacing)));
            for (int i = 0; i < count; i++) {
                float v = 1f - 2f * (i + 0.5f) / count;
                float rr = (float) Math.sqrt(1f - v * v);
                double a = i * 2.399963229728653;
                float nx = (float) Math.cos(a) * rr, nz = (float) Math.sin(a) * rr;
                add(cx + nx * r, cy + v * r, cz + nz * r, nx, v, nz, start + duration * (1f - v) * 0.5f, g);
            }
        }

        /**
         * An outline blown up like a cushion: dots on a curved front and back,
         * thickest in the middle and meeting at the edge.
         */
        void cushion(float[] pts, float start, float duration, float g) {
            float minX = 9f, maxX = -9f, minY = 9f, maxY = -9f;
            for (int i = 0; i + 1 < pts.length; i += 2) {
                minX = Math.min(minX, pts[i]);
                maxX = Math.max(maxX, pts[i]);
                minY = Math.min(minY, pts[i + 1]);
                maxY = Math.max(maxY, pts[i + 1]);
            }
            float step = 0.04f;
            // deepest point inside decides the thickness
            float deep = 0f;
            for (float y = minY; y <= maxY; y += step) {
                for (float x = minX; x <= maxX; x += step) {
                    if (inside(pts, x, y)) {
                        deep = Math.max(deep, edge(pts, x, y, null));
                    }
                }
            }
            if (deep <= 0f) {
                return;
            }
            float thick = Math.min(0.2f, deep * 0.85f);
            float[] dir = new float[2];
            int row = 0;
            for (float y = minY; y <= maxY; y += step, row++) {
                for (float x = minX + (row & 1) * step * 0.5f; x <= maxX; x += step) {
                    if (!inside(pts, x, y)) {
                        continue;
                    }
                    float e = edge(pts, x, y, dir);
                    float u = Math.min(1f, e / deep);
                    float h = (float) Math.sqrt(u * (2f - u));
                    float z = thick * h;
                    float slope = thick * (1f - u) / (deep * Math.max(0.05f, h));
                    float nx = dir[0] * slope, ny = dir[1] * slope;
                    float l = (float) Math.sqrt(nx * nx + ny * ny + 1f);
                    float t = start + duration * (0.7f + 0.3f * u);
                    add(x, y, z, nx / l, ny / l, 1f / l, t, g);
                    add(x, y, -z, nx / l, ny / l, -1f / l, t, g * 0.8f);
                }
            }
        }

        static boolean inside(float[] p, float x, float y) {
            boolean in = false;
            int m = p.length / 2;
            for (int i = 0, j = m - 1; i < m; j = i++) {
                float yi = p[i * 2 + 1], yj = p[j * 2 + 1];
                if ((yi > y) != (yj > y)) {
                    float xi = p[i * 2], xj = p[j * 2];
                    if (x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                        in = !in;
                    }
                }
            }
            return in;
        }

        /** Distance to the outline; {@code dir} gets the unit direction toward it. */
        static float edge(float[] p, float x, float y, float[] dir) {
            float best = 1e9f, bx = 0f, by = 0f;
            for (int i = 2; i + 1 < p.length; i += 2) {
                float ax = p[i - 2], ay = p[i - 1], ex = p[i] - ax, ey = p[i + 1] - ay;
                float l2 = ex * ex + ey * ey;
                float t = l2 <= 0f ? 0f : Math.max(0f, Math.min(1f, ((x - ax) * ex + (y - ay) * ey) / l2));
                float qx = ax + ex * t - x, qy = ay + ey * t - y;
                float d = qx * qx + qy * qy;
                if (d < best) {
                    best = d;
                    bx = qx;
                    by = qy;
                }
            }
            float d = (float) Math.sqrt(best);
            if (dir != null) {
                dir[0] = d > 0f ? bx / d : 0f;
                dir[1] = d > 0f ? by / d : 0f;
            }
            return d;
        }

        /** Keeps an even share of the dots when there are more than the swarm can hold. */
        void fit(int budget) {
            if (n <= budget) {
                return;
            }
            float keep = budget / (float) n;
            int m = 0;
            float acc = 0f;
            for (int i = 0; i < n; i++) {
                acc += keep;
                if (acc >= 1f) {
                    acc -= 1f;
                    System.arraycopy(xyz, i * 3, xyz, m * 3, 3);
                    System.arraycopy(nor, i * 3, nor, m * 3, 3);
                    at[m] = at[i];
                    glow[m] = glow[i];
                    m++;
                }
            }
            n = m;
        }
    }

    // ------------------------------------------------------------ drawings

    /**
     * An explanation drawing built in 3D, stroke by stroke as time passes:
     * lines become round tubes, closed outlines become cushioned solids,
     * circles become rings and balls become spheres. It sways gently so the
     * depth shows. Box -1..1.
     */
    public static final class Drawing implements Swarm.Form {
        private final Dots d = new Dots();
        public final Sketch sketch;
        private float started = -1f;

        public Drawing(Sketch s) {
            sketch = s;
            for (Sketch.Stroke st : s.strokes) {
                switch (st.kind) {
                    case LINE: {
                        float[] p = st.pts;
                        int m = p.length;
                        boolean closed = m >= 8 && Math.abs(p[0] - p[m - 2]) < 1e-3f && Math.abs(p[1] - p[m - 1]) < 1e-3f;
                        d.tube(p, 0.02f, st.start, st.duration, 1f);
                        if (closed) {
                            d.cushion(p, st.start, st.duration, 0.85f);
                        }
                        break;
                    }
                    case ARROW: {
                        float x0 = st.pts[0], y0 = st.pts[1], x1 = st.pts[2], y1 = st.pts[3];
                        d.tube(st.pts, 0.022f, st.start, st.duration, 1f);
                        double ang = Math.atan2(y1 - y0, x1 - x0);
                        for (int side = -1; side <= 1; side += 2) {
                            double b = ang + Math.PI - side * 0.5;
                            d.tube(new float[]{x1, y1, x1 + 0.13f * (float) Math.cos(b), y1 + 0.13f * (float) Math.sin(b)},
                                    0.02f, st.start + st.duration, 0.1f, 1f);
                        }
                        break;
                    }
                    case CIRCLE: {
                        float r = st.pts[2];
                        int k = Math.max(16, (int) (Math.PI * 2 * r / 0.03f));
                        float[] ring = new float[(k + 1) * 2];
                        for (int j = 0; j <= k; j++) {
                            double a = Math.PI / 2 - j * Math.PI * 2 / k;
                            ring[j * 2] = st.pts[0] + (float) Math.cos(a) * r;
                            ring[j * 2 + 1] = st.pts[1] + (float) Math.sin(a) * r;
                        }
                        d.tube(ring, 0.02f, st.start, st.duration, 1f);
                        break;
                    }
                    case DOT:
                        d.ball(st.pts[0], st.pts[1], 0f, st.pts[2], 0.022f, st.start, Math.max(0.12f, st.duration), 1f);
                        break;
                    case BALL:
                        d.ball(st.pts[0], st.pts[1], 0f, st.pts[2], 0.035f, st.start, st.duration, 0.9f);
                        break;
                    default:
                        break; // labels are drawn as glowing text by the painter
                }
            }
            d.fit(DRAW_BUDGET);
        }

        public int size() {
            return d.n;
        }

        @Override
        public int fill(float[] xyz, float[] lit, int max, float time) {
            if (started < 0f) {
                started = time;
            }
            float t = time - started;
            float a = (float) Math.toRadians(24f * Math.sin(t * 0.45f));
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            int n = 0;
            for (int i = 0; i < d.n && n < max; i++) {
                if (d.at[i] > t) {
                    continue; // not drawn yet: this particle waits in the gathering cloud
                }
                float x = d.xyz[i * 3], y = d.xyz[i * 3 + 1], z = d.xyz[i * 3 + 2];
                xyz[n * 3] = x * ca + z * sa;
                xyz[n * 3 + 1] = y;
                xyz[n * 3 + 2] = -x * sa + z * ca;
                float nx = d.nor[i * 3], nz = d.nor[i * 3 + 2];
                lit[n] = d.glow[i] * bright(nx * ca + nz * sa, d.nor[i * 3 + 1], -nx * sa + nz * ca);
                n++;
            }
            return n;
        }

        /** Seconds since the drawing started. */
        public float elapsed(float time) {
            return started < 0f ? 0f : time - started;
        }
    }

    // ------------------------------------------------------------ 3D objects

    /** The 3D objects the swarm can build. */
    public static final String[] OBJECTS = {"globe", "cube", "pyramid", "torus", "dna", "atom", "sphere", "cone",
            "cylinder", "heart",
            // advanced: built, and some of them alive (the butterfly flaps, the tesseract turns in 4D, ripples spread)
            "galaxy", "black hole", "tesseract", "lorenz", "fractal tree", "rocket", "butterfly", "eiffel tower",
            "brain", "mobius", "saturn", "ripples"};

    /** Slowly turning solid 3D objects with curved, shaded surfaces. Radius about 1. */
    public static final class Object3D implements Swarm.Form {
        public final String kind;
        private final Dots d = new Dots();

        public Object3D(String kind) {
            this.kind = kind;
            switch (kind) {
                case "globe":
                    d.ball(0f, 0f, 0f, 0.92f, 0.055f, 0f, 1.2f, 0.7f);
                    for (int lat = -2; lat <= 2; lat++) {
                        float y = lat * 0.3f;
                        float r = (float) Math.sqrt(1 - y * y / 0.9f) * 0.94f;
                        for (int j = 0; j < 90; j++) {
                            double a = j * Math.PI * 2 / 90;
                            float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                            d.add(x, y, z, x, y, z, 1.2f, 1f);
                        }
                    }
                    for (int lon = 0; lon < 6; lon++) {
                        double b = lon * Math.PI / 6;
                        for (int j = 0; j < 90; j++) {
                            double a = j * Math.PI * 2 / 90;
                            float x = (float) (Math.cos(a) * Math.cos(b)) * 0.94f, y = (float) Math.sin(a) * 0.94f,
                                    z = (float) (Math.cos(a) * Math.sin(b)) * 0.94f;
                            d.add(x, y, z, x, y, z, 1.2f, 1f);
                        }
                    }
                    break;
                case "cube":
                    sculpt(new Sculpt().box(0f, 0f, 0f, 0.68f, 0.68f, 0.68f, 0.12f), 2000);
                    break;
                case "pyramid":
                    pyramid();
                    break;
                case "torus":
                    sculpt(new Sculpt().torus(0f, 0f, 0f, 0.66f, 0.27f), 2400);
                    break;
                case "cone":
                    sculpt(new Sculpt().capsule(0f, -0.62f, 0f, 0.62f, 0f, 0.8f, 0f, 0.01f), 2000);
                    break;
                case "cylinder":
                    sculpt(new Sculpt().blend(0f).box(0f, 0f, 0f, 0.5f, 0.75f, 0.5f, 0.49f), 2200);
                    break;
                case "heart":
                    sculpt(new Sculpt().blend(0.12f).ellipsoid(-0.33f, 0.3f, 0f, 0.38f, 0.36f, 0.3f).ellipsoid(0.33f, 0.3f, 0f, 0.38f, 0.36f, 0.3f)
                            .capsule(-0.42f, 0.14f, 0f, 0.26f, 0f, -0.75f, 0f, 0.03f)
                            .capsule(0.42f, 0.14f, 0f, 0.26f, 0f, -0.75f, 0f, 0.03f), 2400);
                    break;
                case "dna":
                    for (int i = 0; i < 300; i++) {
                        float y = -1f + 2f * i / 300f;
                        double a = y * Math.PI * 3;
                        for (int s = 0; s < 2; s++) {
                            double b = a + s * Math.PI;
                            float cx = 0.42f * (float) Math.cos(b), cz = 0.42f * (float) Math.sin(b);
                            for (int r = 0; r < 5; r++) {
                                double c = r * Math.PI * 2 / 5 + i;
                                float nx = (float) Math.cos(c) * (float) Math.cos(b), ny = (float) Math.sin(c),
                                        nz = (float) Math.cos(c) * (float) Math.sin(b);
                                d.add(cx + nx * 0.05f, y + ny * 0.05f, cz + nz * 0.05f, nx, ny, nz, (y + 1f) * 0.8f, 1f);
                            }
                        }
                        if (i % 14 == 0) {
                            for (int j = 1; j < 14; j++) {
                                float q = j / 14f;
                                float x = 0.42f * (float) Math.cos(a) * (1 - 2 * q), z = 0.42f * (float) Math.sin(a) * (1 - 2 * q);
                                d.add(x, y, z, 0f, 0f, 1f, (y + 1f) * 0.8f + 0.2f, 0.65f);
                            }
                        }
                    }
                    break;
                case "atom": {
                    for (int i = 0; i < 9; i++) {
                        float x = Swarm.noise(i * 3) * 0.12f, y = Swarm.noise(i * 3 + 1) * 0.12f, z = Swarm.noise(i * 3 + 2) * 0.12f;
                        d.ball(x, y, z, 0.085f, 0.026f, 0f, 0.6f, i % 2 == 0 ? 1f : 0.7f);
                    }
                    for (int o = 0; o < 3; o++) {
                        double tilt = o * Math.PI / 3;
                        for (int j = 0; j < 200; j++) {
                            double a = j * Math.PI * 2 / 200;
                            float x = 0.92f * (float) Math.cos(a), y = 0.3f * (float) Math.sin(a);
                            float px = x * (float) Math.cos(tilt) - y * (float) Math.sin(tilt);
                            float py = x * (float) Math.sin(tilt) + y * (float) Math.cos(tilt);
                            float pz = 0.3f * (float) Math.sin(a + o);
                            d.add(px, py, pz, px, py, pz, 0.6f + j / 200f, 0.8f);
                        }
                        double e = o * 2.1;
                        float x = 0.92f * (float) Math.cos(e), y = 0.3f * (float) Math.sin(e);
                        d.ball(x * (float) Math.cos(tilt) - y * (float) Math.sin(tilt), x * (float) Math.sin(tilt) + y * (float) Math.cos(tilt),
                                0.3f * (float) Math.sin(e + o), 0.05f, 0.02f, 1.6f, 0.2f, 1f);
                    }
                    break;
                }
                case "galaxy":
                    galaxy();
                    break;
                case "black hole":
                    blackHole();
                    break;
                case "tesseract":
                    break; // drawn fresh every frame (see fill)
                case "lorenz":
                    lorenz();
                    break;
                case "fractal tree":
                    branch(0f, -0.95f, 0f, 0f, 1f, 0f, 0.55f, 0.045f, 0, 0f);
                    break;
                case "rocket":
                    rocket();
                    break;
                case "butterfly":
                    butterfly();
                    break;
                case "eiffel tower":
                    eiffel();
                    break;
                case "brain":
                    brain();
                    break;
                case "mobius":
                    mobius();
                    break;
                case "saturn":
                    d.ball(0f, 0f, 0f, 0.5f, 0.04f, 0f, 1.2f, 1f);
                    for (int i = 0; i < 1500; i++) {
                        float r = 0.68f + 0.42f * (i % 30) / 30f;
                        if (r > 0.88f && r < 0.92f) {
                            continue; // the Cassini division
                        }
                        double a = i * 2.39996;
                        float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                        float y = 0.24f * x; // rings tilted against the planet's orbit
                        d.add(x, y, z, 0f, 1f, 0f, 1.2f + r * 0.6f, 0.75f);
                    }
                    break;
                case "ripples":
                    for (int i = 0; i < 52; i++) {
                        for (int j = 0; j < 52; j++) {
                            float x = -1f + 2f * i / 51f, z = -1f + 2f * j / 51f;
                            if (x * x + z * z > 1f) {
                                continue;
                            }
                            d.add(x, 0f, z, 0f, 1f, 0f, (float) Math.hypot(x, z) * 1.2f, 0.9f);
                        }
                    }
                    break;
                default:
                    d.ball(0f, 0f, 0f, 0.9f, 0.04f, 0f, 1.2f, 1f);
            }
            if (d.n > 0) {
                d.fit(DRAW_BUDGET);
            }
        }

        // ------------------------------------------------------------ advanced builds

        /** Dots along a 3D polyline (a thin tube), drawn in order from start over duration. */
        private void path(float[] p, float radius, float start, float duration, float g) {
            float total = 0f;
            for (int i = 3; i < p.length; i += 3) {
                total += len(p[i] - p[i - 3], p[i + 1] - p[i - 2], p[i + 2] - p[i - 1]);
            }
            float walked = 0f;
            int k = 0;
            for (int i = 3; i < p.length; i += 3) {
                float dx = p[i] - p[i - 3], dy = p[i + 1] - p[i - 2], dz = p[i + 2] - p[i - 1];
                float seg = len(dx, dy, dz);
                if (seg < 1e-5f) {
                    continue;
                }
                dx /= seg;
                dy /= seg;
                dz /= seg;
                // two directions across the path
                float ax = Math.abs(dy) < 0.9f ? 0f : 1f, ay = Math.abs(dy) < 0.9f ? 1f : 0f;
                float ux = dy * 0f - dz * ay, uy = dz * ax - dx * 0f, uz = dx * ay - dy * ax;
                float ul = len(ux, uy, uz);
                ux /= ul;
                uy /= ul;
                uz /= ul;
                float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
                int m = Math.max(1, (int) (seg / 0.028f));
                for (int j = 0; j < m; j++) {
                    float q = j / (float) m;
                    float cx = p[i - 3] + (p[i] - p[i - 3]) * q, cy = p[i - 2] + (p[i + 1] - p[i - 2]) * q,
                            cz = p[i - 1] + (p[i + 2] - p[i - 1]) * q;
                    float at = start + duration * (total <= 0f ? 1f : (walked + seg * q) / total);
                    int ring = radius > 0.02f ? 4 : 2;
                    for (int r = 0; r < ring; r++) {
                        double a = (r + (k & 1) * 0.5) * Math.PI * 2 / ring;
                        float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                        float nx = ux * c + vx * sn, ny = uy * c + vy * sn, nz = uz * c + vz * sn;
                        d.add(cx + nx * radius, cy + ny * radius, cz + nz * radius, nx, ny, nz, at, g);
                    }
                    k++;
                }
                walked += seg;
            }
        }

        private static float len(float x, float y, float z) {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        /** Two spiral arms of stars around a bright bulge (a barred spiral seen from above, tilted). */
        private void galaxy() {
            for (int i = 0; i < 260; i++) {
                float r = 0.16f * (float) Math.cbrt((Swarm.noise(i * 5) + 1f) * 0.5f);
                float x = Swarm.noise(i * 5 + 1), y = Swarm.noise(i * 5 + 2) * 0.5f, z = Swarm.noise(i * 5 + 3);
                float l = len(x, y, z) + 1e-4f;
                d.add(x / l * r, y / l * r, z / l * r, 0f, 1f, 0f, 0.2f, 1f);
            }
            for (int i = 0; i < 2600; i++) {
                int arm = i % 2;
                float u = (float) Math.sqrt((i / 2) / 1300f);
                float r = 0.14f + 0.86f * u;
                double a = arm * Math.PI + Math.log(r / 0.14f) * 2.2;
                float spread = 0.07f + 0.1f * u;
                float x = (float) Math.cos(a) * r + Swarm.noise(i * 3 + 7) * spread;
                float z = (float) Math.sin(a) * r + Swarm.noise(i * 3 + 8) * spread;
                float y = Swarm.noise(i * 3 + 9) * 0.04f * (1.2f - u);
                d.add(x, y, z, 0f, 1f, 0f, 0.4f + 2.2f * u, 1f - 0.45f * u);
            }
        }

        /** A dark sphere (the horizon is the empty middle), a photon ring, a tilted accretion disk and its lensed image. */
        private void blackHole() {
            for (int i = 0; i < 1800; i++) {
                float r = 0.42f + 0.6f * (float) Math.pow((i % 60) / 60f, 1.4f);
                double a = i * 2.39996;
                float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                d.add(x, 0f, z, 0f, 1f, 0f, 0.3f + r, 1.1f - 0.6f * (r - 0.42f));
            }
            for (int i = 0; i < 260; i++) {
                double a = i * Math.PI * 2 / 260;
                float x = (float) Math.cos(a) * 0.33f, y = (float) Math.sin(a) * 0.33f;
                d.add(x, y, 0f, x, y, 1f, 0.1f, 1.2f); // photon ring, facing the viewer
            }
            for (int i = 0; i < 500; i++) {
                double a = i * Math.PI / 500;
                float r = 0.45f + 0.08f * (i % 5);
                float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r * 0.85f;
                d.add(x, y, -0.05f, 0f, 0f, 1f, 1.6f, 0.55f); // the far side of the disk, bent over the top
            }
        }

        /** The Lorenz attractor: one chaotic path that never repeats, drawn as it unfolds. */
        private void lorenz() {
            double x = 0.1, y = 0, z = 0;
            int steps = 9000;
            float[] p = new float[(steps / 6) * 3];
            int k = 0;
            for (int i = 0; i < steps; i++) {
                double dx = 10 * (y - x), dy = x * (28 - z) - y, dz = x * y - 8.0 / 3.0 * z;
                x += dx * 0.005;
                y += dy * 0.005;
                z += dz * 0.005;
                if (i % 6 == 0 && k + 2 < p.length) {
                    p[k++] = (float) x / 22f;
                    p[k++] = (float) (z - 25) / 24f;
                    p[k++] = (float) y / 22f;
                }
            }
            path(java.util.Arrays.copyOf(p, k), 0.006f, 0f, 7f, 1f);
        }

        /** A branching tree in 3D: every branch splits in three, turning around the trunk. */
        private void branch(float x, float y, float z, float dx, float dy, float dz, float length, float radius, int depth,
                            float start) {
            float ex = x + dx * length, ey = y + dy * length, ez = z + dz * length;
            float dur = 0.5f;
            path(new float[]{x, y, z, ex, ey, ez}, radius, start, dur, depth > 5 ? 1.2f : 0.9f);
            if (depth == 6) {
                return;
            }
            for (int b = 0; b < 3; b++) {
                double turn = b * Math.PI * 2 / 3 + depth * 0.9;
                // tilt the direction 32 degrees away from itself, around the parent's axis
                float px = Math.abs(dy) < 0.9f ? 0f : 1f, py = Math.abs(dy) < 0.9f ? 1f : 0f;
                float ux = py * dz, uy = -px * dz, uz = px * dy - py * dx;
                float ul = len(ux, uy, uz);
                ux /= ul;
                uy /= ul;
                uz /= ul;
                float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
                float c = (float) Math.cos(turn), sn = (float) Math.sin(turn);
                float sx = ux * c + vx * sn, sy = uy * c + vy * sn, sz = uz * c + vz * sn;
                float tilt = 0.56f;
                float nx = dx * (float) Math.cos(tilt) + sx * (float) Math.sin(tilt);
                float ny = dy * (float) Math.cos(tilt) + sy * (float) Math.sin(tilt) + 0.08f;
                float nz = dz * (float) Math.cos(tilt) + sz * (float) Math.sin(tilt);
                float nl = len(nx, ny, nz);
                branch(ex, ey, ez, nx / nl, ny / nl, nz / nl, length * 0.68f, radius * 0.62f, depth + 1, start + dur);
            }
        }

        private void rocket() {
            Sculpt s = new Sculpt().blend(0.04f)
                    .capsule(0f, -0.55f, 0f, 0.2f, 0f, 0.35f, 0f, 0.2f)
                    .capsule(0f, 0.35f, 0f, 0.2f, 0f, 0.82f, 0f, 0.01f);
            for (int f = 0; f < 4; f++) {
                double a = f * Math.PI / 2;
                float x = (float) Math.cos(a) * 0.25f, z = (float) Math.sin(a) * 0.25f;
                s.box(x, -0.55f, z, 0.1f, 0.17f, 0.012f, (float) Math.toDegrees(-a));
            }
            sculpt(s, 2300);
            for (int i = 0; i < 420; i++) { // a porthole and the exhaust plume
                double a = i * Math.PI * 2 / 60;
                if (i < 60) {
                    d.add((float) Math.cos(a) * 0.08f, 0.3f + (float) Math.sin(a) * 0.08f, 0.205f, 0f, 0f, 1f, 1.5f, 1.2f);
                } else {
                    float u = (i - 60) / 360f;
                    float r = 0.05f + 0.13f * u;
                    d.add((float) Math.cos(i * 2.4) * r, -0.78f - 0.35f * u, (float) Math.sin(i * 2.4) * r, 0f, -1f, 0f, 1.8f + u,
                            1.1f - u * 0.6f);
                }
            }
        }

        /** Wings from the polar butterfly curve, filled with dots, on a slim body; they flap in fill(). */
        private void butterfly() {
            int k = 0;
            for (int ring = 1; ring <= 9; ring++) {
                float scale = ring / 9f;
                for (int i = 0; i < 300; i++) {
                    double t = i * Math.PI * 2 / 300;
                    double r = Math.exp(Math.sin(t)) - 2 * Math.cos(4 * t) + Math.pow(Math.sin((2 * t - Math.PI) / 24), 5);
                    float x = (float) (Math.sin(t) * r) * 0.27f * scale, y = -(float) (Math.cos(t) * r) * 0.27f * scale + 0.05f; // big wings on top
                    if (Math.abs(x) < 0.03f) {
                        continue;
                    }
                    d.add(x, y, 0f, 0f, 0f, 1f, 0.2f * ring, ring == 9 ? 1.2f : 0.55f + 0.04f * ring);
                    k++;
                }
            }
            path(new float[]{0f, -0.55f, 0f, 0f, 0.4f, 0f}, 0.03f, 0f, 0.6f, 1f);
            path(new float[]{0f, 0.4f, 0f, 0.12f, 0.75f, 0.02f}, 0.006f, 1.8f, 0.5f, 1f);
            path(new float[]{0f, 0.4f, 0f, -0.12f, 0.75f, 0.02f}, 0.006f, 1.8f, 0.5f, 1f);
        }

        /** Four curved lattice legs, three platforms and a spire. */
        private void eiffel() {
            float base = -0.95f;
            for (int leg = 0; leg < 4; leg++) {
                float sx = leg == 0 || leg == 3 ? 1f : -1f, sz = leg < 2 ? 1f : -1f;
                float[] p = new float[40 * 3];
                for (int i = 0; i < 40; i++) {
                    float u = i / 39f;
                    float w = 0.55f * (float) Math.exp(-3.2f * u) + 0.03f;
                    p[i * 3] = sx * w;
                    p[i * 3 + 1] = base + 1.75f * u;
                    p[i * 3 + 2] = sz * w;
                }
                path(p, 0.012f, 0f, 2.4f, 1f);
            }
            float[] levels = {0.12f, 0.33f, 0.62f};
            for (int l = 0; l < levels.length; l++) {
                float y = base + 1.75f * levels[l];
                float w = 0.55f * (float) Math.exp(-3.2f * levels[l]) + 0.05f;
                path(new float[]{w, y, w, -w, y, w, -w, y, -w, w, y, -w, w, y, w}, 0.01f, 0.6f + 0.6f * l, 0.5f, 1.2f);
            }
            for (int side = 0; side < 4; side++) { // cross-bracing between the legs
                for (int b = 0; b < 7; b++) {
                    float u0 = 0.62f * b / 7f, u1 = 0.62f * (b + 1) / 7f; // from the ground to the second platform
                    float w0 = 0.55f * (float) Math.exp(-3.2f * u0) + 0.03f, w1 = 0.55f * (float) Math.exp(-3.2f * u1) + 0.03f;
                    float y0 = base + 1.75f * u0, y1 = base + 1.75f * u1;
                    float[][] c0 = corners(w0), c1 = corners(w1);
                    float[] a0 = c0[side], b0 = c0[(side + 1) % 4], a1 = c1[side], b1 = c1[(side + 1) % 4];
                    path(new float[]{a0[0], y0, a0[1], b1[0], y1, b1[1]}, 0.004f, 1.2f + b * 0.15f, 0.3f, 0.7f);
                    path(new float[]{b0[0], y0, b0[1], a1[0], y1, a1[1]}, 0.004f, 1.2f + b * 0.15f, 0.3f, 0.7f);
                }
            }
            path(new float[]{0f, base + 1.75f, 0f, 0f, base + 1.95f, 0f}, 0.008f, 2.6f, 0.4f, 1.3f);
        }

        private static float[][] corners(float w) {
            return new float[][]{{w, w}, {-w, w}, {-w, -w}, {w, -w}};
        }

        /** Two hemispheres covered in folds (gyri), the cerebellum behind and the brain stem below. */
        private void brain() {
            for (int side = -1; side <= 1; side += 2) {
                for (int fold = 0; fold < 15; fold++) {
                    float[] p = new float[121 * 3];
                    for (int i = 0; i <= 120; i++) {
                        double lon = -Math.PI * 0.95 + Math.PI * 1.9 * i / 120;
                        double lat = -1.0 + 2.1 * fold / 14.0 + 0.09 * Math.sin(lon * 9 + fold * 1.7);
                        float cx = (float) (Math.cos(lat) * Math.sin(lon)), cy = (float) Math.sin(lat), cz = (float) (Math.cos(lat) * Math.cos(lon));
                        // half an ellipsoid each side of the midline gap
                        float x = side * (0.03f + 0.4f * Math.abs(cx * 0.5f + 0.5f)), y = 0.48f * cy + 0.1f, z = 0.75f * cz;
                        p[i * 3] = x;
                        p[i * 3 + 1] = y;
                        p[i * 3 + 2] = z;
                    }
                    path(p, 0.004f, fold * 0.15f, 0.8f, 1f);
                }
            }
            sculpt(new Sculpt().ellipsoid(0f, -0.33f, -0.48f, 0.3f, 0.15f, 0.2f), 260);
            path(new float[]{0f, -0.25f, -0.25f, 0f, -0.55f, -0.33f, 0f, -0.85f, -0.36f}, 0.06f, 2.2f, 0.6f, 0.8f);
        }

        private void mobius() {
            for (int i = 0; i < 160; i++) {
                double u = i * Math.PI * 2 / 160;
                for (int j = 0; j < 14; j++) {
                    float v = -0.28f + 0.56f * j / 13f;
                    float x = (float) ((0.75 + v * Math.cos(u / 2)) * Math.cos(u));
                    float y = (float) (v * Math.sin(u / 2));
                    float z = (float) ((0.75 + v * Math.cos(u / 2)) * Math.sin(u));
                    d.add(x, y, z, (float) Math.cos(u), 0.4f, (float) Math.sin(u), (float) u * 0.45f,
                            j == 0 || j == 13 ? 1.2f : 0.7f);
                }
            }
        }

        private void sculpt(Sculpt s, int count) {
            Sculpt.Cloud c = s.sample(count, 9);
            for (int i = 0; i < c.size; i++) {
                float y = c.xyz[i * 3 + 1];
                d.add(c.xyz[i * 3], y, c.xyz[i * 3 + 2], c.normal[i * 3], c.normal[i * 3 + 1], c.normal[i * 3 + 2],
                        (0.9f - y) * 0.8f, 1f);
            }
        }

        private void pyramid() {
            float[][] v = {{-0.8f, -0.6f, -0.8f}, {0.8f, -0.6f, -0.8f}, {0.8f, -0.6f, 0.8f}, {-0.8f, -0.6f, 0.8f}, {0f, 0.85f, 0f}};
            int[][] faces = {{0, 1, 4}, {1, 2, 4}, {2, 3, 4}, {3, 0, 4}, {0, 2, 1}, {0, 3, 2}};
            int seed = 1;
            for (int[] f : faces) {
                float[] a = v[f[0]], b = v[f[1]], c = v[f[2]];
                float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
                float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
                float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                float area = l * 0.5f;
                int k = (int) (area / (0.045f * 0.045f));
                for (int i = 0; i < k; i++) {
                    float r1 = (float) Math.sqrt((Swarm.noise(seed++) + 1f) * 0.5f), r2 = (Swarm.noise(seed++) + 1f) * 0.5f;
                    float p = 1f - r1, q = r1 * (1f - r2), s = r1 * r2;
                    float x = a[0] * p + b[0] * q + c[0] * s, y = a[1] * p + b[1] * q + c[1] * s, z = a[2] * p + b[2] * q + c[2] * s;
                    d.add(x, y, z, nx / l, ny / l, nz / l, (0.9f - y) * 0.8f, 0.85f);
                }
            }
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {0, 4}, {1, 4}, {2, 4}, {3, 4}};
            for (int[] e : edges) {
                float[] a = v[e[0]], b = v[e[1]];
                for (int j = 0; j <= 50; j++) {
                    float t = j / 50f;
                    float x = a[0] + (b[0] - a[0]) * t, y = a[1] + (b[1] - a[1]) * t, z = a[2] + (b[2] - a[2]) * t;
                    float l = (float) Math.sqrt(x * x + z * z) + 1e-4f;
                    d.add(x, y, z, x / l, 0.3f, z / l, (0.9f - y) * 0.8f, 1f);
                }
            }
        }

        public int size() {
            return d.n;
        }

        @Override
        public int fill(float[] xyz, float[] out, int max, float time) {
            if (kind.equals("tesseract")) {
                return tesseract(xyz, out, max, time - startedAt(time));
            }
            float since = time - startedAt(time);
            boolean flap = kind.equals("butterfly"), ripple = kind.equals("ripples");
            float wing = flap ? 0.65f * (float) Math.sin(since * 2.6f) : 0f;
            // flat things lean toward the viewer; the black hole's rings and the butterfly face us
            boolean flat = kind.equals("galaxy") || kind.equals("saturn") || kind.equals("ripples");
            boolean facing = kind.equals("black hole") || flap;
            float a = facing ? 0.45f * (float) Math.sin(time * 0.5f) * (flap ? 1f : 0f) : time * 0.4f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float tilt = flat ? 0.75f : kind.equals("black hole") ? 0.22f : 0.3f;
            float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
            int n = 0;
            for (int i = 0; i < d.n && n < max; i++) {
                if (d.at[i] > time - startedAt(time)) {
                    continue;
                }
                float x = d.xyz[i * 3], y = d.xyz[i * 3 + 1], z = d.xyz[i * 3 + 2];
                if (flap && Math.abs(x) > 0.035f) { // each wing turns about the body
                    float ang = Math.signum(x) * wing;
                    float r = Math.abs(x);
                    x = Math.signum(x) * r * (float) Math.cos(ang);
                    z = z + r * (float) Math.sin(ang);
                } else if (ripple) {
                    float r = (float) Math.hypot(x, z);
                    y = 0.16f * (float) Math.sin(r * 14f - since * 4f) * (float) Math.exp(-r * 1.6f) - 0.2f;
                }
                float x1 = x * ca + z * sa, z1 = -x * sa + z * ca;
                xyz[n * 3] = x1;
                xyz[n * 3 + 1] = y * ct - z1 * st;
                xyz[n * 3 + 2] = y * st + z1 * ct;
                float nx = d.nor[i * 3], ny = d.nor[i * 3 + 1], nz = d.nor[i * 3 + 2];
                float mx = nx * ca + nz * sa, mz = -nx * sa + nz * ca;
                out[n] = d.glow[i] * bright(mx, ny * ct - mz * st, ny * st + mz * ct);
                n++;
            }
            return n;
        }

        /** A 4D hypercube turning in two planes at once, projected into 3D, then onto the screen. */
        private int tesseract(float[] xyz, float[] out, int max, float t) {
            float a = t * 0.6f, b = t * 0.37f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            float[][] v = new float[16][3];
            for (int i = 0; i < 16; i++) {
                float x = (i & 1) == 0 ? -0.5f : 0.5f, y = (i & 2) == 0 ? -0.5f : 0.5f, z = (i & 4) == 0 ? -0.5f : 0.5f,
                        w = (i & 8) == 0 ? -0.5f : 0.5f;
                float x1 = x * ca - w * sa, w1 = x * sa + w * ca;   // turn in the x-w plane
                float y1 = y * cb - w1 * sb, w2 = y * sb + w1 * cb; // and in the y-w plane
                float k = 1.6f / (2.2f - w2);                       // 4D perspective
                v[i][0] = x1 * k;
                v[i][1] = y1 * k;
                v[i][2] = z * k;
            }
            int n = 0, per = 55;
            for (int i = 0; i < 16; i++) {
                for (int bit = 1; bit < 16; bit <<= 1) {
                    int j = i | bit;
                    if (j == i) {
                        continue;
                    }
                    for (int s = 0; s <= per && n < max; s++) {
                        float q = s / (float) per;
                        if (t < 0.03f * (i + q * 2)) {
                            continue; // edges appear one after another
                        }
                        xyz[n * 3] = v[i][0] + (v[j][0] - v[i][0]) * q;
                        xyz[n * 3 + 1] = v[i][1] + (v[j][1] - v[i][1]) * q;
                        xyz[n * 3 + 2] = v[i][2] + (v[j][2] - v[i][2]) * q;
                        out[n] = (i & 8) == (j & 8) && (i & 8) != 0 ? 0.65f : 1f;
                        n++;
                    }
                }
            }
            return n;
        }

        private float started = -1f;

        private float startedAt(float time) {
            if (started < 0f) {
                started = time;
            }
            return started;
        }
    }

    /** Brightness for drawn and built shapes: lit faces and glowing edges both read clearly. */
    static float bright(float nx, float ny, float nz) {
        float lambert = Math.max(0f, nx * -0.5f + ny * 0.45f + nz * 0.74f);
        float rim = 1f - Math.abs(nz);
        return Math.min(1f, 0.3f + 0.7f * Math.max(lambert, rim * rim));
    }

    /** Which 3D object a request names, or null. */
    public static String objectFor(String lower) {
        if (lower == null) {
            return null;
        }
        String t = lower.toLowerCase(java.util.Locale.US).replaceAll("\\?+$", "")
                .replaceAll("^((please|can you|could you|will you|would you|now|go ahead and|i want you to) )+", "");
        if (!(t.startsWith("draw") || t.startsWith("show") || t.startsWith("explain") || t.startsWith("make")
                || t.startsWith("build"))) {
            return null;
        }
        if (t.contains("black hole")) return "black hole";
        if (t.contains("galaxy") || t.contains("milky way")) return "galaxy";
        if (t.contains("tesseract") || t.contains("hypercube") || t.contains("4d") || t.contains("fourth dimension")) return "tesseract";
        if (t.contains("lorenz") || t.contains("attractor") || t.contains("chaos") || t.contains("butterfly effect")) return "lorenz";
        if (t.contains("fractal")) return "fractal tree";
        if (t.contains("rocket") || t.contains("spaceship") || t.contains("space ship")) return "rocket";
        if (t.contains("butterfly")) return "butterfly";
        if (t.contains("eiffel")) return "eiffel tower";
        if (t.contains("brain")) return "brain";
        if (t.contains("mobius") || t.contains("möbius") || t.contains("moebius")) return "mobius";
        if (t.contains("saturn") || t.contains("planet with rings")) return "saturn";
        if (t.contains("ripple") || t.contains("waves") || t.contains("a wave")) return "ripples";
        if (t.contains("globe") || t.contains("earth") || t.contains("world")) return "globe";
        if (t.contains("cube") || t.contains("box")) return "cube";
        if (t.contains("pyramid")) return "pyramid";
        if (t.contains("torus") || t.contains("donut") || t.contains("doughnut")) return "torus";
        if (t.contains("dna") || t.contains("helix")) return "dna";
        if (t.contains("atom")) return "atom";
        if (t.contains("cone")) return "cone";
        if (t.contains("cylinder") || t.contains("tube")) return "cylinder";
        if (t.contains("3d heart") || t.contains("solid heart")) return "heart";
        if (t.contains("sphere") || t.contains("ball")) return "sphere";
        return null;
    }
}
