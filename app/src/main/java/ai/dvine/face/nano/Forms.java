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
            "cylinder", "heart"};

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
                default:
                    d.ball(0f, 0f, 0f, 0.9f, 0.04f, 0f, 1.2f, 1f);
            }
            d.fit(DRAW_BUDGET);
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
            float a = time * 0.4f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float tilt = 0.3f, ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
            int n = 0;
            for (int i = 0; i < d.n && n < max; i++) {
                if (d.at[i] > time - startedAt(time)) {
                    continue;
                }
                float x = d.xyz[i * 3], y = d.xyz[i * 3 + 1], z = d.xyz[i * 3 + 2];
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
        String t = lower.toLowerCase(java.util.Locale.US);
        if (!(t.startsWith("draw") || t.startsWith("show") || t.startsWith("explain") || t.startsWith("make")
                || t.startsWith("build"))) {
            return null;
        }
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
