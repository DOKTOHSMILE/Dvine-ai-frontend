package ai.dvine.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * The bridge to the phone's model, measured on the desktop build: how long a long prompt (a persona's instructions and
 * a chat) takes to read, how fast the reply is written, and how much quicker the next reply starts when the
 * instructions were already read (warmed). Runs with DVINE_NATIVE_LIB and DVINE_MODEL.
 */
public class OfflineBridgeBenchTest {
    @Test
    public void readingAndWriting() {
        String lib = System.getenv("DVINE_NATIVE_LIB"), gguf = System.getenv("DVINE_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        StringBuilder sys = new StringBuilder(PersonaPrompt.system("obi", "Smile", "Tuesday 3 November 2026", ""));
        for (int i = 0; i < 12; i++) {
            sys.append(" Note ").append(i).append(": the user runs a small shop in Lagos selling phones and accessories,"
                    + " and asks about prices, suppliers, stock, customers and how to grow the business online.");
        }
        String system = sys.toString() + " /no_think";
        long t0 = System.currentTimeMillis();
        long h = NativeLlm.nLoad(f.getPath(), 4096, 4);
        long load = System.currentTimeMillis() - t0;
        assertTrue(h != 0);
        long w0 = System.currentTimeMillis();
        boolean warmed = NativeLlm.warmIfPossible(h, system);
        long warm = System.currentTimeMillis() - w0;
        // as in the app: a short routing call with its own instructions, then the persona's answer, question after question
        String router = BrainOrder.prompt("") + " /no_think";
        String[][] calls = {{router, "How should I price a used iPhone 12 in Lagos?"}, {system, "How should I price a used iPhone 12 in Lagos?"},
                {router, "And a Tecno Camon 20?"}, {system, "And a Tecno Camon 20?"}};
        for (int round = 0; round < calls.length; round++) {
            final long[] first = {-1};
            final int[] n = {0};
            final long s = System.currentTimeMillis();
            NativeLlm.nReply(h, calls[round][0], new String[]{"user"}, new String[]{calls[round][1]}, round % 2 == 0 ? 16 : 48, 0.7f, text -> {
                if (first[0] < 0) {
                    first[0] = System.currentTimeMillis() - s;
                }
                n[0]++;
                return true;
            });
            long total = System.currentTimeMillis() - s;
            System.out.println("BENCH " + (round % 2 == 0 ? "route " : "answer") + " " + round + (warmed ? " (warmed " + warm + " ms)" : "")
                    + ": load " + load + " ms, first word " + first[0] + " ms, " + n[0] + " pieces, writing "
                    + String.format(java.util.Locale.US, "%.1f", (n[0] - 1) * 1000f / Math.max(1, total - first[0])) + "/s");
        }
        NativeLlm.nFree(h);
    }
}
