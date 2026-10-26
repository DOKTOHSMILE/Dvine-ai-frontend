package ai.dvine.human.build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.dvine.face.nano.DotBody;
import ai.dvine.human.mesh.SurfaceNets;
import ai.dvine.human.mesh.TriMesh;
import ai.dvine.human.rig.Skeleton;

/**
 * The standard human body: the MakeHuman base mesh (CC0) in its young male
 * or female shape, with MakeHuman's own skin weights folded onto DVINE's
 * bones (baked by {@code tools/make_standard_body.py} into the resource
 * {@code ai/dvine/human/standard_body.bin}).
 *
 * The body is retargeted onto the blockout skeleton the rest of the builder
 * works in: scaled so its neck stands where ours does, then every bone is
 * turned and stretched (linear-blend, by MakeHuman's weights) so the bone runs
 * from our joint to our child joint. That straightens MakeHuman's arms into
 * our A-pose and lays the hands palm-down with the thumb forward, as the rig
 * expects. MakeHuman's head is removed (the persona keeps its own head and
 * face): everything inside our head or on MakeHuman's head bones goes, the
 * neck's top edge is drawn into our head, and the opening is capped inside it.
 */
final class StandardBody {
    static final String RESOURCE = "/ai/dvine/human/standard_body.bin";

    /** The baked data: shared topology, one shape per persona, joints and the skin. */
    static final class Data {
        int nv;
        int[] tri;
        String[] personas;
        float[][] pos;
        final Map<String, float[][]> joints = new HashMap<String, float[][]>();
        byte[] bone0, bone1;
        float[] weight0;
        String notice;

        int persona(boolean female) {
            String want = female ? "female" : "male";
            for (int i = 0; i < personas.length; i++) {
                if (personas[i].equals(want)) {
                    return i;
                }
            }
            throw new IllegalStateException("no " + want + " body in " + RESOURCE);
        }
    }

    /** A body in the blockout frame, with the bone each vertex mostly rides. */
    static final class Result {
        final TriMesh mesh;
        /** MakeHuman's skin folded onto our bones: the main bone (also the label), the second, and the main weight. */
        final int[] bone;
        final int[] bone1;
        final float[] weight0;
        /** Neck edge points that did not reach the head. */
        final int farSnaps;

        Result(TriMesh mesh, int[] bone, int[] bone1, float[] weight0, int farSnaps) {
            this.mesh = mesh;
            this.bone = bone;
            this.bone1 = bone1;
            this.weight0 = weight0;
            this.farSnaps = farSnaps;
        }
    }

    /** Where MakeHuman's neck is cut, above our neck joint. */
    static final float NECK_CUT = -0.02f;
    /** The neck carried up into the head: ring spacing, and how high above the cut the neck's top aims. */
    static final float RING_STEP = 0.03f, AIM_ABOVE = 0.3f;
    private static final float FLOOR = -4.2f;
    private static Data data;

    private StandardBody() {
    }

    static synchronized Data data() {
        if (data == null) {
            data = read(resource());
        }
        return data;
    }

    private static byte[] resource() {
        try (InputStream in = StandardBody.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("unreadable " + RESOURCE, e);
        }
    }

    static Data read(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (b.get() != 'D' || b.get() != 'V' || b.get() != 'S' || b.get() != 'B') {
            throw new IllegalArgumentException("not a standard body file");
        }
        int version = b.getInt();
        if (version != 2) {
            throw new IllegalArgumentException("unsupported standard body version " + version);
        }
        Data d = new Data();
        d.nv = b.getInt();
        int nt = b.getInt();
        d.tri = new int[nt * 3];
        for (int i = 0; i < d.tri.length; i++) {
            d.tri[i] = b.getShort() & 0xffff;
            if (d.tri[i] >= d.nv) {
                throw new IllegalArgumentException("triangle index out of range");
            }
        }
        int np = b.getInt();
        d.personas = new String[np];
        d.pos = new float[np][];
        for (int p = 0; p < np; p++) {
            d.personas[p] = str(b);
            d.pos[p] = new float[d.nv * 3];
            for (int i = 0; i < d.pos[p].length; i++) {
                d.pos[p][i] = b.getFloat();
            }
        }
        int nj = b.getInt();
        for (int j = 0; j < nj; j++) {
            String name = str(b);
            float[][] at = new float[np][3];
            for (int p = 0; p < np; p++) {
                at[p][0] = b.getFloat();
                at[p][1] = b.getFloat();
                at[p][2] = b.getFloat();
            }
            d.joints.put(name, at);
        }
        d.bone0 = new byte[d.nv];
        d.bone1 = new byte[d.nv];
        d.weight0 = new float[d.nv];
        b.get(d.bone0);
        b.get(d.bone1);
        for (int i = 0; i < d.nv; i++) {
            d.weight0[i] = b.getFloat();
            if ((d.bone0[i] & 0xff) >= Skeleton.COUNT || (d.bone1[i] & 0xff) >= Skeleton.COUNT || !(d.weight0[i] >= 0f && d.weight0[i] <= 1f)) {
                throw new IllegalArgumentException("invalid skin weight");
            }
        }
        d.notice = str(b);
        return d;
    }

    private static String str(ByteBuffer b) {
        int n = b.getShort() & 0xffff;
        byte[] s = new byte[n];
        b.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ retarget

    /** The MakeHuman joint each DVINE bone starts at, and the one it runs to (null: computed). */
    private static String mhJoint(int bone, String s) {
        switch (bone) {
            case DotBody.PELVIS: return "pelvis";
            case DotBody.SPINE: return "spine-3";
            case DotBody.CHEST: return "spine-1";
            case DotBody.NECK: return "neck";
            case DotBody.HEAD: return "head";
            default:
        }
        if (bone < DotBody.BONES) {
            int b = Skeleton.rightSide(bone) ? bone - 3 : bone;
            switch (b) {
                case DotBody.ARM_L: return s + "-shoulder";
                case DotBody.FORE_L: return s + "-elbow";
                case DotBody.HAND_L: return s + "-hand";
                case DotBody.THIGH_L: return s + "-upper-leg";
                case DotBody.SHIN_L: return s + "-knee";
                default: return s + "-ankle";
            }
        }
        int k = (bone - (Skeleton.rightSide(bone) ? Skeleton.FINGER_R : Skeleton.FINGER_L));
        int finger = k / 2, seg = k % 2;
        // thumb: base at the carpometacarpal joint, middle at the knuckle; fingers: knuckle and middle joint
        return s + "-finger-" + (finger + 1) + "-" + (finger == 0 ? seg + 1 : seg + 2);
    }

    static Result build(boolean female, Skeleton sk, SurfaceNets.Field head) {
        Data d = data();
        int p = d.persona(female);
        int n = d.nv;
        float[] mh = d.pos[p].clone();
        float[] ground = d.joints.get("ground")[p], neck = d.joints.get("neck")[p];
        float[] jn = sk.joint;
        // 1. one similarity: MakeHuman's ground on our floor, its neck at our neck's height and depth
        final float s = (jn[DotBody.NECK * 3 + 1] - FLOOR) / (neck[1] - ground[1]);
        final float gy = ground[1], nz = neck[2], oz = jn[DotBody.NECK * 3 + 2];
        for (int i = 0; i < n; i++) {
            mh[i * 3] *= s;
            mh[i * 3 + 1] = FLOOR + (mh[i * 3 + 1] - gy) * s;
            mh[i * 3 + 2] = (mh[i * 3 + 2] - nz) * s + oz;
        }
        Map<String, float[]> J = new HashMap<String, float[]>();
        for (Map.Entry<String, float[][]> e : d.joints.entrySet()) {
            float[] q = e.getValue()[p];
            J.put(e.getKey(), new float[]{q[0] * s, FLOOR + (q[1] - gy) * s, (q[2] - nz) * s + oz});
        }
        // 2. each bone's start and end, MakeHuman's and ours
        float[][] from = new float[Skeleton.COUNT][], to = new float[Skeleton.COUNT][];
        float[][] fromEnd = new float[Skeleton.COUNT][], toEnd = new float[Skeleton.COUNT][];
        for (int b = 0; b < Skeleton.COUNT; b++) {
            from[b] = J.get(mhJoint(b, Skeleton.rightSide(b) ? "r" : "l"));
            to[b] = new float[]{jn[b * 3], jn[b * 3 + 1], jn[b * 3 + 2]};
        }
        for (int b = 0; b < Skeleton.COUNT; b++) {
            int side = Skeleton.rightSide(b) ? -1 : 1;
            int child = childOf(b);
            if (child >= 0) {
                fromEnd[b] = from[child];
                toEnd[b] = to[child];
            } else if (b == DotBody.HAND_L || b == DotBody.HAND_R) {
                int mid = (side > 0 ? Skeleton.FINGER_L : Skeleton.FINGER_R) + 4;
                fromEnd[b] = from[mid];
                toEnd[b] = to[mid];
            } else if (b == DotBody.FOOT_L || b == DotBody.FOOT_R) {
                fromEnd[b] = farthest(mh, d.bone0, b, 0f, 0f, 1f);
                toEnd[b] = new float[]{side * (sk.hx + 0.05f), -4.16f, 0.38f};
            } else if (Skeleton.isFinger(b)) {
                // the tip: the farthest point of this segment along it
                float[] a = from[b];
                float[] dir = sub(from[b], from[b - 1]);
                fromEnd[b] = farthest(mh, d.bone0, b, dir[0], dir[1], dir[2]);
                int k = (b - (side > 0 ? Skeleton.FINGER_L : Skeleton.FINGER_R)) / 2;
                float a2 = female ? 0.9f : 1f;
                if (k == 0) {
                    float tl = Skeleton.FINGER_LEN[0] * a2;
                    float[] base = Skeleton.THUMB_BASE, td = Skeleton.THUMB_DIR;
                    toEnd[b] = sk.arm(side, base[0] + td[0] * tl, base[1] + td[1] * tl, base[2] * a2 + td[2] * tl);
                } else {
                    toEnd[b] = sk.arm(side, Skeleton.KNUCKLE + Skeleton.FINGER_LEN[k] * a2, -0.02f,
                            Skeleton.FINGER_Z[k] * Skeleton.SPREAD_TIP);
                }
                if (a == null) {
                    throw new IllegalStateException("missing joint for " + Skeleton.NAMES[b]);
                }
            }
        }
        // 3. a transform per bone: turn MakeHuman's bone onto ours and stretch it along its length
        float[][] R = new float[Skeleton.COUNT][];
        float[] stretch = new float[Skeleton.COUNT];
        float[][] axis = new float[Skeleton.COUNT][];
        for (int b = 0; b < Skeleton.COUNT; b++) {
            if (fromEnd[b] == null) {
                continue;
            }
            float[] u = sub(fromEnd[b], from[b]), v = sub(toEnd[b], to[b]);
            stretch[b] = len(v) / len(u);
            axis[b] = norm(u);
            if (b == DotBody.HAND_L || b == DotBody.HAND_R) {
                // the whole hand frame: along the middle finger, and across from the little finger to the index
                int base = b == DotBody.HAND_L ? Skeleton.FINGER_L : Skeleton.FINGER_R;
                float[] across = sub(from[base + 2], from[base + 8]), acrossTo = sub(to[base + 2], to[base + 8]);
                R[b] = frame(u, across, v, acrossTo);
            } else if (Skeleton.isFinger(b)) {
                int hand = b < Skeleton.FINGER_R ? DotBody.HAND_L : DotBody.HAND_R;
                float[] turned = mul(R[hand], u);
                R[b] = mulM(align(turned, v), R[hand]);
            } else {
                R[b] = align(u, v);
            }
        }
        // the head (removed) and anything without its own transform rides the neck
        R[DotBody.HEAD] = R[DotBody.NECK];
        axis[DotBody.HEAD] = axis[DotBody.NECK];
        stretch[DotBody.HEAD] = 1f;
        from[DotBody.HEAD] = from[DotBody.NECK];
        to[DotBody.HEAD] = to[DotBody.NECK];
        // the trunk (pelvis to head) is one smooth warp instead: heights mapped through the hip, shoulder and
        // neck joints, widths and depth eased between them, so the limb roots land on our joints and the
        // torso keeps its standard shape between them
        float[] hipM = J.get("l-upper-leg"), shM = J.get("l-shoulder");
        Torso torso = new Torso(new float[]{hipM[1], shM[1], J.get("neck")[1]},
                new float[]{jn[DotBody.THIGH_L * 3 + 1], sk.shy, jn[DotBody.NECK * 3 + 1]},
                new float[]{sk.hx / hipM[0], sk.sh / shM[0], 1f},
                new float[]{jn[DotBody.THIGH_L * 3 + 2] - hipM[2], jn[DotBody.ARM_L * 3 + 2] - shM[2], 0f});
        float[] out = new float[n * 3];
        float[] q = new float[3], r = new float[3];
        for (int i = 0; i < n; i++) {
            int b0 = d.bone0[i] & 0xff, b1 = d.bone1[i] & 0xff;
            float w = d.weight0[i];
            place(b0, mh, i, from, to, R, axis, stretch, torso, q);
            place(b1, mh, i, from, to, R, axis, stretch, torso, r);
            for (int k = 0; k < 3; k++) {
                out[i * 3 + k] = w * q[k] + (1f - w) * r[k];
            }
        }
        // 4. take MakeHuman's head off just above the base of the neck, then carry the neck up into our head
        float cut = jn[DotBody.NECK * 3 + 1] + NECK_CUT;
        boolean[] gone = new boolean[n];
        for (int i = 0; i < n; i++) {
            gone[i] = out[i * 3 + 1] > cut;
        }
        List<int[]> keep = new ArrayList<int[]>();
        for (int t = 0; t < d.tri.length; t += 3) {
            int a = d.tri[t], b = d.tri[t + 1], c = d.tri[t + 2];
            if (!gone[a] && !gone[b] && !gone[c]) {
                keep.add(new int[]{a, b, c});
            }
        }
        int[] map = new int[n];
        Arrays.fill(map, -1);
        int m = 0;
        for (int[] t : keep) {
            for (int v : t) {
                if (map[v] < 0) {
                    map[v] = m++;
                }
            }
        }
        List<Float> pos = new ArrayList<Float>();
        List<Integer> bone = new ArrayList<Integer>(), bone1 = new ArrayList<Integer>();
        List<Float> weight = new ArrayList<Float>();
        float[] tmp = new float[m * 3];
        int[] tmpBone = new int[m], tmpBone1 = new int[m];
        float[] tmpW = new float[m];
        for (int i = 0; i < n; i++) {
            if (map[i] >= 0) {
                System.arraycopy(out, i * 3, tmp, map[i] * 3, 3);
                tmpBone[map[i]] = d.bone0[i] & 0xff;
                tmpBone1[map[i]] = d.bone1[i] & 0xff;
                tmpW[map[i]] = d.weight0[i];
            }
        }
        for (int i = 0; i < m; i++) {
            pos.add(tmp[i * 3]);
            pos.add(tmp[i * 3 + 1]);
            pos.add(tmp[i * 3 + 2]);
            // what is left of MakeHuman's head is neck
            bone.add(tmpBone[i] == DotBody.HEAD ? DotBody.NECK : tmpBone[i]);
            bone1.add(tmpBone1[i] == DotBody.HEAD ? DotBody.NECK : tmpBone1[i]);
            weight.add(tmpW[i]);
        }
        List<Integer> tris = new ArrayList<Integer>();
        for (int[] t : keep) {
            tris.add(map[t[0]]);
            tris.add(map[t[1]]);
            tris.add(map[t[2]]);
        }
        // the cut edge: directed edges with no twin, walked as loops (the new surface runs the other way round)
        java.util.Set<Long> directed = new java.util.HashSet<Long>();
        for (int t = 0; t < tris.size(); t += 3) {
            for (int k = 0; k < 3; k++) {
                directed.add(key(tris.get(t + k), tris.get(t + (k + 1) % 3)));
            }
        }
        Map<Integer, Integer> succ = new HashMap<Integer, Integer>();
        for (long e : directed) {
            int a = (int) (e >>> 32), b = (int) e;
            if (!directed.contains(key(b, a))) {
                succ.put(b, a);
            }
        }
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
        int far = 0, loops = 0;
        for (int start : new ArrayList<Integer>(succ.keySet())) {
            if (seen.contains(start)) {
                continue;
            }
            List<Integer> loop = new ArrayList<Integer>();
            for (int v = start; !seen.contains(v); v = succ.get(v)) {
                seen.add(v);
                loop.add(v);
                if (!succ.containsKey(v)) {
                    throw new IllegalStateException("the neck's cut edge does not close");
                }
            }
            loops++;
            // the neck's axis at the cut, and how far each edge point must rise to be inside the head
            float ax = 0f, az = 0f;
            for (int v : loop) {
                ax += pos.get(v * 3);
                az += pos.get(v * 3 + 2);
            }
            ax /= loop.size();
            az /= loop.size();
            float[][] top = new float[loop.size()][];
            float longest = 0f;
            float qy = cut + AIM_ABOVE;
            for (int k = 0; k < loop.size(); k++) {
                int v = loop.get(k);
                float x = pos.get(v * 3), y = pos.get(v * 3 + 1), z = pos.get(v * 3 + 2);
                // head for a point on the neck's axis inside our head's neck, until inside the head
                float dx = ax - x, dy = qy - y, dz = az - z;
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= l;
                dy /= l;
                dz /= l;
                float go = 0f;
                while (head.d(x + dx * go, y + dy * go, z + dz * go) > -0.006f) {
                    go += 0.004f;
                    if (go > l) {
                        far++;
                        break;
                    }
                }
                top[k] = new float[]{x + dx * go, y + dy * go, z + dz * go};
                longest = Math.max(longest, go);
            }
            int rings = Math.max(1, (int) Math.ceil(longest / RING_STEP));
            int[] prev = new int[loop.size()];
            for (int k = 0; k < loop.size(); k++) {
                prev[k] = loop.get(k);
            }
            for (int j = 1; j <= rings; j++) {
                float t = (float) j / rings;
                int[] ring = new int[loop.size()];
                for (int k = 0; k < loop.size(); k++) {
                    int v = loop.get(k);
                    ring[k] = pos.size() / 3;
                    for (int c = 0; c < 3; c++) {
                        float p0 = pos.get(v * 3 + c);
                        pos.add(p0 + (top[k][c] - p0) * t);
                    }
                    bone.add(DotBody.NECK);
                    bone1.add(DotBody.NECK);
                    weight.add(1f);
                }
                for (int k = 0; k < loop.size(); k++) {
                    int k1 = (k + 1) % loop.size();
                    // loop runs b → a along the cut; keep the winding of the faces below
                    int b0 = prev[k], a0 = prev[k1], b1 = ring[k], a1 = ring[k1];
                    tris.add(b0); tris.add(a0); tris.add(a1);
                    tris.add(b0); tris.add(a1); tris.add(b1);
                }
                prev = ring;
            }
            // close the top inside the head
            float cx = 0f, cy = 0f, cz = 0f;
            for (int v : prev) {
                cx += pos.get(v * 3);
                cy += pos.get(v * 3 + 1);
                cz += pos.get(v * 3 + 2);
            }
            int centre = pos.size() / 3;
            pos.add(cx / prev.length);
            pos.add(cy / prev.length);
            pos.add(cz / prev.length);
            bone.add(DotBody.NECK);
            bone1.add(DotBody.NECK);
            weight.add(1f);
            for (int k = 0; k < prev.length; k++) {
                tris.add(prev[k]);
                tris.add(prev[(k + 1) % prev.length]);
                tris.add(centre);
            }
        }
        if (loops != 1) {
            throw new IllegalStateException("the neck cut left " + loops + " openings");
        }
        float[] all = new float[pos.size()];
        for (int i = 0; i < all.length; i++) {
            all[i] = pos.get(i);
        }
        int[] allBone = new int[bone.size()], allBone1 = new int[bone.size()];
        float[] allW = new float[bone.size()];
        for (int i = 0; i < allBone.length; i++) {
            allBone[i] = bone.get(i);
            allBone1[i] = bone1.get(i);
            allW[i] = weight.get(i);
        }
        int[] allTri = new int[tris.size()];
        for (int i = 0; i < allTri.length; i++) {
            allTri[i] = tris.get(i);
        }
        TriMesh mesh = new TriMesh();
        mesh.pos = all;
        mesh.tri = allTri;
        mesh.computeNormals();
        return new Result(mesh, allBone, allBone1, allW, far);
    }

    private static int childOf(int b) {
        switch (b) {
            case DotBody.PELVIS: return DotBody.SPINE;
            case DotBody.SPINE: return DotBody.CHEST;
            case DotBody.CHEST: return DotBody.NECK;
            case DotBody.NECK: return DotBody.HEAD;
            case DotBody.ARM_L: return DotBody.FORE_L;
            case DotBody.FORE_L: return DotBody.HAND_L;
            case DotBody.ARM_R: return DotBody.FORE_R;
            case DotBody.FORE_R: return DotBody.HAND_R;
            case DotBody.THIGH_L: return DotBody.SHIN_L;
            case DotBody.SHIN_L: return DotBody.FOOT_L;
            case DotBody.THIGH_R: return DotBody.SHIN_R;
            case DotBody.SHIN_R: return DotBody.FOOT_R;
            default:
        }
        if (Skeleton.isFinger(b)) {
            int base = b < Skeleton.FINGER_R ? Skeleton.FINGER_L : Skeleton.FINGER_R;
            return (b - base) % 2 == 0 ? b + 1 : -1;
        }
        return -1;
    }

    private static void place(int b, float[] p, int i, float[][] from, float[][] to, float[][] R, float[][] axis,
                              float[] stretch, Torso torso, float[] out) {
        if (b <= DotBody.HEAD) {
            torso.map(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], out);
        } else {
            apply(b, p, i, from, to, R, axis, stretch, out);
        }
    }

    /** The trunk's warp: knots in height with a width factor and a depth shift at each, linear between. */
    static final class Torso {
        final float[] fromY, toY, sx, dz;

        Torso(float[] fromY, float[] toY, float[] sx, float[] dz) {
            for (int k = 1; k < fromY.length; k++) {
                if (!(fromY[k] > fromY[k - 1]) || !(toY[k] > toY[k - 1])) {
                    throw new IllegalStateException("trunk joints out of order");
                }
            }
            this.fromY = fromY;
            this.toY = toY;
            this.sx = sx;
            this.dz = dz;
        }

        void map(float x, float y, float z, float[] out) {
            int n = fromY.length;
            int k = 1;
            while (k < n - 1 && y > fromY[k]) {
                k++;
            }
            float t = (y - fromY[k - 1]) / (fromY[k] - fromY[k - 1]);
            float tc = Math.max(0f, Math.min(1f, t));
            if (y > fromY[n - 1]) {
                // above the neck: shift with the neck, no stretch
                out[1] = toY[n - 1] + (y - fromY[n - 1]);
            } else {
                out[1] = toY[k - 1] + t * (toY[k] - toY[k - 1]);
            }
            out[0] = x * (sx[k - 1] + tc * (sx[k] - sx[k - 1]));
            out[2] = z + dz[k - 1] + tc * (dz[k] - dz[k - 1]);
        }
    }

    private static void apply(int b, float[] p, int i, float[][] from, float[][] to, float[][] R, float[][] axis,
                              float[] stretch, float[] out) {
        float x = p[i * 3] - from[b][0], y = p[i * 3 + 1] - from[b][1], z = p[i * 3 + 2] - from[b][2];
        float[] u = axis[b];
        float along = (x * u[0] + y * u[1] + z * u[2]) * (stretch[b] - 1f);
        x += along * u[0];
        y += along * u[1];
        z += along * u[2];
        float[] m = R[b];
        out[0] = to[b][0] + m[0] * x + m[1] * y + m[2] * z;
        out[1] = to[b][1] + m[3] * x + m[4] * y + m[5] * z;
        out[2] = to[b][2] + m[6] * x + m[7] * y + m[8] * z;
    }

    /** The point of bone {@code b}'s vertices farthest along {@code (dx, dy, dz)}. */
    private static float[] farthest(float[] p, byte[] bone0, int b, float dx, float dy, float dz) {
        float best = -Float.MAX_VALUE;
        float[] at = null;
        for (int i = 0; i < bone0.length; i++) {
            if ((bone0[i] & 0xff) != b) {
                continue;
            }
            float t = p[i * 3] * dx + p[i * 3 + 1] * dy + p[i * 3 + 2] * dz;
            if (t > best) {
                best = t;
                at = new float[]{p[i * 3], p[i * 3 + 1], p[i * 3 + 2]};
            }
        }
        if (at == null) {
            throw new IllegalStateException("no vertices for " + Skeleton.NAMES[b]);
        }
        return at;
    }

    // ------------------------------------------------------------ small linear algebra (3-vectors, row-major 3×3)

    private static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xffffffffL);
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float len(float[] a) {
        return (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    private static float[] norm(float[] a) {
        float l = len(a);
        return new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] mul(float[] m, float[] v) {
        return new float[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    private static float[] mulM(float[] a, float[] b) {
        float[] c = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return c;
    }

    /** The smallest rotation taking direction {@code a} to direction {@code b}. */
    static float[] align(float[] a, float[] b) {
        float[] u = norm(a), v = norm(b);
        float[] k = cross(u, v);
        float c = u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
        float sn = len(k);
        if (sn < 1e-7f) {
            if (c > 0f) {
                return new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
            }
            float[] o = Math.abs(u[0]) < 0.9f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
            k = norm(cross(u, o));
            return rot(k, (float) Math.PI);
        }
        return rot(new float[]{k[0] / sn, k[1] / sn, k[2] / sn}, (float) Math.atan2(sn, c));
    }

    private static float[] rot(float[] k, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), t = 1f - c;
        float x = k[0], y = k[1], z = k[2];
        return new float[]{t * x * x + c, t * x * y - s * z, t * x * z + s * y,
                t * x * y + s * z, t * y * y + c, t * y * z - s * x,
                t * x * z - s * y, t * y * z + s * x, t * z * z + c};
    }

    /** The rotation taking the frame (along a, across b) onto (along c, across d). */
    static float[] frame(float[] a, float[] b, float[] c, float[] d) {
        float[] f = basis(a, b), g = basis(c, d);
        // R = G · Fᵀ (columns of F and G are the frame axes)
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float sum = 0f;
                for (int k = 0; k < 3; k++) {
                    sum += g[i * 3 + k] * f[j * 3 + k];
                }
                r[i * 3 + j] = sum;
            }
        }
        return r;
    }

    private static float[] basis(float[] along, float[] across) {
        float[] e1 = norm(along);
        float dot = across[0] * e1[0] + across[1] * e1[1] + across[2] * e1[2];
        float[] e2 = norm(new float[]{across[0] - dot * e1[0], across[1] - dot * e1[1], across[2] - dot * e1[2]});
        float[] e3 = cross(e1, e2);
        // columns e1, e2, e3
        return new float[]{e1[0], e2[0], e3[0], e1[1], e2[1], e3[1], e1[2], e2[2], e3[2]};
    }
}
