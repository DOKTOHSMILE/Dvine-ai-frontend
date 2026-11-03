package ai.dvine.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The steps sheet opens at half the screen and goes to full screen from its grip. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BottomSheetTest {
    @Test
    public void opensAtHalfAndTheGripTakesItToFullScreen() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        TextView body = new TextView(a);
        body.setText("Read app/Main.java");
        Dialog d = BottomSheet.showView(a, "Working", body);
        int screen = a.getResources().getDisplayMetrics().heightPixels;
        assertEquals(screen / 2, BottomSheet.heightOf(d));
        View grip = ((ViewGroup) d.getWindow().getDecorView().findViewById(android.R.id.content)).getChildAt(0);
        grip = ((ViewGroup) grip).getChildAt(0); // the sheet's first child: the grip strip
        long t = SystemClock.uptimeMillis();
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10, 10, 0));
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, 10, 10, 0));
        assertTrue("full: " + BottomSheet.heightOf(d), BottomSheet.heightOf(d) > screen * 0.9f);
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t + 100, MotionEvent.ACTION_DOWN, 10, 10, 0));
        grip.dispatchTouchEvent(MotionEvent.obtain(t, t + 150, MotionEvent.ACTION_UP, 10, 10, 0));
        assertEquals(screen / 2, BottomSheet.heightOf(d)); // a second tap: back to half
    }
}
