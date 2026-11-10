package ai.ibiko.chat.ui;

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

    @Test
    public void theBodyDragsTheSheetToo() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        TextView body = new TextView(a);
        body.setText("Reading your message");
        Dialog d = BottomSheet.showView(a, "Working", body);
        int screen = a.getResources().getDisplayMetrics().heightPixels;
        ViewGroup sheet = (ViewGroup) ((ViewGroup) d.getWindow().getDecorView().findViewById(android.R.id.content)).getChildAt(0);
        View scroll = sheet.getChildAt(sheet.getChildCount() - 1); // the body, below the grip and the title
        assertTrue(scroll instanceof BottomSheet.DragScroll);
        long t = SystemClock.uptimeMillis();
        // pulled up from the body: towards full screen
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 50, 400, 0));
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 50, 300, 0));
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_MOVE, 50, 100, 0));
        assertTrue("it grew: " + BottomSheet.heightOf(d), BottomSheet.heightOf(d) > screen / 2);
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, 50, 100, 0));
        assertTrue("settles full: " + BottomSheet.heightOf(d), BottomSheet.heightOf(d) > screen * 0.9f);
        // pulled down from the body (the list at its top): back down, and closed when pulled far
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 100, MotionEvent.ACTION_DOWN, 50, 100, 0));
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 120, MotionEvent.ACTION_MOVE, 50, 200, 0));
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 140, MotionEvent.ACTION_MOVE, 50, 100 + screen * 0.85f, 0));
        scroll.dispatchTouchEvent(MotionEvent.obtain(t, t + 160, MotionEvent.ACTION_UP, 50, 100 + screen * 0.85f, 0));
        assertTrue("closed", !d.isShowing());
    }
}
