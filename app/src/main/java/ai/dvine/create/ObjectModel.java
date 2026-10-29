package ai.dvine.create;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.dvine.geom.TriMesh;

/**
 * A built 3D object: the spec it came from, its version, its parts, where
 * its references came from, and what validation found. The geometry exists
 * on its own, independently of the particles that display it.
 */
public final class ObjectModel {
    public final ObjectSpec spec;
    public final int version;
    public final String title;
    public final List<Component> components;
    public final List<Provenance> provenance;
    /** Problems found by validation (empty when valid). */
    public final List<String> problems = new ArrayList<String>();
    /** Set only when this model was built with a skeleton. Null for everything else. */
    public BodyRig rig;

    public ObjectModel(ObjectSpec spec, int version, String title, List<Component> components, List<Provenance> provenance) {
        this.spec = spec;
        this.version = version;
        this.title = title;
        this.components = Collections.unmodifiableList(new ArrayList<Component>(components));
        this.provenance = Collections.unmodifiableList(new ArrayList<Provenance>(provenance));
    }

    public boolean valid() {
        return problems.isEmpty();
    }

    public Component component(int id) {
        for (Component c : components) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    /** min x, y, z, max x, y, z over the visible parts. */
    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Component c : components) {
            if (!c.visible) {
                continue;
            }
            float[] cb = c.bounds();
            for (int k = 0; k < 3; k++) {
                b[k] = Math.min(b[k], cb[k]);
                b[k + 3] = Math.max(b[k + 3], cb[k + 3]);
            }
        }
        return b;
    }

    /** All visible parts as one mesh (for the dark occluding fill), plus the component id of each triangle. */
    public TriMesh merged(List<Integer> triComponent) {
        List<TriMesh> parts = new ArrayList<TriMesh>();
        for (Component c : components) {
            if (c.visible) {
                parts.add(c.mesh);
                for (int t = 0; t < c.mesh.tri.length / 3; t++) {
                    triComponent.add(c.id);
                }
            }
        }
        return Geo.merge(parts.toArray(new TriMesh[0]));
    }

    public int triangleCount() {
        int n = 0;
        for (Component c : components) {
            n += c.mesh.tri.length / 3;
        }
        return n;
    }
}
