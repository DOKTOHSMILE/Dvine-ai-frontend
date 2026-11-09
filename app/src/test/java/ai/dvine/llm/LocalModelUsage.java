package ai.dvine.llm;

/** Test helper: records one API call in a usage log (its constructor is package-private). */
public final class LocalModelUsage {
    private LocalModelUsage() {
    }

    public static void record(UsageLog log, long at) {
        log.record(new UsageLog.Call("API: gateway (claude)", 200_000, 400, 500, 100, 200, true, 3000, at));
    }
}
