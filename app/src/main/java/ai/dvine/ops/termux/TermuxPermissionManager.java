package ai.dvine.ops.termux;

/** Category A may run, B needs a rule or a confirm, C always needs a described confirm. */
public final class TermuxPermissionManager {
    public enum Category { A_READ, B_REVERSIBLE, C_HIGH }

    public static final class Gate {
        public final boolean allowed;
        public final boolean needsConfirm;
        public final String reason;

        public Gate(boolean allowed, boolean needsConfirm, String reason) {
            this.allowed = allowed;
            this.needsConfirm = needsConfirm;
            this.reason = reason == null ? "" : reason;
        }
    }

    public Gate gate(Category category, boolean userConfirmed) {
        if (category == null) {
            return new Gate(false, false, "That command is not on the approved list.");
        }
        switch (category) {
            case A_READ:
                return new Gate(true, false, "");
            case B_REVERSIBLE:
                return userConfirmed ? new Gate(true, false, "") : new Gate(false, true, "That would change a file or a running process. Should I go ahead?");
            case C_HIGH:
                return userConfirmed ? new Gate(true, false, "") : new Gate(false, true, "That is a high-impact change. I need you to confirm the exact target before I do it.");
            default:
                return new Gate(false, false, "That command is not on the approved list.");
        }
    }
}
