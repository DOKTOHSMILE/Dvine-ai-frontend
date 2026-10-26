package ai.dvine.create;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.dvine.anre.ParticleSet;
import ai.dvine.anre.ParticleSurfaceMapper;
import ai.dvine.human.mesh.TriMesh;

/**
 * Where each of the body's particles goes on an object: points sampled over
 * the object's visible surface (every part gets enough to be recognisable,
 * however small), ordered by build step and then height, and matched by
 * rank to the body particles' own order, so the body leaves feet first,
 * face last, while the object builds part by part.
 * Output per particle: x, y, z (object space, metres), component id plus
 * 256 × material palette index, build order 0..1.
 */
public final class ObjectParticles {
    static final int MIN_PER_PART = 40;

    private ObjectParticles() {
    }

    public static float[] targets(ObjectModel model, ParticleSet body) {
        List<Integer> triPart = new ArrayList<Integer>();
        TriMesh merged = model.merged(triPart);
        int[] parts = new int[triPart.size()];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = triPart.get(i);
        }
        int n = body.count;
        // per-part area, to lift small parts (a headlight, a door handle) to a visible share
        double[] area = new double[model.components.size()];
        double total = 0;
        for (int t = 0; t < parts.length; t++) {
            double a = triangleArea(merged, t);
            area[parts[t]] += a;
            total += a;
        }
        final double[] boost = new double[area.length];
        for (int c = 0; c < area.length; c++) {
            double share = total <= 0 ? 0 : area[c] / total * n;
            boost[c] = share <= 0 ? 0 : Math.max(1.0, MIN_PER_PART / share);
        }
        ParticleSet s = ParticleSurfaceMapper.sample(merged, parts, n, part -> (float) boost[part], (x, y, z, part) -> y, 0x4F424AL);
        if (s.count == 0) {
            return new float[n * 5];
        }
        float[] pos = new float[s.count * 3];
        s.positions(merged.pos, pos);
        // order the samples: build step, then height within the step
        Integer[] idx = new Integer[s.count];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> {
            int sa = model.component(s.part[a]).step, sb = model.component(s.part[b]).step;
            return sa != sb ? sa - sb : Float.compare(pos[a * 3 + 1], pos[b * 3 + 1]);
        });
        // the body's particles in their own order (feet first)
        Integer[] bi = new Integer[n];
        for (int i = 0; i < n; i++) {
            bi[i] = i;
        }
        Arrays.sort(bi, (a, b) -> Float.compare(body.order[a], body.order[b]));
        float[] out = new float[n * 5];
        for (int k = 0; k < n; k++) {
            int smp = idx[(int) ((long) k * s.count / n)];
            int pi = bi[k];
            Component c = model.component(s.part[smp]);
            out[pi * 5] = pos[smp * 3];
            out[pi * 5 + 1] = pos[smp * 3 + 1];
            out[pi * 5 + 2] = pos[smp * 3 + 2];
            out[pi * 5 + 3] = c.id + 256f * Materials.index(c.material);
            out[pi * 5 + 4] = n <= 1 ? 0f : k / (float) (n - 1);
        }
        return out;
    }

    private static double triangleArea(TriMesh m, int t) {
        float[] p = m.pos;
        int a = m.tri[t * 3] * 3, b = m.tri[t * 3 + 1] * 3, c = m.tri[t * 3 + 2] * 3;
        double ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
        double vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }
}
