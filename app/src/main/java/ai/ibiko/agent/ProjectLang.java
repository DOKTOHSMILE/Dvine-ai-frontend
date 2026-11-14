package ai.ibiko.agent;

import java.io.File;
import java.util.Locale;

/**
 * Which languages a project actually contains. Models otherwise invent Kotlin ({@code .kt}) for an Android tree that
 * is Java, then search for files that are not there. Pure Java.
 */
public final class ProjectLang {
    public final int javaFiles, kotlinFiles;

    private ProjectLang(int javaFiles, int kotlinFiles) {
        this.javaFiles = javaFiles;
        this.kotlinFiles = kotlinFiles;
    }

    /** Counts {@code .java} and {@code .kt}/{@code .kts} under {@code root}. Build output and hidden folders are skipped. */
    public static ProjectLang of(File root) {
        int[] n = new int[2];
        walk(root, n, 0);
        return new ProjectLang(n[0], n[1]);
    }

    /** Java is here and Kotlin is not: the case that was sending the agent after files that do not exist. */
    public boolean javaOnly() {
        return javaFiles > 0 && kotlinFiles == 0;
    }

    /** A line for the coding agent's instructions. Empty when the project is not Java-only. */
    public String rule() {
        if (!javaOnly()) {
            return "";
        }
        return "\nLanguages in this project: Java (" + javaFiles + " .java files). There is no Kotlin."
                + " Do not create, open, search for, or mention .kt or .kts files. A .kt name in a note or an old"
                + " document is not a file here: list the folder, or read the .java file. Never write a file ending in .kt.\n";
    }

    /**
     * A tool result that stops a {@code .kt} read or write in a Java-only project, or null when the call may run.
     * {@code tool} is {@code read_file}, {@code write_file}, {@code edit_file} or {@code make_file}.
     */
    public String blocked(String path, String tool) {
        if (!javaOnly() || path == null || !kotlinPath(path)) {
            return null;
        }
        if ("write_file".equals(tool) || "edit_file".equals(tool) || "make_file".equals(tool)) {
            return "Not written: this project is Java (" + javaFiles + " files) and has no Kotlin. Write a .java file."
                    + " Do not create " + path + ".";
        }
        return "No such file: " + path + ". This project has no Kotlin. Do not look for .kt files."
                + " Read the .java file with the same name, or list the folder.";
    }

    /** Prepended to a search that asks for Kotlin in a Java-only project, or null. */
    public String searchNote(String query) {
        if (!javaOnly() || query == null) {
            return null;
        }
        if (!query.toLowerCase(Locale.US).matches("(?s).*(\\.kts?\\b|kotlin).*")) {
            return null;
        }
        return "This project has no Kotlin (" + javaFiles + " .java files, 0 .kt). A mention of a .kt file is not a file"
                + " on disk. Do not open one.";
    }

    private static boolean kotlinPath(String path) {
        String p = path.replace('\\', '/').toLowerCase(Locale.US);
        return p.endsWith(".kt") || p.endsWith(".kts");
    }

    private static void walk(File dir, int[] n, int depth) {
        File[] in = dir == null ? null : dir.listFiles();
        if (in == null || depth > 12 || n[0] + n[1] > 20000) {
            return;
        }
        for (File f : in) {
            String name = f.getName();
            if (f.isDirectory()) {
                if (name.startsWith(".") || name.equals("build") || name.equals("node_modules") || name.equals(".gradle")
                        || name.equals("third_party") || name.equals("llama.cpp")) {
                    continue;
                }
                walk(f, n, depth + 1);
            } else if (name.endsWith(".java")) {
                n[0]++;
            } else if (name.endsWith(".kt") || name.endsWith(".kts")) {
                n[1]++;
            }
        }
    }
}
