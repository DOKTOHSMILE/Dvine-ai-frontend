package ai.dvine.human.bake;

import java.io.File;
import java.io.FileOutputStream;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.Regions;
import ai.dvine.human.mesh.MeshValidator;
import ai.dvine.human.rig.Skeleton;

/**
 * Builds the shipped male (Obi) and female (Ivie) assets into a directory:
 * {@code gradle :app:generateHumanAssets} writes app/src/main/assets/human/.
 * Each asset is validated and read back (checksum) before it is written.
 */
public final class AssetTool {
    private AssetTool() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "app/src/main/assets/human");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }
        for (boolean female : new boolean[]{false, true}) {
            long t0 = System.nanoTime();
            HumanAsset a = AssetBuilder.build(female);
            MeshValidator.Report r = MeshValidator.check(a.mesh, true, Skeleton.COUNT, Regions.COUNT);
            if (!r.ok()) {
                throw new IllegalStateException((female ? "ivie" : "obi") + ": " + r);
            }
            byte[] bytes = a.write();
            HumanAsset.read(new java.io.ByteArrayInputStream(bytes)); // proves the file reads back
            File out = new File(dir, female ? "ivie.dvhm" : "obi.dvhm");
            try (FileOutputStream o = new FileOutputStream(out)) {
                o.write(bytes);
            }
            System.out.printf("%s: %d vertices, %d triangles, %d edges, %d feature lines, %d blend shapes, %d bytes, %.1f s%n",
                    out.getName(), a.mesh.vertexCount(), a.mesh.triangleCount(), a.mesh.edgeCount(), a.lines.length / 2,
                    a.morphs.length, bytes.length, (System.nanoTime() - t0) / 1e9);
        }
    }
}
