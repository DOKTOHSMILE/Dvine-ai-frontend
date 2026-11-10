package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class StepGroupsTest {
    @Test
    public void foldsLikeClaudeCode() {
        List<String> words = Arrays.asList(
                "Planning the work",
                "Listed the files in app, then read app/Main.java", "Read the git status",
                "Read app/Main.java", "Searched for “maxTokens”", "Listed the files in app/src", "Read app/Main.java",
                "Edited app/Main.java", "Created app/Fit.java (40 lines)", "Edited app/Main.java",
                "Checking that the answer is complete");
        List<String> titles = Arrays.asList(null, "$ ls app; cat app/Main.java", "$ git status",
                "app/Main.java", null, "app/src", "app/Main.java", "app/Main.java", "app/Fit.java", "app/Main.java", null);
        List<Long> secs = Arrays.asList(1L, 2L, 1L, 0L, 1L, 0L, 0L, 3L, 2L, 1L, -1L);
        List<StepGroups.Group> g = StepGroups.fold(words, titles, secs);
        assertEquals(5, g.size());
        assertEquals(StepGroups.Kind.OTHER, g.get(0).kind);
        assertEquals("Planning the work", g.get(0).label);
        assertEquals("Ran 2 commands", g.get(1).label);
        assertEquals(3, g.get(1).secs);
        assertEquals("Ran command", g.get(1).items.get(0).verb);
        assertEquals("Explored 2 files, 1 search", g.get(2).label);
        assertEquals("Read file", g.get(2).items.get(0).verb);
        assertEquals("app/Main.java", g.get(2).items.get(0).target);
        assertEquals("Searched files", g.get(2).items.get(1).verb);
        assertEquals("Edited 2 files", g.get(3).label);
        assertEquals("Created file", g.get(3).items.get(1).verb);
        assertEquals("app/Fit.java", g.get(3).items.get(1).target);
        assertEquals(-1, g.get(4).secs);
        assertEquals(10, g.get(4).items.get(0).index);
    }

    @Test
    public void webReadsAndOneOffsStayAlone() {
        // "Read bbc.com" (a web page, no detail) isn't a file read
        assertEquals(StepGroups.Kind.OTHER, StepGroups.kind("Read bbc.com", null));
        assertEquals(StepGroups.Kind.COMMAND, StepGroups.kind("Not run: rm -rf /", null));
        List<StepGroups.Group> g = StepGroups.fold(Arrays.asList("Searched for “x”"), Arrays.asList((String) null),
                Arrays.asList(-1L));
        assertEquals("Explored 1 search", g.get(0).label);
    }
}
