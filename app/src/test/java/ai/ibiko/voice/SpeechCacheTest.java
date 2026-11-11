package ai.ibiko.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** A reply's audio, made while it is read, is kept for a day and then cleared; past that it is made again. */
public class SpeechCacheTest {
    @Test
    public void keptForADayThenCleared() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        SpeechCache c = new SpeechCache(files);
        String key = NeuralVoice.madeKey("Hello there.", 4, 1f);
        float[] sound = {0.1f, -0.2f, 0.3f};
        assertNull(c.get(key));
        c.put(key, sound);
        assertTrue(c.has(key));
        assertArrayEquals(sound, c.get(key), 0f);
        File f = new File(files, "voices/said/" + SpeechCache.name(key));
        assertTrue(f.setLastModified(System.currentTimeMillis() - SpeechCache.KEEP_MS - 1000));
        assertFalse("older than a day: made again when wanted", c.has(key));
        assertNull(c.get(key));
        assertFalse(f.exists());
    }

    @Test
    public void theChildsVoiceIsALightVoicePlayedHigher() {
        assertTrue(TalkBridge.CHILD_PITCH > 1.2f && TalkBridge.CHILD_PITCH < 1.5f);
        assertTrue("Sky is in the pack", VoicePacks.byKey("kokoro").byName(TalkBridge.CHILD_SPEAKER) != null);
    }
}
