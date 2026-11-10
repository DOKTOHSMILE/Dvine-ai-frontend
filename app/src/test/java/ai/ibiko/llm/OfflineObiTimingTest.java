package ai.ibiko.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * How long Obi takes on the phone's own model, as the app asks it (the persona's whole instructions with DVINE's
 * self sheet), and what the reading step in front of it costs. Env-gated like NativeBridgeTest.
 */
public class OfflineObiTimingTest {
    @Test
    public void obiTimings() {
        String lib = System.getenv("DVINE_NATIVE_LIB"), gguf = System.getenv("DVINE_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "-"), true);
        assertTrue(m.load(4));
        DvineSelf.Facts facts = new DvineSelf.Facts();
        facts.version = "0.67.0";
        facts.device = "TECNO Spark";
        facts.userName = "DOKTOHSMILE";
        facts.speaking = "obi";
        String system = PersonaPrompt.system("obi", "DOKTOHSMILE", "Tuesday 3 November 2026", DvineSelf.sheet(facts));
        System.out.println("TIMING obi system: " + system.length() + " chars");
        long t = System.currentTimeMillis();
        m.reply(system, "Hello", 160, null);
        System.out.println("TIMING first hello (reads the instructions): " + (System.currentTimeMillis() - t) + " ms");
        t = System.currentTimeMillis();
        m.reply(system, "Can you write in Java?", 160, null);
        System.out.println("TIMING next message (instructions kept): " + (System.currentTimeMillis() - t) + " ms");
        t = System.currentTimeMillis();
        m.task(ai.ibiko.turn.Meaning.prompt(false), "Check my GitHub repo", 24, null);
        long reading = System.currentTimeMillis() - t;
        t = System.currentTimeMillis();
        m.reply(system, "Check my GitHub repo", 160, null);
        System.out.println("TIMING reading step " + reading + " ms, then the reply (instructions read again): " + (System.currentTimeMillis() - t) + " ms");
    }
}
