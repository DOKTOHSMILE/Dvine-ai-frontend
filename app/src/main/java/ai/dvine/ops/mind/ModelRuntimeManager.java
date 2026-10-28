package ai.dvine.ops.mind;

/** Loads a model runtime once and reuses it. Microphone activity must not call {@link #load}. */
public final class ModelRuntimeManager {
    private int loads;
    private boolean loaded;
    private String id = "";

    public synchronized void load(String modelId) {
        if (loaded && modelId != null && modelId.equals(id)) {
            return;
        }
        loads++;
        loaded = true;
        id = modelId == null ? "" : modelId;
    }

    public synchronized void onMicrophone() {
        // deliberately not a load
    }

    public synchronized int loads() {
        return loads;
    }

    public synchronized boolean loaded() {
        return loaded;
    }
}
