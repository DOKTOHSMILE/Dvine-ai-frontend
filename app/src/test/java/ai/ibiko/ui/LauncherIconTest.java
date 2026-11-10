package ai.ibiko.ui;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
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
 * Ibiko's launcher icon, drawn from its layers: the robot inside the adaptive icon's safe circle, and the same picture
 * as the PNG icons for launchers that read those (written to build/icons, copied into the mipmap folders).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherIconTest {
    static final File OUT = new File("build/icons");

    private static Bitmap layers(int size) {
        android.content.Context c = RuntimeEnvironment.getApplication();
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        for (int id : new int[] {ai.ibiko.R.drawable.ic_launcher_background, ai.ibiko.R.drawable.ic_launcher_foreground}) {
            Drawable d = c.getDrawable(id);
            d.setBounds(0, 0, size, size);
            d.draw(canvas);
        }
        return b;
    }

    /** The middle 72 of the 108 units (what a launcher shows), at {@code size}, cut to a circle or a rounded square. */
    private static Bitmap legacy(int size, boolean round) {
        int full = Math.round(size * 108f / 72f);
        Bitmap all = layers(full);
        int off = (full - size) / 2;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path mask = new Path();
        if (round) {
            mask.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        } else {
            mask.addRoundRect(new RectF(0, 0, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW);
        }
        c.drawPath(mask, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(all, -off, -off, p);
        return out;
    }

    private static void save(Bitmap b, String name) throws Exception {
        OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    @Test
    public void theRobotSitsInsideTheSafeCircleAndThePngsAreMade() throws Exception {
        Bitmap big = layers(432);
        save(big, "adaptive_432.png");
        // nothing of the robot outside the 66-unit safe circle: compare with the background alone
        android.content.Context c = RuntimeEnvironment.getApplication();
        Bitmap fg = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888);
        Drawable d = c.getDrawable(ai.ibiko.R.drawable.ic_launcher_foreground);
        d.setBounds(0, 0, 432, 432);
        d.draw(new Canvas(fg));
        float r = 33f * 4f, cx = 216f;
        int outside = 0, inside = 0;
        for (int y = 0; y < 432; y += 2) {
            for (int x = 0; x < 432; x += 2) {
                if ((fg.getPixel(x, y) >>> 24) > 16) {
                    if (Math.hypot(x - cx, y - cx) > r) {
                        outside++;
                    } else {
                        inside++;
                    }
                }
            }
        }
        assertTrue("the robot is drawn", inside > 2000);
        assertTrue("nothing outside the safe circle: " + outside, outside == 0);
        String[] dens = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < dens.length; i++) {
            save(legacy(sizes[i], false), "mipmap-" + dens[i] + "_ic_launcher.png");
            save(legacy(sizes[i], true), "mipmap-" + dens[i] + "_ic_launcher_round.png");
        }
        save(legacy(512, false), "store_512.png");
    }
}
