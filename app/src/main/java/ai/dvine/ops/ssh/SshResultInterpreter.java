package ai.dvine.ops.ssh;

/** Distinguishes what the output shows from what it does not prove. */
public final class SshResultInterpreter {
    public static final class Finding {
        public final String speech;
        public final String code;
        public final boolean verified;
        public final String limitation;

        public Finding(String speech, String code, boolean verified, String limitation) {
            this.speech = speech;
            this.code = code;
            this.verified = verified;
            this.limitation = limitation == null ? "" : limitation;
        }
    }

    public Finding logs(String text) {
        String low = text == null ? "" : text.toLowerCase();
        if (low.contains("connection timed out") || low.contains("timeout")) {
            boolean db = low.contains("database") || low.contains("jdbc") || low.contains("sql");
            if (db) {
                return new Finding(
                        "The latest logs show the application failed because its database connection timed out. The logs confirm the timeout. I have not checked whether the database itself is offline.",
                        "database_connection_timeout", true, "database process not inspected");
            }
            return new Finding("The latest logs show a timeout. I have not established the cause beyond that.", "timeout", true, "");
        }
        if (text == null || text.isBlank()) {
            return new Finding("I reached the server, but the log was empty. I can't say why the service failed from that.", "empty_log", true, "");
        }
        return new Finding("I read the latest log. It does not name a single failure I can confirm.", "unspecified", true, "");
    }

    public Finding storage(String text) {
        if (text == null || text.isBlank()) {
            return new Finding("I connected, but the server didn't report its free storage.", "no_storage", false, "");
        }
        return new Finding("The server reports free storage of " + text.trim() + ".", "storage", true, "");
    }
}
