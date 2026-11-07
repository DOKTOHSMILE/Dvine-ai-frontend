package ai.dvine.ops;

import java.io.File;

import ai.dvine.ops.mind.DvinePerformanceMonitor;
import ai.dvine.ops.mind.InternetResearchEngine;
import ai.dvine.ops.mind.LanguageInferenceOptimizer;
import ai.dvine.ops.mind.ModelRuntimeManager;
import ai.dvine.ops.pipe.SemanticKnowledgeRepository;
import ai.dvine.ops.recon.EditableScene;
import ai.dvine.ops.ssh.SshConnectionManager;
import ai.dvine.ops.ssh.SshCredentialStore;
import ai.dvine.ops.ssh.SshHostKeyVerifier;
import ai.dvine.ops.ssh.SshTransport;
import ai.dvine.ops.task.DvineCommandGateway;
import ai.dvine.ops.task.DvineTaskManager;
import ai.dvine.ops.task.MicState;
import ai.dvine.ops.task.TaskEventDispatcher;
import ai.dvine.ops.task.TaskRecord;
import ai.dvine.ops.task.TaskState;
import ai.dvine.ops.task.TaskStateRepository;
import ai.dvine.ops.termux.TermuxCapabilityDetector;
import ai.dvine.ops.termux.TermuxCommandEngine;
import ai.dvine.ops.termux.TermuxExecutionService;
import ai.dvine.ops.vision.PhoneFileAccessManager;
import ai.dvine.ops.vision.VisualPerceptionEngine;

/** The phone-facing front door. Conversation and task-control events are separate entry paths. */
public final class AssistantCore {
    private final DvineTaskManager tasks;
    private final DvineTaskOrchestrator orchestrator;
    private final ModelRuntimeManager models = new ModelRuntimeManager();
    private final DvineCommandGateway commandGateway;
    private long now = 1_000;

    public AssistantCore(File dir, TermuxCapabilityDetector.Probe termuxProbe, TermuxExecutionService.Runner runner,
                         SshTransport transport, InternetResearchEngine.Fetcher web) {
        TaskStateRepository repo = new TaskStateRepository(dir);
        TaskEventDispatcher events = new TaskEventDispatcher();
        this.tasks = new DvineTaskManager(repo, events);
        TermuxCommandEngine termux = new TermuxCommandEngine(new TermuxCapabilityDetector(termuxProbe), new TermuxExecutionService(runner));
        SshConnectionManager ssh = new SshConnectionManager(transport, new SshHostKeyVerifier());
        VisualPerceptionEngine vision = new VisualPerceptionEngine(new PhoneFileAccessManager());
        InternetResearchEngine research = new InternetResearchEngine(web);
        LanguageInferenceOptimizer optimizer = new LanguageInferenceOptimizer(models);
        DvinePerformanceMonitor monitor = new DvinePerformanceMonitor();
        this.orchestrator = new DvineTaskOrchestrator(tasks, termux, ssh, vision, research, optimizer, monitor);
        this.orchestrator.setTransport(transport);
        this.performance = monitor;
        this.termux = termux;
        this.ssh = ssh;
        this.vision = vision;
        this.commandGateway = new DvineCommandGateway(tasks, this::executeAuthorizedCommand);
    }

    private final DvinePerformanceMonitor performance;
    private final TermuxCommandEngine termux;
    private final SshConnectionManager ssh;
    private final VisualPerceptionEngine vision;

    public static AssistantCore offline(File dir) {
        return new AssistantCore(dir, new TermuxCapabilityDetector.Probe() {
            public boolean installed() { return false; }
            public boolean permissionGranted() { return false; }
            public boolean resultBridgeConnected() { return false; }
        }, null, null, null);
    }

    public void onMicrophone(MicState state) {
        tasks.microphone(state, now++);
        models.onMicrophone();
    }

    public boolean hasActiveWork() { return tasks.hasActiveWork(); }
    public String activityLine() { return tasks.activityLine(); }
    public String restoredSummary() { return tasks.restoredSummary(); }
    public DvineTaskManager tasks() { return tasks; }
    public DvineTaskOrchestrator orchestrator() { return orchestrator; }
    public DvinePerformanceMonitor performance() { return performance; }
    public TermuxCommandEngine termux() { return termux; }
    public SshConnectionManager ssh() { return ssh; }
    public VisualPerceptionEngine vision() { return vision; }
    public ModelRuntimeManager models() { return models; }
    public DvineCommandGateway commandGateway() { return commandGateway; }
    public SemanticKnowledgeRepository memory() { return orchestrator.memory(); }

    private ai.dvine.ops.pipe.StructuredActionPlan lastPlan;
    private ai.dvine.ops.pipe.BrainCoordinator.Decision lastDecision;

    public ai.dvine.ops.pipe.StructuredActionPlan lastPlan() { return lastPlan; }
    public ai.dvine.ops.pipe.BrainCoordinator.Decision lastDecision() { return lastDecision; }

    /** Ordinary typed or conversational voice text always enters the conversation processor, never task control. */
    public Outcome hear(String text, double confidence) {
        lastDecision = new ai.dvine.ops.pipe.BrainCoordinator().decide(text, orchestrator.scene());
        lastPlan = lastDecision.plan;
        Outcome outcome = orchestrator.handle(text, confidence, now++);
        if (outcome != null && (outcome.persona == null || outcome.persona.isEmpty()) && !lastDecision.persona.isEmpty()) {
            outcome.persona = lastDecision.persona;
        }
        return outcome;
    }

    /** Model interpretation of a user request is not itself a task-control authorization event. */
    public Outcome consider(String text, double confidence) {
        Outcome outcome = orchestrator.consider(text, confidence, now++);
        if (outcome == null) return Outcome.pass();
        lastDecision = new ai.dvine.ops.pipe.BrainCoordinator().decide(text, orchestrator.scene());
        lastPlan = lastDecision.plan;
        if ((outcome.persona == null || outcome.persona.isEmpty()) && !lastDecision.persona.isEmpty()) {
            outcome.persona = lastDecision.persona;
        }
        return outcome;
    }

    public boolean hasModel() { return orchestrator.hasModel(); }

    private void executeAuthorizedCommand(DvineCommandGateway.CommandIntent intent, TaskRecord target) {
        long at = now++;
        switch (intent.action) {
            case PAUSE:
                tasks.requestPause(target, at);
                break;
            case RESUME:
                tasks.markResuming(target, at);
                break;
            case CANCEL:
                tasks.cancel(target, at);
                break;
            case STOP:
                // The gateway currently fails closed for STOP; no alias to cancellation is permitted.
                throw new IllegalStateException("STOP semantics are not implemented");
            default:
                throw new IllegalArgumentException("Unsupported task-control action");
        }
    }

    public void rememberCorrection(String said) {
        String n = said == null ? "" : said.toLowerCase();
        if (n.contains("rim") && n.contains("tyre")) {
            orchestrator.memory().learn("motorcycle", "tyre", "front_tyre");
            orchestrator.memory().learn("motorcycle", "rim", "front_rim");
        }
        if (n.contains("take out") && n.contains("not permanently")) {
            orchestrator.memory().learn("3d-edit", "take out", "detach");
        }
    }

    public SshCredentialStore.Seal testSeal() {
        return new SshCredentialStore.Seal() {
            public boolean strong() { return true; }
            public byte[] seal(byte[] plain) {
                byte[] out = new byte[plain.length];
                for (int i = 0; i < plain.length; i++) out[i] = (byte) (plain[i] ^ 0x5A);
                return out;
            }
            public byte[] open(byte[] sealed) { return seal(sealed); }
        };
    }
}
