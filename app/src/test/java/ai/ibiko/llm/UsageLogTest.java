package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Today's usage: totals, a new day, and keeping them across restarts. */
public class UsageLogTest {
    @Test
    public void countsTheDayAndStartsAgainTomorrow() {
        long now = 1_800_000_000_000L;
        UsageLog log = new UsageLog();
        log.record(new UsageLog.Call("x", 2048, 100, 200, 20, 50, false, 2000, now));
        log.record(new UsageLog.Call("x", 2048, 100, 300, 10, 30, false, 2000, now + 1000));
        log.failure(now + 2000);
        assertEquals(2, log.callsToday(now));
        assertEquals(730, log.inToday(now));
        assertEquals(80, log.outToday(now));
        assertEquals(1, log.failedToday(now));
        assertEquals(20f, log.speedToday(now), 0.01f);
        assertEquals(440, log.last().used());
        UsageLog again = new UsageLog();
        again.restore(log.save(), now + 5000);
        assertEquals(2, again.callsToday(now + 5000));
        assertEquals(0, again.callsToday(now + 86_400_000L * 2)); // two days later: a fresh day
        assertEquals(3, UsageLog.estimate("hello world"));
    }
}
