package ai.ibiko.stage;

import java.util.ArrayList;
import java.util.List;

import ai.ibiko.create.Component;
import ai.ibiko.create.ObjectModel;
import ai.ibiko.geom.TriMesh;

/**
 * A validated design as triangles for the board: position, normal, and material colour.
 * Photographs stay particles. This is the model's own mesh, not a measurement from a photo.
 */
public final class SolidSurface {
    public static final int STRIDE = 9;
    public final float[] verts;
    public final int[] indices;
    public final int triangles;

    private SolidSurface(float[] verts, int[] indices) {
        this.verts = verts;
        this.indices = indices;
        this.triangles = indices.length / 3;
    }

    /** Null when the model has no drawable, finite, in-range triangles. */
    public static SolidSurface pack(ObjectModel model) {
        return pack(model, -1);
    }

    /** {@code selectedId} tints that part. -1 leaves every part in its own material colour. */
    public static SolidSurface pack(ObjectModel model, int selectedId) {
        if (model == null || model.components.isEmpty()) {
            return null;
        }
        float[] bounds = model.bounds();
        float span = Math.max(bounds[3] - bounds[0], Math.max(bounds[4] - bounds[1], bounds[5] - bounds[2]));
        if (!(span > 1e-4f) || !Float.isFinite(span)) {
            return null;
        }
        float cx = (bounds[0] + bounds[3]) / 2f, cy = (bounds[1] + bounds[4]) / 2f, cz = (bounds[2] + bounds[5]) / 2f;
        float scale = 2f / span;
        List<Float> verts = new ArrayList<Float>();
        List<Integer> index = new ArrayList<Integer>();
        for (Component part : model.components) {
            if (!part.visible || part.mesh == null) {
                continue;
            }
            String why = reject(part.mesh);
            if (why != null) {
                return null;
            }
            TriMesh mesh = part.mesh;
            if (mesh.nor == null || mesh.nor.length != mesh.pos.length) {
                mesh.computeNormals();
            }
            float[] tint = part.id == selectedId ? SELECTED : tint(part.material);
            int base = verts.size() / STRIDE;
            int count = mesh.vertexCount();
            for (int i = 0; i < count; i++) {
                verts.add((mesh.pos[i * 3] - cx) * scale);
                verts.add((mesh.pos[i * 3 + 1] - cy) * scale);
                verts.add((mesh.pos[i * 3 + 2] - cz) * scale);
                verts.add(mesh.nor[i * 3]);
                verts.add(mesh.nor[i * 3 + 1]);
                verts.add(mesh.nor[i * 3 + 2]);
                verts.add(tint[0]);
                verts.add(tint[1]);
                verts.add(tint[2]);
            }
            for (int t : mesh.tri) {
                index.add(base + t);
            }
        }
        if (index.size() < 3 || index.size() % 3 != 0) {
            return null;
        }
        float[] v = new float[verts.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = verts.get(i);
        }
        int[] ix = new int[index.size()];
        for (int i = 0; i < ix.length; i++) {
            ix[i] = index.get(i);
        }
        return new SolidSurface(v, ix);
    }

    /** Null when every triangle can be drawn. */
    public static String reject(TriMesh mesh) {
        if (mesh == null || mesh.pos == null || mesh.tri == null) {
            return "missing mesh";
        }
        if (mesh.tri.length < 3 || mesh.tri.length % 3 != 0) {
            return "no triangles";
        }
        int vertices = mesh.pos.length / 3;
        if (vertices < 3 || mesh.pos.length % 3 != 0) {
            return "bad positions";
        }
        for (float value : mesh.pos) {
            if (!Float.isFinite(value)) {
                return "non-finite coordinates";
            }
        }
        for (int index : mesh.tri) {
            if (index < 0 || index >= vertices) {
                return "index out of range";
            }
        }
        return null;
    }

    /** A selected part, so it can be told apart from the same material elsewhere. */
    public static final float[] SELECTED = {0.15f, 0.95f, 0.75f};

    private static float[] tint(String material) {
        String m = material == null ? "" : material.toLowerCase(java.util.Locale.US);
        // natural colours (lit by the mesh shader), not neon: a design should look like what it is made of
        if (m.contains("glass") || m.contains("water")) {
            return new float[]{0.42f, 0.70f, 0.88f};
        }
        if (m.contains("brick")) {
            return new float[]{0.66f, 0.34f, 0.26f};
        }
        if (m.contains("roof") || m.contains("tile") || m.contains("slate")) {
            return new float[]{0.52f, 0.30f, 0.24f};
        }
        if (m.contains("wood") || m.contains("timber") || m.contains("oak")) {
            return new float[]{0.66f, 0.47f, 0.30f};
        }
        if (m.contains("plant") || m.contains("grass") || m.contains("leaf")) {
            return new float[]{0.36f, 0.56f, 0.28f};
        }
        if (m.contains("metal") || m.contains("steel")) {
            return new float[]{0.70f, 0.74f, 0.80f};
        }
        if (m.contains("rubber") || m.contains("tyre") || m.contains("tire")) {
            return new float[]{0.17f, 0.17f, 0.18f};
        }
        if (m.contains("leather")) {
            return new float[]{0.42f, 0.27f, 0.17f};
        }
        if (m.contains("concrete") || m.contains("stone") || m.contains("cement")) {
            return new float[]{0.62f, 0.62f, 0.60f};
        }
        return new float[]{0.86f, 0.87f, 0.88f};
    }
}
