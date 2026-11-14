package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A long work log is carried on from its task line and its latest steps, not copied whole. */
public class WorkLogTest {
    @Test
    public void blankDoesNotNeedACopyAndAShortLogStaysWhole() {
        assertTrue(WorkLog.blank(null));
        assertTrue(WorkLog.blank(" \n\t"));
        assertFalse(WorkLog.blank(" a "));
        assertEquals("Task: hi\n", WorkLog.kept("Task: hi\n", 4000));
        assertEquals("", WorkLog.kept("   \n", 4000));
        assertEquals("", WorkLog.kept(null, 4000));
    }

    @Test
    public void aHugeLogKeepsTheTaskAndTheTailAndDropsTheMiddle() {
        String head = "Task: fix the door\n";
        String big = head + "x".repeat(100_000) + "ONLYONCE" + "y".repeat(100_000) + "TAILMARK-END";
        String out = WorkLog.kept(big, 2000);
        assertTrue(out.startsWith("Task: fix the door"));
        assertTrue(out.contains("TAILMARK-END"));
        assertFalse(out.contains("ONLYONCE"));
        assertTrue(out.length() <= 2000 + 40);
        assertTrue(out.contains("earlier steps left out"));
    }
}
