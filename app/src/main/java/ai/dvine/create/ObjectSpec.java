package ai.dvine.create;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.dvine.brain.MiniJson;

/**
 * A structured description of the object to build: its category, the
 * parameters that shape it (dimensions in metres, counts, switches, styles,
 * colours) and which of those the user actually said (explicit) as opposed
 * to defaults the engine assumed, which are listed so they can be told.
 * Immutable: a change makes a new spec (that is what undo walks back over).
 */
public final class ObjectSpec {
    /** CONCEPT: fast approximate shapes. DETAILED: more parts and closer proportions. Neither is engineering-verified. */
    public enum Quality { CONCEPT, DETAILED }

    public final String category;
    public final Quality quality;
    private final Map<String, String> params;
    private final Set<String> explicit;

    public ObjectSpec(String category, Quality quality, Map<String, String> params, Set<String> explicit) {
        this.category = category;
        this.quality = quality;
        this.params = new LinkedHashMap<String, String>(params);
        this.explicit = new LinkedHashSet<String>(explicit);
    }

    public static ObjectSpec of(String category) {
        return new ObjectSpec(category, Quality.CONCEPT, new LinkedHashMap<String, String>(), new LinkedHashSet<String>());
    }

    public float num(String key, float fallback) {
        String v = params.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int count(String key, int fallback) {
        return Math.round(num(key, fallback));
    }

    public boolean flag(String key) {
        return "true".equals(params.get(key));
    }

    public String str(String key, String fallback) {
        String v = params.get(key);
        return v == null ? fallback : v;
    }

    public boolean has(String key) {
        return params.containsKey(key);
    }

    public boolean isExplicit(String key) {
        return explicit.contains(key);
    }

    /** A copy with one parameter set; {@code said} marks it as the user's own requirement. */
    public ObjectSpec with(String key, Object value, boolean said) {
        Map<String, String> p = new LinkedHashMap<String, String>(params);
        Set<String> e = new LinkedHashSet<String>(explicit);
        p.put(key, String.valueOf(value).toLowerCase(Locale.US));
        if (said) {
            e.add(key);
        } else {
            e.remove(key);
        }
        return new ObjectSpec(category, quality, p, e);
    }

    public ObjectSpec withQuality(Quality q) {
        return new ObjectSpec(category, q, params, explicit);
    }

    public Map<String, String> params() {
        return new LinkedHashMap<String, String>(params);
    }

    /** Parameters the user did not give (the defaults used), for "I assumed …". */
    public List<String> assumptions() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!explicit.contains(e.getKey())) {
                out.add(e.getKey() + " " + e.getValue());
            }
        }
        return out;
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("category", category);
        m.put("quality", quality.name());
        m.put("params", new LinkedHashMap<String, Object>(params));
        m.put("explicit", new ArrayList<Object>(explicit));
        return MiniJson.write(m);
    }

    public static ObjectSpec fromJson(String json) {
        Map<String, Object> m = MiniJson.obj(MiniJson.parse(json));
        Map<String, String> p = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> e : MiniJson.obj(m.get("params")).entrySet()) {
            p.put(e.getKey(), MiniJson.str(e.getValue(), ""));
        }
        Quality q;
        try {
            q = Quality.valueOf(MiniJson.str(m.get("quality"), "CONCEPT"));
        } catch (IllegalArgumentException bad) {
            q = Quality.CONCEPT;
        }
        return new ObjectSpec(MiniJson.str(m.get("category"), "box"), q, p, new LinkedHashSet<String>(MiniJson.strings(m.get("explicit"))));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ObjectSpec && toJson().equals(((ObjectSpec) o).toJson());
    }

    @Override
    public int hashCode() {
        return toJson().hashCode();
    }
}
