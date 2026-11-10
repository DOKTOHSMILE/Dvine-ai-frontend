package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ai.ibiko.suit.ChatTools;
import ai.ibiko.suit.FunctionCode;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Why a small model on the device took so long to start: hidden thinking on every call, and about 2,000 tokens of
 * instructions read before the first word. Both are cut for the device's own model.
 */
public class DeviceSpeedTest {
    @Test
    public void theDevicesThinkingModelNeverThinksInHidingUnlessAsked() throws Exception {
        LocalModel m = new LocalModel(Files.createTempDirectory("m").toFile(), ModelSpec.QWEN3_0_6B, true);
        assertTrue(m.deviceSystem("You write social posts.").endsWith(" /no_think"));
        assertEquals("already said once", "x /no_think", m.deviceSystem("x /no_think"));
        m.localThinking = true;
        assertEquals("You write social posts.", m.deviceSystem("You write social posts."));
        LocalModel gemma = new LocalModel(Files.createTempDirectory("g").toFile(), ModelSpec.GEMMA_3_1B, true);
        assertEquals("a model that doesn't think is left alone", "x", gemma.deviceSystem("x"));
    }

    @Test
    public void theDevicesInstructionsAreAFractionOfTheFullOnes() {
        DvineSelf.Facts f = new DvineSelf.Facts();
        ChatTools t = ChatTools.defaults("ibiko");
        String full = PersonaPrompt.system("ibiko", "Val", "Tuesday 10 November 2026", DvineSelf.sheet(f))
                + FunctionCode.instructions("", t.allowedCodes());
        String lean = PersonaPrompt.lean("Val", "Tuesday 10 November 2026") + FunctionCode.lean("", t.allowedCodes());
        assertTrue(lean.length() + " against " + full.length(), lean.length() * 3 < full.length());
        assertTrue(lean.contains("/no_think") && lean.contains("Val") && lean.contains("10 November 2026"));
        for (FunctionCode c : t.allowedCodes()) {
            if (c != FunctionCode.TL) {
                assertTrue("every code the chat allows is still there: " + c, lean.contains(c.code()));
            }
        }
    }
}
