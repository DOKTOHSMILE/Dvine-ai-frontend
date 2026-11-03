package ai.dvine.create;

import java.util.ArrayList;

/** Measured designs as the model writes them, for the tests: there are no built-in objects to build from. */
final class Designs {
    private Designs() {
    }

    /** "A two-storey building with a 20 foot roof", in feet, on its land. */
    static final String HOUSE = "UNITS: ft\n"
            + "land | box | grass | 80 x 0.5 x 100 | at 0, 0, 0\n"
            + "foundation | box | concrete | 42 x 1 x 32 | at 0, 0.5, 0\n"
            + "ground floor front wall | box | white | 40 x 10 x 0.8 | at 0, 1.5, 15.6\n"
            + "ground floor back wall | box | white | 40 x 10 x 0.8 | at 0, 1.5, -15.6\n"
            + "ground floor left wall | box | white | 0.8 x 10 x 30.4 | at -19.6, 1.5, 0\n"
            + "ground floor right wall | box | white | 0.8 x 10 x 30.4 | at 19.6, 1.5, 0\n"
            + "first floor slab | box | concrete | 40 x 0.8 x 32 | at 0, 11.5, 0\n"
            + "first floor front wall | box | white | 40 x 10 x 0.8 | at 0, 12.3, 15.6\n"
            + "first floor back wall | box | white | 40 x 10 x 0.8 | at 0, 12.3, -15.6\n"
            + "first floor left wall | box | white | 0.8 x 10 x 30.4 | at -19.6, 12.3, 0\n"
            + "first floor right wall | box | white | 0.8 x 10 x 30.4 | at 19.6, 12.3, 0\n"
            + "front window | box | glass | 4 x 5 x 0.9 | at -12, 4.5, 15.6 | repeat 3 every 12 along x\n"
            + "front door | box | wood | 3.5 x 7 x 0.9 | at 6, 1.5, 15.6\n"
            + "first floor window | box | glass | 4 x 5 x 0.9 | at -12, 15.3, 15.6 | repeat 4 every 8 along x\n"
            + "roof | gable roof | red | 42 x 20 x 32 | at 0, 22.3, 0\n";

    static final String CAR = "UNITS: m\n"
            + "body | box | red | 1.8 x 0.6 x 4.4 | at 0, 0.35, 0\n"
            + "cabin | box | glass | 1.6 x 0.55 x 2.2 | at 0, 0.95, -0.2\n"
            + "front left wheel | cylinder-x | rubber | 0.25 x 0.66 x 0.66 | at -0.85, 0, 1.4\n"
            + "front right wheel | cylinder-x | rubber | 0.25 x 0.66 x 0.66 | at 0.85, 0, 1.4\n"
            + "rear left wheel | cylinder-x | rubber | 0.25 x 0.66 x 0.66 | at -0.85, 0, -1.4\n"
            + "rear right wheel | cylinder-x | rubber | 0.25 x 0.66 x 0.66 | at 0.85, 0, -1.4\n";

    static final String MOTORCYCLE = "UNITS: m\n"
            + "front tyre | cylinder-x | rubber | 0.12 x 0.64 x 0.64 | at 0, 0, 0.7\n"
            + "rear tyre | cylinder-x | rubber | 0.15 x 0.64 x 0.64 | at 0, 0, -0.7\n"
            + "front rim | cylinder-x | metal | 0.13 x 0.44 x 0.44 | at 0, 0.1, 0.7\n"
            + "rear rim | cylinder-x | metal | 0.16 x 0.44 x 0.44 | at 0, 0.1, -0.7\n"
            + "front fork | box | metal | 0.08 x 0.6 x 0.08 | at 0, 0.32, 0.62\n"
            + "frame | box | metal | 0.2 x 0.25 x 1.2 | at 0, 0.45, 0\n"
            + "seat | box | leather | 0.25 x 0.08 x 0.5 | at 0, 0.7, -0.2\n";

    static ObjectSpec spec(String category, String lines) {
        return ObjectSpec.of(category).with("title", category, true).with("design", lines, false);
    }

    static ObjectSpec house() {
        return spec("building", HOUSE).with("title", "two-storey building", true);
    }

    static ObjectSpec car() {
        return spec("car", CAR);
    }

    static ObjectSpec motorcycle() {
        return spec("motorcycle", MOTORCYCLE);
    }

    static ObjectModel build(ObjectSpec s) {
        ObjectModel m = MeasuredBuilder.build(s, 1, new ArrayList<Provenance>());
        if (!m.valid()) {
            throw new AssertionError(m.problems.toString());
        }
        return m;
    }

    /** A model that answers the design steps the way the phone's model does: the brief, then the part lines. */
    static MeasuredDesign.Brain brain(String brief, String lines, java.util.List<String> asked) {
        return (system, prompt, max) -> {
            if (asked != null) {
                asked.add(system.substring(0, Math.min(40, system.length())) + " :: " + prompt);
            }
            return system.startsWith("You are DVINE's designer") ? brief : lines;
        };
    }

    static final String BRIEF = "Two-storey building, 40 ft wide x 30 ft deep\n- Each storey 10 ft high\n- Roof: gable, 20 ft high (as asked)\n"
            + "- 3 ground-floor windows, 4 ft x 5 ft (assumed)\n- 4 first-floor windows, 4 ft x 5 ft (assumed)\n- Front door 3.5 ft x 7 ft (assumed)\n"
            + "- Land 80 ft x 100 ft (assumed)";
}
