package ai.dvine.human.bake;

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
        public int headTris = 4000;
        public int bodyTris = 6000;
        public int handTris = 2800;
    }

    private final boolean female;
    private final Look look;
    private final Budget budget;
    private final Skeleton sk;
    /** The head's frame, eyeball centres and radius (from the standard body). */
    private HeadFrame head;
    private float[] eyeL, eyeR;
    private float eyeRadius;
    /** How far forward the chin's point is (head units). */
    private float chinFront;
    /** The skull's half-width, crown height and depth behind the head centre (head units, from the standard head). */
    private float skullHalfWidth, skullTop, skullBack;

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
        StandardBody.Result std = StandardBody.build(female, sk);
        head = HeadFrame.fit(std.eyeL, std.eyeR, std.lipY, std.chinY);
        eyeL = std.eyeL;
        eyeR = std.eyeR;
        eyeRadius = std.eyeRadius;
        chinFront = head.toHead(0f, std.chinY, std.chinZ)[2];
        sculptFace(std);
        measureSkull(std);
        TriMesh body = bodyPiece(std);
        TriMesh eyeMeshL = eye(1);
        TriMesh eyeMeshR = eye(-1);
        TriMesh teeth = teeth();
        TriMesh solid = TriMesh.merge(body, eyeMeshL, eyeMeshR, teeth);
        int solidCount = solid.vertexCount();
        int eyeStart = body.vertexCount();
        int teethStart = eyeStart + eyeMeshL.vertexCount() + eyeMeshR.vertexCount();

        // iris rings and hair are line-only vertices, appended after the solids
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
        a.eyeRange = new int[]{eyeStart, eyeMeshL.vertexCount(), eyeStart + eyeMeshL.vertexCount(), eyeMeshR.vertexCount()};
        a.eyeCenter = new float[]{eyeL[0], eyeL[1], eyeL[2], eyeR[0], eyeR[1], eyeR[2]};
        a.irisRange = new int[]{irisStart, irisL, irisStart + irisL, irisR};
        boolean[] face = new boolean[m.vertexCount()];
        for (int v = 0; v < m.vertexCount(); v++) {
            boolean eyeball = v >= eyeStart && v < teethStart;
            face[v] = !eyeball && (v >= body.vertexCount() || (m.bone0[v] & 0xff) == DotBody.HEAD);
        }
        float[] jaw = new float[m.vertexCount()];
        java.util.Arrays.fill(jaw, -1f);
        for (int v = 0; v < body.vertexCount(); v++) {
            jaw[v] = std.jaw[body.origin[v]];
        }
        a.morphs = FaceShapes.build(m, face, head, solidCount, teethStart, teeth.vertexCount(), irisStart, irisStart + irisL + irisR, jaw);
        landmarks(a);
        InnerAnatomy.Result inside = InnerAnatomy.build(sk, head);
        a.inner = inside.mesh;
        a.innerNames = new String[inside.parts.size()];
        a.innerLayer = new byte[inside.parts.size()];
        for (InnerAnatomy.Part part : inside.parts) {
            a.innerNames[part.id] = part.name;
            a.innerLayer[part.id] = (byte) part.layer;
        }
        // everything above is on the blockout skeleton; fit it to the body-dimension profile (the face is left as it is)
        BodyFit fit = BodyFit.fit(BodyProfile.standard(), sk, m, solidCount, m.pos[a.landmark("chin") * 3 + 1]);
        fit.apply(m);
        fit.applyInner(a.inner);
        a.joints = fit.joints(sk);
        // the head, its eyes, face shapes, hair and frame all scale with it
        float k = fit.headScale;
        float[] d = new float[3];
        for (int e = 0; e < 2; e++) {
            fit.offset(DotBody.HEAD, a.eyeCenter[e * 3], a.eyeCenter[e * 3 + 1], a.eyeCenter[e * 3 + 2], d);
            for (int c = 0; c < 3; c++) {
                a.eyeCenter[e * 3 + c] += d[c];
            }
        }
        for (HumanAsset.Morph mo : a.morphs) {
            for (int i = 0; i < mo.delta.length; i++) {
                mo.delta[i] *= k;
            }
        }
        for (int i = 0; i < a.sway.length; i++) {
            a.sway[i] *= k;
        }
        a.headFrame = head.scaled(k, 0f, fit.pivotY, fit.pivotZ).toArray();
        return a;
    }

    // ------------------------------------------------------------ pieces

    /** Shapes the standard head into the persona's own face (see {@link FaceSculpt}). */
    private void sculptFace(StandardBody.Result std) {
        FaceSculpt sculpt = new FaceSculpt(female, look);
        float[] p = std.mesh.pos;
        for (int v = 0; v < std.mesh.vertexCount(); v++) {
            float w = (std.bone[v] == DotBody.HEAD ? std.weight0[v] : 0f) + (std.bone1[v] == DotBody.HEAD ? 1f - std.weight0[v] : 0f);
            if (w <= 0f) {
                continue;
            }
            float[] h = head.toHead(p[v * 3], p[v * 3 + 1], p[v * 3 + 2]);
            float[] q = sculpt.apply(h[0], h[1], h[2], w);
            float[] b = head.toBody(q[0], q[1], q[2]);
            System.arraycopy(b, 0, p, v * 3, 3);
        }
        std.mesh.computeNormals();
    }

    /** The skull's size in head units, from the head's own vertices above the eyes. */
    private void measureSkull(StandardBody.Result std) {
        skullHalfWidth = 0f;
        skullTop = 0f;
        skullBack = 0f;
        for (int v = 0; v < std.mesh.vertexCount(); v++) {
            if (std.bone[v] != DotBody.HEAD) {
                continue;
            }
            float[] h = head.toHead(std.mesh.pos[v * 3], std.mesh.pos[v * 3 + 1], std.mesh.pos[v * 3 + 2]);
            if (h[1] > 0.1f) {
                skullHalfWidth = Math.max(skullHalfWidth, Math.abs(h[0]) * (Math.abs(h[0]) > 0.66f && h[1] < 0.3f ? 0f : 1f));
                skullTop = Math.max(skullTop, h[1]);
                skullBack = Math.max(skullBack, -0.16f - h[2]);
            }
        }
    }

    /**
     * The standard body, head and all (see {@link StandardBody}), on the
     * blockout skeleton, simplified with more triangles on the face and the
     * hands, labelled by body part and skinned with MakeHuman's own weights.
     */
    private TriMesh bodyPiece(StandardBody.Result std) {
        TriMesh raw = std.mesh;
        if (MeshValidator.volume(raw.pos, raw.tri) < 0) {
            flip(raw);
        }
        final int[] rawBone = std.bone;
        // the hand's parts (palm, and each finger segment) are simplified each on its own, so fingers stay apart
        int[] group = new int[raw.vertexCount()];
        for (int v = 0; v < group.length; v++) {
            int b = rawBone[v];
            group[v] = b == DotBody.HAND_L || b == DotBody.HAND_R || Skeleton.isFinger(b) ? b : -1;
        }
        // the lips stay parted: upper and lower lip are never merged into each other
        for (int v = 0; v < group.length; v++) {
            if (rawBone[v] != DotBody.HEAD) {
                continue;
            }
            float[] h = head.toHead(raw.pos[v * 3], raw.pos[v * 3 + 1], raw.pos[v * 3 + 2]);
            if (Math.abs(h[0]) < 0.4f && Math.abs(h[1] - Anatomy.MOUTH_Y) < 0.15f && h[2] > 0.4f) {
                group[v] = h[1] >= Anatomy.MOUTH_Y ? -1000 : -1001;
            }
        }
        // the webs between fingers stay exactly as they are (a vertex touching another finger is never merged),
        // and remember which finger each web point touches
        final int[] webOther = new int[raw.vertexCount()], span = new int[raw.vertexCount()];
        java.util.Arrays.fill(webOther, -1);
        java.util.Arrays.fill(span, -1);
        for (int t = 0; t < raw.tri.length; t += 3) {
            for (int k = 0; k < 3; k++) {
                int a = raw.tri[t + k], c = raw.tri[t + (k + 1) % 3];
                // skin spanning two joints (the palm straight to a finger's second segment) leans on the joint between
                for (int[] pr : new int[][]{{a, c}, {c, a}}) {
                    int pb = rawBone[pr[0]], qb = rawBone[pr[1]];
                    if ((pb == DotBody.HAND_L || pb == DotBody.HAND_R) && Skeleton.isFinger(qb) && (qb - Skeleton.FINGER_L) % 2 == 1) {
                        span[pr[0]] = qb - 1;
                        span[pr[1]] = qb - 1;
                    }
                }
                if (fingerOf(rawBone[a]) >= 0 && fingerOf(rawBone[c]) >= 0 && fingerOf(rawBone[a]) != fingerOf(rawBone[c])) {
                    group[a] = -2 - a;
                    group[c] = -2 - c;
                    webOther[a] = Skeleton.FINGER_L + fingerOf(rawBone[c]) * 2;
                    webOther[c] = Skeleton.FINGER_L + fingerOf(rawBone[a]) * 2;
                }
            }
        }
        TriMesh m = Decimator.simplify(raw, budget.headTris + budget.bodyTris + 2 * budget.handTris, this::bodyImportance, 0.0015, group);
        require(m, "body");
        int n = m.vertexCount();
        m.region = new byte[n];
        m.bone0 = new byte[n];
        m.bone1 = new byte[n];
        m.weight0 = new float[n];
        int[] nearest = m.origin; // each kept vertex keeps its own labels
        for (int i = 0; i < n; i++) {
            float x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
            int src = nearest[i];
            int bone = rawBone[src];
            float[] hu = head.toHead(x, y, z);
            if (bone == DotBody.HEAD || bone == DotBody.NECK && hu[1] > -1.05f - 0.2f * Math.max(0f, hu[2])) {
                // the face goes by where a point is on the head, whichever bone carries it
                m.region[i] = (byte) headRegion(hu[0], hu[1], hu[2]);
            } else if (bone == DotBody.HAND_L || bone == DotBody.HAND_R || Skeleton.isFinger(bone)) {
                m.region[i] = (byte) handRegion(bone, x);
            } else {
                m.region[i] = (byte) bodyRegion(bone, x, y, z);
            }
            if (span[src] >= 0) {
                m.bone0[i] = (byte) bone;
                m.bone1[i] = (byte) span[src];
                m.weight0[i] = 0.5f;
                continue;
            }
            if (webOther[src] >= 0) {
                // a web between two fingers follows both of them equally
                m.bone0[i] = (byte) (Skeleton.FINGER_L + fingerOf(bone) * 2);
                m.bone1[i] = (byte) webOther[src];
                m.weight0[i] = 0.5f;
                continue;
            }
            if (bone == DotBody.HAND_L || bone == DotBody.HAND_R || Skeleton.isFinger(bone)) {
                handWeights(m, i, bone, x, y, z);
                continue;
            }
            if (bone == DotBody.PELVIS || bone == DotBody.THIGH_L || bone == DotBody.THIGH_R) {
                // hips: a wide blend around each hip joint, as our rig has one hip bone where MakeHuman has several
                skin(m, i, bone, x, y, z, 0.2f);
                continue;
            }
            // MakeHuman's own skin weights, made for this body
            m.bone0[i] = (byte) bone;
            m.bone1[i] = (byte) std.bone1[src];
            m.weight0[i] = std.weight0[src];
        }
        blurWeights(m, WEIGHT_BLUR);
        settleTorso(m);
        m.computeNormals();
        return m;
    }

    /**
     * Two places where neighbouring skin follows different limbs: the crotch
     * (the left and right thighs meet on the midline) and the base of the neck
     * (the arms reach up the trapezius). The middle of the crotch stays with
     * the pelvis, and an arm's pull fades out before the neck, so neither seam
     * is pulled two ways when the legs spread or the arms go up.
     */
    private void settleTorso(TriMesh m) {
        float[] j = sk.joint;
        float hipY = j[DotBody.THIGH_L * 3 + 1], pelvisY = j[DotBody.PELVIS * 3 + 1];
        for (int v = 0; v < m.vertexCount(); v++) {
            float x = m.pos[v * 3], y = m.pos[v * 3 + 1];
            float ax = Math.abs(x);
            for (int pass = 0; pass < 2; pass++) {
                int b = pass == 0 ? m.bone0[v] & 0xff : m.bone1[v] & 0xff;
                int other = pass == 0 ? m.bone1[v] & 0xff : m.bone0[v] & 0xff;
                float wb = pass == 0 ? m.weight0[v] : 1f - m.weight0[v];
                float keep;
                if ((b == DotBody.THIGH_L || b == DotBody.THIGH_R) && other == DotBody.PELVIS && y < pelvisY) {
                    // the crotch band: the thigh's share fades toward the midline, and back in below the crotch
                    float band = smooth((y - (hipY - 0.4f)) / 0.15f);
                    keep = 1f - (1f - smooth(ax / (sk.hx * 0.75f))) * band * 0.6f;
                } else if ((b == DotBody.ARM_L || b == DotBody.ARM_R) && (other == DotBody.CHEST || other == DotBody.NECK)) {
                    keep = smooth((ax - 0.45f * sk.sh) / (0.35f * sk.sh));
                } else {
                    continue;
                }
                float moved = wb * (1f - keep);
                if (pass == 0) {
                    m.weight0[v] -= moved;
                } else {
                    m.weight0[v] += moved;
                }
            }
        }
    }

    /**
     * Hand weights: each finger segment rides its bone and blends half-way
     * into its parent at its own joint; the palm rides the hand and blends
     * into the forearm at the wrist. (Keeping only two of MakeHuman's
     * weights near the knuckles can tie a palm point to the wrong finger.)
     */
    private void handWeights(TriMesh m, int i, int bone, float x, float y, float z) {
        int side = x >= 0f ? 1 : -1;
        int base = side > 0 ? Skeleton.FINGER_L : Skeleton.FINGER_R;
        float[] j = sk.joint;
        if (bone == DotBody.HAND_L || bone == DotBody.HAND_R) {
            // the palm: into the forearm at the wrist, and into a finger at its knuckle (measured along that finger)
            float lx = sk.local(side, x, y, z)[0];
            float best = 0.5f * (1f - smooth((lx - Skeleton.WRIST) / 0.05f));
            int other = bone == DotBody.HAND_L ? DotBody.FORE_L : DotBody.FORE_R;
            for (int f = 0; f < 5; f++) {
                int first = base + f * 2;
                if (dist(j, first, x, y, z) > 0.08f) {
                    continue;
                }
                float w = 0.4f * (1f - smooth(Math.abs(along(first, x, y, z)) / KNUCKLE_BLEND));
                if (w > best) {
                    best = w;
                    other = first;
                }
            }
            m.bone0[i] = (byte) bone;
            m.bone1[i] = (byte) other;
            m.weight0[i] = 1f - best;
            return;
        }
        // a finger: the segment is where the point lies along it; blend evenly across both of its joints
        int first = base + (bone - base) / 2 * 2;
        float len = dist(j, first, j[(first + 1) * 3], j[(first + 1) * 3 + 1], j[(first + 1) * 3 + 2]);
        float a = along(first, x, y, z);
        int hand = side > 0 ? DotBody.HAND_L : DotBody.HAND_R;
        float atKnuckle = 0.5f * (1f - smooth(Math.abs(a) / KNUCKLE_BLEND));
        float atMiddle = 0.5f * (1f - smooth(Math.abs(a - len) / FINGER_BLEND));
        // the thumb sits at an angle to the palm: its own labels say which segment a point is on
        boolean second = first == base ? bone == first + 1 : a > len;
        if (second) {
            m.bone0[i] = (byte) (first + 1);
            m.bone1[i] = (byte) first;
            m.weight0[i] = 1f - atMiddle;
        } else {
            boolean knuckleSide = atKnuckle > atMiddle;
            m.bone0[i] = (byte) first;
            m.bone1[i] = (byte) (knuckleSide ? hand : first + 1);
            m.weight0[i] = 1f - Math.max(atKnuckle, atMiddle);
        }
    }

    /** How far a point lies along a finger's first segment from its first joint (along that segment's direction). */
    private float along(int first, float x, float y, float z) {
        float[] j = sk.joint;
        float ux = j[(first + 1) * 3] - j[first * 3], uy = j[(first + 1) * 3 + 1] - j[first * 3 + 1], uz = j[(first + 1) * 3 + 2] - j[first * 3 + 2];
        float len = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        return ((x - j[first * 3]) * ux + (y - j[first * 3 + 1]) * uy + (z - j[first * 3 + 2]) * uz) / len;
    }

    /** How far either side of a knuckle the skin blends between palm and finger: it covers the webs between fingers. */
    private static final float KNUCKLE_BLEND = 0.07f;

    /** How far either side of a finger's middle joint the skin blends between its two segments. */
    private static final float FINGER_BLEND = 0.04f;

    /** Which finger (0..9, both hands) a bone belongs to, or −1. */
    private static int fingerOf(int bone) {
        return Skeleton.isFinger(bone) ? (bone - Skeleton.FINGER_L) / 2 : -1;
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

    /** How far forward the front teeth sit (head units): behind the closed lips. */
    private static final float TEETH_Z = 0.42f;

    /** A low-poly eyeball (an icosphere) in its socket. */
    private TriMesh eye(int side) {
        TriMesh m = Shapes.icosphere(1);
        float r = eyeRadius;
        float[] c = side > 0 ? eyeL : eyeR;
        int n = m.vertexCount();
        m.region = new byte[n];
        m.bone0 = new byte[n];
        m.bone1 = new byte[n];
        m.weight0 = new float[n];
        for (int i = 0; i < n; i++) {
            float[] b = {c[0] + m.pos[i * 3] * r, c[1] + m.pos[i * 3 + 1] * r, c[2] + m.pos[i * 3 + 2] * r};
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
                float cx = (float) Math.sin(a) * 0.19f * look.lips, cz = TEETH_Z - (1f - (float) Math.cos(a)) * 0.22f;
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

    /** A ring around the iris and one around the pupil, on the front of each eyeball (head units → body). */
    private int irisRing(int side) {
        int before = linePos.size();
        float r = eyeRadius;
        float[] c = side > 0 ? eyeL : eyeR;
        int region = side > 0 ? Regions.EYE_L : Regions.EYE_R;
        for (float ang : new float[]{24f, 10f}) {
            List<float[]> ring = new ArrayList<float[]>();
            double a = Math.toRadians(ang);
            for (int i = 0; i <= 16; i++) {
                double t = i * Math.PI * 2 / 16;
                float x = (float) (Math.sin(a) * Math.cos(t)), y = (float) (Math.sin(a) * Math.sin(t)), z = (float) Math.cos(a);
                ring.add(new float[]{c[0] + x * r * 1.01f, c[1] + y * r * 1.01f, c[2] + z * r * 1.01f});
            }
            addStrip(ring, region, 0f, true);
        }
        return linePos.size() - before;
    }

    /** Ivie's hair: strands sweeping back from the hairline over the head and streaming out behind it. */
    private void hair() {
        float grow = 0.05f + 0.03f * look.hair;
        // the shell the strands lie on: just outside the skull's own extent (head units)
        float rx = skullHalfWidth + grow, ry = skullTop - 0.26f + grow, rz = skullBack + grow * 1.6f;
        int strands = 90;
        float flow = 1.3f + 0.7f * look.hair;
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
            // long hair streaming back and out, in waves, as if in a breeze
            for (int i = 1; i <= 16; i++) {
                float t = i / 16f;
                float wave = 0.07f * t * (float) Math.sin(t * 7f + k * 0.9f);
                float x = lx * (1f + 0.5f * t) + 0.08f * t * noise(k * 7) + wave * 0.5f;
                float droop = 0.35f + 0.3f * noise(k * 7 + 2) + 0.25f * Math.abs(u);
                float y = ly - droop * len * t * t + 0.12f * t * noise(k * 7 + 1) + wave;
                float z = lz - len * t * (1f - 0.12f * t);
                pts.add(toBody(x, y, z));
                sw.add(t * len);
            }
            int first = linePos.size();
            addStrip(pts, Regions.HAIR, 0f, false);
            for (int i = 0; i < sw.size(); i++) {
                lineSway.set(first + i, sw.get(i) * head.sy);
            }
        }
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
        if (ax < 0.33f && y > -1.12f && y <= -0.77f && z > 0.2f) {
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
    private static final float WEIGHT_BLUR = 0.24f;

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
        float[][] at = {toBody(0f, 1.02f, 0f), toBody(0f, -0.34f, 1.08f), toBody(0f, HeadFrame.CHIN_Y + 0.12f, chinFront),
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
                    dx *= 12f; // midline landmarks (nose tip, chin, crown…) stay on the centre line
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

    /** Even triangles over the body; the face and the hands keep more, so they keep their shape. */
    private float bodyImportance(float x, float y, float z) {
        float[] h = head.toHead(x, y, z);
        if (h[1] > -1.15f && Math.abs(h[0]) < 1.3f) {
            return headImportance(h[0], h[1], h[2]);
        }
        return sk.local(x >= 0f ? 1 : -1, x, y, z)[0] > Skeleton.WRIST - 0.04f && y > -1.6f ? 30f : 1f;
    }

    private static float dist(float[] j, int b, float x, float y, float z) {
        float dx = x - j[b * 3], dy = y - j[b * 3 + 1], dz = z - j[b * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float[] toBody(float x, float y, float z) {
        return head.toBody(x, y, z);
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
