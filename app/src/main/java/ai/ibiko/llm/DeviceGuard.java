package ai.ibiko.llm;

/**
 * Whether the phone's own model may run a turn now, and on how many threads, from the phone's heat and battery.
 * Plain Java, no Android types, so every transition is tested without a hot phone. After the Harvana miner's
 * SafetyController, which keeps a phone that hashes all day out of thermal shutdown.
 *
 * Heat comes from two signals, and the worse one counts:
 *  - Android's thermal status (PowerManager, Android 10+): NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;
 *  - the battery's temperature, a second signal (Android does not give the CPU's).
 * Either may be missing (-1); nothing is made up in its place.
 *
 * Levels:
 *  NORMAL        the threads asked for (the model runs two at most)
 *  WARM, HOT     one thread
 *  CRITICAL      the phone's model doesn't start a turn
 *  COOLING_DOWN  still not, after CRITICAL, until the phone has read NORMAL for {@link #COOL_MS} in a row
 * Hotter applies at once; cooler waits {@link #STEP_DOWN_MS} of the cooler reading, so the level doesn't flap. Unplugged
 * at {@link #MIN_BATTERY_PCT}% or less, the phone's model doesn't start a turn either: a long answer could empty it.
 */
public final class DeviceGuard {

    public enum State { NORMAL, WARM, HOT, CRITICAL, COOLING_DOWN }

    /** Why the phone's model won't run now, or NONE. */
    public enum Pause { NONE, TOO_HOT, COOLING_DOWN, BATTERY_LOW }

    /** Android's PowerManager.THERMAL_STATUS_* values, copied so this class needs no Android import. */
    public static final int THERMAL_NONE = 0, THERMAL_LIGHT = 1, THERMAL_MODERATE = 2, THERMAL_SEVERE = 3, THERMAL_CRITICAL = 4;

    /** Battery temperatures, in tenths of a degree, for each step. Charging brings every step {@link #CHARGING_MARGIN} earlier. */
    static final int WARM_TENTHS = 420, HOT_TENTHS = 450, CRITICAL_TENTHS = 480, CHARGING_MARGIN = 20;
    public static final int MIN_BATTERY_PCT = 5;
    public static final long STEP_DOWN_MS = 60_000L;
    public static final long COOL_MS = 180_000L;

    /** One reading of the phone. -1 = not available. */
    public static final class Reading {
        public final long nowMs;
        public final int thermalStatus;
        public final int batteryTenthsC;
        public final int batteryPct;
        public final boolean charging;

        public Reading(long nowMs, int thermalStatus, int batteryTenthsC, int batteryPct, boolean charging) {
            this.nowMs = nowMs;
            this.thermalStatus = thermalStatus;
            this.batteryTenthsC = batteryTenthsC;
            this.batteryPct = batteryPct;
            this.charging = charging;
        }
    }

    public static final class Decision {
        public final State state;
        public final Pause pause;
        /** The most threads the phone's model may use. */
        public final int maxThreads;

        Decision(State state, Pause pause, int maxThreads) {
            this.state = state;
            this.pause = pause;
            this.maxThreads = maxThreads;
        }

        public boolean paused() {
            return pause != Pause.NONE;
        }

        /** What the user is told when the phone's model won't answer, or null. */
        public String why() {
            switch (pause) {
                case TOO_HOT:
                    return "the phone is too hot to run its own model right now, so it is resting to cool down";
                case COOLING_DOWN:
                    return "the phone is still cooling down after getting too hot, so its own model is resting a few minutes more";
                case BATTERY_LOW:
                    return "the battery is almost empty, so the phone's own model isn't started until it is charging";
                default:
                    return null;
            }
        }
    }

    private State applied = State.NORMAL;
    private long coolerSince = -1;

    public synchronized State state() {
        return applied;
    }

    public synchronized Decision evaluate(Reading r) {
        State measured = measure(r);
        if (applied == State.CRITICAL || applied == State.COOLING_DOWN) {
            if (measured == State.CRITICAL) {
                applied = State.CRITICAL;
                coolerSince = -1;
            } else if (measured == State.NORMAL) {
                if (coolerSince < 0) {
                    coolerSince = r.nowMs;
                }
                applied = r.nowMs - coolerSince >= COOL_MS ? State.NORMAL : State.COOLING_DOWN;
                if (applied == State.NORMAL) {
                    coolerSince = -1;
                }
            } else {
                applied = State.COOLING_DOWN; // warm is not cool: the wait starts again once it reads normal
                coolerSince = -1;
            }
        } else if (measured.ordinal() > applied.ordinal()) {
            applied = measured;
            coolerSince = -1;
        } else if (measured.ordinal() < applied.ordinal()) {
            if (coolerSince < 0) {
                coolerSince = r.nowMs;
            }
            if (r.nowMs - coolerSince >= STEP_DOWN_MS) {
                applied = measured;
                coolerSince = -1;
            }
        } else {
            coolerSince = -1;
        }
        Pause pause = applied == State.CRITICAL ? Pause.TOO_HOT : applied == State.COOLING_DOWN ? Pause.COOLING_DOWN : Pause.NONE;
        if (pause == Pause.NONE && !r.charging && r.batteryPct >= 0 && r.batteryPct <= MIN_BATTERY_PCT) {
            pause = Pause.BATTERY_LOW;
        }
        int threads = applied == State.NORMAL ? 2 : applied == State.WARM || applied == State.HOT ? 1 : 0;
        return new Decision(applied, pause, threads);
    }

    /** The worse of the two heat signals in one reading, before any waiting. */
    static State measure(Reading r) {
        State byStatus = r.thermalStatus < 0 ? State.NORMAL
                : r.thermalStatus >= THERMAL_CRITICAL ? State.CRITICAL
                : r.thermalStatus == THERMAL_SEVERE ? State.HOT
                : r.thermalStatus == THERMAL_MODERATE ? State.WARM : State.NORMAL;
        State byBattery = State.NORMAL;
        if (r.batteryTenthsC > 0) {
            int t = r.batteryTenthsC + (r.charging ? CHARGING_MARGIN : 0);
            byBattery = t >= CRITICAL_TENTHS ? State.CRITICAL : t >= HOT_TENTHS ? State.HOT : t >= WARM_TENTHS ? State.WARM : State.NORMAL;
        }
        return byStatus.ordinal() >= byBattery.ordinal() ? byStatus : byBattery;
    }
}
