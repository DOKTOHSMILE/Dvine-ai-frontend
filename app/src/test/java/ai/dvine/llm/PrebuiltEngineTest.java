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
        File built = newest(new File("build/intermediates/cxx/Release"));
        if (built != null) {
            // after a full build, the shipped copy must be the library the C++ makes now: the same build ID
            // (stripping keeps it). Packaging prefers the shipped copy, so a stale one would reach the phone.
            assertEquals("refresh the ready-built engine: gradle :app:refreshNativeEngine",
                    buildId(Files.readAllBytes(built.toPath())), buildId(head));
        }
    }

    /** The C++ build's newest arm64 library, or null before a full build. */
    private static File newest(File dir) {
        File[] runs = dir.listFiles();
        File best = null;
        if (runs != null) {
            for (File r : runs) {
                File so = new File(r, "obj/arm64-v8a/libdvine_llm.so");
                if (so.isFile() && (best == null || so.lastModified() > best.lastModified())) {
                    best = so;
                }
            }
        }
        return best;
    }

    /** The GNU build ID note's bytes, as hex ("" when there is none). */
    static String buildId(byte[] elf) {
        for (int i = 0; i + 16 < elf.length; i++) {
            // note header: name size 4, descriptor size, type 3 (NT_GNU_BUILD_ID), then "GNU\0"
            if (elf[i] == 4 && elf[i + 1] == 0 && elf[i + 2] == 0 && elf[i + 3] == 0 && elf[i + 8] == 3 && elf[i + 9] == 0
                    && elf[i + 10] == 0 && elf[i + 11] == 0 && elf[i + 12] == 'G' && elf[i + 13] == 'N' && elf[i + 14] == 'U' && elf[i + 15] == 0) {
                int n = (elf[i + 4] & 0xff) | (elf[i + 5] & 0xff) << 8;
                if (n < 4 || n > 64 || i + 16 + n > elf.length) {
                    continue;
                }
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < n; k++) {
                    b.append(String.format("%02x", elf[i + 16 + k] & 0xff));
                }
                return b.toString();
            }
        }
        return "";
    }
}
