package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One call answers and may act: an order on the reply's first line is DVINE's, the rest is the user's answer. */
public class OneCallOrderTest {
    @Test
    public void theModelsFirstLineIsTheOrder() {
        BrainOrder o = BrainOrder.leading("DO SEARCH weather in Lagos\nLooking it up now.");
        assertEquals(BrainOrder.Kind.SEARCH, o.kind);
        assertEquals("Looking it up now.", BrainOrder.withoutOrder("DO SEARCH weather in Lagos\nLooking it up now."));
        assertEquals("", BrainOrder.withoutOrder("DO TIME"));
    }

    @Test
    public void anAnswerIsOnlyAnAnswer() {
        assertNull(BrainOrder.leading("Do you want me to look it up?")); // "Do " in a sentence is not an order
        assertNull(BrainOrder.leading("The capital is Nairobi.\nDO SEARCH Nairobi")); // only the first line counts
        assertNull(BrainOrder.leading("DO SAY hello"));
        assertEquals("The capital is Nairobi.", BrainOrder.withoutOrder("The capital is Nairobi."));
    }

    @Test
    public void theModelIsToldItDecides() {
        String a = BrainOrder.actions("\n- github.issues <owner/repo>: open issues");
        assertTrue(a.contains("not from single words"));
        assertTrue(a.contains("DO SEARCH"));
        assertTrue(a.contains("github.issues"));
        assertTrue(a.contains("Never claim something was done"));
    }
}
