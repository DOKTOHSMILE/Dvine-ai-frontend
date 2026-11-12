package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

import org.junit.Test;

/** The phone's heat and battery decide whether its model starts a turn, and on how many threads. */
public class DeviceGuardTest {
    private static DeviceGuard.Reading at(long ms, int status, int tenthsC, int pct, boolean charging) {
        return new DeviceGuard.Reading(ms, status, tenthsC, pct, charging);
    }

    @Test
    public void aCoolPhoneRunsOnTwoThreadsAndMissingReadingsInventNothing() {
        DeviceGuard g = new DeviceGuard();
        DeviceGuard.Decision d = g.evaluate(at(0, DeviceGuard.THERMAL_NONE, 330, 80, false));
        assertEquals(DeviceGuard.State.NORMAL, d.state);
        assertEquals(2, d.maxThreads);
        assertFalse(d.paused());
        assertNull(d.why());
        assertEquals(DeviceGuard.State.NORMAL, new DeviceGuard().evaluate(at(0, -1, -1, -1, false)).state);
    }

    @Test
    public void hotterAppliesAtOnceAndCoolerOnlyAfterItHolds() {
        DeviceGuard g = new DeviceGuard();
        DeviceGuard.Decision warm = g.evaluate(at(0, DeviceGuard.THERMAL_MODERATE, -1, 80, false));
        assertEquals(DeviceGuard.State.WARM, warm.state);
        assertEquals(1, warm.maxThreads);
        assertFalse(warm.paused());
        assertEquals("a cool reading doesn't undo it at once", DeviceGuard.State.WARM,
                g.evaluate(at(1_000, DeviceGuard.THERMAL_NONE, -1, 80, false)).state);
        assertEquals(DeviceGuard.State.NORMAL, g.evaluate(at(1_000 + DeviceGuard.STEP_DOWN_MS, DeviceGuard.THERMAL_NONE, -1, 80, false)).state);
    }

    @Test
    public void tooHotPausesThenCoolsDownBeforeRunningAgain() {
        DeviceGuard g = new DeviceGuard();
        DeviceGuard.Decision hot = g.evaluate(at(0, DeviceGuard.THERMAL_CRITICAL, -1, 80, false));
        assertEquals(DeviceGuard.Pause.TOO_HOT, hot.pause);
        assertEquals(0, hot.maxThreads);
        assertTrue(hot.why().contains("too hot"));
        DeviceGuard.Decision cooling = g.evaluate(at(10_000, DeviceGuard.THERMAL_NONE, -1, 80, false));
        assertEquals(DeviceGuard.Pause.COOLING_DOWN, cooling.pause);
        assertTrue(g.evaluate(at(10_000 + DeviceGuard.COOL_MS - 1, DeviceGuard.THERMAL_NONE, -1, 80, false)).paused());
        DeviceGuard.Decision cool = g.evaluate(at(10_000 + DeviceGuard.COOL_MS, DeviceGuard.THERMAL_NONE, -1, 80, false));
        assertFalse(cool.paused());
        assertEquals(2, cool.maxThreads);
    }

    @Test
    public void aWarmReadingWhileCoolingStartsTheWaitAgain() {
        DeviceGuard g = new DeviceGuard();
        g.evaluate(at(0, DeviceGuard.THERMAL_CRITICAL, -1, 80, false));
        g.evaluate(at(1_000, DeviceGuard.THERMAL_NONE, -1, 80, false));
        g.evaluate(at(100_000, DeviceGuard.THERMAL_MODERATE, -1, 80, false));
        assertTrue(g.evaluate(at(1_000 + DeviceGuard.COOL_MS, DeviceGuard.THERMAL_NONE, -1, 80, false)).paused());
    }

    @Test
    public void theBatteryTemperatureCountsAndChargingBringsEachStepEarlier() {
        assertEquals(DeviceGuard.State.HOT, DeviceGuard.measure(at(0, -1, 460, 80, false)));
        assertEquals(DeviceGuard.State.CRITICAL, DeviceGuard.measure(at(0, -1, 460, 80, true)));
        assertEquals("the worse of the two signals", DeviceGuard.State.HOT,
                DeviceGuard.measure(at(0, DeviceGuard.THERMAL_SEVERE, 300, 80, false)));
    }

    @Test
    public void anAlmostEmptyBatteryPausesUnlessCharging() {
        assertEquals(DeviceGuard.Pause.BATTERY_LOW, new DeviceGuard().evaluate(at(0, DeviceGuard.THERMAL_NONE, 300, 5, false)).pause);
        assertFalse(new DeviceGuard().evaluate(at(0, DeviceGuard.THERMAL_NONE, 300, 5, true)).paused());
        assertFalse(new DeviceGuard().evaluate(at(0, DeviceGuard.THERMAL_NONE, 300, 6, false)).paused());
    }

    /** A model file of the right size, without writing a gigabyte (a sparse file). */
    private static File installed(ModelSpec spec) throws Exception {
        File dir = Files.createTempDirectory("models").toFile();
        try (RandomAccessFile f = new RandomAccessFile(new File(dir, spec.file), "rw")) {
            f.setLength(spec.bytes);
        }
        return dir;
    }

    @Test
    public void aHotPhoneSaysWhyItsModelDidntAnswer() throws Exception {
        LocalModel m = new LocalModel(installed(ModelSpec.QWEN3_1_7B), ModelSpec.QWEN3_1_7B, true);
        DeviceGuard g = new DeviceGuard();
        m.guard(() -> g.evaluate(at(System.currentTimeMillis(), DeviceGuard.THERMAL_CRITICAL, -1, 80, false)));
        assertNull(m.reply("sys", "hi", 48, null));
        assertTrue(m.problem(), m.problem().contains("too hot"));
    }

    @Test
    public void offlineWithAnApiTheFallbackIsOptInAndNeverSilent() throws Exception {
        ModelSpec spec = ModelSpec.QWEN3_1_7B;
        LocalModel m = new LocalModel(installed(spec), spec, true);
        m.useRemote(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "",
                (url, headers, body, timeoutMs) -> {
                    throw new java.io.IOException("offline");
                }), 400_000, () -> false);
        // off (the default): nothing answers, and the user hears it's the internet
        assertFalse(m.fallsBackOffline());
        assertNull(m.reply("sys", "hi", 48, null));
        assertTrue(m.problem(), m.problem().contains("no internet"));
        // on, with the model on the phone: the phone's model is asked (here its engine isn't in a unit test's build,
        // so it can't answer either, and the reply that never came isn't labelled as the phone's)
        m.offlineFallback(true);
        assertTrue(m.fallsBackOffline());
        assertNull(m.reply("sys", "hi", 48, null));
        assertFalse(m.problem(), m.problem().contains("no internet"));
        assertNull(m.takeSwitchNote());
        // on, but no model on the phone: as when off
        LocalModel none = new LocalModel(Files.createTempDirectory("models").toFile(), spec, true);
        none.offlineFallback(true);
        assertFalse(none.fallsBackOffline());
    }
}
