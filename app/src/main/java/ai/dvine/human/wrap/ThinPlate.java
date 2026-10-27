package ai.dvine.human.wrap;

/**
 * A thin-plate spline from the plane to the plane: the smoothest warp that carries each control point exactly onto
 * its target (body landmarks onto the same landmarks on a picture). Pure Java.
 */
public final class ThinPlate {
    private final float[] cx, cy;
    private final double[] wu, wv; // N kernel weights, then the affine part (1, x, y)

    public ThinPlate(float[] fromX, float[] fromY, float[] toU, float[] toV, double smooth) {
        int n = fromX.length, m = n + 3;
        cx = fromX.clone();
        cy = fromY.clone();
        double[][] a = new double[m][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = i == j ? smooth : kernel(cx[i] - cx[j], cy[i] - cy[j]);
            }
            a[i][n] = a[n][i] = 1;
            a[i][n + 1] = a[n + 1][i] = cx[i];
            a[i][n + 2] = a[n + 2][i] = cy[i];
        }
        double[] bu = new double[m], bv = new double[m];
        for (int i = 0; i < n; i++) {
            bu[i] = toU[i];
            bv[i] = toV[i];
        }
        wu = solve(copy(a), bu);
        wv = solve(copy(a), bv);
    }

    private static double kernel(double dx, double dy) {
        double r2 = dx * dx + dy * dy;
        return r2 < 1e-12 ? 0 : r2 * Math.log(r2) * 0.5;
    }

    /** Where (x, y) lands: out[0] = u, out[1] = v. */
    public void map(float x, float y, float[] out) {
        int n = cx.length;
        double u = wu[n] + wu[n + 1] * x + wu[n + 2] * y, v = wv[n] + wv[n + 1] * x + wv[n + 2] * y;
        for (int i = 0; i < n; i++) {
            double k = kernel(x - cx[i], y - cy[i]);
            u += wu[i] * k;
            v += wv[i] * k;
        }
        out[0] = (float) u;
        out[1] = (float) v;
    }

    private static double[][] copy(double[][] a) {
        double[][] c = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i].clone();
        }
        return c;
    }

    /** Gaussian elimination with partial pivoting. */
    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) {
                    piv = r;
                }
            }
            double[] t = a[col];
            a[col] = a[piv];
            a[piv] = t;
            double tb = b[col];
            b[col] = b[piv];
            b[piv] = tb;
            double d = a[col][col];
            if (Math.abs(d) < 1e-12) {
                continue;
            }
            for (int r = col + 1; r < n; r++) {
                double f = a[r][col] / d;
                if (f == 0) {
                    continue;
                }
                for (int c = col; c < n; c++) {
                    a[r][c] -= f * a[col][c];
                }
                b[r] -= f * b[col];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int c = r + 1; c < n; c++) {
                s -= a[r][c] * x[c];
            }
            x[r] = Math.abs(a[r][r]) < 1e-12 ? 0 : s / a[r][r];
        }
        return x;
    }
}
