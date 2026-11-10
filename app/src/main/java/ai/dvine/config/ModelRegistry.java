package ai.dvine.config;

import android.content.Context;

import java.util.Locale;

/**
 * Which agent a persona is, and how many steps that agent may take. Obi reads, Ivie reviews, Ibiko writes.
 * A missing row falls back to Obi so a run still starts.
 */
public final class ModelRegistry {
    public static final class Agent {
        public final String id;
        public final String name;
        public final int maxSteps;

        public Agent(String id, String name, int maxSteps) {
            this.id = id;
            this.name = name;
            this.maxSteps = maxSteps;
        }
    }

    private static final Agent OBI = new Agent("obi", "DVINE (reader)", 20);
    private static final Agent IVIE = new Agent("ivie", "DVINE (reviewer)", 20);
    private static final Agent IBIKO = new Agent("ibiko", "DVINE", 40);

    private ModelRegistry() {
    }

    public static ModelRegistry of(Context context) {
        return new ModelRegistry();
    }

    public static String agentIdFor(String persona) {
        String p = persona == null ? "" : persona.trim().toLowerCase(Locale.US);
        if (p.startsWith("ibiko") || p.startsWith("yibiku")) {
            return "ibiko";
        }
        if (p.startsWith("ivie") || p.startsWith("evie")) {
            return "ivie";
        }
        return "obi";
    }

    public Agent agent(String id) {
        if ("ibiko".equals(id)) {
            return IBIKO;
        }
        if ("ivie".equals(id)) {
            return IVIE;
        }
        return OBI;
    }
}
