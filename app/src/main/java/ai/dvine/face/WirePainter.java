package ai.dvine.face;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Draws a {@link WireHead} as glowing lines. Edges are sorted into a few
 * depth buckets so a whole head is a handful of drawLines calls per frame.
 */
public final class WirePainter {
    private static final int BUCKETS = 5;

    private final WireHead head;
    private final WireHead.Pose pose = new WireHead.Pose();
    private final float[] deformed;
    private final float[] screen;
    private final float[][] lines = new float[BUCKETS * 3][];
    private final int[] fill = new int[BUCKETS * 3];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] normals;
    private final float[] light;
    private final float[] verts2d;
    private final int[] colors;
    private final short[] indices;
    private final Paint skin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float top;
    private float bottom;

    public WirePainter(WireHead head) {
        this.head = head;
        deformed = new float[head.rest.length];
        screen = new float[head.rest.length];
        normals = new float[head.rest.length];
        light = new float[head.vertexCount()];
        verts2d = new float[head.vertexCount() * 2];
        colors = new int[head.vertexCount()];
        indices = new short[head.tris.length];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = new float[head.edgeCount() * 4];
        }
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        dot.setStyle(Paint.Style.FILL);
    }

    /** Top of the drawn head in screen pixels after the last draw. */
    public float top() {
        return top;
    }

    public float bottom() {
        return bottom;
    }

    public void draw(Canvas canvas, FaceRig rig, float extraYaw, float cx, float cy, float unit,
                     int color, float alpha, float density, float fadeTop, float fadeBottom) {
        if (alpha <= 0.01f) {
            return;
        }
        pose.jaw = rig.jaw.value;
        pose.smile = rig.smile.value;
        pose.browL = rig.browL.value;
        pose.browR = rig.browR.value;
        pose.blink = rig.blink.value;
        pose.lookX = rig.lookX.value;
        pose.lookY = rig.lookY.value;
        pose.time = rig.time();
        pose.breathe = (float) Math.sin(rig.time() * 1.35f) * 0.010f;
        head.deform(pose, deformed);
        int count = head.vertexCount();
        WireHead.project(deformed, count, rig.yaw.value + extraYaw, rig.pitch.value, rig.roll.value,
                cx, cy, unit, screen);

        top = Float.MAX_VALUE;
        bottom = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float y = screen[i * 3 + 1];
            if (y < top) {
                top = y;
            }
            if (y > bottom) {
                bottom = y;
            }
        }

        drawSkin(canvas, unit, color, alpha * WireHead.clamp(rig.glow.value, 0.3f, 1.1f), fadeTop, fadeBottom);

        for (int i = 0; i < fill.length; i++) {
            fill[i] = 0;
        }
        int[] edges = head.edges;
        int[] groups = head.edgeGroup;
        for (int e = 0; e < groups.length; e++) {
            int a = edges[e * 2] * 3;
            int b = edges[e * 2 + 1] * 3;
            float z = (screen[a + 2] + screen[b + 2]) * 0.5f;
            int group = groups[e];
            float vis = (z + 0.30f) / 1.05f;
            if (group == WireHead.HAIR) {
                vis = Math.max(vis, 0.28f + 0.2f * vis);
            }
            float y = (screen[a + 1] + screen[b + 1]) * 0.5f;
            if (y > fadeTop) {
                vis *= Math.max(0f, (fadeBottom - y) / Math.max(1f, fadeBottom - fadeTop));
            }
            if (vis <= 0.04f) {
                continue;
            }
            int bucket = Math.min(BUCKETS - 1, (int) (vis * BUCKETS));
            int slot = group * BUCKETS + bucket;
            float[] arr = lines[slot];
            int o = fill[slot];
            arr[o] = screen[a];
            arr[o + 1] = screen[a + 1];
            arr[o + 2] = screen[b];
            arr[o + 3] = screen[b + 1];
            fill[slot] = o + 4;
        }

        float glowLevel = WireHead.clamp(rig.glow.value, 0.25f, 1.2f);
        float thin = Math.max(density * 0.7f, unit * 0.0042f);
        line.setColor(color);
        glow.setColor(color);
        for (int group = 0; group < 3; group++) {
            float width = group == WireHead.FEATURE ? thin * 1.9f : thin;
            float boost = group == WireHead.FEATURE ? 1.25f : group == WireHead.HAIR ? 0.85f : 0.8f;
            for (int k = 0; k < BUCKETS; k++) {
                int slot = group * BUCKETS + k;
                if (fill[slot] == 0) {
                    continue;
                }
                float level = (k + 0.5f) / BUCKETS;
                float a = alpha * glowLevel * boost * level;
                glow.setStrokeWidth(width * 4.5f);
                glow.setAlpha(clampAlpha(a * 40f));
                canvas.drawLines(lines[slot], 0, fill[slot], glow);
                line.setStrokeWidth(width);
                line.setAlpha(clampAlpha(a * 235f));
                canvas.drawLines(lines[slot], 0, fill[slot], line);
            }
        }

        if (head.ivie) {
            dot.setColor(color);
            float r = thin * 1.4f;
            for (int i = 0; i < count; i += 2) {
                float z = screen[i * 3 + 2];
                if (z < 0.35f || head.tag[i] != WireHead.T_SKIN || screen[i * 3 + 1] > fadeTop) {
                    continue;
                }
                dot.setAlpha(clampAlpha(alpha * glowLevel * 150f * Math.min(1f, z)));
                canvas.drawCircle(screen[i * 3], screen[i * 3 + 1], r, dot);
            }
        }
    }

    /** Translucent lit skin under the wires: this is what makes the head read as 3D. */
    private void drawSkin(Canvas canvas, float unit, int color, float alpha, float fadeTop, float fadeBottom) {
        head.light(screen, unit, normals, light);
        int n = head.vertexCount();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int i = 0; i < n; i++) {
            verts2d[i * 2] = screen[i * 3];
            float y = screen[i * 3 + 1];
            verts2d[i * 2 + 1] = y;
            float f = y > fadeTop ? Math.max(0f, (fadeBottom - y) / Math.max(1f, fadeBottom - fadeTop)) : 1f;
            float l = light[i] * f;
            int a = clampAlpha(alpha * 150f * l);
            colors[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        int count = 0;
        int[] tris = head.tris;
        for (int t = 0; t < tris.length; t += 3) {
            float z = (screen[tris[t] * 3 + 2] + screen[tris[t + 1] * 3 + 2] + screen[tris[t + 2] * 3 + 2]) / 3f;
            if (z < -0.05f) {
                continue;
            }
            indices[count++] = (short) tris[t];
            indices[count++] = (short) tris[t + 1];
            indices[count++] = (short) tris[t + 2];
        }
        if (count > 0) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, n * 2, verts2d, 0, null, 0, colors, 0,
                    indices, 0, count, skin);
        }
    }

    private static int clampAlpha(float v) {
        return (int) Math.max(0f, Math.min(255f, v));
    }
}
