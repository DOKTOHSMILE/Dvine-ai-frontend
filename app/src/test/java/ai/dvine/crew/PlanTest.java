package ai.dvine.crew;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The plan an agent reads is one line of order, not the task itself: the model was
 * handed the whole job and answered with the finished work. A plan is steps in
 * named sections, never a completed file. A part runs on a worker while another
 * still writes the report, and every part comes back.
 */
public class PlanTest {

    /** The order the job was given in, without the file the plan must not contain. */
    @Test
    public void orderOfTheWorkIsNotTheWorkItself() {
        String instruction = "Read the project's README file, then fix the bug named there";
        String plan = Crew.planPrompt(instruction);

        assertTrue(plan.contains("Read the project's README file"));
        assertTrue(plan.contains("### Step 1"));
        assertFalse("the plan is the order, never a finished file",
                plan.contains("FixResult.java"));
        assertFalse(plan.contains("apply_patch"));
        assertFalse(plan.contains("read_file"));
    }

    /** The plan says how many parts there may be, and that count is honoured. */
    @Test
    public void thePlanNamesThePartsItAsksFor() {
        String plan = Crew.planPrompt("build a thing");

        assertTrue(plan.startsWith("There are 5 parts"));
        assertTrue(plan.contains("no more than 5 parts"));
        assertTrue(plan.contains("### Step "));

        String bigger = Crew.planPrompt("build eight things at once");
        assertTrue(bigger.startsWith("There are 8 parts"));
        assertTrue(bigger.contains("no more than 8 parts"));
    }

    /** Eight workers are not silently five: the plan may ask for eight. */
    @Test
    public void eightWorkersAreEightParts() {
        assertEquals(8, Crew.plan(8));
        assertEquals(5, Crew.plan(4));
        assertEquals(5, Crew.plan(1));
    }

    /** A plan with no steps at all is refused, so nothing runs against a blank order. */
    @Test
    public void aPlanWithNoStepsIsRefused() {
        List<PlanStep> none = Crew.partsOf("");
        assertTrue(none.isEmpty());
        none = Crew.partsOf("### \n");
        assertTrue(none.isEmpty());
    }

    /** Parts come back in the order they were planned, never reordered by the pool. */
    @Test
    public void everyPartComesBackInOrder() throws Exception {
        List<PlanStep> parts = Crew.partsOf(Crew.planPrompt("Read, then fix, then check"));
        assertEquals(3, parts.size());
        assertEquals("Step 1", parts.get(0).name);
        assertEquals("Step 3", parts.get(2).name);

        // Four parts at once on three workers: every one still lands, in order.
        List<String> names = new ArrayList<>();
        List<Future<String>> out = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 4; i++) {
            final int n = i;
            out.add(pool.submit(() -> "part " + n));
        }
        for (int i = 0; i < 4; i++) {
            names.add(out.get(i).get());
        }
        pool.shutdown();
        assertEquals(4, names.size());
        assertEquals("part 0", names.get(0));
        assertEquals("part 3", names.get(3));
    }
}