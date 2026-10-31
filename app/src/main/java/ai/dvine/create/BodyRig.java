package ai.dvine.create;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import ai.dvine.geom.TriMesh;

/**
 * A procedural skeleton with one bone per part and linear blend skinning
 * (the whole part has weight 1 on its bone). Not a scanned person and not FACS.
 */
public final class BodyRig {
    public static final class Bone {
        public final String name;
        public final int parent;
        public final float jointX, jointY, jointZ;
        public float rx, ry, rz;

        Bone(String name, int parent, float jointX, float jointY, float jointZ) {
            this.name = name;
            this.parent = parent;
            this.jointX = jointX;
            this.jointY = jointY;
            this.jointZ = jointZ;
        }
    }

    public final List<Bone> bones = new ArrayList<Bone>();
    public boolean visible = true;
    private final List<float[]> local = new ArrayList<float[]>();
    private final Deque<float[]> history = new ArrayDeque<float[]>();
    private ObjectModel bound;

    private BodyRig() {
    }

    public static boolean shown(ObjectModel model) {
        return model != null && model.rig != null && model.rig.visible && !model.components.isEmpty();
    }

    /** Adds the body parts. The rig is attached after the model exists. */
    static String assemble(ObjectSpec spec, ProceduralModelingEngine.Parts p) {
        box(p, "hips", 0f, 0.96f, 0f, 0.32f, 0.22f, 0.18f);
        box(p, "spine", 0f, 1.18f, 0f, 0.16f, 0.22f, 0.12f);
        box(p, "chest", 0f, 1.40f, 0f, 0.42f, 0.28f, 0.18f);
        box(p, "head", 0f, 1.62f, 0f, 0.20f, 0.24f, 0.20f);
        box(p, "left upper arm", -0.24f, 1.38f, 0f, 0.12f, 0.30f, 0.12f);
        box(p, "left forearm", -0.24f, 1.10f, 0f, 0.10f, 0.30f, 0.10f);
        box(p, "right upper arm", 0.24f, 1.38f, 0f, 0.12f, 0.30f, 0.12f);
        box(p, "right forearm", 0.24f, 1.10f, 0f, 0.10f, 0.30f, 0.10f);
        box(p, "left thigh", -0.10f, 0.66f, 0f, 0.13f, 0.40f, 0.13f);
        box(p, "left shin", -0.10f, 0.28f, 0f, 0.11f, 0.36f, 0.11f);
        box(p, "left foot", -0.10f, 0.04f, 0.04f, 0.12f, 0.08f, 0.24f);
        box(p, "right thigh", 0.10f, 0.66f, 0f, 0.13f, 0.40f, 0.13f);
        box(p, "right shin", 0.10f, 0.28f, 0f, 0.11f, 0.36f, 0.11f);
        box(p, "right foot", 0.10f, 0.04f, 0.04f, 0.12f, 0.08f, 0.24f);
        return "person";
    }

    private static void box(ProceduralModelingEngine.Parts p, String name, float x, float y, float z, float w, float h, float d) {
        p.add(name, "bone", "skin", 0, Geo.box(x, y, z, w, h, d));
    }

    public static void attach(ObjectModel model) {
        if (model == null || model.spec == null || !"person".equals(model.spec.category)) {
            return;
        }
        BodyRig rig = new BodyRig();
        String[] names = {"hips", "spine", "chest", "head", "left upper arm", "left forearm", "right upper arm", "right forearm",
                "left thigh", "left shin", "left foot", "right thigh", "right shin", "right foot"};
        int[] parent = {-1, 0, 1, 2, 2, 4, 2, 6, 0, 8, 9, 0, 11, 12};
        float[][] joint = {
                {0f, 0.85f, 0f}, {0f, 1.07f, 0f}, {0f, 1.27f, 0f}, {0f, 1.53f, 0f},
                {-0.22f, 1.50f, 0f}, {-0.30f, 1.22f, 0f}, {0.22f, 1.50f, 0f}, {0.30f, 1.22f, 0f},
                {-0.10f, 0.86f, 0f}, {-0.10f, 0.46f, 0f}, {-0.10f, 0.08f, 0f},
                {0.10f, 0.86f, 0f}, {0.10f, 0.46f, 0f}, {0.10f, 0.08f, 0f}
        };
        for (int i = 0; i < names.length; i++) {
            Component part = named(model, names[i]);
            if (part == null) {
                return;
            }
            float jx = joint[i][0], jy = joint[i][1], jz = joint[i][2];
            if (parent[i] >= 0) {
                float[] pj = joint[parent[i]];
                rig.bones.add(new Bone(names[i], parent[i], jx - pj[0], jy - pj[1], jz - pj[2]));
            } else {
                rig.bones.add(new Bone(names[i], -1, jx, jy, jz));
            }
            float[] rest = part.mesh.pos;
            float[] loc = new float[rest.length];
            for (int v = 0; v < rest.length; v += 3) {
                loc[v] = rest[v] - jx;
                loc[v + 1] = rest[v + 1] - jy;
                loc[v + 2] = rest[v + 2] - jz;
            }
            rig.local.add(loc);
            int nv = rest.length / 3;
            part.mesh.bone0 = new byte[nv];
            part.mesh.bone1 = new byte[nv];
            part.mesh.weight0 = new float[nv];
            part.mesh.region = new byte[nv];
            for (int v = 0; v < nv; v++) {
                part.mesh.bone0[v] = (byte) i;
                part.mesh.bone1[v] = (byte) i;
                part.mesh.weight0[v] = 1f;
                part.mesh.region[v] = (byte) i;
            }
        }
        model.rig = rig;
        rig.bound = model;
        rig.skin(model);
    }

    public void remember() {
        history.push(angles());
    }

    public void dropLast() {
        if (!history.isEmpty()) {
            history.pop();
        }
    }

    public boolean undo() {
        if (history.isEmpty()) {
            return false;
        }
        float[] back = history.pop();
        for (int i = 0; i < bones.size(); i++) {
            bones.get(i).rx = back[i * 3];
            bones.get(i).ry = back[i * 3 + 1];
            bones.get(i).rz = back[i * 3 + 2];
        }
        skin(bound);
        return true;
    }

    /** True when a bone angle changed. */
    public boolean pose(ObjectModel model, String phrase) {
        String n = phrase == null ? "" : phrase.toLowerCase(Locale.US);
        if (n.contains("wave")) {
            setAngle("right upper arm", 0f, 0f, 0.9f);
            setAngle("right forearm", 0f, 0f, 0.8f);
            skin(model);
            return true;
        }
        if (n.contains("look up") || n.contains("tilt your head") || n.contains("chin up")) {
            setAngle("head", -0.4f, 0f, 0f);
            skin(model);
            return true;
        }
        if (n.contains("arms down") || n.contains("put your arms down") || n.contains("arms at your sides")) {
            setAngle("left upper arm", 0f, 0f, 0f);
            setAngle("right upper arm", 0f, 0f, 0f);
            setAngle("left forearm", 0f, 0f, 0f);
            setAngle("right forearm", 0f, 0f, 0f);
            skin(model);
            return true;
        }
        boolean both = n.contains("both arms") || n.contains("arms up") || n.contains("hands up");
        boolean raise = n.contains("raise") || n.contains("lift") || n.contains("up");
        boolean lower = n.contains("lower") || n.contains("put down");
        if (both && (raise || lower)) {
            float z = lower ? 0f : 1.15f;
            setAngle("left upper arm", 0f, 0f, -z);
            setAngle("right upper arm", 0f, 0f, z);
            skin(model);
            return true;
        }
        boolean left = n.contains("left");
        boolean right = n.contains("right");
        if (!n.contains("arm") || (!left && !right) || (!n.contains("raise") && !n.contains("lift") && !lower)) {
            return false;
        }
        String bone = (left ? "left" : "right") + " upper arm";
        setAngle(bone, 0f, 0f, lower ? 0f : (left ? -1.15f : 1.15f));
        skin(model);
        return true;
    }

    private void setAngle(String name, float rx, float ry, float rz) {
        for (Bone b : bones) {
            if (b.name.equals(name)) {
                b.rx = rx;
                b.ry = ry;
                b.rz = rz;
            }
        }
    }

    public static String describe(String phrase, boolean applied) {
        if (!applied) {
            return "I can raise or lower one arm or both, put the arms down, wave, or look up. I left the pose unchanged. This is a procedural skeleton, not a scanned person.";
        }
        String n = phrase.toLowerCase(Locale.US);
        String done;
        if (n.contains("wave")) {
            done = "I bent the right arm into a wave";
        } else if (n.contains("look up") || n.contains("tilt your head") || n.contains("chin up")) {
            done = "I tilted the head up";
        } else if (n.contains("arms down") || n.contains("put your arms down") || n.contains("arms at your sides")) {
            done = "I put both arms down";
        } else if (n.contains("both") || n.contains("arms up") || n.contains("hands up")) {
            done = n.contains("lower") || n.contains("put down") ? "I lowered both arms" : "I raised both arms";
        } else {
            String side = n.contains("left") ? "left" : "right";
            String verb = n.contains("lower") || n.contains("put down") ? "lowered" : "raised";
            done = "I " + verb + " the " + side + " arm from the shoulder. The forearm follows that bone";
        }
        return done + ". This is linear blend skinning on a procedural skeleton, not a scanned person and not a face rig.";
    }

    public void skin(ObjectModel model) {
        float[][] world = worlds();
        for (int i = 0; i < bones.size() && i < local.size(); i++) {
            Component part = named(model, bones.get(i).name);
            if (part == null) {
                continue;
            }
            float[] loc = local.get(i);
            float[] pos = part.mesh.pos;
            float[] m = world[i];
            for (int v = 0; v < pos.length; v += 3) {
                float x = loc[v], y = loc[v + 1], z = loc[v + 2];
                float w0 = part.mesh.weight0 == null ? 1f : part.mesh.weight0[v / 3];
                pos[v] = (m[0] * x + m[4] * y + m[8] * z + m[12]) * w0;
                pos[v + 1] = (m[1] * x + m[5] * y + m[9] * z + m[13]) * w0;
                pos[v + 2] = (m[2] * x + m[6] * y + m[10] * z + m[14]) * w0;
            }
            part.mesh.computeNormals();
        }
    }

    private float[][] worlds() {
        float[][] world = new float[bones.size()][16];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            float[] localM = mul(translate(b.jointX, b.jointY, b.jointZ), rotate(b.rx, b.ry, b.rz));
            world[i] = b.parent < 0 ? localM : mul(world[b.parent], localM);
        }
        return world;
    }

    private float[] angles() {
        float[] a = new float[bones.size() * 3];
        for (int i = 0; i < bones.size(); i++) {
            a[i * 3] = bones.get(i).rx;
            a[i * 3 + 1] = bones.get(i).ry;
            a[i * 3 + 2] = bones.get(i).rz;
        }
        return a;
    }

    private static Component named(ObjectModel model, String name) {
        for (Component c : model.components) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static float[] translate(float x, float y, float z) {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1};
    }

    /** Column-major. Z rotation raises the arms sideways. */
    private static float[] rotate(float rx, float ry, float rz) {
        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);
        float[] x = {1, 0, 0, 0, 0, cx, sx, 0, 0, -sx, cx, 0, 0, 0, 0, 1};
        float[] y = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};
        float[] z = {cz, sz, 0, 0, -sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        return mul(mul(z, y), x);
    }

    private static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
            }
        }
        return o;
    }
}
