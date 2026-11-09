package ai.dvine.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** DVINE's own brain learns how words became actions, and acts again without a model only when that is safe. */
public class BrainBoxTest {
    private static File file() throws Exception {
        return new File(Files.createTempDirectory("brainbox").toFile(), "brainbox.tsv");
    }

    @Test
    public void aLearnedActionComesBackWithoutAModel() throws Exception {
        File f = file();
        BrainBox b = new BrainBox(f);
        b.learn("what's the time now", "DO TIME");
        b.learn("check the open issues on me/app", "DO TOOL github.issues me/app");
        b.learn("hello there", "SAY Hi!"); // talk is the model's to write: not learned
        b.learn("build me a rocket", "CANNOT no");
        assertEquals(2, b.size());
        assertEquals("DO TIME", b.recall("Time now please"));                      // close, and the order has no words of its own
        assertEquals("DO TOOL github.issues me/app", b.recall("Check the open issues on me/app")); // the same words
        assertNull("an order with the user's words is not reused for other words", b.recall("check the open issues on you/lib"));
        assertNull(b.recall("hello there"));
        assertNull(b.recall("write me a poem about rain"));
        assertEquals("kept on the device", 2, new BrainBox(f).size());
    }

    @Test
    public void theNewestOrderForTheSameWordsWinsAndTheBoxStaysSmall() throws Exception {
        BrainBox b = new BrainBox(file());
        b.learn("search the weather", "DO SEARCH weather");
        b.learn("Search the weather!", "DO SEARCH weather today");
        assertEquals(1, b.size());
        assertEquals("DO SEARCH weather today", b.recall("search weather"));
        for (int i = 0; i < BrainBox.MOST + 5; i++) {
            b.learn("job number " + (1000 + i), "DO TIME");
        }
        assertEquals(BrainBox.MOST, b.size());
        b.clear();
        assertEquals(0, b.size());
    }
}
