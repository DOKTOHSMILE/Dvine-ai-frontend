package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One route: every job is an order the brain gives (or that the words give at once), and Ibiko carries it out. */
public class BrainOrderTest {
    @Test
    public void theModelCanOrderABuildOrASocialJob() {
        String p = BrainOrder.prompt("");
        assertTrue(p.contains("DO IMAGE"));
        assertFalse("there is no 3D", p.contains("DO BUILD"));
        assertTrue(p.contains("DO SOCIAL SEARCH"));
        BrainOrder b = BrainOrder.read("DO IMAGE a flat green phone-call icon on white");
        assertEquals(BrainOrder.Kind.IMAGE, b.kind);
        assertEquals("a flat green phone-call icon on white", b.arg);
        assertEquals("an older brain's BUILD makes a picture", BrainOrder.Kind.IMAGE, BrainOrder.read("DO BUILD a house").kind);
        BrainOrder s = BrainOrder.read("DO SOCIAL SEARCH X fuel price in Abuja");
        assertEquals(BrainOrder.Kind.SOCIAL, s.kind);
        assertEquals("search x: fuel price in abuja", s.arg);
        assertEquals("connect facebook", BrainOrder.read("DO SOCIAL CONNECT facebook").arg);
    }

    @Test
    public void clearWordsGiveTheSameOrdersAtOnce() {
        assertEquals("connect facebook", BrainOrder.decide("Connect my Facebook", null).arg);
        assertEquals(BrainOrder.Kind.SOCIAL, BrainOrder.decide("How do I access my social media functions?", null).kind);
        assertEquals("post facebook: ", BrainOrder.decide("Generate a new post for Facebook", null).arg);
        assertEquals("trending x", BrainOrder.decide("Check trending post on x", null).arg);
        assertEquals("search reddit: flutter", BrainOrder.decide("search reddit for flutter", null).arg);
        BrainOrder icon = BrainOrder.decide("Generate a call icon", null);
        assertEquals("a picture, not a 3D build", BrainOrder.Kind.IMAGE, icon.kind);
        assertEquals("Generate a call icon", icon.arg);
        assertEquals(BrainOrder.Kind.IMAGE, BrainOrder.decide("draw me a cute lion illustration", null).kind);
        // a plain question isn't an order
        assertTrue(BrainOrder.decide("hello there", null) == null || BrainOrder.decide("hello there", null).kind == BrainOrder.Kind.SAY);
    }
}
