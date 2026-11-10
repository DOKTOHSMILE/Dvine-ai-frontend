package ai.ibiko.render.forms;


import ai.ibiko.stage.Sketch;

/**
 * Drawings as clouds of dots on curved 3D surfaces (no line drawing): the explanation drawings the model describes
 * become tubes, inflated solids and spheres. There are no built-in 3D objects: a 3D thing is built from the parts the
 * model describes ({@link ai.ibiko.render.field.SolidSpec}). Pure Java, y up.
 */
public final class Forms {
    private Forms() {
    }

    /** Stays inside the drawing swarm with room to spare. */
    static final int DRAW_BUDGET = 3900;

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

    /** Brightness for drawn and built shapes: lit faces and glowing edges both read clearly. */
    static float bright(float nx, float ny, float nz) {
        float lambert = Math.max(0f, nx * -0.5f + ny * 0.45f + nz * 0.74f);
        float rim = 1f - Math.abs(nz);
        return Math.min(1f, 0.3f + 0.7f * Math.max(lambert, rim * rim));
    }
}
