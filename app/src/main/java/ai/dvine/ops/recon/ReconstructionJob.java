package ai.dvine.ops.recon;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bytes alone are not views. Sparse triangulation runs only on {@link RasterImage} pixels.
 */
public final class ReconstructionJob {
    public enum State { EMPTY, UNSUPPORTED }

    public final String jobId;
    public final int imageCount;
    public final List<String> checksums;
    public final State state;
    public final boolean measured;
    public final String note;

    private ReconstructionJob(String jobId, int imageCount, List<String> checksums, State state, String note) {
        this.jobId = jobId;
        this.imageCount = imageCount;
        this.checksums = checksums;
        this.state = state;
        this.measured = false;
        this.note = note;
    }

    public static ReconstructionJob fromImages(List<byte[]> images) {
        String id = UUID.randomUUID().toString();
        if (images == null || images.isEmpty()) {
            return new ReconstructionJob(id, 0, List.of(), State.EMPTY, "No images, so nothing was reconstructed.");
        }
        List<String> sums = new ArrayList<String>();
        int empty = 0;
        for (byte[] image : images) {
            if (image == null || image.length == 0) {
                empty++;
                sums.add("");
            } else {
                sums.add(sha256(image));
            }
        }
        int same = 0;
        for (int i = 0; i < sums.size(); i++) {
            if (sums.get(i).isEmpty()) {
                continue;
            }
            for (int j = 0; j < i; j++) {
                if (sums.get(i).equals(sums.get(j))) {
                    same++;
                    break;
                }
            }
        }
        String note = images.size() + (images.size() == 1 ? " image" : " images")
                + ". These are file bytes, not decoded views, so the solver did not run. No camera poses, no point cloud, and no mesh were produced. This is not measured.";
        if (empty > 0) {
            note += " " + empty + " of them had no bytes.";
        }
        if (same > 0) {
            note += " Some files are the same.";
        }
        return new ReconstructionJob(id, images.size(), List.copyOf(sums), State.UNSUPPORTED, note);
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder b = new StringBuilder();
            for (byte v : dig) {
                b.append(String.format("%02x", v & 0xff));
            }
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
