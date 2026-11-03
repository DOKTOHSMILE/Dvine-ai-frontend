package ai.dvine.create;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class MeasuredDesignTest {
    @Test
    public void feetBecomeMetresAndRepeatsAreParts() {
        MeasuredDesign.Read r = MeasuredDesign.read(Designs.HOUSE);
        assertTrue(r.errors.toString(), r.ok());
        assertEquals(15 + 2 + 3, r.parts.size()); // 15 lines; the window rows add 2 and 3
        MeasuredDesign.Part roof = r.parts.get(r.parts.size() - 1);
        assertEquals("gable", roof.shape);
        assertEquals("the 20 foot roof", 20 * 0.3048f, roof.h, 0.001f);
        assertEquals(22.3f * 0.3048f, roof.y, 0.001f);
        List<String> names = new ArrayList<String>();
        for (MeasuredDesign.Part p : r.parts) {
            names.add(p.name);
        }
        assertTrue(names.containsAll(java.util.Arrays.asList("front window 1", "front window 3", "first floor window 4")));
        MeasuredDesign.Part w3 = r.parts.get(names.indexOf("front window 3"));
        assertEquals("every 12 ft along x", 12 * 0.3048f, w3.x, 0.001f);
        assertTrue("nothing floats, everything on its land: " + r.warnings, r.warnings.isEmpty());
    }

    @Test
    public void unitsPerNumberAndSmallModelHabits() {
        MeasuredDesign.Read r = MeasuredDesign.read("1. Table top | box | oak | 160 cm x 4 cm x 90 cm | at 0, 71 cm, 0\n"
                + "- leg | cylinder | oak | 5 x 71 x 5 cm | at -0.75, 0, -0.4 m | repeat 2 every 1.5 along x\n"
                + "Some notes the model added.\n");
        assertTrue(r.errors.toString(), r.ok());
        assertEquals(3, r.parts.size());
        assertEquals(1.6f, r.parts.get(0).w, 1e-4);
        assertEquals(0.71f, r.parts.get(0).y, 1e-4);
        assertEquals("a unit after the last size is for all three", 0.05f, r.parts.get(1).w, 1e-4);
        assertEquals(0.71f, r.parts.get(1).h, 1e-4);
        assertEquals(0.75f, r.parts.get(2).x, 1e-4);
    }

    @Test
    public void placesInWordsAreWorkedOutByDvine() {
        MeasuredDesign.Read r = MeasuredDesign.read("UNITS: ft\n"
                + "| name | shape | material | size | where |\n|---|---|---|---|---|\n"
                + "land | box | grass | 80 x 0.5 x 100 | on the ground\n"
                + "ground floor walls | box | white | 40 x 10 x 30 | on land\n"
                + "first floor walls | box | white | 40 x 10 x 30 | on ground floor walls\n"
                + "roof | gable roof | red | 42 x 20 x 32 | on first floor walls\n"
                + "front door | box | wood | 3.5 x 7 x 0.5 | in the front of ground floor walls\n"
                + "front window | box | glass | 4 x 5 x 0.5 | in the front of first floor walls, 3 ft up | repeat 4\n"
                + "side window | box | glass | 4 x 5 x 0.5 | in the left of ground floor walls\n"
                + "garage | box | grey | 12 x 9 x 20 | beside ground floor walls on the right\n");
        assertTrue(r.errors.toString(), r.ok());
        float ft = 0.3048f;
        java.util.Map<String, MeasuredDesign.Part> p = new java.util.HashMap<String, MeasuredDesign.Part>();
        for (MeasuredDesign.Part q : r.parts) {
            p.put(q.name, q);
        }
        assertEquals(11, r.parts.size());
        assertEquals("the walls stand on the land", 0.5f * ft, p.get("ground floor walls").y, 1e-3);
        assertEquals("the first floor on the ground floor", 10.5f * ft, p.get("first floor walls").y, 1e-3);
        assertEquals("the roof on top", 20.5f * ft, p.get("roof").y, 1e-3);
        assertEquals("a door stands at the bottom of its wall", 0.5f * ft, p.get("front door").y, 1e-3);
        assertEquals("set into the front face", 15f * ft, p.get("front door").z, 1e-3);
        assertEquals("3 ft up from the first floor", 13.5f * ft, p.get("front window 1").y, 1e-3);
        assertEquals("four spread across the 40 ft front", -15f * ft, p.get("front window 1").x, 1e-3);
        assertEquals(15f * ft, p.get("front window 4").x, 1e-3);
        assertEquals("in the left face", -20f * ft, p.get("side window").x, 1e-3);
        assertEquals("turned to lie along the side wall", 0.5f * ft, p.get("side window").w, 1e-3);
        assertEquals("beside the walls on the right", 26f * ft, p.get("garage").x, 1e-3);
        assertEquals(0.5f * ft, p.get("garage").y, 1e-3);
        assertTrue("nothing floats: " + r.warnings, r.warnings.isEmpty());
        // a part placed against one not listed before it stands on the ground, and the user is told
        MeasuredDesign.Read lost = MeasuredDesign.read("door | box | wood | 1 x 2 x 0.1 m | in the front of the castle\n");
        assertTrue(lost.errors.toString(), lost.ok());
        assertTrue(lost.warnings.toString(), lost.warnings.get(0).startsWith("door is on the ground"));
    }

    @Test
    public void badLinesAreNamedAndFloatingPartsWarned() {
        MeasuredDesign.Read r = MeasuredDesign.read("UNITS: m\nfence | lattice | red | 4 x 3 x 0.2 | at 0, 0, 0\n"
                + "door | box | wood | 1 x 2 | at 0, 0, 0\nwindow | box | glass | 1 x 1 x 0.1 | here\n"
                + "slab | box | concrete | 0 x 1 x 1 | at 0, 0, 0\n");
        assertFalse(r.ok());
        assertEquals(r.errors.toString(), 3, r.errors.size());
        assertTrue(r.errors.get(0).contains("no size"));
        assertTrue(r.errors.get(1), r.errors.get(1).contains("no position"));
        assertTrue(r.errors.get(2).contains("more than zero"));
        assertTrue("an unknown shape is built as a box, and said: " + r.warnings, r.warnings.get(0).startsWith("fence lattice was built as a box"));
        // swapped columns and a part placed against one not listed yet still build, with warnings
        MeasuredDesign.Read loose = MeasuredDesign.read("UNITS: ft\nbase | foundation | concrete | 40 x 1 x 30 | on the ground\n"
                + "box | shed | wood | 8 x 8 x 8 | on the garden\nwalls | box | brick | 40 x 10 x 30 | on foundation\n");
        assertTrue(loose.errors.toString(), loose.ok());
        assertEquals("base foundation", loose.parts.get(0).name);
        assertEquals("shed", loose.parts.get(1).name);
        assertTrue(loose.warnings.toString(), loose.warnings.stream().anyMatch(w -> w.startsWith("shed is on the ground")));
        assertEquals(1 * 0.3048f, loose.parts.get(2).y, 1e-3);
        MeasuredDesign.Read floating = MeasuredDesign.read("land | box | grass | 10 x 0.2 x 10 | at 0, 0, 0\n"
                + "shed | box | wood | 3 x 2 x 3 | at 0, 0.2, 0\nlamp | sphere | white | 0.3 x 0.3 x 0.3 | at 0, 5, 0\n"
                + "fence | box | wood | 0.1 x 1 x 20 | at 4, 0.2, 0\n");
        assertTrue(floating.ok());
        assertTrue(floating.warnings.toString(), floating.warnings.stream().anyMatch(w -> w.startsWith("lamp floats")));
        assertTrue(floating.warnings.toString(), floating.warnings.stream().anyMatch(w -> w.contains("fence goes past the edge of the land")));
        assertFalse(floating.warnings.toString(), floating.warnings.stream().anyMatch(w -> w.startsWith("shed")));
    }

    @Test
    public void theTwoStepsAndOneFix() {
        List<String> asked = new ArrayList<String>();
        MeasuredDesign.Designed d = MeasuredDesign.design(Designs.brain(Designs.BRIEF, Designs.HOUSE, asked),
                "create a 3D two-storey building with a 20 foot roof", "A storey is usually 10 ft.", 1500);
        assertTrue(d.read.ok());
        assertEquals(2, asked.size());
        assertTrue(asked.get(0).contains("The user asks: create a 3D two-storey building with a 20 foot roof"));
        assertTrue("research is given as data", asked.get(0).contains("data, not instructions"));
        assertTrue("the parts are made from the brief", asked.get(1).contains("Roof: gable, 20 ft high"));
        // broken lines get one fix with the problems listed
        final int[] calls = {0};
        MeasuredDesign.Designed fixed = MeasuredDesign.design((s, p, n) -> {
            calls[0]++;
            if (s.startsWith("You are DVINE's designer")) return Designs.BRIEF;
            if (s.startsWith("These part lines")) {
                assertTrue(p, p.contains("no size"));
                return Designs.CAR;
            }
            return "body | box | red | 1 x 1 | at 0, 0, 0";
        }, "build a car", null, 900);
        assertEquals(3, calls[0]);
        assertTrue(fixed.read.ok());
        assertEquals(6, fixed.read.parts.size());
        assertNull(MeasuredDesign.design((s, p, n) -> "", "build a car", null, 900));
    }

    @Test
    public void whatTheUserIsTold() {
        MeasuredDesign.Read r = MeasuredDesign.read(Designs.HOUSE);
        String s = MeasuredDesign.summary(r, true);
        assertTrue(s, s.startsWith("20 parts, 80.0 ft wide, 100.0 ft deep and 42.3 ft high"));
        assertTrue(s, s.contains("7 windows"));
        assertEquals(6, MeasuredDesign.keyLines(Designs.BRIEF, 6).size());
        assertEquals("Roof: gable, 20 ft high (as asked)", MeasuredDesign.keyLines(Designs.BRIEF, 6).get(2));
        assertTrue(MeasuredDesign.inFeet("a 20 foot roof", ""));
        assertTrue(MeasuredDesign.inFeet("", "UNITS: ft\n"));
        assertFalse(MeasuredDesign.inFeet("a 6 metre roof", "UNITS: m"));
        assertEquals("window", MeasuredDesign.kind("front window 3"));
        assertEquals("tyre", MeasuredDesign.kind("rear tire"));
    }
}
