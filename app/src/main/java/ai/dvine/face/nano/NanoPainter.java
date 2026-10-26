package ai.dvine.face.nano;

import android.graphics.Canvas;
import android.graphics.Paint;

import ai.dvine.face.WireHead;

/**
 * Draws nano dots: every point gets a soft glow pass and a bright core,
 * bucketed by brightness so a whole swarm is a few drawPoints calls.
 * Also draws the Blender-style dotted grid floor.
 */
public final class NanoPainter {
    private static final int BUCKETS = 5;

    private final float[][] buckets = new float[BUCKETS][];
    private final int[] fill = new int[BUCKETS];
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] screen = new float[0];
    private float[] scratch = new float[0];
    private final float[] grid;
    private final int gridMajor;
    private float[] gridWorld;
    private float gridFloor = Float.NaN;

    public NanoPainter(int capacity) {
        for (int i = 0; i < BUCKETS; i++) {
            buckets[i] = new float[capacity * 2];
        }
        core.setStyle(Paint.Style.STROKE);
        core.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        // grid floor: lines every 0.5 from -6 to 6, dotted every 0.1; axes first
        java.util.List<float[]> g = new java.util.ArrayList<float[]>();
        for (int k = -12; k <= 12; k++) {
            float c = k * 0.5f;
            for (int j = -60; j <= 60; j++) {
                float t = j * 0.1f;
                g.add(new float[]{t, c, k == 0 ? 1 : 0});
                g.add(new float[]{c, t, k == 0 ? 2 : 0});
            }
        }
        grid = new float[g.size() * 3];
        int major = 0;
        for (int i = 0; i < g.size(); i++) {
            grid[i * 3] = g.get(i)[0];
            grid[i * 3 + 1] = g.get(i)[1];
            grid[i * 3 + 2] = g.get(i)[2];
            if (g.get(i)[2] > 0) {
                major++;
            }
        }
        gridMajor = major;
    }

    /** Draws the swarm projected with the given camera. */
    public void drawSwarm(Canvas canvas, Swarm s, float yawDeg, float pitchDeg, float cx, float cy, float unit,
                          int color, float alpha, float density, float time) {
        int n = s.capacity;
        if (screen.length < n * 3) {
            screen = new float[n * 3];
        }
        WireHead.project(s.pos, n, yawDeg, pitchDeg, 0f, cx, cy, unit, screen);
        clear();
        int flick = (int) (time * 9f);
        for (int i = 0; i < n; i++) {
            float l = s.light[i];
            if (l < 0.02f) {
                continue;
            }
            float z = screen[i * 3 + 2];
            float depth = Math.max(0.12f, Math.min(1f, (z + 1.3f) / 2.3f));
            float shimmer = 0.85f + 0.15f * Swarm.noise(i * 31 + flick);
            put(screen[i * 3], screen[i * 3 + 1], l * depth * shimmer);
        }
        flush(canvas, color, alpha, Math.max(density * 1.1f, unit * 0.0075f));
    }

    /** Blender-like floor: dotted grid on the ground plane, red X axis and green depth axis. */
    public void drawGrid(Canvas canvas, float yawDeg, float pitchDeg, float cx, float cy, float unit, float floorY,
                         float alpha, float density) {
        int n = grid.length / 3;
        if (scratch.length < n * 3) {
            scratch = new float[n * 3];
        }
        if (gridWorld == null || gridFloor != floorY) {
            if (gridWorld == null) {
                gridWorld = new float[n * 3];
            }
            for (int i = 0; i < n; i++) {
                gridWorld[i * 3] = grid[i * 3];
                gridWorld[i * 3 + 1] = floorY;
                gridWorld[i * 3 + 2] = grid[i * 3 + 1];
            }
            gridFloor = floorY;
        }
        float[] p = gridWorld;
        WireHead.project(p, n, yawDeg, pitchDeg, 0f, cx, cy, unit, scratch);
        core.setStrokeWidth(Math.max(density * 1.2f, 1.5f));
        float[] plain = buckets[0];
        float[] red = buckets[1];
        float[] green = buckets[2];
        int a = 0, b = 0, c = 0;
        for (int i = 0; i < n; i++) {
            float z = scratch[i * 3 + 2];
            // only the floor near the viewer: far lines would cross the persona
            if (z < -3.2f || z > 3.5f) {
                continue;
            }
            float type = grid[i * 3 + 2];
            if (type == 1f) {
                red[b++] = scratch[i * 3];
                red[b++] = scratch[i * 3 + 1];
            } else if (type == 2f) {
                green[c++] = scratch[i * 3];
                green[c++] = scratch[i * 3 + 1];
            } else if (((int) (Math.abs(grid[i * 3]) * 10 + 0.5f)) % 2 == 0) {
                plain[a++] = scratch[i * 3];
                plain[a++] = scratch[i * 3 + 1];
            }
        }
        core.setColor(0xFF5A6470);
        core.setAlpha((int) (70 * alpha));
        canvas.drawPoints(plain, 0, a, core);
        core.setColor(0xFFE0504A);
        core.setAlpha((int) (150 * alpha));
        canvas.drawPoints(red, 0, b, core);
        core.setColor(0xFF6BCB4A);
        core.setAlpha((int) (150 * alpha));
        canvas.drawPoints(green, 0, c, core);
    }

    /** Draws loose points (already in screen space) as nano dots. */
    public void drawPoints(Canvas canvas, float[] xy, float[] level, int count, int color, float alpha, float size) {
        clear();
        for (int i = 0; i < count; i++) {
            put(xy[i * 2], xy[i * 2 + 1], level[i]);
        }
        flush(canvas, color, alpha, size);
    }

    private void clear() {
        for (int i = 0; i < BUCKETS; i++) {
            fill[i] = 0;
        }
    }

    private void put(float x, float y, float level) {
        int b = Math.min(BUCKETS - 1, Math.max(0, (int) (level * BUCKETS)));
        float[] arr = buckets[b];
        int o = fill[b];
        if (o + 2 > arr.length) {
            return;
        }
        arr[o] = x;
        arr[o + 1] = y;
        fill[b] = o + 2;
    }

    private void flush(Canvas canvas, int color, float alpha, float size) {
        core.setColor(color);
        glow.setColor(color);
        for (int b = 0; b < BUCKETS; b++) {
            if (fill[b] == 0) {
                continue;
            }
            float level = (b + 0.5f) / BUCKETS;
            if (b >= 2) {
                glow.setStrokeWidth(size * (3.2f + level * 2f));
                glow.setAlpha(clamp(alpha * 34f * level));
                canvas.drawPoints(buckets[b], 0, fill[b], glow);
            }
            core.setStrokeWidth(size * (0.7f + level * 0.7f));
            core.setAlpha(clamp(alpha * (60f + 195f * level)));
            canvas.drawPoints(buckets[b], 0, fill[b], core);
        }
    }

    private static int clamp(float v) {
        return (int) Math.max(0f, Math.min(255f, v));
    }

    public int gridMajorCount() {
        return gridMajor;
    }
}
