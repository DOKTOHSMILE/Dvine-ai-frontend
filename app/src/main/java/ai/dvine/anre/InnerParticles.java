package ai.dvine.anre;

import ai.dvine.human.HumanAsset;

/**
 * Particles on the inner anatomy (bones, muscles, organs, vessels) for the
 * anatomical view. Their corner indices are offset by the skin's vertex
 * count, because the renderer keeps skin and inner vertices in one texture.
 * Small structures (finger bones, vessels) get enough particles to read.
 */
public final class InnerParticles {
    public static final int BUDGET = 16000;

    private InnerParticles() {
    }

    public static ParticleSet build(HumanAsset a, int budget) {
        if (a.inner == null) {
            return null;
        }
        int nt = a.inner.tri.length / 3;
        int[] parts = new int[nt];
        double[] area = new double[a.innerNames.length];
        double total = 0;
        float[] p = a.inner.pos;
        for (int t = 0; t < nt; t++) {
            int i = a.inner.tri[t * 3] * 3, j = a.inner.tri[t * 3 + 1] * 3, k = a.inner.tri[t * 3 + 2] * 3;
            parts[t] = a.inner.region[a.inner.tri[t * 3]] & 0xff;
            double ux = p[j] - p[i], uy = p[j + 1] - p[i + 1], uz = p[j + 2] - p[i + 2];
            double vx = p[k] - p[i], vy = p[k + 1] - p[i + 1], vz = p[k + 2] - p[i + 2];
            double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            double ar = 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
            area[parts[t]] += ar;
            total += ar;
        }
        final double[] boost = new double[area.length];
        for (int i = 0; i < area.length; i++) {
            double share = total <= 0 ? 0 : area[i] / total * budget;
            boost[i] = share <= 0 ? 0 : Math.max(1.0, 60.0 / share);
        }
        ParticleSet s = ParticleSurfaceMapper.sample(a.inner, parts, budget, part -> (float) boost[part], null, 0x494E4EL);
        int offset = a.mesh.vertexCount();
        for (int i = 0; i < s.corners.length; i++) {
            s.corners[i] += offset;
        }
        // the order slot carries the layer (skeleton, muscles, organs, vessels) for the shader
        for (int i = 0; i < s.count; i++) {
            s.order[i] = a.innerLayer[s.part[i]];
        }
        return s;
    }
}
