package ai.dvine.ops.mind;

/** Mirrors model readiness. {@link #load} only starts a load. Ready is a separate confirmation. */
public final class ModelRuntimeManager {
    public enum Phase {
        NOT_INSTALLED, DOWNLOADING, LOADING, READY, GENERATING, STOPPING, ERROR
    }

    private int loads;
    private String id = "";
    private Phase phase = Phase.NOT_INSTALLED;

    public synchronized void load(String modelId) {
        if (modelId != null && modelId.equals(id) && (phase == Phase.LOADING || phase == Phase.READY)) {
            return;
        }
        loads++;
        id = modelId == null ? "" : modelId;
        phase = Phase.LOADING;
    }

    /** Called only after the real runtime has loaded the file and can accept a prompt. */
    public synchronized void confirmReady(String modelId) {
        if (modelId == null || !modelId.equals(id) || phase == Phase.ERROR) {
            return;
        }
        phase = Phase.READY;
    }

    public synchronized void fail(String modelId) {
        if (modelId != null && modelId.equals(id)) {
            phase = Phase.ERROR;
        }
    }

    public synchronized void onMicrophone() {
        // deliberately not a load
    }

    public synchronized int loads() {
        return loads;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized boolean ready() {
        return phase == Phase.READY;
    }

    /** True only after {@link #confirmReady}. A load that has not finished is not loaded. */
    public synchronized boolean loaded() {
        return ready();
    }
}
