package ai.dvine;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Boots the real Activity on a simulated Android 14 so start-up crashes show up in CI. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LaunchTest {
    @Test(timeout = 120000)
    public void startsAndDrawsWithoutCrashing() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        MainActivity a = c.get();
        assertNotNull(a);
        android.view.View root = a.getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        root.draw(new android.graphics.Canvas(bmp));
        c.pause().stop().destroy();
    }
}
