package ai.ibiko.create;

import java.io.File;

import org.junit.Test;

import ai.ibiko.llm.LocalModel;
import ai.ibiko.llm.ModelSpec;

/** The phone's model designs a thing with its measurements and Ibiko builds it (desktop engine). Runs with IBIKO_NATIVE_LIB and IBIKO_MODEL. */
public class OfflineDesignTest {
    @Test
    public void aTwoStoreyBuildingWithA20FootRoof() throws Exception {
        String lib = System.getenv("IBIKO_NATIVE_LIB"), gguf = System.getenv("IBIKO_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        java.lang.reflect.Method load = ai.ibiko.llm.NativeLlm.class.getDeclaredMethod("loadFrom", String.class);
        load.setAccessible(true);
        org.junit.Assert.assertTrue((Boolean) load.invoke(null, lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "-"), true);
        m.context(8192);
        org.junit.Assert.assertTrue(m.load(4));
        long t0 = System.currentTimeMillis();
        MeasuredDesign.Designed d = MeasuredDesign.design((s, p, n) -> m.task(s, p, n, null),
                "Create a 3D two-storey building with a 20 foot roof", null, 1500);
        System.out.println("DESIGN took " + (System.currentTimeMillis() - t0) / 1000 + " s");
        System.out.println("DESIGN brief:\n" + (d == null ? null : d.brief));
        System.out.println("DESIGN lines:\n" + (d == null ? null : d.lines));
        if (d != null) {
            System.out.println("DESIGN errors " + d.read.errors + " warnings " + d.read.warnings);
            ObjectModel built = MeasuredBuilder.build(ObjectSpec.of("building").with("design", d.lines, false), 1, new java.util.ArrayList<Provenance>());
            System.out.println("DESIGN valid " + built.valid() + " problems " + built.problems + " parts " + built.components.size());
            System.out.println("DESIGN summary " + MeasuredDesign.summary(d.read, true));
        }
    }
}
