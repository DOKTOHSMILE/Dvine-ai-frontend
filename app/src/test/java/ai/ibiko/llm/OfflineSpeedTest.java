package ai.ibiko.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * The speed test the app runs (Settings › Test the speed), on a desktop build of the same native engine with a real
 * model file. Runs only with IBIKO_NATIVE_LIB and IBIKO_MODEL set. A desktop CPU is several times a phone's.
 */
public class OfflineSpeedTest {
    @Test
    public void measuresTheModel() {
        String lib = System.getenv("IBIKO_NATIVE_LIB"), gguf = System.getenv("IBIKO_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "-"), true);
        m.context(Integer.parseInt(System.getenv().getOrDefault("IBIKO_CONTEXT", "8192")));
        int threads = Integer.parseInt(System.getenv().getOrDefault("IBIKO_THREADS", "4"));
        LocalModel.Speed first = m.speedTest(threads, 96), again = m.speedTest(threads, 96);
        System.out.println("SPEED " + f.getName() + " threads " + threads + "\nSPEED cold: " + first.words() + "\nSPEED warm: " + again.words());
        assertTrue(again.pieces > 10 && again.writing() > 0);
    }
}
