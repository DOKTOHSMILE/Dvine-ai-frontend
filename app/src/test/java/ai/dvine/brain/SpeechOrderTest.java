package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeechOrderTest {
    @Test
    public void aMisheardInstallGoesToTermuxWithoutAskingAgain() {
        SpeechOrder.Order order = SpeechOrder.read("let's intel install termux");
        assertEquals("INSTALL_TERMUX", order.operation);
        assertEquals("let's install termux", order.repaired);
        assertFalse(order.speech.toLowerCase().contains("not sure"));
        assertFalse(order.speech.toLowerCase().contains("what do you mean"));
        assertEquals("OPEN_TERMUX", SpeechOrder.read("open termux").operation);
        assertNull(SpeechOrder.read("what is a triangle"));
    }

    @Test
    public void theOfflineBrainDoesNotBounceAShortRequest() throws Exception {
        OfflineBrain brain = TestPack.brain(TestPack.tempDir());
        BrainAnswer termux = brain.respond("let's intel install termux", "obi");
        assertEquals("INSTALL_TERMUX", termux.command);
        assertFalse(termux.spoken().toLowerCase().contains("not sure i follow"));
        assertFalse(termux.listenAfter);
        BrainAnswer odd = brain.respond("the blue one goes sideways", "ivie");
        assertFalse(odd.listenAfter);
        assertFalse(odd.spoken().toLowerCase().contains("what do you mean"));
        assertFalse(odd.spoken().toLowerCase().contains("not sure i follow"));
    }
}
