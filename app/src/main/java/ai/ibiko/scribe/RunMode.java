package ai.ibiko.scribe;

/**
 * How long Ibiko is willing to stay with one job. Quick and Balanced keep today's short-reply limits. Deep Reasoning,
 * Extended Project and Autonomous Development do not: a live model is not failed just because a short timer ran out.
 * These are still budgets, not a promise that Android will keep the process alive forever.
 */
public enum RunMode {
    QUICK(45_000L, 90_000L, 120_000L, 20_000L, 60_000L, 30L * 60_000L, 8, 1_000_000L),
    BALANCED(60_000L, 180_000L, 600_000L, 30_000L, 180_000L, 8L * 3_600_000L, 8, 1_000_000L),
    DEEP(90_000L, 600_000L, 900_000L, 90_000L, 600_000L, 8L * 3_600_000L, 20, 4_000_000L),
    EXTENDED(120_000L, 1_200_000L, 1_200_000L, 180_000L, 1_200_000L, 24L * 3_600_000L, 40, 8_000_000L),
    AUTONOMOUS(180_000L, 3_600_000L, 1_800_000L, 300_000L, 3_600_000L, 24L * 3_600_000L, 80, 8_000_000L);

    /** Silence while tokens are arriving before the reply is treated as stalled. */
    public final long idleMs;
    /** Writing time after the first token. */
    public final long replyCapMs;
    /** Time allowed to read a long prompt before the first token. */
    public final long readMs;
    /** One local shell command. */
    public final long shellMs;
    /** One Termux command. A dead process is not resumed mid-instruction; the checkpoint is. */
    public final long termuxMs;
    /** A whole agent job, across rounds. */
    public final long workMs;
    /** Tool steps in one round before a progress note. */
    public final int roundSteps;
    /** Largest single file a clone will copy. Bigger files are named, not loaded into memory. */
    public final long fileBytes;

    RunMode(long idleMs, long replyCapMs, long readMs, long shellMs, long termuxMs, long workMs, int roundSteps, long fileBytes) {
        this.idleMs = idleMs;
        this.replyCapMs = replyCapMs;
        this.readMs = readMs;
        this.shellMs = shellMs;
        this.termuxMs = termuxMs;
        this.workMs = workMs;
        this.roundSteps = roundSteps;
        this.fileBytes = fileBytes;
    }

    /** The setting value (0 quick … 4 autonomous). Anything else is Balanced. */
    public static RunMode of(int id) {
        RunMode[] all = values();
        return id < 0 || id >= all.length ? BALANCED : all[id];
    }
}
