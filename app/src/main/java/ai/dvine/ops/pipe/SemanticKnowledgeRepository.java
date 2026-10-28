package ai.dvine.ops.pipe;

import java.util.LinkedHashMap;
import java.util.Map;

/** Corrections are structured notes, not weight updates, and they stay tied to a context. */
public final class SemanticKnowledgeRepository {
    public static final class Entry {
        public final String context;
        public final String phrase;
        public final String means;
        public int uses;

        public Entry(String context, String phrase, String means) {
            this.context = context;
            this.phrase = phrase;
            this.means = means;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

    public void learn(String context, String phrase, String means) {
        if (context == null || phrase == null || means == null) {
            return;
        }
        entries.put(key(context, phrase), new Entry(context, phrase, means));
    }

    public String correction(String context, String phrase) {
        Entry e = entries.get(key(context, phrase));
        if (e == null) {
            return null;
        }
        e.uses++;
        return e.means;
    }

    public void forget(String context, String phrase) {
        entries.remove(key(context, phrase));
    }

    public int size() {
        return entries.size();
    }

    private static String key(String context, String phrase) {
        return context + "|" + phrase;
    }
}
