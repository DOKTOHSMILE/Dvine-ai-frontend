package ai.dvine.human.wrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ai.dvine.brain.MiniJson;
import ai.dvine.face.nano.DotBody;
import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;
import ai.dvine.human.mesh.TriMesh;
import ai.dvine.human.rig.Skeleton;

/**
 * Shapes a body to its character sheet, the way an artist moves a mesh's vertices onto a reference picture: row by
 * row up the body, each part (head, torso, each arm, each leg) is widened or narrowed and moved so its outline in the
 * front view matches the sheet's front outline, and deepened or flattened so its outline in the side view matches
 * the profile. The picture is never distorted; the mesh is. The joints move with the parts, so the rig still fits.
 * Rows where two parts touch on the sheet (an arm against the body, the thighs at the hips) tell nothing about
 * either, and take the shape of the rows around them. Done once at start-up, on the asset, before it is used.
 * Pure Java.
 */
public final class SheetFit {
    private SheetFit() {
    }

    static final int HEAD = 0, TORSO = 1, ARM_L = 2, ARM_R = 3, LEG_L = 4, LEG_R = 5, PARTS = 6;

    /** The sheet's outlines (looks/*_outline.json): per view its runs of figure per pixel row. */
    public static final class Outline {
        final Map<String, Object> views;

        Outline(Map<String, Object> views) {
            this.views = views;
        }

        public static Outline parse(String json) {
            return new Outline(MiniJson.obj(MiniJson.parse(json)));
        }

        /** The runs [start, end] on sheet row {@code y} of a view (the nearest traced row), or none. */
        List<float[]> runs(String view, int y) {
            Map<String, Object> rows = MiniJson.obj(MiniJson.obj(views.get(view)).get("rows"));
            List<float[]> out = new ArrayList<float[]>();
            for (int d = 0; d <= 2; d++) {
                Object r = rows.get(String.valueOf(y - d));
                if (r == null) {
                    r = rows.get(String.valueOf(y + d));
                }
                if (r != null) {
                    for (Object seg : MiniJson.list(r)) {
                        List<Object> s = MiniJson.list(seg);
                        out.add(new float[]{(float) MiniJson.num(s.get(0), 0), (float) MiniJson.num(s.get(1), 0)});
                    }
                    return out;
                }
            }
            return out;
        }
    }

    /** What the fit did, for the self-check: per part the mean width change in the front view (1 = none). */
    public static final class Report {
        public final float[] widthScale = new float[PARTS], depthScale = new float[PARTS];
        public int moved;
    }

    /** Which part a vertex belongs to, by the bone that carries it most. */
    static int part(int bone) {
        if (bone == DotBody.HEAD) return HEAD;
        if (bone == DotBody.ARM_L || bone == DotBody.FORE_L || bone == DotBody.HAND_L
                || bone >= Skeleton.FINGER_L && bone < Skeleton.FINGER_L + 10) return ARM_L;
        if (bone == DotBody.ARM_R || bone == DotBody.FORE_R || bone == DotBody.HAND_R
                || bone >= Skeleton.FINGER_R && bone < Skeleton.FINGER_R + 10) return ARM_R;
        if (bone == DotBody.THIGH_L || bone == DotBody.SHIN_L || bone == DotBody.FOOT_L) return LEG_L;
        if (bone == DotBody.THIGH_R || bone == DotBody.SHIN_R || bone == DotBody.FOOT_R) return LEG_R;
        return TORSO;
    }

    /**
     * Shapes {@code a} (its rest mesh and joints) to the sheet. {@code keepHair}: the head above the eyes keeps its
     * own width (the sheet's outline there is hair, which the hair's own volume draws).
     */
    public static Report fit(HumanAsset a, SheetWrap cal, Outline outline, boolean keepHair) {
        HumanInstance h = new HumanInstance(a);
        h.update(0f);
        Map<String, float[]> marks = BodyMarks.of(h);
        TriMesh m = a.mesh;
        int n = m.vertexCount();
        int solid = h.solidCount();
        float[] p = Arrays.copyOf(h.pos, n * 3), q = p.clone();
        int[] partOf = new int[n];
        for (int v = 0; v < n; v++) {
            int b = m.weight0 == null || m.weight0[v] >= 0.5f || m.bone1 == null ? m.bone0[v] : m.bone1[v];
            partOf[v] = part(b & 0xFF);
        }
        Report rep = new Report();
        View front = new View(cal, outline, "front", marks, false), side = new View(cal, outline, "side", marks, true);
        if (front.ok) {
            fitAxis(front, p, q, partOf, solid, marks, 0, keepHair, rep.widthScale);
        }
        if (side.ok) {
            fitAxis(side, p, q, partOf, solid, marks, 2, keepHair, rep.depthScale);
        }
        // the change, carried to the rest mesh (the stance here is the rest pose, settled on the floor) and the joints
        for (int v = 0; v < n; v++) {
            for (int k = 0; k < 3; k++) {
                float d = q[v * 3 + k] - p[v * 3 + k];
                if (d != 0f) {
                    m.pos[v * 3 + k] += d;
                }
            }
            if (q[v * 3] != p[v * 3] || q[v * 3 + 2] != p[v * 3 + 2]) {
                rep.moved++;
            }
        }
        moveJoints(a, h, p, q, partOf);
        // the eyes turn about their new centres
        for (int e = 0; e < 2; e++) {
            int s0 = a.eyeRange[e * 2], c = a.eyeRange[e * 2 + 1];
            if (c <= 0) {
                continue;
            }
            float[] sum = new float[3];
            for (int v = s0; v < s0 + c; v++) {
                for (int k = 0; k < 3; k++) {
                    sum[k] += m.pos[v * 3 + k];
                }
            }
            float[] old = {a.eyeCenter[e * 3], a.eyeCenter[e * 3 + 1], a.eyeCenter[e * 3 + 2]};
            float[] centroidOld = new float[3];
            for (int v = s0; v < s0 + c; v++) {
                for (int k = 0; k < 3; k++) {
                    centroidOld[k] += m.pos[v * 3 + k] - (q[v * 3 + k] - p[v * 3 + k]);
                }
            }
            for (int k = 0; k < 3; k++) {
                a.eyeCenter[e * 3 + k] = old[k] + (sum[k] - centroidOld[k]) / c;
            }
        }
        TriMesh.normals(m.pos, m.tri, m.nor);
        return rep;
    }

    /** Each joint moves as the mesh around it moved (the mean change of the vertices nearest it on its part). */
    private static void moveJoints(HumanAsset a, HumanInstance h, float[] p, float[] q, int[] partOf) {
        float[] j = h.rig.joint;
        int bones = Math.min(a.joints.length / 3, j.length / 3);
        for (int b = 0; b < bones; b++) {
            int part = part(b);
            float jx = j[b * 3], jy = j[b * 3 + 1], jz = j[b * 3 + 2];
            // the vertices of its part in a thin slab at its height
            double sx = 0, sz = 0, w = 0;
            for (int v = 0; v < partOf.length; v++) {
                if (partOf[v] != part) {
                    continue;
                }
                float dy = Math.abs(p[v * 3 + 1] - jy);
                if (dy > 0.12f) {
                    continue;
                }
                float dx = p[v * 3] - jx, dz = p[v * 3 + 2] - jz;
                double d2 = dx * dx + dz * dz;
                if (d2 > 0.6) {
                    continue;
                }
                double k = 1.0 / (0.02 + d2);
                // a joint inside the limb moves as the limb's middle does: the mean of the change, weighted to the near side
                sx += k * (q[v * 3] - p[v * 3]);
                sz += k * (q[v * 3 + 2] - p[v * 3 + 2]);
                w += k;
            }
            if (w > 0) {
                a.joints[b * 3] += (float) (sx / w);
                a.joints[b * 3 + 2] += (float) (sz / w);
            }
        }
    }

    /** A view's mapping between the body and the sheet: heights by landmarks, a pixel scale, the body's middle. */
    static final class View {
        final String name;
        final boolean profile;
        boolean ok;
        final float[] bodyY, imgY; // matched heights, sorted by body height (descending)
        float scale; // sheet pixels per body unit
        final Map<String, Object> px;
        final Outline outline;

        View(SheetWrap cal, Outline outline, String view, Map<String, float[]> marks, boolean profile) {
            this.name = view;
            this.profile = profile;
            this.outline = outline;
            px = cal.view(view);
            List<float[]> pairs = new ArrayList<float[]>();
            for (Map.Entry<String, Object> e : px.entrySet()) {
                float[] b = marks.get(e.getKey());
                List<Object> pt = MiniJson.list(e.getValue());
                if (b != null && pt.size() >= 2 && !e.getKey().endsWith("_r")) {
                    pairs.add(new float[]{b[1], (float) MiniJson.num(pt.get(1), 0)});
                }
            }
            pairs.sort((x, y) -> Float.compare(y[0], x[0]));
            // keep heights strictly falling both ways (a monotone map)
            List<float[]> mono = new ArrayList<float[]>();
            for (float[] pr : pairs) {
                if (mono.isEmpty() || pr[0] < mono.get(mono.size() - 1)[0] - 1e-3f && pr[1] > mono.get(mono.size() - 1)[1]) {
                    mono.add(pr);
                }
            }
            bodyY = new float[mono.size()];
            imgY = new float[mono.size()];
            for (int i = 0; i < mono.size(); i++) {
                bodyY[i] = mono.get(i)[0];
                imgY[i] = mono.get(i)[1];
            }
            if (mono.size() >= 3) {
                scale = (imgY[imgY.length - 1] - imgY[0]) / (bodyY[0] - bodyY[bodyY.length - 1]);
                ok = scale > 0 && outline.views.containsKey(view);
            }
        }

        /** The sheet row for a body height. */
        float row(float y) {
            int n = bodyY.length;
            if (y >= bodyY[0]) {
                return imgY[0] - (y - bodyY[0]) * scale;
            }
            for (int i = 1; i < n; i++) {
                if (y >= bodyY[i]) {
                    float t = (y - bodyY[i]) / (bodyY[i - 1] - bodyY[i]);
                    return imgY[i] + (imgY[i - 1] - imgY[i]) * t;
                }
            }
            return imgY[n - 1] + (bodyY[n - 1] - y) * scale;
        }

        /** A landmark's sheet x, or NaN. */
        float x(String mark) {
            List<Object> pt = MiniJson.list(px.get(mark));
            return pt.size() >= 2 ? (float) MiniJson.num(pt.get(0), Double.NaN) : Float.NaN;
        }

        float y(String mark) {
            List<Object> pt = MiniJson.list(px.get(mark));
            return pt.size() >= 2 ? (float) MiniJson.num(pt.get(1), Double.NaN) : Float.NaN;
        }
    }

    /** Where a chain of landmarks crosses sheet row {@code row} (its x), or NaN outside it. */
    private static float along(View v, String[] chain, float row) {
        for (int i = 0; i + 1 < chain.length; i++) {
            float y0 = v.y(chain[i]), y1 = v.y(chain[i + 1]);
            if (Float.isNaN(y0) || Float.isNaN(y1)) {
                continue;
            }
            if (row >= Math.min(y0, y1) && row <= Math.max(y0, y1) && y1 != y0) {
                float t = (row - y0) / (y1 - y0);
                return v.x(chain[i]) + (v.x(chain[i + 1]) - v.x(chain[i])) * t;
            }
        }
        return Float.NaN;
    }

    /** The middle line of a part on a view, at a sheet row: where its own outline should contain it. */
    private static float axis(View v, int part, float row) {
        if (v.profile) {
            switch (part) {
                case HEAD: return mid(v.x("nose"), v.x("head_back"));
                case TORSO: return along(v, new String[]{"nape", "shoulder_l", "hip_l"}, row);
                case LEG_L:
                case LEG_R: return along(v, new String[]{"hip_l", "knee_l", "ankle_l"}, row);
                default: return Float.NaN; // the arms lie over the body in a profile: they tell nothing there
            }
        }
        switch (part) {
            case HEAD: return mid(v.x("ear_l"), v.x("ear_r"));
            case TORSO: return mid(v.x("shoulder_l"), v.x("shoulder_r"));
            case ARM_L: return along(v, new String[]{"shoulder_l", "elbow_l", "wrist_l", "index_l"}, row);
            case ARM_R: return along(v, new String[]{"shoulder_r", "elbow_r", "wrist_r", "index_r"}, row);
            case LEG_L: return along(v, new String[]{"hip_l", "knee_l", "ankle_l", "toe_l"}, row);
            case LEG_R: return along(v, new String[]{"hip_r", "knee_r", "ankle_r", "toe_r"}, row);
            default: return Float.NaN;
        }
    }

    private static float mid(float a, float b) {
        return Float.isNaN(a) || Float.isNaN(b) ? Float.NaN : (a + b) / 2;
    }

    /**
     * Fits one axis (0 x from the front view, 2 z from the profile): per part, per band of sheet rows, the mesh's
     * extent is mapped onto the sheet's run around the part's middle line.
     */
    private static void fitAxis(View view, float[] p, float[] q, int[] partOf, int solid, Map<String, float[]> marks,
                                int axis, boolean keepHair, float[] scaleOut) {
        float[] crown = marks.get("crown"), eye = marks.get("eye_l"), chin = marks.get("chin");
        // the sheet's span of rows the body covers
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int v = 0; v < solid; v++) {
            float r = view.row(p[v * 3 + 1]);
            top = Math.min(top, r);
            bottom = Math.max(bottom, r);
        }
        final int band = 4;
        int rows = (int) Math.ceil((bottom - top) / band) + 1;
        // the body's middle on the sheet for converting pixels to body units: the line through the shoulders' middle
        // (front) or the joints' line (profile), where the body has x = 0 (front) or z of the joints (profile)
        for (int part = 0; part < PARTS; part++) {
            float[] mMin = new float[rows], mMax = new float[rows], tMin = new float[rows], tMax = new float[rows];
            boolean[] have = new boolean[rows], good = new boolean[rows];
            Arrays.fill(mMin, Float.MAX_VALUE);
            Arrays.fill(mMax, -Float.MAX_VALUE);
            int count = 0;
            for (int v = 0; v < solid; v++) {
                if (partOf[v] != part) {
                    continue;
                }
                int r = (int) ((view.row(p[v * 3 + 1]) - top) / band);
                if (r < 0 || r >= rows) {
                    continue;
                }
                float c = p[v * 3 + axis];
                mMin[r] = Math.min(mMin[r], c);
                mMax[r] = Math.max(mMax[r], c);
                have[r] = true;
                count++;
            }
            if (count == 0) {
                continue;
            }
            // the reference: on the profile the joints sit at the body's z of its skeleton (about 0); in front x = 0
            float centreBody = 0f;
            float centreImg = view.profile ? along(view, new String[]{"nape", "shoulder_l", "hip_l", "knee_l", "ankle_l"}, view.y("hip_l"))
                    : mid(view.x("shoulder_l"), view.x("shoulder_r"));
            for (int r = 0; r < rows; r++) {
                if (!have[r] || mMax[r] - mMin[r] < 1e-3f) {
                    continue;
                }
                float row = top + (r + 0.5f) * band;
                if (part == HEAD && keepHair && eye != null && row < view.row(eye[1]) - 2f) {
                    continue; // the head above the eyes on the sheet is hair: the skull keeps its own shape
                }
                float ax = axis(view, part, row);
                if (Float.isNaN(ax)) {
                    continue;
                }
                // the body's own centre on the profile follows the joints' line at this height
                float cImg = view.profile ? along(view, new String[]{"nape", "shoulder_l", "hip_l", "knee_l", "ankle_l"}, row) : centreImg;
                if (Float.isNaN(cImg)) {
                    cImg = centreImg;
                }
                float[] run = runAround(view, Math.round(row), ax);
                if (run == null) {
                    continue;
                }
                // a run that holds another part's middle line too is two parts touching: no use for either
                boolean shared = false;
                for (int o = 0; o < PARTS && !shared; o++) {
                    if (o == part || o == HEAD && part != HEAD || part == HEAD) {
                        continue;
                    }
                    float ox = axis(view, o, row);
                    shared = !Float.isNaN(ox) && Math.abs(ox - ax) > 3f && ox >= run[0] - 1 && ox <= run[1] + 1;
                }
                if (shared) {
                    continue;
                }
                tMin[r] = (run[0] - cImg) / view.scale + centreBody;
                tMax[r] = (run[1] + 1 - cImg) / view.scale + centreBody;
                if (view.profile) {
                    // the profile faces +z to the sheet's right
                    good[r] = true;
                } else {
                    good[r] = true;
                }
            }
            float[] s = new float[rows], o = new float[rows];
            int goodRows = 0;
            double sumS = 0;
            for (int r = 0; r < rows; r++) {
                if (good[r]) {
                    float mw = mMax[r] - mMin[r], tw = tMax[r] - tMin[r];
                    s[r] = Math.max(0.55f, Math.min(1.9f, tw / mw));
                    // the part's middle moves to the sheet's middle of the run
                    o[r] = (tMin[r] + tMax[r]) / 2 - (mMin[r] + mMax[r]) / 2 * 1f;
                    goodRows++;
                    sumS += s[r];
                }
            }
            if (goodRows < 3) {
                continue;
            }
            scaleOut[part] = (float) (sumS / goodRows);
            fill(s, good);
            fill(o, good);
            smooth(s, 3);
            smooth(o, 3);
            // the mesh middle per band (to scale about), smoothed the same way
            float[] mc = new float[rows];
            boolean[] hv = have.clone();
            for (int r = 0; r < rows; r++) {
                mc[r] = have[r] ? (mMin[r] + mMax[r]) / 2 : 0f;
            }
            fill(mc, hv);
            smooth(mc, 3);
            for (int v = 0; v < p.length / 3; v++) {
                if (partOf[v] != part) {
                    continue;
                }
                float fr = (view.row(p[v * 3 + 1]) - top) / band - 0.5f;
                int r0 = Math.max(0, Math.min(rows - 1, (int) Math.floor(fr))), r1 = Math.min(rows - 1, r0 + 1);
                float t = Math.max(0f, Math.min(1f, fr - r0));
                float sc = s[r0] + (s[r1] - s[r0]) * t, of = o[r0] + (o[r1] - o[r0]) * t, cm = mc[r0] + (mc[r1] - mc[r0]) * t;
                float c = p[v * 3 + axis];
                q[v * 3 + axis] = cm + of + (c - cm) * sc;
            }
        }
    }

    /** The run on a row that holds x (or the nearest within 6 pixels). */
    private static float[] runAround(View v, int row, float x) {
        float[] best = null;
        float bd = 6f;
        for (float[] r : v.outline.runs(v.name, row)) {
            float d = x < r[0] ? r[0] - x : x > r[1] ? x - r[1] : 0f;
            if (d <= bd) {
                bd = d;
                best = r;
            }
        }
        return best;
    }

    /** Fills the rows without a value from their nearest valued neighbours (linearly between, flat beyond). */
    private static void fill(float[] a, boolean[] ok) {
        int n = a.length, last = -1;
        for (int i = 0; i < n; i++) {
            if (!ok[i]) {
                continue;
            }
            if (last < 0) {
                for (int k = 0; k < i; k++) {
                    a[k] = a[i];
                }
            } else {
                for (int k = last + 1; k < i; k++) {
                    float t = (k - last) / (float) (i - last);
                    a[k] = a[last] + (a[i] - a[last]) * t;
                }
            }
            last = i;
        }
        if (last >= 0) {
            for (int k = last + 1; k < n; k++) {
                a[k] = a[last];
            }
        }
        for (int i = 0; i < n; i++) {
            ok[i] = last >= 0;
        }
    }

    private static void smooth(float[] a, int radius) {
        float[] b = a.clone();
        for (int i = 0; i < a.length; i++) {
            double s = 0;
            int c = 0;
            for (int k = -radius; k <= radius; k++) {
                int j = i + k;
                if (j >= 0 && j < a.length) {
                    s += b[j];
                    c++;
                }
            }
            a[i] = (float) (s / c);
        }
    }
}
