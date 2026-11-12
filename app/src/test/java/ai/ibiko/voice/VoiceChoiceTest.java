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
        app.getSharedPreferences("ibiko", 0).edit().putString("voice_pack", "kokoro").apply();
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

    /** One voice, not a setting: the child's (Kokoro's Sky, played higher), at normal speed; no Voice page in Settings. */
    @Test
    public void theChildsVoiceIsTheOnlyVoiceAndNotASetting() throws Exception {
        assertEquals(java.util.Arrays.asList(VoicePacks.KOKORO), VoicePacks.ALL);
        assertEquals("af_sky", TalkBridge.CHILD_SPEAKER);
        assertEquals(1.32f, TalkBridge.CHILD_PITCH, 0.0001f);
        java.lang.reflect.Field f = ai.ibiko.chat.ui.SettingsActivity.class.getDeclaredField("SECTIONS");
        f.setAccessible(true);
        for (String[] section : (String[][]) f.get(null)) {
            assertFalse("no Voice page", "Voice".equals(section[0]));
        }
        java.lang.reflect.Field sc = ai.ibiko.chat.ui.SettingsActivity.class.getDeclaredField("SCREENS");
        sc.setAccessible(true);
        assertEquals("every section still opens its own screen", ((String[][]) f.get(null)).length, ((Class<?>[]) sc.get(null)).length);
        // the voices an older version downloaded (Piper) are removed, Kokoro stays
        File files = RuntimeEnvironment.getApplication().getFilesDir();
        File piper = VoicePacks.folder(files, VoicePacks.PIPER_RYAN), kokoro = VoicePacks.folder(files, VoicePacks.KOKORO);
        piper.mkdirs();
        kokoro.mkdirs();
        Files.write(new File(piper, "model.onnx").toPath(), new byte[] {1});
        Files.write(new File(VoicePacks.root(files), VoicePacks.PIPER_RYAN.key + ".ready").toPath(), new byte[] {1});
        assertEquals(1, VoicePacks.dropOthers(files));
        assertFalse(piper.exists());
        assertFalse(new File(VoicePacks.root(files), VoicePacks.PIPER_RYAN.key + ".ready").exists());
        assertTrue(kokoro.exists());
    }
}
