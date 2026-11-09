package ai.dvine.engine;

import java.util.Locale;

/**
 * A fully resolved engine configuration. Every value has already been reasoned
 * about: a context size, a batch size and a token cap that fit the model file
 * AND the device's free memory.
 */
public class EngineConfig {

    /** A model file we cannot size: context and dims unknown. */
    public static final int UNKNOWN = -1;

    public final String modelPath;
    /** Context length (n_ctx) actually requested. */
    public final int nCtx;
    /** Model's own limit from the file (0 when unknown). */
    public final int modelLimit;
    /** Batch size (n_batch), capped. */
    public final int nBatch;
    /** Tokens a prompt may use: n_ctx minus room for the answer. */
    public final int maxInputTokens;
    /** Cap for one reply's output. */
    public final int maxTokens;
    public final int nThreads;
    public final boolean mlock;
    public final boolean gpu;
    /** Estimated bytes for weights + KV cache + compute. */
    public final long estimatedBytes;
    /** The reason for every cap that was applied (empty when nothing was capped). */
    public final String reason;

    /** Model geometry from the GGUF header (UNKNOWN = not probed). */
    public final int nLayer, nEmbd, nHead, nHeadKv, nEmbdK, nEmbdV, nGqa;

    /** A free-RAM figure in bytes; 0 when the device did not say. */
    public final long freeBytes;

    public EngineConfig(String modelPath, int nCtx, int modelLimit, int nBatch,
                        int maxInputTokens, int maxTokens, int nThreads,
                        boolean mlock, boolean gpu, long estimatedBytes,
                        String reason, int nLayer, int nEmbd, int nHead, int nHeadKv,
                        int nEmbdK, int nEmbdV, int nGqa, long freeBytes) {
        this.modelPath = modelPath;
        this.nCtx = nCtx;
        this.modelLimit = modelLimit;
        this.nBatch = nBatch;
        this.maxInputTokens = maxInputTokens;
        this.maxTokens = maxTokens;
        this.nThreads = nThreads;
        this.mlock = mlock;
        this.gpu = gpu;
        this.estimatedBytes = estimatedBytes;
        this.reason = reason == null ? "" : reason;
        this.nLayer = nLayer;
        this.nEmbd = nEmbd;
        this.nHead = nHead;
        this.nHeadKv = nHeadKv;
        this.nEmbdK = nEmbdK;
        this.nEmbdV = nEmbdV;
        this.nGqa = nGqa;
        this.freeBytes = freeBytes;
    }

    /**
     * True when a requested context was reduced and a reason was written down.
     */
    public boolean hasReason() {
        return reason.length() > 0;
    }

    /**
     * One line for the log: what was asked for, what was given and why.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("n_ctx=").append(nCtx)
          .append(" modelLimit=").append(modelLimit <= 0 ? "?" : modelLimit)
          .append(" n_batch=").append(nBatch)
          .append(" in=").append(maxInputTokens)
          .append(" out=").append(maxTokens)
          .append(" threads=").append(nThreads);
        if (freeBytes > 0) {
            sb.append(" free=").append(freeBytes / (1024 * 1024)).append("MB");
        }
        if (estimatedBytes > 0) {
            sb.append(" est=").append(estimatedBytes / (1024 * 1024)).append("MB");
        }
        if (hasReason()) {
            sb.append(" (").append(reason).append(")");
        }
        return sb.toString();
    }

    /**
     * A human sentence for the UI when the context had to come down.
     */
    public String userMessage() {
        if (!hasReason()) {
            return null;
        }
        return String.format(Locale.US,
            "Context set to %d tokens%s. The model file asks for %d.",
            nCtx,
            freeBytes > 0 ? " to fit the free memory" : "",
            modelLimit > 0 ? modelLimit : nCtx);
    }
}