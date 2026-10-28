package ai.dvine.render.field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The nano-particle display: pictures from pixels, the springs that form them, and the drawing language. */
public class ParticleDisplayTest {
    private static Shape square(int size, int maxPoints) {
        int[] px = new int[size * size];
        for (int y = size / 4; y < size * 3 / 4; y++) {
            for (int x = size / 4; x < size * 3 / 4; x++) {
                px[y * size + x] = 0xFF40C0FF;
            }
        }
        return Shape.fromPixels(px, size, size, maxPoints, 2f, 0.1f);
    }

    @Test
    public void litPixelsBecomeCentredPoints() {
        Shape s = square(100, 800);
        assertTrue(s.count > 300 && s.count <= 1600);
        float minX = 9, maxX = -9, minY = 9, maxY = -9;
        for (int i = 0; i < s.count; i++) {
            minX = Math.min(minX, s.xyz[i * 3]);
            maxX = Math.max(maxX, s.xyz[i * 3]);
            minY = Math.min(minY, s.xyz[i * 3 + 1]);
            maxY = Math.max(maxY, s.xyz[i * 3 + 1]);
        }
        // the lit square is the middle half of a picture 2 units wide: about -0.5..0.5 both ways
        assertEquals(-0.5f, minX, 0.05f);
        assertEquals(0.5f, maxX, 0.06f);
        assertEquals(0f, (minY + maxY) / 2f, 0.05f);
        assertEquals(0.25f, s.rgb[0], 0.01f);
        assertEquals(0, Shape.fromPixels(new int[100], 10, 10, 50, 2f, 0f).count); // nothing lit, nothing to show
    }

    @Test
    public void particlesFormThePictureThenFadeAway() {
        ParticleField f = new ParticleField(1000);
        f.show(square(80, 900));
        float start = f.settle();
        for (int i = 0; i < 120; i++) {
            f.step(1f / 60f);
        }
        assertTrue(start > 0.05f);
        assertTrue("formed: " + f.settle(), f.settle() < 0.01f);
        float[] out = new float[1000 * ParticleField.STRIDE];
        int n = f.pack(out);
        assertEquals(f.used(), n);
        assertEquals(0.25f, out[3], 0.02f); // red, settled onto the picture's colour
        f.show(null);
        for (int i = 0; i < 90; i++) {
            f.step(1f / 60f);
        }
        assertEquals(0, f.pack(out));
    }

    @Test
    public void aBiggerPictureIsThinnedEvenlyIntoThePool() {
        ParticleField f = new ParticleField(200);
        Shape s = square(100, 2500);
        f.show(s);
        assertEquals(200, f.used());
        for (int i = 0; i < 200; i++) {
            f.step(1f / 60f);
        }
        float[] out = new float[200 * ParticleField.STRIDE];
        int n = f.pack(out);
        float minY = 9, maxY = -9;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, out[i * ParticleField.STRIDE + 1]);
            maxY = Math.max(maxY, out[i * ParticleField.STRIDE + 1]);
        }
        assertTrue("spans the whole square", maxY - minY > 0.9f);
    }

    @Test
    public void readsTheShapesTheModelWrites() {
        DrawSpec d = DrawSpec.parse("Here is your drawing:\n```\n1. circle 50 40 20 yellow fill\n"
                + "ellipse 50, 60, 30, 12, #8899aa\nrect 20 70 60 10 brown fill\nline 10 90 90 90 white 3\n"
                + "polygon red fill 30 60 50 40 70 60\ntriangle green 10 10 20 10\ntext 50 95 6 white A helicopter\n"
                + "circle 50 forty 5 red\n```");
        assertEquals(6, d.items.size()); // the prose, the fences, the 4-number triangle and the bad circle are skipped
        assertEquals(DrawSpec.Kind.CIRCLE, d.items.get(0).kind);
        assertTrue(d.items.get(0).fill);
        assertEquals(0xFFFFD23F, d.items.get(0).color);
        assertEquals(0xFF8899AA, d.items.get(1).color);
        assertEquals(3f, d.items.get(3).v[4], 0f);
        assertEquals(6, d.items.get(4).v.length);
        assertEquals("A helicopter", d.items.get(5).text);
        assertTrue(DrawSpec.parse(null).isEmpty());
        assertEquals(Integer.valueOf(0xFFFFFFFF), DrawSpec.Palette.colour("#fff"));
        assertNull(DrawSpec.Palette.colour("sparkly"));
    }

    @Test
    public void wordsSwitchInPlaceInsteadOfFlying() {
        ParticleField f = new ParticleField(1000);
        f.setCalm(true);
        f.show(square(80, 900));
        f.step(1f / 60f);
        assertTrue("already in place", f.settle() < 1e-4f);
        f.show(square(60, 900)); // the next percent, the next sentence
        f.step(1f / 60f);
        assertTrue("still in place: " + f.settle(), f.settle() < 1e-4f);
        float[] out = new float[1000 * ParticleField.STRIDE];
        int n = f.pack(out);
        float y0 = out[1];
        f.step(1f / 60f);
        f.pack(out);
        assertEquals("no shimmer on words", y0, out[1], 0f);
        assertTrue(n > 0);
    }

    @Test
    public void samplesAsDenselyAsTheBudgetAllows() {
        Shape s = square(100, 1000); // 2500 lit pixels, 1000 allowed: a step of about 1.6, not 2
        assertTrue("" + s.count, s.count > 800 && s.count <= 1100);
    }

    @Test
    public void skinKeepsDarkHairAndDropsTheBlackBackdrop() {
        int[] px = new int[] {0xFF1C120C, 0xFF000000, 0xFFC47A4A, 0x00000000};
        Shape s = Shape.skin(px, 2, 2, 8, 2f);
        assertEquals(2, s.count);
        boolean hair = false, tone = false;
        for (int i = 0; i < s.count; i++) {
            float r = s.rgb[i * 3], g = s.rgb[i * 3 + 1], b = s.rgb[i * 3 + 2];
            if (r < 0.2f && g < 0.15f && b < 0.1f) {
                hair = true;
            }
            if (r > 0.5f) {
                tone = true;
            }
        }
        assertTrue(hair);
        assertTrue(tone);
        assertTrue(s.grid > 0f);
        assertEquals(0, Shape.skin(new int[] {0xFF000000, 0xFF010101}, 1, 2, 10, 1f).count);
    }

    @Test
    public void aFullyLitPictureIsOnePointPerPixel() {
        int[] px = new int[8 * 8];
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF808080;
        }
        Shape s = Shape.skin(px, 8, 8, 1000, 2f);
        assertEquals(64, s.count);
        assertEquals(2f / 8f, s.grid, 0.001f);
    }

    @Test
    public void aPhotographIsMarkedUntilItIsCleared() {
        ParticleDisplay d = new ParticleDisplay();
        int[] px = new int[16];
        for (int i = 0; i < px.length; i++) {
            px[i] = i < 12 ? 0xFFC47A4A : 0xFF000000;
        }
        d.showPhotograph("face", Shape.skin(px, 4, 4, 64, 1.6f));
        assertTrue(d.photographShown());
        assertEquals("face", d.drawingName());
        d.drawing(null, null, 0f);
        assertTrue(!d.photographShown());
    }
}
