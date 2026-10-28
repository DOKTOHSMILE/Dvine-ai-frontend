package ai.dvine.ops.mind;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stage timings only. Prompts and secrets are not stored. */
public final class DvinePerformanceMonitor {
    private final Map<String, Long> totals = new LinkedHashMap<String, Long>();
    private final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    private int researchCalls;

    public void add(String stage, long nanos) {
        totals.put(stage, totals.getOrDefault(stage, 0L) + Math.max(0, nanos));
        counts.put(stage, counts.getOrDefault(stage, 0) + 1);
    }

    public void research() {
        researchCalls++;
    }

    public int researchCalls() {
        return researchCalls;
    }

    public long total(String stage) {
        return totals.getOrDefault(stage, 0L);
    }

    public int count(String stage) {
        return counts.getOrDefault(stage, 0);
    }
}
