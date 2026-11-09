package ai.dvine.engine;

import ai.dvine.bridge.Probe;

import java.io.File;

/**
 * Decides the engine's context size from the model file and the phone's free
 * memory, and writes down why.
 *
 * <p>Two kinds of limits have to be obeyed at once: the model's own context
 * (what the file says it was trained with) and the device's memory (weights +
 * KV cache + compute workspace must fit in what is actually free). The rule is:
 * start from the model's number, never go above it, and step down while the
 * estimate does not fit. Nothing is silent — every cap lands in the log line.
 *
 * <p>Everything is static: it is arithmetic on a {@link Probe}, so it is
 * testable without a phone and without loading a model.
 */
public final class EngineContext {

    /** Below this a chat is useless; the caller refuses to start. */
    public static final int MIN_CTX = 1024;
    /** What we ask for when the model does not say. */
    public static final int TARGET_CTX = 4096;
    /** No context above this, whatever the file claims. */
    public static final int MAX_CTX = 32768;
    /** The old, proven batch cap. */
    public static final int MAX_BATCH = 512;
    /** Room left at the top of the context for the answer. */
    private static final int CTX_HEADROOM = 256;

    /** Scratch space beyond the weights: the compute graph and activations. */
    private static final double COMPUTE_RATIO = 0.15;
    /** KV per token when the GGUF gave us no dimensions: scaled from weights. */
    private static final double KV_UNKNOWN_RATIO = 1e-4;
    /** Headroom on top of the estimate for allocator and thread overhead. */
    private static final double GUARD = 1.25;
    /** Never plan to use more than this share of the free memory. */
    private static final double FREE_SAFETY = 0.80;
    /** Always leave this much free. */
    private static final long FREE_MARGIN = 64L * 1024 * 1024;
    /** Never plan less KV than this per token. */
    private static final long MIN_KV_TOKEN = 64L * 1024;

    private EngineContext() {
    }

    /**
     * The context length a bundled model.json asks for, or 0.
     */
    public static int jsonContext(String modelJsonPath) {
        if (modelJsonPath == null) {
            return 0;
        }
        File f = new File(modelJsonPath);
        if (!f.isFile()) {
            return 0;
        }
        String json = readFile(f);
        if (json == null) {
            return 0;
        }
        int i = json.indexOf("\"context_length\"");
        if (i < 0) {
            return 0;
        }
        i = json.indexOf(':', i + 16);
        if (i < 0) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; j < json.length() && sb.length() < 12; j++) {
            char c = json.charAt(j);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (sb.length() > 0) {
                break;
            } else if (!Character.isWhitespace(c) && c != '"') {
                return 0;
            }
        }
        try {
            return sb.length() == 0 ? 0 : Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String readFile(File f) {
        byte[] b = null;
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                b = new byte[(int) Math.min(f.length(), 64 * 1024)];
                int off = 0;
                while (off < b.length) {
                    int n = in.read(b, off, b.length - off);
                    if (n < 0) {
                        break;
                    }
                    off += n;
                }
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
        return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Bytes of KV cache for one token of context: n_layer tokens worth of K and
     * V, in f16. Falls back to a share of the weights when the header had no
     * dimensions (which happens when a GGUF was written by an older tool).
     */
    public static long kvPerToken(Probe p, long weightsBytes) {
        int k = p.nEmbdK > 0 ? p.nEmbdK : p.nEmbd;
        int v = p.nEmbdV > 0 ? p.nEmbdV : p.nEmbd;
        if (p.nLayer > 0 && k > 0 && v > 0) {
            return Math.max(MIN_KV_TOKEN, (long) p.nLayer * (k + v) * 2L);
        }
        if (weightsBytes > 0) {
            return Math.max(MIN_KV_TOKEN, (long) (weightsBytes * KV_UNKNOWN_RATIO));
        }
        return Math.max(MIN_KV_TOKEN, 1024L * 1024);
    }

    /**
     * The whole footprint of a run at a given context: weights + compute
     * workspace + KV cache, with {@link #GUARD} on top.
     */
    public static long estimateBytes(Probe p, long weightsBytes, int nCtx) {
        long kv = kvPerToken(p, weightsBytes) * Math.max(1, nCtx);
        long compute = (long) (weightsBytes * COMPUTE_RATIO);
        return (long) ((weightsBytes + compute + kv) * GUARD);
    }

    /**
     * The context this device can give this model.
     *
     * <p>Returns an {@link EngineConfig} with {@code nCtx == 0} when nothing
     * fits: the reason explains what was needed and what was there.
     *
     * @param freeBytes free memory now, or 0 when the device would not say
     */
    public static EngineConfig decide(Probe p, String modelJsonPath, long weightsBytes,
                                      long freeBytes, int nThreads, boolean mlock) {
        int modelLimit = 0;
        String limitSource = "";
        if (p.contextLength > 0) {
            modelLimit = p.contextLength;
            limitSource = "the GGUF header";
        }
        int json = jsonContext(modelJsonPath);
        if (json > 0 && (modelLimit == 0 || json < modelLimit)) {
            // model.json is what the author intended for the phone; a smaller
            // number there wins, a larger one does not beat the file.
            modelLimit = json;
            limitSource = "model.json";
        }

        int wanted = modelLimit > 0 ? Math.min(modelLimit, MAX_CTX) : TARGET_CTX;
        if (modelLimit > MAX_CTX) {
            wanted = MAX_CTX;
        }
        if (wanted < MIN_CTX) {
            wanted = MIN_CTX;
        }

        long budget = 0;
        boolean haveBudget = freeBytes > 0;
        if (haveBudget) {
            budget = (long) (freeBytes * FREE_SAFETY) - FREE_MARGIN;
        }

        StringBuilder why = new StringBuilder();
        if (modelLimit > MAX_CTX) {
            why.append("model asks for ").append(modelLimit)
               .append(" tokens, capped at ").append(MAX_CTX).append("; ");
        }

        int ctx = wanted;
        while (ctx > MIN_CTX) {
            long need = estimateBytes(p, weightsBytes, ctx);
            if (!haveBudget || need <= budget) {
                break;
            }
            ctx = step(ctx);
        }

        long need = estimateBytes(p, weightsBytes, ctx);
        if (haveBudget && need > budget) {
            EngineConfig refused = new EngineConfig(modelJsonPath, 0, modelLimit, 0, 0, 0,
                nThreads, mlock, false, need,
                String.format(java.util.Locale.US,
                    "needs %d MB for %d tokens, only %d MB is free",
                    mb(need), ctx, mb(freeBytes)),
                p.nLayer, p.nEmbd, p.nHead, p.nHeadKv, p.nEmbdK, p.nEmbdV, p.nGqa, freeBytes);
            return refused;
        }

        if (ctx < wanted) {
            why.append("context reduced from ").append(wanted).append(" to ").append(ctx)
               .append(" to fit the free memory (").append(mb(freeBytes)).append(" MB free); ");
        }

        int nBatch = Math.max(64, Math.min(MAX_BATCH, ctx / 2));
        int maxInput = Math.max(128, ctx - CTX_HEADROOM);
        int maxTokens = Math.max(128, Math.min(1024, ctx / 4));

        EngineConfig cfg = new EngineConfig(modelJsonPath, ctx, modelLimit, nBatch,
            maxInput, maxTokens, nThreads, mlock, false, need, why.toString(),
            p.nLayer, p.nEmbd, p.nHead, p.nHeadKv, p.nEmbdK, p.nEmbdV, p.nGqa, freeBytes);
        return cfg;
    }

    private static int step(int ctx) {
        if (ctx > 8192) {
            return 1024;
        }
        if (ctx > 2048) {
            return 256;
        }
        return 128;
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * A one-line log of the decision.
     */
    public static String logLine(EngineConfig c, long freeBytes, long weightsBytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("weights=").append(mb(weightsBytes)).append("MB");
        if (freeBytes > 0) {
            sb.append(" free=").append(mb(freeBytes)).append("MB");
        } else {
            sb.append(" free=?");
        }
        sb.append(" -> ").append(c.describe());
        return sb.toString();
    }
}