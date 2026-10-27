package ai.dvine.human.wrap;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.Test;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.HumanInstance;

/** Writes a body, its wrap and its triangles for an off-device look (tools: python render). Runs with WRAP_OUT set. */
public class MarksDump {
    @Test
    public void dump() throws Exception {
        String out = System.getenv("WRAP_OUT");
        if (out == null) return;
        for (String who : new String[]{"obi", "ivie"}) {
            HumanAsset a;
            try (FileInputStream in = new FileInputStream("src/main/assets/human/" + who + ".dvhm")) { a = HumanAsset.read(in); }
            HumanInstance h = new HumanInstance(a);
            h.update(0f);
            SheetWrap w = SheetWrap.parse(new String(Files.readAllBytes(Paths.get("src/main/assets/looks/" + who + ".json")), StandardCharsets.UTF_8));
            SheetWrap.Result r = w.compute(h);
            try (DataOutputStream o = new DataOutputStream(new FileOutputStream(out + "/" + who + ".bin"))) {
                int n = h.pos.length / 3;
                o.writeInt(n);
                for (int i = 0; i < n * 3; i++) o.writeFloat(h.pos[i]);
                for (int i = 0; i < n * 3; i++) o.writeFloat(h.nor[i]);
                for (float f : r.uv) o.writeFloat(f);
                for (float f : r.weight) o.writeFloat(f);
                int[] tri = a.mesh.tri;
                int solid = h.solidCount();
                int k = 0;
                for (int t = 0; t + 2 < tri.length; t += 3) if (tri[t] < solid && tri[t + 1] < solid && tri[t + 2] < solid) k++;
                o.writeInt(k);
                for (int t = 0; t + 2 < tri.length; t += 3) if (tri[t] < solid && tri[t + 1] < solid && tri[t + 2] < solid) { o.writeInt(tri[t]); o.writeInt(tri[t + 1]); o.writeInt(tri[t + 2]); }
            }
        }
    }
}
