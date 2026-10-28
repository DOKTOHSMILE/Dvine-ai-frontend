package ai.dvine.ops.mind;

/** Routes a request to the cheapest path that can still do it. */
public final class LanguageInferenceOptimizer {
    public enum Path { FAST, REASONING, RESEARCH, VISION }

    private final ModelRuntimeManager runtime;
    private final PromptContextManager context = new PromptContextManager();
    private final InferenceScheduler scheduler = new InferenceScheduler();
    private final TokenizationOptimizer tokens = new TokenizationOptimizer();
    private final HardwareCapabilityDetector hardware = new HardwareCapabilityDetector();

    public LanguageInferenceOptimizer(ModelRuntimeManager runtime) {
        this.runtime = runtime;
    }

    public Path route(String utterance) {
        String n = utterance == null ? "" : utterance.toLowerCase();
        if (n.matches(".*(pause|resume|rotate|open the selected|list the files).*")) {
            return Path.FAST;
        }
        if (n.matches(".*(image|photo|picture|reconstruct|3d|wheel|tyre|tire).*")) {
            return Path.VISION;
        }
        if (n.matches(".*(unfamiliar|how do i|what is a|latest|documentation|error i haven't).*") || n.contains("xyzzy")) {
            return Path.RESEARCH;
        }
        if (n.length() > 180 || n.contains(" and ") && n.contains("then")) {
            return Path.REASONING;
        }
        return Path.FAST;
    }

    public ModelRuntimeManager runtime() {
        return runtime;
    }

    public PromptContextManager context() {
        return context;
    }

    public InferenceScheduler scheduler() {
        return scheduler;
    }

    public TokenizationOptimizer tokens() {
        return tokens;
    }

    public HardwareCapabilityDetector hardware() {
        return hardware;
    }

    public static final class InferenceScheduler {
        private int queued;

        public void submit(Runnable job) {
            queued++;
            if (job != null) {
                job.run();
            }
        }

        public int queued() {
            return queued;
        }
    }

    public static final class TokenizationOptimizer {
        public int estimate(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            return Math.max(1, text.length() / 4);
        }
    }

    public static final class HardwareCapabilityDetector {
        public int threads() {
            int cpus = Runtime.getRuntime().availableProcessors();
            return Math.max(1, Math.min(4, cpus - 1));
        }
    }
}
