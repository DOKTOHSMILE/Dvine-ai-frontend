package ai.dvine.engine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the model configuration the rest of the app reads. No native state here:
 * the llama.cpp engine lives outside this class, so this file must stay pure
 * Java (the DVCIDE builder cannot resolve the {@code ai.dvine.bridge} package).
 *
 * <p>Copies are deep, so a caller that keeps a config around cannot see it change
 * under it.
 */
public final class EngineContext {

    public static final class Config {

        /** Where the GGUF weights live. */
        public final String modelPath;
        /** Where the tokenizer cache and mmap go. */
        public final String cacheDir;
        /** Model name reported to callers. */
        public final String modelId;
        /** Human readable model family, e.g. "llama". */
        public final String arch;
        /** Context length (n_ctx) actually requested. */
        public final int contextSize;
        /** Tokens a prompt may use: n_ctx minus room for the answer. */
        public final int promptBudget;
        /** Answer budget in tokens. */
        public final int maxTokens;
        /** Whether weights are memory-mapped. */
        public final boolean mmap;
        /** Extra llama.cpp options, e.g. threads and batch size. */
        public final Map<String, String> options;

        Config(
                String modelPath,
                String cacheDir,
                String modelId,
                String arch,
                int contextSize,
                int promptBudget,
                int maxTokens,
                boolean mmap,
                Map<String, String> options) {
            this.modelPath = modelPath;
            this.cacheDir = cacheDir;
            this.modelId = modelId;
            this.arch = arch;
            this.contextSize = contextSize;
            this.promptBudget = promptBudget;
            this.maxTokens = maxTokens;
            this.mmap = mmap;
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        }
    }

    private static volatile Config config;

    /** The configuration set by {@link #bind}, or null when the engine was never bound. */
    public static Config config() {
        return config;
    }

    /** True once {@link #bind} has run in this process. */
    public static boolean isBound() {
        return config != null;
    }

    /** Called once, right after the engine starts. */
    public static void bind(Config c) {
        config = c == null ? null : copy(c);
    }

    /** Drops the configuration; used on shutdown and by tests. */
    public static void clear() {
        config = null;
    }

    /** Model path of the current configuration, or null. */
    public static String modelPath() {
        Config c = config;
        return c == null ? null : c.modelPath;
    }

    /** Model id of the current configuration, or null. */
    public static String modelId() {
        Config c = config;
        return c == null ? null : c.modelId;
    }

    /** Context size of the current configuration, or 0. */
    public static int contextSize() {
        Config c = config;
        return c == null ? 0 : c.contextSize;
    }

    /** Copy of the current configuration's options, empty when unbound. */
    public static Map<String, String> options() {
        Config c = config;
        return c == null ? Collections.<String, String>emptyMap() : c.options;
    }

    private static Config copy(Config c) {
        return new Config(
                c.modelPath,
                c.cacheDir,
                c.modelId,
                c.arch,
                c.contextSize,
                c.promptBudget,
                c.maxTokens,
                c.mmap,
                c.options);
    }

    private EngineContext() {
    }
}