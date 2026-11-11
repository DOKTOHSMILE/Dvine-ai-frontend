package ai.ibiko.ibiko1.comprehend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Common sense, offline: ConceptNet 5.7 (CC BY-SA 4.0), English only, kept as knowledge/concepts.tsv and read by
 * {@link SortedTable}. What a thing is (IsA), what it is for (UsedFor), what it can do (CapableOf), its parts (HasPart,
 * from PartOf), what it has (HasA), where it is found (AtLocation), what it is made of (MadeOf), what it causes, what it
 * needs first, what people want, and so on, strongest first. Pure Java.
 */
public final class Concepts {
    /** One link: the concept, the relation, the other end. */
    public static final class Link {
        public final String from, relation, to;

        Link(String[] c) {
            from = c[0];
            relation = c.length > 1 ? c[1] : "";
            to = c.length > 2 ? c[2] : "";
        }
    }

    private final SortedTable table;

    public Concepts(SortedTable table) {
        this.table = table;
    }

    public boolean present() {
        return table != null && table.present();
    }

    /** Every link from a concept (lower case; its base form tried too), strongest first. */
    public List<Link> links(String concept) {
        List<Link> out = new ArrayList<Link>();
        if (table == null || concept == null) {
            return out;
        }
        for (String base : Dictionary.bases(concept)) {
            for (String[] c : table.rows(base.toLowerCase(Locale.US))) {
                out.add(new Link(c));
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    /** The other ends of one relation ("UsedFor", "HasPart", "IsA" …), strongest first, at most {@code most}. */
    public List<String> of(String concept, String relation, int most) {
        List<String> out = new ArrayList<String>();
        for (Link l : links(concept)) {
            if (l.relation.equals(relation) && !out.contains(l.to)) {
                out.add(l.to);
                if (out.size() >= most) {
                    break;
                }
            }
        }
        return out;
    }

    /** Whether {@code part} is a part of {@code whole} (a tyre of a car), by either direction of the link. */
    public boolean partOf(String part, String whole) {
        for (String b : Dictionary.bases(whole)) {
            List<String> parts = of(b, "HasPart", 60);
            parts.addAll(of(b, "HasA", 60)); // "a car has four tyres"
            for (String p : parts) {
                for (String pb : Dictionary.bases(part)) {
                    if (p.equals(pb) || p.endsWith(" " + pb)) {
                        return true;
                    }
                }
            }
        }
        for (String pb : Dictionary.bases(part)) {
            for (String w : of(pb, "PartOf", 30)) {
                if (Dictionary.bases(whole).contains(w)) {
                    return true;
                }
            }
        }
        return false;
    }
}
