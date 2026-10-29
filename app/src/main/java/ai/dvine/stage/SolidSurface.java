package ai.dvine.stage;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.create.Component;
import ai.dvine.create.ObjectModel;
import ai.dvine.geom.TriMesh;

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
            float[] tint = tint(part.material);
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

    private static float[] tint(String material) {
        String m = material == null ? "" : material.toLowerCase(java.util.Locale.US);
        if (m.contains("glass") || m.contains("water")) {
            return new float[]{0.45f, 0.85f, 1f};
        }
        if (m.contains("wood") || m.contains("brick") || m.contains("roof")) {
            return new float[]{1f, 0.72f, 0.45f};
        }
        if (m.contains("plant") || m.contains("grass") || m.contains("leaf")) {
            return new float[]{0.5f, 1f, 0.6f};
        }
        if (m.contains("metal") || m.contains("steel")) {
            return new float[]{0.75f, 0.82f, 0.95f};
        }
        return new float[]{0.85f, 0.92f, 1f};
    }
}
