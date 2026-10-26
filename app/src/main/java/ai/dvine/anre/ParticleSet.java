package ai.dvine.anre;

/**
 * Particles attached to a triangle surface. Particle i sits on triangle
 * {@code tri[i]} at barycentric (u, v, 1-u-v), so wherever the mesh deforms
 * its position is u·A + v·B + w·C of that triangle's current corners: the
 * particles follow the skin instead of floating on their own. Also kept per
 * particle: the body region (or object component), a size factor, and an
 * order value 0..1 used to sequence assembly and dissolving.
 */
public final class ParticleSet {
    public final int count;
    /** The three mesh vertices each particle rides on (xyz index triples); a line particle repeats its second vertex. */
    public final int[] corners;
    /** The triangle it sits on, or -1 for a particle on a feature line (lids, brows, lips, hair). */
    public final int[] tri;
    public final float[] u;
    public final float[] v;
    /** Region id (human) or component id (object). */
    public final short[] part;
    public final float[] size;
    public final float[] order;
    /** The seed and budget it was sampled with, so a damaged set can be re-made exactly. */
    public final long seed;
    public final int budget;

    /** Joins sets (surface, then lines) into one, in order. */
    public static ParticleSet concat(ParticleSet a, ParticleSet b) {
        ParticleSet s = new ParticleSet(a.count + b.count, a.seed, a.budget + b.budget);
        for (ParticleSet x : new ParticleSet[]{a, b}) {
            int o = x == a ? 0 : a.count;
            System.arraycopy(x.corners, 0, s.corners, o * 3, x.count * 3);
            System.arraycopy(x.tri, 0, s.tri, o, x.count);
            System.arraycopy(x.u, 0, s.u, o, x.count);
            System.arraycopy(x.v, 0, s.v, o, x.count);
            System.arraycopy(x.part, 0, s.part, o, x.count);
            System.arraycopy(x.size, 0, s.size, o, x.count);
            System.arraycopy(x.order, 0, s.order, o, x.count);
        }
        return s;
    }

    ParticleSet(int count, long seed, int budget) {
        this.count = count;
        this.seed = seed;
        this.budget = budget;
        corners = new int[count * 3];
        tri = new int[count];
        u = new float[count];
        v = new float[count];
        part = new short[count];
        size = new float[count];
        order = new float[count];
    }

    /** World positions from the mesh's current vertex positions (xyz per vertex), into out (xyz per particle). */
    public void positions(float[] pos, float[] out) {
        for (int i = 0; i < count; i++) {
            int a = corners[i * 3] * 3, b = corners[i * 3 + 1] * 3, c = corners[i * 3 + 2] * 3;
            float uu = u[i], vv = v[i], ww = 1f - uu - vv;
            out[i * 3] = uu * pos[a] + vv * pos[b] + ww * pos[c];
            out[i * 3 + 1] = uu * pos[a + 1] + vv * pos[b + 1] + ww * pos[c + 1];
            out[i * 3 + 2] = uu * pos[a + 2] + vv * pos[b + 2] + ww * pos[c + 2];
        }
    }
}
