package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class ActionsAndOnboardingTest {
    private OfflineBrain brain;

    @Before
    public void setUp() throws Exception {
        brain = TestPack.brain(TestPack.tempDir());
    }

    @Test
    public void shareAsksThenHandsOffOnYes() {
        BrainAnswer ask = brain.respond("post this on WhatsApp: Good morning everyone", "obi");
        assertTrue(ask.listenAfter);
        assertNull(ask.action);
        assertTrue(ask.spoken().contains("Shall I open WhatsApp"));
        assertTrue(ask.spoken().contains("Good morning everyone"));
        BrainAnswer yes = brain.respond("yes", "obi");
        assertNotNull(yes.action);
        assertEquals(PendingAction.Kind.SHARE, yes.action.kind);
        assertEquals("whatsapp", yes.action.app);
        assertEquals("Good morning everyone", yes.action.content);
        assertTrue(yes.spoken().contains("press send"));
    }

    @Test
    public void copyThatUsesLastAnswer() {
        brain.respond("what is the capital of Nigeria", "obi");
        BrainAnswer ask = brain.respond("copy that", "obi");
        assertTrue(ask.spoken().contains("Abuja"));
        BrainAnswer yes = brain.respond("go ahead", "obi");
        assertEquals(PendingAction.Kind.COPY, yes.action.kind);
        assertEquals("Copied. It's on your clipboard.", yes.spoken());
    }

    @Test
    public void noCancels() {
        brain.respond("share this: hello world", "ivie");
        BrainAnswer no = brain.respond("no", "ivie");
        assertNull(no.action);
        assertTrue(no.spoken().contains("Cancelled"));
        assertNull(brain.respond("yes", "ivie").action);
    }

    @Test
    public void moneyIsStillRefused() {
        BrainAnswer a = brain.respond("send 5000 naira to Tunde", "obi");
        assertNull(a.action);
        assertTrue(a.spoken().contains("Nothing was sent"));
    }

    @Test
    public void names() {
        assertEquals("Valentine", Onboarding.parseName("my name is valentine"));
        assertEquals("Val", Onboarding.parseName("I'm Val."));
        assertEquals("Ada Obi", Onboarding.parseName("Ada Obi"));
        assertNull(Onboarding.parseName("what is the weather today in Lagos"));
        assertNull(Onboarding.parseName("yes"));
        BrainAnswer r = brain.respond("call me Doktoh", "obi");
        assertEquals("Doktoh", r.newName);
        assertTrue(brain.respond("hello", "obi").spoken().contains("Doktoh"));
    }

    @Test
    public void gesturesAndSketches() {
        assertEquals("👋", brain.respond("hello", "obi").gesture);
        assertEquals("👍", ai.dvine.face.Gestures.find("show me a thumbs up"));
        assertEquals("🙏", ai.dvine.face.Gestures.find("🙏🏽"));
        assertNull(ai.dvine.face.Gestures.find("🖕"));
        BrainAnswer d = brain.respond("explain 3 plus 4", "ivie");
        assertNotNull(d.sketch);
        assertTrue(d.spoken().contains("7"));
        assertNotNull(brain.respond("draw a triangle", "obi").sketch);
        assertEquals("MUSIC_OFF", brain.respond("music off", "obi").command);
        assertEquals("SKIN_OFF", brain.respond("wireframe mode", "obi").command);
        assertEquals("SKIN_ON", brain.respond("skin mode", "obi").command);
    }
}
