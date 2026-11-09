package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** One agent: a numbered plan, then each step in order. */
public class CrewTest {
    @Test
    public void aHeavyJobIsDoneOneStepAtATime() {
        List<String> said = new ArrayList<String>(Arrays.asList(
                "1. Read the keys\n2. Check the rules\nPLAN\n1. Read the keys\n2. Check the rules",
                "keys are in prefs",
                "DONE"));
        List<String> steps = new ArrayList<String>();
        String r = Crew.run((sys, p) -> said.remove(0), "Deep audit the repo", new Crew.Progress() {
            @Override
            public void step(int n, int of, String step, String found) {
                steps.add(n + "/" + of + " " + step);
            }

            @Override
            public void finished(String result) {
            }

            @Override
            public void plan(String plan) {
            }
        }, () -> false);
        assertTrue(r.contains("Read the keys"));
        assertTrue(r.contains("Check the rules"));
        assertEquals(Arrays.asList("1/2 Read the keys", "2/2 Check the rules"), steps);
    }

    @Test
    public void aStoppedJobReturnsNothing() {
        assertNull(Crew.run((s, p) -> "1. Go\nPLAN\n1. Go", "Deep audit", null, () -> true));
    }

    @Test
    public void onlyHeavyJobsArePlanned() {
        assertTrue(Crew.heavy("Deep audit my Harvana app codebase in my GitHub repo"));
        assertTrue(Crew.heavy("Find all the bugs in the whole repository"));
        assertFalse(Crew.heavy("Fix the typo in README"));
    }

    /** Every part the planner asked for comes back, and the count is honoured above MOST. */
    @Test
    public void severalWorkersEachReturnTheirPart() {
        final List<String> said = new ArrayList<String>(Arrays.asList(
                "1. One\n2. Two\n3. Three\nPLAN\n1. One\n2. Two\n3. Three",
                "done 0", "done 1", "done 2"));
        final List<String> steps = new ArrayList<String>();
        String r = Crew.run((sys, p) -> {
            synchronized (said) {
                return said.remove(0);
            }
        }, "Deep audit the repo", new Crew.Progress() {
            @Override
            public void step(int n, int of, String step, String found) {
                synchronized (steps) {
                    steps.add(step);
                }
            }

            @Override
            public void finished(String result) {
            }

            @Override
            public void plan(String plan) {
            }
        }, () -> false, 3);
        assertEquals(Arrays.asList("One", "Two", "Three"), new ArrayList<String>(steps));
        assertTrue(r.contains("done 0"));
        assertTrue(r.contains("done 1"));
        assertTrue(r.contains("done 2"));
        assertEquals(8, Crew.plan(8));
        assertTrue(Crew.plan(2) >= 3);
    }

    /** One worker still runs the parts one after another. */
    @Test
    public void oneWorkerRunsInOrder() {
        final List<String> steps = new ArrayList<String>();
        String r = Crew.run((sys, p) -> sys.contains("planner")
                ? "1. First\n2. Second\nPLAN\n1. First\n2. Second"
                : "ok:" + p, "Deep audit", new Crew.Progress() {
            @Override
            public void step(int n, int of, String step, String found) {
                steps.add(n + "/" + of);
            }

            @Override
            public void finished(String result) {
            }

            @Override
            public void plan(String plan) {
            }
        }, () -> false, 1);
        assertEquals(Arrays.asList("1/2", "2/2"), steps);
        assertTrue(r.contains("First"));
        assertTrue(r.contains("Second"));
    }
}
