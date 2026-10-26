package ai.dvine.face;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.dvine.face.nano.Forms;
import ai.dvine.face.nano.Swarm;

public class NanoSwarmTest {
    private static float settle(Swarm s, Swarm.Form f, float seconds) {
        float t = 0f;
        for (int i = 0; i < (int) (seconds * 60); i++) {
            s.shape(f, t);
            s.step(1 / 60f);
            t += 1 / 60f;
        }
        float worst = 0f;
        for (int i = 0; i < s.active() * 3; i++) {
            worst = Math.max(worst, Math.abs(s.pos[i] - s.target[i]));
            assertFalse(Float.isNaN(s.pos[i]));
        }
        return worst;
    }

    @Test
    public void everyFormFitsTheSwarmAndSettles() {
        Swarm s = new Swarm(10000);
        Swarm.Form[] forms = {
                new Forms.Head(WireHead.create(false)),
                new Forms.Head(WireHead.create(true)),
                new Forms.Body(WireHead.create(false)),
                new Forms.Body(WireHead.create(true))
        };
        for (Swarm.Form f : forms) {
            float worst = settle(s, f, 3f);
            assertTrue("form overflows the swarm", s.active() < s.capacity);
            assertTrue("did not settle: " + worst, worst < 0.05f);
        }
    }

    @Test
    public void objectsAndDrawingsBuildFromDots() {
        Swarm s = new Swarm(4000);
        for (String k : new String[]{"globe", "cube", "pyramid", "torus", "dna", "atom", "sphere"}) {
            settle(s, new Forms.Object3D(k), 1f);
            assertTrue(k, s.active() > 100 && s.active() < s.capacity);
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
    }

    @Test
    public void scatterThenReassemble() {
        Swarm s = new Swarm(10000);
        Forms.Head f = new Forms.Head(WireHead.create(true));
        settle(s, f, 2f);
        s.scatter(4f);
        assertTrue(settle(s, f, 3f) < 0.05f);
    }

    @Test
    public void requestsNameObjects() {
        assertEquals("globe", Forms.objectFor("draw the earth"));
        assertEquals("atom", Forms.objectFor("show me an atom"));
        assertEquals(null, Forms.objectFor("what is an atom"));
    }
}
