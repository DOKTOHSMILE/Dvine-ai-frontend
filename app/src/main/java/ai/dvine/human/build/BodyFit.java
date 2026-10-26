package ai.dvine.human.build;

import ai.dvine.face.nano.DotBody;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.Regions;
import ai.dvine.human.mesh.TriMesh;
import ai.dvine.human.rig.BodyProfile;
import ai.dvine.human.rig.Skeleton;

/**
 * Fits a body sculpted in the blockout layout to the body-dimension profile.
 * The sculpt's own height (floor to crown, hair excluded) is the one scale
 * source: every profile landmark becomes {@code floor + y · H}. The trunk and
 * legs are re-proportioned by a piecewise-linear map of height whose knots are
 * the joints (ankle, knee, hip, shoulder), so each segment stretches or
 * shortens as a whole and the joints land on the profile's heights; the head
 * is left exactly as sculpted (identity). The arms ride the shoulder, and the
 * upper arm and forearm lengthen together until the T-pose span matches the
 * profile's arm-span ratio; hands and fingers keep their size.
 *
 * The same map moves mesh vertices (blended by their skin weights), the inner
 * anatomy (by the bone each part rides) and the joints, so the rig and the
 * meshes stay in register. {@link #of(HumanAsset)} recovers the map from a
 * built asset, and {@link #unfit} inverts it (back to the sculpt's layout).
 */
public final class BodyFit {
    /** Floor and height of the body, body units (the fit leaves both unchanged). */
    public final float floor, height;
    /** Arm-length factor for the upper arm and forearm. */
    public final float armScale;
    private final float sh;
    private final float[] oldKnot, newKnot;
    private final float shoulderOld, shoulderLift;
    private static final float COS = (float) Math.cos(Math.toRadians(DotBody.BIND_ARM));
    private static final float SIN = (float) Math.sin(Math.toRadians(DotBody.BIND_ARM));

    private BodyFit(Skeleton from, float floor, float height, float[] newKnot, float armScale) {
        this.floor = floor;
        this.height = height;
        this.armScale = armScale;
        this.sh = from.sh;
        float[] j = from.joint;
        shoulderOld = j[DotBody.ARM_L * 3 + 1];
        oldKnot = new float[]{floor, j[DotBody.FOOT_L * 3 + 1], j[DotBody.SHIN_L * 3 + 1], j[DotBody.THIGH_L * 3 + 1],
                shoulderOld, j[DotBody.HEAD * 3 + 1]};
        this.newKnot = newKnot;
        for (int k = 1; k < newKnot.length; k++) {
            if (!(newKnot[k] > newKnot[k - 1]) || !(oldKnot[k] > oldKnot[k - 1])) {
                throw new IllegalStateException("joints out of order at knot " + k);
            }
        }
        shoulderLift = newKnot[4] - shoulderOld;
    }

    /** The fit of a freshly sculpted body ({@code solid}: its first {@code solidCount} vertices) to {@code profile}. */
    static BodyFit fit(BodyProfile profile, Skeleton from, TriMesh solid, int solidCount) {
        float[] ext = extent(solid.pos, solid.region, solidCount);
        float lo = ext[0], h = ext[1] - ext[0];
        float[] j = from.joint;
        float[] knots = {lo, lo + profile.fit("ankle_center") * h, lo + profile.fit("knee_center") * h,
                lo + profile.fit("hip_joint_center") * h, lo + profile.fit("shoulder_joint_center") * h, j[DotBody.HEAD * 3 + 1]};
        // T-pose half span = shoulder joint x + k · wrist + the hand's reach past the wrist
        float shoulderY = j[DotBody.ARM_L * 3 + 1];
        float reach = 0f;
        for (int v = 0; v < solidCount; v++) {
            if (Skeleton.isFinger(solid.bone0[v])) {
                float x = solid.pos[v * 3], y = solid.pos[v * 3 + 1];
                int side = x >= 0f ? 1 : -1;
                reach = Math.max(reach, (side * x - from.sh) * COS - (y - shoulderY) * SIN - Skeleton.WRIST);
            }
        }
        float want = profile.spanRatio * h * 0.5f;
        float k = Math.max(0.85f, Math.min(1.2f, (want - from.sh - reach) / Skeleton.WRIST));
        return new BodyFit(from, lo, h, knots, k);
    }

    /** The fit a built asset carries: its fitted joints against the blockout layout. */
    public static BodyFit of(HumanAsset a) {
        Skeleton from = new Skeleton(a.female);
        float[] j = a.joints;
        int solids = a.mesh.vertexCount();
        for (int i = 0; a.lines != null && i < a.lines.length; i++) {
            solids = Math.min(solids, a.lines[i]);
        }
        float[] ext = extent(a.mesh.pos, a.mesh.region, solids);
        float[] knots = {ext[0], j[DotBody.FOOT_L * 3 + 1], j[DotBody.SHIN_L * 3 + 1], j[DotBody.THIGH_L * 3 + 1],
                j[DotBody.ARM_L * 3 + 1], j[DotBody.HEAD * 3 + 1]};
        float elbowOld = dist(from.joint, DotBody.ARM_L, DotBody.FORE_L), elbowNew = dist(j, DotBody.ARM_L, DotBody.FORE_L);
        return new BodyFit(from, ext[0], ext[1] - ext[0], knots, elbowNew / elbowOld);
    }

    private static float[] extent(float[] pos, byte[] region, int count) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int v = 0; v < count; v++) {
            if (region != null && region[v] == Regions.HAIR) {
                continue;
            }
            lo = Math.min(lo, pos[v * 3 + 1]);
            hi = Math.max(hi, pos[v * 3 + 1]);
        }
        return new float[]{lo, hi};
    }

    private static float dist(float[] j, int a, int b) {
        float dx = j[b * 3] - j[a * 3], dy = j[b * 3 + 1] - j[a * 3 + 1], dz = j[b * 3 + 2] - j[a * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The piecewise-linear height map (identity above the head joint and below the floor). */
    public float warp(float y) {
        return map(y, oldKnot, newKnot);
    }

    private static float map(float y, float[] from, float[] to) {
        if (y <= from[0] || y >= from[from.length - 1]) {
            return y;
        }
        int k = 1;
        while (y > from[k]) {
            k++;
        }
        float t = (y - from[k - 1]) / (from[k] - from[k - 1]);
        return to[k - 1] + t * (to[k] - to[k - 1]);
    }

    static boolean armChain(int b) {
        return b >= DotBody.ARM_L && b <= DotBody.HAND_R || Skeleton.isFinger(b);
    }

    /** How far a blockout point riding {@code bone} moves; written into {@code d}. */
    public void offset(int bone, float x, float y, float z, float[] d) {
        if (bone == DotBody.HEAD) {
            d[0] = d[1] = d[2] = 0f;
        } else if (armChain(bone)) {
            int side = x >= 0f ? 1 : -1;
            float along = (side * x - sh) * COS - (y - shoulderOld) * SIN;
            float e = (armScale - 1f) * Math.max(0f, Math.min(Skeleton.WRIST, along));
            d[0] = side * e * COS;
            d[1] = shoulderLift - e * SIN;
            d[2] = 0f;
        } else {
            d[0] = 0f;
            d[1] = warp(y) - y;
            d[2] = 0f;
        }
    }

    /** The inverse of {@link #offset}: a fitted point riding {@code bone} → where it was in the sculpt's layout. */
    public float[] unfit(int bone, float x, float y, float z) {
        if (bone == DotBody.HEAD) {
            return new float[]{x, y, z};
        }
        if (armChain(bone)) {
            int side = x >= 0f ? 1 : -1;
            float yo = y - shoulderLift;
            float along = (side * x - sh) * COS - (yo - shoulderOld) * SIN;
            // along_fitted = along + (k − 1)·clamp(along, 0, wrist)
            float a0 = along <= 0f ? along : along <= armScale * Skeleton.WRIST ? along / armScale
                    : along - (armScale - 1f) * Skeleton.WRIST;
            float e = along - a0;
            return new float[]{x - side * e * COS, yo + e * SIN, z};
        }
        return new float[]{x, map(y, newKnot, oldKnot), z};
    }

    /** {@link #unfit} for a skinned vertex blended between two bones (solved by fixed-point iteration). */
    public float[] unfit(int bone0, int bone1, float w, float x, float y, float z) {
        float[] p = {x, y, z}, a = new float[3], b = new float[3];
        for (int i = 0; i < 8; i++) {
            offset(bone0, p[0], p[1], p[2], a);
            offset(bone1, p[0], p[1], p[2], b);
            for (int k = 0; k < 3; k++) {
                p[k] = (k == 0 ? x : k == 1 ? y : z) - (w * a[k] + (1f - w) * b[k]);
            }
        }
        return p;
    }

    /** The fitted bind joints. */
    float[] joints(Skeleton from) {
        float[] j = from.joint.clone(), d = new float[3];
        for (int b = 0; b < Skeleton.COUNT; b++) {
            offset(b, j[b * 3], j[b * 3 + 1], j[b * 3 + 2], d);
            j[b * 3] += d[0];
            j[b * 3 + 1] += d[1];
            j[b * 3 + 2] += d[2];
        }
        return j;
    }

    /** Moves a skinned mesh's vertices by their blended bone offsets and recomputes its normals. */
    void apply(TriMesh m) {
        float[] a = new float[3], b = new float[3];
        for (int v = 0, n = m.vertexCount(); v < n; v++) {
            float x = m.pos[v * 3], y = m.pos[v * 3 + 1], z = m.pos[v * 3 + 2];
            float w = m.weight0[v];
            offset(m.bone0[v], x, y, z, a);
            offset(m.bone1[v], x, y, z, b);
            m.pos[v * 3] += w * a[0] + (1f - w) * b[0];
            m.pos[v * 3 + 1] += w * a[1] + (1f - w) * b[1];
            m.pos[v * 3 + 2] += w * a[2] + (1f - w) * b[2];
        }
        m.computeNormals();
    }

    /**
     * Moves the inner anatomy (each vertex by the bone its part rides), so the
     * bones lengthen with the limbs they sit in. Normals are kept: the bind
     * reshaping is a small stretch along the limb.
     */
    void applyInner(TriMesh m) {
        float[] d = new float[3];
        for (int v = 0, n = m.vertexCount(); v < n; v++) {
            offset(m.bone0[v], m.pos[v * 3], m.pos[v * 3 + 1], m.pos[v * 3 + 2], d);
            m.pos[v * 3] += d[0];
            m.pos[v * 3 + 1] += d[1];
            m.pos[v * 3 + 2] += d[2];
        }
    }
}
