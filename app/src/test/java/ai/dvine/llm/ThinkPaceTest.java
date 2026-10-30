package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThinkPaceTest {
    @Test
    public void theLineShowsTimeAndOnlyFinishedWords() {
        ThinkPace pace = new ThinkPace("Reading your words", 1_000);
        pace.tick(1_000);
        assertEquals("Reading your words · 0s", pace.line());
        pace.tick(3_400);
        assertEquals("Reading your words · 2s", pace.line());
        pace.addText("\u0001");
        assertFalse(pace.line().contains("· 1 word"));
        pace.phase("Writing the answer");
        pace.addText("Hel");
        assertFalse(pace.line().contains("· 1 word"));
        pace.addText("lo there. ");
        assertTrue(pace.line().contains("2 words"));
        assertTrue(pace.line().startsWith("Writing the answer · 2s"));
    }
}
