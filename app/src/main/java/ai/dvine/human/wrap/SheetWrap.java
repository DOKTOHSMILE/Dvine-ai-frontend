package ai.dvine.human.wrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.dvine.brain.MiniJson;
import ai.dvine.human.HumanInstance;

/**
 * Dresses a body in its character sheet: each view on the sheet (front, back, side profile, face close-up) is warped
 * onto the standing body by its landmarks (a {@link ThinPlate} per view, from the body's landmarks to where the sheet
 * shows them), and every vertex gets where it lands in each view and how much of each view it takes, by the way its
 * surface faces: the face close-up for the front of the head, the profile for the sides of the head, the back view
 * behind, the front view for the front of the body. The skin shader then paints from the sheet itself. Computed at
 * start-up, once per body; the coordinates ride the vertices, so the paint stays put as the body moves. Pure Java.
 */
public final class SheetWrap {
    /** The sheet's picture in the app's assets, and its size in pixels. */
    public final String sheet;
    public final int width, height;
    /** Hair geometry to keep over the painted head ("none" for a bald look). */
    public final String hair;
    private final Map<String, Object> views;

    private SheetWrap(String sheet, int width, int height, String hair, Map<String, Object> views) {
        this.sheet = sheet;
        this.width = width;
        this.height = height;
        this.hair = hair;
        this.views = views;
    }

    /** The calibration (assets/looks/*.json): the sheet, its size, and per view the landmarks' pixels. */
    public static SheetWrap parse(String json) {
        Map<String, Object> m = MiniJson.obj(MiniJson.parse(json));
        SheetWrap w = new SheetWrap(MiniJson.str(m.get("sheet"), null), (int) MiniJson.num(m.get("width"), 1024),
                (int) MiniJson.num(m.get("height"), 1536), MiniJson.str(m.get("hair"), "none"), MiniJson.obj(m.get("views")));
        for (Map.Entry<String, Object> e : MiniJson.obj(m.get("boxes")).entrySet()) {
            List<Object> b = MiniJson.list(e.getValue());
            if (b.size() == 4) {
                w.boxes.put(e.getKey(), new int[]{(int) MiniJson.num(b.get(0), 0), (int) MiniJson.num(b.get(1), 0),
                        (int) MiniJson.num(b.get(2), 0), (int) MiniJson.num(b.get(3), 0)});
            }
        }
        return w;
    }

    /** A view's landmark pixels (name → [x, y]). */
    public Map<String, Object> view(String name) {
        return MiniJson.obj(views.get(name));
    }

    /** Each view's box on the look picture (x0, y0, x1, y1). */
    public final Map<String, int[]> boxes = new java.util.LinkedHashMap<String, int[]>();

    /** Per vertex: uv[8] (front, back, side, face; 0..1 across the sheet) and weight[4] in the same order. */
    public static final class Result {
        public final float[] uv, weight;

        Result(float[] uv, float[] weight) {
            this.uv = uv;
            this.weight = weight;
        }
    }

    /** The wrap for a body standing as it is now (its rest stance). */
    public Result compute(HumanInstance h) {
        return compute(h.pos, h.nor, h.pos.length / 3, BodyMarks.of(h));
    }

    public Result compute(float[] pos, float[] nor, int count, Map<String, float[]> marks) {
        ThinPlate front = warp("front", marks, false), back = warp("back", marks, false), side = warp("side", marks, true),
                face = warp("face", marks, false);
        float[] crown = marks.get("crown"), chin = marks.get("chin");
        float hh = crown != null && chin != null ? crown[1] - chin[1] : 0.7f;
        float neckY = chin != null ? chin[1] : 0.3f;
        float[] uv = new float[count * 8], w = new float[count * 4], t = new float[2];
        for (int v = 0; v < count; v++) {
            float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
            float nx = nor[v * 3], ny = nor[v * 3 + 1], nz = nor[v * 3 + 2];
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1e-6f) {
                nx /= nl;
                nz /= nl;
            }
            ThinPlate[] tp = {front, back, side, face};
            for (int k = 0; k < 4; k++) {
                if (tp[k] == null) {
                    continue;
                }
                tp[k].map(k == 2 ? z : x, y, t);
                uv[v * 8 + k * 2] = t[0] / width;
                uv[v * 8 + k * 2 + 1] = t[1] / height;
            }
            // the head (from just under the chin up) takes the close-up and the profile; the body front and back
            float head = smooth(neckY - 0.12f * hh, neckY + 0.04f * hh, y);
            float f = smooth(-0.22f, 0.22f, nz);
            float wf = f * (1 - head), wb = (1 - f) * (1 - head);
            float a = (float) Math.pow(Math.max(0f, nz), 2), s = nx * nx * 1.2f, bk = (float) Math.pow(Math.max(0f, -nz), 2);
            float sum = a + s + bk + 1e-6f;
            a = (float) Math.pow(a / sum, 1.5);
            s = (float) Math.pow(s / sum, 1.5);
            bk = (float) Math.pow(bk / sum, 1.5);
            sum = a + s + bk + 1e-6f;
            float wface = head * a / sum, wside = head * s / sum, wback = head * bk / sum;
            if (face == null) {
                wf += wface;
                wface = 0f;
            }
            if (side == null) {
                wf += wside * Math.max(0f, nz);
                wb += wside * (1 - Math.max(0f, nz));
                wside = 0f;
            }
            w[v * 4] = wf;
            w[v * 4 + 1] = wb + wback;
            w[v * 4 + 2] = wside;
            w[v * 4 + 3] = wface;
        }
        return new Result(uv, w);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** A view's warp from the body's landmarks it shows, or null when the sheet doesn't give enough of them. */
    private ThinPlate warp(String view, Map<String, float[]> marks, boolean profile) {
        Map<String, Object> px = MiniJson.obj(views.get(view));
        List<float[]> pairs = new ArrayList<float[]>();
        for (Map.Entry<String, Object> e : px.entrySet()) {
            float[] b = marks.get(e.getKey());
            List<Object> p = MiniJson.list(e.getValue());
            if (b == null || p.size() < 2) {
                continue;
            }
            pairs.add(new float[]{profile ? b[2] : b[0], b[1], (float) MiniJson.num(p.get(0), 0), (float) MiniJson.num(p.get(1), 0)});
        }
        if (pairs.size() < 4) {
            return null;
        }
        float[] fx = new float[pairs.size()], fy = new float[pairs.size()], tu = new float[pairs.size()], tv = new float[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            fx[i] = pairs.get(i)[0];
            fy[i] = pairs.get(i)[1];
            tu[i] = pairs.get(i)[2];
            tv[i] = pairs.get(i)[3];
        }
        return new ThinPlate(fx, fy, tu, tv, 1e-3);
    }
}
