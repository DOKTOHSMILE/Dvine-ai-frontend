package ai.dvine.scribe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Looks at a workspace before any files are written. The plan comes from what is already there, not from a guess
 * that the folder is empty.
 */
public final class ProjectSurvey {
    static final int MOST_FILES = 2000;
    static final int MOST_DEPTH = 6;

    public final int files;
    public final int dirs;
    public final boolean capped;
    public final List<String> markers;
    public final List<String> languages;
    public final List<String> top;
    public final List<String> steps;

    private ProjectSurvey(int files, int dirs, boolean capped, List<String> markers, List<String> languages,
            List<String> top, List<String> steps) {
        this.files = files;
        this.dirs = dirs;
        this.capped = capped;
        this.markers = markers;
        this.languages = languages;
        this.top = top;
        this.steps = steps;
    }

    public static ProjectSurvey of(File root) {
        List<String> markers = new ArrayList<String>();
        List<String> top = new ArrayList<String>();
        Map<String, Integer> lang = new LinkedHashMap<String, Integer>();
        int[] count = new int[2]; // files, dirs
        boolean[] capped = new boolean[1];
        if (root != null && root.isDirectory()) {
            File[] kids = root.listFiles();
            if (kids != null) {
                java.util.Arrays.sort(kids);
                for (File k : kids) {
                    if (skip(k.getName()) || top.size() >= 40) {
                        continue;
                    }
                    top.add(k.getName() + (k.isDirectory() ? "/" : ""));
                }
            }
            walk(root, 0, count, capped, markers, lang);
        }
        List<String> languages = ranked(lang);
        return new ProjectSurvey(count[0], count[1], capped[0], markers, languages, top, steps(count[0], markers, languages));
    }

    public String plan(String instruction) {
        StringBuilder b = new StringBuilder();
        b.append("# Plan\n\n## Instruction\n\n").append(instruction == null ? "" : instruction).append("\n\n");
        b.append("## What is already here\n\n");
        if (files == 0) {
            b.append("The workspace has no project files yet.\n\n");
        } else {
            b.append(files).append(" files in ").append(dirs).append(" directories.");
            if (!languages.isEmpty()) {
                b.append(" Languages: ").append(join(languages)).append('.');
            }
            if (capped) {
                b.append(" The survey stopped at ").append(MOST_FILES).append(" files; the rest is still on disk.");
            }
            b.append("\n\n");
        }
        if (!markers.isEmpty()) {
            b.append("Build files: ").append(join(markers)).append(".\n\n");
        }
        b.append("## Steps\n\n");
        int n = 1;
        for (String step : steps) {
            b.append(n++).append(". ").append(step).append('\n');
        }
        b.append("\nDo not overwrite an existing file unless the instruction asks for that file.\n");
        return b.toString();
    }

    public String map() {
        StringBuilder b = new StringBuilder("# Workspace map\n\n");
        if (top.isEmpty()) {
            b.append("(empty)\n");
            return b.toString();
        }
        for (String name : top) {
            b.append("- ").append(name).append('\n');
        }
        if (!markers.isEmpty()) {
            b.append("\n## Markers\n\n");
            for (String m : markers) {
                b.append("- ").append(m).append('\n');
            }
        }
        return b.toString();
    }

    private static List<String> steps(int files, List<String> markers, List<String> languages) {
        List<String> steps = new ArrayList<String>();
        steps.add("Read the instruction and the map. Ask only if a required fact cannot be worked out.");
        if (files == 0) {
            steps.add("The workspace is empty. Create only the files this instruction needs.");
        } else {
            steps.add("Inspect the existing " + (languages.isEmpty() ? "project" : join(languages) + " project")
                    + ". Do not start a second project beside it.");
        }
        if (markers.contains("build.gradle") || markers.contains("settings.gradle") || markers.contains("AndroidManifest.xml")) {
            steps.add("This is an Android project. Keep its Gradle structure and do not replace working sources.");
        } else if (markers.contains("package.json")) {
            steps.add("This is a Node project. Use the existing package.json; do not invent a second one.");
        } else if (markers.contains("CMakeLists.txt") || markers.contains("Makefile")) {
            steps.add("This is a native build. Keep the existing build file.");
        } else if (markers.contains("requirements.txt") || markers.contains("pyproject.toml")) {
            steps.add("This is a Python project. Keep the existing dependency file.");
        }
        steps.add("Carry out the instruction in small steps, one file change at a time.");
        steps.add("Verify from the files and command results. A sentence in chat is not completion.");
        return steps;
    }

    private static void walk(File dir, int depth, int[] count, boolean[] capped, List<String> markers, Map<String, Integer> lang) {
        if (depth > MOST_DEPTH || capped[0]) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (capped[0] || skip(k.getName())) {
                continue;
            }
            if (k.isDirectory()) {
                count[1]++;
                noteMarker(k.getName(), markers);
                walk(k, depth + 1, count, capped, markers, lang);
            } else {
                count[0]++;
                noteMarker(k.getName(), markers);
                String language = language(k.getName());
                if (language != null) {
                    Integer n = lang.get(language);
                    lang.put(language, n == null ? 1 : n + 1);
                }
                if (count[0] >= MOST_FILES) {
                    capped[0] = true;
                    return;
                }
            }
        }
    }

    private static void noteMarker(String name, List<String> markers) {
        if (name.equals("build.gradle") || name.equals("build.gradle.kts") || name.equals("settings.gradle")
                || name.equals("AndroidManifest.xml") || name.equals("package.json") || name.equals("CMakeLists.txt")
                || name.equals("Makefile") || name.equals("pom.xml") || name.equals("requirements.txt")
                || name.equals("pyproject.toml") || name.equals("Cargo.toml") || name.equals("go.mod")) {
            if (!markers.contains(name) && markers.size() < 12) {
                markers.add(name);
            }
        }
    }

    private static String language(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".java")) return "Java";
        if (n.endsWith(".kt")) return "Kotlin";
        if (n.endsWith(".py")) return "Python";
        if (n.endsWith(".js") || n.endsWith(".jsx")) return "JavaScript";
        if (n.endsWith(".ts") || n.endsWith(".tsx")) return "TypeScript";
        if (n.endsWith(".c") || n.endsWith(".cc") || n.endsWith(".cpp") || n.endsWith(".h") || n.endsWith(".hpp")) return "C/C++";
        if (n.endsWith(".xml")) return "XML";
        if (n.endsWith(".rs")) return "Rust";
        if (n.endsWith(".go")) return "Go";
        return null;
    }

    private static boolean skip(String name) {
        return name.equals("memory") || name.equals(".git") || name.equals("node_modules") || name.equals("build")
                || name.equals(".gradle") || name.equals(".idea");
    }

    private static List<String> ranked(Map<String, Integer> lang) {
        List<Map.Entry<String, Integer>> rows = new ArrayList<Map.Entry<String, Integer>>(lang.entrySet());
        Collections.sort(rows, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : rows) {
            if (out.size() == 4) {
                break;
            }
            out.add(e.getKey());
        }
        return out;
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(parts.get(i));
        }
        return b.toString();
    }
}
