package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Only sayable words reach the voice, in short parts, with clean samples: no jargon read out, no clicks or crackle. */
public class VoiceStabilityTest {
    @Test
    public void unsayableTextIsLeftOut() {
        String said = SentenceStream.speakable("Done. See app/src/main/java/ai/ibiko/MainActivity.java for it.");
        assertFalse(said, said.contains("/"));
        assertFalse(said, said.contains(".java"));
        assertEquals("", SentenceStream.speakable("```\nint x = 1;\n```"));
        assertEquals("", SentenceStream.speakable("§IM a logo"));
        assertFalse(SentenceStream.speakable("Commit 3f9a0c1b2d4e5f60718293 is in.").contains("3f9a0c"));
        assertEquals("", SentenceStream.speakable("{\"a\":[1,2,3],\"b\":{}}"));
        assertTrue(SentenceStream.speakable("Hello there, how are you?").contains("Hello there"));
    }

    @Test
    public void longTextIsSaidInShortParts() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            b.append("word").append(i).append(i % 7 == 6 ? ", " : " ");
        }
        List<String> parts = NeuralVoice.parts(b.toString());
        assertTrue(parts.size() > 1);
        for (String p : parts) {
            assertTrue(p, p.length() <= 220);
            assertFalse(p.trim().isEmpty());
        }
        assertEquals(1, NeuralVoice.parts("Short and sweet.").size());
    }

    @Test
    public void samplesAreCleaned() {
        float[] s = new float[1000];
        for (int i = 0; i < s.length; i++) {
            s[i] = 1.5f;
        }
        s[500] = Float.NaN;
        float[] c = NeuralVoice.clean(s);
        assertEquals(0f, c[0], 0.001f);
        assertEquals(0f, c[500], 0.001f);
        assertEquals(0.98f, c[300], 0.001f);
        for (float v : c) {
            assertTrue(Math.abs(v) <= 0.98f);
        }
    }

    @Test
    public void afterSilenceTheFirstPieceIsShortSoTheVoiceKeepsUpWithTheText() {
        String s = "When you open the app for the first time, Ibiko downloads its files and gets the voice ready for you.";
        List<String> quick = NeuralVoice.partsFrom(s, true);
        assertTrue(quick.get(0), quick.get(0).length() <= NeuralVoice.LEAD_LETTERS);
        assertEquals(s, String.join(" ", quick));
        assertEquals("while speaking, a sentence stays whole", 1, NeuralVoice.partsFrom(s, false).size());
        assertEquals("a short sentence isn't cut", 1, NeuralVoice.partsFrom("Hello there, friend.", true).size());
    }

    @Test
    public void theNextSentenceMadeAheadIsFoundByTheSameVoiceAndSpeed() {
        assertEquals(NeuralVoice.madeKey("Next one.", 5, 1.0f), NeuralVoice.madeKey(" Next one. ", 5, 1.001f));
        assertTrue(!NeuralVoice.madeKey("Next one.", 5, 1.0f).equals(NeuralVoice.madeKey("Next one.", 6, 1.0f)));
        assertTrue(!NeuralVoice.madeKey("Next one.", 5, 1.0f).equals(NeuralVoice.madeKey("Next one.", 5, 1.5f)));
    }
}
