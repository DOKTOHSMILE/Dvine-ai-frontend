package ai.dvine.linux;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.dvine.agent.Zips;

/**
 * One shell inside DVINE. The commands run in Java, in the app's own folder. There is no Termux and no system shell.
 * A program that is not built in (python, git, a compiler) comes back as exit 127. It is not pretended to run.
 */
public final class ShellSession {
    static final int MOST_OUT = 64 * 1024;
    /** A command longer than this is refused. It is never split into tokens. */
    static final int MOST_LINE = 64 * 1024;
    /** Enough for a real command. Past this, stop instead of growing without limit. */
    static final int MOST_TOKENS = 4096;
    private static final String[] NAMES = {
            "cd", "pwd", "ls", "mkdir", "rmdir", "touch", "cat", "cp", "mv", "rm", "find", "grep",
            "echo", "printf", "head", "tail", "sort", "wc", "chmod", "date", "env", "which", "clear", "help", "unzip"
    };

    private final File root;
    private File cwd;
    private final Map<String, String> env = new LinkedHashMap<String, String>();
    private final List<String> history = new ArrayList<String>();
    private volatile boolean cancelled;
    private List<File> extraRoots = Collections.emptyList();

    public ShellSession(File root) {
        this.root = root;
        this.cwd = root;
        if (root != null) {
            env.put("HOME", root.getAbsolutePath());
            env.put("PWD", root.getAbsolutePath());
            env.put("TMPDIR", new File(root, "workspace/temporary").getAbsolutePath());
        }
    }

    public String where() {
        return cwd == null ? "" : cwd.getPath();
    }

    public void cancel() {
        cancelled = true;
    }

    public List<String> history() {
        return new ArrayList<String>(history);
    }

    public CommandResult exec(String line, List<File> roots) {
        long start = System.currentTimeMillis();
        extraRoots = roots == null ? Collections.<File>emptyList() : roots;
        if (line == null || line.trim().isEmpty()) {
            return done(line, 0, "", "", start);
        }
        if (line.length() > MOST_LINE) {
            return done(line, 1, "", "That command is too long to run (" + line.length() + " characters).", start);
        }
        history.add(line);
        if (root == null) {
            return done(line, 1, "", "The shell has no folder.", start);
        }
        root.mkdirs();
        if (line.indexOf('`') >= 0 || line.contains("$(")) {
            return done(line, 1, "", "Command substitution is not run.", start);
        }
        List<String> tokens;
        try {
            tokens = expand(tokenize(line));
        } catch (IllegalArgumentException e) {
            return done(line, 1, "", e.getMessage(), start);
        }
        List<String> out = new ArrayList<String>();
        List<String> err = new ArrayList<String>();
        int exit = 0;
        List<String> stmt = new ArrayList<String>();
        String join = ";";
        for (int i = 0; i <= tokens.size(); i++) {
            String t = i < tokens.size() ? tokens.get(i) : ";";
            if ("&&".equals(t) || "||".equals(t) || ";".equals(t)) {
                if (cancelled) {
                    return done(line, 130, join(out), "cancelled", start);
                }
                boolean run = ";".equals(join) || ("&&".equals(join) && exit == 0) || ("||".equals(join) && exit != 0);
                if (run && !stmt.isEmpty()) {
                    CommandResult one = pipeline(stmt);
                    exit = one.exit;
                    if (!one.stdout.isEmpty()) {
                        out.add(one.stdout);
                    }
                    if (!one.stderr.isEmpty()) {
                        err.add(one.stderr);
                    }
                }
                stmt = new ArrayList<String>();
                join = t;
            } else {
                stmt.add(t);
            }
        }
        return done(line, exit, join(out), join(err), start);
    }

    /** True when every command in the line is one this shell runs, and nothing in it leaves the folder. */
    public static boolean supported(String line) {
        if (line == null || line.trim().isEmpty() || line.length() > MOST_LINE || line.indexOf('`') >= 0 || line.contains("$(")) {
            return false;
        }
        List<String> tokens;
        try {
            tokens = tokenize(line);
        } catch (IllegalArgumentException e) {
            return false;
        }
        boolean name = true;
        for (String t : tokens) {
            if ("&&".equals(t) || "||".equals(t) || ";".equals(t) || "|".equals(t)) {
                name = true;
                continue;
            }
            if (">".equals(t) || ">>".equals(t) || "<".equals(t)) {
                name = false;
                continue;
            }
            if (name) {
                if (!known(t) || t.startsWith("-")) {
                    return false;
                }
                name = false;
            } else if (t.contains("..") || "-r".equals(t) || "-rf".equals(t) || "-fr".equals(t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean known(String name) {
        for (String n : NAMES) {
            if (n.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private CommandResult pipeline(List<String> tokens) {
        List<List<String>> cmds = new ArrayList<List<String>>();
        List<String> cur = new ArrayList<String>();
        for (String t : tokens) {
            if ("|".equals(t)) {
                cmds.add(cur);
                cur = new ArrayList<String>();
            } else {
                cur.add(t);
            }
        }
        cmds.add(cur);
        String stdin = "";
        CommandResult last = new CommandResult("", 0, "", "", 0);
        for (List<String> cmd : cmds) {
            if (cmd.isEmpty()) {
                return new CommandResult("", 1, "", "Empty command.", 0);
            }
            String inFile = null, outFile = null;
            boolean append = false;
            List<String> args = new ArrayList<String>();
            for (int i = 0; i < cmd.size(); i++) {
                String t = cmd.get(i);
                if (("<".equals(t) || ">".equals(t) || ">>".equals(t)) && i + 1 < cmd.size()) {
                    String file = cmd.get(++i);
                    if ("<".equals(t)) {
                        inFile = file;
                    } else {
                        outFile = file;
                        append = ">>".equals(t);
                    }
                } else {
                    args.add(t);
                }
            }
            if (args.isEmpty()) {
                return new CommandResult("", 1, "", "Empty command.", 0);
            }
            if (inFile != null) {
                File f = resolve(inFile, false);
                if (f == null || !f.isFile()) {
                    return new CommandResult(args.get(0), 1, "", "No such file: " + inFile, 0);
                }
                stdin = read(f, MOST_OUT);
            }
            CommandResult ran = builtin(args, stdin);
            if (outFile != null && ran.exit == 0) {
                File f = resolve(outFile, true);
                if (f == null) {
                    return new CommandResult(args.get(0), 1, "", "Refused to write " + outFile, 0);
                }
                try {
                    write(f, ran.stdout, append);
                } catch (IOException e) {
                    return new CommandResult(args.get(0), 1, "", e.getMessage(), 0);
                }
                stdin = "";
                last = new CommandResult(args.get(0), 0, "", ran.stderr, 0);
            } else {
                stdin = ran.stdout;
                last = ran;
            }
        }
        return last;
    }

    private CommandResult builtin(List<String> args, String stdin) {
        String name = args.get(0);
        try {
            switch (name) {
                case "pwd":
                    return ok(name, cwd.getPath() + "\n");
                case "cd":
                    return cd(args);
                case "ls":
                    return ls(args);
                case "mkdir":
                    return make(args, true);
                case "touch":
                    return make(args, false);
                case "rmdir":
                    return rmdir(args);
                case "rm":
                    return rm(args);
                case "cp":
                case "mv":
                    return copy(args, "mv".equals(name));
                case "cat":
                    return cat(args, stdin);
                case "echo":
                    return ok(name, joinArgs(args, 1) + "\n");
                case "printf":
                    return printf(args);
                case "head":
                case "tail":
                    return ends(args, stdin, "tail".equals(name));
                case "grep":
                    return grep(args, stdin);
                case "sort":
                    return sort(args, stdin);
                case "wc":
                    return wc(args, stdin);
                case "find":
                    return find(args);
                case "chmod":
                    return chmod(args);
                case "date":
                    return ok(name, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()) + "\n");
                case "env":
                    return env();
                case "which":
                    return which(args);
                case "clear":
                    return ok(name, "");
                case "help":
                    return ok(name, "DVINE shell: " + String.join(" ", NAMES)
                            + "\nChains: && || ; | > >> < and $NAME. python, git, pkg and compilers are not bundled.\n");
                case "unzip":
                    return unzip(args);
                default:
                    return new CommandResult(name, 127, "", name + ": not in DVINE's shell. It is not bundled, and Termux is not used.", 0);
            }
        } catch (IOException e) {
            return new CommandResult(name, 1, "", e.getMessage() == null ? "failed" : e.getMessage(), 0);
        }
    }

    private CommandResult cd(List<String> args) {
        if (args.size() != 2) {
            return fail("cd", "cd needs one folder");
        }
        File d = resolve(args.get(1), true);
        if (d == null || !d.isDirectory()) {
            return fail("cd", "No such folder: " + args.get(1));
        }
        cwd = d;
        env.put("PWD", d.getAbsolutePath());
        return ok("cd", "");
    }

    private CommandResult ls(List<String> args) {
        File dir = args.size() < 2 ? cwd : resolve(args.get(1), false);
        if (dir == null || !dir.isDirectory()) {
            return fail("ls", "No such folder");
        }
        File[] kids = dir.listFiles();
        if (kids == null || kids.length == 0) {
            return ok("ls", "");
        }
        Arrays.sort(kids);
        StringBuilder b = new StringBuilder();
        for (File f : kids) {
            b.append(f.getName()).append('\n');
        }
        return ok("ls", b.toString());
    }

    private CommandResult make(List<String> args, boolean dir) throws IOException {
        if (args.size() != 2) {
            return fail(args.get(0), "needs one name");
        }
        File f = resolve(args.get(1), true);
        if (f == null) {
            return fail(args.get(0), "Refused");
        }
        if (dir) {
            if (!f.mkdirs() && !f.isDirectory()) {
                return fail("mkdir", "Couldn't make " + args.get(1));
            }
        } else {
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (!f.exists() && !f.createNewFile()) {
                return fail("touch", "Couldn't make " + args.get(1));
            }
        }
        return ok(args.get(0), "");
    }

    private CommandResult rmdir(List<String> args) {
        if (args.size() != 2) {
            return fail("rmdir", "needs one folder");
        }
        File f = resolve(args.get(1), false);
        if (f == null || !f.isDirectory()) {
            return fail("rmdir", "No such folder");
        }
        String[] kids = f.list();
        if (kids != null && kids.length > 0) {
            return fail("rmdir", "Folder is not empty");
        }
        return f.delete() ? ok("rmdir", "") : fail("rmdir", "Couldn't remove it");
    }

    private CommandResult rm(List<String> args) {
        if (args.size() < 2 || args.get(1).startsWith("-")) {
            return fail("rm", "I remove one file at a time, not a tree. There is no -r.");
        }
        File f = resolve(args.get(1), false);
        if (f == null || !f.isFile() || same(f, root) || !f.delete()) {
            return fail("rm", "I only remove one file inside the workspace.");
        }
        return ok("rm", "");
    }

    private CommandResult copy(List<String> args, boolean move) throws IOException {
        if (args.size() != 3) {
            return fail(args.get(0), "needs a source and a destination");
        }
        File from = resolve(args.get(1), false);
        File to = resolve(args.get(2), true);
        if (from == null || to == null || !from.isFile()) {
            return fail(args.get(0), "No such file");
        }
        File parent = to.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        if (move && !from.delete()) {
            return fail("mv", "Copied, but the original stayed");
        }
        return ok(args.get(0), "");
    }

    private CommandResult cat(List<String> args, String stdin) {
        if (args.size() == 1) {
            return ok("cat", stdin);
        }
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < args.size(); i++) {
            File f = resolve(args.get(i), false);
            if (f == null || !f.isFile()) {
                return fail("cat", "No such file: " + args.get(i));
            }
            b.append(read(f, MOST_OUT));
        }
        return ok("cat", b.toString());
    }

    private CommandResult printf(List<String> args) {
        if (args.size() < 2) {
            return ok("printf", "");
        }
        String fmt = args.get(1);
        StringBuilder b = new StringBuilder();
        if (args.size() == 2) {
            b.append(unescape(fmt));
        } else {
            for (int i = 2; i < args.size(); i++) {
                b.append(unescape(fmt).replace("%s", args.get(i)).replace("%d", args.get(i)));
            }
        }
        return ok("printf", b.toString());
    }

    private CommandResult ends(List<String> args, String stdin, boolean tail) {
        int n = 10;
        String file = null;
        for (int i = 1; i < args.size(); i++) {
            if ("-n".equals(args.get(i)) && i + 1 < args.size()) {
                try {
                    n = Integer.parseInt(args.get(++i));
                } catch (NumberFormatException e) {
                    return fail(args.get(0), "bad count");
                }
            } else {
                file = args.get(i);
            }
        }
        String text = stdin;
        if (file != null) {
            File f = resolve(file, false);
            if (f == null || !f.isFile()) {
                return fail(args.get(0), "No such file");
            }
            text = read(f, MOST_OUT);
        }
        String[] lines = text.split("\n", -1);
        int from = tail ? Math.max(0, lines.length - Math.max(0, n)) : 0;
        int to = tail ? lines.length : Math.min(lines.length, Math.max(0, n));
        StringBuilder b = new StringBuilder();
        for (int i = from; i < to; i++) {
            b.append(lines[i]);
            if (i + 1 < to || text.endsWith("\n")) {
                b.append('\n');
            }
        }
        return ok(args.get(0), b.toString());
    }

    private CommandResult grep(List<String> args, String stdin) {
        boolean num = false, ignore = false;
        List<String> rest = new ArrayList<String>();
        for (int i = 1; i < args.size(); i++) {
            if ("-n".equals(args.get(i))) {
                num = true;
            } else if ("-i".equals(args.get(i))) {
                ignore = true;
            } else {
                rest.add(args.get(i));
            }
        }
        if (rest.isEmpty()) {
            return fail("grep", "needs a pattern");
        }
        String pattern = rest.get(0);
        String text = stdin;
        String label = "";
        if (rest.size() > 1) {
            File f = resolve(rest.get(1), false);
            if (f == null || !f.isFile()) {
                return fail("grep", "No such file");
            }
            text = read(f, MOST_OUT);
            label = f.getName() + ":";
        }
        String want = ignore ? pattern.toLowerCase(Locale.US) : pattern;
        String[] lines = text.split("\n", -1);
        StringBuilder b = new StringBuilder();
        int hits = 0;
        for (int i = 0; i < lines.length; i++) {
            String look = ignore ? lines[i].toLowerCase(Locale.US) : lines[i];
            if (look.contains(want)) {
                if (label.length() > 0) {
                    b.append(label);
                }
                if (num) {
                    b.append(i + 1).append(':');
                }
                b.append(lines[i]).append('\n');
                hits++;
            }
        }
        return new CommandResult("grep", hits == 0 ? 1 : 0, b.toString(), "", 0);
    }

    private CommandResult sort(List<String> args, String stdin) {
        String text = stdin;
        if (args.size() > 1) {
            File f = resolve(args.get(1), false);
            if (f == null || !f.isFile()) {
                return fail("sort", "No such file");
            }
            text = read(f, MOST_OUT);
        }
        List<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        Collections.sort(lines);
        StringBuilder b = new StringBuilder();
        for (String s : lines) {
            b.append(s).append('\n');
        }
        return ok("sort", b.toString());
    }

    private CommandResult wc(List<String> args, String stdin) {
        boolean lines = false, words = false, bytes = false;
        String file = null;
        for (int i = 1; i < args.size(); i++) {
            if ("-l".equals(args.get(i))) {
                lines = true;
            } else if ("-w".equals(args.get(i))) {
                words = true;
            } else if ("-c".equals(args.get(i))) {
                bytes = true;
            } else {
                file = args.get(i);
            }
        }
        if (!lines && !words && !bytes) {
            lines = words = bytes = true;
        }
        String text = stdin;
        if (file != null) {
            File f = resolve(file, false);
            if (f == null || !f.isFile()) {
                return fail("wc", "No such file");
            }
            text = read(f, MOST_OUT);
        }
        int nLines = text.isEmpty() ? 0 : text.split("\n", -1).length - (text.endsWith("\n") ? 1 : 0);
        if (text.endsWith("\n")) {
            nLines = text.split("\n", -1).length - 1;
        }
        int nWords = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        StringBuilder b = new StringBuilder();
        if (lines) {
            b.append(nLines).append(' ');
        }
        if (words) {
            b.append(nWords).append(' ');
        }
        if (bytes) {
            b.append(text.getBytes(StandardCharsets.UTF_8).length).append(' ');
        }
        return ok("wc", b.toString().trim() + "\n");
    }

    private CommandResult find(List<String> args) {
        File dir = cwd;
        String glob = "*";
        for (int i = 1; i < args.size(); i++) {
            if ("-name".equals(args.get(i)) && i + 1 < args.size()) {
                glob = args.get(++i);
            } else if (!args.get(i).startsWith("-")) {
                File d = resolve(args.get(i), false);
                if (d == null || !d.isDirectory()) {
                    return fail("find", "No such folder");
                }
                dir = d;
            }
        }
        List<String> hits = new ArrayList<String>();
        walk(dir, glob, hits);
        StringBuilder b = new StringBuilder();
        for (String h : hits) {
            b.append(h).append('\n');
        }
        return ok("find", b.toString());
    }

    private void walk(File dir, String glob, List<String> hits) {
        if (dir == null || hits.size() > 200) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            String rel = rel(f);
            if (match(glob, f.getName()) && !f.isDirectory()) {
                hits.add(rel);
            }
            if (f.isDirectory()) {
                walk(f, glob, hits);
            }
        }
    }

    private CommandResult chmod(List<String> args) {
        if (args.size() != 3 || args.get(1).length() < 3) {
            return fail("chmod", "chmod MODE FILE, for example chmod 644 notes.txt");
        }
        File f = resolve(args.get(2), false);
        if (f == null || !f.exists()) {
            return fail("chmod", "No such file");
        }
        char owner = args.get(1).charAt(args.get(1).length() - 3);
        if (owner < '0' || owner > '7') {
            return fail("chmod", "MODE is three digits");
        }
        int bits = owner - '0';
        f.setReadable((bits & 4) != 0, true);
        f.setWritable((bits & 2) != 0, true);
        f.setExecutable((bits & 1) != 0, true);
        return ok("chmod", "");
    }

    private CommandResult env() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : env.entrySet()) {
            b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return ok("env", b.toString());
    }

    private CommandResult which(List<String> args) {
        if (args.size() != 2) {
            return fail("which", "needs one name");
        }
        if (known(args.get(1))) {
            return ok("which", args.get(1) + "\n");
        }
        return new CommandResult("which", 1, "", args.get(1) + " is not bundled\n", 0);
    }

    private CommandResult unzip(List<String> args) throws IOException {
        String file = null, dest = null;
        for (int i = 1; i < args.size(); i++) {
            String p = args.get(i);
            if ("-n".equals(p) || "-o".equals(p) || "-q".equals(p)) {
                continue;
            }
            if ("-d".equals(p) && i + 1 < args.size()) {
                dest = args.get(++i);
                continue;
            }
            if (p.startsWith("-") || file != null) {
                return fail("unzip", "unzip [-n] FILE [-d DIR]");
            }
            file = p;
        }
        if (file == null || file.contains("..")) {
            return fail("unzip", "Tell me the zip's name.");
        }
        File zip = resolve(file, false);
        if (zip == null || !zip.isFile()) {
            zip = search(new File(file).getName());
        }
        if (zip == null) {
            return fail("unzip", "No such zip: " + file);
        }
        String folder = dest == null ? file.replaceAll("(?i)\\.zip$", "") : dest;
        File into = resolve(folder, true);
        if (into == null) {
            return fail("unzip", "Refused to write outside the workspace");
        }
        int n = Zips.unzip(zip, into);
        return ok("unzip", "Unzipped " + n + (n == 1 ? " file" : " files") + " into " + folder + "\n");
    }

    private File search(String base) {
        List<File> look = new ArrayList<File>();
        look.add(root);
        look.addAll(extraRoots);
        List<File> found = new ArrayList<File>();
        for (File r : look) {
            hunt(r, 0, base.toLowerCase(Locale.US), found);
        }
        Collections.sort(found, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return found.isEmpty() ? null : found.get(0);
    }

    private void hunt(File dir, int depth, String base, List<File> found) {
        if (dir == null || !dir.isDirectory() || depth > 4 || found.size() > 20) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (f.isDirectory()) {
                hunt(f, depth + 1, base, found);
            } else if (f.getName().toLowerCase(Locale.US).equals(base)) {
                found.add(f);
            }
        }
    }

    private File resolve(String name, boolean create) {
        if (name == null || name.contains("..")) {
            return null;
        }
        String expanded = name.equals("~") ? root.getPath() : name;
        File f = expanded.startsWith("/") ? new File(expanded) : new File(cwd, expanded);
        if (!inside(root, f)) {
            return null;
        }
        if (create) {
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
        }
        return f;
    }

    private static boolean inside(File root, File file) {
        try {
            String r = root.getCanonicalPath();
            String p = file.getCanonicalPath();
            return p.equals(r) || p.startsWith(r + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private String rel(File f) {
        String r = root.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (p.startsWith(r)) {
            String cut = p.substring(r.length());
            return cut.startsWith(File.separator) ? cut.substring(1) : cut;
        }
        return f.getName();
    }

    private static boolean same(File a, File b) {
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean match(String glob, String name) {
        String re = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return name.matches(re);
    }

    private static String read(File f, int most) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(Math.max(0, f.length()), most)];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static void write(File f, String text, boolean append) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(f, append)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t");
    }

    private static String joinArgs(List<String> args, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < args.size(); i++) {
            if (i > from) {
                b.append(' ');
            }
            b.append(args.get(i));
        }
        return b.toString();
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (b.length() > 0 && !b.toString().endsWith("\n")) {
                b.append('\n');
            }
            b.append(p);
        }
        String s = b.toString();
        return s.length() > MOST_OUT ? s.substring(0, MOST_OUT) + "\n…(output cut short)\n" : s;
    }

    private static CommandResult ok(String name, String stdout) {
        return new CommandResult(name, 0, stdout, "", 0);
    }

    private static CommandResult fail(String name, String stderr) {
        return new CommandResult(name, 1, "", stderr + "\n", 0);
    }

    private static CommandResult done(String line, int exit, String stdout, String stderr, long start) {
        return new CommandResult(line, exit, stdout, stderr, System.currentTimeMillis() - start);
    }

    /** Words, with quotes removed and $NAME filled in. Operators stay as their own words. */
    static List<String> tokenize(String line) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '&' && i + 1 < line.length() && line.charAt(i + 1) == '&') {
                add(out, "&&");
                i += 2;
                continue;
            }
            if (c == '&') {
                throw new IllegalArgumentException("Background jobs (&) are not run.");
            }
            if (c == '|' && i + 1 < line.length() && line.charAt(i + 1) == '|') {
                add(out, "||");
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < line.length() && line.charAt(i + 1) == '>') {
                add(out, ">>");
                i += 2;
                continue;
            }
            if (c == '|' || c == '>' || c == '<' || c == ';') {
                add(out, String.valueOf(c));
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                char q = c;
                i++;
                StringBuilder b = new StringBuilder();
                while (i < line.length() && line.charAt(i) != q) {
                    b.append(line.charAt(i++));
                }
                if (i >= line.length()) {
                    throw new IllegalArgumentException("A quote was not closed.");
                }
                i++;
                add(out, q == '"' ? b.toString() : "\u0001" + b.toString());
                continue;
            }
            int start = i;
            StringBuilder b = new StringBuilder();
            while (i < line.length()) {
                char d = line.charAt(i);
                if (Character.isWhitespace(d) || d == '|' || d == '>' || d == '<' || d == ';' || d == '&' || d == '\'' || d == '"') {
                    break;
                }
                b.append(d);
                i++;
            }
            if (i == start) {
                throw new IllegalArgumentException("That character is not a command: " + c);
            }
            add(out, b.toString());
        }
        return out;
    }

    private static void add(List<String> out, String token) {
        if (out.size() >= MOST_TOKENS) {
            throw new IllegalArgumentException("That command has too many parts to run.");
        }
        out.add(token);
    }

    private List<String> expand(List<String> tokens) {
        List<String> out = new ArrayList<String>();
        for (String t : tokens) {
            if (t.startsWith("\u0001")) {
                out.add(t.substring(1));
            } else if ("&&".equals(t) || "||".equals(t) || ";".equals(t) || "|".equals(t) || ">".equals(t) || ">>".equals(t) || "<".equals(t)) {
                out.add(t);
            } else {
                out.add(expand(t));
            }
        }
        return out;
    }

    private String expand(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '$' && i + 1 < s.length()) {
                int j = i + 1;
                if (s.charAt(j) == '{') {
                    int end = s.indexOf('}', j);
                    if (end > j) {
                        b.append(envGet(s.substring(j + 1, end)));
                        i = end;
                        continue;
                    }
                }
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
                    j++;
                }
                if (j > i + 1) {
                    b.append(envGet(s.substring(i + 1, j)));
                    i = j - 1;
                    continue;
                }
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private String envGet(String name) {
        String v = env.get(name);
        if (v != null) {
            return v;
        }
        v = System.getenv(name);
        return v == null ? "" : v;
    }
}
