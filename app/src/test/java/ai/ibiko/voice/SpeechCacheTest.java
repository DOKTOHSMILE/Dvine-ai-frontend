package ai.ibiko.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

/** A reply's voice is kept under its message's id for a week, in the order it is said, and goes with its chat. */
public class SpeechCacheTest {
    @Test
    public void keptForAWeekThenCleared() throws Exception {
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
        assertFalse("older than a week: made again when wanted", c.has(key));
        assertNull(c.get(key));
        assertFalse(f.exists());
        assertEquals(7L * 24 * 60 * 60 * 1000, SpeechCache.KEEP_MS);
    }

    @Test
    public void eachMessageKeepsItsOwnVoiceInOrderAndGoesWithItsChat() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        SpeechCache c = new SpeechCache(files);
        String one = NeuralVoice.madeKey("First sentence.", 4, 1f), two = NeuralVoice.madeKey("Second sentence.", 4, 1f);
        c.put("g7/mA", one, new float[]{0.1f});
        c.put("g7/mA", two, new float[]{0.2f});
        c.put("g8/mB", one, new float[]{0.3f});
        assertTrue(c.has("g7/mA", one));
        assertFalse("another message's voice is not this one's", c.has("g7/mZ", one));
        List<File> order = c.ordered("g7/mA");
        assertEquals(2, order.size());
        assertTrue(order.get(0).getName().startsWith("000-") && order.get(1).getName().startsWith("001-"));
        assertArrayEquals(new float[]{0.2f}, c.get("g7/mA", two), 0f);
        SpeechCache.forgetChat(files, "g7");
        assertFalse(c.has("g7/mA", one));
        assertFalse(new File(files, "voices/said/g7").exists());
        assertTrue("other chats keep theirs", c.has("g8/mB", one));
    }

    @Test
    public void theChildsVoiceIsALightVoicePlayedHigher() {
        assertTrue(TalkBridge.CHILD_PITCH > 1.2f && TalkBridge.CHILD_PITCH < 1.5f);
        assertTrue("Sky is in the pack", VoicePacks.byKey("kokoro").byName(TalkBridge.CHILD_SPEAKER) != null);
    }
}
