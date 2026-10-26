package ai.dvine.human;

import ai.dvine.face.nano.DotBody;
import ai.dvine.human.rig.HumanRig;
import ai.dvine.human.rig.Skeleton;

/**
 * One living copy of a {@link HumanAsset}: each frame it applies the facial
 * blend shapes, turns the eyes, sways the hair, skins every vertex to the
 * rig (two bones each), sets the body on the floor and computes normals.
 * Buffers are allocated once; nothing is allocated per frame. Positions come
 * out in body units; {@link #toCamera} gives the stage's camera units.
 */
public final class HumanInstance {
    /** Body units → camera units, as the nano-dot body uses (feet land on {@link DotBody#FLOOR_OUT}). */
    public static final float OUT = DotBody.OUT;
    public static final float CENTRE = -1.6f;
    private static final float FLOOR = -4.2f;

    public final HumanAsset asset;
    public final HumanRig rig;
    /** Current skinned positions and normals (body units), xyz per vertex. */
    public final float[] pos;
    public final float[] nor;
    /** The inside (anatomical view): skinned positions and normals of the inner anatomy, or null when the asset has none. */
    public final float[] innerPos;
    public final float[] innerNor;
    /** Blend-shape weights, one per asset morph (0..1). */
    public final float[] weights;
    private final float[] morphed;
    private final float[] bindNor;
    private float ground;
    private boolean groundSet;
    /** Frame counter, bumped by every {@link #update}; renderers upload only when it changes. */
    public volatile int frame;

    public HumanInstance(HumanAsset asset) {
        this.asset = asset;
        this.rig = new HumanRig(new Skeleton(asset.female));
        int n = asset.mesh.vertexCount();
        pos = new float[n * 3];
        nor = new float[n * 3];
        morphed = new float[n * 3];
        bindNor = asset.mesh.nor.clone();
        weights = new float[asset.morphs.length];
        innerPos = asset.inner == null ? null : new float[asset.inner.pos.length];
        innerNor = asset.inner == null ? null : new float[asset.inner.pos.length];
    }

    /** Sets a blend-shape weight by name (ignored when the asset has no such shape). */
    public void setWeight(String name, float w) {
        int i = asset.morph(name);
        if (i >= 0) {
            weights[i] = Math.max(0f, Math.min(1f, w));
        }
    }

    /** Poses the body and face for {@code time} seconds. */
    public void update(float time) {
        rig.update(time);
        float[] bind = asset.mesh.pos;
        System.arraycopy(bind, 0, morphed, 0, bind.length);
        for (int m = 0; m < weights.length; m++) {
            float w = weights[m];
            if (w <= 0.001f) {
                continue;
            }
            HumanAsset.Morph mo = asset.morphs[m];
            for (int k = 0; k < mo.index.length; k++) {
                int v = mo.index[k] * 3;
                morphed[v] += mo.delta[k * 3] * w;
                morphed[v + 1] += mo.delta[k * 3 + 1] * w;
                morphed[v + 2] += mo.delta[k * 3 + 2] * w;
            }
        }
        turnEyes();
        sway(time);
        skin();
        skinInner();
        settle();
        frame++;
    }

    /** Rotates each eyeball and its iris rings about the eye's centre by the rig's gaze angles. */
    private void turnEyes() {
        float yaw = (float) Math.toRadians(rig.eyeYaw), pitch = (float) Math.toRadians(-rig.eyePitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        for (int e = 0; e < 2; e++) {
            float ex = asset.eyeCenter[e * 3], ey = asset.eyeCenter[e * 3 + 1], ez = asset.eyeCenter[e * 3 + 2];
            for (int[] range : new int[][]{{asset.eyeRange[e * 2], asset.eyeRange[e * 2 + 1]},
                    {asset.irisRange[e * 2], asset.irisRange[e * 2 + 1]}}) {
                for (int v = range[0]; v < range[0] + range[1]; v++) {
                    float x = morphed[v * 3] - ex, y = morphed[v * 3 + 1] - ey, z = morphed[v * 3 + 2] - ez;
                    float y1 = y * cp - z * sp, z1 = y * sp + z * cp;
                    float x2 = x * cy + z1 * sy, z2 = -x * sy + z1 * cy;
                    morphed[v * 3] = ex + x2;
                    morphed[v * 3 + 1] = ey + y1;
                    morphed[v * 3 + 2] = ez + z2;
                }
            }
        }
    }

    private void sway(float t) {
        float[] s = asset.sway;
        if (s == null) {
            return;
        }
        for (int v = 0; v < s.length; v++) {
            float a = s[v];
            if (a <= 0f) {
                continue;
            }
            float ph = v * 0.37f;
            morphed[v * 3] += a * 0.12f * (float) Math.sin(t * 1.1f + ph);
            morphed[v * 3 + 1] += a * 0.2f * (float) Math.sin(t * 1.5f + ph * 1.3f);
        }
    }

    /** Linear-blend skinning with two bones per vertex. */
    private void skin() {
        float[] R = rig.rot, T = rig.move;
        byte[] b0 = asset.mesh.bone0, b1 = asset.mesh.bone1;
        float[] w0 = asset.mesh.weight0;
        int n = w0.length;
        for (int v = 0; v < n; v++) {
            int i = v * 3;
            float x = morphed[i], y = morphed[i + 1], z = morphed[i + 2];
            float nx = bindNor[i], ny = bindNor[i + 1], nz = bindNor[i + 2];
            int a = (b0[v] & 0xff), b = (b1[v] & 0xff);
            float wa = w0[v], wb = 1f - wa;
            int ra = a * 9, rb = b * 9, ta = a * 3, tb = b * 3;
            pos[i] = wa * (R[ra] * x + R[ra + 1] * y + R[ra + 2] * z + T[ta]) + wb * (R[rb] * x + R[rb + 1] * y + R[rb + 2] * z + T[tb]);
            pos[i + 1] = wa * (R[ra + 3] * x + R[ra + 4] * y + R[ra + 5] * z + T[ta + 1]) + wb * (R[rb + 3] * x + R[rb + 4] * y + R[rb + 5] * z + T[tb + 1]);
            pos[i + 2] = wa * (R[ra + 6] * x + R[ra + 7] * y + R[ra + 8] * z + T[ta + 2]) + wb * (R[rb + 6] * x + R[rb + 7] * y + R[rb + 8] * z + T[tb + 2]);
            float mx = wa * (R[ra] * nx + R[ra + 1] * ny + R[ra + 2] * nz) + wb * (R[rb] * nx + R[rb + 1] * ny + R[rb + 2] * nz);
            float my = wa * (R[ra + 3] * nx + R[ra + 4] * ny + R[ra + 5] * nz) + wb * (R[rb + 3] * nx + R[rb + 4] * ny + R[rb + 5] * nz);
            float mz = wa * (R[ra + 6] * nx + R[ra + 7] * ny + R[ra + 8] * nz) + wb * (R[rb + 6] * nx + R[rb + 7] * ny + R[rb + 8] * nz);
            float l = (float) Math.sqrt(mx * mx + my * my + mz * mz);
            if (l > 1e-9f) {
                nor[i] = mx / l;
                nor[i + 1] = my / l;
                nor[i + 2] = mz / l;
            }
        }
    }

    /** Each inner structure rides its bone rigidly (bones, organs and muscles inside the moving body). */
    private void skinInner() {
        if (innerPos == null) {
            return;
        }
        float[] R = rig.rot, T = rig.move, p = asset.inner.pos, n = asset.inner.nor;
        byte[] b0 = asset.inner.bone0;
        for (int v = 0; v < b0.length; v++) {
            int i = v * 3, r = (b0[v] & 0xff) * 9, t = (b0[v] & 0xff) * 3;
            float x = p[i], y = p[i + 1], z = p[i + 2];
            innerPos[i] = R[r] * x + R[r + 1] * y + R[r + 2] * z + T[t];
            innerPos[i + 1] = R[r + 3] * x + R[r + 4] * y + R[r + 5] * z + T[t + 1];
            innerPos[i + 2] = R[r + 6] * x + R[r + 7] * y + R[r + 8] * z + T[t + 2];
            innerNor[i] = R[r] * n[i] + R[r + 1] * n[i + 1] + R[r + 2] * n[i + 2];
            innerNor[i + 1] = R[r + 3] * n[i] + R[r + 4] * n[i + 1] + R[r + 5] * n[i + 2];
            innerNor[i + 2] = R[r + 6] * n[i] + R[r + 7] * n[i + 1] + R[r + 8] * n[i + 2];
        }
    }

    /** Keeps the lowest point on the floor (it never sinks; it settles gently), then adds travel and lift. */
    private void settle() {
        float low = Float.MAX_VALUE;
        int solids = asset.lines.length > 0 ? firstLine() : pos.length / 3;
        for (int v = 0; v < solids; v++) {
            low = Math.min(low, pos[v * 3 + 1]);
        }
        float want = FLOOR - low;
        if (!groundSet || want > ground || Math.abs(want - ground) > 1.5f) {
            ground = want;
            groundSet = true;
        } else {
            ground += (want - ground) * 0.6f;
        }
        float tx = rig.travel(), ly = rig.lift();
        for (int i = 0; i < pos.length; i += 3) {
            pos[i] += tx;
            pos[i + 1] += ground + ly;
        }
        if (innerPos != null) {
            for (int i = 0; i < innerPos.length; i += 3) {
                innerPos[i] += tx;
                innerPos[i + 1] += ground + ly;
            }
        }
        for (int b = 0; b < HumanRig.N; b++) {
            rig.joint[b * 3] += tx;
            rig.joint[b * 3 + 1] += ground + ly;
        }
    }

    private int firstLineCache = -1;

    private int firstLine() {
        if (firstLineCache < 0) {
            int f = pos.length / 3;
            for (int v : asset.lines) {
                f = Math.min(f, v);
            }
            firstLineCache = f;
        }
        return firstLineCache;
    }

    /**
     * This frame's shape before skinning (blend shapes, eye turn and hair sway
     * applied): the rest shape the skinning itself is judged against. Read only.
     */
    public float[] unskinned() {
        return morphed;
    }

    /** Number of surface (triangle) vertices; feature-line vertices follow them. */
    public int solidCount() {
        return firstLine();
    }

    // ------------------------------------------------------------ camera units

    /** Body units → camera units, in place. */
    public static void toCamera(float[] p, int count, float[] out) {
        for (int i = 0; i < count * 3; i += 3) {
            out[i] = p[i] * OUT;
            out[i + 1] = (p[i + 1] - CENTRE) * OUT;
            out[i + 2] = p[i + 2] * OUT;
        }
    }

    // ------------------------------------------------------------ picking

    /** A hit on the body: the triangle, the body part, the contact point (world, body units) and the distance. */
    public static final class Hit {
        public final int triangle;
        public final int region;
        public final float[] point;
        public final float distance;

        Hit(int triangle, int region, float[] point, float distance) {
            this.triangle = triangle;
            this.region = region;
            this.point = point;
            this.distance = distance;
        }
    }

    /**
     * Casts a ray (origin and direction in body units) against the current
     * skinned triangles and returns the nearest front-facing hit, or null.
     */
    public Hit pick(float ox, float oy, float oz, float dx, float dy, float dz) {
        int[] tri = asset.mesh.tri;
        float best = Float.MAX_VALUE;
        int bt = -1;
        for (int t = 0; t < tri.length; t += 3) {
            int a = tri[t] * 3, b = tri[t + 1] * 3, c = tri[t + 2] * 3;
            float e1x = pos[b] - pos[a], e1y = pos[b + 1] - pos[a + 1], e1z = pos[b + 2] - pos[a + 2];
            float e2x = pos[c] - pos[a], e2y = pos[c + 1] - pos[a + 1], e2z = pos[c + 2] - pos[a + 2];
            float px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
            float det = e1x * px + e1y * py + e1z * pz;
            if (det < 1e-12f) {
                continue; // back face or parallel: only surfaces facing the viewer can be touched
            }
            float inv = 1f / det;
            float sx = ox - pos[a], sy = oy - pos[a + 1], sz = oz - pos[a + 2];
            float u = (sx * px + sy * py + sz * pz) * inv;
            if (u < 0f || u > 1f) {
                continue;
            }
            float qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
            float v = (dx * qx + dy * qy + dz * qz) * inv;
            if (v < 0f || u + v > 1f) {
                continue;
            }
            float d = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (d > 1e-5f && d < best) {
                best = d;
                bt = t / 3;
            }
        }
        if (bt < 0) {
            return null;
        }
        float[] p = {ox + dx * best, oy + dy * best, oz + dz * best};
        // the region of the corner nearest the hit point
        int region = asset.mesh.region[tri[bt * 3]];
        float bd = Float.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            int v = tri[bt * 3 + k];
            float ex = pos[v * 3] - p[0], ey = pos[v * 3 + 1] - p[1], ez = pos[v * 3 + 2] - p[2];
            float d = ex * ex + ey * ey + ez * ez;
            if (d < bd) {
                bd = d;
                region = asset.mesh.region[v];
            }
        }
        return new Hit(bt, region, p, best);
    }

    /**
     * The contact point in the local frame of the bone that carries it (so a
     * touch can be remembered relative to the body part, wherever it moves).
     */
    public float[] toBoneLocal(int bone, float[] world) {
        float[] R = rig.rot, J = rig.joint;
        float x = world[0] - J[bone * 3], y = world[1] - J[bone * 3 + 1], z = world[2] - J[bone * 3 + 2];
        int r = bone * 9;
        return new float[]{R[r] * x + R[r + 3] * y + R[r + 6] * z, R[r + 1] * x + R[r + 4] * y + R[r + 7] * z,
                R[r + 2] * x + R[r + 5] * y + R[r + 8] * z};
    }
}
