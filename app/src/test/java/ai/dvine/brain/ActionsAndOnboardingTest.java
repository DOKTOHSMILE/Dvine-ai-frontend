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
    public void drawingsMovesAndSettings() {
        BrainAnswer d = brain.respond("explain 3 plus 4", "ivie");
        assertNotNull(d.sketch);
        assertTrue(d.spoken().contains("7"));
        assertNotNull(brain.respond("draw a triangle", "obi").sketch);
        assertEquals("MUSIC_OFF", brain.respond("music off", "obi").command);
        // the movement engine: moves for a body DVINE designs, in everyday words
        BrainAnswer pose = brain.respond("do a t-pose", "obi");
        assertEquals("POSE", pose.command);
        assertEquals("tpose", pose.commandValue);
        String[][] moves = {{"make it walk", "walk"}, {"can it dance", "dance"}, {"do 10 push ups", "pushups"},
                {"do a cartwheel", "cartwheel"}, {"make the robot jump", "jump"}, {"can you do a backflip?", "backflip"}};
        for (String[] m : moves) {
            BrainAnswer a = brain.respond(m[0], "obi");
            assertEquals(m[0], "POSE", a.command);
            assertEquals(m[0], m[1], a.commandValue);
        }
        assertTrue(!"POSE".equals(brain.respond("can you walk me through the steps", "obi").command));
        assertTrue(!"POSE".equals(brain.respond("can you run the numbers", "obi").command));
        assertTrue(!"POSE".equals(brain.respond("how do I run a business", "obi").command));
        assertEquals("STOP", brain.respond("stop", "obi").command);
        assertEquals("STOP", brain.respond("that's enough", "ivie").command);
        assertEquals("SELF_REPORT", brain.respond("what are you doing", "obi").command);
        assertEquals("FAVOURITES", brain.respond("what do I like", "obi").command);
        // a named picture is a photograph lookup, not a stored shape
        assertEquals("LOOKUP_PHOTO", brain.respond("draw the globe", "obi").command);
        assertEquals("globe", brain.respond("draw the globe", "obi").commandValue);
        assertEquals(null, brain.respond("show me dna", "obi").object3d);
        assertEquals(null, brain.respond("can you draw a galaxy?", "obi").sketch);
        assertEquals("LOOKUP_PHOTO", brain.respond("draw a black hole", "ivie").command);
        assertEquals("LOOKUP_PHOTO", brain.respond("please draw the eiffel tower", "obi").command);
        // no face: the old face and body commands are gone
        for (String gone : new String[]{"show your face", "show your full body", "smile", "show me your skeleton", "make your skin darker"}) {
            BrainAnswer a = brain.respond(gone, "obi");
            assertTrue(gone, a.command == null || !a.command.matches("FACE_ON|BODY_ON|EXPRESSION|ANATOMY|LOOK"));
        }
    }
}
