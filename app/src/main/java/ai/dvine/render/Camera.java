package ai.dvine.render;

import ai.dvine.human.HumanInstance;

/**
 * The stage camera as a matrix. It reproduces the stage's own projection
 * (turn about a pivot by pitch then yaw, perspective from an eye at distance
 * {@code D}, screen centre (cx, cy), {@code unit} pixels per camera unit), so
 * GL drawing and the Canvas overlays line up exactly. Also turns a screen
 * point back into a ray for touch picking. Pure Java.
 */
public final class Camera {
    public float width = 1f, height = 1f;
    public float cx, cy, unit = 1f;
    /** Eye distance in camera units (small for the face close-up, larger for the full body). */
    public float distance = 4.4f;
    public float yawDeg, pitchDeg;
    /** Height of the rotation pivot, camera units. */
    public float pivot;

    /** Column-major model-view-projection for body-unit positions (includes body → camera units). */
    public final float[] mvp = new float[16];
    /** Column-major 3x3 view rotation, for normals. */
    public final float[] rot3 = new float[9];

    public void update() {
        float D = distance;
        float n = D * 0.05f, f = D * 4f + 12f;
        float A = (f + n) / (f - n), B = 2f * f * n / (f - n);
        float a = unit * D / (width * 0.5f), b = unit * D / (height * 0.5f);
        // the eye sits level with the pivot, so the pivot height draws at cy - unit·pivot
        float ox = (cx - width * 0.5f) / (width * 0.5f), oy = (height * 0.5f - (cy - unit * pivot)) / (height * 0.5f);
        // projection P (row-major): clip = P · (X, Y, z3, 1)
        float[] P = {a, 0, -ox, ox * D,
                0, b, -oy, oy * D,
                0, 0, -A, A * D - B,
                0, 0, -1, D};
        double yw = Math.toRadians(yawDeg), pt = Math.toRadians(pitchDeg);
        float cyw = (float) Math.cos(yw), syw = (float) Math.sin(yw), cp = (float) Math.cos(pt), sp = (float) Math.sin(pt);
        // R = Ryaw · Rpitch (row-major)
        float[] R = {cyw, syw * sp, syw * cp,
                0, cp, -sp,
                -syw, cyw * sp, cyw * cp};
        // V: p' = R (p - pv) + pv ; M: body units → camera units
        float s = HumanInstance.OUT, ty = -HumanInstance.CENTRE * HumanInstance.OUT;
        // combined affine part: p_view = R (s·p + (0, ty - pivot, 0)), the eye at (0, 0, D) level with the pivot
        float[] VM = new float[16];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                VM[i * 4 + j] = R[i * 3 + j] * s;
            }
            VM[i * 4 + 3] = R[i * 3 + 1] * (ty - pivot);
        }
        VM[15] = 1f;
        float[] out = new float[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float v = 0f;
                for (int k = 0; k < 4; k++) {
                    v += P[i * 4 + k] * VM[k * 4 + j];
                }
                out[i * 4 + j] = v;
            }
        }
        // to column-major for GL
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                mvp[j * 4 + i] = out[i * 4 + j];
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                rot3[j * 3 + i] = R[i * 3 + j];
            }
        }
    }

    /** Projects a body-unit point to screen pixels (x, y) and view depth; for tests and overlays. */
    public float[] project(float x, float y, float z) {
        float[] m = mvp;
        float cx_ = m[0] * x + m[4] * y + m[8] * z + m[12];
        float cy_ = m[1] * x + m[5] * y + m[9] * z + m[13];
        float cw = m[3] * x + m[7] * y + m[11] * z + m[15];
        return new float[]{(cx_ / cw + 1f) * 0.5f * width, (1f - cy_ / cw) * 0.5f * height, cw};
    }

    /** A ray through a screen pixel: origin and unit direction in body units. */
    public float[] ray(float sx, float sy) {
        double yw = Math.toRadians(yawDeg), pt = Math.toRadians(pitchDeg);
        float cyw = (float) Math.cos(yw), syw = (float) Math.sin(yw), cp = (float) Math.cos(pt), sp = (float) Math.sin(pt);
        float D = distance;
        // view space: the eye sits at (0, 0, D) level with the pivot and looks down -z; undo the turn about the pivot
        float dx = (sx - cx) / (unit * D), dy = (cy - unit * pivot - sy) / (unit * D), dz = -1f;
        float[] o = {0f, 0f, D}, d = {dx, dy, dz};
        float[] ow = invRotate(o, cyw, syw, cp, sp), dw = invRotate(d, cyw, syw, cp, sp);
        ow[1] += pivot;
        // camera units → body units
        float s = HumanInstance.OUT;
        float[] origin = {ow[0] / s, ow[1] / s + HumanInstance.CENTRE, ow[2] / s};
        float l = (float) Math.sqrt(dw[0] * dw[0] + dw[1] * dw[1] + dw[2] * dw[2]);
        return new float[]{origin[0], origin[1], origin[2], dw[0] / l, dw[1] / l, dw[2] / l};
    }

    /** Rᵀ·v with R = Ryaw · Rpitch. */
    private static float[] invRotate(float[] v, float cyw, float syw, float cp, float sp) {
        // undo yaw
        float x1 = v[0] * cyw - v[2] * syw, z1 = v[0] * syw + v[2] * cyw, y1 = v[1];
        // undo pitch
        float y2 = y1 * cp + z1 * sp, z2 = -y1 * sp + z1 * cp;
        return new float[]{x1, y2, z2};
    }
}
