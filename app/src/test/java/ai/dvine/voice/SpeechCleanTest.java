package ai.dvine.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The voice reads the words only: no **, <, ", # or other symbols read out. */
public class SpeechCleanTest {
    @Test
    public void formatCharactersAreNotRead() {
        assertEquals("The fix is in Main.java, line 12.", SentenceStream.clean("**The fix** is in `Main.java`, line 12."));
        assertEquals("Done. It works.", SentenceStream.clean("<final>Done.</final> It works."));
        assertEquals("She said hello to me.", SentenceStream.clean("She said \"hello\" to me."));
        assertEquals("Click Save to Settings.", SentenceStream.clean("Click [Save] -> Settings."));
        assertEquals("Step one", SentenceStream.clean("## Step one"));
        assertEquals("a b", SentenceStream.clean("a | b"));
        assertEquals("It's Obi's turn.", SentenceStream.clean("It's Obi's turn.")); // apostrophes in words stay
        assertEquals("", SentenceStream.clean("***"));
    }
}
