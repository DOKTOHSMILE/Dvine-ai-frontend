package ai.ibiko.create;

import java.util.ArrayList;
import java.util.List;

/**
 * Ibiko's builder: turns a design's measured part lines (kept in its spec as "design", written by the model from the
 * user's request, see {@link MeasuredDesign}) into a 3D model in metres, and checks it. There are no built-in
 * objects; a spec without a design builds nothing and says so. The same lines always build the same model, so undo,
 * redo, save and reopen rebuild exactly. Pure Java.
 */
public final class MeasuredBuilder {
    private MeasuredBuilder() {
    }

    public static ObjectModel build(ObjectSpec s, int version, List<Provenance> provenance) {
        String lines = s.str("design", "");
        MeasuredDesign.Read r = MeasuredDesign.read(lines);
        List<Component> parts = r.errors.isEmpty() ? MeasuredDesign.build(r) : new ArrayList<Component>();
        List<Provenance> prov = new ArrayList<Provenance>(provenance == null ? new ArrayList<Provenance>() : provenance);
        if (prov.isEmpty()) {
            prov.add(new Provenance("design", "measurements designed by the model from the request", System.currentTimeMillis(),
                    "own work", "sizes the user gave are used as given; the rest are the model's standard sizes"));
        }
        ObjectModel m = new ObjectModel(s, version, s.str("title", s.category), parts, prov);
        if (lines.trim().isEmpty()) {
            m.problems.add("there is no design to build: the model describes it with its measurements first");
        }
        m.problems.addAll(r.errors);
        if (!parts.isEmpty()) {
            m.problems.addAll(GeometryValidator.validate(m));
        }
        return m;
    }
}
