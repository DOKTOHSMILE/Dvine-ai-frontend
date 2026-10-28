package ai.dvine.ops.pipe;

import java.util.List;
import java.util.Locale;

public final class SemanticRelationshipEngine {
    public void preserve(String n, List<String> into) {
        String l = n.toLowerCase(Locale.US);
        int cut = earliest(l, " but ", " without ", " except ", " do not ", " don't ", " leave the ", " keep the ");
        if (cut < 0) {
            return;
        }
        String tail = l.substring(cut);
        if (tail.contains("fork")) {
            into.add("front_fork");
        }
        if (tail.contains("brake")) {
            into.add("front_brake");
        }
        if (tail.contains("rim")) {
            into.add(tail.contains("left") ? "front_left_rim" : "front_rim");
        }
        if (tail.contains("window")) {
            into.add("windows");
        }
        if (tail.contains("rest") || tail.contains("untouched") || tail.contains("anything else") || tail.contains("don't touch") || tail.contains("do not touch")) {
            into.add("unrelated_geometry");
        }
    }

    private static int earliest(String l, String... marks) {
        int best = -1;
        for (String m : marks) {
            int i = l.indexOf(m);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }
}
