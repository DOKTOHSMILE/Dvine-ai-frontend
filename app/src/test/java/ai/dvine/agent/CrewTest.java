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
    public void onlineHelpersWorkSideBySideAndTheAnswerKeepsTheOrder() {
        final java.util.concurrent.atomic.AtomicInteger busy = new java.util.concurrent.atomic.AtomicInteger(), most = new java.util.concurrent.atomic.AtomicInteger();
        List<String> brainSaid = new ArrayList<String>(Arrays.asList("PART: one\nPART: two\nPART: three", null));
        List<Integer> doneOrder = java.util.Collections.synchronizedList(new ArrayList<Integer>());
        String r = Crew.run((sys, p) -> brainSaid.isEmpty() ? null : brainSaid.remove(0), (part, brief) -> {
            int now = busy.incrementAndGet();
            most.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep("one".equals(part) ? 300 : 100);
            } catch (InterruptedException ignored) {
            }
            busy.decrementAndGet();
            return "found " + part;
        }, "Deep audit", (n, of, part, found) -> doneOrder.add(n), () -> false, 3);
        assertTrue("side by side: " + most.get(), most.get() >= 2);
        assertEquals(3, doneOrder.size());
        assertTrue(r, r.indexOf("Part 1: one") < r.indexOf("Part 2: two") && r.indexOf("Part 2: two") < r.indexOf("Part 3: three")); // writer failed: parts, in order
    }

    @Test
    public void eightWorkersGetEightPartsNotTheOldCapOfFive() {
        assertEquals(8, Crew.plan(8));
        assertEquals(5, Crew.plan(4));
        assertEquals(5, Crew.plan(1));
        assertTrue(Crew.planPrompt(8).contains("2 to 8"));
        StringBuilder plan = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            plan.append("PART: part ").append(i).append('\n');
        }
        List<String> brainSaid = new ArrayList<String>(Arrays.asList(plan.toString(), null));
        List<Integer> done = java.util.Collections.synchronizedList(new ArrayList<Integer>());
        String r = Crew.run((sys, p) -> {
            if (brainSaid.size() == 2) {
                assertTrue(sys, sys.contains("2 to 8"));
            }
            return brainSaid.isEmpty() ? null : brainSaid.remove(0);
        }, (part, brief) -> {
            try {
                Thread.sleep(part.endsWith("1") ? 60 : 15);
            } catch (InterruptedException ignored) {
            }
            return "found " + part;
        }, "Deep audit", (n, of, part, found) -> done.add(n), () -> false, 8);
        assertEquals(8, done.size());
        List<Integer> sorted = new ArrayList<Integer>(done);
        java.util.Collections.sort(sorted);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8), sorted);
        for (int i = 1; i < 8; i++) {
            assertTrue(r, r.indexOf("Part " + i + ": part " + i) < r.indexOf("Part " + (i + 1) + ": part " + (i + 1)));
        }
        // one worker still reports in order, not as a race
        List<String> oneBrain = new ArrayList<String>(Arrays.asList("PART: one\nPART: two\nPART: three", "written"));
        List<Integer> order = new ArrayList<Integer>();
        String one = Crew.run((sys, p) -> oneBrain.remove(0), (part, brief) -> "found " + part,
                "Deep audit", (n, of, part, found) -> order.add(n), () -> false, 1);
        assertEquals("written", one);
        assertEquals(Arrays.asList(1, 2, 3), order);
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
