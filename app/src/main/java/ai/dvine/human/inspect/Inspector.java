package ai.dvine.human.inspect;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.face.Look;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;
import ai.dvine.human.Regions;
import ai.dvine.human.build.AssetBuilder;
import ai.dvine.human.mesh.MeshValidator;

/**
 * Self-visual inspection and controlled reconstruction. It keeps the
 * approved reference (the shipped asset, verified by its checksum) and never
 * changes it. Three modes:
 * <ul>
 *   <li>NORMAL: render and check;</li>
 *   <li>LOCAL_REPAIR: undo a bad deformation (blend shapes off, back to a
 *   standing pose, re-skin) and check again;</li>
 *   <li>RECONSTRUCTION: rebuild the asset from its recipe in the background,
 *   validate it, and accept it only if the face keeps its identity (or the
 *   user approved a new look).</li>
 * </ul>
 */
public final class Inspector {
    public enum Mode { NORMAL, LOCAL_REPAIR, RECONSTRUCTION }

    /** Landmark ratios may drift at most this much (fraction) in a rebuild before identity is lost. */
    public static final float IDENTITY_TOLERANCE = 0.04f;
    /**
     * An edge stretched beyond this many times its rest length is a torn
     * deformation (a wrong bone or weight). Ordinary skin stretch stays far
     * below it: under 2.6 in everyday poses, up to about 5.7 in the crotch at
     * the deepest hip bends (sitting, squatting), where two-bone linear skinning
     * reaches its limit.
     */
    public static final float MAX_STRETCH = 6f;

    public final HumanAsset approved;
    private final float[] approvedPos;
    public Mode mode = Mode.NORMAL;
    /** Rendering problems the renderer reports (GL errors, a lost context it could not restore). */
    public volatile int renderErrors;
    private String lastReport = "not checked yet";

    public Inspector(HumanAsset approved) {
        this.approved = approved;
        this.approvedPos = approved.mesh.pos.clone();
    }

    /** Everything wrong with an instance right now; empty when healthy. */
    public List<String> inspect(HumanInstance in) {
        List<String> out = new ArrayList<String>();
        HumanAsset a = in.asset;
        MeshValidator.Report r = MeshValidator.check(a.mesh, true, ai.dvine.human.rig.Skeleton.COUNT, Regions.COUNT);
        if (!r.ok()) {
            out.add("mesh: " + r);
        }
        // every body part must still be mapped to some vertices
        boolean[] seen = new boolean[Regions.COUNT];
        for (byte b : a.mesh.region) {
            seen[b & 0xff] = true;
        }
        for (int id = 0; id < Regions.COUNT; id++) {
            if (!seen[id] && !(id == Regions.HAIR && !a.female)) {
                out.add("body part " + Regions.key(id) + " has no geometry");
            }
        }
        // skin deformation: finite and not over-stretched (judged against this frame's
        // blend-shaped rest shape, since an open mouth is meant to stretch the lip seam)
        int[] e = a.mesh.edge;
        float[] rest0 = in.unskinned();
        int stretched = 0;
        for (int i = 0; i < e.length; i += 2) {
            float rest = d(rest0, e[i], e[i + 1]), now = d(in.pos, e[i], e[i + 1]);
            if (!Float.isFinite(now)) {
                out.add("non-finite skinned position");
                break;
            }
            if (rest > 1e-6f && now / rest > MAX_STRETCH) {
                stretched++;
            }
        }
        if (stretched > 0) {
            out.add(stretched + " edges torn (bad skin deformation)");
        }
        // the eyes stay inside the head
        float eyeSpan = d3(a.eyeCenter, 0, 1);
        if (eyeSpan < 0.15f || eyeSpan > 0.3f) {
            out.add("eyes misplaced");
        }
        out.addAll(in.rig.validate());
        // proportions: head about an eighth of the height
        float[] b = a.mesh.bounds();
        float height = b[4] - b[1];
        int crown = a.landmark("crown"), chin = a.landmark("chin");
        if (crown >= 0 && chin >= 0) {
            float head = a.mesh.pos[crown * 3 + 1] - a.mesh.pos[chin * 3 + 1];
            float ratio = head / height;
            if (ratio < 0.1f || ratio > 0.17f) {
                out.add("head-to-body proportion off (" + ratio + ")");
            }
        }
        if (renderErrors > 0) {
            out.add(renderErrors + " rendering errors");
        }
        lastReport = out.isEmpty() ? "all checks passed" : String.join("; ", out);
        return out;
    }

    /** Local repair: blend shapes off, standing pose, re-skin; returns what is still wrong. */
    public List<String> repair(HumanInstance in, float time) {
        mode = Mode.LOCAL_REPAIR;
        java.util.Arrays.fill(in.weights, 0f);
        in.rig.setPose("stand");
        for (int i = 0; i < 40; i++) {
            in.update(time + i / 30f);
        }
        List<String> left = inspect(in);
        mode = left.isEmpty() ? Mode.NORMAL : Mode.LOCAL_REPAIR;
        return left;
    }

    /**
     * Full reconstruction from the recipe (slow: run it off the UI thread).
     * Returns the rebuilt asset if it validates and keeps the approved face's
     * identity, else null. {@code approvedChange} is true only when the user
     * asked for a new look; then identity may change.
     */
    public HumanAsset reconstruct(Look look, AssetBuilder.Budget budget, boolean approvedChange) {
        mode = Mode.RECONSTRUCTION;
        try {
            HumanAsset fresh = new AssetBuilder(approved.female, look, budget).build();
            MeshValidator.Report r = MeshValidator.check(fresh.mesh, true, ai.dvine.human.rig.Skeleton.COUNT, Regions.COUNT);
            if (!r.ok()) {
                lastReport = "rebuild rejected: " + r;
                return null;
            }
            if (!approvedChange && !sameIdentity(fresh)) {
                lastReport = "rebuild rejected: the face would change";
                return null;
            }
            lastReport = "rebuilt and verified";
            return fresh;
        } catch (IllegalStateException e) {
            lastReport = "rebuild rejected: " + e.getMessage();
            return null;
        } finally {
            mode = Mode.NORMAL;
        }
    }

    /** Whether a rebuilt face keeps the approved proportions (eyes, nose, mouth, chin). */
    public boolean sameIdentity(HumanAsset other) {
        float[] mine = signature(approved, approvedPos), theirs = signature(other, other.mesh.pos);
        if (mine == null || theirs == null) {
            return false;
        }
        for (int i = 0; i < mine.length; i++) {
            if (Math.abs(mine[i] - theirs[i]) > IDENTITY_TOLERANCE * Math.max(0.05f, Math.abs(mine[i]))) {
                return false;
            }
        }
        return true;
    }

    /** Face proportions relative to the distance between the eyes. */
    static float[] signature(HumanAsset a, float[] p) {
        String[] names = {"eye left", "eye right", "nose tip", "chin", "mouth left", "mouth right", "crown"};
        int[] v = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            v[i] = a.landmark(names[i]);
            if (v[i] < 0) {
                return null;
            }
        }
        float eyes = d(p, v[0], v[1]);
        return new float[]{d(p, v[2], v[3]) / eyes, d(p, v[4], v[5]) / eyes, d(p, v[0], v[2]) / eyes,
                d(p, v[6], v[3]) / eyes, d(p, v[1], v[4]) / eyes};
    }

    public String lastReport() {
        return lastReport;
    }

    private static float d(float[] p, int a, int b) {
        float x = p[a * 3] - p[b * 3], y = p[a * 3 + 1] - p[b * 3 + 1], z = p[a * 3 + 2] - p[b * 3 + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float d3(float[] p, int a, int b) {
        return d(p, a, b);
    }
}
