package ai.dvine.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A pause keeps what was said: the next words go after it. */
public class DictationJoinTest {
    @Test
    public void wordsAfterAPauseGoAfterTheEarlierOnes() {
        String box = Dictation.join("", "hello there");
        // the stretch ended: what is in the box is kept, and the next stretch is written after it
        box = Dictation.join(box, "how are you");
        assertEquals("hello there how are you", box);
        assertEquals("typed words stay", "Note: hello", Dictation.join("Note: ", "hello"));
        assertEquals("abc", Dictation.join("abc", ""));
    }

    @Test
    public void theVoiceLevelIsZeroToOne() {
        assertEquals(0f, ListenBridge.level(-5f), 0.001f);
        assertEquals(1f, ListenBridge.level(12f), 0.001f);
        assertEquals(0.5f, ListenBridge.level(4f), 0.001f);
    }
}
