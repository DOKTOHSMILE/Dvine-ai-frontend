package ai.dvine.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The live reply's typewriter: whole words, fast, and catching up when the model is ahead. */
public class TypewriterTest {
    @Test
    public void typesWholeWordsFastAndCatchesUp() {
        String t = "The capital of France is Paris, a city of light.";
        int at = ChatPane.nextStop(t, 0);
        assertTrue("stop " + at, at >= ChatPane.LIVE_CHARS_PER_TICK);
        assertTrue("stops at the end of a word: " + t.substring(0, at), at == t.length() || t.charAt(at) == ' ');
        // a reply far ahead of what is shown is caught up quickly: under 20 frames for 4,000 characters
        StringBuilder b = new StringBuilder();
        while (b.length() < 4000) {
            b.append("word ");
        }
        String big = b.toString();
        int shown = 0, frames = 0;
        while (shown < big.length()) {
            shown = ChatPane.nextStop(big, shown);
            frames++;
        }
        assertTrue("frames " + frames, frames < 20);
        assertEquals(big.length(), shown);
        // when it keeps pace, about 500 characters a second (8 a frame at 60 frames a second, rounded up to a word)
        assertTrue(1000 / ChatPane.LIVE_TICK_MS * ChatPane.LIVE_CHARS_PER_TICK >= 480);
    }
}
