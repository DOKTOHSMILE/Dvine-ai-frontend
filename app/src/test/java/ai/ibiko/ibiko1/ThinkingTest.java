package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Thinking;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Ibiko 1 learns how the other brains think (the moves of their reasoning), and speaks like a professional. */
public class ThinkingTest {
    static final String PLAN = "The user is asking which country is bigger. First, I recall the areas I know. Then compare"
            + " them side by side. Let me double-check the figures. So the answer is Nigeria.";

    @Test
    public void theMovesOfReasoningAreRead() {
        List<Thinking.Move> m = Thinking.moves(PLAN);
        assertEquals(Arrays.asList(Thinking.Move.UNDERSTAND, Thinking.Move.RECALL, Thinking.Move.PLAN, Thinking.Move.CHECK,
                Thinking.Move.CONCLUDE).subList(0, 2), m.subList(0, 2));
        assertTrue(m.contains(Thinking.Move.CHECK));
        assertEquals(Thinking.Move.CONCLUDE, m.get(m.size() - 1));
    }

    @Test
    public void aWaySeenTwiceIsFollowedAndKept() throws Exception {
        File f = new File(Files.createTempDirectory("brain").toFile(), "thinking.tsv");
        Thinking t = new Thinking(f);
        List<String> own = Arrays.asList("Reading your question", "Found it in my built-in knowledge");
        assertNull("once is not yet a habit", t.learn("other:which is", PLAN) ? t.pattern("other:which is") : null);
        t.learn("other:which is", PLAN);
        List<String> steps = t.steps("other:which is", own);
        assertEquals("Reading your question", steps.get(0));
        assertTrue(steps.toString(), steps.contains("Recalling what I know about it"));
        assertTrue(steps.contains("Checking it holds"));
        assertEquals("its own last step stays last", "Found it in my built-in knowledge", steps.get(steps.size() - 1));
        assertTrue("kept on the device", new Thinking(f).pattern("other:which is") != null);
    }

    @Test
    public void notTheSameFixedReplyTwice() {
        String a = Phrasing.unknown("quantum dots", true, false, 0);
        String b = Phrasing.unknown("quantum dots", true, false, 1);
        assertNotEquals(a, b);
        assertTrue(a.contains("“quantum dots”"));
        assertTrue(Phrasing.unknown("quantum dots", false, true, 0).contains("offline"));
        assertEquals("Same answer as before: Accra.", Phrasing.again("Accra.", 2));
        assertEquals("Accra.", Phrasing.again("Accra.", 0));
    }
}
