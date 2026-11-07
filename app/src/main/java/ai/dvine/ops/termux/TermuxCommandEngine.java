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
    private TermuxSessionLog sessions;

    public TermuxCommandEngine(TermuxCapabilityDetector detector, TermuxExecutionService service) {
        this.detector = detector;
        this.service = service;
    }

    /** Where a command's state is kept across a process death. Null keeps the old in-memory history only. */
    public void sessions(java.io.File dir) {
        this.sessions = dir == null ? null : new TermuxSessionLog(dir);
    }

    public TermuxSessionLog sessions() {
        return sessions;
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
        if (n.contains("git status") || n.contains("what changed in git")) {
            return planned(Arrays.asList("git", "status", "--short"), TermuxPermissionManager.Category.A_READ, "git status");
        }
        if (n.contains("which packages") || n.contains("installed packages") || (n.contains("check") && n.contains("dependencies"))) {
            return planned(Arrays.asList("pkg", "list-installed"), TermuxPermissionManager.Category.A_READ, "installed packages");
        }
        if (n.contains("git push") || n.contains("push the ") || n.contains("push my ")) {
            return refused("push", "I don't push from a guessed command. Use the GitHub tool, which checks the diff first.");
        }
        if (n.contains("install python")) {
            return planned(Arrays.asList("pkg", "install", "-y", "python"), TermuxPermissionManager.Category.B_REVERSIBLE, "python install");
        }
        if (n.contains("install node")) {
            return planned(Arrays.asList("pkg", "install", "-y", "nodejs"), TermuxPermissionManager.Category.B_REVERSIBLE, "node install");
        }
        if ((n.contains("install") && (n.contains("package") || n.contains("dependencies") || n.contains("dependency")))) {
            return refused("package install", "Tell me the package name. I didn't install anything.");
        }
        if (n.contains("run the test") || n.contains("test suite")) {
            if (n.contains("gradle")) {
                return planned(Arrays.asList("./gradlew", "test"), TermuxPermissionManager.Category.B_REVERSIBLE, "gradle tests");
            }
            if (n.contains("python")) {
                return planned(Arrays.asList("python", "-m", "unittest"), TermuxPermissionManager.Category.B_REVERSIBLE, "python tests");
            }
            return refused("tests", "Tell me whether to run Gradle or Python tests. I didn't start a suite.");
        }
        if ((n.contains("compile") || n.contains("build the app") || n.contains("build the application")) && !n.contains("error")) {
            if (n.contains("gradle") || n.contains("android")) {
                return planned(Arrays.asList("./gradlew", "assembleDebug"), TermuxPermissionManager.Category.B_REVERSIBLE, "android compile");
            }
            return refused("compile", "Tell me the build tool. I didn't compile anything.");
        }
        String zip = zipName(utterance);
        if (n.contains("unzip") || n.contains("extract")) {
            if (zip == null) {
                return refused("unzip", "Tell me the zip file's name. I didn't extract anything.");
            }
            return planned(Arrays.asList("unzip", "-n", zip), TermuxPermissionManager.Category.B_REVERSIBLE, "unzip");
        }
        String url = httpsUrl(utterance);
        if (n.contains("clone")) {
            if (url == null) {
                return refused("clone", "I need one https address to clone. I didn't run anything.");
            }
            return planned(Arrays.asList("git", "clone", "--depth", "1", url), TermuxPermissionManager.Category.B_REVERSIBLE, "clone");
        }
        return new Planned(null, null, "unlisted", "I don't have an approved terminal command for that, so I didn't run anything.");
    }

    private static Planned planned(List<String> argv, TermuxPermissionManager.Category category, String purpose) {
        return new Planned(argv, category, purpose, null);
    }

    private static Planned refused(String purpose, String why) {
        return new Planned(null, null, purpose, why);
    }

    /** A single relative zip name. Anything with a space, a quote, or `..` is rejected. */
    static String zipName(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\b([A-Za-z0-9._/-]+\\.zip)\\b").matcher(text);
        if (!m.find()) {
            return null;
        }
        String name = m.group(1);
        int token = m.start(1);
        while (token > 0 && !Character.isWhitespace(text.charAt(token - 1))) {
            token--;
        }
        String whole = text.substring(token, m.end(1));
        if (whole.contains("..") || whole.startsWith("/") || name.contains("..") || name.startsWith("/") || name.indexOf('\\') >= 0) {
            return null;
        }
        return name;
    }

    /** One https URL, with no user or password in it. */
    static String httpsUrl(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%\\-]+").matcher(text);
        if (!m.find()) {
            return null;
        }
        String url = m.group();
        if (url.contains("@") || url.contains("..") || url.indexOf(' ') >= 0) {
            return null;
        }
        return url;
    }

    public TermuxResultInterpreter.Reading execute(Planned planned, boolean confirmed) {
        return execute(planned, confirmed, 4_000);
    }

    public TermuxResultInterpreter.Reading execute(Planned planned, boolean confirmed, long timeoutMs) {
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
        String id = sessions == null ? null : sessions.open(planned.purpose, join(planned.argv));
        TermuxExecutionService.Result ran;
        try {
            ran = service.execute(new ArrayList<String>(planned.argv), Math.max(1_000L, timeoutMs));
        } catch (RuntimeException e) {
            if (sessions != null) {
                sessions.close(id, "DISCONNECTED", -1);
            }
            history.add(new TermuxTaskHistoryRepository.Entry(planned.purpose, -1, false, "disconnected"));
            return new TermuxResultInterpreter.Reading(false,
                    "Termux disconnected before the command finished. It is not verified, and it was not resumed mid-way.",
                    "disconnected");
        }
        if (sessions != null) {
            String state = ran.timedOut ? "TIMED_OUT" : ran.cancelled ? "STOPPED" : "FINISHED";
            sessions.close(id, state, ran.exit);
        }
        TermuxResultInterpreter.Reading reading = interpreter.read(planned.purpose, ran.exit, ran.stdout, ran.stderr, ran.timedOut, ran.cancelled);
        history.add(new TermuxTaskHistoryRepository.Entry(planned.purpose, ran.exit, reading.verified, reading.speech));
        return reading;
    }

    private static String join(List<String> argv) {
        StringBuilder b = new StringBuilder();
        for (String a : argv) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(a);
        }
        return b.toString();
    }

    public TermuxTaskHistoryRepository history() {
        return history;
    }
}
