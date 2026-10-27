package ai.dvine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

/** Draws the real start screen to a PNG when SCREEN_OUT is set (to see the stage without a phone). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenPreview {
    @Test(timeout = 180000)
    public void preview() throws Exception {
        String out = System.getenv("SCREEN_OUT");
        if (out == null) {
            return;
        }
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        MainActivity a = c.get();
        String said = System.getenv("SCREEN_SAY");
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS);
        if (said != null) {
            java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod("answer", String.class);
            m.setAccessible(true);
            for (String s : said.split("\\|")) {
                m.invoke(a, s);
                ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        android.view.View root = a.getWindow().getDecorView();
        int w = root.getWidth() > 0 ? root.getWidth() : 1080, h = root.getHeight() > 0 ? root.getHeight() : 2340;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        try (java.io.FileOutputStream f = new java.io.FileOutputStream(out)) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, f);
        }
        c.pause().stop().destroy();
    }
}
