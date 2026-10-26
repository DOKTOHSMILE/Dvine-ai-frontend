package ai.dvine.create;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.dvine.human.mesh.TriMesh;

/**
 * Checks that an object is real, usable geometry before any particle goes
 * near it: every part a closed, outward-wound shell with finite coordinates
 * and sensible size; the object standing on the ground; no part floating
 * free of the rest; wheels touching the ground. A model that fails is never
 * shown as complete; the last valid version stays.
 */
public final class GeometryValidator {
    /** How far apart two parts may be and still count as touching (metres). */
    static final float TOUCH = 0.03f;

    private GeometryValidator() {
    }

    public static List<String> validate(ObjectModel m) {
        List<String> out = new ArrayList<String>();
        if (m.components.isEmpty()) {
            out.add("the object has no parts");
            return out;
        }
        for (Component c : m.components) {
            String why = shell(c.mesh);
            if (why != null) {
                out.add(c.name + ": " + why);
            }
        }
        float[] b = m.bounds();
        float size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        if (!(size > 0.005f && size < 200f)) {
            out.add("the object's size is implausible (" + size + " m)");
        }
        if (Math.abs(b[1]) > 0.05f) {
            out.add("the object does not stand on the ground (lowest point " + b[1] + " m)");
        }
        for (Component c : m.components) {
            float[] cb = c.bounds();
            if ("wheel".equals(c.kind) && Math.abs(cb[1]) > 0.01f) {
                out.add(c.name + " does not touch the ground");
            }
            if (cb[1] <= TOUCH || m.components.size() == 1) {
                continue; // standing on the ground
            }
            boolean touches = false;
            for (Component o : m.components) {
                if (o != c && overlaps(cb, o.bounds())) {
                    touches = true;
                    break;
                }
            }
            if (!touches) {
                out.add(c.name + " is floating, attached to nothing");
            }
        }
        return out;
    }

    /** Null when the mesh is a closed, outward-wound, finite shell; otherwise why not. */
    static String shell(TriMesh m) {
        if (m.tri.length < 12) {
            return "too few triangles";
        }
        for (float v : m.pos) {
            if (!Float.isFinite(v)) {
                return "non-finite coordinates";
            }
        }
        Map<Long, Integer> edges = new HashMap<Long, Integer>();
        int nv = m.pos.length / 3;
        for (int t = 0; t < m.tri.length; t += 3) {
            for (int k = 0; k < 3; k++) {
                int a = m.tri[t + k], c = m.tri[t + (k + 1) % 3];
                if (a < 0 || a >= nv || c < 0 || c >= nv || a == c) {
                    return "broken triangle";
                }
                long key = (long) a * nv + c; // directed: in a closed consistent shell each appears once
                if (edges.put(key, 1) != null) {
                    return "inconsistent winding";
                }
            }
        }
        for (Long key : edges.keySet()) {
            long a = key / nv, c = key % nv;
            if (!edges.containsKey(c * nv + a)) {
                return "has a hole";
            }
        }
        if (Geo.volume(m) <= 0) {
            return "wound inside out";
        }
        return null;
    }

    static boolean overlaps(float[] a, float[] b) {
        for (int k = 0; k < 3; k++) {
            if (a[k] > b[k + 3] + TOUCH || b[k] > a[k + 3] + TOUCH) {
                return false;
            }
        }
        return true;
    }
}
