package ai.dvine.dvine1;

import ai.dvine.dvine1.learner.Learner;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * One copy of each of DVINE 1's memory files for the whole app. The chat, the learning session (a job in the same
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

    public static synchronized Knowledge knowledge(File dir) {
        String k = key(dir, "knowledge.tsv");
        Object o = OPEN.get(k);
        if (o == null) {
            o = new Knowledge(new File(dir, "knowledge.tsv"));
            OPEN.put(k, o);
        }
        return (Knowledge) o;
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
