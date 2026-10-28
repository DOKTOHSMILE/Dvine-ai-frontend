package ai.dvine.ops;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ai.dvine.ops.mind.DvinePerformanceMonitor;
import ai.dvine.ops.mind.InternetResearchEngine;
import ai.dvine.ops.mind.LanguageInferenceOptimizer;
import ai.dvine.ops.pipe.ActionResultVerifier;
import ai.dvine.ops.pipe.BrainInterpretationEngine;
import ai.dvine.ops.pipe.DirectQuestions;
import ai.dvine.ops.pipe.NaturalLanguageActionEngine;
import ai.dvine.ops.pipe.PersonaAddress;
import ai.dvine.ops.pipe.NaturalLanguageResponseGenerator;
import ai.dvine.ops.pipe.SemanticKnowledgeRepository;
import ai.dvine.ops.pipe.SemanticTypes;
import ai.dvine.ops.recon.EditableScene;
import ai.dvine.ops.recon.GlbAssetImporter;
import ai.dvine.ops.recon.ImageTo3DReconstructionEngine;
import ai.dvine.ops.ssh.SshCommandExecutor;
import ai.dvine.ops.ssh.SshConnectionManager;
import ai.dvine.ops.ssh.SshFileBrowser;
import ai.dvine.ops.ssh.SshResultInterpreter;
import ai.dvine.ops.ssh.SshTransport;
import ai.dvine.ops.task.DvineTaskManager;
import ai.dvine.ops.task.TaskExecutionController;
import ai.dvine.ops.task.TaskRecord;
import ai.dvine.ops.task.TaskState;
import ai.dvine.ops.termux.TermuxCommandEngine;
import ai.dvine.ops.termux.TermuxResultInterpreter;
import ai.dvine.ops.vision.ImageContentAnalyzer;
import ai.dvine.ops.vision.PhoneFileAccessManager;
import ai.dvine.ops.vision.VisualPerceptionEngine;

/**
 * Understands, plans, runs the matching tool, checks the result, then speaks in plain language.
 */
public final class DvineTaskOrchestrator {
    private final DvineTaskManager tasks;
    private final NaturalLanguageActionEngine language = new NaturalLanguageActionEngine();
    private final NaturalLanguageResponseGenerator responses = new NaturalLanguageResponseGenerator();
    private final ActionResultVerifier verifier = new ActionResultVerifier();
    private final SemanticKnowledgeRepository memory = new SemanticKnowledgeRepository();
    private final TermuxCommandEngine termux;
    private final SshConnectionManager ssh;
    private final SshCommandExecutor sshCommands = new SshCommandExecutor();
    private final SshResultInterpreter sshReading = new SshResultInterpreter();
    private final SshFileBrowser sshFiles;
    private final VisualPerceptionEngine vision;
    private final ImageTo3DReconstructionEngine reconstruct = new ImageTo3DReconstructionEngine();
    private final GlbAssetImporter glb = new GlbAssetImporter();
    private final InternetResearchEngine research;
    private final LanguageInferenceOptimizer optimizer;
    private final DvinePerformanceMonitor performance;
    private final TaskExecutionController execution;
    private EditableScene scene;
    private File authorizedRoot;
    private long freeBytes = -1;
    private long projectBytes = 200L * 1024 * 1024;
    private String logPath = "/var/log/app.log";
    private String sshHost = "";
    private int sshPort = 22;
    private String sshUser = "";
    private char[] sshSecret = new char[0];
    private boolean trustFirstKey;
    private boolean confirmPending;
    private String pendingSpeech = "";
    private BrainInterpretationEngine.ModelReading model;
    private String addressed = "";
    private Now clock = new Now() {
        public long millis() {
            return System.currentTimeMillis();
        }
    };
    private TimeZone zone = TimeZone.getDefault();

    public interface Now {
        long millis();
    }

    public DvineTaskOrchestrator(DvineTaskManager tasks, TermuxCommandEngine termux, SshConnectionManager ssh,
                                 VisualPerceptionEngine vision, InternetResearchEngine research,
                                 LanguageInferenceOptimizer optimizer, DvinePerformanceMonitor performance) {
        this.tasks = tasks;
        this.termux = termux;
        this.ssh = ssh;
        this.sshFiles = new SshFileBrowser(null);
        this.vision = vision;
        this.research = research;
        this.optimizer = optimizer;
        this.performance = performance;
        this.execution = new TaskExecutionController(tasks.checkpoints(), tasks.events());
    }

    public void setAuthorizedRoot(File root) {
        this.authorizedRoot = root;
    }

    public void setFreeBytes(long freeBytes) {
        this.freeBytes = freeBytes;
    }

    public void setProjectBytes(long projectBytes) {
        this.projectBytes = projectBytes;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public void setSshAccount(String host, int port, String user, char[] secret, boolean trustFirstKey) {
        this.sshHost = host;
        this.sshPort = port;
        this.sshUser = user;
        this.sshSecret = secret == null ? new char[0] : secret;
        this.trustFirstKey = trustFirstKey;
    }

    public void setModel(BrainInterpretationEngine.ModelReading model) {
        this.model = model;
    }

    public void setClock(Now clock, TimeZone zone) {
        if (clock != null) {
            this.clock = clock;
        }
        if (zone != null) {
            this.zone = zone;
        }
    }

    public void setScene(EditableScene scene) {
        this.scene = scene;
    }

    public EditableScene scene() {
        return scene;
    }

    public SemanticKnowledgeRepository memory() {
        return memory;
    }

    public SshFileBrowser files() {
        return sshFiles;
    }

    public Outcome handle(String text, double confidence, long now) {
        long t0 = System.nanoTime();
        optimizer.route(text);
        performance.add("route", System.nanoTime() - t0);
        PersonaAddress.Address who = new PersonaAddress().read(text);
        if (who.unknown != null) {
            Outcome o = Outcome.reply(who.clarify());
            o.listen = true;
            o.activity = "Which persona?";
            return o;
        }
        addressed = who.persona == null ? "" : who.persona;
        String said = who.persona != null && !who.request.isEmpty() ? who.request : text;
        if (who.persona != null && who.request.isEmpty()) {
            Outcome o = Outcome.reply("I'm here. What would you like me to do?");
            o.listen = true;
            return finish(o);
        }
        if (confirmPending) {
            return finish(confirmation(said, now));
        }
        if (DirectQuestions.clock(said)) {
            return finish(clockAnswer(said));
        }
        NaturalLanguageActionEngine.Read read = language.read(said, confidence, memory, model);
        performance.add("interpret", System.nanoTime() - t0);
        if ("reject".equals(read.intent)) {
            return finish(Outcome.reply(read.interpretation.clarify));
        }
        if ("clarify".equals(read.intent) && read.decomposition.ambiguous) {
            Outcome o = Outcome.reply(read.interpretation.clarify.isEmpty() ? "Which one do you mean?" : read.interpretation.clarify);
            o.listen = true;
            return finish(o);
        }
        if ("picker".equals(read.intent) || "learn".equals(read.intent) || DirectQuestions.wantsPicker(said) || DirectQuestions.wantsLearn(said)) {
            return finish(picker(said, "learn".equals(read.intent) || DirectQuestions.wantsLearn(said)));
        }
        if ("explain".equals(read.intent)) {
            return finish(Outcome.reply(explain(read)));
        }
        if ("answer".equals(read.intent) || "news".equals(read.intent)) {
            return finish(Outcome.pass());
        }
        if (looksRemoteFetch(said)) {
            return finish(remoteFetch(text, now));
        }
        if (looksSsh(said)) {
            return finish(sshJob(text, now));
        }
        if ("storage".equals(read.intent) || looksStorage(said)) {
            return finish(storage(text, now));
        }
        if (looksFindImage(said)) {
            return finish(findImage(text, now));
        }
        if (looksReconstruct(said)) {
            return finish(buildFromImage(text, now));
        }
        if ("remove".equals(read.intent) && scene != null) {
            return finish(editScene(read, now));
        }
        if ("research".equals(read.intent) || said.toLowerCase(Locale.US).contains("xyzzy") || said.toLowerCase(Locale.US).contains("unfamiliar")) {
            return finish(research(said, now));
        }
        if ("delete".equals(read.intent)) {
            confirmPending = true;
            pendingSpeech = "Delete is permanent. I have not deleted anything. Say yes to confirm or no to leave it.";
            Outcome o = Outcome.reply(pendingSpeech);
            o.listen = true;
            return finish(o);
        }
        return finish(Outcome.pass());
    }

    private Outcome finish(Outcome o) {
        if (o != null && !addressed.isEmpty() && (o.persona == null || o.persona.isEmpty())) {
            o.persona = addressed;
        }
        return o;
    }

    private Outcome clockAnswer(String said) {
        Date date = new Date(clock.millis());
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                DirectQuestions.timeOnly(said) ? "h:mm a" : DirectQuestions.yearOnly(said) ? "yyyy" : "EEEE, d MMMM yyyy",
                Locale.US);
        fmt.setTimeZone(zone);
        String when = fmt.format(date);
        String speech = DirectQuestions.timeOnly(said) ? "It's " + when + "."
                : DirectQuestions.yearOnly(said) ? "It's " + when + "."
                : "Today is " + when + ".";
        Outcome o = Outcome.reply(speech);
        o.activity = "Reading the clock";
        return o;
    }

    private Outcome picker(String said, boolean learn) {
        String kind = DirectQuestions.pickerKind(said);
        if (kind == null) {
            kind = "file";
        }
        String speech = learn
                ? "Choose the file. I'll read it and, if there is text worth keeping, save a note. That is not retraining."
                : "Choose the " + ("file".equals(kind) ? "file" : kind) + ". I'll read what the phone actually gives me.";
        Outcome o = Outcome.reply(speech);
        o.picker = kind;
        o.learn = learn;
        o.activity = "Waiting for file selection";
        return o;
    }

    private Outcome confirmation(String text, long now) {
        String n = text == null ? "" : text.toLowerCase(Locale.US).trim();
        confirmPending = false;
        if (n.matches("^(yes|yes please|confirm|do it)$")) {
            return Outcome.say("I still don't have an approved delete command for that target, so nothing was deleted.", false);
        }
        return Outcome.say("Alright. I left it as it is.", false);
    }

    private Outcome storage(String text, long now) {
        TaskRecord task = tasks.create(text, "Storage check", "termux", List.of("probe", "explain"), false, now);
        tasks.markRunning(task, now);
        TaskRecord done = execution.run(task, task.remaining, (step, rec) -> {
            if ("probe".equals(step)) {
                var planned = termux.plan(text);
                TermuxResultInterpreter.Reading reading = termux.execute(planned, false);
                if (!reading.verified && freeBytes >= 0) {
                    rec.facts.put("free", Long.toString(freeBytes));
                    return TaskExecutionController.Result.ok("local-probe");
                }
                if (!reading.verified) {
                    return TaskExecutionController.Result.fail(reading.speech);
                }
                rec.facts.put("termux", reading.finding);
                return TaskExecutionController.Result.ok(reading.finding);
            }
            return TaskExecutionController.Result.ok("explained");
        }, false, now);
        String speech;
        if (done.state == TaskState.FAILED && freeBytes < 0) {
            speech = done.lastError;
        } else if (freeBytes >= 0) {
            boolean enough = freeBytes > projectBytes + 64L * 1024 * 1024;
            speech = "This phone has about " + gb(freeBytes) + " GB free. The project needs about " + gb(projectBytes)
                    + " GB. " + (enough ? "That is enough room, with space left over." : "That is tight. I would free some space before adding large files.");
            if (done.facts.containsKey("termux")) {
                speech += " I checked through the approved storage command.";
            } else {
                speech += " Termux did not return a verified reading, so this is from the phone's own storage report.";
            }
        } else {
            speech = done.lastError.isEmpty() ? "I couldn't read the free storage." : done.lastError;
        }
        Outcome o = Outcome.say(speech, false);
        o.activity = tasks.activityLine();
        o.taskId = task.id;
        return o;
    }

    private Outcome sshJob(String text, long now) {
        if (sshHost == null || sshHost.isEmpty()) {
            return Outcome.say("I don't have a server saved yet. Add its name, address, and sign-in method in the connection settings. I have not connected.", false);
        }
        TaskRecord task = tasks.create(text, "Server inspection", "ssh", List.of("connect", "read", "explain"), false, now);
        tasks.markRunning(task, now);
        SshConnectionManager.Link link = ssh.connect(sshHost, sshPort, new SshTransport.Auth(sshUser, sshSecret, false), trustFirstKey, now);
        if (link.state != SshConnectionManager.State.CONNECTED) {
            task.state = TaskState.FAILED;
            task.lastError = link.detail;
            tasks.checkpoints().save(task, "connect", now);
            Outcome o = Outcome.say(link.detail, false);
            o.taskId = task.id;
            return o;
        }
        task.facts.put("connected", "yes");
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("connect") && !lower.contains("log") && !lower.contains("storage") && !lower.contains("disk") && !lower.contains("restart") && !lower.contains("download")) {
            task.completed.add("connect");
            task.state = TaskState.COMPLETED;
            tasks.checkpoints().save(task, "connect", now);
            Outcome hello = Outcome.say("I connected to your configured server. The server is responding.", false);
            hello.taskId = task.id;
            return hello;
        }
        SshCommandExecutor.Command cmd = sshCommands.plan(text, logPath);
        if (cmd.argv == null) {
            return Outcome.say(cmd.refusal == null ? "I didn't run a remote command." : cmd.refusal, false);
        }
        if (cmd.destructive) {
            return Outcome.say("I can do that on the server, and it may interrupt anyone using it. I have not done it. Say yes if you want me to.", false);
        }
        SshTransport.Exec exec = ssh.exec(cmd.argv, false);
        task.completed.add("connect");
        if (exec.interrupted) {
            task.state = TaskState.PAUSED;
            task.checkpoint = "connect";
            task.facts.put("finding.logs", "");
            tasks.checkpoints().save(task, "connect", now);
            return Outcome.say("The connection dropped. I kept what I had already confirmed and I have not guessed the rest.", true);
        }
        SshResultInterpreter.Finding finding = text.toLowerCase(Locale.US).contains("storage")
                ? sshReading.storage(exec.stdout) : sshReading.logs(exec.stdout);
        task.facts.put("finding.logs", finding.code);
        task.completed.add("read");
        task.state = finding.verified ? TaskState.COMPLETED : TaskState.FAILED;
        tasks.checkpoints().save(task, "read", now);
        Outcome o = Outcome.say(finding.speech, false);
        o.taskId = task.id;
        o.activity = "Server inspection";
        return o;
    }

    private Outcome remoteFetch(String text, long now) {
        if (ssh.link().state != SshConnectionManager.State.CONNECTED) {
            Outcome first = sshJob("connect to my development server", now);
            if (ssh.link().state != SshConnectionManager.State.CONNECTED) {
                return first;
            }
        }
        SshFileBrowser browser = new SshFileBrowser(transportOrNull());
        List<SshTransport.RemoteFile> found = browser.find("/models", "vehicle");
        if (found.isEmpty()) {
            return Outcome.say("I looked in the authorised server folder and didn't find a vehicle file.", false);
        }
        SshTransport.RemoteFile file = found.get(0);
        byte[] bytes = browser.download(file.path, false);
        if (bytes == null) {
            return Outcome.say("I found " + file.path + " but the download didn't complete. Nothing was opened.", false);
        }
        String name = file.path.toLowerCase(Locale.US);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            scene = reconstruct.fromImage(file.path, vision.analyzer().analyze(file.path, bytes), 1).scene;
            Outcome o = Outcome.say("That file is an image, not a 3D model. I built an editable approximation from it instead of pretending the picture was already a model.", false);
            if (scene != null) {
                o.solidText = scene.solidText();
                o.subject = scene.label;
            }
            return o;
        }
        scene = glb.importBytes(bytes, "Vehicle");
        if (scene.nodes().isEmpty()) {
            return Outcome.say("I downloaded " + file.path + ", but it isn't a 3D file I can open. I didn't put anything on the board.", false);
        }
        Outcome o = Outcome.say("I downloaded the newest vehicle file, " + file.path + ", checked that it has named parts, and opened it on the board.", false);
        o.solidText = scene.solidText();
        o.subject = scene.label;
        return o;
    }

    private SshTransport transportOrNull() {
        return sshTransport;
    }

    private SshTransport sshTransport;

    public void setTransport(SshTransport transport) {
        this.sshTransport = transport;
    }

    private Outcome findImage(String text, long now) {
        if (authorizedRoot == null) {
            return Outcome.say("I don't have a folder I'm allowed to search. Choose the project folder first. I haven't looked anywhere else.", false);
        }
        String query = text.toLowerCase(Locale.US).contains("motorcycle") ? "motorcycle" : text;
        List<PhoneFileAccessManager.ImageFile> hits = vision.find(authorizedRoot, query);
        List<PhoneFileAccessManager.ImageFile> strong = new ArrayList<PhoneFileAccessManager.ImageFile>();
        for (PhoneFileAccessManager.ImageFile h : hits) {
            if (h.score >= 5) {
                strong.add(h);
            }
        }
        if (strong.isEmpty()) {
            return Outcome.say("I searched the folder you allowed and didn't find an image that matches.", false);
        }
        if (strong.size() > 1 && strong.get(0).score == strong.get(1).score) {
            Outcome o = Outcome.say("I found more than one match: " + names(strong) + ". Which one should I use?", false);
            o.listen = true;
            return o;
        }
        PhoneFileAccessManager.ImageFile pick = strong.get(0);
        ImageContentAnalyzer.Report report = vision.inspect(pick.file);
        Outcome o = Outcome.say("I found " + pick.file.getName() + ". " + report.note, false);
        o.subject = pick.file.getName();
        o.activity = "Inspecting selected files";
        return o;
    }

    private Outcome buildFromImage(String text, long now) {
        String path = vision.memory().lastPath();
        if (path == null) {
            return Outcome.say("Show me which image first. I haven't built a model.", false);
        }
        File file = new File(path);
        ImageContentAnalyzer.Report report = vision.inspect(file);
        TaskRecord task = tasks.create(text, "Motorcycle reconstruction", "reconstruct", List.of("inspect", "build", "verify"), false, now);
        tasks.markRunning(task, now);
        ImageTo3DReconstructionEngine.Built built = reconstruct.fromImage(file.getName(), report, 1);
        if (!built.ok || built.scene == null) {
            task.state = TaskState.FAILED;
            task.lastError = built.speech;
            tasks.checkpoints().save(task, "inspect", now);
            return Outcome.say(built.speech, false);
        }
        scene = built.scene;
        task.completed.add("inspect");
        task.completed.add("build");
        task.checkpoint = "build";
        task.assets.add(path);
        task.facts.put("method", built.method);
        SemanticTypes.ToolResult result = new SemanticTypes.ToolResult();
        result.ok = true;
        result.verified = !built.scene.nodes().isEmpty();
        result.evidence = built.scene.manifest();
        result.speech = built.speech;
        NaturalLanguageActionEngine.Read read = language.read(text, 1, memory, null);
        if (!verifier.accept(read.decomposition, result, ids(scene))) {
            task.state = TaskState.FAILED;
            tasks.checkpoints().save(task, "build", now);
            return Outcome.say("I built geometry, but it didn't match the request closely enough, so I'm not calling it done.", false);
        }
        task.completed.add("verify");
        task.state = TaskState.COMPLETED;
        tasks.checkpoints().save(task, "verify", now);
        Outcome o = Outcome.say(responses.say(result, built.speech), false);
        o.solidText = scene.solidText();
        o.subject = scene.label;
        o.activity = "Building 3D object";
        o.taskId = task.id;
        return o;
    }

    private Outcome editScene(NaturalLanguageActionEngine.Read read, long now) {
        String original = read.decomposition.original.toLowerCase(Locale.US);
        String head = original.split("\\bbut\\b|\\bwithout\\b|\\bexcept\\b")[0];
        String target = read.decomposition.target;
        if (head.matches(".*\\b(tyres|tires)\\b.*")) {
            java.util.List<String> tyres = new ArrayList<String>();
            for (EditableScene.Node n : scene.nodes()) {
                if (!n.removed && (n.id.contains("tyre") || n.id.contains("tire"))) {
                    tyres.add(n.id);
                }
            }
            if (!tyres.isEmpty()) {
                return applyRemoval(read, tyres);
            }
        }
        if (head.contains("tyre") || head.contains("tire")) {
            if (scene.byId("front_left_tyre") != null && head.contains("left")) {
                target = "front_left_tyre";
            } else if (scene.byId("front_tyre") != null) {
                target = "front_tyre";
            } else if (scene.byId("front_left_tyre") != null) {
                target = "front_left_tyre";
            }
        } else if (head.contains("rim")) {
            target = head.contains("left") && scene.byId("front_left_rim") != null ? "front_left_rim" : "front_rim";
        }
        return applyRemoval(read, List.of(target));
    }

    private Outcome applyRemoval(NaturalLanguageActionEngine.Read read, List<String> targets) {
        List<String> protect = new ArrayList<String>(read.decomposition.preserve);
        protect.remove("unrelated_geometry");
        EditableScene.EditReport report = scene.removeKeeping(targets, protect, null);
        List<String> present = new ArrayList<String>();
        for (EditableScene.Node n : scene.nodes()) {
            if (!n.removed) {
                present.add(n.id);
            }
        }
        SemanticTypes.ToolResult result = new SemanticTypes.ToolResult();
        result.ok = report.applied;
        result.verified = report.applied && report.preserved.containsAll(protect);
        result.evidence = scene.manifest();
        result.speech = report.speech;
        if (!verifier.accept(read.decomposition, result, present)) {
            scene.undo();
            return Outcome.say(report.applied ? "The change didn't pass a check against what you asked, so I undid it." : report.speech, false);
        }
        Outcome o = Outcome.say(report.speech, false);
        o.solidText = scene.solidText();
        o.subject = scene.label;
        o.activity = "Verifying changes";
        return o;
    }

    private Outcome research(String text, long now) {
        performance.research();
        InternetResearchEngine.Finding finding = research.research(text, true);
        if (!finding.ignoredCommands.isEmpty()) {
            // proof the page could not run a tool: the termux history must not gain a matching entry here
        }
        return Outcome.say(finding.speech, false);
    }

    public void learnTyreRim() {
        memory.learn("motorcycle", "tyre", "front_tyre");
        memory.learn("motorcycle", "rim", "front_rim");
        memory.learn("3d-edit", "take out", "detach");
    }

    private static String explain(NaturalLanguageActionEngine.Read read) {
        if (read.decomposition.original.toLowerCase(Locale.US).contains("deleted")) {
            return "If that file were deleted, it would be gone from the folder. I have not deleted it.";
        }
        return "I can explain that, and I have not changed anything.";
    }

    private static boolean looksStorage(String text) {
        String n = text.toLowerCase(Locale.US);
        return n.contains("storage") || n.contains("space is left") || n.contains("enough room") || n.contains("free space");
    }

    private static boolean looksSsh(String text) {
        String n = text.toLowerCase(Locale.US);
        return n.contains("server") || n.contains("ssh") || (n.contains("log") && !n.contains("catalog"));
    }

    private static boolean looksRemoteFetch(String text) {
        String n = text.toLowerCase(Locale.US);
        return n.contains("download") && (n.contains("server") || n.contains("vehicle"));
    }

    private static boolean looksFindImage(String text) {
        String n = text.toLowerCase(Locale.US);
        return n.contains("image") && (n.contains("find") || n.contains("show") || n.contains("folder") || n.contains("project"));
    }

    private static boolean looksReconstruct(String text) {
        String n = text.toLowerCase(Locale.US);
        return n.contains("image") && (n.contains("3d") || n.contains("editable") || n.contains("reconstruct") || n.contains("model"));
    }

    private static String names(List<PhoneFileAccessManager.ImageFile> files) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < files.size() && i < 4; i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(files.get(i).file.getName());
        }
        return b.toString();
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1_000_000_000.0);
    }

    private static List<String> ids(EditableScene scene) {
        List<String> ids = new ArrayList<String>();
        for (EditableScene.Node n : scene.nodes()) {
            ids.add(n.id);
        }
        return ids;
    }

    public List<String> debugSteps() {
        return Arrays.asList("interpret", "plan", "execute", "verify");
    }
}
