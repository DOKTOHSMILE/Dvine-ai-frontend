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
        StringBuilder plan = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
        plan.append("PART: part ").append(i).append(" of the audit\n");
        }
        // the parser keeps as many parts as the job has workers for
        assertEquals(8, Crew.parts(plan.toString(), Crew.plan(8)).size());
        // ...and stays at the usual MOST for the plain one-argument call
        assertEquals(Crew.MOST, Crew.parts(plan.toString()).size());
        List<String> worked = java.util.Collections.synchronizedList(new ArrayList<String>());
        List<String> done = java.util.Collections.synchronizedList(new ArrayList<String>());
        List<String> brainSaid = new ArrayList<String>(Arrays.asList(plan.toString(), "Final: all eight."));
        String r = Crew.run((sys, p) -> brainSaid.remove(0), (part, brief) -> {
            worked.add(part);
            return "done " + part;
        }, "Full review of the whole codebase", (n, of, part, found) -> done.add(n + "/" + of), () -> false, 8);
        assertEquals("Final: all eight.", r);
        assertEquals(8, worked.size());
        assertEquals(8, done.size());
        assertEquals("1/8", done.get(0));
        assertEquals("8/8", done.get(7));
        // the planner is asked for up to 8 parts, so an 8-worker job is not quietly 5
        assertTrue(Crew.planPrompt(8).contains("2 to 8"));
        assertTrue(Crew.planPrompt(1).contains("2 to " + Crew.MOST));
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
