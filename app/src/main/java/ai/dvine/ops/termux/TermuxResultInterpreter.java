package ai.dvine.ops.termux;

/** Turns an exit status into plain language. A launched command is not a success. */
public final class TermuxResultInterpreter {
    public static final class Reading {
        public final boolean verified;
        public final String speech;
        public final String finding;

        public Reading(boolean verified, String speech, String finding) {
            this.verified = verified;
            this.speech = speech;
            this.finding = finding;
        }
    }

    public Reading read(String purpose, int exit, String stdout, String stderr, boolean timedOut, boolean cancelled) {
        if (cancelled) {
            return new Reading(false, "I stopped that command before it finished.", "cancelled");
        }
        if (timedOut) {
            return new Reading(false, "The command took too long and I stopped waiting. I don't know whether it finished.", "timeout");
        }
        String out = stdout == null ? "" : stdout.trim();
        String err = stderr == null ? "" : stderr.trim();
        if (exit != 0) {
            String why = !err.isEmpty() ? err : out;
            return new Reading(true, explainFailure(purpose, why), "exit " + exit);
        }
        return new Reading(true, explainOk(purpose, out), out.isEmpty() ? "ok" : clip(out));
    }

    private static String explainFailure(String purpose, String why) {
        String low = (why == null ? "" : why).toLowerCase();
        if (low.contains("address already in use") || low.contains("port") && low.contains("bind")) {
            return "The process could not start because another process is already using its network port. I have not changed anything.";
        }
        if (why == null || why.isEmpty()) {
            return "The " + purpose + " failed. The command exited with an error, and it didn't say why.";
        }
        return "The " + purpose + " failed. " + clip(why);
    }

    private static String explainOk(String purpose, String out) {
        if (out.isEmpty()) {
            return "The " + purpose + " finished without any output.";
        }
        return "The " + purpose + " finished. " + clip(out);
    }

    static String clip(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 280 ? t.substring(0, 277) + "..." : t;
    }
}
