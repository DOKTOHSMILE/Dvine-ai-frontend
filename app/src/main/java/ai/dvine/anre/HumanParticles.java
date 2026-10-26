package ai.dvine.anre;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.Regions;

/**
 * The particle skin of one persona: particles on every surface triangle
 * (the face several times denser) plus particles along the feature lines
 * (eyelids, brows, lip line, iris rings and, for Ivie, the hair strands).
 * Deterministic for a given asset and budget.
 */
public final class HumanParticles {
    /** Surface particles for a full body; the face takes about a third of them. */
    public static final int BUDGET = 26000;
    /** Spacing of particles along feature lines (body units). */
    static final float LINE_SPACING = 0.0075f;
    static final long SEED = 0x414E5245L; // "ANRE"

    private HumanParticles() {
    }

    public static ParticleSet build(HumanAsset a, int budget) {
        int[] parts = ParticleSurfaceMapper.trianglesByRegion(a.mesh);
        for (int t = 0; t < parts.length; t++) {
            if (parts[t] == Regions.TEETH) {
                parts[t] = Regions.TEETH; // kept: they show when the mouth opens (sparser, below)
            }
        }
        ParticleSurfaceMapper.Density d = part -> part == Regions.TEETH ? 0.6f : ParticleSurfaceMapper.HUMAN.of(part);
        ParticleSet surface = ParticleSurfaceMapper.sample(a.mesh, parts, budget, d, null, SEED);
        if (a.lines.length == 0) {
            return surface;
        }
        ParticleSet lines = ParticleSurfaceMapper.along(a.mesh.pos, a.mesh.region, a.lines, LINE_SPACING, a.mesh.bounds(), SEED + 1);
        return ParticleSet.concat(surface, lines);
    }

    /** The part ids per triangle used by {@link #build} (for validation). */
    public static int[] parts(HumanAsset a) {
        return ParticleSurfaceMapper.trianglesByRegion(a.mesh);
    }
}
