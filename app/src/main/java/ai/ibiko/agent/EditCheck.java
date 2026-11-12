package ai.ibiko.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The check Ibiko runs right after it writes or edits a code file, so a mistake is caught at the edit that made it
 * rather than at the end of the task. The project's own tests come first (npm test, pytest, cargo test, go test,
 * make test), found by walking up from the file's folder to the workspace; failing that, a syntax check of the one
 * file (python, node, sh, ruby, php, C, C++). Nothing for a file that isn't code (notes, a plan, data). Pure Java.
 */
public final class EditCheck {
    private EditCheck() {
    }

    /** Settings › Coding › Check code after each edit (on unless the user turns it off). */
    public static final String PREF = "check_after_edit";

    /** How a check went. */
    public enum Verdict {
        PASSED, FAILED, NOT_AVAILABLE
    }

    /** The shell command that checks {@code changed} (run from {@code workspace}), or null when there is nothing to run. */
    public static String command(File workspace, File changed) {
        String name = changed.getName().toLowerCase(Locale.ROOT);
        String ext = name.lastIndexOf('.') < 0 ? "" : name.substring(name.lastIndexOf('.') + 1);
        if (!isCode(ext)) {
            return null;
        }
        String tests = projectTests(workspace, changed, ext);
        if (tests != null) {
            return tests;
        }
        String rel = relative(workspace, changed);
        if (rel == null) {
            return null;
        }
        String f = quote(rel);
        switch (ext) {
            case "py":
                return "python3 -m py_compile " + f;
            case "js":
            case "mjs":
            case "cjs":
                return "node --check " + f;
            case "sh":
                return "sh -n " + f;
            case "rb":
                return "ruby -c " + f;
            case "php":
                return "php -l " + f;
            case "c":
                return "cc -fsyntax-only " + f;
            case "cc":
            case "cpp":
            case "cxx":
                return "c++ -fsyntax-only " + f;
            default:
                return null;
        }
    }

    /** Reads a shell result: a non-zero exit or a stop is a failure; 127 (no such program) means nothing could check. */
    public static Verdict verdict(String output) {
        String out = output == null ? "" : output;
        Matcher m = Pattern.compile("\\[exit (\\d+)\\]").matcher(out);
        int code = 0;
        while (m.find()) {
            code = Integer.parseInt(m.group(1));
        }
        if (code == 127 || (code != 0 && out.matches("(?s).*(command not found|not found: |No module named (pytest|py_compile)).*"))) {
            return Verdict.NOT_AVAILABLE;
        }
        if (code != 0 || out.contains("[stopped")) {
            return Verdict.FAILED;
        }
        return Verdict.PASSED;
    }

    private static boolean isCode(String ext) {
        switch (ext) {
            case "py": case "js": case "mjs": case "cjs": case "ts": case "tsx": case "jsx": case "sh": case "rb": case "php":
            case "c": case "h": case "cc": case "cpp": case "cxx": case "hpp": case "go": case "rs":
                return true;
            default:
                return false;
        }
    }

    /** The nearest project's test command for this kind of file, with a cd into the project when it isn't the workspace. */
    private static String projectTests(File workspace, File changed, String ext) {
        File root;
        try {
            root = workspace.getCanonicalFile();
        } catch (IOException e) {
            return null;
        }
        File dir = changed.getAbsoluteFile().getParentFile();
        while (dir != null) {
            String run = testsIn(dir, ext);
            if (run != null) {
                String rel = relative(root, dir);
                if (rel == null) {
                    return null;
                }
                return rel.isEmpty() || rel.equals(".") ? run : "cd " + quote(rel) + " && " + run;
            }
            File canon;
            try {
                canon = dir.getCanonicalFile();
            } catch (IOException e) {
                return null;
            }
            if (canon.equals(root)) {
                return null; // never above the workspace
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    private static String testsIn(File dir, String ext) {
        boolean js = ext.equals("js") || ext.equals("mjs") || ext.equals("cjs") || ext.equals("ts") || ext.equals("tsx") || ext.equals("jsx");
        File pkg = new File(dir, "package.json");
        if (js && pkg.isFile()) {
            String p = read(pkg);
            // npm's own placeholder ("Error: no test specified") is no test
            if (p.matches("(?s).*\"scripts\"\\s*:\\s*\\{[^}]*\"test\"\\s*:.*") && !p.contains("no test specified")) {
                return "npm test --silent";
            }
        }
        if (ext.equals("py") && (new File(dir, "pytest.ini").isFile() || new File(dir, "conftest.py").isFile()
                || read(new File(dir, "pyproject.toml")).contains("pytest") || read(new File(dir, "setup.cfg")).contains("pytest")
                || new File(dir, "tests").isDirectory())) {
            return "python3 -m pytest -q -x";
        }
        if (ext.equals("rs") && new File(dir, "Cargo.toml").isFile()) {
            return "cargo test -q";
        }
        if (ext.equals("go") && new File(dir, "go.mod").isFile()) {
            return "go test ./...";
        }
        File make = new File(dir, "Makefile");
        if (make.isFile() && read(make).matches("(?s)(.*\\n)?test\\s*:.*")) {
            return "make test";
        }
        return null;
    }

    private static String read(File f) {
        try {
            return f.isFile() && f.length() < 512 * 1024 ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** {@code f}'s path from {@code root} with forward slashes ("" for the root itself), or null when it is outside. */
    static String relative(File root, File f) {
        try {
            String r = root.getCanonicalPath(), p = f.getCanonicalPath();
            if (p.equals(r)) {
                return "";
            }
            if (!p.startsWith(r + File.separator)) {
                return null;
            }
            return p.substring(r.length() + 1).replace(File.separatorChar, '/');
        } catch (IOException e) {
            return null;
        }
    }

    /** A single-quoted shell word: the path exactly, whatever it holds. */
    static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
