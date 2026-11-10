package ai.ibiko.linux;

import java.io.File;

/** DVINE's private workspace. It is created on the device and works with no network. */
public final class WorkspaceManager {
    private static final String[] DIRS = {
            "system/bin", "system/lib", "system/config", "system/runtime",
            "workspace/projects", "workspace/scripts", "workspace/downloads", "workspace/temporary",
            "models", "memory", "agents", "logs", "cache", "backups"
    };

    private WorkspaceManager() {
    }

    /** Makes the workspace folders under {@code root} and returns that folder. */
    public static File ensure(File root) {
        if (root != null) {
            for (String d : DIRS) {
                new File(root, d).mkdirs();
            }
        }
        return root;
    }
}
