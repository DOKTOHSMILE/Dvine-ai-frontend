package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.llm.UsageLog;

/** The workers board and today's use: what the Workload sheet and the Usage screen read. */
public class WorkersAndUsageTest {
    @Test
    public void aWorkerSaysWhoWhatOnWhichBrainAndHowItEnded() {
        AgentBoard.clear();
        String w = AgentBoard.start("Obi · reply", "Summarise <attachment name=\"Pasted text\">a long paste</attachment> please", "Claude Opus 5.5");
        AgentBoard.step(w, "Reading the page");
        AgentBoard.Entry e = AgentBoard.now().get(0);
        assertEquals("Claude Opus 5.5", e.brain);
        assertEquals("Reading the page", e.step);
        assertEquals("pasted attachments stay out of the title", "Summarise please", e.task);
        AgentBoard.end(w, AgentBoard.Outcome.FAILED);
        assertTrue(AgentBoard.now().isEmpty());
        assertEquals(AgentBoard.Outcome.FAILED, AgentBoard.recent().get(0).outcome);
        assertTrue(AgentBoard.recent().get(0).ended > 0);
        for (int i = 0; i < 10; i++) {
            AgentBoard.end(AgentBoard.start("x", "y"));
        }
        assertEquals(AgentBoard.RECENT, AgentBoard.recent().size());
    }

    @Test
    public void todaysUseAndTheLastCallSurviveARestart() {
        long now = System.currentTimeMillis();
        UsageLog log = new UsageLog();
        log.restore(null, now);
        assertEquals(null, log.last());
        ai.ibiko.llm.LocalModelUsage.record(log, now); // a call to an API brain
        log.recordOwn(UsageLog.Own.MEMORY, now);
        log.recordOwn(UsageLog.Own.RESEARCH, now);
        UsageLog back = new UsageLog();
        back.restore(log.save(), now);
        assertNotNull("the context window is still shown after a restart", back.last());
        assertEquals(1200, back.last().used());
        assertEquals("API: gateway (claude)", back.last().brain);
        assertEquals(1, back.callsToday(now));
        assertEquals(2, back.ownToday(now));
        assertEquals(1, back.ownMemoryToday(now));
        assertEquals(1, back.ownResearchToday(now));
        UsageLog old = new UsageLog();
        old.restore((UsageLog.dayOfForTest(now) - 1) + " 5 1 1 0 10", now);
        assertEquals("yesterday's numbers don't count today", 0, old.callsToday(now));
        UsageLog older = new UsageLog();
        older.restore(UsageLog.dayOfForTest(now) + " 3 30 20 1 900", now);
        assertEquals("an older saved line still reads", 3, older.callsToday(now));
    }

    @Test
    public void projectTitlesAreReadable() {
        assertEquals("Now create a plan.md I recommend", ai.ibiko.scribe.Scribe.readable(
                "Now create a plan.md <attachment name=\"Pasted text\">\nlots of pasted text\n</attachment> I recommend"));
        assertEquals("Untitled task", ai.ibiko.scribe.Scribe.readable("<attachment name=\"x\">only paste"));
    }
}
