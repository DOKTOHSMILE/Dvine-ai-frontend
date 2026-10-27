package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/**
 * The brain's engine ships ready-built (src/main/jniLibs), so an APK built without the C++ toolchain still has it:
 * a 64-bit ARM shared library, the same one the full build makes.
 */
public class PrebuiltEngineTest {
    @Test
    public void theReadyBuiltEngineIsThere() throws Exception {
        File so = new File("src/main/jniLibs/arm64-v8a/libdvine_llm.so");
        assertTrue("ready-built engine missing", so.isFile() && so.length() > 1_000_000);
        byte[] head = Files.readAllBytes(so.toPath());
        assertEquals(0x7f, head[0] & 0xff); // ELF
        assertEquals('E', head[1]);
        assertEquals(2, head[4]);           // 64-bit
        assertEquals(183, (head[18] & 0xff) | (head[19] & 0xff) << 8); // EM_AARCH64
        File built = new File("build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a/libdvine_llm.so");
        if (built.isFile()) {
            // after a full build, the shipped copy must be the library the C++ makes now (refreshNativeEngine)
            assertTrue("refresh the ready-built engine: gradle :app:refreshNativeEngine",
                    java.util.Arrays.equals(Files.readAllBytes(built.toPath()), head));
        }
    }
}
