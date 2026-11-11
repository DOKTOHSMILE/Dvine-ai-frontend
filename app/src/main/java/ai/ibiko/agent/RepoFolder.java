package ai.ibiko.agent;

/**
 * A cloned repository lives in one folder: {@code repos/<name>} in the chat's own folder (on the phone's Linux,
 * {@code /workspace/repos/<name>}), apart from the chat's memories ({@code memory/}). {@code projects/<name>} and the
 * old {@code memory/projects/<name>} are not folders: file tools, shell commands and plan steps that still say them
 * are pointed at the real folder.
 */
public final class RepoFolder {
    public static final String RELATIVE = "repos";

    private RepoFolder() {
    }

    /**
     * {@code projects/<name>} and {@code memory/projects/<name>} become {@code repos/<name>}. Anything else is left as
     * it is.
     */
    public static String place(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.replaceAll("(?<![\\w.-])(memory/)?projects/", "repos/");
    }
}
