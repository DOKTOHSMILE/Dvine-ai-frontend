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

    /**
     * From the pilot's screenshot: Qwen3 1.7B Fast (Q4_0, 1.06 GB) on a device with about 2.2 GB free was cut to 1,024
     * tokens, too little to work in. Its weights are mapped from the file and its memory is 8-bit: it gets 4,096.
     */
    @Test
    public void theFastModelGetsRoomToWork() {
        Probe qwen = new Probe(40960, 28, 2048, 16, 8, 1024, 1024, 2);
        EngineConfig c = EngineContext.decide(qwen, null, 1056782912L, 2250L * 1024 * 1024, 2, false);
        assertTrue("got " + c.nCtx, c.nCtx >= 4096);
        // and a device with too little still steps down rather than failing to load
        EngineConfig tight = EngineContext.decide(qwen, null, 1056782912L, 1800L * 1024 * 1024, 2, false);
        assertTrue("got " + tight.nCtx, tight.nCtx >= EngineContext.MIN_CTX && tight.nCtx < 4096);
    }
}
