package ai.dvine.face;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.dvine.face.nano.DotBody;
import ai.dvine.face.nano.DotHead;
import ai.dvine.face.nano.Forms;
import ai.dvine.face.nano.Sculpt;
import ai.dvine.face.nano.Swarm;

public class NanoSwarmTest {
    private static float settle(Swarm s, Swarm.Form f, float seconds) {
        float t = 0f;
        for (int i = 0; i < (int) (seconds * 60); i++) {
            s.shape(f, t);
            s.step(1 / 60f);
            t += 1 / 60f;
        }
        // average lag: moving parts (hair in the breeze, a running stride) trail their targets a little
        float sum = 0f;
        float worst = 0f;
        for (int i = 0; i < s.active() * 3; i++) {
            float e = Math.abs(s.pos[i] - s.target[i]);
            sum += e;
            worst = Math.max(worst, e);
            assertFalse(Float.isNaN(s.pos[i]));
        }
        assertTrue("a dot is far off: " + worst, worst < 0.2f);
        return sum / Math.max(1, s.active() * 3);
    }

    @Test
    public void sculptedDotsLieOnTheCurvedSurface() {
        Sculpt ball = new Sculpt().sphere(0f, 0f, 0f, 0.8f);
        Sculpt.Cloud c = ball.sample(1500, 1);
        assertTrue("about the asked count: " + c.size, c.size > 1200 && c.size <= 1500);
        for (int i = 0; i < c.size; i++) {
            float r = (float) Math.sqrt(c.xyz[i * 3] * c.xyz[i * 3] + c.xyz[i * 3 + 1] * c.xyz[i * 3 + 1] + c.xyz[i * 3 + 2] * c.xyz[i * 3 + 2]);
            assertEquals(0.8f, r, 0.005f);
            // normals point outward
            assertTrue(c.xyz[i * 3] * c.normal[i * 3] + c.xyz[i * 3 + 1] * c.normal[i * 3 + 1] + c.xyz[i * 3 + 2] * c.normal[i * 3 + 2] > 0.7f);
        }
    }

    @Test
    public void everyFormFitsTheSwarmAndSettles() {
        Swarm s = new Swarm(10000);
        Swarm.Form[] forms = {
                new Forms.Head(DotHead.create(false)),
                new Forms.Head(DotHead.create(true)),
                new DotBody(DotHead.create(false)),
                new DotBody(DotHead.create(true))
        };
        for (Swarm.Form f : forms) {
            float worst = settle(s, f, 3f);
            assertTrue("form overflows the swarm: " + s.active(), s.active() < s.capacity);
            assertTrue("did not settle: " + worst, worst < 0.01f);
        }
    }

    @Test
    public void everyPoseStandsOnTheFloor() {
        DotBody b = new DotBody(DotHead.create(true));
        float[] xyz = new float[10000 * 3];
        float[] lit = new float[10000];
        float t = 0f;
        for (String pose : DotBody.POSES) {
            b.setPose(pose);
            int n = 0;
            for (int i = 0; i < 120; i++, t += 1 / 60f) {
                n = b.fill(xyz, lit, 10000, t);
            }
            float low = 9f, high = -9f;
            for (int i = 0; i < n; i++) {
                low = Math.min(low, xyz[i * 3 + 1]);
                high = Math.max(high, xyz[i * 3 + 1]);
                assertFalse(pose, Float.isNaN(xyz[i * 3]));
            }
            assertEquals(pose + " floor", DotBody.FLOOR_OUT, low, 0.04f);
            assertTrue(pose + " has height", high - low > 0.5f);
        }
    }

    @Test
    public void theHeadSpeaksBlinksAndLooks() {
        DotHead h = DotHead.create(false);
        WireHead.Pose rest = new WireHead.Pose();
        WireHead.Pose open = new WireHead.Pose();
        open.jaw = 1f;
        open.blink = 1f;
        float[] a = new float[h.size * 3], b = new float[h.size * 3], la = new float[h.size], lb = new float[h.size];
        assertEquals(h.pose(rest, a, la, h.size, 1, -9f), h.pose(open, b, lb, h.size, 1, -9f));
        float drop = 0f;
        float lit = 0f, litClosed = 0f;
        for (int i = 0; i < h.size; i++) {
            if (a[i * 3 + 1] < -0.65f && a[i * 3 + 2] > 0.5f) {
                drop = Math.max(drop, a[i * 3 + 1] - b[i * 3 + 1]);
            }
            lit += la[i];
            litClosed += lb[i];
        }
        assertTrue("jaw opens: " + drop, drop > 0.08f);
        assertTrue("eyes close", litClosed < lit);
    }

    @Test
    public void objectsAndDrawingsBuildFromDots() {
        Swarm s = new Swarm(4000);
        for (String k : Forms.OBJECTS) {
            settle(s, new Forms.Object3D(k), 3f);
            assertTrue(k + " " + s.active(), s.active() > 300 && s.active() < s.capacity);
        }
        Forms.Drawing house = new Forms.Drawing(Sketch.forRequest("draw a house"));
        Swarm d = new Swarm(4000);
        d.shape(house, 0f);
        int early = d.active();
        for (int i = 1; i < 600; i++) {
            d.shape(house, i / 60f);
        }
        assertTrue("strokes appear over time", d.active() > early);
        assertEquals(house.size(), d.active());
        // drawings are solid: dots in front of and behind the drawing plane
        float front = 0f, back = 0f;
        for (int i = 0; i < d.active(); i++) {
            front = Math.max(front, d.target[i * 3 + 2]);
            back = Math.min(back, d.target[i * 3 + 2]);
        }
        assertTrue("has depth", front - back > 0.1f);
    }

    @Test
    public void scatterThenReassemble() {
        Swarm s = new Swarm(10000);
        Forms.Head f = new Forms.Head(DotHead.create(true));
        settle(s, f, 2f);
        s.scatter(4f);
        assertTrue(settle(s, f, 3f) < 0.01f);
    }

    @Test
    public void requestsNameObjectsAndPoses() {
        assertEquals("globe", Forms.objectFor("draw the earth"));
        assertEquals("atom", Forms.objectFor("show me an atom"));
        assertEquals("cone", Forms.objectFor("draw a cone"));
        assertNull(Forms.objectFor("what is an atom"));
        assertEquals("tpose", DotBody.poseFor("do a t-pose"));
        assertEquals("sit", DotBody.poseFor("sit cross legged"));
        assertEquals("handstand", DotBody.poseFor("do a handstand"));
    }
}
