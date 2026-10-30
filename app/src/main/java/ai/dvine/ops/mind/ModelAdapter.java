package ai.dvine.ops.mind;

/**
 * The only place that tells the status flag what the real runtime just did.
 * Calling {@link ModelRuntimeManager#load} is not the same as the model being ready.
 */
public final class ModelAdapter {
    private final ModelRuntimeManager runtime;

    public ModelAdapter(ModelRuntimeManager runtime) {
        this.runtime = runtime;
    }

    public void report(String modelId, boolean inferenceReady) {
        if (runtime == null || modelId == null || modelId.isEmpty()) {
            return;
        }
        runtime.load(modelId);
        if (inferenceReady) {
            runtime.confirmReady(modelId);
        } else {
            runtime.fail(modelId);
        }
    }
}
