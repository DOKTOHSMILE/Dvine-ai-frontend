package ai.ibiko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;


/** Every dialog in one standard style; the slow GitHub list behind a waiting dialog that a second tap cannot repeat. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DialogStyleTest {
    static final File OUT = new File("build/screens");

    private static void shoot(Dialog d, String name) throws Exception {
        android.view.View root = d.getWindow().getDecorView();
        int w = 1080, h = 900;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.AT_MOST));
        root.layout(0, 0, w, root.getMeasuredHeight());
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, Math.max(1, root.getMeasuredHeight()),
                android.graphics.Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF444444);
        root.draw(new android.graphics.Canvas(bmp));
        OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name + ".png"))) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    private void run(String suffix) throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        MainActivity a = c.get();
        androidx.appcompat.app.AlertDialog plain = new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Delete this chat?").setMessage("Its messages go; what Ibiko learned stays.")
                .setPositiveButton("Delete", null).setNegativeButton("Cancel", null).show();
        ShadowLooper.idleMainLooper();
        android.widget.Button yes = plain.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        assertEquals("accent, bold, not shouting", a.getColor(ai.ibiko.R.color.accent), yes.getCurrentTextColor());
        assertFalse(yes.isAllCaps());
        shoot(plain, "30_dialog" + suffix);
        plain.dismiss();

        // the GitHub list: a standard waiting dialog, with Cancel
        final boolean[] cancelled = {false};
        androidx.appcompat.app.AlertDialog waiting = ai.ibiko.chat.ui.Waiting.show(a, "Loading your repositories from GitHub\u2026",
                () -> cancelled[0] = true);
        ShadowLooper.idleMainLooper();
        assertTrue(waiting.isShowing());
        shoot(waiting, "31_waiting" + suffix);
        waiting.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertTrue(cancelled[0] && !waiting.isShowing());
        // while it loads, a second tap starts nothing: no second fetch, no second dialog
        a.toolsMenu.reposLoading = true;
        Dialog before = ShadowDialog.getLatestDialog();
        a.toolsMenu.chooseRepos("ibiko", ai.ibiko.suit.ChatTools.defaults("ibiko"), new android.widget.TextView(a));
        ShadowLooper.idleMainLooper();
        assertTrue("nothing new opened", ShadowDialog.getLatestDialog() == before);
        a.toolsMenu.reposLoading = false;
        c.pause().stop().destroy();
    }

    @Test(timeout = 120000)
    @Config(sdk = 35, qualifiers = "w411dp-h891dp-night-xxhdpi")
    public void dialogsAtNight() throws Exception {
        run("_night");
    }

    @Test(timeout = 120000)
    public void dialogsByDay() throws Exception {
        run("");
    }
}
