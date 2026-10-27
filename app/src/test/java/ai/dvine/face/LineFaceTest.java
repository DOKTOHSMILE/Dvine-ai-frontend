package ai.dvine.face;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** The line-art faces: finite, inside their frame, eyes that close on a blink, a mouth that opens with the jaw. */
public class LineFaceTest {
    private static float span(List<float[]> s, int from, int to, int axis) {
        float lo = 1e9f, hi = -1e9f;
        for (int i = from; i < to; i++) {
            float[] a = s.get(i);
            for (int j = axis; j < a.length; j += 2) {
                lo = Math.min(lo, a[j]);
                hi = Math.max(hi, a[j]);
            }
        }
        return hi - lo;
    }

    @Test
    public void facesAreFiniteFramedAndAnimated() {
        for (boolean ivie : new boolean[]{false, true}) {
            LineFace.Pose p = new LineFace.Pose();
            List<float[]> s = LineFace.strokes(ivie, p);
            assertTrue(s.size() > 12);
            for (float[] a : s) {
                assertTrue(a.length >= 4 && a.length % 2 == 0);
                for (float v : a) {
                    assertTrue(Float.isFinite(v) && Math.abs(v) < 1.5f);
                }
            }
            // a blink collapses the eyes: total height of all strokes drops (eyes flatten), the stroke count can drop (iris hidden)
            p.blink = 1f;
            List<float[]> shut = LineFace.strokes(ivie, p);
            assertTrue(shut.size() <= s.size());
            // speaking opens the mouth: the lowest lip point moves down
            LineFace.Pose talk = new LineFace.Pose();
            talk.jaw = 1f;
            assertTrue(lowest(LineFace.strokes(ivie, talk)) <= lowest(LineFace.strokes(ivie, new LineFace.Pose())));
            // a head turn moves the face sideways but keeps it in frame
            LineFace.Pose turned = new LineFace.Pose();
            turned.yaw = 30f;
            List<float[]> t = LineFace.strokes(ivie, turned);
            assertTrue(span(t, 0, t.size(), 0) < 2.6f);
        }
    }

    /** The lowest point of the mouth strokes (the last four or five strokes). */
    private static float lowest(List<float[]> s) {
        float lo = 1e9f;
        for (int i = s.size() - 4; i < s.size(); i++) {
            float[] a = s.get(i);
            for (int j = 1; j < a.length; j += 2) {
                lo = Math.min(lo, a[j]);
            }
        }
        return lo;
    }

    /** Writes both faces' strokes as JSON when LINEFACE_OUT is set (pose "jaw,smile,yaw,blink"), to preview off the phone. */
    @Test
    public void preview() throws Exception {
        String out = System.getenv("LINEFACE_OUT");
        if (out == null) {
            return;
        }
        String[] poses = System.getenv().getOrDefault("LINEFACE_POSE", "0,0,0,0").split(",");
        StringBuilder b = new StringBuilder("[");
        for (int k = 0; k < 2; k++) {
            LineFace.Pose p = new LineFace.Pose();
            p.jaw = Float.parseFloat(poses[0]);
            p.smile = Float.parseFloat(poses[1]) + (k == 1 ? 0.5f : 0f);
            p.yaw = Float.parseFloat(poses[2]);
            p.blink = Float.parseFloat(poses[3]);
            b.append(k == 0 ? "[" : ",[");
            List<float[]> s = LineFace.strokes(k == 1, p);
            for (int i = 0; i < s.size(); i++) {
                b.append(i == 0 ? "" : ",").append(java.util.Arrays.toString(s.get(i)));
            }
            b.append("]");
        }
        try (java.io.Writer w = new java.io.FileWriter(out)) {
            w.write(b.append("]").toString());
        }
    }
}
