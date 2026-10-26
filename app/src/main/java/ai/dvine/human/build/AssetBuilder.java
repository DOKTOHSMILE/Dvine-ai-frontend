package ai.dvine.human.build;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.face.Look;
import ai.dvine.face.nano.DotBody;
import ai.dvine.face.nano.Sculpt;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.Regions;
import ai.dvine.human.mesh.Decimator;
import ai.dvine.human.mesh.MeshValidator;
import ai.dvine.human.mesh.SurfaceNets;
import ai.dvine.human.mesh.TriMesh;
import ai.dvine.human.rig.BodyProfile;
import ai.dvine.human.rig.Skeleton;

/**
 * Builds a complete digital human from the anatomical sculpts: each piece
 * is meshed, simplified to a low-poly triangulation with more detail where
 * the face deforms, validated, labelled by body part and skinned to the
 * skeleton; then eyes, teeth, feature lines, hair strands, facial blend
 * shapes and landmarks are added. Deterministic: the same recipe always
 * gives the same asset. Used offline to make the shipped assets, and on the
 * phone for a full reconstruction.
 */
public final class AssetBuilder {
    /** Bumped whenever the recipe changes. */
    public static final int RECIPE = 2;

    /** Triangle budgets per piece. */
    public static final class Budget {
        public float headCell = 0.02f;
        public int headTris = 3200;
        public int bodyTris = 5200;
        public int handTris = 900;
    }

    private final boolean female;
    private final Look look;
    private final Budget budget;
    private final Skeleton sk;

    // line-only vertices (feature lines, iris rings, hair) gathered while building
    private final List<float[]> linePos = new ArrayList<float[]>();
    private final List<int[]> lineSeg = new ArrayList<int[]>();
    private final List<Integer> lineRegion = new ArrayList<Integer>();
    private final List<Float> lineSway = new ArrayList<Float>();

    public AssetBuilder(boolean female, Look look, Budget budget) {
        this.female = female;
        this.look = look == null ? Look.defaults(female) : look;
        this.budget = budget == null ? new Budget() : budget;
        this.sk = new Skeleton(female);
    }

    public static HumanAsset build(boolean female) {
        return new AssetBuilder(female, Look.defaults(female), new Budget()).build();
    }

    public HumanAsset build() {
        TriMesh head = headPiece();
        TriMesh body = bodyPiece();
        TriMesh eyeL = eye(1);
        TriMesh eyeR = eye(-1);
        TriMesh teeth = teeth();
        TriMesh solid = TriMesh.merge(head, body, eyeL, eyeR, teeth);
        int solidCount = solid.vertexCount();
        int headCount = head.vertexCount();
        int eyeStart = headCount + body.vertexCount();
        int teethStart = eyeStart + eyeL.vertexCount() + eyeR.vertexCount();

        // feature lines and hair are line-only vertices, appended after the solids
        featureLines(head);
        int irisStart = solidCount + linePos.size();
        int irisL = irisRing(1);
        int irisR = irisRing(-1);
        if (female && look.hair > 0.05f) {
            hair();
        }
        TriMesh m = appendLines(solid);
        m.buildEdges();

        HumanAsset a = new HumanAsset();
        a.female = female;
        a.version = RECIPE;
        a.mesh = m;
        a.joints = sk.joint.clone();
        a.lines = new int[lineSeg.size() * 2];
        for (int i = 0; i < lineSeg.size(); i++) {
            a.lines[i * 2] = lineSeg.get(i)[0] + solidCount;
            a.lines[i * 2 + 1] = lineSeg.get(i)[1] + solidCount;
        }
        a.sway = new float[m.vertexCount()];
        for (int i = 0; i < lineSway.size(); i++) {
            a.sway[solidCount + i] = lineSway.get(i);
        }
        // eye ranges: each eyeball plus its iris ring (the ring vertices are contiguous per eye)
        a.eyeRange = new int[]{eyeStart, eyeL.vertexCount(), eyeStart + eyeL.vertexCount(), eyeR.vertexCount()};
        float[] cl = toBody(Anatomy.EYE_X, Anatomy.EYE_Y, Anatomy.EYE_Z), cr = toBody(-Anatomy.EYE_X, Anatomy.EYE_Y, Anatomy.EYE_Z);
        a.eyeCenter = new float[]{cl[0], cl[1], cl[2], cr[0], cr[1], cr[2]};
        a.irisRange = new int[]{irisStart, irisL, irisStart + irisL, irisR};
        a.morphs = FaceShapes.build(m, headCount, solidCount, teethStart, teeth.vertexCount(), irisStart, irisStart + irisL + irisR);
        landmarks(a);
        InnerAnatomy.Result inside = InnerAnatomy.build(sk);
        a.inner = inside.mesh;
        a.innerNames = new String[inside.parts.size()];
        a.innerLayer = new byte[inside.parts.size()];
        for (InnerAnatomy.Part part : inside.parts) {
            a.innerNames[part.id] = part.name;
            a.innerLayer[part.id] = (byte) part.layer;
        }
        // everything above was sculpted in the blockout layout; fit it to the body-dimension profile
        BodyFit fit = BodyFit.fit(BodyProfile.standard(), sk, m, solidCount);
        fit.apply(m);
        fit.applyInner(a.inner);
        a.joints = fit.joints(sk);
        return a;
    }

    // ------------------------------------------------------------ pieces

    private TriMesh headPiece() {
        final Sculpt s = Anatomy.head(female, look);
        SurfaceNets.Field f = (x, y, z) -> Math.max(s.d(x, y, z), Anatomy.HEAD_CUT_Y - y);
        float back = female && look.hair > 0.05f ? -1.45f : -1.2f;
        TriMesh raw = SurfaceNets.extract(f, new float[]{-1.02f, Anatomy.HEAD_CUT_Y - 0.06f, back},
                new float[]{1.02f, 1.18f, 1.22f}, budget.headCell);
        TriMesh m = Decimator.simplify(raw, female ? budget.headTris + 400 : budget.headTris, AssetBuilder::headImportance, 0.004);
        require(m, "head");
        final Sculpt bare = female ? Anatomy.head(true, bald(look)) : s;
        int n = m.vertexCount();
        m.region = new byte[n];
        m.bone0 = new byte[n];
        m.bone1 = new byte[n];
        m.weight0 = new float[n];
        for (int i = 0; i < n; i++) {
            float x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
            boolean hair = female && Anatomy.hairArea(x, y, z) && bare.d(x, y, z) > 0.02f;
            m.region[i] = (byte) (hair ? Regions.HAIR : headRegion(x, y, z));
            // the head bone carries the head; the neck blends into the neck bone
            float t = smooth((y + 1.45f) / 0.5f);
            m.bone0[i] = (byte) DotBody.HEAD;
            m.bone1[i] = (byte) DotBody.NECK;
            m.weight0[i] = 0.25f + 0.75f * t;
            float[] b = toBody(x, y, z);
            m.pos[i * 3] = b[0];
            m.pos[i * 3 + 1] = b[1];
            m.pos[i * 3 + 2] = b[2];
        }
        m.computeNormals();
        return m;
    }

    /**
     * The standard body (torso, limbs, hands and feet; see {@link StandardBody})
     * on the blockout skeleton, simplified with more triangles on the hands,
     * labelled by body part and skinned. Its neck runs up into the head piece.
     */
    private TriMesh bodyPiece() {
        final Sculpt hs = Anatomy.head(female, bald(look));
        final float k = Anatomy.HEAD_SCALE;
        SurfaceNets.Field inHead = (x, y, z) -> {
            float hy = (y - Anatomy.HEAD_Y) / k;
            return Math.max(hs.d(x / k, hy, z / k), Anatomy.HEAD_CUT_Y - hy) * k;
        };
        StandardBody.Result std = StandardBody.build(female, sk, inHead);
        if (std.farSnaps > 0) {
            throw new IllegalStateException(std.farSnaps + " neck edge vertices are too far from the head to join it");
        }
        TriMesh raw = std.mesh;
        if (MeshValidator.volume(raw.pos, raw.tri) < 0) {
            flip(raw);
        }
        TriMesh m = Decimator.simplify(raw, budget.bodyTris + 2 * budget.handTris, this::bodyImportance, 0.0015);
        require(m, "body");
        int n = m.vertexCount();
        m.region = new byte[n];
        m.bone0 = new byte[n];
        m.bone1 = new byte[n];
        m.weight0 = new float[n];
        int[] nearest = nearestVertices(raw.pos, m.pos);
        for (int i = 0; i < n; i++) {
            float x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
            int src = nearest[i];
            int bone = std.bone[src];
            m.region[i] = (byte) (bone == DotBody.HAND_L || bone == DotBody.HAND_R || Skeleton.isFinger(bone)
                    ? handRegion(bone, x) : bodyRegion(bone, x, y, z));
            // MakeHuman's own skin weights, made for this body
            m.bone0[i] = (byte) bone;
            m.bone1[i] = (byte) std.bone1[src];
            m.weight0[i] = std.weight0[src];
        }
        blurWeights(m, WEIGHT_BLUR);
        m.computeNormals();
        return m;
    }

    /** Palm and finger labels. */
    private static int handRegion(int bone, float x) {
        boolean left = x >= 0f;
        if (bone == DotBody.HAND_L || bone == DotBody.HAND_R) {
            return left ? Regions.PALM_L : Regions.PALM_R;
        }
        int base = bone < Skeleton.FINGER_R ? Skeleton.FINGER_L : Skeleton.FINGER_R;
        return (base == Skeleton.FINGER_L ? Regions.THUMB_L : Regions.THUMB_R) + (bone - base) / 2 * 2;
    }

    /** For each point of {@code to}, the index of the nearest point of {@code from} (a grid search). */
    static int[] nearestVertices(float[] from, float[] to) {
        float cell = 0.05f;
        java.util.HashMap<Long, List<Integer>> grid = new java.util.HashMap<Long, List<Integer>>();
        for (int i = 0; i < from.length / 3; i++) {
            grid.computeIfAbsent(cellKey(from[i * 3], from[i * 3 + 1], from[i * 3 + 2], cell, 0, 0, 0),
                    k -> new ArrayList<Integer>()).add(i);
        }
        int[] out = new int[to.length / 3];
        for (int v = 0; v < out.length; v++) {
            float x = to[v * 3], y = to[v * 3 + 1], z = to[v * 3 + 2];
            int best = -1;
            float bd = Float.MAX_VALUE;
            for (int r = 1; best < 0 || r <= 2; r++) {
                for (int i = -r; i <= r; i++) {
                    for (int j = -r; j <= r; j++) {
                        for (int k = -r; k <= r; k++) {
                            List<Integer> c = grid.get(cellKey(x, y, z, cell, i, j, k));
                            if (c == null) {
                                continue;
                            }
                            for (int f : c) {
                                float dx = from[f * 3] - x, dy = from[f * 3 + 1] - y, dz = from[f * 3 + 2] - z;
                                float d = dx * dx + dy * dy + dz * dz;
                                if (d < bd) {
                                    bd = d;
                                    best = f;
                                }
                            }
                        }
                    }
                }
                if (r > 40) {
                    throw new IllegalStateException("no vertex near " + x + ", " + y + ", " + z);
                }
            }
            out[v] = best;
        }
        return out;
    }

    private static long cellKey(float x, float y, float z, float cell, int i, int j, int k) {
        long a = (long) Math.floor(x / cell) + i + 100000, b = (long) Math.floor(y / cell) + j + 100000,
                c = (long) Math.floor(z / cell) + k + 100000;
        return (a << 40) | (b << 20) | c;
    }

    /** A low-poly eyeball (an icosphere) in its socket. */
    private TriMesh eye(int side) {
        TriMesh m = Shapes.icosphere(1);
        float r = Anatomy.EYE_R * look.eyes;
        int n = m.vertexCount();
        m.region = new byte[n];
        m.bone0 = new byte[n];
        m.bone1 = new byte[n];
        m.weight0 = new float[n];
        for (int i = 0; i < n; i++) {
            float[] b = toBody(side * Anatomy.EYE_X + m.pos[i * 3] * r, Anatomy.EYE_Y + m.pos[i * 3 + 1] * r,
                    Anatomy.EYE_Z + m.pos[i * 3 + 2] * r);
            m.pos[i * 3] = b[0];
            m.pos[i * 3 + 1] = b[1];
            m.pos[i * 3 + 2] = b[2];
            m.region[i] = (byte) (side > 0 ? Regions.EYE_L : Regions.EYE_R);
            m.bone0[i] = (byte) DotBody.HEAD;
            m.bone1[i] = (byte) DotBody.HEAD;
            m.weight0[i] = 1f;
        }
        m.computeNormals();
        return m;
    }

    /** Upper and lower teeth: two arcs of small blocks behind the lips. */
    private TriMesh teeth() {
        List<TriMesh> parts = new ArrayList<TriMesh>();
        for (int row = 0; row < 2; row++) {
            float y = row == 0 ? Anatomy.MOUTH_Y + 0.028f : Anatomy.MOUTH_Y - 0.03f;
            for (int k = 0; k < 10; k++) {
                float a = (float) Math.toRadians(-62 + k * 13.8f);
                float cx = (float) Math.sin(a) * 0.19f * look.lips, cz = 0.79f - (1f - (float) Math.cos(a)) * 0.22f;
                TriMesh t = Shapes.box(cx, y, cz, 0.026f, 0.024f, 0.022f, a);
                parts.add(t);
            }
        }
        TriMesh m = TriMesh.merge(parts.toArray(new TriMesh[0]));
        int n = m.vertexCount();
        for (int i = 0; i < n; i++) {
            float[] b = toBody(m.pos[i * 3], m.pos[i * 3 + 1], m.pos[i * 3 + 2]);
            m.pos[i * 3] = b[0];
            m.pos[i * 3 + 1] = b[1];
            m.pos[i * 3 + 2] = b[2];
            m.region[i] = (byte) Regions.TEETH;
            m.bone0[i] = (byte) DotBody.HEAD;
            m.bone1[i] = (byte) DotBody.HEAD;
            m.weight0[i] = 1f;
        }
        m.computeNormals();
        return m;
    }

    // ------------------------------------------------------------ lines

    /** Brows, eyelids, lip lines and nostrils, seated on the head surface. */
    private void featureLines(TriMesh head) {
        Sculpt s = Anatomy.head(female, bald(look));
        float e = look.eyes;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * Anatomy.EYE_X;
            float rx = (female ? 0.16f : 0.15f) * e, ru = (female ? 0.085f : 0.075f) * e, rl = (female ? 0.06f : 0.055f) * e;
            int eyeRegion = side > 0 ? Regions.EYE_L : Regions.EYE_R;
            List<float[]> up = new ArrayList<float[]>(), lo = new ArrayList<float[]>();
            for (int i = 0; i <= 16; i++) {
                double t = Math.PI * i / 16;
                float c = (float) Math.cos(t);
                float lift = side * c > 0f ? (female ? 0.014f : 0.006f) * side * c : 0f;
                up.add(new float[]{ex + rx * c, Anatomy.EYE_Y + ru * (float) Math.sin(t) + lift});
                lo.add(new float[]{ex + rx * c, Anatomy.EYE_Y - rl * (float) Math.sin(t) + lift * 0.5f});
            }
            strip(s, up, eyeRegion, 0.006f, false);
            strip(s, lo, eyeRegion, 0.004f, false);
            List<float[]> brow = new ArrayList<float[]>();
            for (int i = 0; i <= 10; i++) {
                float u = i / 10f;
                brow.add(new float[]{side * (0.13f + 0.34f * u),
                        0.17f + (female ? 0.055f : 0.04f) * (float) Math.sin(Math.PI * Math.min(1f, u * 1.15f))});
            }
            strip(s, brow, Regions.FOREHEAD, 0.008f, false);
            List<float[]> nostril = new ArrayList<float[]>();
            float nw = (female ? 0.085f : 0.1f) * look.nose;
            for (int i = 0; i <= 6; i++) {
                double t = Math.PI * (0.15 + 0.7 * i / 6.0);
                nostril.add(new float[]{side * (nw - 0.035f * (float) Math.cos(t)), -0.405f - 0.018f * (float) Math.sin(t)});
            }
            strip(s, nostril, Regions.NOSE, 0.004f, false);
        }
        float mw = (female ? 0.2f : 0.21f) * look.lips;
        List<float[]> part = new ArrayList<float[]>(), bow = new ArrayList<float[]>(), low = new ArrayList<float[]>();
        for (int i = 0; i <= 20; i++) {
            float u = -1f + 2f * i / 20f;
            part.add(new float[]{u * mw, Anatomy.MOUTH_Y + 0.006f * (1f - u * u)});
            bow.add(new float[]{u * mw * 0.96f, Anatomy.MOUTH_Y + 0.05f * look.lips * (1f - u * u)
                    * (1f - 0.35f * (float) Math.exp(-u * u / 0.02f)) + 0.008f});
            low.add(new float[]{u * mw * 0.9f, Anatomy.MOUTH_Y - ((female ? 0.075f : 0.062f) * look.lips
                    * (float) Math.sqrt(Math.max(0f, 1f - u * u)) + 0.004f)});
        }
        strip(s, part, Regions.LIPS, 0.03f, false);
        strip(s, bow, Regions.LIPS, 0.006f, false);
        strip(s, low, Regions.LIPS, 0.006f, false);
    }

    /** A ring around the iris and one around the pupil, on the front of each eyeball (head units → body). */
    private int irisRing(int side) {
        int before = linePos.size();
        float r = Anatomy.EYE_R * look.eyes;
        int region = side > 0 ? Regions.EYE_L : Regions.EYE_R;
        for (float ang : new float[]{24f, 10f}) {
            List<float[]> ring = new ArrayList<float[]>();
            double a = Math.toRadians(ang);
            for (int i = 0; i <= 16; i++) {
                double t = i * Math.PI * 2 / 16;
                float x = (float) (Math.sin(a) * Math.cos(t)), y = (float) (Math.sin(a) * Math.sin(t)), z = (float) Math.cos(a);
                ring.add(toBody(side * Anatomy.EYE_X + x * r * 1.01f, Anatomy.EYE_Y + y * r * 1.01f, Anatomy.EYE_Z + z * r * 1.01f));
            }
            addStrip(ring, region, 0f, true);
        }
        return linePos.size() - before;
    }

    /** Ivie's hair: strands sweeping back from the hairline over the head and streaming out behind it. */
    private void hair() {
        float grow = 0.05f + 0.03f * look.hair;
        float rx = 0.74f * look.width + grow, ry = 0.84f + grow, rz = 0.9f + grow * 1.6f;
        int strands = 46;
        float flow = 0.6f + 0.45f * look.hair;
        for (int k = 0; k < strands; k++) {
            float u = -0.86f + 1.72f * (k + 0.5f) / strands + 0.02f * noise(k * 5);
            float side = (float) Math.sqrt(1f - u * u);
            float start = (float) Math.toRadians(52f + 14f * Math.abs(u) + 6f * noise(k * 5 + 1));
            float end = (float) Math.toRadians(168f + 10f * noise(k * 5 + 2));
            float lift = 1.01f + 0.015f * noise(k * 5 + 3);
            List<float[]> pts = new ArrayList<float[]>();
            List<Float> sw = new ArrayList<Float>();
            float lx = 0, ly = 0, lz = 0;
            for (int i = 0; i <= 14; i++) {
                float a = start + (end - start) * i / 14f;
                float x = u * rx * lift * (1f - 0.12f * i / 14f);
                float y = 0.26f + side * (float) Math.sin(a) * ry * lift;
                float z = -0.16f + side * (float) Math.cos(a) * rz * lift;
                pts.add(toBody(x, y, z));
                sw.add(0f);
                lx = x;
                ly = y;
                lz = z;
            }
            float len = flow * (0.75f + 0.45f * (noise(k * 5 + 4) + 1f) * 0.5f);
            for (int i = 1; i <= 10; i++) {
                float t = i / 10f;
                float x = lx * (1f + 0.55f * t) + 0.05f * t * noise(k * 7);
                float y = ly - 0.55f * len * t + 0.06f * t * noise(k * 7 + 1);
                float z = lz - len * t * (1f - 0.25f * t);
                pts.add(toBody(x, y, z));
                sw.add(t * len);
            }
            int first = linePos.size();
            addStrip(pts, Regions.HAIR, 0f, false);
            for (int i = 0; i < sw.size(); i++) {
                lineSway.set(first + i, sw.get(i) * Anatomy.HEAD_SCALE);
            }
        }
    }

    /** Seats a 2D curve (head-space x, y) onto the head surface and adds it as a line strip. */
    private void strip(Sculpt s, List<float[]> xy, int region, float lift, boolean closed) {
        List<float[]> pts = new ArrayList<float[]>();
        for (float[] p : xy) {
            float z = s.frontZ(p[0], p[1], 1.6f);
            if (Float.isNaN(z)) {
                continue;
            }
            pts.add(toBody(p[0], p[1], z + lift));
        }
        addStrip(pts, region, 0f, closed);
    }

    private void addStrip(List<float[]> pts, int region, float sway, boolean closed) {
        int first = linePos.size();
        for (float[] p : pts) {
            linePos.add(p);
            lineRegion.add(region);
            lineSway.add(sway);
        }
        for (int i = 1; i < pts.size(); i++) {
            lineSeg.add(new int[]{first + i - 1, first + i});
        }
        if (closed && pts.size() > 2) {
            lineSeg.add(new int[]{first + pts.size() - 1, first});
        }
    }

    private TriMesh appendLines(TriMesh solid) {
        int ns = solid.vertexCount(), nl = linePos.size(), n = ns + nl;
        TriMesh m = new TriMesh();
        m.pos = java.util.Arrays.copyOf(solid.pos, n * 3);
        m.tri = solid.tri;
        m.region = java.util.Arrays.copyOf(solid.region, n);
        m.bone0 = java.util.Arrays.copyOf(solid.bone0, n);
        m.bone1 = java.util.Arrays.copyOf(solid.bone1, n);
        m.weight0 = java.util.Arrays.copyOf(solid.weight0, n);
        for (int i = 0; i < nl; i++) {
            float[] p = linePos.get(i);
            m.pos[(ns + i) * 3] = p[0];
            m.pos[(ns + i) * 3 + 1] = p[1];
            m.pos[(ns + i) * 3 + 2] = p[2];
            m.region[ns + i] = (byte) (int) lineRegion.get(i);
            m.bone0[ns + i] = (byte) DotBody.HEAD;
            m.bone1[ns + i] = (byte) DotBody.HEAD;
            m.weight0[ns + i] = 1f;
        }
        m.computeNormals();
        return m;
    }

    // ------------------------------------------------------------ labels and skin

    static int headRegion(float x, float y, float z) {
        float ax = Math.abs(x);
        boolean left = x > 0f;
        if (y < -1.0f - 0.2f * Math.max(0f, z)) {
            return Regions.NECK;
        }
        if (ax > 0.66f && y > -0.3f && y < 0.28f && z < 0.2f) {
            return left ? Regions.EAR_L : Regions.EAR_R;
        }
        if (Math.abs(ax - Anatomy.EYE_X) < 0.2f && Math.abs(y - Anatomy.EYE_Y) < 0.13f && z > 0.45f) {
            return left ? Regions.EYE_L : Regions.EYE_R;
        }
        if (ax < 0.16f && y > -0.47f && y < 0.1f && z > 0.72f) {
            return Regions.NOSE;
        }
        if (ax < 0.26f && y > -0.77f && y < -0.47f && z > 0.6f) {
            return Regions.LIPS;
        }
        if (ax < 0.3f && y > -1.06f && y <= -0.77f && z > 0.3f) {
            return Regions.CHIN;
        }
        if (y > 0.12f && y < 0.78f && z > 0.25f) {
            return Regions.FOREHEAD;
        }
        if (z > 0.15f && y > -0.6f && y <= 0.12f) {
            return left ? Regions.CHEEK_L : Regions.CHEEK_R;
        }
        if (y < -0.45f) {
            return Regions.JAW;
        }
        return Regions.SCALP;
    }

    private int bodyRegion(int bone, float x, float y, float z) {
        boolean left = x > 0f;
        float[] j = sk.joint;
        switch (bone) {
            case DotBody.CHEST:
                return z > 0f ? Regions.CHEST : Regions.BACK;
            case DotBody.SPINE:
                return z > 0.05f ? Regions.BELLY : Regions.BACK;
            case DotBody.PELVIS:
                return Regions.HIPS;
            case DotBody.NECK:
                return Regions.NECK;
            case DotBody.ARM_L:
            case DotBody.ARM_R:
                return dist(j, bone, x, y, z) < 0.24f ? (left ? Regions.SHOULDER_L : Regions.SHOULDER_R)
                        : (left ? Regions.UPPER_ARM_L : Regions.UPPER_ARM_R);
            case DotBody.FORE_L:
            case DotBody.FORE_R:
                return left ? Regions.FOREARM_L : Regions.FOREARM_R;
            case DotBody.THIGH_L:
            case DotBody.THIGH_R:
                return dist(j, bone + 1, x, y, z) < 0.17f ? (left ? Regions.KNEE_L : Regions.KNEE_R)
                        : (left ? Regions.THIGH_L : Regions.THIGH_R);
            case DotBody.SHIN_L:
            case DotBody.SHIN_R:
                return dist(j, bone, x, y, z) < 0.17f ? (left ? Regions.KNEE_L : Regions.KNEE_R)
                        : (left ? Regions.SHIN_L : Regions.SHIN_R);
            default:
                return left ? Regions.FOOT_L : Regions.FOOT_R;
        }
    }

    /**
     * Linear-blend skin: the vertex's bone, blending half-way into the parent
     * at its own joint, or half-way into a child near the child's joint (so
     * the torso side of a shoulder or hip follows the limb as much as the limb
     * side does, and the skin does not tear when the limb moves).
     */
    private void skin(TriMesh m, int i, int bone, float x, float y, float z, float radius) {
        m.bone0[i] = (byte) bone;
        m.bone1[i] = (byte) bone;
        m.weight0[i] = 1f;
        int parent = Skeleton.PARENT[bone];
        float best = 0f;
        if (parent >= 0) {
            float r = isLimbRoot(bone) ? rootRadius(bone) : radius;
            float t = Math.min(1f, dist(sk.joint, bone, x, y, z) / r);
            best = 0.5f * (1f - t * t * (3f - 2f * t));
            m.bone1[i] = (byte) parent;
        }
        for (int c = 0; c < DotBody.BONES; c++) {
            if (Skeleton.PARENT[c] != bone || !isLimbRoot(c)) {
                continue;
            }
            float t = Math.min(1f, dist(sk.joint, c, x, y, z) / rootRadius(c));
            float w = 0.5f * (1f - t * t * (3f - 2f * t));
            if (c == DotBody.ARM_L || c == DotBody.ARM_R) {
                w *= smooth((Math.abs(x) - 0.4f * sk.sh) / 0.15f); // the base of the neck stays with the chest
            }
            if (w > best) {
                best = w;
                m.bone1[i] = (byte) c;
            }
        }
        m.weight0[i] = 1f - best;
    }

    /** Weight-diffusion passes over the body mesh (more passes, softer joints). */
    /** The radius over which the body's skin weights are averaged (the hands keep theirs). */
    private static final float WEIGHT_BLUR = 0.09f;

    /**
     * Averages each body vertex's skin weights with every body vertex within
     * {@code radius} (a smooth falloff), then keeps the two strongest bones.
     * Unlike diffusion over edges this is the same whatever the triangle
     * sizes, so a weight never jumps across a run of tiny triangles. Hands and
     * fingers are left alone so neighbouring fingers do not blend.
     */
    static void blurWeights(TriMesh m, float radius) {
        int n = m.vertexCount(), bones = Skeleton.COUNT;
        float[] w = new float[n * bones];
        boolean[] hand = new boolean[n];
        for (int v = 0; v < n; v++) {
            w[v * bones + (m.bone0[v] & 0xff)] += m.weight0[v];
            w[v * bones + (m.bone1[v] & 0xff)] += 1f - m.weight0[v];
            int b = m.bone0[v] & 0xff;
            hand[v] = b == DotBody.HAND_L || b == DotBody.HAND_R || Skeleton.isFinger(b);
        }
        // neighbours along the surface
        List<List<Integer>> nb = new ArrayList<List<Integer>>();
        for (int v = 0; v < n; v++) {
            nb.add(new ArrayList<Integer>());
        }
        for (int t = 0; t < m.tri.length; t += 3) {
            for (int k = 0; k < 3; k++) {
                int a = m.tri[t + k], c = m.tri[t + (k + 1) % 3];
                nb.get(a).add(c);
                nb.get(c).add(a);
            }
        }
        float[] dist = new float[n];
        java.util.Arrays.fill(dist, Float.MAX_VALUE);
        float[] acc = new float[bones];
        List<Integer> reached = new ArrayList<Integer>();
        java.util.PriorityQueue<float[]> queue = new java.util.PriorityQueue<float[]>((p, q) -> Float.compare(p[0], q[0]));
        byte[] b0s = new byte[n], b1s = new byte[n];
        float[] w0s = new float[n];
        for (int v = 0; v < n; v++) {
            b0s[v] = m.bone0[v];
            b1s[v] = m.bone1[v];
            w0s[v] = m.weight0[v];
            if (hand[v]) {
                continue;
            }
            // the body vertices within radius along the surface (Dijkstra over edge lengths)
            java.util.Arrays.fill(acc, 0f);
            reached.clear();
            queue.clear();
            dist[v] = 0f;
            queue.add(new float[]{0f, v});
            while (!queue.isEmpty()) {
                float[] e = queue.poll();
                int u = (int) e[1];
                if (e[0] > dist[u]) {
                    continue;
                }
                reached.add(u);
                for (int x : nb.get(u)) {
                    if (hand[x]) {
                        continue;
                    }
                    float dx = m.pos[x * 3] - m.pos[u * 3], dy = m.pos[x * 3 + 1] - m.pos[u * 3 + 1], dz = m.pos[x * 3 + 2] - m.pos[u * 3 + 2];
                    float d = dist[u] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < radius && d < dist[x]) {
                        dist[x] = d;
                        queue.add(new float[]{d, x});
                    }
                }
            }
            for (int u : reached) {
                float r = dist[u] / radius;
                float f = (1f - r * r) * (1f - r * r);
                for (int b = 0; b < bones; b++) {
                    acc[b] += f * w[u * bones + b];
                }
                dist[u] = Float.MAX_VALUE;
            }
            int b0 = 0, b1 = 0;
            float w0 = -1f, w1 = -1f;
            for (int b = 0; b < bones; b++) {
                if (acc[b] > w0) {
                    b1 = b0;
                    w1 = w0;
                    b0 = b;
                    w0 = acc[b];
                } else if (acc[b] > w1) {
                    b1 = b;
                    w1 = acc[b];
                }
            }
            b0s[v] = (byte) b0;
            b1s[v] = (byte) b1;
            w0s[v] = w0 + w1 > 1e-9f ? w0 / (w0 + Math.max(0f, w1)) : 1f;
        }
        m.bone0 = b0s;
        m.bone1 = b1s;
        m.weight0 = w0s;
    }

    /**
     * Diffuses skin weights over the mesh (each pass mixes every vertex half
     * with the average of its neighbours), then keeps each vertex's two
     * strongest bones. Weights then change gradually across the surface, so
     * where two bones meet (crotch, armpit, the web between fingers) the skin
     * stretches over a band instead of tearing along one row of edges.
     */
    static void smoothWeights(TriMesh m, int passes) {
        int n = m.vertexCount();
        if (passes <= 0 || n == 0) {
            return;
        }
        int bones = Skeleton.COUNT;
        float[] w = new float[n * bones], next = new float[n * bones];
        for (int v = 0; v < n; v++) {
            w[v * bones + (m.bone0[v] & 0xff)] += m.weight0[v];
            w[v * bones + (m.bone1[v] & 0xff)] += 1f - m.weight0[v];
        }
        // neighbours from the triangles (each vertex lists every vertex it shares an edge with)
        int[] count = new int[n + 1];
        int[] tri = m.tri;
        for (int t = 0; t < tri.length; t++) {
            count[tri[t] + 1] += 2;
        }
        for (int v = 0; v < n; v++) {
            count[v + 1] += count[v];
        }
        int[] nb = new int[count[n]], fill = count.clone();
        for (int t = 0; t < tri.length; t += 3) {
            for (int k = 0; k < 3; k++) {
                int a = tri[t + k];
                nb[fill[a]++] = tri[t + (k + 1) % 3];
                nb[fill[a]++] = tri[t + (k + 2) % 3];
            }
        }
        for (int p = 0; p < passes; p++) {
            for (int v = 0; v < n; v++) {
                int deg = count[v + 1] - count[v];
                for (int b = 0; b < bones; b++) {
                    float sum = 0f;
                    for (int k = count[v]; k < count[v + 1]; k++) {
                        sum += w[nb[k] * bones + b];
                    }
                    next[v * bones + b] = deg == 0 ? w[v * bones + b] : 0.5f * w[v * bones + b] + 0.5f * sum / deg;
                }
            }
            float[] tmp = w;
            w = next;
            next = tmp;
        }
        for (int v = 0; v < n; v++) {
            int b0 = 0, b1 = 0;
            float w0 = -1f, w1 = -1f;
            for (int b = 0; b < bones; b++) {
                float x = w[v * bones + b];
                if (x > w0) {
                    b1 = b0;
                    w1 = w0;
                    b0 = b;
                    w0 = x;
                } else if (x > w1) {
                    b1 = b;
                    w1 = x;
                }
            }
            m.bone0[v] = (byte) b0;
            m.bone1[v] = (byte) b1;
            m.weight0[v] = w0 + w1 > 1e-6f ? w0 / (w0 + w1) : 1f;
        }
    }

    /** How far around a shoulder or hip the skin blends between torso and limb. */
    private static final float ROOT_RADIUS = 0.42f;

    /** Shoulders blend over a radius in proportion to the shoulder width, so a narrow frame keeps the neck out of it. */
    private float rootRadius(int bone) {
        boolean arm = bone == DotBody.ARM_L || bone == DotBody.ARM_R;
        return arm ? ROOT_RADIUS * sk.sh / 0.51f : ROOT_RADIUS;
    }

    private static boolean isLimbRoot(int bone) {
        return bone == DotBody.ARM_L || bone == DotBody.ARM_R || bone == DotBody.THIGH_L || bone == DotBody.THIGH_R;
    }

    private void landmarks(HumanAsset a) {
        String[] names = {"crown", "nose tip", "chin", "eye left", "eye right", "mouth left", "mouth right",
                "ear left", "ear right", "neck front", "chest", "navel", "left index tip", "right index tip",
                "left knee", "right knee", "left toe", "right toe"};
        float[][] at = {toBody(0f, 1.02f, 0f), toBody(0f, -0.34f, 1.08f), toBody(0f, -1.0f, 0.5f),
                toBody(Anatomy.EYE_X, Anatomy.EYE_Y, Anatomy.EYE_Z + Anatomy.EYE_R), toBody(-Anatomy.EYE_X, Anatomy.EYE_Y, Anatomy.EYE_Z + Anatomy.EYE_R),
                toBody(0.21f, Anatomy.MOUTH_Y, 0.85f), toBody(-0.21f, Anatomy.MOUTH_Y, 0.85f),
                toBody(0.86f, 0f, -0.08f), toBody(-0.86f, 0f, -0.08f), {0f, 0.05f, 0.15f}, {0f, -0.4f, 0.3f},
                {0f, -1.05f, 0.25f},
                sk.arm(1, Skeleton.KNUCKLE + Skeleton.FINGER_LEN[1], -0.02f, Skeleton.FINGER_Z[1] * Skeleton.SPREAD_TIP),
                sk.arm(-1, Skeleton.KNUCKLE + Skeleton.FINGER_LEN[1], -0.02f, Skeleton.FINGER_Z[1] * Skeleton.SPREAD_TIP),
                {sk.hx + 0.02f, -2.86f, 0.18f}, {-sk.hx - 0.02f, -2.86f, 0.18f},
                {sk.hx + 0.05f, -4.16f, 0.38f}, {-sk.hx - 0.05f, -4.16f, 0.38f}};
        a.landmarkNames = names;
        a.landmarkVertex = new int[names.length];
        TriMesh m = a.mesh;
        int solids = m.vertexCount();
        // only real surface vertices (not feature lines) are landmarks
        for (int i = 0; i < a.lines.length; i++) {
            solids = Math.min(solids, a.lines[i]);
        }
        for (int k = 0; k < names.length; k++) {
            int best = 0;
            float bd = Float.MAX_VALUE;
            for (int v = 0; v < solids; v++) {
                float dx = m.pos[v * 3] - at[k][0], dy = m.pos[v * 3 + 1] - at[k][1], dz = m.pos[v * 3 + 2] - at[k][2];
                if (at[k][0] == 0f) {
                    dx *= 4f; // midline landmarks (nose tip, chin, crown…) stay on the centre line
                }
                float d = dx * dx + dy * dy + dz * dz;
                if (d < bd) {
                    bd = d;
                    best = v;
                }
            }
            a.landmarkVertex[k] = best;
        }
    }

    // ------------------------------------------------------------ helpers

    /** Where detail matters on the head: the face front, most of all eyes, mouth and nose. */
    static float headImportance(float x, float y, float z) {
        float face = z > 0.25f && y > -1.05f && y < 0.45f ? 1f : 0f;
        float ax = Math.abs(x);
        float eye = (float) Math.exp(-((ax - 0.31f) * (ax - 0.31f) / 0.02f + (y - 0.02f) * (y - 0.02f) / 0.012f));
        float mouth = (float) Math.exp(-(x * x / 0.05f + (y + 0.61f) * (y + 0.61f) / 0.01f));
        float nose = (float) Math.exp(-(x * x / 0.02f + (y + 0.25f) * (y + 0.25f) / 0.05f));
        float ear = ax > 0.66f && Math.abs(y) < 0.3f ? 1.5f : 0f;
        return 0.8f + face * (2f + 5f * eye + 5f * mouth + 3f * nose) + ear;
    }

    /** Even triangles over the body; hands and fingers keep more, so they keep their shape. */
    private float bodyImportance(float x, float y, float z) {
        return sk.local(x >= 0f ? 1 : -1, x, y, z)[0] > Skeleton.WRIST - 0.04f && y > -1.6f ? 4f : 1f;
    }

    private static float dist(float[] j, int b, float x, float y, float z) {
        float dx = x - j[b * 3], dy = y - j[b * 3 + 1], dz = z - j[b * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static float[] toBody(float x, float y, float z) {
        return new float[]{x * Anatomy.HEAD_SCALE, y * Anatomy.HEAD_SCALE + Anatomy.HEAD_Y, z * Anatomy.HEAD_SCALE};
    }

    private static Look bald(Look l) {
        Look b = l.copy();
        b.hair = 0f;
        return b;
    }

    private static void flip(TriMesh m) {
        for (int t = 0; t < m.tri.length; t += 3) {
            int s = m.tri[t + 1];
            m.tri[t + 1] = m.tri[t + 2];
            m.tri[t + 2] = s;
        }
    }

    private static void require(TriMesh m, String what) {
        MeshValidator.Report r = MeshValidator.check(m, true, 0, 0);
        if (!r.ok()) {
            throw new IllegalStateException(what + " mesh failed validation: " + r);
        }
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float noise(int seed) {
        return ai.dvine.face.nano.Swarm.noise(seed);
    }
}
