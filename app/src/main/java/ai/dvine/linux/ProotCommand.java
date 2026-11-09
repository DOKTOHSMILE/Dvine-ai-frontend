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
        c.add("-w");
        c.add("/root");
        c.add("/bin/sh");
        return c;
    }
}
