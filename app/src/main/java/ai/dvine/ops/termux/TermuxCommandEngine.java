package ai.dvine.ops.termux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Natural-language terminal requests become an allow-listed argument vector, or a refusal.
 * Nothing here is passed to a shell as a single string.
 */
public final class TermuxCommandEngine {
    public static final class Planned {
        public final List<String> argv;
        public final TermuxPermissionManager.Category category;
        public final String purpose;
        public final String refusal;

        private Planned(List<String> argv, TermuxPermissionManager.Category category, String purpose, String refusal) {
            this.argv = argv;
            this.category = category;
            this.purpose = purpose;
            this.refusal = refusal;
        }
    }

    private final TermuxCapabilityDetector detector;
    private final TermuxExecutionService service;
    private final TermuxResultInterpreter interpreter = new TermuxResultInterpreter();
    private final TermuxPermissionManager permissions = new TermuxPermissionManager();
    private final TermuxTaskHistoryRepository history = new TermuxTaskHistoryRepository();

    public TermuxCommandEngine(TermuxCapabilityDetector detector, TermuxExecutionService service) {
        this.detector = detector;
        this.service = service;
    }

    public Planned plan(String utterance) {
        String n = utterance == null ? "" : utterance.toLowerCase(Locale.US);
        if (n.matches(".*(delete|rm |remove the|wipe|format|shutdown|reboot|passwd|chmod 777|firewall).*")) {
            return new Planned(null, TermuxPermissionManager.Category.C_HIGH, "high-impact",
                    "I won't run a destructive terminal command from that wording.");
        }
        if (n.contains("storage") || n.contains("disk space") || n.contains("space is left") || n.contains("room for")) {
            return new Planned(Arrays.asList("df", "-k", "."), TermuxPermissionManager.Category.A_READ, "storage check", null);
        }
        if (n.contains("memory") || n.contains("which process")) {
            return new Planned(Arrays.asList("ps", "-o", "pid,rss,comm"), TermuxPermissionManager.Category.A_READ, "memory check", null);
        }
        if (n.contains("compilation") || n.contains("compile error") || n.contains("build error")) {
            return new Planned(Arrays.asList("echo", "build-log"), TermuxPermissionManager.Category.A_READ, "build inspection", null);
        }
        if (n.contains("why") && (n.contains("server") || n.contains("last operation") || n.contains("failed"))) {
            return new Planned(Arrays.asList("echo", "last-status"), TermuxPermissionManager.Category.A_READ, "failure check", null);
        }
        if (n.contains("ssh") && (n.contains("available") || n.contains("configured"))) {
            return new Planned(Arrays.asList("echo", "ssh-config-present"), TermuxPermissionManager.Category.A_READ, "ssh check", null);
        }
        if (n.contains("list") && n.contains("image")) {
            return new Planned(Arrays.asList("ls", "-1"), TermuxPermissionManager.Category.A_READ, "image listing", null);
        }
        return new Planned(null, null, "unlisted", "I don't have an approved terminal command for that, so I didn't run anything.");
    }

    public TermuxResultInterpreter.Reading execute(Planned planned, boolean confirmed) {
        if (planned == null || planned.argv == null) {
            String why = planned == null ? "I didn't understand a terminal command there." : planned.refusal;
            history.add(new TermuxTaskHistoryRepository.Entry(planned == null ? "" : planned.purpose, 1, false, why));
            return new TermuxResultInterpreter.Reading(false, why, "refused");
        }
        String missing = detector == null ? "Termux is not connected." : detector.setupAdvice();
        if (missing != null) {
            history.add(new TermuxTaskHistoryRepository.Entry(planned.purpose, 127, false, missing));
            return new TermuxResultInterpreter.Reading(false, missing, "unavailable");
        }
        TermuxPermissionManager.Gate gate = permissions.gate(planned.category, confirmed);
        if (!gate.allowed) {
            history.add(new TermuxTaskHistoryRepository.Entry(planned.purpose, 1, false, gate.reason));
            return new TermuxResultInterpreter.Reading(false, gate.reason, "needs confirmation");
        }
        TermuxExecutionService.Result ran = service.execute(new ArrayList<String>(planned.argv), 4000);
        TermuxResultInterpreter.Reading reading = interpreter.read(planned.purpose, ran.exit, ran.stdout, ran.stderr, ran.timedOut, ran.cancelled);
        history.add(new TermuxTaskHistoryRepository.Entry(planned.purpose, ran.exit, reading.verified, reading.speech));
        return reading;
    }

    public TermuxTaskHistoryRepository history() {
        return history;
    }
}
