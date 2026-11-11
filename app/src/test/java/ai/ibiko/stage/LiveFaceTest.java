package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** The live face: it moves on its own (the eyes glide and blink), each kind of work has its own look. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveFaceTest {
    @Test
    public void eachStepHasItsLook() {
        assertEquals(LiveFace.Mood.READING, LiveFace.moodOf("Read app/Main.java (40 lines)"));
        assertEquals(LiveFace.Mood.READING, LiveFace.moodOf("Explored 2 files"));
        assertEquals(LiveFace.Mood.WRITING, LiveFace.moodOf("Edited 2 files"));
        assertEquals(LiveFace.Mood.WORKING, LiveFace.moodOf("Ran 3 commands"));
        assertEquals(LiveFace.Mood.WORKING, LiveFace.moodOf("Cloning DOKTOHSMILE/app"));
        assertEquals(LiveFace.Mood.THINKING, LiveFace.moodOf("Thinking…"));
    }

    /** Where the eyes are in a frame: the middle of the white-hot pixels. */
    private static int[] eyes(Bitmap b) {
        long sx = 0, sy = 0, n = 0;
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                int p = b.getPixel(x, y);
                if (((p >> 16) & 0xff) > 150 && (p & 0xff) > 200 && ((p >> 8) & 0xff) > 200) {
                    sx += x;
                    sy += y;
                    n++;
                }
            }
        }
        return n == 0 ? new int[] {-1, -1, 0} : new int[] {(int) (sx / n), (int) (sy / n), (int) n};
    }

    @Test
    public void itIsAliveAndNeverStill() throws Exception {
        LiveFace f = new LiveFace(RuntimeEnvironment.getApplication());
        int size = 240;
        f.measure(size, size);
        f.layout(0, 0, size, size);
        File out = new File("build/face");
        out.mkdirs();
        Set<String> where = new HashSet<String>();
        int shut = 0;
        for (LiveFace.Mood m : LiveFace.Mood.values()) {
            f.setMood(m);
            Bitmap strip = Bitmap.createBitmap(size * 8, size, Bitmap.Config.ARGB_8888);
            Canvas sc = new Canvas(strip);
            for (int i = 0; i < 40; i++) {
                ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(120));
                Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                f.draw(new Canvas(b));
                int[] e = eyes(b);
                if (e[2] < 40) {
                    shut++; // a blink (or the smile's thin arcs)
                } else {
                    where.add(m + ":" + e[0] / 6 + "," + e[1] / 6);
                }
                if (i % 5 == 0) {
                    sc.drawBitmap(b, (i / 5) * size, 0, null);
                }
            }
            try (FileOutputStream o = new FileOutputStream(new File(out, "face_" + m.name().toLowerCase() + ".png"))) {
                strip.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
        }
        assertTrue("the eyes go to many places: " + where.size(), where.size() > 25);
        assertTrue("it blinks (or smiles) now and then: " + shut, shut > 0);
        assertTrue(SystemClock.uptimeMillis() > 0);
    }
}
