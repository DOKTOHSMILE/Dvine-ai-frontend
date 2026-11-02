package ai.dvine.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.dvine.llm.TaskSize;

/** Sentences cut from a reply as it streams, so the voice starts after the first one. */
public class SentenceStreamTest {
    private static List<String> feed(SentenceStream s, String text, int size) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < text.length(); i += size) {
            out.addAll(s.add(text.substring(i, Math.min(text.length(), i + size))));
        }
        return out;
    }

    @Test
    public void firstSentenceComesOutBeforeTheReplyEnds() {
        SentenceStream s = new SentenceStream();
        List<String> early = feed(s, "Sure. Lagos is the largest city in Nigeria by population, and Abuja is its capital. It grew", 3);
        assertEquals(Arrays.asList("Sure.", "Lagos is the largest city in Nigeria by population, and Abuja is its capital."), early);
        s.add(" fast.");
        assertEquals(Arrays.asList("It grew fast."), s.finish());
    }

    @Test
    public void doesNotSplitAbbreviationsOrNumbers() {
        SentenceStream s = new SentenceStream();
        List<String> out = feed(s, "Hello there, friend of mine. Take e.g. Dr. Ade, who paid 3.5 million in 2020. Next one is here. ", 4);
        out.addAll(s.finish());
        assertEquals(Arrays.asList("Hello there, friend of mine.", "Take e.g. Dr. Ade, who paid 3.5 million in 2020.",
                "Next one is here."), out);
    }

    @Test
    public void codeIsLeftToTheScreenAndMarkdownIsCleaned() {
        SentenceStream s = new SentenceStream();
        List<String> out = feed(s, "Here is the **function** you asked for:\n```python\ndef add(a, b):\n    return a + b\n```\n"
                + "Call it with `add(1, 2)`, see [the docs](https://docs.python.org). ", 5);
        out.addAll(s.finish());
        String all = String.join(" | ", out);
        assertTrue(all, all.contains("Here is the function you asked for:"));
        assertTrue(all, all.contains("The code is on the screen."));
        assertTrue(all, all.contains("Call it with add(1, 2), see the docs."));
        assertFalse(all, all.contains("return a + b") || all.contains("```") || all.contains("**") || all.contains("http"));
    }

    @Test
    public void shortLinesWaitForTheNextAfterTheFirst() {
        SentenceStream s = new SentenceStream();
        List<String> out = feed(s, "Yes. No. Ok. Then a longer sentence follows here. ", 2);
        out.addAll(s.finish());
        assertEquals(Arrays.asList("Yes.", "No. Ok. Then a longer sentence follows here."), out);
    }

    @Test
    public void ordersStillGoThroughRouting() {
        assertTrue(TaskSize.mayNeedAction("Search for the latest news and write a summary"));
        assertTrue(TaskSize.mayNeedAction("write a file called notes"));
        assertFalse(TaskSize.mayNeedAction("Write me a short essay about the history of Benin bronzes"));
    }
}
