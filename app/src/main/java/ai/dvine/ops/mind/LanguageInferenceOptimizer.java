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

    public static final class InferenceScheduler implements AutoCloseable {
        private final int maxQueued;
        private int waiting;
        private int rejected;
        private int ran;
        private final java.util.concurrent.ExecutorService pool;

        public InferenceScheduler() {
            this(2);
        }

        public InferenceScheduler(int maxQueued) {
            this.maxQueued = Math.max(1, maxQueued);
            pool = java.util.concurrent.Executors.newFixedThreadPool(1, runnable -> {
                Thread t = new Thread(runnable, "dvine-work");
                t.setDaemon(true);
                return t;
            });
        }

        /** False when the queue is full. The job does not run on the caller. */
        public synchronized boolean submit(Runnable job) {
            if (job == null || waiting >= maxQueued) {
                rejected++;
                return false;
            }
            waiting++;
            pool.execute(() -> {
                try {
                    job.run();
                } finally {
                    synchronized (InferenceScheduler.this) {
                        waiting--;
                        ran++;
                        InferenceScheduler.this.notifyAll();
                    }
                }
            });
            return true;
        }

        public synchronized int rejected() {
            return rejected;
        }

        public synchronized int ran() {
            return ran;
        }

        public synchronized void awaitIdle(long ms) throws InterruptedException {
            long end = System.currentTimeMillis() + ms;
            while (waiting > 0 && System.currentTimeMillis() < end) {
                wait(Math.max(1, end - System.currentTimeMillis()));
            }
        }

        @Override
        public void close() {
            pool.shutdownNow();
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
