package ai.dvine.render.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** The built-in 3D plans, and changing a drawing part by part: "take out the tyres". */
public class PartsTest {
    @Test
    public void aPlanBuildsWithItsNamedParts() {
        SolidSpec cat = TestPlans.of("cat");
        java.util.List<String> names = new ArrayList<String>();
        for (SolidSpec.Part p : cat.parts) {
            names.add(p.name);
        }
        assertTrue(names.contains("tail"));
        assertTrue(names.contains("left ear"));
        assertTrue(cat.points(4000, 2f).count > 3000);
    }

    @Test
    public void nearAndFarLegsStandApart() {
        SolidSpec dog = TestPlans.of("dog");
        float near = 0, far = 0;
        for (SolidSpec.Part p : dog.parts) {
            if (p.name.equals("front leg")) near = p.z;
            if (p.name.equals("far front leg")) far = p.z;
        }
        assertTrue(near > far + 5);
    }

    @Test
    public void takeOutTheTyres() {
        SolidSpec car = TestPlans.of("car").fixFrame();
        PartEdit e = PartEdit.parse("Take out the tyres");
        assertEquals(PartEdit.Op.REMOVE, e.op);
        List<Integer> which = PartEdit.find(car, e.target);
        assertEquals(6, which.size()); // four wheels and the two hubcaps on them
        List<SolidSpec.Part> removed = new ArrayList<SolidSpec.Part>();
        SolidSpec after = e.apply(car, which, removed);
        assertEquals(car.parts.size() - 6, after.parts.size());
        assertEquals(6, removed.size());
        // the rest stays where it was: the body's points don't move or grow
        Shape a = car.points(3000, 2f), b = after.points(3000, 2f);
        assertTrue(maxAbs(b) <= 1.001f && maxAbs(b) <= maxAbs(a) + 0.02f); // same frame: nothing grows to fill the space
        assertEquals(PartEdit.Op.RESTORE, PartEdit.parse("put the wheels back").op);
        assertEquals(6, PartEdit.pick(removed, "wheels").size());
    }

    @Test
    public void partsByNameAndPlace() {
        SolidSpec car = TestPlans.of("car");
        assertEquals(2, PartEdit.find(car, "hubcaps").size()); // only the hubcaps, not the wheels
        assertEquals(2, PartEdit.find(car, "front wheels").size());
        SolidSpec cat = TestPlans.of("cat");
        assertEquals(2, PartEdit.find(cat, "ears").size());
        assertEquals(1, PartEdit.find(cat, "left ear").size());
        assertEquals(4, PartEdit.find(cat, "paws").size()); // a paw is a leg's end: the legs
        PartEdit c = PartEdit.parse("make the ears pink");
        assertEquals(PartEdit.Op.COLOUR, c.op);
        assertEquals("ears", c.target);
        assertNull(PartEdit.parse("make it red")); // the whole thing: the shape commands do that
        assertEquals(PartEdit.Op.BIGGER, PartEdit.parse("make the tail bigger").op);
        assertEquals(PartEdit.Op.ADD, PartEdit.parse("add a spoiler").op);
        assertEquals("ears", PartEdit.names(cat, PartEdit.find(cat, "ears")).replace("left ear and right ear", "ears"));
        assertEquals("legs", PartEdit.names(cat, PartEdit.find(cat, "legs")));
    }

    @Test
    public void addingKeepsWhatIsThere() {
        SolidSpec car = TestPlans.of("car").fixFrame();
        SolidSpec more = car.extend("spoiler | box | navy | above body, left | small wide flat");
        assertEquals(car.parts.size() + 1, more.parts.size());
        assertEquals(car.parts.get(0).x, more.parts.get(0).x, 0f);
        assertTrue(PartEdit.addPrompt(car, "car", "a spoiler").contains("body"));
    }

    @Test
    public void readsTheBrainsAnswerAboutParts() {
        SolidSpec cat = TestPlans.of("cat");
        assertEquals(1, PartEdit.fromBrain(cat, "tail").size());
        assertEquals(0, PartEdit.fromBrain(cat, "none").size());
    }

    @Test
    public void readsWhatAThingLooksLikeOnline() {
        String json = "{\"query\":{\"pages\":[{\"title\":\"Cat\",\"extract\":\"The cat is a small \\\"domestic\\\" carnivore.\\nIt has a tail \\u2013 long.\"}]}}";
        assertEquals("The cat is a small \"domestic\" carnivore. It has a tail – long.", PictureFinder.extract(json));
        assertNull(PictureFinder.extract("{}"));
        assertTrue(PictureFinder.describeUrl("red panda").contains("red+panda"));
    }

    @Test
    public void aBuildingPlanIsNotACat() {
        SolidSpec building = SolidSpec.parse("walls | box | beige | middle centre | large\nroof | pyramid | red | above walls | large\n"
                + "window | box | sky | on walls | small flat\nchimney | box | red | above walls, right | small tall", "cat");
        assertFalse(ai.dvine.llm.DrawPrompt.usable(building, "cat"));
        assertTrue(ai.dvine.llm.DrawPrompt.usable(building, "two-story house"));
        assertFalse(ai.dvine.llm.DrawPrompt.usable(SolidSpec.parse("body | oval | grey | middle centre | large", "cat"), "cat"));
        String withFacts = ai.dvine.llm.DrawPrompt.user("cat", "Cats have fur.");
        assertTrue(withFacts.contains("cat") && withFacts.contains("Cats have fur.")); // what was found online reaches the brain
        ai.dvine.agent.Interpretation wrong = ai.dvine.agent.Interpretation.parse(
                "INTENT: request\nMEANS: draw\nTASK: draw\nTARGET: two-story building\nINTERNET: no");
        assertFalse(wrong.groundedIn("draw a cat"));
        assertTrue(wrong.groundedIn("draw me a building with two floors"));
    }

    @Test
    public void meshesFaceOutward() {
        for (String k : new String[]{"car", "cat", "bicycle", "rocket", "cup"}) {
            for (SolidSpec.Part p : TestPlans.of(k).parts) {
                if (p.solid == SolidSpec.Solid.RING) {
                    continue; // a ring's inner side rightly faces its middle
                }
                float[] t = Gltf.triangles(p, 0.01f), n = Gltf.normals(t);
                int outward = 0, all = t.length / 9;
                for (int i = 0; i < t.length; i += 9) {
                    float mx = (t[i] + t[i + 3] + t[i + 6]) / 3, my = (t[i + 1] + t[i + 4] + t[i + 7]) / 3, mz = (t[i + 2] + t[i + 5] + t[i + 8]) / 3;
                    if (mx * n[i] + my * n[i + 1] + mz * n[i + 2] >= -1e-6f) outward++;
                }
                assertTrue(k + " " + p.name + " faces out", outward >= all * 0.9);
            }
        }
    }

    private static float maxAbs(Shape s) {
        float m = 0;
        for (int i = 0; i < s.count * 3; i++) {
            m = Math.max(m, Math.abs(s.xyz[i]));
        }
        return m;
    }
}
