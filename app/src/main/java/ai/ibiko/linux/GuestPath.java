package ai.ibiko.linux;

import java.io.File;

/**
 * Where a folder on the phone appears inside Linux. The coding workspace ({@code files/ibiko}, where cloned
 * projects such as {@code Dvine-ai-frontend} live) is {@code /workspace}. The shell's own files are {@code /work}.
 */
public final class GuestPath {
    private GuestPath() {
    }

    public static String of(File dir) {
        if (dir == null) {
            return "/workspace";
        }
        String path = dir.getAbsolutePath().replace('\\', '/');
        String workspace = map(path, "/ibiko", "/workspace");
        if (workspace != null) {
            return workspace;
        }
        String work = map(path, "/linux", "/work");
        return work == null ? "/workspace" : work;
    }

    /** A command that starts in {@code dir}, so a relative path is the same one the file tools use. */
    public static String at(File dir, String command) {
        return "cd " + Guest.quote(of(dir)) + "\n" + (command == null ? "" : command);
    }

    private static String map(String path, String hostTail, String guestRoot) {
        int at = path.indexOf(hostTail);
        if (at < 0) {
            return null;
        }
        int end = at + hostTail.length();
        if (end != path.length() && path.charAt(end) != '/') {
            return null;
        }
        String rest = path.substring(end);
        if (rest.isEmpty() || "/".equals(rest)) {
            return guestRoot;
        }
        return guestRoot + rest;
    }
}
