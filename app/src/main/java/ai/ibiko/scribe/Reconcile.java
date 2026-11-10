package ai.ibiko.scribe;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares the files a job claims to have written with the files actually in the workspace. A path that escapes the
 * workspace is treated as missing. A claim is not proof.
 */
public final class Reconcile {
    public final List<String> present = new ArrayList<String>();
    public final List<String> missing = new ArrayList<String>();

    private Reconcile() {
    }

    /** True only when at least one claimed file is on disk and none are missing. */
    public boolean verified() {
        return missing.isEmpty() && !present.isEmpty();
    }

    public static Reconcile check(File workspace, List<String> claimed) {
        Reconcile out = new Reconcile();
        if (claimed == null) {
            return out;
        }
        for (String raw : claimed) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String name = raw.trim();
            File file = resolve(workspace, name);
            if (file != null && file.isFile()) {
                out.present.add(name);
            } else {
                out.missing.add(name);
            }
        }
        return out;
    }

    public String report() {
        StringBuilder b = new StringBuilder("# Recovery\n\n");
        b.append("Present: ").append(present.size()).append('\n');
        for (String f : present) {
            b.append("- ").append(f).append('\n');
        }
        b.append("\nMissing: ").append(missing.size()).append('\n');
        for (String f : missing) {
            b.append("- ").append(f).append('\n');
        }
        if (present.isEmpty() && missing.isEmpty()) {
            b.append("\nNo files were claimed, so the job is not finished.\n");
        } else if (!missing.isEmpty()) {
            b.append("\nMissing files stay outstanding. They are not assumed written.\n");
        } else {
            b.append("\nEvery claimed file is on disk.\n");
        }
        return b.toString();
    }

    static File resolve(File workspace, String claimed) {
        if (workspace == null || claimed.indexOf('\0') >= 0 || claimed.contains("..")) {
            return null;
        }
        File file = new File(claimed);
        if (!file.isAbsolute()) {
            file = new File(workspace, claimed);
        }
        try {
            String root = workspace.getCanonicalPath();
            String got = file.getCanonicalPath();
            if (!got.equals(root) && !got.startsWith(root + File.separator)) {
                return null;
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }
}
