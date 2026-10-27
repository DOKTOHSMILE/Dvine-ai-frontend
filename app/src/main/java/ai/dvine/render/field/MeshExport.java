package ai.dvine.render.field;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A 3D drawing in the file formats other 3D software opens, besides glTF ({@link Gltf}):
 * <ul>
 * <li>OBJ + MTL: Blender, Maya, 3ds Max, Cinema 4D, SketchUp, ZBrush; each part a named group with its own colour.</li>
 * <li>STL (binary): 3D printing (Cura, PrusaSlicer, Bambu Studio), CAD; shape only, no colour.</li>
 * <li>PLY (binary, colour per vertex): MeshLab, CloudCompare, Blender.</li>
 * </ul>
 * Metres, y up, the thing 1 m across standing on the ground, as in the .glb. Pure Java.
 */
public final class MeshExport {
    private MeshExport() {
    }

    public enum Format {
        GLB("glb", "model/gltf-binary", "Blender, Unity, Unreal, Godot, and Windows 3D Viewer"),
        OBJ("obj", "model/obj", "Blender, Maya, 3ds Max, Cinema 4D and SketchUp"),
        STL("stl", "model/stl", "3D printers and slicers like Cura and PrusaSlicer, and CAD"),
        PLY("ply", "application/octet-stream", "MeshLab, CloudCompare and Blender");

        public final String extension, mime, opensIn;

        Format(String extension, String mime, String opensIn) {
            this.extension = extension;
            this.mime = mime;
            this.opensIn = opensIn;
        }
    }

    /** The format the words ask for ("as an stl", "for 3d printing", "in obj format"); GLB when none is named. */
    public static Format formatIn(String said) {
        String s = said == null ? "" : " " + said.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ") + " ";
        if (s.matches(".* (stl|3d print\\w*|printing|printer|slicer|cura|cad) .*")) return Format.STL;
        if (s.matches(".* (obj|wavefront|maya|3ds max|cinema 4d|sketchup|zbrush) .*")) return Format.OBJ;
        if (s.matches(".* (ply|meshlab|point cloud|cloudcompare) .*")) return Format.PLY;
        return Format.GLB;
    }

    /** Every part's triangles in metres, the same placing as the .glb (one array per part). */
    static float[][] world(SolidSpec spec) {
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (SolidSpec.Part p : spec.parts) {
            minX = Math.min(minX, p.x - p.w / 2);
            maxX = Math.max(maxX, p.x + p.w / 2);
            minY = Math.min(minY, p.y - p.h / 2);
            maxY = Math.max(maxY, p.y + p.h / 2);
            minZ = Math.min(minZ, p.z - p.d / 2);
            maxZ = Math.max(maxZ, p.z + p.d / 2);
        }
        float k = 1f / Math.max(1f, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        float cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2, ground = maxY;
        float[][] out = new float[spec.parts.size()][];
        for (int i = 0; i < out.length; i++) {
            SolidSpec.Part p = spec.parts.get(i);
            float[] t = Gltf.triangles(p, k);
            float ox = (p.x - cx) * k, oy = (ground - p.y) * k, oz = (p.z - cz) * k;
            for (int v = 0; v < t.length; v += 3) {
                t[v] += ox;
                t[v + 1] += oy;
                t[v + 2] += oz;
            }
            out[i] = t;
        }
        return out;
    }

    /** The OBJ text; its materials are in {@link #mtl} under {@code mtlName}. */
    public static String obj(SolidSpec spec, String name, String mtlName) {
        float[][] parts = world(spec);
        StringBuilder b = new StringBuilder("# ").append(name).append(", drawn by DVINE\nmtllib ").append(mtlName).append('\n');
        int base = 1;
        java.util.Map<String, Integer> seen = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < parts.length; i++) {
            float[] t = parts[i];
            float[] n = Gltf.normals(t);
            String g = unique(spec.parts.get(i).name, seen).replace(' ', '_');
            b.append("o ").append(g).append("\nusemtl m").append(i).append('\n');
            for (int v = 0; v < t.length; v += 3) {
                b.append("v ").append(f(t[v])).append(' ').append(f(t[v + 1])).append(' ').append(f(t[v + 2])).append('\n');
            }
            for (int v = 0; v < n.length; v += 9) {
                b.append("vn ").append(f(n[v])).append(' ').append(f(n[v + 1])).append(' ').append(f(n[v + 2])).append('\n');
            }
            int tris = t.length / 9;
            for (int k = 0; k < tris; k++) {
                int a = base + k * 3, nn = base / 3 + k + 1;
                b.append("f ").append(a).append("//").append(nn).append(' ').append(a + 1).append("//").append(nn).append(' ')
                        .append(a + 2).append("//").append(nn).append('\n');
            }
            base += t.length / 3;
        }
        return b.toString();
    }

    public static String mtl(SolidSpec spec) {
        StringBuilder b = new StringBuilder("# DVINE materials\n");
        for (int i = 0; i < spec.parts.size(); i++) {
            int c = spec.parts.get(i).colour;
            b.append("newmtl m").append(i).append("\nKd ").append(f(((c >> 16) & 255) / 255f)).append(' ')
                    .append(f(((c >> 8) & 255) / 255f)).append(' ').append(f((c & 255) / 255f)).append("\nKa 0 0 0\nKs 0.1 0.1 0.1\nNs 30\nd 1\nillum 2\n");
        }
        return b.toString();
    }

    /** Binary STL, in millimetres (slicers read STL as mm): the thing 100 mm across. */
    public static byte[] stl(SolidSpec spec, String name) {
        float[][] parts = world(spec);
        int tris = 0;
        for (float[] t : parts) {
            tris += t.length / 9;
        }
        ByteBuffer bb = ByteBuffer.allocate(84 + tris * 50).order(ByteOrder.LITTLE_ENDIAN);
        byte[] head = java.util.Arrays.copyOf(("DVINE " + name).getBytes(StandardCharsets.US_ASCII), 80);
        bb.put(head).putInt(tris);
        for (float[] t : parts) {
            float[] n = Gltf.normals(t);
            for (int v = 0; v < t.length; v += 9) {
                bb.putFloat(n[v]).putFloat(-n[v + 2]).putFloat(n[v + 1]);
                // STL is z up: (x, y, z) → (x, -z, y), turning the same way so the triangles still face out
                for (int c = 0; c < 3; c++) {
                    bb.putFloat(t[v + c * 3] * 100f).putFloat(-t[v + c * 3 + 2] * 100f).putFloat(t[v + c * 3 + 1] * 100f);
                }
                bb.putShort((short) 0);
            }
        }
        return bb.array();
    }

    /** Binary PLY with a colour on every vertex. */
    public static byte[] ply(SolidSpec spec) {
        float[][] parts = world(spec);
        int verts = 0;
        for (float[] t : parts) {
            verts += t.length / 3;
        }
        String head = "ply\nformat binary_little_endian 1.0\ncomment drawn by DVINE\nelement vertex " + verts
                + "\nproperty float x\nproperty float y\nproperty float z\nproperty uchar red\nproperty uchar green\nproperty uchar blue\n"
                + "element face " + verts / 3 + "\nproperty list uchar int vertex_indices\nend_header\n";
        byte[] h = head.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer bb = ByteBuffer.allocate(h.length + verts * 15 + verts / 3 * 13).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(h);
        for (int i = 0; i < parts.length; i++) {
            int c = spec.parts.get(i).colour;
            for (int v = 0; v < parts[i].length; v += 3) {
                bb.putFloat(parts[i][v]).putFloat(parts[i][v + 1]).putFloat(parts[i][v + 2])
                        .put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c);
            }
        }
        for (int f = 0; f < verts / 3; f++) {
            bb.put((byte) 3).putInt(f * 3).putInt(f * 3 + 1).putInt(f * 3 + 2);
        }
        return bb.array();
    }

    private static String unique(String name, java.util.Map<String, Integer> seen) {
        int n = seen.merge(name, 1, Integer::sum);
        return n == 1 ? name : name + " " + n;
    }

    private static String f(float v) {
        return String.format(Locale.US, "%.5f", v);
    }
}
