package ai.dvine.render.field;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A 3D drawing as a game-ready glTF 2.0 binary (.glb), the format Blender, Unity, Unreal and Godot all open. Each
 * part is its own node with its own mesh and colour, named as it was drawn ("left ear", "front wheel"), and hangs
 * from the part it was placed against: the ear from the head, the head from the body. So every part is a joint
 * that turns about its own centre, and the whole hierarchy moves together. Y up, metres, the thing 1 m across,
 * standing on the ground at its lowest point. Pure Java.
 */
public final class Gltf {
    private Gltf() {
    }

    private static final int SEGMENTS = 24, RINGS = 12;

    /** The .glb bytes for the drawing, called {@code name}. */
    public static byte[] glb(SolidSpec spec, String name) {
        List<SolidSpec.Part> parts = spec.parts;
        // canvas units (y down) to metres (y up), the thing 1 m across, centred, its lowest point on the ground
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (SolidSpec.Part p : parts) {
            minX = Math.min(minX, p.x - p.w / 2);
            maxX = Math.max(maxX, p.x + p.w / 2);
            minY = Math.min(minY, p.y - p.h / 2);
            maxY = Math.max(maxY, p.y + p.h / 2);
            minZ = Math.min(minZ, p.z - p.d / 2);
            maxZ = Math.max(maxZ, p.z + p.d / 2);
        }
        final float k = 1f / Math.max(1f, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)));
        final float cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2, ground = maxY;

        // each part's joint: the part it hangs from, if that is still in the drawing
        IdentityHashMap<SolidSpec.Part, Integer> index = new IdentityHashMap<SolidSpec.Part, Integer>();
        Map<String, Integer> byName = new HashMap<String, Integer>();
        for (int i = 0; i < parts.size(); i++) {
            index.put(parts.get(i), i);
            byName.putIfAbsent(parts.get(i).name, i);
        }
        int[] parent = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            SolidSpec.Part up = parts.get(i).parent;
            Integer j = up == null ? null : index.containsKey(up) ? index.get(up) : byName.get(up.name);
            parent[i] = j == null || j >= i ? -1 : j; // always an earlier part, so the joints never loop
        }

        ByteArrayOutputStream bin = new ByteArrayOutputStream();
        StringBuilder meshes = new StringBuilder(), accessors = new StringBuilder(), views = new StringBuilder(),
                materials = new StringBuilder(), nodes = new StringBuilder();
        List<Integer> roots = new ArrayList<Integer>();
        Map<String, Integer> seen = new HashMap<String, Integer>();
        for (int i = 0; i < parts.size(); i++) {
            SolidSpec.Part p = parts.get(i);
            float[] tri = triangles(p, k);
            float[] nrm = normals(tri);
            int count = tri.length / 3;
            float[] lo = {1e9f, 1e9f, 1e9f}, hi = {-1e9f, -1e9f, -1e9f};
            for (int v = 0; v < count; v++) {
                for (int c = 0; c < 3; c++) {
                    lo[c] = Math.min(lo[c], tri[v * 3 + c]);
                    hi[c] = Math.max(hi[c], tri[v * 3 + c]);
                }
            }
            int posView = view(bin, tri, views), nrmView = view(bin, nrm, views);
            sep(accessors).append("{\"bufferView\":").append(posView).append(",\"componentType\":5126,\"count\":").append(count)
                    .append(",\"type\":\"VEC3\",\"min\":[").append(f(lo[0])).append(',').append(f(lo[1])).append(',').append(f(lo[2]))
                    .append("],\"max\":[").append(f(hi[0])).append(',').append(f(hi[1])).append(',').append(f(hi[2])).append("]}");
            sep(accessors).append("{\"bufferView\":").append(nrmView).append(",\"componentType\":5126,\"count\":").append(count)
                    .append(",\"type\":\"VEC3\"}");
            sep(materials).append("{\"name\":").append(str(p.name)).append(",\"pbrMetallicRoughness\":{\"baseColorFactor\":[")
                    .append(f(linear((p.colour >> 16) & 255))).append(',').append(f(linear((p.colour >> 8) & 255))).append(',')
                    .append(f(linear(p.colour & 255))).append(",1],\"metallicFactor\":0,\"roughnessFactor\":0.7}}");
            String partName = unique(p.name, seen);
            sep(meshes).append("{\"name\":").append(str(partName)).append(",\"primitives\":[{\"attributes\":{\"POSITION\":")
                    .append(i * 2).append(",\"NORMAL\":").append(i * 2 + 1).append("},\"material\":").append(i).append("}]}");
            // the joint sits at the part's centre, relative to the part it hangs from
            float px = (p.x - cx) * k, py = (ground - p.y) * k, pz = (p.z - cz) * k;
            if (parent[i] >= 0) {
                SolidSpec.Part up = parts.get(parent[i]);
                px -= (up.x - cx) * k;
                py -= (ground - up.y) * k;
                pz -= (up.z - cz) * k;
            } else {
                roots.add(i);
            }
            StringBuilder kids = new StringBuilder();
            for (int j = 0; j < parts.size(); j++) {
                if (parent[j] == i) {
                    kids.append(kids.length() == 0 ? "" : ",").append(j + 1);
                }
            }
            sep(nodes).append("{\"name\":").append(str(partName)).append(",\"mesh\":").append(i).append(",\"translation\":[")
                    .append(f(px)).append(',').append(f(py)).append(',').append(f(pz)).append(']')
                    .append(kids.length() > 0 ? ",\"children\":[" + kids + "]" : "").append('}');
        }
        StringBuilder rootKids = new StringBuilder();
        for (int r : roots) {
            rootKids.append(rootKids.length() == 0 ? "" : ",").append(r + 1);
        }
        String json = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"DVINE\"},\"scene\":0,\"scenes\":[{\"name\":" + str(name)
                + ",\"nodes\":[0]}],\"nodes\":[{\"name\":" + str(name) + (rootKids.length() > 0 ? ",\"children\":[" + rootKids + "]" : "")
                + "}" + (nodes.length() > 0 ? "," + nodes : "") + "],\"meshes\":[" + meshes + "],\"materials\":[" + materials
                + "],\"accessors\":[" + accessors + "],\"bufferViews\":[" + views + "],\"buffers\":[{\"byteLength\":" + bin.size() + "}]}";
        byte[] j = pad(json.getBytes(StandardCharsets.UTF_8), (byte) ' ');
        byte[] b = pad(bin.toByteArray(), (byte) 0);
        ByteBuffer out = ByteBuffer.allocate(12 + 8 + j.length + 8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546C67).putInt(2).putInt(out.capacity());
        out.putInt(j.length).putInt(0x4E4F534A).put(j);
        out.putInt(b.length).putInt(0x004E4942).put(b);
        return out.array();
    }

    /** "cat.glb" from "a cute cat!" */
    public static String fileName(String subject) {
        String s = subject == null ? "" : subject.toLowerCase(Locale.US).replaceAll("^(a|an|the|some) ", "")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return (s.isEmpty() ? "dvine-drawing" : s.length() > 40 ? s.substring(0, 40) : s) + ".glb";
    }

    // ------------------------------------------------------------ the solids as triangles

    /** The part's surface as triangles around its own centre, in metres, y up, wound outward. */
    static float[] triangles(SolidSpec.Part p, float k) {
        List<float[]> tris = new ArrayList<float[]>();
        float a = p.w / 2, b = p.h / 2, c = p.d / 2;
        switch (p.solid) {
            case SPHERE:
            case ELLIPSOID:
                for (int r = 0; r < RINGS; r++) {
                    for (int s = 0; s < SEGMENTS; s++) {
                        float[] q00 = ell(a, b, c, r, s), q01 = ell(a, b, c, r, s + 1), q10 = ell(a, b, c, r + 1, s), q11 = ell(a, b, c, r + 1, s + 1);
                        quad(tris, q00, q01, q11, q10, null);
                    }
                }
                break;
            case BOX:
                for (int axis = 0; axis < 3; axis++) {
                    for (int side = -1; side <= 1; side += 2) {
                        float[][] q = new float[4][];
                        for (int n = 0; n < 4; n++) {
                            float u = n == 0 || n == 3 ? -1 : 1, v = n < 2 ? -1 : 1;
                            float[] pt = new float[3];
                            pt[axis] = side;
                            pt[(axis + 1) % 3] = u;
                            pt[(axis + 2) % 3] = v;
                            q[n] = new float[]{pt[0] * a, pt[1] * b, pt[2] * c};
                        }
                        quad(tris, q[0], q[1], q[2], q[3], null);
                    }
                }
                break;
            case RING: {
                boolean sideways = p.w < p.d;
                float big = (sideways ? Math.max(b, c) : Math.max(a, b)) * 0.8f, tube = Math.max(1.2f, (sideways ? a : c) * 0.9f + big * 0.12f);
                for (int i = 0; i < SEGMENTS; i++) {
                    for (int j = 0; j < RINGS; j++) {
                        float[] q00 = torus(big, tube, i, j, sideways), q01 = torus(big, tube, i + 1, j, sideways),
                                q11 = torus(big, tube, i + 1, j + 1, sideways), q10 = torus(big, tube, i, j + 1, sideways);
                        float[] centre = torusCentre(big, i + 0.5f, sideways);
                        quad(tris, q00, q01, q11, q10, centre);
                    }
                }
                break;
            }
            default: { // cylinder, disc, cone, pyramid, as SolidSpec samples them
                int axis = p.solid == SolidSpec.Solid.DISC ? (p.w < p.d ? 0 : 2) : p.axis;
                float len = axis == 0 ? p.w : axis == 1 ? p.h : p.d;
                float r1 = axis == 0 ? Math.min(b, c) : axis == 1 ? Math.min(a, c) : Math.min(a, b);
                boolean pointed = p.solid == SolidSpec.Solid.CONE || p.solid == SolidSpec.Solid.PYRAMID;
                boolean square = p.solid == SolidSpec.Solid.PYRAMID;
                int seg = square ? 4 : SEGMENTS;
                float rr = square ? (float) (r1 * Math.sqrt(2)) : r1;
                float[] baseC = round(axis, len, 0, 0, -1), tipC = round(axis, len, 0, 0, 1);
                for (int s = 0; s < seg; s++) {
                    double t0 = Math.PI * 2 * s / seg + (square ? Math.PI / 4 : 0), t1 = Math.PI * 2 * (s + 1) / seg + (square ? Math.PI / 4 : 0);
                    float[] b0 = round(axis, len, (float) Math.cos(t0) * rr, (float) Math.sin(t0) * rr, -1);
                    float[] b1 = round(axis, len, (float) Math.cos(t1) * rr, (float) Math.sin(t1) * rr, -1);
                    if (pointed) {
                        tri(tris, b0, b1, tipC, null);
                    } else {
                        float[] u0 = round(axis, len, (float) Math.cos(t0) * rr, (float) Math.sin(t0) * rr, 1);
                        float[] u1 = round(axis, len, (float) Math.cos(t1) * rr, (float) Math.sin(t1) * rr, 1);
                        quad(tris, b0, b1, u1, u0, null);
                        tri(tris, u0, u1, tipC, null);
                    }
                    tri(tris, b0, b1, baseC, null);
                }
            }
        }
        float[] out = new float[tris.size() * 9];
        for (int i = 0; i < tris.size(); i++) {
            float[] t = tris.get(i);
            for (int v = 0; v < 3; v++) {
                out[i * 9 + v * 3] = t[v * 3] * k;
                out[i * 9 + v * 3 + 1] = -t[v * 3 + 1] * k; // canvas y down → y up
                out[i * 9 + v * 3 + 2] = t[v * 3 + 2] * k;
            }
            // turning y over mirrors the triangle: swap two corners so it still faces out
            for (int e = 0; e < 3; e++) {
                float tmp = out[i * 9 + 3 + e];
                out[i * 9 + 3 + e] = out[i * 9 + 6 + e];
                out[i * 9 + 6 + e] = tmp;
            }
        }
        return out;
    }

    private static float[] ell(float a, float b, float c, int r, int s) {
        double v = Math.PI * r / RINGS, u = Math.PI * 2 * s / SEGMENTS;
        return new float[]{(float) (a * Math.sin(v) * Math.cos(u)), (float) (b * Math.cos(v)), (float) (c * Math.sin(v) * Math.sin(u))};
    }

    private static float[] torus(float big, float tube, int i, int j, boolean sideways) {
        double t = Math.PI * 2 * i / SEGMENTS, q = Math.PI * 2 * j / RINGS;
        float rx = (float) Math.cos(t), ry = (float) Math.sin(t), cq = (float) Math.cos(q), sq = (float) Math.sin(q);
        return sideways ? new float[]{tube * sq, (big + tube * cq) * ry, (big + tube * cq) * rx}
                : new float[]{(big + tube * cq) * rx, (big + tube * cq) * ry, tube * sq};
    }

    private static float[] torusCentre(float big, float i, boolean sideways) {
        double t = Math.PI * 2 * i / SEGMENTS;
        float rx = (float) Math.cos(t), ry = (float) Math.sin(t);
        return sideways ? new float[]{0, big * ry, big * rx} : new float[]{big * rx, big * ry, 0};
    }

    /** A point of a round solid: (u, v) across its axis, {@code along} -1 at the base to 1 at the tip (canvas, y down). */
    private static float[] round(int axis, float len, float u, float v, float along) {
        float off = along * len / 2;
        return axis == 1 ? new float[]{u, -off, v} : axis == 0 ? new float[]{off, u, v} : new float[]{u, v, off};
    }

    private static void quad(List<float[]> tris, float[] p0, float[] p1, float[] p2, float[] p3, float[] inside) {
        tri(tris, p0, p1, p2, inside);
        tri(tris, p0, p2, p3, inside);
    }

    /** A triangle wound to face away from {@code inside} (the part's centre when null); degenerate ones are dropped. */
    private static void tri(List<float[]> tris, float[] p0, float[] p1, float[] p2, float[] inside) {
        float[] n = cross(p0, p1, p2);
        double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len < 1e-6) {
            return;
        }
        float mx = (p0[0] + p1[0] + p2[0]) / 3, my = (p0[1] + p1[1] + p2[1]) / 3, mz = (p0[2] + p1[2] + p2[2]) / 3;
        if (inside != null) {
            mx -= inside[0];
            my -= inside[1];
            mz -= inside[2];
        }
        boolean out = n[0] * mx + n[1] * my + n[2] * mz >= 0;
        tris.add(out ? new float[]{p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]}
                : new float[]{p0[0], p0[1], p0[2], p2[0], p2[1], p2[2], p1[0], p1[1], p1[2]});
    }

    private static float[] cross(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    /** Flat normals, one per triangle, repeated on its three corners. */
    static float[] normals(float[] tri) {
        float[] out = new float[tri.length];
        for (int i = 0; i + 8 < tri.length; i += 9) {
            float[] n = cross(new float[]{tri[i], tri[i + 1], tri[i + 2]}, new float[]{tri[i + 3], tri[i + 4], tri[i + 5]},
                    new float[]{tri[i + 6], tri[i + 7], tri[i + 8]});
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            for (int v = 0; v < 3; v++) {
                out[i + v * 3] = len > 0 ? n[0] / len : 0;
                out[i + v * 3 + 1] = len > 0 ? n[1] / len : 1;
                out[i + v * 3 + 2] = len > 0 ? n[2] / len : 0;
            }
        }
        return out;
    }

    // ------------------------------------------------------------ the file

    private static int view(ByteArrayOutputStream bin, float[] data, StringBuilder views) {
        int offset = bin.size();
        ByteBuffer bb = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : data) {
            bb.putFloat(v);
        }
        bin.write(bb.array(), 0, bb.capacity());
        int index = views.length() == 0 ? 0 : views.toString().split("\\{").length - 1;
        sep(views).append("{\"buffer\":0,\"byteOffset\":").append(offset).append(",\"byteLength\":").append(data.length * 4)
                .append(",\"target\":34962}");
        return index;
    }

    private static StringBuilder sep(StringBuilder b) {
        return b.length() == 0 ? b : b.append(',');
    }

    private static byte[] pad(byte[] b, byte with) {
        int n = (b.length + 3) & ~3;
        byte[] out = java.util.Arrays.copyOf(b, n);
        for (int i = b.length; i < n; i++) {
            out[i] = with;
        }
        return out;
    }

    private static String unique(String name, Map<String, Integer> seen) {
        int n = seen.merge(name, 1, Integer::sum);
        return n == 1 ? name : name + " " + n;
    }

    private static float linear(int srgb) {
        float c = srgb / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static String f(float v) {
        return String.format(Locale.US, "%.5f", v);
    }

    private static String str(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\x00-\\x1f]", " ")) + "\"";
    }
}
