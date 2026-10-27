package ai.dvine.render.field;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The pictures the particles form, drawn with Android's real canvas: the download bar, text and a drawing. With
 * DVINE_FIELD_OUT set, writes each as the particles' points (x y r g b per line) for a look, and draws the shape files
 * (*.txt) found there too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RasterTest {
    @Test
    public void drawsTheBarTextAndDrawings() throws Exception {
        Shape bar = Raster.progress("DOWNLOADING QWEN3", 0.42f, "460 MB of 1.1 GB · 2.0 MB/s · 5 min left", 0xFF2EE6FF,
                ParticleDisplay.PANEL_POINTS, 2f);
        Shape words = Raster.text("Here's a lighthouse, drawn in particles.", 0xFFE4E8EA, ParticleDisplay.CAPTION_POINTS, 2f, 3);
        Shape drawing = Raster.drawing(DrawSpec.parse("circle 50 40 20 yellow fill\nrect 20 70 60 10 brown fill\nline 10 90 90 90 white 2"),
                ParticleDisplay.DRAWING_POINTS, 2f);
        assertTrue("bar " + bar.count, bar.count > 1500 && bar.count <= ParticleDisplay.PANEL_POINTS * 2);
        assertTrue("words " + words.count, words.count > 800);
        assertTrue("drawing " + drawing.count, drawing.count > 1500);
        // the bar fills from the left: more points left of the middle of the track than right of it
        int left = 0, right = 0;
        for (int i = 0; i < bar.count; i++) {
            float y = bar.xyz[i * 3 + 1], x = bar.xyz[i * 3];
            if (Math.abs(y) < 0.05f) {
                if (x < 0f) {
                    left++;
                } else {
                    right++;
                }
            }
        }
        assertTrue(left + " vs " + right, left > right * 2);
        String out = System.getenv("DVINE_FIELD_OUT");
        if (out == null) {
            return;
        }
        dump(bar, new File(out, "bar.pts"));
        dump(words, new File(out, "words.pts"));
        dump(Raster.text("Finding a picture of a lighthouse\u2026", 0xFFFFE08A, ParticleDisplay.THOUGHT_POINTS, 2f, 3), new File(out, "thought.pts"));
        File photo = new File(out, "photo.png");
        if (photo.isFile()) {
            android.graphics.Bitmap bm = Raster.decode(java.nio.file.Files.readAllBytes(photo.toPath()));
            Shape p = Raster.image(bm, ParticleDisplay.DRAWING_POINTS, 2f);
            assertTrue("photo " + p.count, p.count > 2000);
            dump(p, new File(out, "photo.pts"));
        }
        File[] specs = new File(out).listFiles((d, n) -> n.endsWith(".txt"));
        if (specs != null) {
            for (File f : specs) {
                String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
                dump(Raster.drawing(ai.dvine.llm.DrawPrompt.read(text), ParticleDisplay.DRAWING_POINTS, 2f), new File(out, f.getName().replace(".txt", ".pts")));
            }
        }
    }

    private static void dump(Shape s, File f) throws Exception {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.count; i++) {
            b.append(s.xyz[i * 3]).append(' ').append(s.xyz[i * 3 + 1]).append(' ')
                    .append(s.rgb[i * 3]).append(' ').append(s.rgb[i * 3 + 1]).append(' ').append(s.rgb[i * 3 + 2]).append('\n');
        }
        java.nio.file.Files.write(f.toPath(), b.toString().getBytes("UTF-8"));
    }
}
