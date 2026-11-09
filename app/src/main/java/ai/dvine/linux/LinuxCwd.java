package ai.dvine.linux;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The folder the Linux shell was left in, shared by the terminal and the chat. */
public final class LinuxCwd {
    private LinuxCwd() {
    }

    public static String read(File rootfs) {
        if (rootfs == null) {
            return "/root";
        }
        try {
            File file = new File(rootfs, "root/.dvine_cwd");
            if (!file.isFile()) {
                return "/root";
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            return text.startsWith("/") ? text : "/root";
        } catch (Exception e) {
            return "/root";
        }
    }

    /** A command that starts in the folder the last command finished in, and records the new one. */
    public static String script(File rootfs, String command) {
        return Guest.wrap(read(rootfs), command);
    }
}
