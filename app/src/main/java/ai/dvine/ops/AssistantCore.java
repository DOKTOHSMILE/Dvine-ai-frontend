package ai.dvine.ops;

import java.io.File;

import ai.dvine.ops.mind.DvinePerformanceMonitor;
import ai.dvine.ops.mind.InternetResearchEngine;
import ai.dvine.ops.mind.LanguageInferenceOptimizer;
import ai.dvine.ops.mind.ModelRuntimeManager;
import ai.dvine.ops.pipe.SemanticKnowledgeRepository;
import ai.dvine.ops.recon.EditableScene;
import ai.dvine.ops.recon.ProceduralGeometryBuilder;
import ai.dvine.ops.ssh.SshConnectionManager;
import ai.dvine.ops.ssh.SshCredentialStore;
import ai.dvine.ops.ssh.SshHostKeyVerifier;
import ai.dvine.ops.ssh.SshTransport;
import ai.dvine.ops.task.DvineTaskManager;
import ai.dvine.ops.task.MicState;
import ai.dvine.ops.task.TaskEventDispatcher;
import ai.dvine.ops.task.TaskRecord;
import ai.dvine.ops.task.TaskStateRepository;
import ai.dvine.ops.task.VoiceInterruptionManager;
import ai.dvine.ops.termux.TermuxCapabilityDetector;
import ai.dvine.ops.termux.TermuxCommandEngine;
import ai.dvine.ops.termux.TermuxExecutionService;
import ai.dvine.ops.vision.PhoneFileAccessManager;
import ai.dvine.ops.vision.VisualPerceptionEngine;

/** The phone-facing front door. Microphone events and task control stay here. */
public final class AssistantCore {
    private final DvineTaskManager tasks;
    private final VoiceInterruptionManager voice = new VoiceInterruptionManager();
    private final DvineTaskOrchestrator orchestrator;
    private final ModelRuntimeManager models = new ModelRuntimeManager();
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

    public boolean hasActiveWork() {
        return tasks.hasActiveWork();
    }

    public String activityLine() {
        return tasks.activityLine();
    }

    public String restoredSummary() {
        return tasks.restoredSummary();
    }

    public DvineTaskManager tasks() {
        return tasks;
    }

    public DvineTaskOrchestrator orchestrator() {
        return orchestrator;
    }

    public DvinePerformanceMonitor performance() {
        return performance;
    }

    public TermuxCommandEngine termux() {
        return termux;
    }

    public SshConnectionManager ssh() {
        return ssh;
    }

    public VisualPerceptionEngine vision() {
        return vision;
    }

    public ModelRuntimeManager models() {
        return models;
    }

    public SemanticKnowledgeRepository memory() {
        return orchestrator.memory();
    }

    public Outcome hear(String text, double confidence) {
        long at = System.currentTimeMillis();
        VoiceInterruptionManager.Decision d = voice.decide(text, confidence, tasks.hasActiveWork(), tasks.paused().size(), at);
        if (d.action != VoiceInterruptionManager.Action.NONE) {
            return control(d, now++);
        }
        return orchestrator.handle(text, confidence, now++);
    }

    private Outcome control(VoiceInterruptionManager.Decision d, long at) {
        switch (d.action) {
            case CONTINUE:
                Outcome keep = Outcome.say(d.say, false);
                keep.resumeSpeech = true;
                keep.activity = tasks.activityLine();
                return keep;
            case CLARIFY:
                Outcome ask = Outcome.say(d.say == null ? "" : d.say, false);
                ask.listen = d.say != null && !d.say.isEmpty();
                ask.activity = tasks.activityLine();
                return ask;
            case PAUSE: {
                TaskRecord active = running();
                if (active != null) {
                    tasks.requestPause(active, at);
                }
                Outcome o = Outcome.say(d.say, active != null);
                o.activity = tasks.activityLine();
                return o;
            }
            case RESUME:
                return resume(d.followUp == null ? "" : d.followUp, at);
            case CANCEL: {
                TaskRecord active = running();
                if (active == null) {
                    return Outcome.say("Nothing is running to cancel.", false);
                }
                tasks.cancel(active, at);
                Outcome o = Outcome.say("I cancelled that task. Anything else I was keeping is still there.", true);
                o.activity = tasks.activityLine();
                return o;
            }
            case SWITCH: {
                TaskRecord active = running();
                if (active != null) {
                    tasks.requestPause(active, at);
                }
                Outcome next = orchestrator.handle(d.followUp, 1, at);
                next.stopGeneration = true;
                if (next.speech == null || next.speech.isEmpty()) {
                    next.speech = "I paused the other task.";
                    next.handled = true;
                }
                return next;
            }
            default:
                return Outcome.pass();
        }
    }

    private Outcome resume(String utterance, long at) {
        var choice = tasks.chooseResume(utterance);
        if (choice.task == null) {
            Outcome o = Outcome.say(choice.clarify, false);
            o.listen = !choice.refused;
            return o;
        }
        tasks.markResuming(choice.task, at);
        String where = choice.task.checkpoint == null || choice.task.checkpoint.isEmpty() ? "the start" : choice.task.checkpoint;
        Outcome o = Outcome.say("I'm resuming " + choice.task.description + " from " + where.replace('_', ' ') + ".", false);
        o.taskId = choice.task.id;
        o.activity = tasks.activityLine();
        return o;
    }

    private TaskRecord running() {
        for (TaskRecord t : tasks.all()) {
            if (t.state != null && t.state.runningLike()) {
                return t;
            }
        }
        TaskRecord active = tasks.active();
        return active != null && active.state.runningLike() ? active : null;
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

    public EditableScene demoMotorcycle() {
        EditableScene scene = new ProceduralGeometryBuilder().motorcycle();
        orchestrator.setScene(scene);
        return scene;
    }

    public EditableScene demoCar() {
        EditableScene scene = new ProceduralGeometryBuilder().car();
        orchestrator.setScene(scene);
        return scene;
    }

    public SshCredentialStore.Seal testSeal() {
        return new SshCredentialStore.Seal() {
            public boolean strong() { return true; }
            public byte[] seal(byte[] plain) {
                byte[] out = new byte[plain.length];
                for (int i = 0; i < plain.length; i++) {
                    out[i] = (byte) (plain[i] ^ 0x5A);
                }
                return out;
            }
            public byte[] open(byte[] sealed) {
                return seal(sealed);
            }
        };
    }
}
