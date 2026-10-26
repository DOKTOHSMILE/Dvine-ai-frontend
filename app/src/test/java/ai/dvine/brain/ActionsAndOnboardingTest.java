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
        // the default look is the wireframe hologram; dots are a style you can ask for
        assertEquals(ai.dvine.face.Look.Style.WIRE, brain.respond("wireframe mode", "obi").look.style);
        assertEquals(ai.dvine.face.Look.Style.DOTS, brain.respond("dot mode", "obi").look.style);
        assertTrue(brain.respond("make your skin darker", "obi").spoken().contains("wireframe"));
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
        // asked as questions, in everyday words
        String[][] moves = {{"can you run?", "run"}, {"Can you walk?", "walk"}, {"jump", "jump"}, {"can you jump?", "jump"},
                {"sit", "sit"}, {"sit down please", "sit"}, {"dance", "dance"}, {"can you dance for me?", "dance"},
                {"roll over", "roll"}, {"can you roll over?", "roll"}, {"flip", "backflip"}, {"do a flip", "backflip"},
                {"can you do a backflip?", "backflip"}, {"lie down", "lie"}, {"start running", "run"},
                {"keep dancing", "dance"}, {"jump up and down", "jump"}, {"show me how you walk", "walk"}};
        for (String[] m : moves) {
            BrainAnswer a = brain.respond(m[0], "obi");
            assertEquals(m[0], "POSE", a.command);
            assertEquals(m[0], m[1], a.commandValue);
        }
        assertTrue(brain.respond("can you run?", "obi").spoken().startsWith("Yes") || brain.respond("can you run?", "obi").spoken().startsWith("Of course"));
        assertTrue(!"POSE".equals(brain.respond("can you walk me through the steps", "obi").command));
        assertTrue(!"POSE".equals(brain.respond("can you run the numbers", "obi").command));
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
        assertEquals("galaxy", brain.respond("can you draw a galaxy?", "obi").object3d);
        // Mode B: the anatomical view
        String[][] inside = {{"show me your skeleton", "skeleton"}, {"show your bones", "skeleton"}, {"x-ray", "all"}, {"show me your heart", "heart"},
                {"can you show your lungs?", "lungs"}, {"show your ribs", "chest"}, {"show me your organs", "organs"}, {"cover up", "off"},
                {"hide your skeleton", "off"}};
        for (String[] i : inside) {
            BrainAnswer a = brain.respond(i[0], "obi");
            assertEquals(i[0], "ANATOMY", a.command);
            assertEquals(i[0], i[1], a.commandValue);
        }
        assertTrue(brain.respond("show me your heart", "obi").spoken().contains("doesn't beat"));
        assertEquals("black hole", brain.respond("draw a black hole", "ivie").object3d);
        assertEquals("tesseract", brain.respond("show me a 4d cube", "obi").object3d);
        assertEquals("lorenz", brain.respond("draw the butterfly effect", "obi").object3d);
        assertEquals("butterfly", brain.respond("draw a butterfly", "ivie").object3d);
        assertEquals("eiffel tower", brain.respond("please draw the eiffel tower", "obi").object3d);
        BrainAnswer hair = brain.respond("make Ivie's hair longer", "obi");
        assertTrue(hair.lookIvie);
        assertTrue(hair.look.hair > 1f);
        assertEquals(0f, brain.respond("Ivie remove your hair", "ivie").look.hair, 0f);
        assertEquals(ai.dvine.face.Look.Style.WIRE, brain.respond("reset your look", "obi").look.style);
    }
}
