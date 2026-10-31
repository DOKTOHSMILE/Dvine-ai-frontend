package ai.dvine.create;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The object being designed: the current validated model, its history
 * (undo and redo walk over specs; geometry is rebuilt from them exactly),
 * how it is being looked at (turn, tilt, zoom, interior or floor plan), the
 * selected part, and where it stands in the scene. A change that fails
 * validation never replaces the last valid version.
 */
public final class ObjectSession {
    /** The object's height on screen, in body units (the human is about 4.4 tall). */
    static final float SHOW_HEIGHT = 3.4f, SHOW_WIDTH = 2.3f; // fits a portrait stage beside nothing

    private ObjectModel model;
    private final Deque<ObjectSpec> undo = new ArrayDeque<ObjectSpec>();
    private final Deque<ObjectSpec> redo = new ArrayDeque<ObjectSpec>();
    private List<Provenance> provenance = new ArrayList<Provenance>();
    private int version;
    public float yaw = 30f, pitch = 12f, zoom = 1f;
    public boolean spin = true;
    private String view = "exterior";
    private int selected = -1;
    private List<String> lastProblems = new ArrayList<String>();
    private final Deque<boolean[]> partShown = new ArrayDeque<boolean[]>();
    private final Deque<boolean[]> partKept = new ArrayDeque<boolean[]>();
    private final Deque<boolean[]> partShownRedo = new ArrayDeque<boolean[]>();
    private final Deque<boolean[]> partKeptRedo = new ArrayDeque<boolean[]>();
    private final Deque<ObjectModel> held = new ArrayDeque<ObjectModel>();

    public ObjectModel model() {
        return model;
    }

    public List<String> lastProblems() {
        return lastProblems;
    }

    /** Builds and validates a new object. Returns it, or null (and keeps whatever was there) when invalid. */
    public ObjectModel create(ObjectSpec spec, List<Provenance> sources) {
        ObjectModel m = ProceduralModelingEngine.build(spec, 1, sources == null ? new ArrayList<Provenance>() : sources);
        if (!m.valid()) {
            lastProblems = m.problems;
            return null;
        }
        ObjectModel previous = model;
        undo.clear();
        redo.clear();
        clearParts();
        if (previous != null && previous.spec != null) {
            undo.push(previous.spec);
        } else if (previous != null) {
            held.push(previous);
        } else {
            held.clear();
        }
        provenance = new ArrayList<Provenance>(m.provenance);
        version = 1;
        model = m;
        view = "exterior";
        selected = -1;
        yaw = 30f;
        pitch = 12f;
        zoom = 1f;
        spin = true;
        return m;
    }

    /** Opens a saved (already rebuilt) model as the current one. */
    public ObjectModel adopt(ObjectModel m) {
        if (m == null || !m.valid()) {
            return null;
        }
        undo.clear();
        redo.clear();
        held.clear();
        clearParts();
        provenance = new ArrayList<Provenance>(m.provenance);
        version = m.version;
        model = m;
        view = "exterior";
        selected = -1;
        return m;
    }

    /**
     * Puts a sparse reconstruction on the board and keeps the previous model
     * so undo can restore it. The reconstruction is not a procedural spec.
     */
    public ObjectModel showReconstruction(ObjectModel incoming) {
        if (incoming == null || incoming.triangleCount() < 1) {
            return null;
        }
        if (model != null) {
            held.push(model);
        }
        clearParts();
        model = incoming;
        selected = -1;
        return model;
    }

    /** Restores the model that was showing before the last reconstruction. */
    public boolean undoReconstruction() {
        if (held.isEmpty()) {
            return false;
        }
        clearParts();
        model = held.pop();
        selected = -1;
        return true;
    }

    /** Applies a changed spec as a new version, if it validates. */
    public ObjectModel modify(ObjectSpec spec) {
        if (model == null) {
            return null;
        }
        ObjectModel m = ProceduralModelingEngine.build(spec, version + 1, provenance);
        if (!m.valid()) {
            lastProblems = m.problems;
            return null; // the last valid version stays
        }
        undo.push(model.spec);
        redo.clear();
        held.clear();
        clearParts();
        version++;
        model = m;
        applyView();
        selected = -1;
        return m;
    }

    public ObjectModel undo() {
        if (model == null || undo.isEmpty()) {
            return null;
        }
        redo.push(model.spec);
        clearParts();
        version++;
        model = ProceduralModelingEngine.build(undo.pop(), version, provenance);
        applyView();
        return model;
    }

    public ObjectModel redo() {
        if (model == null || redo.isEmpty()) {
            return null;
        }
        undo.push(model.spec);
        clearParts();
        version++;
        model = ProceduralModelingEngine.build(redo.pop(), version, provenance);
        applyView();
        return model;
    }

    public void clear() {
        model = null;
        undo.clear();
        redo.clear();
        held.clear();
        clearParts();
        selected = -1;
    }

    /** Remembers which parts are showing, so a detach can be undone without rebuilding the spec. */
    public void rememberParts() {
        if (model == null) {
            return;
        }
        partShown.push(flags(true));
        partKept.push(flags(false));
        partShownRedo.clear();
        partKeptRedo.clear();
    }

    public void dropLastPartMemory() {
        if (!partShown.isEmpty()) {
            partShown.pop();
            partKept.pop();
        }
    }

    public boolean undoParts() {
        return swapParts(partShown, partKept, partShownRedo, partKeptRedo);
    }

    public boolean redoParts() {
        return swapParts(partShownRedo, partKeptRedo, partShown, partKept);
    }

    private boolean swapParts(Deque<boolean[]> fromShown, Deque<boolean[]> fromKept, Deque<boolean[]> toShown, Deque<boolean[]> toKept) {
        if (model == null || fromShown.isEmpty()) {
            return false;
        }
        boolean[] shown = fromShown.pop();
        boolean[] kept = fromKept.pop();
        if (shown.length != model.components.size()) {
            return false;
        }
        toShown.push(flags(true));
        toKept.push(flags(false));
        for (int i = 0; i < shown.length; i++) {
            model.components.get(i).visible = shown[i];
            model.components.get(i).retained = kept[i];
        }
        return true;
    }

    private boolean[] flags(boolean shown) {
        boolean[] out = new boolean[model.components.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = shown ? model.components.get(i).visible : model.components.get(i).retained;
        }
        return out;
    }

    private void clearParts() {
        partShown.clear();
        partKept.clear();
        partShownRedo.clear();
        partKeptRedo.clear();
    }

    /**
     * Sets a view. Front/side/rear/top turn the object; interior hides the roof
     * and the front walls; plan looks down with the roof and upper floors
     * hidden; exterior shows everything again. Returns true when the visible
     * parts changed (the particles then re-form).
     */
    public boolean view(String name) {
        spin = false;
        switch (name) {
            case "front": yaw = 0f; pitch = 8f; break;
            case "side": yaw = 90f; pitch = 8f; break;
            case "rear": yaw = 180f; pitch = 8f; break;
            case "top": yaw = 0f; pitch = 80f; break;
            case "rotate": spin = true; break;
            case "zoom in": zoom = Math.min(2.5f, zoom * 1.35f); break;
            case "zoom out": zoom = Math.max(0.4f, zoom / 1.35f); break;
            case "plan": yaw = 0f; pitch = 85f; break;
            case "interior": yaw = 20f; pitch = 25f; break;
            default: break;
        }
        String before = view;
        if (name.equals("interior") || name.equals("plan") || name.equals("exterior")) {
            view = name;
        }
        return applyView() || !before.equals(view);
    }

    public String currentView() {
        return view;
    }

    private boolean applyView() {
        if (model == null) {
            return false;
        }
        boolean changed = false;
        for (Component c : model.components) {
            boolean show = true;
            if (view.equals("interior")) {
                show = !c.kind.equals("roof") && !(c.kind.equals("wall") && c.name.contains("front")) && !c.kind.equals("window")
                        && !c.name.contains("front door") && !c.kind.equals("railing");
            } else if (view.equals("plan")) {
                show = !c.kind.equals("roof") && (c.name.startsWith("ground") || c.name.startsWith("stairs ground") || c.kind.equals("foundation")
                        || c.kind.equals("garage") || c.kind.equals("pool") || c.kind.equals("water") || c.kind.equals("fence")
                        || c.name.equals("front door"));
            }
            if (c.visible != show) {
                c.visible = show;
                changed = true;
            }
        }
        return changed;
    }

    public int selected() {
        return selected;
    }

    public void select(int id) {
        selected = id;
    }

    /** Scale from metres to body units so the object fits the stage. */
    public float scale() {
        float[] b = model.bounds();
        float h = b[4] - b[1], w = Math.max(b[3] - b[0], b[5] - b[2]);
        return Math.min(SHOW_HEIGHT / Math.max(0.01f, h), SHOW_WIDTH / Math.max(0.01f, w)) * zoom;
    }

    /**
     * Object space (metres, y up, ground 0) → body units, column-major: centred
     * on the stage, standing on the floor, turned by yaw and tilted by pitch.
     */
    public float[] matrix(float floorY, float time) {
        float[] b = model.bounds();
        float cx = (b[0] + b[3]) / 2, cz = (b[2] + b[5]) / 2, cy = (b[1] + b[4]) / 2;
        float k = scale();
        double y = Math.toRadians(yaw + (spin ? time * 14f : 0f)), x = Math.toRadians(pitch * 0.5f);
        float cyw = (float) Math.cos(y), syw = (float) Math.sin(y), cp = (float) Math.cos(x), sp = (float) Math.sin(x);
        // R = Rx(pitch) · Ry(yaw); p' = R·k·(p - c) + (0, floor + k·(cy - b[1]) lifted, 0)
        float[] r = {cyw, sp * syw, -cp * syw, 0f, cp, sp, syw, -sp * cyw, cp * cyw};
        float tx = 0f, ty = floorY + k * (cy - b[1]), tz = 0f;
        float[] m = new float[16];
        for (int c = 0; c < 3; c++) {
            for (int rr = 0; rr < 3; rr++) {
                m[c * 4 + rr] = r[rr * 3 + c] * k;
            }
        }
        m[12] = tx - (m[0] * cx + m[4] * cy + m[8] * cz);
        m[13] = ty - (m[1] * cx + m[5] * cy + m[9] * cz);
        m[14] = tz - (m[2] * cx + m[6] * cy + m[10] * cz);
        m[15] = 1f;
        return m;
    }

    /** The part under a ray (origin and direction in body units, through {@link #matrix}), or -1. */
    public int pick(float[] matrix, float ox, float oy, float oz, float dx, float dy, float dz) {
        if (model == null) {
            return -1;
        }
        float[] inv = invertAffine(matrix);
        float[] o = apply(inv, ox, oy, oz, 1f), d = apply(inv, dx, dy, dz, 0f);
        float best = Float.MAX_VALUE;
        int hit = -1;
        for (Component c : model.components) {
            if (!c.visible) {
                continue;
            }
            float[] p = c.mesh.pos;
            int[] tri = c.mesh.tri;
            for (int t = 0; t < tri.length; t += 3) {
                int a = tri[t] * 3, b = tri[t + 1] * 3, cc = tri[t + 2] * 3;
                float e1x = p[b] - p[a], e1y = p[b + 1] - p[a + 1], e1z = p[b + 2] - p[a + 2];
                float e2x = p[cc] - p[a], e2y = p[cc + 1] - p[a + 1], e2z = p[cc + 2] - p[a + 2];
                float px = d[1] * e2z - d[2] * e2y, py = d[2] * e2x - d[0] * e2z, pz = d[0] * e2y - d[1] * e2x;
                float det = e1x * px + e1y * py + e1z * pz;
                if (Math.abs(det) < 1e-12f) {
                    continue;
                }
                float inv1 = 1f / det;
                float sx = o[0] - p[a], sy = o[1] - p[a + 1], sz = o[2] - p[a + 2];
                float u = (sx * px + sy * py + sz * pz) * inv1;
                if (u < 0f || u > 1f) {
                    continue;
                }
                float qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
                float v = (d[0] * qx + d[1] * qy + d[2] * qz) * inv1;
                if (v < 0f || u + v > 1f) {
                    continue;
                }
                float dist = (e2x * qx + e2y * qy + e2z * qz) * inv1;
                if (dist > 1e-6f && dist < best) {
                    best = dist;
                    hit = c.id;
                }
            }
        }
        return hit;
    }

    static float[] apply(float[] m, float x, float y, float z, float w) {
        return new float[]{m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w,
                m[2] * x + m[6] * y + m[10] * z + m[14] * w};
    }

    /** Inverse of a rotation·scale + translation matrix (column-major). */
    static float[] invertAffine(float[] m) {
        float a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
        float det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        float id = 1f / det;
        float[] r = {(e * i - f * h) * id, (c * h - b * i) * id, (b * f - c * e) * id,
                (f * g - d * i) * id, (a * i - c * g) * id, (c * d - a * f) * id,
                (d * h - e * g) * id, (b * g - a * h) * id, (a * e - b * d) * id};
        float[] out = new float[16];
        // rows of r → column-major
        out[0] = r[0]; out[4] = r[1]; out[8] = r[2];
        out[1] = r[3]; out[5] = r[4]; out[9] = r[5];
        out[2] = r[6]; out[6] = r[7]; out[10] = r[8];
        out[12] = -(out[0] * m[12] + out[4] * m[13] + out[8] * m[14]);
        out[13] = -(out[1] * m[12] + out[5] * m[13] + out[9] * m[14]);
        out[14] = -(out[2] * m[12] + out[6] * m[13] + out[10] * m[14]);
        out[15] = 1f;
        return out;
    }
}
