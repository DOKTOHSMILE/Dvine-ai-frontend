package ai.dvine.ops.recon;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.create.Component;
import ai.dvine.create.ObjectModel;
import ai.dvine.create.ObjectSpec;
import ai.dvine.create.Provenance;

/**
 * Turns picked photographs into the sparse solver. A sentence with no pixels
 * never reaches this. The mesh is an open triangulation, not a closed scan.
 */
public final class PhotoReconstruction {
    public static final class Built {
        public final SparsePhotogrammetry.Result result;
        public final ObjectModel model;
        public final String speech;

        Built(SparsePhotogrammetry.Result result, ObjectModel model, String speech) {
            this.result = result;
            this.model = model;
            this.speech = speech;
        }
    }

    private PhotoReconstruction() {
    }

    public static Built from(List<RasterImage> images) {
        if (images == null || images.isEmpty()) {
            return new Built(null, null, "I didn't get any photographs, so nothing was triangulated.");
        }
        List<RasterImage> ready = new ArrayList<RasterImage>();
        for (RasterImage image : images) {
            if (image != null) {
                ready.add(image.limit(480));
            }
        }
        if (ready.isEmpty()) {
            return new Built(null, null, "I didn't get any photographs, so nothing was triangulated.");
        }
        if (ready.size() == 1) {
            SparsePhotogrammetry.Result one = SparsePhotogrammetry.reconstruct(ready, null, null);
            return new Built(one, null, one.note + " I did not build a mesh.");
        }
        int w = ready.get(0).width, h = ready.get(0).height;
        boolean scaled = false;
        for (int i = 0; i < ready.size(); i++) {
            RasterImage image = ready.get(i);
            if (image.width != w || image.height != h) {
                ready.set(i, image.scaled(w, h));
                scaled = true;
            }
        }
        SparsePhotogrammetry.Result result = search(ready);
        String note = result.note + " The focal length was searched, not read from the photograph."
                + (scaled ? " The views were scaled to the same size." : "");
        if (!result.measured || result.mesh == null || !result.meshValidated) {
            return new Built(result, null, note + " I did not put a mesh on the board.");
        }
        Component part = new Component(0, "sparse surface", "cloud", "metal", 0, result.mesh);
        List<Component> parts = new ArrayList<Component>();
        parts.add(part);
        List<Provenance> prov = new ArrayList<Provenance>();
        prov.add(new Provenance("reconstruction", result.method, System.currentTimeMillis(), "user photographs", note));
        ObjectModel model = new ObjectModel(ObjectSpec.of("reconstruction"), 1, "sparse reconstruction", parts, prov);
        model.problems.add("open triangulation of sparse points, not a closed scan");
        return new Built(result, model, note
                + " I put that open triangulation on the board. It is not a closed scan and not COLMAP. Say undo to bring back what was showing. I will not save it as a procedural model.");
    }

    /** Tries a short range of focal lengths and keeps the tightest measured solve. */
    private static SparsePhotogrammetry.Result search(List<RasterImage> images) {
        int w = images.get(0).width, h = images.get(0).height;
        SparsePhotogrammetry.Result best = null;
        for (double scale = 0.7; scale <= 1.61; scale += 0.1) {
            double[] k = {w * scale, (w - 1) / 2.0, (h - 1) / 2.0};
            SparsePhotogrammetry.Result result = SparsePhotogrammetry.reconstruct(images, k, null);
            if (better(result, best)) {
                best = result;
            }
        }
        return best;
    }

    private static boolean better(SparsePhotogrammetry.Result next, SparsePhotogrammetry.Result best) {
        if (best == null) {
            return true;
        }
        if (next.measured != best.measured) {
            return next.measured;
        }
        if (Double.isFinite(next.medianReprojectionPx) && Double.isFinite(best.medianReprojectionPx)
                && next.medianReprojectionPx != best.medianReprojectionPx) {
            return next.medianReprojectionPx < best.medianReprojectionPx;
        }
        return next.sparsePointCount > best.sparsePointCount;
    }
}
