package ai.dvine.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.dvine.brain.BrainAnswer;
import ai.dvine.brain.MiniJson;
import ai.dvine.brain.OfflineBrain;
import ai.dvine.brain.TestPack;
import ai.dvine.face.Sketch;

public class EngineTest {
    /** Records what the body was told to do. */
    static final class FakeBody implements BodyPort {
        final List<String> calls = new ArrayList<String>();
        float smile;

        public void setBodyMode(boolean on) { calls.add("body " + on); }
        public void setPose(String pose) { calls.add("pose " + pose); }
        public void playScene(String[] poses, float[] seconds) { calls.add("scene " + Arrays.toString(poses)); }
        public void turnAround() { calls.add("turn"); }
        public void redraw() { calls.add("redraw"); }
        public void gesture(String key) { calls.add("gesture " + key); }
        public void showSketch(Sketch sketch) { calls.add("sketch " + sketch.title); }
        public void showObject(String kind) { calls.add("object " + kind); }
        public void clearSketch() { calls.add("clear"); }
        public void setMood(float s, float brow, float tilt) { smile = s; }
        public void setExpression(String name) { calls.add("expression " + name); }
        public void setDemo(boolean on) { calls.add("demo " + on); }

        String last() { return calls.get(calls.size() - 1); }
    }

    private static BrainAnswer answer(OfflineBrain brain, String text) {
        return brain.respond(text, "obi");
    }

    private static OfflineBrain brain() {
        return TestPack.brain();
    }

    @Test
    public void theBrainProposesAndTheEngineMovesTheBody() {
        FakeBody body = new FakeBody();
        Engine e = new Engine(body, null, () -> true);
        OfflineBrain b = brain();
        e.perform(answer(b, "do 10 push ups"), 0);
        assertEquals("pose pushups", body.last());
        assertTrue(e.selfReport(10).startsWith("I'm doing pushups."));
        assertEquals("body", e.state(10).get("view"));

        // a touch on the hand mid-exercise does not interrupt what the user asked for
        e.onTouch("left hand", 100);
        assertEquals("pose pushups", body.last());
        e.tick(5000); // the wave waited too long and expired
        assertEquals(Command.Status.EXPIRED, e.supervisor.history().get(e.supervisor.history().size() - 1).status);

        // "stop" cancels everything and stands up
        e.perform(answer(b, "stop"), 6000);
        assertEquals("pose stand", body.last());
        assertNull(e.supervisor.active(Command.Channel.BODY));
    }

    @Test
    public void scenesFinishUnlessTheUserAsksForSomethingElse() {
        FakeBody body = new FakeBody();
        Engine e = new Engine(body, null, () -> true);
        OfflineBrain b = brain();
        e.perform(answer(b, "act out the tortoise and the hare"), 0);
        assertTrue(body.last().startsWith("scene [stand, run"));
        assertTrue(e.selfReport(10).contains("acting the hare and the tortoise"));
        e.onTouch("right hand", 100);
        assertTrue("a touch waits", body.last().startsWith("scene [stand"));
        e.perform(answer(b, "do a cartwheel"), 200);
        assertEquals("pose cartwheel", body.last());
        assertEquals(Command.Status.PREEMPTED, find(e, "body.scene").status);
    }

    @Test
    public void invalidAndStaleRequestsAreRejectedAndRemembered() {
        FakeBody body = new FakeBody();
        Engine e = new Engine(body, null, () -> true);
        assertEquals(Command.Status.REJECTED, e.supervisor.submit(new Command("body.pose", "moonwalk", 60, 0), 0));
        assertEquals(Command.Status.REJECTED, e.supervisor.submit(new Command("body.fly", null, 60, 0), 0));
        assertEquals(Command.Status.EXPIRED, e.supervisor.submit(new Command("body.pose", "squat", 60, 0), 9000));
        assertTrue(body.calls.isEmpty());
        e.onResult(e.supervisor.history().get(0)); // the engine is the listener; failures become episodes
        assertTrue(e.episodes.size() >= 1);
        assertTrue(e.selfReport(9000).contains("couldn't do"));
    }

    @Test
    public void momentaryActionsOverlayWithoutStoppingAPose() {
        FakeBody body = new FakeBody();
        Engine e = new Engine(body, null, () -> true);
        OfflineBrain b = brain();
        e.perform(answer(b, "do a plank"), 0);
        e.perform(answer(b, "turn around"), 10);
        e.perform(answer(b, "redraw yourself"), 20);
        assertEquals("redraw", body.last());
        assertEquals("body.pose:plank", e.supervisor.active(Command.Channel.BODY).toString());
        e.perform(answer(b, "show your face"), 30);
        assertEquals("body false", body.last());
        assertNull(e.supervisor.active(Command.Channel.BODY));
        assertEquals("face", e.state(40).get("view"));
    }

    @Test
    public void moodFollowsAppraisalAndSettles() {
        FakeBody body = new FakeBody();
        Engine e = new Engine(body, null, () -> true);
        e.tick(0);
        e.onHeard("thank you, well done", 0);
        e.tick(100);
        assertTrue(body.smile > 0.03f);
        assertEquals("warm", e.affect.label());
        for (long t = 200; t < 200_000; t += 1000) {
            e.tick(t);
        }
        assertTrue(Math.abs(body.smile) < 0.01f);
        e.onHeard("that is wrong", 0);
        assertTrue("criticism is feedback, not hostility", e.affect.valence > -0.3f);
    }

    @Test
    public void workingMemoryIsSmallAndForgets() {
        WorkingMemory m = new WorkingMemory();
        for (int i = 0; i < 10; i++) {
            m.put("k" + i, "v", i, 1000, 0);
        }
        assertEquals(WorkingMemory.CAPACITY, m.size(0));
        assertNull("the least important went first", m.get("k0", 0));
        assertNotNull(m.get("k9", 0));
        assertEquals(0, m.size(2000));
    }

    @Test
    public void episodesStayShortOnThePhoneAndRespectConsent() {
        boolean[] share = {false};
        Episodes ep = new Episodes(null, () -> share[0]);
        Engine e = new Engine(new FakeBody(), null, () -> share[0]);
        e.onHeard("my secret plan is bananas", 0);
        e.onTouch("chest", 0);
        assertTrue(e.episodes.pending(10).isEmpty()); // sharing off
        share[0] = true;
        List<Episodes.Episode> p = e.episodes.pending(10);
        assertEquals(1, p.size());
        String body = Episodes.syncBody("phone-1", p);
        assertFalse("never the user's words", body.contains("bananas"));
        Map<String, Object> json = MiniJson.obj(MiniJson.parse(body));
        assertEquals("touch:chest", MiniJson.str(MiniJson.obj(MiniJson.list(json.get("episodes")).get(0)).get("tag"), ""));

        for (int i = 0; i < 400; i++) {
            ep.record("TOUCH", "touch:chest", "touched chest", 0.2f, i);
        }
        List<String> ids = new ArrayList<String>();
        for (Episodes.Episode x : ep.pending(1000)) {
            ids.add(x.id);
        }
        assertEquals(400, ids.size()); // nothing un-uploaded is dropped while offline
        ep.markUploaded(ids, 1000);
        assertEquals(Episodes.MAX_KEPT, ep.size());
        ep.markUploaded(new ArrayList<String>(), Episodes.KEEP_UPLOADED_MS + 2000);
        assertEquals("old uploaded episodes live on the server only", 0, ep.size());
    }

    @Test
    public void serverFavouritesComeBackAsHints() {
        Engine e = new Engine(new FakeBody(), null, () -> true);
        assertTrue(e.favouritesLine().startsWith("I don't know your favourites yet"));
        Map<String, Object> fav = new LinkedHashMap<String, Object>();
        fav.put("pose", Arrays.asList("pushups", "cartwheel"));
        fav.put("scene", Arrays.asList("morning workout"));
        Map<String, Object> profile = new LinkedHashMap<String, Object>();
        profile.put("favourites", fav);
        e.applyProfile(profile, 0);
        assertEquals("You often ask me for pushups and cartwheel, and your favourite scene is morning workout.",
                e.favouritesLine());
        assertEquals("morning workout", e.memory.get("favourite_scene", 0));
    }

    private static Command.Result find(Engine e, String action) {
        Command.Result last = null;
        for (Command.Result r : e.supervisor.history()) {
            if (r.command.action.equals(action)) {
                last = r;
            }
        }
        return last;
    }
}
