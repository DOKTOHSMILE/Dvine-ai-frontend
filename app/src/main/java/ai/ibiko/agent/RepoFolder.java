package ai.ibiko.agent;

/**
 * A cloned repository lives in one folder: {@code memory/projects/<name>} (on the phone's Linux,
 * {@code /workspace/memory/projects/<name>}). {@code projects/<name>} is not a folder. File tools, shell
 * commands and plan steps are pointed at the real folder, so a plan that still says {@code projects/<name>}
 * reads the clone instead of being told the path does not exist.
 */
public final class RepoFolder {
    public static final String RELATIVE = "memory/projects";

    private RepoFolder() {
    }

    /**
     * {@code projects/<name>} becomes {@code memory/projects/<name>}. A path that already says
     * {@code memory/projects} is left as it is. Anything else is left as it is.
     */
    public static String place(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.replaceAll("(?<!memory/)(?<![\\w.-])projects/", "memory/projects/");
    }
}
