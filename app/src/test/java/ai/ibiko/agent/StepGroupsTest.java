package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void readsOfOneFileAreOneBadgeFromTheTop() {
        List<String> words = Arrays.asList(
                "Read app/src/MainActivity.java (lines 2001–2400)",
                "Read app/src/MainActivity.java (lines 1–400)",
                "Read app/src/MainActivity.java (lines 401–800)",
                "Read docs/BUILD_STATUS.md (lines 1–40)");
        List<String> titles = Arrays.asList(null, null, null, null);
        List<Long> secs = Arrays.asList(1L, 1L, 1L, 1L);
        List<StepGroups.Group> g = StepGroups.fold(words, titles, secs);
        assertEquals(1, g.size());
        assertEquals("Explored 2 files", g.get(0).label);
        assertEquals(2, g.get(0).items.size());
        assertEquals("app/src/MainActivity.java (lines 1–800, 2001–2400)", g.get(0).items.get(0).target);
        assertEquals("docs/BUILD_STATUS.md (lines 1–40)", g.get(0).items.get(1).target);
        assertEquals(3, g.get(0).lastIndex);
        assertEquals(StepGroups.Kind.EXPLORE, StepGroups.kind("Read app/src/Foo.java — still to read", null));
        assertTrue("a short run still opens while it works", StepGroups.startOpen(3, true));
        assertTrue("the whole tree stays one banner", !StepGroups.startOpen(900, true));
        assertEquals(24, StepGroups.window(900, 0));
        assertEquals(48, StepGroups.window(900, 24));
        assertEquals(30, StepGroups.window(30, 24));
    }

    /** From the pilot's screenshot of the Process sheet: repeats and "X" then "X: result" were two lines each. */
    @Test
    public void repeatsAndFollowUpsAreOneLineAndPagesOneBanner() {
        List<String> words = Arrays.asList("Thinking", "Replying", "Planning", "Replying",
                "Listed the chosen repositories", "Listed the chosen repositories: The repositories the pilot chose (2 lines)",
                "Fetched https://api.github.com/repos/doktohsmile/ibiko-ai",
                "Fetched https://api.github.com/repos/doktohsmile/ibiko-ai: The page couldn't be fetched: the site said 403.",
                "Fetched https://github.com/doktohsmile/ibiko-ai",
                "Fetched https://github.com/doktohsmile/ibiko-ai: GitHub - DOKTOHSMILE/Ibiko-ai (247 lines)",
                "Custom gateway didn't answer");
        String[] none = new String[words.size()];
        Long[] secs = new Long[words.size()];
        java.util.Arrays.fill(secs, 1L);
        List<StepGroups.Group> g = StepGroups.fold(words, Arrays.asList(none), Arrays.asList(secs));
        java.util.List<String> labels = new java.util.ArrayList<String>();
        for (StepGroups.Group x : g) {
            labels.add(x.label);
        }
        assertEquals(Arrays.asList("Thinking", "Replying", "Planning",
                "Listed the chosen repositories: The repositories the pilot chose (2 lines)", "Fetched 2 pages",
                "Custom gateway didn't answer"), labels);
        StepGroups.Group pages = g.get(4);
        assertEquals(StepGroups.Kind.WEB, pages.kind);
        assertEquals("each page once, with what happened to it", 2, pages.items.size());
        assertTrue(pages.items.get(0).target.endsWith("the site said 403."));
    }
}
