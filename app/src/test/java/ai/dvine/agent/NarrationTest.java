package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Long work is never silent: each step says what it looked for and what it found. */
public class NarrationTest {
    @Test
    public void eachStepSaysWhatItFound() {
        assertEquals("Read PLAN.md (3 lines). It starts: # DVINE plan", Narration.of("Read PLAN.md", "# DVINE plan\n\nStep one"));
        assertEquals("Searched for “night”: 2 matches, first app/Learn.java:12.",
                Narration.of("Searched for “night”", "app/Learn.java:12\napp/Job.java:40"));
        assertEquals("Searched for “x”: nothing found.", Narration.of("Searched for “x”", ""));
        assertEquals("Pulling Dvine-ai-frontend: Already up to date.", Narration.of("Pulling Dvine-ai-frontend", "Already up to date."));
        assertTrue(Narration.of("Checked the status", "Error: not a git repository").startsWith("Checked the status. It didn't work: not a git"));
        assertEquals("Edited app/Main.java.", Narration.of("Edited app/Main.java", "ok"));
        assertNull(Narration.of(null, "x"));
    }
}
