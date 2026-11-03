package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One route: every job is an order the brain gives (or that the words give at once), and DVINE carries it out. */
public class BrainOrderTest {
    @Test
    public void theModelCanOrderABuildOrASocialJob() {
        String p = BrainOrder.prompt("");
        assertTrue(p.contains("DO BUILD"));
        assertTrue(p.contains("DO SOCIAL SEARCH"));
        BrainOrder b = BrainOrder.read("DO BUILD a family house for five people, two storeys");
        assertEquals(BrainOrder.Kind.BUILD, b.kind);
        assertEquals("a family house for five people, two storeys", b.arg);
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
        BrainOrder build = BrainOrder.decide("Create a 3D two-storey building with a 20 foot roof", null);
        assertEquals(BrainOrder.Kind.BUILD, build.kind);
        assertEquals("Create a 3D two-storey building with a 20 foot roof", build.arg);
        // a plain question isn't an order
        assertTrue(BrainOrder.decide("hello there", null) == null || BrainOrder.decide("hello there", null).kind == BrainOrder.Kind.SAY);
    }
}
