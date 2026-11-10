package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One call answers and may act: a code on the reply's first line is DVINE's, the rest is the user's answer. */
public class OneCallOrderTest {
    @Test
    public void theBrainsCodeIsTheOrder() {
        BrainOrder o = BrainOrder.leading("§SR weather in Lagos\nLooking it up now.");
        assertEquals(BrainOrder.Kind.SEARCH, o.kind);
        assertEquals("weather in Lagos", o.arg);
        assertEquals("Looking it up now.", BrainOrder.withoutOrder("§SR weather in Lagos\nLooking it up now."));
        assertEquals(BrainOrder.Kind.NEWCHAT, BrainOrder.leading("[[NC]]").kind);
        assertEquals("ibiko", BrainOrder.leading("§SW Ibiko").arg);
        assertEquals("pdf", BrainOrder.leading("§MF").arg);
    }

    @Test
    public void wordsAreNeverOrders() {
        assertNull("the old word orders are not codes", BrainOrder.leading("DO SEARCH weather"));
        assertNull(BrainOrder.leading("stop"));
        assertNull(BrainOrder.leading("Start a new chat please"));
        // a code under a first sentence is still the brain's order (and is never shown), but not one buried deep in an answer
        assertEquals(BrainOrder.Kind.SEARCH, BrainOrder.leading("Let me check.\n§SR Nairobi").kind);
        assertNull(BrainOrder.leading("One.\nTwo.\nThree.\nFour.\nFive.\n§SR Nairobi"));
        assertNull("a code that needs its words, without them", BrainOrder.leading("§SR"));
        assertEquals("The capital is Nairobi.", BrainOrder.withoutOrder("The capital is Nairobi."));
    }

    @Test
    public void theBrainIsToldItDecides() {
        String a = BrainOrder.actions("\n- github.issues <owner/repo>: open issues");
        assertTrue(a.contains("never run anything by themselves"));
        assertTrue(a.contains("§SR"));
        assertTrue(a.contains("§CA"));
        assertTrue(a.contains("github.issues"));
        assertTrue(a.contains("Never claim a function ran"));
    }
}
