package ai.dvine.face;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Raises the hands into view for a gesture, holds, then lowers them. */
public final class HandPainter {
    private final WireHand hand = new WireHand();
    private final float[] pos = new float[hand.vertexCount() * 3];
    private final float[] screen = new float[pos.length];
    private final float[] lines = new float[hand.edges.length * 2];
    private final WireHand.Pose live = new WireHand.Pose();
    private final Spring1D lift = new Spring1D(7f, 0.9f, 0f);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] normals = new float[pos.length];
    private final float[] light = new float[hand.vertexCount()];
    private final float[] verts2d = new float[hand.vertexCount() * 2];
    private final int[] colors = new int[hand.vertexCount()];
    private final short[] indices = new short[hand.tris.length];
    private final long[] keys = new long[hand.tris.length / 3];
    private final Paint skinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean skinMode = true;
    private int skinTone = 0xFF8A5634;
    private Gestures.Gesture gesture;
    private float holdUntil;
    private float time;

    public HandPainter() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Shows a gesture for {@code seconds}. Unknown keys are ignored. */
    public void show(String key, float seconds) {
        Gestures.Gesture g = Gestures.get(key);
        if (g == null) {
            return;
        }
        gesture = g;
        live.set(g.pose);
        holdUntil = time + seconds;
    }

    public void setLook(Look look) {
        skinMode = look.style == Look.Style.SKIN;
        skinTone = look.skinColor();
    }

    public boolean visible() {
        return lift.value > 0.02f;
    }

    public void tick(float dt) {
        time += dt;
        lift.step(gesture != null && time < holdUntil ? 1f : 0f, dt);
        if (gesture != null && time >= holdUntil && lift.value < 0.02f) {
            gesture = null;
        }
    }

    public void draw(Canvas canvas, float cx, float cy, float unit, int color, float alpha, float density) {
        Gestures.Gesture g = gesture;
        if (g == null || lift.value <= 0.02f) {
            return;
        }
        live.set(g.pose);
        float wobble = 0f;
        float dx = 0f;
        float dy = 0f;
        switch (g.motion) {
            case WAVE:
                live.rollDeg += (float) Math.sin(time * 9f) * 18f;
                break;
            case SHAKE:
                live.rollDeg += (float) Math.sin(time * 14f) * 10f;
                break;
            case TAP:
                dy = (float) Math.abs(Math.sin(time * 6f)) * 0.06f;
                break;
            case CLAP:
                wobble = (float) Math.abs(Math.sin(time * 7f)) * 0.18f;
                break;
            case SCRIBBLE:
                dx = (float) Math.sin(time * 11f) * 0.05f;
                dy = (float) Math.sin(time * 7f) * 0.03f;
                break;
            default:
                break;
        }
        float rise = (1f - lift.value) * 0.9f;
        float thin = Math.max(density * 0.8f, unit * 0.005f);
        line.setColor(color);
        glow.setColor(color);
        int sides = g.twoHands ? 2 : 1;
        for (int side = 0; side < sides; side++) {
            boolean mirror = side == 1;
            float ox = (g.twoHands ? g.x + wobble : g.x) * (mirror ? -1f : 1f) + dx;
            hand.pose(live, mirror, 0.62f, ox, g.y - 0.35f - rise + dy, 0.5f, pos);
            WireHead.project(pos, hand.vertexCount(), 0f, 0f, 0f, cx, cy, unit, screen);
            float a = alpha * lift.value;
            if (skinMode) {
                Shade.light(screen, hand.tris, hand.vertexCount(), unit, normals, light);
                for (int i = 0; i < hand.vertexCount(); i++) {
                    verts2d[i * 2] = screen[i * 3];
                    verts2d[i * 2 + 1] = screen[i * 3 + 1];
                    colors[i] = Shade.skin(skinTone, color, light[i], a * 0.97f);
                }
                int count = Shade.order(screen, hand.tris, -10f, keys, indices);
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, hand.vertexCount() * 2, verts2d, 0, null, 0,
                        colors, 0, indices, 0, count, skinPaint);
            }
            int n = 0;
            for (int e = 0; e < hand.edges.length; e += 2) {
                int va = hand.edges[e] * 3;
                int vb = hand.edges[e + 1] * 3;
                lines[n++] = screen[va];
                lines[n++] = screen[va + 1];
                lines[n++] = screen[vb];
                lines[n++] = screen[vb + 1];
            }
            glow.setStrokeWidth(thin * 5f);
            glow.setAlpha((int) (45 * a));
            canvas.drawLines(lines, 0, n, glow);
            line.setStrokeWidth(thin);
            line.setAlpha((int) ((skinMode ? 150 : 230) * a));
            canvas.drawLines(lines, 0, n, line);
        }
    }
}
