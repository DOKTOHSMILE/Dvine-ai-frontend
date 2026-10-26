package ai.dvine.face.nano;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.face.Sketch;
import ai.dvine.face.WireHead;

/** The shapes the nano swarm can take. All pure Java, model space, y up. */
public final class Forms {
    private Forms() {
    }

    // ------------------------------------------------------------ head

    /** The persona's head as nano dots: skin samples, bright feature lines, hair strands. */
    public static final class Head implements Swarm.Form {
        public final WireHead head;
        public final WireHead.Pose pose = new WireHead.Pose();
        private final float[] deformed;
        private final float scale;
        private final float ox;
        private final float oy;
        private final int stride;

        public Head(WireHead head) {
            this(head, 1f, 0f, 0f, 1);
        }

        Head(WireHead head, float scale, float ox, float oy, int stride) {
            this.head = head;
            this.stride = stride;
            this.deformed = new float[head.rest.length];
            this.scale = scale;
            this.ox = ox;
            this.oy = oy;
        }

        /** Number of points this head produces (stable frame to frame). */
        public int size() {
            int n = head.dotTri.length;
            for (int g : head.edgeGroup) {
                if (g == WireHead.FEATURE) n += 4;
                else if (g == WireHead.HAIR) n += 3;
            }
            return n;
        }

        @Override
        public int fill(float[] xyz, float[] lit, int max, float time) {
            pose.time = time;
            head.deform(pose, deformed);
            int n = 0;
            int[] tris = head.tris;
            for (int d = 0; d < head.dotTri.length && n < max; d += stride) {
                int t = head.dotTri[d] * 3;
                float r1 = head.dotBary[d * 2];
                float r2 = head.dotBary[d * 2 + 1];
                float r0 = 1f - r1 - r2;
                int a = tris[t] * 3, b = tris[t + 1] * 3, c = tris[t + 2] * 3;
                put(xyz, n, deformed[a] * r0 + deformed[b] * r1 + deformed[c] * r2,
                        deformed[a + 1] * r0 + deformed[b + 1] * r1 + deformed[c + 1] * r2,
                        deformed[a + 2] * r0 + deformed[b + 2] * r1 + deformed[c + 2] * r2);
                lit[n] = 0.5f;
                n++;
            }
            int[] edges = head.edges;
            for (int e = 0; e < head.edgeGroup.length && n < max; e++) {
                int g = head.edgeGroup[e];
                int k = g == WireHead.FEATURE ? (stride > 1 ? 2 : 4) : g == WireHead.HAIR ? (stride > 1 ? 1 : 3) : 0;
                int a = edges[e * 2] * 3, b = edges[e * 2 + 1] * 3;
                for (int s = 0; s < k && n < max; s++) {
                    float q = (s + 0.5f) / k;
                    put(xyz, n, deformed[a] + (deformed[b] - deformed[a]) * q,
                            deformed[a + 1] + (deformed[b + 1] - deformed[a + 1]) * q,
                            deformed[a + 2] + (deformed[b + 2] - deformed[a + 2]) * q);
                    lit[n] = g == WireHead.FEATURE ? 1f : 0.7f;
                    n++;
                }
            }
            return n;
        }

        private void put(float[] xyz, int i, float x, float y, float z) {
            xyz[i * 3] = x * scale + ox;
            xyz[i * 3 + 1] = y * scale + oy;
            xyz[i * 3 + 2] = z * scale;
        }
    }

    // ------------------------------------------------------------ full body

    /**
     * Full standing figure: the same living head (scaled), with neck, torso,
     * arms, hands and legs built from sampled capsules. Feet at y = -4.2,
     * crown at about y = +1. Ivie has narrower shoulders and fuller hips.
     */
    public static final class Body implements Swarm.Form {
        private final Head head;
        private final float[] body;
        private final float[] bodyLit;

        public Body(WireHead wire) {
            head = new Head(wire, 0.34f, 0f, 0.62f, 4);
            boolean ivie = wire.ivie;
            List<float[]> pts = new ArrayList<float[]>();
            float sh = ivie ? 0.46f : 0.58f;
            float hip = ivie ? 0.40f : 0.33f;
            capsule(pts, 0f, 0.12f, 0f, 0f, -0.15f, 0f, 0.12f, 120);           // neck
            ellipsoid(pts, 0f, -0.75f, 0f, sh * 0.95f, 0.72f, 0.27f, 900);       // chest
            ellipsoid(pts, 0f, -1.55f, 0f, hip * 1.05f, 0.42f, 0.25f, 500);      // waist and hips
            for (int s = -1; s <= 1; s += 2) {
                ellipsoid(pts, s * sh, -0.32f, 0f, 0.16f, 0.14f, 0.15f, 90);       // shoulder
                capsule(pts, s * sh, -0.35f, 0f, s * (sh + 0.12f), -1.25f, 0.05f, 0.10f, 280); // upper arm
                capsule(pts, s * (sh + 0.12f), -1.25f, 0.05f, s * (sh + 0.16f), -2.05f, 0.12f, 0.08f, 240); // forearm
                ellipsoid(pts, s * (sh + 0.17f), -2.25f, 0.12f, 0.07f, 0.14f, 0.05f, 90); // hand
                capsule(pts, s * hip * 0.55f, -1.8f, 0f, s * hip * 0.6f, -2.95f, 0.02f, 0.16f, 380); // thigh
                capsule(pts, s * hip * 0.6f, -2.95f, 0.02f, s * hip * 0.62f, -4.05f, -0.02f, 0.11f, 320); // shin
                ellipsoid(pts, s * hip * 0.62f, -4.15f, 0.12f, 0.09f, 0.06f, 0.2f, 80); // foot
            }
            ring(pts, 0f, -1.0f, 0.27f, 0.12f, 40); // a small light at the chest, like a core
            body = new float[pts.size() * 3];
            bodyLit = new float[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                float[] p = pts.get(i);
                body[i * 3] = p[0];
                body[i * 3 + 1] = p[1];
                body[i * 3 + 2] = p[2];
                bodyLit[i] = p[3];
            }
        }

        public WireHead.Pose pose() {
            return head.pose;
        }

        @Override
        public int fill(float[] xyz, float[] lit, int max, float time) {
            int n = head.fill(xyz, lit, max, time);
            float breathe = 1f + 0.012f * (float) Math.sin(time * 1.3f);
            float sway = 0.03f * (float) Math.sin(time * 0.7f);
            int m = body.length / 3;
            for (int i = 0; i < m && n < max; i++) {
                float y = body[i * 3 + 1];
                float arm = Math.abs(body[i * 3]) > 0.62f && y < -0.3f ? (float) Math.sin(time * 0.9f) * 0.03f * (-0.3f - y) : 0f;
                xyz[n * 3] = body[i * 3] * (y > -1.4f ? breathe : 1f) + sway * (y + 4.2f) * 0.1f;
                xyz[n * 3 + 1] = y;
                xyz[n * 3 + 2] = body[i * 3 + 2] + arm;
                lit[n] = bodyLit[i];
                n++;
            }
            return n;
        }
    }

    static void capsule(List<float[]> out, float ax, float ay, float az, float bx, float by, float bz, float r, int count) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float ux = dx / len, uy = dy / len, uz = dz / len;
        // two perpendicular axes
        float px = -uy, py = ux, pz = 0f;
        float pl = (float) Math.sqrt(px * px + py * py);
        if (pl < 1e-4f) {
            px = 1f;
            py = 0f;
            pl = 1f;
        }
        px /= pl;
        py /= pl;
        float qx = uy * pz - uz * py, qy = uz * px - ux * pz, qz = ux * py - uy * px;
        for (int i = 0; i < count; i++) {
            float t = (i + 0.5f) / count;
            double a = i * 2.399963;
            float taper = 1f - 0.18f * t;
            float c = (float) Math.cos(a) * r * taper, s = (float) Math.sin(a) * r * taper;
            out.add(new float[]{ax + dx * t + px * c + qx * s, ay + dy * t + py * c + qy * s, az + dz * t + pz * c + qz * s,
                    0.45f + 0.15f * (float) Math.cos(a)});
        }
    }

    static void ellipsoid(List<float[]> out, float cx, float cy, float cz, float rx, float ry, float rz, int count) {
        for (int i = 0; i < count; i++) {
            float y = 1f - 2f * (i + 0.5f) / count;
            float rad = (float) Math.sqrt(Math.max(0f, 1f - y * y));
            double a = i * 2.399963;
            out.add(new float[]{cx + (float) Math.cos(a) * rad * rx, cy + y * ry, cz + (float) Math.sin(a) * rad * rz,
                    0.45f + 0.2f * (float) Math.sin(a)});
        }
    }

    static void ring(List<float[]> out, float cx, float cy, float cz, float r, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            out.add(new float[]{cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, cz, 1f});
        }
    }

    // ------------------------------------------------------------ drawings

    /** A 2D explanation drawing built by the swarm, stroke by stroke as time passes. Box -1..1. */
    public static final class Drawing implements Swarm.Form {
        private final float[] pts;
        private final float[] at;
        private final float[] glow;
        public final Sketch sketch;
        private float started = -1f;

        public Drawing(Sketch s) {
            sketch = s;
            List<float[]> list = new ArrayList<float[]>();
            for (Sketch.Stroke st : s.strokes) {
                switch (st.kind) {
                    case LINE:
                    case ARROW: {
                        float total = 0f;
                        for (int i = 2; i + 1 < st.pts.length; i += 2) {
                            total += (float) Math.hypot(st.pts[i] - st.pts[i - 2], st.pts[i + 1] - st.pts[i - 1]);
                        }
                        float walked = 0f;
                        for (int i = 2; i + 1 < st.pts.length; i += 2) {
                            float x0 = st.pts[i - 2], y0 = st.pts[i - 1], x1 = st.pts[i], y1 = st.pts[i + 1];
                            float seg = (float) Math.hypot(x1 - x0, y1 - y0);
                            int k = Math.max(2, (int) (seg / 0.018f));
                            for (int j = 0; j < k; j++) {
                                float q = j / (float) k;
                                float p = total <= 0f ? 1f : (walked + seg * q) / total;
                                list.add(new float[]{x0 + (x1 - x0) * q, y0 + (y1 - y0) * q, st.start + st.duration * p, 1f});
                            }
                            walked += seg;
                        }
                        if (st.kind == Sketch.Kind.ARROW) {
                            float x0 = st.pts[0], y0 = st.pts[1], x1 = st.pts[2], y1 = st.pts[3];
                            double ang = Math.atan2(y1 - y0, x1 - x0);
                            for (int side = -1; side <= 1; side += 2) {
                                double b = ang + Math.PI - side * 0.5;
                                for (int j = 0; j < 8; j++) {
                                    float q = j / 8f * 0.12f;
                                    list.add(new float[]{x1 + (float) Math.cos(b) * q, y1 + (float) Math.sin(b) * q,
                                            st.start + st.duration, 1f});
                                }
                            }
                        }
                        break;
                    }
                    case CIRCLE: {
                        float r = st.pts[2];
                        int k = Math.max(16, (int) (Math.PI * 2 * r / 0.018f));
                        for (int j = 0; j < k; j++) {
                            double a = Math.PI / 2 - j * Math.PI * 2 / k;
                            list.add(new float[]{st.pts[0] + (float) Math.cos(a) * r, st.pts[1] + (float) Math.sin(a) * r,
                                    st.start + st.duration * j / (float) k, 1f});
                        }
                        break;
                    }
                    case DOT: {
                        float r = st.pts[2];
                        for (int j = 0; j < 24; j++) {
                            double a = j * 2.399963;
                            float rr = r * (float) Math.sqrt((j + 0.5f) / 24f);
                            list.add(new float[]{st.pts[0] + (float) Math.cos(a) * rr, st.pts[1] + (float) Math.sin(a) * rr,
                                    st.start, 0.9f});
                        }
                        break;
                    }
                    default:
                        break; // labels are drawn as glowing text by the painter
                }
            }
            pts = new float[list.size() * 2];
            at = new float[list.size()];
            glow = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                float[] p = list.get(i);
                pts[i * 2] = p[0];
                pts[i * 2 + 1] = p[1];
                at[i] = p[2];
                glow[i] = p[3];
            }
        }

        public int size() {
            return at.length;
        }

        @Override
        public int fill(float[] xyz, float[] lit, int max, float time) {
            if (started < 0f) {
                started = time;
            }
            float t = time - started;
            int n = 0;
            for (int i = 0; i < at.length && n < max; i++) {
                if (at[i] > t) {
                    continue; // not drawn yet: this particle stays in the gathering cloud
                }
                xyz[n * 3] = pts[i * 2];
                xyz[n * 3 + 1] = pts[i * 2 + 1];
                xyz[n * 3 + 2] = 0.02f * (float) Math.sin(time * 2f + i);
                lit[n] = glow[i];
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

    /** Slowly turning 3D objects: globe, cube, pyramid, torus, dna, atom, sphere. Radius about 1. */
    public static final class Object3D implements Swarm.Form {
        public final String kind;
        private final float[] pts;
        private final float[] lit;

        public Object3D(String kind) {
            this.kind = kind;
            List<float[]> l = new ArrayList<float[]>();
            switch (kind) {
                case "globe":
                    ellipsoid(l, 0f, 0f, 0f, 0.95f, 0.95f, 0.95f, 900);
                    for (int lat = -2; lat <= 2; lat++) {
                        float y = lat * 0.3f;
                        float r = (float) Math.sqrt(1 - y * y) * 0.97f;
                        for (int j = 0; j < 90; j++) {
                            double a = j * Math.PI * 2 / 90;
                            l.add(new float[]{(float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 1f});
                        }
                    }
                    for (int lon = 0; lon < 6; lon++) {
                        double b = lon * Math.PI / 6;
                        for (int j = 0; j < 90; j++) {
                            double a = j * Math.PI * 2 / 90;
                            l.add(new float[]{(float) (Math.cos(a) * Math.cos(b)) * 0.97f, (float) Math.sin(a) * 0.97f,
                                    (float) (Math.cos(a) * Math.sin(b)) * 0.97f, 1f});
                        }
                    }
                    break;
                case "cube":
                    for (int e = 0; e < 12; e++) {
                        int axis = e / 4;
                        float s1 = (e & 1) == 0 ? -0.7f : 0.7f;
                        float s2 = (e & 2) == 0 ? -0.7f : 0.7f;
                        for (int j = 0; j <= 40; j++) {
                            float t = -0.7f + 1.4f * j / 40f;
                            float[] p = axis == 0 ? new float[]{t, s1, s2} : axis == 1 ? new float[]{s1, t, s2} : new float[]{s1, s2, t};
                            l.add(new float[]{p[0], p[1], p[2], 1f});
                        }
                    }
                    for (int i = 0; i < 600; i++) {
                        int face = i % 6;
                        float u = Swarm.noise(i * 2) * 0.7f, v = Swarm.noise(i * 2 + 1) * 0.7f, w = face < 3 ? -0.7f : 0.7f;
                        int ax = face % 3;
                        l.add(ax == 0 ? new float[]{w, u, v, 0.4f} : ax == 1 ? new float[]{u, w, v, 0.4f} : new float[]{u, v, w, 0.4f});
                    }
                    break;
                case "pyramid": {
                    float[][] v = {{-0.8f, -0.6f, -0.8f}, {0.8f, -0.6f, -0.8f}, {0.8f, -0.6f, 0.8f}, {-0.8f, -0.6f, 0.8f}, {0f, 0.85f, 0f}};
                    int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {0, 4}, {1, 4}, {2, 4}, {3, 4}};
                    for (int[] p : e) {
                        for (int j = 0; j <= 50; j++) {
                            float t = j / 50f;
                            l.add(new float[]{v[p[0]][0] + (v[p[1]][0] - v[p[0]][0]) * t, v[p[0]][1] + (v[p[1]][1] - v[p[0]][1]) * t,
                                    v[p[0]][2] + (v[p[1]][2] - v[p[0]][2]) * t, 1f});
                        }
                    }
                    break;
                }
                case "torus":
                    for (int i = 0; i < 1400; i++) {
                        double a = i * 2.399963, b = (i % 37) * Math.PI * 2 / 37;
                        float r = 0.75f + 0.25f * (float) Math.cos(b);
                        l.add(new float[]{(float) Math.cos(a) * r, 0.25f * (float) Math.sin(b), (float) Math.sin(a) * r,
                                0.5f + 0.4f * (float) Math.cos(b)});
                    }
                    break;
                case "dna":
                    for (int i = 0; i < 260; i++) {
                        float y = -1f + 2f * i / 260f;
                        double a = y * Math.PI * 3;
                        for (int s = 0; s < 2; s++) {
                            double b = a + s * Math.PI;
                            l.add(new float[]{0.4f * (float) Math.cos(b), y, 0.4f * (float) Math.sin(b), 1f});
                        }
                        if (i % 12 == 0) {
                            for (int j = 1; j < 10; j++) {
                                float q = j / 10f;
                                l.add(new float[]{0.4f * (float) Math.cos(a) * (1 - 2 * q), y, 0.4f * (float) Math.sin(a) * (1 - 2 * q), 0.6f});
                            }
                        }
                    }
                    break;
                case "atom":
                    ellipsoid(l, 0f, 0f, 0f, 0.16f, 0.16f, 0.16f, 160);
                    for (int o = 0; o < 3; o++) {
                        double tilt = o * Math.PI / 3;
                        for (int j = 0; j < 160; j++) {
                            double a = j * Math.PI * 2 / 160;
                            float x = 0.9f * (float) Math.cos(a), y = 0.32f * (float) Math.sin(a);
                            l.add(new float[]{x * (float) Math.cos(tilt) - y * (float) Math.sin(tilt),
                                    x * (float) Math.sin(tilt) + y * (float) Math.cos(tilt), 0.32f * (float) Math.sin(a + o), 0.9f});
                        }
                    }
                    break;
                default:
                    ellipsoid(l, 0f, 0f, 0f, 0.9f, 0.9f, 0.9f, 1200);
            }
            pts = new float[l.size() * 3];
            lit = new float[l.size()];
            for (int i = 0; i < l.size(); i++) {
                float[] p = l.get(i);
                pts[i * 3] = p[0];
                pts[i * 3 + 1] = p[1];
                pts[i * 3 + 2] = p[2];
                lit[i] = p[3];
            }
        }

        @Override
        public int fill(float[] xyz, float[] out, int max, float time) {
            float a = time * 0.4f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float tilt = 0.35f, ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
            int n = Math.min(max, lit.length);
            for (int i = 0; i < n; i++) {
                float x = pts[i * 3], y = pts[i * 3 + 1], z = pts[i * 3 + 2];
                float x1 = x * ca + z * sa, z1 = -x * sa + z * ca;
                float y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;
                xyz[i * 3] = x1;
                xyz[i * 3 + 1] = y2;
                xyz[i * 3 + 2] = z2;
                out[i] = lit[i];
            }
            return n;
        }
    }

    /** Which 3D object a request names, or null. */
    public static String objectFor(String lower) {
        if (lower == null) {
            return null;
        }
        String t = lower.toLowerCase(java.util.Locale.US);
        if (!(t.startsWith("draw") || t.startsWith("show") || t.startsWith("explain") || t.startsWith("make"))) {
            return null;
        }
        if (t.contains("globe") || t.contains("earth") || t.contains("world")) return "globe";
        if (t.contains("cube") || t.contains("box")) return "cube";
        if (t.contains("pyramid")) return "pyramid";
        if (t.contains("torus") || t.contains("donut") || t.contains("doughnut")) return "torus";
        if (t.contains("dna") || t.contains("helix")) return "dna";
        if (t.contains("atom")) return "atom";
        if (t.contains("sphere") || t.contains("ball")) return "sphere";
        return null;
    }
}
