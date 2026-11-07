package ai.dvine.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionFreshnessTest {
    @Test
    public void aTokenIsRenewedOnTheMonotonicClockNotTheWallClock() {
        long now = 1_000_000L;
        long until = SessionClient.freshUntil(now, 12 * 60 * 60_000L);
        assertEquals(now + 12 * 60 * 60_000L - 5 * 60_000L, until);
        assertTrue(until > now);
        // already inside the five-minute margin: do not keep using it
        assertEquals(now, SessionClient.freshUntil(now, 4 * 60_000L));
    }
}
