package ai.ibiko.engine;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.bridge.Probe;

public class EngineContextTest {
    @Test
    public void aTightPhoneStepsDownAndStops() {
        Probe p = new Probe(32768, 24, 2048, 16, 2, 2048, 256, 8);
        long weights = 2L * 1024 * 1024 * 1024;
        EngineConfig c = EngineContext.decide(p, null, weights, 180L * 1024 * 1024, 8, false);
        assertTrue(c.nCtx == 0 || (c.nCtx >= EngineContext.MIN_CTX && c.nCtx <= EngineContext.MAX_CTX));
        assertTrue(c.nThreads <= 8);
    }

    @Test
    public void aSmallModelKeepsAUsableContext() {
        Probe p = new Probe(4096, 16, 1024, 8, 2, 1024, 256, 4);
        EngineConfig c = EngineContext.decide(p, null, 400L * 1024 * 1024, 3L * 1024 * 1024 * 1024, 2, false);
        assertTrue(c.nCtx >= EngineContext.MIN_CTX);
        assertTrue(c.nCtx <= 4096);
    }
}
