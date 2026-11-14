package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Similar models are picked from a server list; a mini is not paired with an opus. */
public class ModelRingTest {
    @Test
    public void aFlagshipGetsOtherFlagshipsAndAMiniGetsMinis() {
        List<String> offered = Arrays.asList("gpt-5", "gpt-5-mini", "claude-opus-5-5", "claude-haiku-5-5",
                "gemini-2.5-pro", "gemini-2.5-flash");
        List<String> large = ModelRing.choose("gpt-5", offered);
        assertEquals("gpt-5", large.get(0));
        assertEquals(3, large.size());
        assertTrue(large.contains("claude-opus-5-5"));
        assertTrue(large.contains("gemini-2.5-pro"));
        assertTrue(!large.contains("gpt-5-mini") && !large.contains("claude-haiku-5-5"));

        List<String> small = ModelRing.choose("gpt-5-mini", offered);
        assertEquals("gpt-5-mini", small.get(0));
        assertTrue(small.contains("claude-haiku-5-5"));
        assertTrue(!small.contains("claude-opus-5-5") && !small.contains("gemini-2.5-pro"));
    }

    @Test
    public void oneModelStillThinksAndThePassesStayInOrder() {
        List<String> asked = new java.util.ArrayList<String>();
        String answer = RoundThink.run((model, system, prompt) -> {
            asked.add(model + ":" + (system.startsWith("You are the first") ? "analyse"
                    : system.startsWith("You are the second") ? "solve"
                    : system.startsWith("You are another") ? "angle"
                    : system.startsWith("You already") ? "higher" : "merge"));
            return "merge".equals(asked.get(asked.size() - 1).substring(asked.get(asked.size() - 1).indexOf(':') + 1))
                    ? "The stronger answer." : "A note.";
        }, Arrays.asList("gpt-5", "claude-opus-5-5"), "How should a small shop take payments?", "", "You are Ibiko.",
                line -> { }, () -> false);
        assertEquals("The stronger answer.", answer);
        assertEquals(Arrays.asList("gpt-5:analyse", "claude-opus-5-5:solve", "gpt-5:angle", "gpt-5:higher",
                "claude-opus-5-5:higher", "gpt-5:merge"), asked);
        assertTrue(RoundThink.worth("How should a small shop take payments?"));
        assertTrue(!RoundThink.worth("hi"));
    }
}
