package ai.ibiko.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Ibiko's design board: a brain's SVG is drawn to a picture on the device, with no image service. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SvgDrawTest {
    private static final String LOGO = "Sure, here it is:\n<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
            + "<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\"><stop offset=\"0\" stop-color=\"#ff0000\"/>"
            + "<stop offset=\"100%\" stop-color=\"#0000ff\"/></linearGradient></defs>"
            + "<rect x=\"0\" y=\"0\" width=\"100\" height=\"50\" fill=\"url(#g)\"/>"
            + "<circle cx=\"50\" cy=\"75\" r=\"20\" fill=\"#00ff00\"/>"
            + "<g transform=\"translate(80,80)\"><path d=\"M0 0 L10 0 L10 10 Z\" fill=\"black\"/></g>"
            + "<script>alert(1)</script></svg>\nEnjoy!";

    @Test
    public void findsTheDrawingInAReply() {
        assertTrue(SvgDraw.extract(LOGO).startsWith("<svg"));
        assertTrue(SvgDraw.extract(LOGO).endsWith("</svg>"));
        assertNull(SvgDraw.extract("I can't draw that."));
    }

    @Test
    public void drawsShapesGradientsAndPaths() {
        Bitmap b = SvgDraw.render(SvgDraw.extract(LOGO), 200);
        assertNotNull(b);
        int left = b.getPixel(4, 50), right = b.getPixel(196, 50);
        assertTrue("red on the left of the gradient", ((left >> 16) & 0xff) > 200 && (left & 0xff) < 60);
        assertTrue("blue on the right of the gradient", (right & 0xff) > 200 && ((right >> 16) & 0xff) < 60);
        int mid = b.getPixel(100, 150);
        assertTrue("the green circle", ((mid >> 8) & 0xff) > 200 && ((mid >> 16) & 0xff) < 60);
        assertEquals("the path in its group", 0xff000000, b.getPixel(176, 164));
        assertEquals("nothing drawn there", 0, b.getPixel(10, 190));
    }

    @Test
    public void readsColoursAndTransforms() {
        assertEquals(0xffff0000, SvgDraw.color("#f00"));
        assertEquals(0xff123456, SvgDraw.color("#123456"));
        assertEquals(0xff0a141e, SvgDraw.color("rgb(10, 20, 30)"));
        assertEquals(0xffffd700, SvgDraw.color("gold"));
        float[] p = {1, 1};
        SvgDraw.transform("translate(10 5) scale(2)").mapPoints(p);
        assertEquals(12f, p[0], 0.01f);
        assertEquals(7f, p[1], 0.01f);
    }

    @Test
    public void badDrawingsAreRefusedNotCrashed() {
        assertNull(SvgDraw.render("<svg><rect", 64));
        assertNull(SvgDraw.save("no drawing here", RuntimeEnvironment.getApplication().getFilesDir(), 64));
    }

    @Test
    public void savesThePictureAndTheDesign() {
        File dir = new File(RuntimeEnvironment.getApplication().getFilesDir(), "chat-images");
        File png = SvgDraw.save(LOGO, dir, 128);
        assertNotNull(png);
        assertTrue(png.isFile());
        assertTrue(new File(dir, png.getName().replace(".png", ".svg")).isFile());
    }

    @Test
    public void designsGoToTheBoard() {
        assertTrue(SvgDraw.designish("make an app icon for my bakery"));
        assertTrue(SvgDraw.designish("draw a flowchart of the login"));
        assertTrue(SvgDraw.designish("Design a logo"));
        assertFalse(SvgDraw.designish("a photo of a lion at sunset"));
    }
}
