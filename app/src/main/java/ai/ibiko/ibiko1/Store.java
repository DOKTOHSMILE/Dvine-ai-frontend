package ai.ibiko.ibiko1;

import ai.ibiko.ibiko1.learner.Learner;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * One copy of each of Ibiko 1's memory files for the whole app. The chat, the learning session (a job in the same
 * process) and the Memory screen all read and write through these, so one never saves an old copy over what another
 * just learned. {@code dir} is the app's {@code brain} folder.
 */
public final class Store {
    private Store() {
    }

    private static final Map<String, Object> OPEN = new HashMap<String, Object>();

    private static String key(File dir, String name) {
        try {
            return new File(dir, name).getCanonicalPath();
        } catch (java.io.IOException e) {
            return new File(dir, name).getAbsolutePath();
        }
    }

    /** Which version of the large knowledge files the app carries; a new version is unpacked again. */
    static final String KNOWLEDGE_VERSION = "2026-11-11";

    /**
     * A large knowledge file shipped in the app (knowledge/<name>), unpacked once into {@code dir} so it can be read
     * in place by binary search; null when the app carries none. Call it off the main thread (tens of MB, once).
     */
    public static synchronized File unpacked(File dir, String name) {
        File out = new File(dir, name), mark = new File(dir, name + ".version");
        try {
            if (out.isFile() && out.length() > 0 && mark.isFile()
                    && KNOWLEDGE_VERSION.equals(new String(java.nio.file.Files.readAllBytes(mark.toPath()), "UTF-8").trim())) {
                return out;
            }
            java.io.InputStream in = Plug.asset.apply("knowledge/" + name);
            if (in == null) {
                return out.isFile() ? out : null;
            }
            dir.mkdirs();
            File tmp = new File(dir, name + ".part");
            try (java.io.InputStream src = in; java.io.OutputStream dst = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = src.read(buf)) > 0) {
                    dst.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(out)) {
                java.nio.file.Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.nio.file.Files.write(mark.toPath(), KNOWLEDGE_VERSION.getBytes("UTF-8"));
            return out;
        } catch (java.io.IOException e) {
            return out.isFile() ? out : null;
        }
    }

    public static synchronized Knowledge knowledge(File dir) {
        String k = key(dir, "knowledge.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new Knowledge(new File(dir, "knowledge.tsv"));
            OPEN.put(k, o);
        }
        return (Knowledge) o;
    }

    public static synchronized ai.ibiko.ibiko1.formulas.Reflection reflection(File dir) {
        String k = key(dir, "reflection.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new ai.ibiko.ibiko1.formulas.Reflection(new File(dir, "reflection.tsv"));
            OPEN.put(k, o);
        }
        return (ai.ibiko.ibiko1.formulas.Reflection) o;
    }

    /** The truth ledger (formulas/Truth G9): every sentence judged true or false. */
    public static synchronized ai.ibiko.ibiko1.formulas.TruthLedger truthLedger(File dir) {
        String k = key(dir, "truth.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new ai.ibiko.ibiko1.formulas.TruthLedger(new File(dir, "truth.tsv"));
            OPEN.put(k, o);
        }
        return (ai.ibiko.ibiko1.formulas.TruthLedger) o;
    }

    public static synchronized ai.ibiko.ibiko1.formulas.Foresight foresight(File dir) {
        String k = key(dir, "foresight.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new ai.ibiko.ibiko1.formulas.Foresight(new File(dir, "foresight.tsv"));
            OPEN.put(k, o);
        }
        return (ai.ibiko.ibiko1.formulas.Foresight) o;
    }

    public static synchronized Methods methods(File dir) {
        String k = key(dir, "methods.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new Methods(new File(dir, "methods.tsv"));
            OPEN.put(k, o);
        }
        return (Methods) o;
    }

    public static synchronized Facts facts(File dir) {
        String k = key(dir, "facts.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new Facts(new File(dir, "facts.tsv"));
            OPEN.put(k, o);
        }
        return (Facts) o;
    }

    public static synchronized Lexicon lexicon(File dir) {
        String k = key(dir, "words.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new Lexicon(new File(dir, "words.tsv"));
            OPEN.put(k, o);
        }
        return (Lexicon) o;
    }

    public static synchronized Learner learner(File dir) {
        String k = key(dir, "learner-state.json");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new Learner(dir, facts(dir), lexicon(dir));
            OPEN.put(k, o);
        }
        return (Learner) o;
    }

    /** For tests: forget the open copies. */
    static synchronized void reset() {
        OPEN.clear();
    }
}
