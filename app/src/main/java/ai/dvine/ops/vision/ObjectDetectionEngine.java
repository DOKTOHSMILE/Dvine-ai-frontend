package ai.dvine.ops.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Without an installed detector this reports filename hints as uncertain, never as confirmed sightings.
 */
public final class ObjectDetectionEngine {
    public static final class Hit {
        public final String label;
        public final String certainty;

        public Hit(String label, String certainty) {
            this.label = label;
            this.certainty = certainty;
        }
    }

    public List<Hit> detect(String filename, boolean modelLoaded) {
        List<Hit> hits = new ArrayList<Hit>();
        String n = filename == null ? "" : filename.toLowerCase(Locale.US);
        String[] known = {"motorcycle", "bike", "car", "chair", "person", "wheel"};
        for (String k : known) {
            if (n.contains(k)) {
                hits.add(new Hit(k.equals("bike") ? "motorcycle" : k, modelLoaded ? "likely" : "uncertain"));
            }
        }
        if (!modelLoaded && hits.isEmpty()) {
            hits.add(new Hit("unknown", "uncertain"));
        }
        return hits;
    }
}
