package ai.dvine.render.forms;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny sculpting kit for nano dots. Shapes are signed distance fields
 * (ellipsoids, tapered capsules, rounded boxes, tori) melted together with a
 * smooth union, so surfaces are curved everywhere and there are no mesh edges.
 * {@link #sample} spreads dots evenly over the melted surface and returns each
 * dot's normal for shading. Pure Java, deterministic.
 */
public final class Sculpt {
    static final int ELLIPSOID = 0;
    static final int CAPSULE = 1;
    static final int BOX = 2;
    static final int TORUS = 3;

    static final class Prim {
        final int type;
        final float[] a;
        final float blend;
        final boolean cut;
        final int tag;

        Prim(int type, float[] a, float blend, boolean cut, int tag) {
            this.type = type;
            this.a = a;
            this.blend = blend;
            this.cut = cut;
            this.tag = tag;
        }
    }

    private final List<Prim> prims = new ArrayList<Prim>();
    private float blend = 0.1f;
    private int tag;

    /** Smoothness used by the next shapes added (0 = hard union). */
    public Sculpt blend(float k) {
        blend = k;
        return this;
    }

    /** A label carried by the next shapes; a sample reports the label of its nearest shape. */
    public Sculpt tag(int t) {
        tag = t;
        return this;
    }

    public Sculpt ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz) {
        prims.add(new Prim(ELLIPSOID, new float[]{cx, cy, cz, rx, ry, rz}, blend, false, tag));
        return this;
    }

    public Sculpt sphere(float cx, float cy, float cz, float r) {
        return ellipsoid(cx, cy, cz, r, r, r);
    }

    /** A capsule from a (radius ra) to b (radius rb). */
    public Sculpt capsule(float ax, float ay, float az, float ra, float bx, float by, float bz, float rb) {
        prims.add(new Prim(CAPSULE, new float[]{ax, ay, az, ra, bx, by, bz, rb}, blend, false, tag));
        return this;
    }

    /** Axis-aligned box with half sizes and a corner radius. */
    public Sculpt box(float cx, float cy, float cz, float hx, float hy, float hz, float round) {
        prims.add(new Prim(BOX, new float[]{cx, cy, cz, hx, hy, hz, round}, blend, false, tag));
        return this;
    }

    /** A torus lying in the xz plane. */
    public Sculpt torus(float cx, float cy, float cz, float major, float minor) {
        prims.add(new Prim(TORUS, new float[]{cx, cy, cz, major, minor}, blend, false, tag));
        return this;
    }

    /** Carves an ellipsoid out of everything added before it. */
    public Sculpt carve(float cx, float cy, float cz, float rx, float ry, float rz) {
        prims.add(new Prim(ELLIPSOID, new float[]{cx, cy, cz, rx, ry, rz}, blend, true, tag));
        return this;
    }

    // ------------------------------------------------------------ field

    /** Signed distance (negative inside) at a point. */
    public float d(float x, float y, float z) {
        float d = 1e9f;
        for (int i = 0; i < prims.size(); i++) {
            Prim p = prims.get(i);
            float di = dist(p, x, y, z);
            if (p.cut) {
                d = smax(d, -di, p.blend);
            } else if (i == 0) {
                d = di;
            } else {
                d = smin(d, di, p.blend);
            }
        }
        return d;
    }

    /** The tag of the solid shape nearest to a point. */
    public int tagAt(float x, float y, float z) {
        float best = 1e9f;
        int t = 0;
        for (Prim p : prims) {
            if (p.cut) {
                continue;
            }
            float di = dist(p, x, y, z);
            if (di < best) {
                best = di;
                t = p.tag;
            }
        }
        return t;
    }

    /**
     * Where a ray from the viewer straight down -z first meets the surface at
     * (x, y), or NaN when it misses. Used to seat eyes, brows and lips on the skin.
     */
    public float frontZ(float x, float y, float fromZ) {
        float z = fromZ;
        for (int i = 0; i < 96; i++) {
            float d = d(x, y, z);
            if (d < 0.0006f) {
                return z;
            }
            z -= Math.max(d * 0.9f, 0.002f);
            if (z < -fromZ) {
                break;
            }
        }
        return Float.NaN;
    }

    /**
     * How sharply the surface bends at a point (the field's Laplacian): large
     * on small round features like the nose, lips and ears, near zero on the skull.
     */
    public float curvature(float x, float y, float z) {
        float h = 0.03f;
        float sum = d(x + h, y, z) + d(x - h, y, z) + d(x, y + h, z) + d(x, y - h, z) + d(x, y, z + h) + d(x, y, z - h);
        return (sum - 6f * d(x, y, z)) / (h * h);
    }

    /** Unit outward normal at a point. */
    public void normal(float x, float y, float z, float[] out, int o) {
        float h = 0.002f;
        float nx = d(x + h, y, z) - d(x - h, y, z);
        float ny = d(x, y + h, z) - d(x, y - h, z);
        float nz = d(x, y, z + h) - d(x, y, z - h);
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) {
            l = 1f;
        }
        out[o] = nx / l;
        out[o + 1] = ny / l;
        out[o + 2] = nz / l;
    }

    private static float dist(Prim p, float x, float y, float z) {
        float[] a = p.a;
        switch (p.type) {
            case ELLIPSOID: {
                float px = (x - a[0]) / a[3], py = (y - a[1]) / a[4], pz = (z - a[2]) / a[5];
                float k0 = (float) Math.sqrt(px * px + py * py + pz * pz);
                float qx = px / a[3], qy = py / a[4], qz = pz / a[5];
                float k1 = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
                return k1 < 1e-9f ? -Math.min(a[3], Math.min(a[4], a[5])) : k0 * (k0 - 1f) / k1;
            }
            case CAPSULE: {
                float bax = a[4] - a[0], bay = a[5] - a[1], baz = a[6] - a[2];
                float pax = x - a[0], pay = y - a[1], paz = z - a[2];
                float h = (pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz);
                h = Math.max(0f, Math.min(1f, h));
                float dx = pax - bax * h, dy = pay - bay * h, dz = paz - baz * h;
                return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - (a[3] + (a[7] - a[3]) * h);
            }
            case BOX: {
                float r = a[6];
                float qx = Math.abs(x - a[0]) - a[3] + r, qy = Math.abs(y - a[1]) - a[4] + r, qz = Math.abs(z - a[2]) - a[5] + r;
                float mx = Math.max(qx, 0f), my = Math.max(qy, 0f), mz = Math.max(qz, 0f);
                return (float) Math.sqrt(mx * mx + my * my + mz * mz) + Math.min(Math.max(qx, Math.max(qy, qz)), 0f) - r;
            }
            default: {
                float px = x - a[0], py = y - a[1], pz = z - a[2];
                float q = (float) Math.sqrt(px * px + pz * pz) - a[3];
                return (float) Math.sqrt(q * q + py * py) - a[4];
            }
        }
    }

    static float smin(float a, float b, float k) {
        if (k <= 0f) {
            return Math.min(a, b);
        }
        float h = Math.max(k - Math.abs(a - b), 0f) / k;
        return Math.min(a, b) - h * h * k * 0.25f;
    }

    static float smax(float a, float b, float k) {
        return -smin(-a, -b, k);
    }

    // ------------------------------------------------------------ sampling

    /** Evenly spread surface dots: positions, normals and the tag of each dot's shape. */
    public static final class Cloud {
        public final float[] xyz;
        public final float[] normal;
        public final int[] tag;
        public final int size;

        Cloud(float[] xyz, float[] normal, int[] tag, int size) {
            this.xyz = xyz;
            this.normal = normal;
            this.tag = tag;
            this.size = size;
        }
    }

    /** A filter on candidate dots (return false to drop one). */
    public interface Keep {
        boolean keep(float x, float y, float z);
    }

    public Cloud sample(int target, int seed) {
        return sample(target, seed, null);
    }

    /**
     * About {@code target} dots spread evenly over the surface. Candidates are
     * thrown on each shape's own surface, pulled onto the melted surface with
     * a few Newton steps, then thinned so neighbours keep an even spacing.
     */
    public Cloud sample(int target, int seed, Keep keep) {
        float[] area = new float[prims.size()];
        float total = 0f;
        for (int i = 0; i < prims.size(); i++) {
            Prim p = prims.get(i);
            area[i] = p.cut ? 0f : area(p);
            total += area[i];
        }
        int want = target * 3;
        float[] cand = new float[want * 3 + 3 * prims.size()];
        int n = 0;
        int s = seed * 7919;
        float[] g = new float[3];
        for (int i = 0; i < prims.size(); i++) {
            if (area[i] <= 0f) {
                continue;
            }
            int k = Math.max(4, Math.round(area[i] / total * want));
            for (int j = 0; j < k && n < cand.length / 3; j++) {
                onPrim(prims.get(i), j, k, s++, g);
                float x = g[0], y = g[1], z = g[2];
                boolean ok = false;
                for (int it = 0; it < 6; it++) {
                    float d = d(x, y, z);
                    if (Math.abs(d) < 0.0015f) {
                        ok = true;
                        break;
                    }
                    normal(x, y, z, g, 0);
                    x -= g[0] * d;
                    y -= g[1] * d;
                    z -= g[2] * d;
                }
                if (!ok || keep != null && !keep.keep(x, y, z)) {
                    continue;
                }
                cand[n * 3] = x;
                cand[n * 3 + 1] = y;
                cand[n * 3 + 2] = z;
                n++;
            }
        }
        // thin with an even spacing; tune the spacing until the count is close to the target
        float spacing = (float) Math.sqrt(total / Math.max(1, target)) * 0.9f;
        int[] kept = new int[n];
        int count = 0;
        for (int pass = 0; pass < 5; pass++) {
            count = thin(cand, n, spacing, kept);
            float ratio = count / (float) target;
            if (ratio > 0.94f && ratio < 1.06f || count >= n && ratio < 1f) {
                break;
            }
            spacing *= (float) Math.sqrt(ratio);
        }
        if (count > target) {
            count = target;
        }
        float[] xyz = new float[count * 3];
        float[] nor = new float[count * 3];
        int[] tags = new int[count];
        for (int i = 0; i < count; i++) {
            int c = kept[i];
            xyz[i * 3] = cand[c * 3];
            xyz[i * 3 + 1] = cand[c * 3 + 1];
            xyz[i * 3 + 2] = cand[c * 3 + 2];
            normal(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], nor, i * 3);
            tags[i] = tagAt(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);
        }
        return new Cloud(xyz, nor, tags, count);
    }

    /** Greedy Poisson-disc thinning on a hash grid. Returns how many were kept. */
    private static int thin(float[] p, int n, float r, int[] out) {
        int size = Integer.highestOneBit(Math.max(16, n * 2)) << 1;
        int[] head = new int[size];
        java.util.Arrays.fill(head, -1);
        int[] next = new int[n];
        float inv = 1f / r;
        float r2 = r * r;
        int count = 0;
        for (int i = 0; i < n; i++) {
            float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
            int cx = (int) Math.floor(x * inv), cy = (int) Math.floor(y * inv), cz = (int) Math.floor(z * inv);
            boolean free = true;
            for (int dx = -1; dx <= 1 && free; dx++) {
                for (int dy = -1; dy <= 1 && free; dy++) {
                    for (int dz = -1; dz <= 1 && free; dz++) {
                        for (int j = head[hash(cx + dx, cy + dy, cz + dz, size)]; j >= 0; j = next[j]) {
                            float ex = p[j * 3] - x, ey = p[j * 3 + 1] - y, ez = p[j * 3 + 2] - z;
                            if (ex * ex + ey * ey + ez * ez < r2) {
                                free = false;
                                break;
                            }
                        }
                    }
                }
            }
            if (free) {
                int h = hash(cx, cy, cz, size);
                next[i] = head[h];
                head[h] = i;
                out[count++] = i;
            }
        }
        return count;
    }

    private static int hash(int x, int y, int z, int size) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return h & (size - 1);
    }

    private static float area(Prim p) {
        float[] a = p.a;
        switch (p.type) {
            case ELLIPSOID: {
                double ab = a[3] * a[4], ac = a[3] * a[5], bc = a[4] * a[5];
                double q = 1.6075;
                return (float) (4 * Math.PI * Math.pow((Math.pow(ab, q) + Math.pow(ac, q) + Math.pow(bc, q)) / 3, 1 / q));
            }
            case CAPSULE: {
                float len = (float) Math.sqrt(sq(a[4] - a[0]) + sq(a[5] - a[1]) + sq(a[6] - a[2]));
                float r = (a[3] + a[7]) * 0.5f;
                return (float) (2 * Math.PI * r * len + 4 * Math.PI * r * r);
            }
            case BOX:
                return 8f * (a[3] * a[4] + a[3] * a[5] + a[4] * a[5]);
            default:
                return (float) (4 * Math.PI * Math.PI * a[3] * a[4]);
        }
    }

    /** The j-th of k well-spread points on one shape's own surface. */
    private static void onPrim(Prim p, int j, int k, int seed, float[] out) {
        float[] a = p.a;
        float u = (j + 0.5f) / k;
        double ang = j * 2.399963229728653;
        switch (p.type) {
            case ELLIPSOID: {
                float y = 1f - 2f * u;
                float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
                out[0] = a[0] + (float) Math.cos(ang) * r * a[3];
                out[1] = a[1] + y * a[4];
                out[2] = a[2] + (float) Math.sin(ang) * r * a[5];
                return;
            }
            case CAPSULE: {
                float bax = a[4] - a[0], bay = a[5] - a[1], baz = a[6] - a[2];
                float len = (float) Math.sqrt(bax * bax + bay * bay + baz * baz);
                float ux = bax / len, uy = bay / len, uz = baz / len;
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
                // stretch t past both ends so the caps get dots too
                float r0 = Math.max(a[3], a[7]);
                float t = -r0 / len + u * (1f + 2f * r0 / len);
                float tc = Math.max(0f, Math.min(1f, t));
                float r = a[3] + (a[7] - a[3]) * tc;
                float c = (float) Math.cos(ang) * r, s = (float) Math.sin(ang) * r;
                out[0] = a[0] + bax * t + px * c + qx * s;
                out[1] = a[1] + bay * t + py * c + qy * s;
                out[2] = a[2] + baz * t + pz * c + qz * s;
                return;
            }
            case BOX: {
                int face = j % 6;
                float s1 = Swarm.noise(seed * 2), s2 = Swarm.noise(seed * 2 + 1);
                float x = s1, y = s2, z = face < 3 ? -1f : 1f;
                int ax = face % 3;
                out[0] = a[0] + (ax == 0 ? z : x) * a[3];
                out[1] = a[1] + (ax == 1 ? z : ax == 0 ? x : y) * a[4];
                out[2] = a[2] + (ax == 2 ? z : y) * a[5];
                return;
            }
            default: {
                double b = (j * 0.618034 % 1.0) * Math.PI * 2;
                float r = a[3] + a[4] * (float) Math.cos(b);
                out[0] = a[0] + (float) Math.cos(u * Math.PI * 2 * 1) * r;
                out[1] = a[1] + a[4] * (float) Math.sin(b);
                out[2] = a[2] + (float) Math.sin(u * Math.PI * 2 * 1) * r;
            }
        }
    }

    private static float sq(float v) {
        return v * v;
    }

    /** Soft shading for a dot: key light from the upper left plus a glowing rim. */
    public static float shade(float nx, float ny, float nz) {
        float lambert = Math.max(0f, nx * -0.5f + ny * 0.45f + nz * 0.74f);
        float rim = 1f - Math.abs(nz);
        return Math.min(0.92f, 0.08f + 0.7f * lambert * lambert + 0.38f * rim * rim);
    }

    /** Hologram shading: faint face-on surfaces, a bright silhouette. */
    public static float holo(float nx, float ny, float nz) {
        float rim = 1f - Math.abs(nz);
        float lambert = Math.max(0f, nx * -0.38f + ny * 0.5f + nz * 0.78f);
        return Math.min(0.95f, 0.1f + 0.18f * lambert + 0.75f * rim * rim * rim);
    }
}
