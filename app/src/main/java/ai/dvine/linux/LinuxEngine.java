package ai.dvine.linux;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ai.dvine.agent.Zips;

/**
 * DVINE's own Linux. One function so far: unzip. It runs inside the app, with no Termux and no system shell.
 * A command this engine does not know returns null, and the caller may use another runner.
 */
public final class LinuxEngine {
    private LinuxEngine() {
    }

    /** What the one function did. */
    public static final class Result {
        public final boolean ok;
        public final String text;

        Result(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }
    }

    /** True when {@code command} is the one function this engine runs itself. */
    public static boolean knows(String command) {
        return argv(command) != null;
    }

    /** Runs unzip, or null when the command is not that function. The zip is taken from {@code work}, then from {@code roots}. */
    public static Result run(String command, File work, List<File> roots) {
        String[] args = argv(command);
        if (args == null) {
            return null;
        }
        if (work == null) {
            return new Result(false, "unzip has no folder to work in.\nThis ran in DVINE's own Linux. Termux was not opened.");
        }
        work.mkdirs();
        File zip = resolve(args[0], work, roots);
        if (zip == null || !zip.isFile()) {
            return new Result(false, "unzip " + args[0] + "\nNo such zip in this app's folders."
                    + "\nThis ran in DVINE's own Linux. Termux was not opened.");
        }
        String destName = args[1].isEmpty() ? args[0].replaceAll("(?i)\\.zip$", "") : args[1];
        File into = new File(work, destName);
        try {
            if (!inside(work, into)) {
                return new Result(false, "unzip refused to write outside its folder.\nThis ran in DVINE's own Linux. Termux was not opened.");
            }
            int n = Zips.unzip(zip, into);
            return new Result(true, "unzip " + zip.getName() + "\nUnzipped " + n + (n == 1 ? " file" : " files")
                    + " into " + destName + ".\nThis ran in DVINE's own Linux. Termux was not opened.");
        } catch (IOException e) {
            return new Result(false, "unzip " + zip.getName() + " failed: " + e.getMessage()
                    + "\nThis ran in DVINE's own Linux. Termux was not opened.");
        }
    }

    /** {@code unzip [-n|-o|-q] FILE [-d DIR]}, or null. A shell character, a flag this engine does not know, or {@code ..} is refused. */
    static String[] argv(String command) {
        if (command == null) {
            return null;
        }
        String c = command.trim();
        if (c.isEmpty() || c.indexOf('\n') >= 0 || c.matches(".*[|&;`$<>\\\\\"'].*")) {
            return null;
        }
        String[] parts = c.split("\\s+");
        if (parts.length < 2 || !"unzip".equals(parts[0])) {
            return null;
        }
        String file = null;
        String dest = "";
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if ("-n".equals(p) || "-o".equals(p) || "-q".equals(p)) {
                continue;
            }
            if ("-d".equals(p)) {
                if (i + 1 >= parts.length || bad(parts[i + 1])) {
                    return null;
                }
                dest = parts[++i];
                continue;
            }
            if (p.startsWith("-") || file != null || bad(p)) {
                return null;
            }
            file = p;
        }
        return file == null ? null : new String[]{file, dest};
    }

    private static boolean bad(String path) {
        return path.contains("..") || path.startsWith("/") || path.indexOf('\\') >= 0;
    }

    private static File resolve(String name, File work, List<File> roots) {
        File direct = new File(work, name);
        if (direct.isFile() && inside(work, direct)) {
            return direct;
        }
        String base = new File(name).getName().toLowerCase(Locale.US);
        List<File> found = new ArrayList<File>();
        List<File> look = new ArrayList<File>();
        look.add(work);
        if (roots != null) {
            look.addAll(roots);
        }
        for (File root : look) {
            walk(root, 0, base, found);
        }
        Collections.sort(found, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return found.isEmpty() ? null : found.get(0);
    }

    private static void walk(File dir, int depth, String base, List<File> found) {
        if (dir == null || !dir.isDirectory() || depth > 4 || found.size() > 20) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (f.isDirectory()) {
                walk(f, depth + 1, base, found);
            } else if (f.getName().toLowerCase(Locale.US).equals(base)) {
                found.add(f);
            }
        }
    }

    private static boolean inside(File root, File file) {
        try {
            String r = root.getCanonicalPath() + File.separator;
            String p = file.getCanonicalPath();
            return p.startsWith(r) || p.equals(root.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }
}
