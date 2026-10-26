package ai.dvine.create;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import ai.dvine.human.mesh.TriMesh;

/**
 * Reads a Wavefront OBJ model (an asset downloaded with a known licence, or
 * one the user supplies) into parts: one per "o"/"g" group, polygons fanned
 * into triangles. The file is untrusted input: sizes are capped, indices are
 * checked, and nothing in it is executed. The result is scaled to the
 * requested height and set on the ground.
 */
public final class ObjImporter {
    public static final int MAX_VERTICES = 200_000;
    public static final int MAX_TRIANGLES = 300_000;

    private ObjImporter() {
    }

    public static List<Component> read(Reader in, float height, String material) throws IOException {
        List<float[]> v = new ArrayList<float[]>();
        List<String> names = new ArrayList<String>();
        List<List<int[]>> groups = new ArrayList<List<int[]>>();
        String current = "part";
        List<int[]> faces = new ArrayList<int[]>();
        int triangles = 0;
        BufferedReader r = new BufferedReader(in);
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("v ")) {
                String[] p = line.split("\\s+");
                if (p.length < 4 || v.size() >= MAX_VERTICES) {
                    throw new IOException(v.size() >= MAX_VERTICES ? "too many vertices" : "bad vertex");
                }
                float x = Float.parseFloat(p[1]), y = Float.parseFloat(p[2]), z = Float.parseFloat(p[3]);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    throw new IOException("bad vertex");
                }
                v.add(new float[]{x, y, z});
            } else if (line.startsWith("f ")) {
                String[] p = line.split("\\s+");
                int[] idx = new int[p.length - 1];
                for (int k = 1; k < p.length; k++) {
                    int i = Integer.parseInt(p[k].split("/")[0]);
                    idx[k - 1] = i < 0 ? v.size() + i : i - 1;
                    if (idx[k - 1] < 0 || idx[k - 1] >= v.size()) {
                        throw new IOException("face index out of range");
                    }
                }
                for (int k = 1; k + 1 < idx.length; k++) {
                    faces.add(new int[]{idx[0], idx[k], idx[k + 1]});
                    if (++triangles > MAX_TRIANGLES) {
                        throw new IOException("too many triangles");
                    }
                }
            } else if (line.startsWith("o ") || line.startsWith("g ")) {
                if (!faces.isEmpty()) {
                    names.add(current);
                    groups.add(faces);
                    faces = new ArrayList<int[]>();
                }
                current = line.substring(2).trim().replace('_', ' ');
                if (current.length() > 40) {
                    current = current.substring(0, 40);
                }
            }
        }
        if (!faces.isEmpty()) {
            names.add(current);
            groups.add(faces);
        }
        if (groups.isEmpty()) {
            throw new IOException("no faces");
        }
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, cx = 0, cz = 0;
        for (float[] p : v) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            cx += p[0];
            cz += p[2];
        }
        cx /= v.size();
        cz /= v.size();
        float k = maxY > minY ? height / (maxY - minY) : 1f;
        List<Component> out = new ArrayList<Component>();
        for (int g = 0; g < groups.size(); g++) {
            List<int[]> fs = groups.get(g);
            int[] map = new int[v.size()];
            java.util.Arrays.fill(map, -1);
            List<float[]> used = new ArrayList<float[]>();
            int[] tri = new int[fs.size() * 3];
            for (int f = 0; f < fs.size(); f++) {
                for (int c = 0; c < 3; c++) {
                    int i = fs.get(f)[c];
                    if (map[i] < 0) {
                        map[i] = used.size();
                        float[] p = v.get(i);
                        used.add(new float[]{(p[0] - cx) * k, (p[1] - minY) * k, (p[2] - cz) * k});
                    }
                    tri[f * 3 + c] = map[i];
                }
            }
            TriMesh m = new TriMesh();
            m.pos = new float[used.size() * 3];
            for (int i = 0; i < used.size(); i++) {
                System.arraycopy(used.get(i), 0, m.pos, i * 3, 3);
            }
            m.tri = tri;
            m.computeNormals();
            out.add(new Component(g, names.get(g), "imported", material, g, m));
        }
        return out;
    }
}
