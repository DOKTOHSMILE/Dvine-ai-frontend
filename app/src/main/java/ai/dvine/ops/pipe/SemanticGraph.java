package ai.dvine.ops.pipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Facts about a scene stay on that scene. A correction is a candidate note.
 * It does not rewrite general knowledge unless {@link #approveGeneral} is called on purpose.
 */
public final class SemanticGraph {
    public enum Relation {
        PART_OF, CONNECTED_TO, CONTAINS, ATTACHED_TO, INSTANCE_OF, DEPENDS_ON, LOCATED_AT, CAN_PERFORM, REQUIRES
    }

    public static final class Edge {
        public final String scope;
        public final String subject;
        public final Relation relation;
        public final String object;
        public final String provenance;

        public Edge(String scope, String subject, Relation relation, String object, String provenance) {
            this.scope = scope;
            this.subject = subject;
            this.relation = relation;
            this.object = object;
            this.provenance = provenance;
        }
    }

    public static final class Correction {
        public final String context;
        public final String phrase;
        public final String means;

        public Correction(String context, String phrase, String means) {
            this.context = context;
            this.phrase = phrase;
            this.means = means;
        }
    }

    private final List<Edge> edges = new ArrayList<Edge>();
    private final Map<String, Correction> corrections = new LinkedHashMap<String, Correction>();
    private final Map<String, String> general = new LinkedHashMap<String, String>();

    public void observe(String scope, String subject, Relation relation, String object, String provenance) {
        if (blank(scope) || blank(subject) || relation == null || blank(object)) {
            return;
        }
        edges.add(new Edge(scope, subject, relation, object, provenance == null ? "" : provenance));
    }

    public List<Edge> related(String scope, String subject, Relation relation) {
        List<Edge> out = new ArrayList<Edge>();
        for (Edge e : edges) {
            if (e.scope.equals(scope) && e.subject.equals(subject) && (relation == null || e.relation == relation)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Stored as a candidate. General knowledge is unchanged. */
    public void correct(String context, String phrase, String means) {
        if (blank(context) || blank(phrase) || blank(means)) {
            return;
        }
        corrections.put(key(context, phrase), new Correction(context, phrase, means));
    }

    public String correction(String context, String phrase) {
        Correction c = corrections.get(key(context, phrase));
        return c == null ? null : c.means;
    }

    public void forgetCorrection(String context, String phrase) {
        corrections.remove(key(context, phrase));
    }

    public String general(String key) {
        return general.get(norm(key));
    }

    public void approveGeneral(String key, String value) {
        if (blank(key) || value == null) {
            return;
        }
        general.put(norm(key), value);
    }

    public int corrections() {
        return corrections.size();
    }

    private static String key(String context, String phrase) {
        return norm(context) + "|" + norm(phrase);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).trim();
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
