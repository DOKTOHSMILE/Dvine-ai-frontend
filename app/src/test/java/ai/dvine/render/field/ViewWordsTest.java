package ai.dvine.render.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Turning the drawing, however it is said. */
public class ViewWordsTest {
    private static ShapeCommand.Op op(String said) {
        ShapeCommand c = ShapeCommand.parse(said);
        assertNotNull(said, c);
        return c.op;
    }

    @Test
    public void stopping() {
        for (String s : new String[]{"stop rotating", "stop spinning", "stop turning it", "stop it from rotating", "freeze",
                "hold it still", "keep it still", "don't rotate", "no more spinning", "pause the rotation", "stay still", "stop moving"}) {
            assertEquals(s, ShapeCommand.Op.STOP, op(s));
        }
    }

    @Test
    public void spinningEachWay() {
        assertEquals(ShapeCommand.Op.SPIN, op("rotate it horizontally"));
        assertEquals(ShapeCommand.Op.SPIN, op("spin it side to side"));
        assertEquals(ShapeCommand.Op.SPIN, op("keep rotating"));
        assertEquals(ShapeCommand.Op.SPIN, op("spin it"));
        assertEquals(-1f, ShapeCommand.parse("spin it to the left").a, 0f);
        assertEquals(-1f, ShapeCommand.parse("rotate it anticlockwise").a, 0f);
        for (String s : new String[]{"rotate it vertically", "rotate it up to down", "spin it up and down", "rotate from top to bottom",
                "make it tumble", "keep flipping it over", "spin it forwards", "rotate it on the x axis"}) {
            assertEquals(s, ShapeCommand.Op.SPIN_VERTICAL, op(s));
        }
        assertEquals(-1f, ShapeCommand.parse("rotate it from bottom to top").a, 0f);
        assertEquals(ShapeCommand.Op.FASTER, op("faster"));
        assertEquals(ShapeCommand.Op.SLOWER, op("slow down"));
    }

    @Test
    public void steppingAndViews() {
        ShapeCommand q = ShapeCommand.parse("turn it 90 degrees to the left");
        assertEquals(ShapeCommand.Op.TURN_BY, q.op);
        assertEquals(-90f, q.a, 0f);
        assertEquals(ShapeCommand.Op.TILT_BY, op("tilt it forward"));
        assertEquals(180f, ShapeCommand.parse("turn it upside down").a, 0f);
        assertEquals(ShapeCommand.Op.TURN_BY, op("turn it right a bit"));
        assertEquals(ShapeCommand.Op.TURN, op("turn it around"));
        assertEquals(90f, ShapeCommand.parse("show me the top").b, 0f);
        assertEquals(-90f, ShapeCommand.parse("view it from underneath").b, 0f);
        assertEquals(180f, ShapeCommand.parse("show me the back").a, 0f);
        assertEquals(ShapeCommand.Op.VIEW, op("reset the view"));
        assertEquals(ShapeCommand.Op.VIEW, op("face me"));
        // still the old ones
        assertEquals(ShapeCommand.Op.LEFT, op("move it left"));
        assertEquals(ShapeCommand.Op.BIGGER, op("make it bigger"));
        assertNull(ShapeCommand.parse("what time is it"));
        assertNull(ShapeCommand.parse("turn on the lights"));
    }

    @Test
    public void everyCanonicalCommandIsUnderstood() {
        for (String c : ShapeCommand.CANONICAL) {
            assertNotNull(c, ShapeCommand.parse(c));
        }
        assertEquals("rotate it vertically", ai.dvine.agent.ViewIntent.read("<think></think>Rotate it vertically."));
        assertNull(ai.dvine.agent.ViewIntent.read("none"));
        assertTrue(ai.dvine.agent.ViewIntent.looksLikeOne("make it do cartwheels"));
    }

    @Test
    public void theDisplayTurnsAsTold() {
        ParticleDisplay d = new ParticleDisplay();
        d.drawing("x", SolidSpec.parse("body | box | red | middle centre | w40 h20 d20\nhead | sphere | red | above body | w10 h10 d10")
                .points(500, 2f), 0f, true);
        assertTrue(d.apply(ShapeCommand.parse("rotate it vertically")));
        assertTrue(d.apply(ShapeCommand.parse("stop rotating")));
        assertTrue(d.apply(ShapeCommand.parse("show me the top")));
    }
}
