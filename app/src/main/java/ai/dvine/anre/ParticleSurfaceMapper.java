package ai.dvine.anre;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.human.Regions;
import ai.dvine.human.mesh.TriMesh;

/**
 * Lays particles on a triangle surface: the anatomy gives the shape, the
 * particles only render it.
 * <ul>
 *   <li>area-weighted, with a density factor per region (more on the face
 *   and hands, where identity and small shapes live);</li>
 *   <li>stratified, not random: each triangle gets its expected share
 *   (the fractions carried over in a fixed order) and its particles are
 *   spread with a low-discrepancy sequence, so nothing clumps and no
 *   triangle is left bare by chance;</li>
 *   <li>deterministic: the same mesh, budget and seed give the same layout,
 *   which is how a damaged layout is restored exactly.</li>
 * </ul>
 */
public final class ParticleSurfaceMapper {
    /** Relative particle density for a part (region or component) id. */
    public interface Density {
        float of(int part);
    }

    /** What the value "order" means for a particle: here, how high it sits (0 feet .. 1 crown). */
    public interface Order {
        float of(float x, float y, float z, int part);
    }

    private ParticleSurfaceMapper() {
    }

    /** Human skin density: the face several times denser than the body, hands denser too. */
    public static final Density HUMAN = part -> {
        if (part == Regions.HAIR) return 1.2f;
        if (part == Regions.EYE_L || part == Regions.EYE_R || part == Regions.LIPS || part == Regions.NOSE) return 7f;
        if (Regions.isFace(part)) return 5f;
        if (part == Regions.NECK) return 2f;
        String k = Regions.key(part);
        if (k.startsWith("hand.")) return 3f;
        return 1f;
    };

    /**
     * Samples {@code budget} particles (exactly, give or take rounding) over
     * the triangles of {@code m}, using {@code partOf} for each triangle's part
     * id. Triangles with part id below zero are skipped.
     */
    public static ParticleSet sample(TriMesh m, int[] partOf, int budget, Density density, Order order, long seed) {
        int nt = m.tri.length / 3;
        float[] p = m.pos;
        double[] w = new double[nt];
        double total = 0;
        for (int t = 0; t < nt; t++) {
            if (partOf[t] < 0) {
                continue;
            }
            int a = m.tri[t * 3] * 3, b = m.tri[t * 3 + 1] * 3, c = m.tri[t * 3 + 2] * 3;
            double ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
            double vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
            double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            double area = 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
            w[t] = area * Math.max(0f, density.of(partOf[t]));
            total += w[t];
        }
        int[] per = new int[nt];
        int count = 0;
        double carry = hash01(seed) * 0.999; // where the running remainder starts
        for (int t = 0; t < nt; t++) {
            if (total <= 0) {
                break;
            }
            double want = w[t] / total * budget + carry;
            per[t] = (int) Math.floor(want);
            carry = want - per[t];
            count += per[t];
        }
        ParticleSet s = new ParticleSet(count, seed, budget);
        int i = 0;
        // bounds for the order value
        float[] b = m.bounds();
        for (int t = 0; t < nt; t++) {
            int k = per[t];
            if (k == 0) {
                continue;
            }
            // each triangle starts at its own point of the sequence, so neighbours don't line up
            double start = hash01(seed * 31 + t);
            for (int j = 0; j < k; j++) {
                // R2 low-discrepancy point in the unit square, folded into the triangle
                double r1 = frac(start + (j + 1) * 0.7548776662466927);
                double r2 = frac(start * 1.37 + (j + 1) * 0.5698402909980532);
                if (r1 + r2 > 1) {
                    r1 = 1 - r1;
                    r2 = 1 - r2;
                }
                float uu = (float) r1, vv = (float) r2;
                s.tri[i] = t;
                s.corners[i * 3] = m.tri[t * 3];
                s.corners[i * 3 + 1] = m.tri[t * 3 + 1];
                s.corners[i * 3 + 2] = m.tri[t * 3 + 2];
                s.u[i] = uu;
                s.v[i] = vv;
                s.part[i] = (short) partOf[t];
                s.size[i] = 0.8f + 0.4f * (float) hash01(seed * 7 + i);
                int a = m.tri[t * 3] * 3, bb = m.tri[t * 3 + 1] * 3, c = m.tri[t * 3 + 2] * 3;
                float ww = 1f - uu - vv;
                float x = uu * p[a] + vv * p[bb] + ww * p[c], y = uu * p[a + 1] + vv * p[bb + 1] + ww * p[c + 1],
                        z = uu * p[a + 2] + vv * p[bb + 2] + ww * p[c + 2];
                s.order[i] = order == null ? (y - b[1]) / Math.max(1e-6f, b[4] - b[1]) : order.of(x, y, z, partOf[t]);
                i++;
            }
        }
        return s;
    }

    /**
     * Particles along line segments (vertex index pairs in {@code lines}):
     * evenly spaced, {@code spacing} apart (at least one per segment), with
     * the region of the segment's first vertex. Used for the features drawn
     * as lines (eyelids, brows, lip line, iris rings, hair strands).
     */
    public static ParticleSet along(float[] pos, byte[] region, int[] lines, float spacing, float[] bounds, long seed) {
        int n = lines.length / 2;
        int[] per = new int[n];
        int count = 0;
        for (int e = 0; e < n; e++) {
            int a = lines[e * 2] * 3, b = lines[e * 2 + 1] * 3;
            float len = (float) Math.sqrt(sq(pos[b] - pos[a]) + sq(pos[b + 1] - pos[a + 1]) + sq(pos[b + 2] - pos[a + 2]));
            per[e] = Math.max(1, Math.round(len / spacing));
            count += per[e];
        }
        ParticleSet s = new ParticleSet(count, seed, count);
        int i = 0;
        for (int e = 0; e < n; e++) {
            int va = lines[e * 2], vb = lines[e * 2 + 1];
            for (int j = 0; j < per[e]; j++) {
                float q = (j + 0.5f) / per[e];
                s.corners[i * 3] = va;
                s.corners[i * 3 + 1] = vb;
                s.corners[i * 3 + 2] = vb;
                s.tri[i] = -1;
                s.u[i] = 1f - q;
                s.v[i] = q;
                s.part[i] = (short) (region == null ? 0 : region[va] & 0xff);
                s.size[i] = 0.85f;
                float y = pos[va * 3 + 1] * (1f - q) + pos[vb * 3 + 1] * q;
                s.order[i] = (y - bounds[1]) / Math.max(1e-6f, bounds[4] - bounds[1]);
                i++;
            }
        }
        return s;
    }

    private static float sq(float x) {
        return x * x;
    }

    /** Each triangle's region: the region of its first corner (how touch reports a hit, too). */
    public static int[] trianglesByRegion(TriMesh m) {
        int nt = m.tri.length / 3;
        int[] out = new int[nt];
        for (int t = 0; t < nt; t++) {
            out[t] = m.region == null ? 0 : m.region[m.tri[t * 3]] & 0xff;
        }
        return out;
    }

    /**
     * Checks a particle set against its mesh: every particle on an existing
     * triangle, inside it (barycentric within bounds), with the part id of
     * that triangle. Returns the problems found (empty when sound).
     */
    public static List<String> validate(ParticleSet s, TriMesh m, int[] partOf) {
        List<String> out = new ArrayList<String>();
        int nt = m.tri.length / 3, bad = 0, wrongPart = 0, outside = 0;
        int nv = m.vertexCount();
        for (int i = 0; i < s.count; i++) {
            int t = s.tri[i];
            boolean cornersOk = true;
            for (int k = 0; k < 3; k++) {
                int c = s.corners[i * 3 + k];
                cornersOk &= c >= 0 && c < nv;
            }
            if (!cornersOk) {
                bad++;
                continue;
            }
            if (t == -1) {
                continue; // a line particle: its two ends are all it needs
            }
            if (t < 0 || t >= nt || s.corners[i * 3] != m.tri[t * 3] || s.corners[i * 3 + 1] != m.tri[t * 3 + 1]
                    || s.corners[i * 3 + 2] != m.tri[t * 3 + 2]) {
                bad++;
                continue;
            }
            float uu = s.u[i], vv = s.v[i];
            if (!(uu >= -1e-5f && vv >= -1e-5f && uu + vv <= 1f + 1e-5f)) {
                outside++;
            }
            if (s.part[i] != partOf[t]) {
                wrongPart++;
            }
        }
        if (bad > 0) out.add(bad + " particles on triangles that don't exist");
        if (outside > 0) out.add(outside + " particles off their triangle");
        if (wrongPart > 0) out.add(wrongPart + " particles with the wrong body part");
        return out;
    }

    static double hash01(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }
}
