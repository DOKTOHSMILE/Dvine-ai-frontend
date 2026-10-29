package ai.dvine.create;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import ai.dvine.ops.recon.RasterImage;
import ai.dvine.ops.recon.SparsePhotogrammetry;
import ai.dvine.ops.task.TaskControl;

/** Drawing model, sparse photogrammetry, the body rig, and the part tree. */
public class StudioTest {
    @Test
    public void aPictureIsPaintedAndIsNotAPhotograph() {
        ObjectSession session = new ObjectSession();
        CreationDirector director = new CreationDirector(session, new java.io.File(System.getProperty("java.io.tmpdir")));
        CreationDirector.Outcome drawn = director.perform(ObjectRequestInterpreter.interpret("show me a picture of a red motorcycle", null));
        assertNotNull(drawn.picture);
        assertEquals(DrawingModel.METHOD, drawn.picture.method);
        assertFalse(drawn.picture.photograph);
        assertFalse(drawn.picture.diffusion);
        assertEquals("motorcycle", drawn.picture.subject);
        assertTrue(drawn.lines.get(0).toLowerCase().contains("not a photograph"));
        assertTrue(drawn.lines.get(0).toLowerCase().contains("not a diffusion"));
        assertEquals(null, session.model());
        assertEquals((byte) 137, drawn.picture.png[0]);
        assertEquals((byte) 80, drawn.picture.png[1]);
        int red = 0;
        for (int c : drawn.picture.argb) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (r > 160 && g < 90 && b < 90) {
                red++;
            }
        }
        assertTrue(red > 40);
    }

    @Test
    public void twoOverlappingViewsTriangulateAndACopyDoesNot() {
        double f = 520, cx = 239.5, cy = 179.5;
        double[][] world = new double[12][3];
        int n = 0;
        for (int ix = 0; ix < 4; ix++) {
            for (int iy = 0; iy < 3; iy++) {
                world[n][0] = -0.9 + ix * 0.6;
                world[n][1] = -0.45 + iy * 0.45;
                world[n][2] = 4.4 + ((ix * 3 + iy) % 4) * 0.35;
                n++;
            }
        }
        RasterImage left = project(world, f, cx, cy, 0, 0, 0);
        RasterImage right = project(world, f, cx, cy, Math.toRadians(14), -0.32, 0.02);
        SparsePhotogrammetry.Result ok = SparsePhotogrammetry.reconstruct(List.of(left, right), new double[]{f, cx, cy}, null);
        assertEquals(ok.note, SparsePhotogrammetry.Quality.SPARSE_MULTI_VIEW, ok.quality);
        assertTrue(ok.measured);
        assertEquals(2, ok.cameraCount);
        assertEquals(0, ok.densePointCount);
        assertTrue(ok.sparsePointCount >= 8);
        assertTrue(ok.matchCount >= 8);
        assertTrue(ok.meshValidated);
        assertNotNull(ok.mesh);
        assertTrue("reprojection " + ok.medianReprojectionPx, ok.medianReprojectionPx < 8.0);
        assertFalse(ok.intrinsicsAssumed);
        assertTrue(ok.note.toLowerCase().contains("not colmap"));
        assertTrue("shape " + shapeError(world, ok.points), shapeError(world, ok.points) < 0.2);

        SparsePhotogrammetry.Result same = SparsePhotogrammetry.reconstruct(List.of(left, left), new double[]{f, cx, cy}, null);
        assertFalse(same.measured);
        assertEquals(0, same.cameraCount);

        SparsePhotogrammetry.Result one = SparsePhotogrammetry.reconstruct(List.of(left), new double[]{f, cx, cy}, null);
        assertEquals(SparsePhotogrammetry.Quality.SINGLE_VIEW, one.quality);
        assertFalse(one.measured);
        assertEquals(0, one.sparsePointCount);

        TaskControl stop = new TaskControl();
        stop.cancel();
        SparsePhotogrammetry.Result cancelled = SparsePhotogrammetry.reconstruct(List.of(left, right), new double[]{f, cx, cy}, stop);
        assertEquals(SparsePhotogrammetry.Quality.CANCELLED, cancelled.quality);
        assertFalse(cancelled.measured);
    }

    @Test
    public void raisingTheLeftArmMovesTheForearmAndUndoPutsItBack() {
        ObjectSession session = new ObjectSession();
        CreationDirector director = new CreationDirector(session, new java.io.File(System.getProperty("java.io.tmpdir")));
        CreationDirector.Outcome built = director.perform(ObjectRequestInterpreter.interpret("build a person", null));
        assertEquals(CreationDirector.Action.BECOME, built.action);
        ObjectModel m = session.model();
        assertNotNull(m);
        assertTrue(m.problems.toString(), m.valid());
        assertTrue(BodyRig.shown(m));
        Component arm = part(m, "left forearm");
        float restY = SceneCommands.centroid(arm)[1];
        float hipY = SceneCommands.centroid(part(m, "hips"))[1];
        for (int i = 0; i < arm.mesh.weight0.length; i++) {
            assertEquals(1f, arm.mesh.weight0[i], 0f);
        }
        CreationDirector.Outcome posed = director.perform(ObjectRequestInterpreter.interpret("raise the left arm", m.spec));
        assertEquals(CreationDirector.Action.CHANGED, posed.action);
        assertTrue(posed.lines.get(0).toLowerCase().contains("not a scanned"));
        assertTrue(SceneCommands.centroid(arm)[1] > restY + 0.08f);
        assertEquals(hipY, SceneCommands.centroid(part(m, "hips"))[1], 0.02f);
        director.perform(ObjectRequestInterpreter.interpret("undo", m.spec));
        assertEquals(restY, SceneCommands.centroid(arm)[1], 0.02f);
    }

    @Test
    public void thePartTreeCollapsesAndATapNamesTheFrontTyre() {
        ObjectModel m = ProceduralModelingEngine.build(ObjectRequestInterpreter.defaults("motorcycle"), 1, new ArrayList<Provenance>());
        Component tyre = part(m, "front tyre");
        PartTree open = PartTree.layout(m, -1, new HashSet<String>(), 10, 20, 160, 18);
        PartTree.Row tyreRow = null;
        for (PartTree.Row row : open.rows) {
            if (row.componentId == tyre.id) {
                tyreRow = row;
            }
        }
        assertNotNull(tyreRow);
        assertEquals(tyre.id, open.hit(tyreRow.x + 4, tyreRow.y + 4));
        assertTrue(open.text().contains("front tyre"));
        HashSet<String> shut = new HashSet<String>();
        shut.add("wheel-front");
        PartTree closed = PartTree.layout(m, tyre.id, shut, 10, 20, 160, 18);
        for (PartTree.Row row : closed.rows) {
            assertFalse(row.componentId == tyre.id);
        }
        assertTrue(closed.text().contains("> wheel-front"));
    }

    /** Median nearest-neighbour error after scaling both clouds to the same size. */
    private static double shapeError(double[][] world, float[] got) {
        int n = got.length / 3;
        if (n < 8) {
            return 99;
        }
        double[][] est = new double[n][3];
        for (int i = 0; i < n; i++) {
            est[i][0] = got[i * 3];
            est[i][1] = got[i * 3 + 1];
            est[i][2] = got[i * 3 + 2];
        }
        double scale = medianNorm(est) / Math.max(1e-6, medianNorm(world));
        double[] err = new double[n];
        for (int i = 0; i < n; i++) {
            double best = 1e9;
            for (double[] w : world) {
                double dx = est[i][0] - w[0] * scale;
                double dy = est[i][1] - w[1] * scale;
                double dz = est[i][2] - w[2] * scale;
                best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            double flipped = 1e9;
            for (double[] w : world) {
                double dx = est[i][0] + w[0] * scale;
                double dy = est[i][1] - w[1] * scale;
                double dz = est[i][2] - w[2] * scale;
                flipped = Math.min(flipped, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            err[i] = Math.min(best, flipped);
        }
        java.util.Arrays.sort(err);
        return err[err.length / 2] / Math.max(1e-6, medianNorm(est));
    }

    private static double medianNorm(double[][] p) {
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            v[i] = Math.sqrt(p[i][0] * p[i][0] + p[i][1] * p[i][1] + p[i][2] * p[i][2]);
        }
        java.util.Arrays.sort(v);
        return v[v.length / 2];
    }

    private static double[] distances(double[][] p) {
        List<Double> d = new ArrayList<Double>();
        for (int i = 0; i < p.length; i++) {
            for (int j = i + 1; j < p.length; j++) {
                d.add(Math.hypot(p[i][0] - p[j][0], Math.hypot(p[i][1] - p[j][1], p[i][2] - p[j][2])));
            }
        }
        double[] a = new double[d.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = d.get(i);
        }
        return a;
    }

    private static double[] distances(float[] p) {
        int n = p.length / 3;
        List<Double> d = new ArrayList<Double>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = p[i * 3] - p[j * 3], dy = p[i * 3 + 1] - p[j * 3 + 1], dz = p[i * 3 + 2] - p[j * 3 + 2];
                d.add(Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        double[] a = new double[d.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = d.get(i);
        }
        return a;
    }

    private static RasterImage project(double[][] world, double f, double cx, double cy, double yaw, double tx, double tz) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        RasterImage image = RasterImage.fill(480, 360, 0xFF05070A);
        for (int i = 0; i < world.length; i++) {
            double x = world[i][0], y = world[i][1], z = world[i][2];
            double xr = c * x + s * z + tx;
            double yr = y;
            double zr = -s * x + c * z + tz;
            int u = (int) Math.round(f * xr / zr + cx);
            int v = (int) Math.round(cy - f * yr / zr);
            int r = 40 + (i * 37) % 200;
            int g = 30 + (i * 67) % 180;
            int b = 50 + (i * 91) % 160;
            image.dot(u, v, 6, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
        return image;
    }

    private static Component part(ObjectModel m, String name) {
        for (Component c : m.components) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        throw new AssertionError(name);
    }
}
