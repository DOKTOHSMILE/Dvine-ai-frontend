package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DrawPromptTest {
    @Test
    public void findsWhatToDraw() {
        assertEquals("helicopter", DrawPrompt.subject("Draw me a helicopter"));
        assertEquals("red bicycle", DrawPrompt.subject("can you sketch a red bicycle please"));
        assertEquals("lighthouse at night", DrawPrompt.subject("show me a picture of a lighthouse at night."));
        assertEquals("owl", DrawPrompt.subject("paint an owl for me"));
        assertNull(DrawPrompt.subject("what is a helicopter"));
        assertNull(DrawPrompt.subject("draw"));
        assertTrue(DrawPrompt.system().contains("part | solid | colour | where | size") && DrawPrompt.system().endsWith("/no_think"));
        assertEquals("an owl", DrawPrompt.article("owl"));
        assertEquals("two cats", DrawPrompt.article("two cats"));
        assertEquals("a helicopter", DrawPrompt.article("helicopter"));
    }

    @Test
    public void buildsTheThingIn3dFromItsParts() {
        ai.dvine.render.field.SolidSpec d = DrawPrompt.read("base | sphere | white | middle centre | large\n"
                + "middle | sphere | white | above base | medium\nhead | sphere | white | above middle | small\n"
                + "nose | cone | orange | in front of head | tiny forward\neyes | sphere | navy | on head, left | tiny, both sides\n"
                + "not a part line\n");
        assertEquals(6, d.parts.size()); // the eyes are a pair
        ai.dvine.render.field.SolidSpec.Part base = d.parts.get(0), mid = d.parts.get(1), head = d.parts.get(2), nose = d.parts.get(3);
        assertTrue("stacked", head.y < mid.y && mid.y < base.y && Math.abs((base.y - base.h / 2) - (mid.y + mid.h / 2)) < 2f);
        assertTrue("nose in front", nose.z > head.z + head.d / 2 - 2f);
        assertEquals(2 * base.x - d.parts.get(4).x, d.parts.get(5).x, 0.01f); // mirrored
        ai.dvine.render.field.Shape s = d.points(4000, 2f);
        assertTrue(s.count > 3800);
        float minZ = 9, maxZ = -9, minY = 9, maxY = -9;
        for (int i = 0; i < s.count; i++) {
            minZ = Math.min(minZ, s.xyz[i * 3 + 2]);
            maxZ = Math.max(maxZ, s.xyz[i * 3 + 2]);
            minY = Math.min(minY, s.xyz[i * 3 + 1]);
            maxY = Math.max(maxY, s.xyz[i * 3 + 1]);
        }
        assertTrue("real depth: " + (maxZ - minZ), maxZ - minZ > 0.6f);
        assertTrue("fits", maxY - minY <= 2.01f && maxY - minY > 1.5f);
        assertTrue(DrawPrompt.read("circle 50 50 10 red fill").isEmpty());
    }

    @Test
    public void findsWhatToShape() {
        assertEquals("dragon", DrawPrompt.formSubject("form a dragon"));
        assertEquals("lion", DrawPrompt.formSubject("build me a lion in 3D"));
        assertEquals("star", DrawPrompt.formSubject("turn into a star"));
        assertNull(DrawPrompt.formSubject("make it bigger"));
        assertNull(DrawPrompt.formSubject("make a note"));
        assertNull(DrawPrompt.formSubject("make your hair longer"));
        assertTrue(DrawPrompt.in3d("draw a car in 3d"));
        assertEquals("car", DrawPrompt.subject("draw a car in 3d"));
    }

    @Test
    public void changesTheThingOnShow() {
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.BIGGER, ai.dvine.render.field.ShapeCommand.parse("make it bigger").op);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.SPIN, ai.dvine.render.field.ShapeCommand.parse("spin it").op);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.TURN, ai.dvine.render.field.ShapeCommand.parse("turn it around").op);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.STOP, ai.dvine.render.field.ShapeCommand.parse("stop spinning").op);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.LEFT, ai.dvine.render.field.ShapeCommand.parse("move it left").op);
        assertEquals(0xFFF2453D, ai.dvine.render.field.ShapeCommand.parse("make it red").colour);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.SCATTER, ai.dvine.render.field.ShapeCommand.parse("scatter it").op);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.GATHER, ai.dvine.render.field.ShapeCommand.parse("put it back together").op);
        assertEquals(ai.dvine.render.field.ShapeCommand.Op.CLEAR, ai.dvine.render.field.ShapeCommand.parse("clear the drawing").op);
        assertNull(ai.dvine.render.field.ShapeCommand.parse("make your hair bigger"));
        assertNull(ai.dvine.render.field.ShapeCommand.parse("make it sparkly"));
        // on the display: nothing shown, nothing changed; then size, colour and scatter take effect
        ai.dvine.render.field.ParticleDisplay pd = new ai.dvine.render.field.ParticleDisplay();
        assertTrue(!pd.apply(ai.dvine.render.field.ShapeCommand.parse("spin it")));
        ai.dvine.render.field.Shape s = DrawPrompt.read("ball | sphere | white | middle centre | large").points(500, 2f);
        pd.drawing("ball", s, 90f, true);
        assertTrue(pd.apply(ai.dvine.render.field.ShapeCommand.parse("make it red")));
        assertEquals("ball", pd.drawingName());
        ai.dvine.render.field.Shape red = ai.dvine.render.field.ShapeCommand.tint(s, 0xFFF2453D);
        assertTrue(red.rgb[0] > red.rgb[2] * 2);
        assertTrue(pd.apply(ai.dvine.render.field.ShapeCommand.parse("clear it")));
        assertTrue(!pd.drawingShown());
    }

    @Test
    public void findsTheReferencePicture() {
        assertTrue(ai.dvine.render.field.PictureFinder.searchUrl("red bicycle", 480).contains("gsrsearch=red+bicycle"));
        String json = "{\"query\":{\"pages\":[{\"title\":\"Lighthouse\",\"thumbnail\":{\"source\":"
                + "\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/L.jpg/480px-L.jpg\",\"width\":480}}]}}";
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/L.jpg/480px-L.jpg",
                ai.dvine.render.field.PictureFinder.thumbnail(json));
        assertNull(ai.dvine.render.field.PictureFinder.thumbnail("{\"thumbnail\":{\"source\":\"https://evil.example/x.jpg\"}}"));
    }
}
