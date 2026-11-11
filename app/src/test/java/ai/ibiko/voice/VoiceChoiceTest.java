package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.View;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** The human voice: a failure to start is told (no other voice reads in its place); a voice choice redraws in place. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VoiceChoiceTest {
    @Test
    public void aVoiceThatCantStartIsToldAndNoOtherVoiceReads() throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        File files = app.getFilesDir();
        // Kokoro on the device (its files in place), but no engine to run it here: as on a device whose library fails
        File d = VoicePacks.folder(files, VoicePacks.KOKORO);
        d.mkdirs();
        Files.write(new File(d, VoicePacks.KOKORO.model).toPath(), new byte[] {1});
        Files.write(new File(VoicePacks.root(files), "kokoro.ready").toPath(), VoicePacks.KOKORO.sha256.getBytes(StandardCharsets.UTF_8));
        assertTrue(VoicePacks.installed(files, VoicePacks.KOKORO));
        app.getSharedPreferences("dvine", 0).edit().putString("voice_pack", "kokoro").apply();
        TalkBridge t = new TalkBridge(app);
        File problem = new File(VoicePacks.root(files), NeuralVoice.PROBLEM);
        long until = System.currentTimeMillis() + 10000;
        while (!problem.isFile() && System.currentTimeMillis() < until) {
            Thread.sleep(50);
        }
        assertTrue("why it couldn't start is kept for Settings", problem.isFile());
        assertFalse(t.human());
        assertTrue(new String(Files.readAllBytes(problem.toPath()), StandardCharsets.UTF_8).contains("Kokoro"));
        t.shutdown();
    }

    @Test
    public void choosingAVoiceRedrawsOnlyTheVoiceOptions() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("dvine", 0).edit().putString("voice_gender", "male").apply();
        ActivityController<? extends ai.ibiko.chat.ui.SettingsActivity> c = Robolectric.buildActivity(ai.ibiko.chat.ui.SettingsVoiceActivity.class,
                new android.content.Intent(app, ai.ibiko.chat.ui.SettingsVoiceActivity.class)
                        .putExtra(ai.ibiko.chat.ui.SettingsActivity.EXTRA_SECTION, "Voice")).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View root = c.get().getWindow().getDecorView();
        ArrayList<View> found = new ArrayList<View>();
        root.findViewsWithText(found, "Speaking speed", View.FIND_VIEWS_WITH_TEXT);
        View speed = found.get(0);
        found.clear();
        root.findViewsWithText(found, "A woman's voice", View.FIND_VIEWS_WITH_TEXT);
        assertFalse(found.isEmpty());
        View chip = found.get(0);
        while (!chip.hasOnClickListeners()) {
            chip = (View) chip.getParent();
        }
        chip.performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("female", app.getSharedPreferences("dvine", 0).getString("voice_gender", ""));
        assertFalse("the screen isn't rebuilt", c.get().isFinishing());
        found.clear();
        c.get().getWindow().getDecorView().findViewsWithText(found, "Speaking speed", View.FIND_VIEWS_WITH_TEXT);
        assertSame("the rest of the page stays exactly as it was", speed, found.get(0));
        assertNotNull(c.get().getWindow().getDecorView());
        c.pause().stop().destroy();
    }
}
