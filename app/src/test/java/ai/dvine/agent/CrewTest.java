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
}
