package ai.dvine.face;

/**
 * ============================================================================
 * DVINE PROTOCOL - LATTICE CONTROL MATRIX (FFD TENSOR FIELD)
 * ============================================================================
 * Manages a 3D parametric control lattice (4x5x4 nodes) that wraps around
 * the facial mesh, enabling organic micro-deformations, expressions, and speech 
 * articulation without high computational overhead on mobile devices.
 * ============================================================================
 */
public final class LatticeControlMatrix {
    private static final int DIM_X = 4;
    private static final int DIM_Y = 5;
    private static final int DIM_Z = 4;
    private static final int TOTAL_NODES = DIM_X * DIM_Y * DIM_Z;

    private final float[] displacements = new float[TOTAL_NODES * 3];
    private final float minX = -0.85f, maxX = 0.90f;
    private final float minY = -0.70f, maxY = 1.15f;
    private final float minZ = -0.75f, maxZ = 0.75f;

    public LatticeControlMatrix() {
        reset();
    }

    public void reset() {
        java.util.Arrays.fill(displacements, 0f);
    }

    public void update(float smile, float jaw, float browL, float browR, float time, boolean obi) {
        float breath = (float) Math.sin(time * 1.35f) * 0.008f;
        float speechWave = (float) Math.sin(time * 24f);

        for (int y = 0; y < DIM_Y; y++) {
            float normY = y / (float) (DIM_Y - 1);
            for (int x = 0; x < DIM_X; x++) {
                for (int z = 0; z < DIM_Z; z++) {
                    int idx = ((y * DIM_X + x) * DIM_Z + z) * 3;

                    float dx = 0f;
                    float dy = breath;
                    float dz = breath * 0.5f;

                    if (y <= 1) {
                        float jawFactor = (2 - y) * 0.5f;
                        dy -= jaw * 0.18f * jawFactor;
                        dx += speechWave * 0.015f * jawFactor * (x == 1 || x == 2 ? 1f : 0.5f);
                    }

                    if (y == 2) {
                        float cheekFactor = Math.abs(x - 1.5f) / 1.5f;
                        dx += (x < 2 ? -1f : 1f) * smile * 0.06f * cheekFactor;
                        dy += smile * 0.04f;
                    }

                    if (y >= 3) {
                        float browVal = (z < 2 ? browL : browR);
                        dy += browVal * 0.05f * (y == 4 ? 1.2f : 0.8f);
                    }

                    displacements[idx] = dx;
                    displacements[idx + 1] = dy;
                    displacements[idx + 2] = dz;
                }
            }
        }
    }

    public void deform(float[] restVerts, float[] outVerts) {
        int count = restVerts.length / 3;
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            float rx = restVerts[o];
            float ry = restVerts[o + 1];
            float rz = restVerts[o + 2];

            float s = (rx - minX) / (maxX - minX);
            float t = (ry - minY) / (maxY - minY);
            float u = (rz - minZ) / (maxZ - minZ);

            s = clamp(s, 0f, 1f);
            t = clamp(t, 0f, 1f);
            u = clamp(u, 0f, 1f);

            float fx = s * (DIM_X - 1);
            float fy = t * (DIM_Y - 1);
            float fz = u * (DIM_Z - 1);

            int x0 = (int) Math.floor(fx);
            int y0 = (int) Math.floor(fy);
            int z0 = (int) Math.floor(fz);

            int x1 = Math.min(x0 + 1, DIM_X - 1);
            int y1 = Math.min(y0 + 1, DIM_Y - 1);
            int z1 = Math.min(z0 + 1, DIM_Z - 1);

            float ds = fx - x0;
            float dt = fy - y0;
            float du = fz - z0;

            float accumDx = 0f;
            float accumDy = 0f;
            float accumDz = 0f;

            for (int iz = 0; iz <= 1; iz++) {
                int cz = (iz == 0) ? z0 : z1;
                float wz = (iz == 0) ? (1f - du) : du;

                for (int iy = 0; iy <= 1; iy++) {
                    int cy = (iy == 0) ? y0 : y1;
                    float wy = (iy == 0) ? (1f - dt) : dt;

                    for (int ix = 0; ix <= 1; ix++) {
                        int cx = (ix == 0) ? x0 : x1;
                        float wx = (ix == 0) ? (1f - ds) : ds;

                        float weight = wx * wy * wz;
                        int nodeIdx = ((cy * DIM_X + cx) * DIM_Z + cz) * 3;

                        accumDx += displacements[nodeIdx] * weight;
                        accumDy += displacements[nodeIdx + 1] * weight;
                        accumDz += displacements[nodeIdx + 2] * weight;
                    }
                }
            }

            outVerts[o] = rx + accumDx;
            outVerts[o + 1] = ry + accumDy;
            outVerts[o + 2] = rz + accumDz;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}


