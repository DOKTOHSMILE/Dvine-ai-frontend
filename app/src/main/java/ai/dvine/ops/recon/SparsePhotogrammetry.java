package ai.dvine.ops.recon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import ai.dvine.geom.TriMesh;
import ai.dvine.ops.task.TaskControl;

/**
 * Sparse two-view photogrammetry: blobs, patch matching, a calibrated
 * eight-point essential matrix, pose hypotheses, and linear triangulation.
 * Dense stereo, texture, and COLMAP are not run. Counts are whatever this
 * pass actually produced.
 */
public final class SparsePhotogrammetry {
    public enum Quality { SPARSE_MULTI_VIEW, FAILED, CANCELLED, SINGLE_VIEW }

    public static final class Result {
        public final String jobId;
        public final Quality quality;
        public final String method;
        public final int cameraCount;
        public final int matchCount;
        public final int sparsePointCount;
        public final int densePointCount;
        public final boolean meshValidated;
        public final boolean measured;
        public final boolean intrinsicsAssumed;
        public final double medianReprojectionPx;
        public final List<String> warnings;
        public final float[] points;
        public final TriMesh mesh;
        public final String note;

        Result(String jobId, Quality quality, String method, int cameraCount, int matchCount, int sparsePointCount,
                boolean meshValidated, boolean measured, boolean intrinsicsAssumed, double medianReprojectionPx,
                List<String> warnings, float[] points, TriMesh mesh, String note) {
            this.jobId = jobId;
            this.quality = quality;
            this.method = method;
            this.cameraCount = cameraCount;
            this.matchCount = matchCount;
            this.sparsePointCount = sparsePointCount;
            this.densePointCount = 0;
            this.meshValidated = meshValidated;
            this.measured = measured;
            this.intrinsicsAssumed = intrinsicsAssumed;
            this.medianReprojectionPx = medianReprojectionPx;
            this.warnings = warnings;
            this.points = points == null ? new float[0] : points;
            this.mesh = mesh;
            this.note = note;
        }
    }

    private static final String METHOD = "calibrated-eight-point";

    private SparsePhotogrammetry() {
    }

    /** {@code k} is focal length, cx, cy in pixels. Null assumes a centre principal point and focal 1.2× width. */
    public static Result reconstruct(List<RasterImage> images, double[] k, TaskControl control) {
        String id = UUID.randomUUID().toString();
        List<String> warnings = new ArrayList<String>();
        warnings.add("Sparse points only. No dense surface and no texture. This is not COLMAP.");
        if (control != null && control.isCancelled()) {
            return fail(id, Quality.CANCELLED, 0, 0, false, warnings, "Cancelled before any pixels were read.");
        }
        if (images == null || images.isEmpty()) {
            return fail(id, Quality.FAILED, 0, 0, false, warnings, "No images, so nothing was triangulated.");
        }
        if (images.size() == 1) {
            warnings.add("One view cannot fix a baseline.");
            return fail(id, Quality.SINGLE_VIEW, 0, 0, true, warnings, "One image was supplied. Nothing was triangulated. This is not measured.");
        }
        RasterImage a = images.get(0);
        RasterImage b = images.get(1);
        if (a.width != b.width || a.height != b.height) {
            return fail(id, Quality.FAILED, 0, 0, false, warnings, "The two views are different sizes. I did not guess a scale.");
        }
        boolean assumed = k == null || k.length < 3 || !(k[0] > 1);
        double f = assumed ? a.width * 1.2 : k[0];
        double cx = assumed ? (a.width - 1) * 0.5 : k[1];
        double cy = assumed ? (a.height - 1) * 0.5 : k[2];
        if (assumed) {
            warnings.add("Camera intrinsics were assumed, not read from the file.");
        }
        if (control != null && control.isCancelled()) {
            return fail(id, Quality.CANCELLED, 0, 0, assumed, warnings, "Cancelled during setup.");
        }
        List<Blob> left = blobs(a);
        List<Blob> right = blobs(b);
        int[][] pairs = match(a, b, left, right);
        if (control != null && control.isCancelled()) {
            return fail(id, Quality.CANCELLED, pairs.length, 0, assumed, warnings, "Cancelled after matching.");
        }
        if (pairs.length < 8) {
            warnings.add(pairs.length + " matches. Eight are required.");
            return new Result(id, Quality.FAILED, METHOD, 0, pairs.length, 0, false, false, assumed, Double.NaN, warnings,
                    new float[0], null, "Not enough overlap to triangulate. No mesh was built. This is not measured.");
        }
        double[][] n1 = new double[pairs.length][2];
        double[][] n2 = new double[pairs.length][2];
        for (int i = 0; i < pairs.length; i++) {
            Blob p = left.get(pairs[i][0]);
            Blob q = right.get(pairs[i][1]);
            n1[i][0] = (p.x - cx) / f;
            n1[i][1] = (cy - p.y) / f;
            n2[i][0] = (q.x - cx) / f;
            n2[i][1] = (cy - q.y) / f;
        }
        double[][] e = essential(n1, n2);
        if (e == null) {
            return new Result(id, Quality.FAILED, METHOD, 0, pairs.length, 0, false, false, assumed, Double.NaN, warnings,
                    new float[0], null, "The essential matrix did not solve. No points were invented.");
        }
        Pose pose = bestPose(e, n1, n2);
        if (pose == null) {
            return new Result(id, Quality.FAILED, METHOD, 0, pairs.length, 0, false, false, assumed, Double.NaN, warnings,
                    new float[0], null, "No camera pose put the points in front of both views. No mesh was built.");
        }
        double medianPx = pose.medianNorm * f;
        if (!(medianPx < 8.0) || pose.points.size() < 8) {
            warnings.add("Median reprojection " + medianPx + " px.");
            return new Result(id, Quality.FAILED, METHOD, 0, pairs.length, pose.points.size(), false, false, assumed, medianPx,
                    warnings, pack(pose.points), null, "The pose did not agree with the pixels tightly enough. This is not measured.");
        }
        float[] xyz = pack(pose.points);
        TriMesh mesh = fan(pose.points, pose.pixels);
        boolean meshOk = mesh != null && finite(mesh);
        if (!meshOk) {
            warnings.add("Points reconstructed but the triangulation was empty.");
        }
        warnings.add("Reconstructed camera count is 2. Dense point count is 0.");
        String note = "Sparse photogrammetry from " + pairs.length + " matches, " + pose.points.size()
                + " points, median reprojection " + round(medianPx) + " px. Not a dense scan and not COLMAP.";
        return new Result(id, Quality.SPARSE_MULTI_VIEW, METHOD, 2, pairs.length, pose.points.size(), meshOk, true, assumed,
                medianPx, warnings, xyz, mesh, note);
    }

    private static Result fail(String id, Quality quality, int matches, int points, boolean assumed, List<String> warnings, String note) {
        return new Result(id, quality, METHOD, 0, matches, points, false, false, assumed, Double.NaN, warnings, new float[0], null, note);
    }

    private static final class Blob {
        final double x, y;
        final int i;

        Blob(double x, double y, int i) {
            this.x = x;
            this.y = y;
            this.i = i;
        }
    }

    private static List<Blob> blobs(RasterImage image) {
        int w = image.width, h = image.height;
        boolean[] seen = new boolean[w * h];
        List<Blob> out = new ArrayList<Blob>();
        int[] stack = new int[w * h];
        for (int i = 0; i < seen.length; i++) {
            if (seen[i] || brightness(image.argb[i]) < 50) {
                continue;
            }
            int sp = 0;
            stack[sp++] = i;
            seen[i] = true;
            double sx = 0, sy = 0, sw = 0;
            int n = 0;
            int mark = out.size();
            while (sp > 0) {
                int p = stack[--sp];
                int x = p % w, y = p / w;
                float b = brightness(image.argb[p]);
                sx += x * b;
                sy += y * b;
                sw += b;
                n++;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int q = ny * w + nx;
                        if (!seen[q] && brightness(image.argb[q]) >= 50) {
                            seen[q] = true;
                            stack[sp++] = q;
                        }
                    }
                }
            }
            if (n >= 6 && n <= 500 && sw > 0) {
                out.add(new Blob(sx / sw, sy / sw, mark));
            }
        }
        return out;
    }

    private static float brightness(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return 0.3f * r + 0.59f * g + 0.11f * b;
    }

    private static int[][] match(RasterImage a, RasterImage b, List<Blob> left, List<Blob> right) {
        int n = left.size(), m = right.size();
        int[] bestR = new int[n];
        double[] bestS = new double[n];
        Arrays.fill(bestR, -1);
        int[] bestL = new int[m];
        double[] bestLS = new double[m];
        Arrays.fill(bestL, -1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double s = patch(a, left.get(i), b, right.get(j));
                if (s > bestS[i]) {
                    bestS[i] = s;
                    bestR[i] = j;
                }
                if (s > bestLS[j]) {
                    bestLS[j] = s;
                    bestL[j] = i;
                }
            }
        }
        List<int[]> pairs = new ArrayList<int[]>();
        for (int i = 0; i < n; i++) {
            int j = bestR[i];
            if (j >= 0 && bestL[j] == i && bestS[i] > 0.82) {
                pairs.add(new int[]{i, j});
            }
        }
        return pairs.toArray(new int[0][]);
    }

    private static double patch(RasterImage a, Blob p, RasterImage b, Blob q) {
        double dot = 0, na = 0, nb = 0;
        int hit = 0;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int ax = (int) Math.round(p.x) + dx, ay = (int) Math.round(p.y) + dy;
                int bx = (int) Math.round(q.x) + dx, by = (int) Math.round(q.y) + dy;
                if (ax < 0 || ay < 0 || bx < 0 || by < 0 || ax >= a.width || ay >= a.height || bx >= b.width || by >= b.height) {
                    continue;
                }
                int ca = a.argb[ay * a.width + ax], cb = b.argb[by * b.width + bx];
                for (int c = 0; c < 3; c++) {
                    double va = ((ca >> (16 - 8 * c)) & 255) / 255.0;
                    double vb = ((cb >> (16 - 8 * c)) & 255) / 255.0;
                    dot += va * vb;
                    na += va * va;
                    nb += vb * vb;
                }
                hit++;
            }
        }
        if (hit < 20 || na < 1e-6 || nb < 1e-6) {
            return 0;
        }
        return dot / Math.sqrt(na * nb);
    }

    /** Calibrated eight-point. Null when the matrix is useless. */
    static double[][] essential(double[][] p1, double[][] p2) {
        int n = p1.length;
        double mx1 = 0, my1 = 0, mx2 = 0, my2 = 0;
        for (int i = 0; i < n; i++) {
            mx1 += p1[i][0];
            my1 += p1[i][1];
            mx2 += p2[i][0];
            my2 += p2[i][1];
        }
        mx1 /= n;
        my1 /= n;
        mx2 /= n;
        my2 /= n;
        double d1 = 0, d2 = 0;
        for (int i = 0; i < n; i++) {
            d1 += Math.hypot(p1[i][0] - mx1, p1[i][1] - my1);
            d2 += Math.hypot(p2[i][0] - mx2, p2[i][1] - my2);
        }
        double s1 = Math.sqrt(2) / Math.max(1e-8, d1 / n);
        double s2 = Math.sqrt(2) / Math.max(1e-8, d2 / n);
        double[][] ata = new double[9][9];
        for (int i = 0; i < n; i++) {
            double x1 = (p1[i][0] - mx1) * s1, y1 = (p1[i][1] - my1) * s1;
            double x2 = (p2[i][0] - mx2) * s2, y2 = (p2[i][1] - my2) * s2;
            double[] row = {x1 * x2, x1 * y2, x1, y1 * x2, y1 * y2, y1, x2, y2, 1};
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    ata[r][c] += row[r] * row[c];
                }
            }
        }
        double[] eval = new double[9];
        double[][] evec = new double[9][9];
        if (!jacobi(ata, eval, evec)) {
            return null;
        }
        int small = 0;
        for (int i = 1; i < 9; i++) {
            if (Math.abs(eval[i]) < Math.abs(eval[small])) {
                small = i;
            }
        }
        double[][] en = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                en[r][c] = evec[r * 3 + c][small];
            }
        }
        double[][] t1 = {{s1, 0, -s1 * mx1}, {0, s1, -s1 * my1}, {0, 0, 1}};
        double[][] t2 = {{s2, 0, -s2 * mx2}, {0, s2, -s2 * my2}, {0, 0, 1}};
        double[][] e = mul(mul(transpose(t2), en), t1);
        return forceEssential(e);
    }

    private static double[][] forceEssential(double[][] e) {
        double[][] u = new double[3][3];
        double[][] vt = new double[3][3];
        double[] s = new double[3];
        if (!svd3(e, u, s, vt)) {
            return null;
        }
        s[0] = 1;
        s[1] = 1;
        s[2] = 0;
        return mul(mul(u, diag(s)), vt);
    }

    private static final class Pose {
        final double[][] r;
        final double[] t;
        final List<double[]> points = new ArrayList<double[]>();
        final List<double[]> pixels = new ArrayList<double[]>();
        double medianNorm;

        Pose(double[][] r, double[] t) {
            this.r = r;
            this.t = t;
        }
    }

    private static Pose bestPose(double[][] e, double[][] n1, double[][] n2) {
        double[][] u = new double[3][3];
        double[][] vt = new double[3][3];
        double[] s = new double[3];
        if (!svd3(e, u, s, vt)) {
            return null;
        }
        if (det(u) < 0) {
            scale(u, -1);
        }
        if (det(transpose(vt)) < 0) {
            scale(vt, -1);
        }
        double[][] w = {{0, -1, 0}, {1, 0, 0}, {0, 0, 1}};
        double[][] v = transpose(vt);
        double[][] r1 = mul(mul(u, w), vt);
        double[][] r2 = mul(mul(u, transpose(w)), vt);
        if (det(r1) < 0) {
            scale(r1, -1);
        }
        if (det(r2) < 0) {
            scale(r2, -1);
        }
        double[] t = {u[0][2], u[1][2], u[2][2]};
        double[] tn = {-t[0], -t[1], -t[2]};
        Pose best = null;
        for (double[][] r : new double[][][]{r1, r2}) {
            for (double[] translation : new double[][]{t, tn}) {
                Pose pose = triangulate(r, translation, n1, n2);
                if (best == null || pose.points.size() > best.points.size()
                        || (pose.points.size() == best.points.size() && pose.medianNorm < best.medianNorm)) {
                    best = pose;
                }
            }
        }
        if (best == null || best.points.size() < 8) {
            return null;
        }
        return best;
    }

    private static Pose triangulate(double[][] r, double[] t, double[][] n1, double[][] n2) {
        Pose pose = new Pose(r, t);
        double[][] p1 = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}};
        double[][] p2 = new double[3][4];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                p2[i][j] = r[i][j];
            }
            p2[i][3] = t[i];
        }
        List<Double> err = new ArrayList<Double>();
        for (int i = 0; i < n1.length; i++) {
            double[] x = linearTri(p1, p2, n1[i], n2[i]);
            if (x == null) {
                continue;
            }
            double z1 = x[2];
            double[] xc = mul(r, x);
            double z2 = xc[2] + t[2];
            if (!(z1 > 0.05) || !(z2 > 0.05)) {
                continue;
            }
            double e1 = Math.hypot(n1[i][0] - x[0] / z1, n1[i][1] - x[1] / z1);
            double e2 = Math.hypot(n2[i][0] - (xc[0] + t[0]) / z2, n2[i][1] - (xc[1] + t[1]) / z2);
            double repro = Math.max(e1, e2);
            if (repro > 0.05) {
                continue;
            }
            pose.points.add(x);
            pose.pixels.add(new double[]{n1[i][0], n1[i][1]});
            err.add(repro);
        }
        if (err.isEmpty()) {
            pose.medianNorm = Double.POSITIVE_INFINITY;
        } else {
            double[] arr = new double[err.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = err.get(i);
            }
            Arrays.sort(arr);
            pose.medianNorm = arr[arr.length / 2];
        }
        return pose;
    }

    private static double[] linearTri(double[][] p1, double[][] p2, double[] a, double[] b) {
        double[][] ata = new double[4][4];
        double[][] rows = {
                row(a[0], a[1], p1, 0),
                row(a[0], a[1], p1, 1),
                row(b[0], b[1], p2, 0),
                row(b[0], b[1], p2, 1)
        };
        for (double[] row : rows) {
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    ata[r][c] += row[r] * row[c];
                }
            }
        }
        double[] eval = new double[4];
        double[][] evec = new double[4][4];
        if (!jacobi(ata, eval, evec)) {
            return null;
        }
        int small = 0;
        for (int i = 1; i < 4; i++) {
            if (Math.abs(eval[i]) < Math.abs(eval[small])) {
                small = i;
            }
        }
        double w = evec[3][small];
        if (Math.abs(w) < 1e-12) {
            return null;
        }
        double[] x = {evec[0][small] / w, evec[1][small] / w, evec[2][small] / w};
        for (double v : x) {
            if (!Double.isFinite(v)) {
                return null;
            }
        }
        return x;
    }

    private static double[] row(double x, double y, double[][] p, int which) {
        // x * row2 - row0, or y * row2 - row1
        double[] out = new double[4];
        int r = which == 0 ? 0 : 1;
        double s = which == 0 ? x : y;
        for (int c = 0; c < 4; c++) {
            out[c] = s * p[2][c] - p[r][c];
        }
        return out;
    }

    private static TriMesh fan(List<double[]> points, List<double[]> pixels) {
        int n = points.size();
        if (n < 4) {
            return null;
        }
        double cx = 0, cy = 0;
        for (double[] p : pixels) {
            cx += p[0];
            cy += p[1];
        }
        cx /= n;
        cy /= n;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final double ox = cx, oy = cy;
        Arrays.sort(order, (i, j) -> {
            double ai = Math.atan2(pixels.get(i)[1] - oy, pixels.get(i)[0] - ox);
            double aj = Math.atan2(pixels.get(j)[1] - oy, pixels.get(j)[0] - ox);
            return Double.compare(ai, aj);
        });
        List<Integer> tri = new ArrayList<Integer>();
        for (int i = 1; i < n - 1; i++) {
            int a = order[0], b = order[i], c = order[i + 1];
            double area = Math.abs(
                    (points.get(b)[0] - points.get(a)[0]) * (points.get(c)[1] - points.get(a)[1])
                            - (points.get(c)[0] - points.get(a)[0]) * (points.get(b)[1] - points.get(a)[1]));
            if (area < 1e-8) {
                continue;
            }
            tri.add(a);
            tri.add(b);
            tri.add(c);
        }
        if (tri.size() < 3) {
            return null;
        }
        float[] pos = new float[n * 3];
        for (int i = 0; i < n; i++) {
            pos[i * 3] = (float) points.get(i)[0];
            pos[i * 3 + 1] = (float) points.get(i)[1];
            pos[i * 3 + 2] = (float) points.get(i)[2];
        }
        int[] ix = new int[tri.size()];
        for (int i = 0; i < ix.length; i++) {
            ix[i] = tri.get(i);
        }
        TriMesh mesh = new TriMesh();
        mesh.pos = pos;
        mesh.tri = ix;
        mesh.computeNormals();
        return mesh;
    }

    private static boolean finite(TriMesh mesh) {
        if (mesh.tri.length < 3 || mesh.tri.length % 3 != 0) {
            return false;
        }
        int n = mesh.pos.length / 3;
        for (float v : mesh.pos) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        for (int t : mesh.tri) {
            if (t < 0 || t >= n) {
                return false;
            }
        }
        return true;
    }

    private static float[] pack(List<double[]> points) {
        float[] out = new float[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            out[i * 3] = (float) points.get(i)[0];
            out[i * 3 + 1] = (float) points.get(i)[1];
            out[i * 3 + 2] = (float) points.get(i)[2];
        }
        return out;
    }

    static boolean jacobi(double[][] aIn, double[] d, double[][] v) {
        int n = aIn.length;
        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = aIn[i][j];
                v[i][j] = i == j ? 1 : 0;
            }
        }
        for (int iter = 0; iter < 60; iter++) {
            int p = 0, q = 1;
            double max = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double value = Math.abs(a[i][j]);
                    if (value > max) {
                        max = value;
                        p = i;
                        q = j;
                    }
                }
            }
            if (max < 1e-14) {
                break;
            }
            double app = a[p][p], aqq = a[q][q], apq = a[p][q];
            double tau = (aqq - app) / (2.0 * apq);
            double t = tau >= 0 ? 1.0 / (tau + Math.sqrt(1.0 + tau * tau)) : -1.0 / (-tau + Math.sqrt(1.0 + tau * tau));
            double c = 1.0 / Math.sqrt(1.0 + t * t);
            double s = t * c;
            a[p][p] = app - t * apq;
            a[q][q] = aqq + t * apq;
            a[p][q] = a[q][p] = 0;
            for (int i = 0; i < n; i++) {
                if (i == p || i == q) {
                    continue;
                }
                double aip = a[i][p], aiq = a[i][q];
                a[i][p] = a[p][i] = c * aip - s * aiq;
                a[i][q] = a[q][i] = s * aip + c * aiq;
            }
            for (int i = 0; i < n; i++) {
                double vip = v[i][p], viq = v[i][q];
                v[i][p] = c * vip - s * viq;
                v[i][q] = s * vip + c * viq;
            }
        }
        for (int i = 0; i < n; i++) {
            d[i] = a[i][i];
            if (!Double.isFinite(d[i])) {
                return false;
            }
        }
        return true;
    }

    /** u columns, vt is V transposed, singular values descending. */
    static boolean svd3(double[][] m, double[][] u, double[] s, double[][] vt) {
        double[][] aat = mul(m, transpose(m));
        double[] eval = new double[3];
        double[][] evec = new double[3][3];
        if (!jacobi(aat, eval, evec)) {
            return false;
        }
        int[] order = {0, 1, 2};
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (eval[order[j]] > eval[order[i]]) {
                    int tmp = order[i];
                    order[i] = order[j];
                    order[j] = tmp;
                }
            }
        }
        for (int c = 0; c < 3; c++) {
            s[c] = Math.sqrt(Math.max(0, eval[order[c]]));
            for (int r = 0; r < 3; r++) {
                u[r][c] = evec[r][order[c]];
            }
        }
        if (det(u) < 0) {
            for (int r = 0; r < 3; r++) {
                u[r][2] = -u[r][2];
            }
        }
        double[][] v = new double[3][3];
        for (int c = 0; c < 2; c++) {
            if (s[c] < 1e-10) {
                return false;
            }
            double[] mt = mul(transpose(m), column(u, c));
            for (int r = 0; r < 3; r++) {
                v[r][c] = mt[r] / s[c];
            }
        }
        double[] c0 = column(v, 0), c1 = column(v, 1);
        double[] c2 = cross(c0, c1);
        for (int r = 0; r < 3; r++) {
            v[r][2] = c2[r];
        }
        if (det(v) < 0) {
            for (int r = 0; r < 3; r++) {
                v[r][2] = -v[r][2];
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                vt[r][c] = v[c][r];
            }
        }
        return true;
    }

    private static double[][] diag(double[] s) {
        return new double[][]{{s[0], 0, 0}, {0, s[1], 0}, {0, 0, s[2]}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        int n = a.length, m = b[0].length, k = b.length;
        double[][] o = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double s = 0;
                for (int t = 0; t < k; t++) {
                    s += a[i][t] * b[t][j];
                }
                o[i][j] = s;
            }
        }
        return o;
    }

    private static double[] mul(double[][] a, double[] x) {
        double[] o = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < x.length; j++) {
                o[i] += a[i][j] * x[j];
            }
        }
        return o;
    }

    private static double[][] transpose(double[][] a) {
        double[][] o = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                o[j][i] = a[i][j];
            }
        }
        return o;
    }

    private static double det(double[][] a) {
        return a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1])
                - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
                + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]);
    }

    private static void scale(double[][] a, double s) {
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                a[i][j] *= s;
            }
        }
    }

    private static double[] column(double[][] a, int c) {
        double[] o = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            o[i] = a[i][c];
        }
        return o;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static String round(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
