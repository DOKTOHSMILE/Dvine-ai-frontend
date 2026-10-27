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
        assertTrue(DrawPrompt.system().contains("part | shape | colour | where | size") && DrawPrompt.system().endsWith("/no_think"));
        assertEquals("an owl", DrawPrompt.article("owl"));
        assertEquals("two cats", DrawPrompt.article("two cats"));
        assertEquals("a helicopter", DrawPrompt.article("helicopter"));
    }

    @Test
    public void laysOutThePartsFromWords() {
        ai.dvine.render.field.DrawSpec d = DrawPrompt.read("body | circle | white | lower centre | large\n"
                + "head | circle | white | above body | medium\nhat | box | red | above head | small\n"
                + "nose | triangle | orange | on head | tiny right\nnot a part line\n");
        assertEquals(4, d.items.size());
        float[] body = d.items.get(0).v, head = d.items.get(1).v;
        assertTrue("head sits on the body", head[1] < body[1] && Math.abs((body[1] - body[2]) - (head[1] + head[2])) < 2f);
        assertEquals(body[0], head[0], 0.01f);
        float[] hat = d.items.get(2).v;
        assertTrue("hat above the head", hat[1] + hat[3] <= head[1] - head[2] + 1.5f);
        // shapes written directly still work
        assertEquals(1, DrawPrompt.read("circle 50 50 10 red fill").items.size());
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
