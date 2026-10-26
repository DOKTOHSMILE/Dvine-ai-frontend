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
        // everything is nano dots now: old style requests stay on dots and say so
        assertEquals(ai.dvine.face.Look.Style.DOTS, brain.respond("wireframe mode", "obi").look.style);
        assertTrue(brain.respond("make your skin darker", "obi").spoken().contains("nano dots"));
        assertEquals("BODY_ON", brain.respond("show your full body", "obi").command);
        assertEquals("FACE_ON", brain.respond("show your face", "obi").command);
        assertEquals("REDRAW", brain.respond("redraw yourself", "obi").command);
        BrainAnswer pose = brain.respond("do a t-pose", "obi");
        assertEquals("POSE", pose.command);
        assertEquals("tpose", pose.commandValue);
        assertEquals("squat", brain.respond("can you squat", "ivie").commandValue);
        assertEquals("TURN", brain.respond("turn around", "obi").command);
        assertEquals("pushups", brain.respond("do 10 push ups", "obi").commandValue);
        assertEquals("cartwheel", brain.respond("do a cartwheel", "ivie").commandValue);
        BrainAnswer scene = brain.respond("act out the tortoise and the hare", "ivie");
        assertNotNull(scene.scenePoses);
        assertEquals(scene.scenePoses.length, scene.sceneSeconds.length);
        assertEquals("the hare and the tortoise", scene.sceneTitle);
        assertEquals("STOP", brain.respond("stop", "obi").command);
        assertEquals("STOP", brain.respond("that's enough", "ivie").command);
        assertEquals("SELF_REPORT", brain.respond("what are you doing", "obi").command);
        assertEquals("FAVOURITES", brain.respond("what do I like", "obi").command);
        // ordinary talk is not a pose
        assertTrue(!"POSE".equals(brain.respond("how do I run a business", "obi").command));
        assertEquals("globe", brain.respond("draw the globe", "obi").object3d);
        assertEquals("dna", brain.respond("show me dna", "obi").object3d);
        BrainAnswer hair = brain.respond("make Ivie's hair longer", "obi");
        assertTrue(hair.lookIvie);
        assertTrue(hair.look.hair > 1f);
        assertEquals(0f, brain.respond("Ivie remove your hair", "ivie").look.hair, 0f);
        assertEquals(ai.dvine.face.Look.Style.DOTS, brain.respond("reset your look", "obi").look.style);
    }
}
