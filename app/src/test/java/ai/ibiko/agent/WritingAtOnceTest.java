package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The same hands for any feature: every part done by its own worker, whole and in order. */
public class WritingAtOnceTest {
    @Test
    public void sectionsAreWrittenAtOnceAndComeBackWholeInOrder() {
        StringBuilder longOne = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longOne.append("word ");
        }
        final String big = longOne.toString();
        List<String> got = Squad.all(Arrays.asList("Opening", "Middle", "Close"), (n, part) -> n == 2 ? big : part + " written", 3, null);
        assertEquals(Arrays.asList("Opening written", big, "Close written"), got);
        assertTrue("never clipped", got.get(1).length() > 2900);
    }

    @Test
    public void aWriterKeepsToItsOwnSection() {
        String p = WritingPlan.sectionAlone("Write a story about a lighthouse", "Title: Light\n1. Opening: the storm\n2. Close: dawn", 2, 2,
                "Close: dawn");
        assertTrue(p.contains("section 2 of 2") && p.contains("cover only yours") && p.contains("Close: dawn"));
    }
}
