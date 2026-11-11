package ai.ibiko.ui;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Ibiko's launcher icon is the pilot's own art (tools/make_icon.py makes the layers and PNGs from it), its face smaller
 * on a teal background so every mask shows its curved corners: the eyes sit inside the safe circle every launcher mask
 * keeps, and the one-colour layer for themed icons covers the same eyes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherIconTest {
    static final File OUT = new File("build/icons");

    private static Bitmap draw(int id, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable d = RuntimeEnvironment.getApplication().getDrawable(id);
        d.setBounds(0, 0, size, size);
        d.draw(new Canvas(b));
        return b;
    }

    @Test
    public void theEyesSitInsideTheSafeCircleAndTheThemedLayerMatchesThem() throws Exception {
        int n = 432;
        Bitmap art = draw(ai.ibiko.R.drawable.ic_launcher_art, n);
        Bitmap mono = draw(ai.ibiko.R.drawable.ic_launcher_monochrome, n);
        OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(OUT, "adaptive_432.png"))) {
            art.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        float c = n / 2f, safe = 33f * n / 108f;
        int eyes = 0, outside = 0, covered = 0;
        for (int y = 0; y < n; y += 2) {
            for (int x = 0; x < n; x += 2) {
                int p = art.getPixel(x, y);
                int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                boolean eye = r > 200 && g > 230 && b > 230; // the white-hot middle of the glowing eyes
                if (!eye) {
                    continue;
                }
                eyes++;
                if (Math.hypot(x - c, y - c) > safe) {
                    outside++;
                }
                if ((mono.getPixel(x, y) >>> 24) > 128) {
                    covered++;
                }
            }
        }
        assertTrue("the eyes are drawn (the face is 46 of 108 units, with room for the background): " + eyes, eyes > 150);
        assertTrue("no eye outside the safe circle: " + outside, outside == 0);
        assertTrue("the themed layer covers the eyes: " + covered + " of " + eyes, covered > eyes * 0.8);
        for (String d : new String[] {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"}) {
            for (String name : new String[] {"ic_launcher.png", "ic_launcher_round.png"}) {
                assertTrue(d + "/" + name, new File("src/main/res/mipmap-" + d + "/" + name).length() > 500);
            }
        }
    }
}
