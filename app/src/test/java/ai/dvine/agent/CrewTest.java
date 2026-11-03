package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Mini agents: a plan in parts, a fresh helper each, findings as they come, one written answer. */
public class CrewTest {
    @Test
    public void aHeavyJobIsSplitWorkedPartByPartAndWrittenUp() {
        List<String> brainSaid = new ArrayList<String>(Arrays.asList(
                "PART: security: keys, storage, network in app/src/main\nPART: data: Firestore rules and models\nPART: build: gradle and CI",
                "Final report: 1 security issue, 1 data issue, build fine."));
        List<String> worked = new ArrayList<String>(), done = new ArrayList<String>();
        String r = Crew.run((sys, p) -> brainSaid.remove(0), (part, brief) -> {
            worked.add(part + " | " + brief.substring(0, 25));
            return "found in " + part.substring(0, part.indexOf(':'));
        }, "Deep audit my Harvana repo", (n, of, part, found) -> done.add(n + "/" + of + " " + found), () -> false);
        assertEquals("Final report: 1 security issue, 1 data issue, build fine.", r);
        assertEquals(3, worked.size());
        assertTrue(worked.get(1).startsWith("data: Firestore rules and models | You are helper 2 of 3"));
        assertEquals(Arrays.asList("1/3 found in security", "2/3 found in data", "3/3 found in build"), done); // as they come
        // a job that doesn't split: one agent does it
        assertNull(Crew.run((s, p) -> "PART: everything", (p, b) -> "x", "Deep audit", (n, o, p, f) -> { }, () -> false));
    }

    @Test
    public void partsWrittenOverSeveralLinesStayWhole() {
        assertEquals(Arrays.asList("UI/UX What to look at: Layouts. What to report: Poor UX.", "Security - keys and storage."),
                Crew.parts("Here is the plan.\nPART: UI/UX  \nWhat to look at: Layouts.\nWhat to report: Poor UX.\n\nPART: Security - keys and storage."));
    }

    @Test
    public void onlyHeavyJobsGetHelpers() {
        assertTrue(Crew.heavy("Deep audit my Harvana app codebase in my GitHub repo"));
        assertTrue(Crew.heavy("Find all the bugs in the whole repository"));
        assertTrue(Crew.heavy("Research the best Android databases"));
        assertFalse(Crew.heavy("Fix the typo in README"));
        assertFalse(Crew.heavy("Explain this function"));
    }
}
