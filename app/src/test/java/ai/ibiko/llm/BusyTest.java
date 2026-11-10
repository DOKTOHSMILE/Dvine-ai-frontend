package ai.ibiko.llm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BusyTest {
    @Test
    public void aBusyModelIsWaitedForButARefusedKeyIsNot() {
        assertTrue("the gateway's 403", RemoteBrain.busy(403, "That model is busy right now, please try again"));
        assertTrue(RemoteBrain.busy(429, "Rate limit reached for requests"));
        assertTrue(RemoteBrain.busy(503, ""));
        assertTrue(RemoteBrain.busy(529, "Overloaded"));
        assertFalse("a wrong key", RemoteBrain.busy(401, "Incorrect API key provided"));
        assertFalse(RemoteBrain.busy(403, "Permission denied"));
        assertFalse("no credit", RemoteBrain.busy(429, "You exceeded your current quota, please check your plan and billing"));
        assertFalse(RemoteBrain.busy(400, "Bad request"));
    }
}
