package ai.dvine.create;

import ai.dvine.human.mesh.TriMesh;

/**
 * One part of a 3D object (a wall, a wheel, a table leg): its own closed
 * geometry in object space (metres, y up, ground at y = 0), a name people
 * can say, a material, the step of the build it belongs to, and whether it
 * is shown (an interior view hides the roof and the front wall).
 */
public final class Component {
    public final int id;
    public final String name;
    /** What kind of part it is ("wall", "wheel", "window"…); a request like "make the wheels larger" names a kind. */
    public final String kind;
    public final String material;
    /** Build step: parts with lower steps assemble first (foundation before walls before the roof). */
    public final int step;
    public final TriMesh mesh;
    public boolean visible = true;

    public Component(int id, String name, String kind, String material, int step, TriMesh mesh) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.material = material;
        this.step = step;
        this.mesh = mesh;
    }

    /** Bounds: min x, y, z, max x, y, z. */
    public float[] bounds() {
        return mesh.bounds();
    }
}
