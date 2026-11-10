package ai.dvine.linux;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The command that starts Alpine inside the app. {@code proot} is the only program the phone runs
 * directly. Everything after it (the shell, {@code apk}, python, git) runs inside the root.
 */
public final class ProotCommand {
    /** The same jobs as Termux {@code pkg}, carried out by Alpine's {@code apk}. */
    public static final String PKG = "#!/bin/sh\n"
            + "# DVINE package command. Same shape as Termux pkg, on Alpine apk.\n"
            + "cmd=$1\n"
            + "if [ $# -gt 0 ]; then shift; fi\n"
            + "case \"$cmd\" in\n"
            + "  \"\"|help|-h|--help)\n"
            + "    echo \"pkg update\"\n"
            + "    echo \"pkg upgrade\"\n"
            + "    echo \"pkg install <name>...\"\n"
            + "    echo \"pkg uninstall <name>...\"\n"
            + "    echo \"pkg search <query>\"\n"
            + "    echo \"pkg list\"\n"
            + "    ;;\n"
            + "  update) apk update ;;\n"
            + "  upgrade) apk update && apk upgrade ;;\n"
            + "  install|add) apk add \"$@\" ;;\n"
            + "  uninstall|remove|del) apk del \"$@\" ;;\n"
            + "  search) apk search \"$@\" ;;\n"
            + "  list) apk info ;;\n"
            + "  *) echo \"pkg: unknown command $cmd\"; exit 1 ;;\n"
            + "esac\n";

    private ProotCommand() {
    }

    public static List<String> line(File proot, File rootfs) {
        return line(proot, rootfs, null);
    }

    /** The long-lived shell. {@code work} is mounted at {@code /work} when it is given. */
    public static List<String> line(File proot, File rootfs, File work) {
        List<String> c = new ArrayList<String>();
        c.add(proot.getAbsolutePath());
        c.add("--link2symlink");
        c.add("-0");
        c.add("-r");
        c.add(rootfs.getAbsolutePath());
        c.add("-b");
        c.add("/dev");
        c.add("-b");
        c.add("/proc");
        c.add("-b");
        c.add("/sys");
        if (work != null) {
            c.add("-b");
            c.add(work.getAbsolutePath() + ":/work");
        }
        if (new File("/storage").isDirectory()) {
            c.add("-b");
            c.add("/storage");
        }
        if (new File("/sdcard").isDirectory()) {
            c.add("-b");
            c.add("/sdcard");
        }
        c.add("-w");
        c.add("/root");
        c.add("/bin/sh");
        return c;
    }

    /**
     * The shell on a terminal device. A new session opens {@code slave} itself, which makes it that session's
     * terminal: CTRL-C and CTRL-Z reach the running program, and full-screen programs can draw.
     */
    public static List<String> terminal(File proot, File rootfs, File work, String slave) {
        List<String> c = line(proot, rootfs, work);
        c.add("-c");
        c.add("exec setsid /bin/sh -c 'exec /bin/sh -i <>" + slave + " >&0 2>&1'");
        return c;
    }

    /** One command, with the phone folder mounted at {@code /work} so the shell can see those files. */
    public static List<String> once(File proot, File rootfs, File work, String command) {
        List<String> c = new ArrayList<String>();
        c.add(proot.getAbsolutePath());
        c.add("--link2symlink");
        c.add("-0");
        c.add("-r");
        c.add(rootfs.getAbsolutePath());
        c.add("-b");
        c.add("/dev");
        c.add("-b");
        c.add("/proc");
        c.add("-b");
        c.add("/sys");
        if (work != null) {
            c.add("-b");
            c.add(work.getAbsolutePath() + ":/work");
            c.add("-w");
            c.add("/work");
        } else {
            c.add("-w");
            c.add("/root");
        }
        c.add("/bin/sh");
        c.add("-c");
        c.add(command == null ? "" : command);
        return c;
    }
}
