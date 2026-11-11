package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** One sound at a time: whatever starts silences what was playing; skipping moves by whole sentences. */
public class AudioGateTest {
    @Test
    public void whatStartsSilencesWhatWasPlaying() {
        List<String> silenced = new ArrayList<String>();
        AudioGate.Owner reply = () -> silenced.add("reply");
        AudioGate.Owner preview = () -> silenced.add("preview");
        AudioGate.Owner video = () -> silenced.add("video");
        AudioGate.take(reply);
        AudioGate.take(reply);
        assertEquals("taking it again silences nothing", 0, silenced.size());
        AudioGate.take(preview);
        AudioGate.take(video);
        assertEquals(Arrays.asList("reply", "preview"), silenced);
        AudioGate.silenceAll(); // the microphone opens
        assertEquals(Arrays.asList("reply", "preview", "video"), silenced);
        assertNull(AudioGate.holder());
        AudioGate.take(reply);
        AudioGate.leave(reply);
        assertNull(AudioGate.holder());
    }

    @Test
    public void backAndForwardFifteenSecondsMoveWholeSentences() {
        List<String> s = new ArrayList<String>();
        for (int i = 0; i < 20; i++) {
            s.add("This sentence is about fifty letters long, give or take."); // 56 letters: ~3.7 s at 15 a second
        }
        int ahead = ai.ibiko.main.ReadAloudTestAccess.skipTo(s, 5, 15, 1f);
        assertEquals("15 s at 1x is 225 letters: 5 sentences of 56", 10, ahead);
        assertEquals("back the same 5", 0, ai.ibiko.main.ReadAloudTestAccess.skipTo(s, 5, -15, 1f));
        assertEquals(5, ai.ibiko.main.ReadAloudTestAccess.skipTo(s, 10, -15, 1f));
        assertEquals("never before the first", 0, ai.ibiko.main.ReadAloudTestAccess.skipTo(s, 1, -15, 1f));
        assertEquals("never past the last", 19, ai.ibiko.main.ReadAloudTestAccess.skipTo(s, 18, 15, 1f));
        assertEquals("at 2x the same 15 s covers twice the text: 9 sentences", 14, ai.ibiko.main.ReadAloudTestAccess.skipTo(s, 5, 15, 2f));
    }
}
