package ai.ibiko.create;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The 3D creation conversation: understands a request, researches when it
 * can, has the model design the thing with every measurement, builds and
 * checks the geometry from those numbers ({@link MeasuredDesign},
 * {@link MeasuredBuilder}), and tells the stage what the particles should do.
 * There are no built-in objects. Android-free; the slow part
 * ({@link #perform} with research) belongs off the UI thread.
 */
public final class CreationDirector {
    /** What the stage should do with the particles. */
    public enum Action { NONE, BECOME, CHANGED, RESTORE }

    public static final class Outcome {
        public final Action action;
        public final ObjectModel model;
        public final List<String> lines = new ArrayList<String>();
        /** A picture painted by the drawing model, when this turn asked for one. */
        public DrawingModel.Picture picture;
        /** A thought to show while working (research), or null. */
        public String thinking;

        Outcome(Action action, ObjectModel model) {
            this.action = action;
            this.model = model;
        }
    }

    private final ObjectSession session;
    private final ObjectStore store;
    private volatile ReferenceResearch.Fetcher fetcher;

    public CreationDirector(ObjectSession session, File dir) {
        this.session = session;
        this.store = new ObjectStore(dir);
    }

    /** Research through the server when online; null when not (models are then built from defaults, and say so). */
    public void setFetcher(ReferenceResearch.Fetcher f) {
        fetcher = f;
    }

    public boolean researchAvailable() {
        return fetcher != null;
    }

    private volatile MeasuredDesign.Brain designer;
    private volatile int designTokens = 1500;

    /**
     * The model that designs things: there are no built-in objects, so without it nothing is built. {@code tokens}: how
     * long its brief and its part lines may be.
     */
    public void setDesigner(MeasuredDesign.Brain brain, int tokens) {
        setDesigner(brain, tokens, false);
    }

    private volatile boolean capable;

    /** {@code capable}: an API model, given the full design request (an expanded specification, exact coordinates). */
    public void setDesigner(MeasuredDesign.Brain brain, int tokens, boolean capable) {
        designer = brain;
        designTokens = Math.max(600, tokens);
        this.capable = capable;
    }

    public boolean canDesign() {
        return designer != null;
    }

    /**
     * Is this a creation request? Fast; call on the UI thread. {@code objectShown}
     * says whether the particles are an object right now (edits and views only
     * apply then). Drawing stays with the drawing engine.
     */
    public ObjectRequestInterpreter.Request parse(String text, boolean objectShown) {
        String lower = ObjectRequestInterpreter.clean(text);
        if (lower.startsWith("draw") || lower.startsWith("sketch") || lower.startsWith("explain")) {
            return null;
        }
        ObjectRequestInterpreter.Request r = ObjectRequestInterpreter.interpret(text,
                objectShown && session.model() != null ? session.model().spec : null);
        return r;
    }

    /** Carries a request out. CREATE may read the web: run it off the UI thread. */
    public Outcome perform(ObjectRequestInterpreter.Request r) {
        switch (r.kind) {
            case CREATE: return create(r.spec);
            case MODIFY: return modify(r.spec, r.detail);
            case VIEW: {
                boolean parts = session.view(r.detail);
                Outcome o = new Outcome(parts ? Action.CHANGED : Action.NONE, session.model());
                o.lines.add(viewLine(r.detail));
                return o;
            }
            case UNDO:
            case REDO: {
                if (r.kind == ObjectRequestInterpreter.Kind.UNDO && session.undoReconstruction()) {
                    Outcome back = new Outcome(Action.CHANGED, session.model());
                    back.lines.add("The sparse reconstruction is off the board. What was showing before is back.");
                    return back;
                }
                boolean parts = r.kind == ObjectRequestInterpreter.Kind.UNDO ? session.undoParts() : session.redoParts();
                ObjectModel m = parts ? session.model() : r.kind == ObjectRequestInterpreter.Kind.UNDO ? session.undo() : session.redo();
                Outcome o = new Outcome(m == null ? Action.NONE : Action.CHANGED, m);
                o.lines.add(m == null ? "There's nothing to " + (r.kind == ObjectRequestInterpreter.Kind.UNDO ? "undo." : "redo.")
                        : (r.kind == ObjectRequestInterpreter.Kind.UNDO ? "Undone. " : "Redone. ") + (parts ? "The parts are as they were." : "Version " + m.version + "."));
                return o;
            }
            case PART:
                return part(r.detail);
            case INSPECT: {
                Outcome o = new Outcome(Action.NONE, session.model());
                o.lines.add(session.model() == null ? "There's no model on show." : SceneInventory.text(session.model(), session.selected()));
                return o;
            }
            case IMAGE: {
                DrawingModel.Picture picture = new DrawingModel().draw(r.detail);
                Outcome o = new Outcome(Action.NONE, session.model());
                o.picture = picture;
                o.lines.add("I drew a picture of " + picture.subject + " with the local drawing model. It is not a photograph and not a diffusion model.");
                return o;
            }
            case RECON: {
                Outcome o = new Outcome(Action.NONE, session.model());
                o.lines.add("Choose two or more overlapping photographs. I will triangulate the pixels you pick. I will not invent a mesh from this sentence. One photo is not a reconstruction, and this is not a dense scan.");
                return o;
            }
            case LIBRARY: {
                Outcome o = new Outcome(Action.NONE, session.model());
                o.lines.add(store.catalogue());
                return o;
            }
            case SAVE: {
                Outcome o = new Outcome(Action.NONE, session.model());
                if (session.model() != null && "reconstruction".equals(session.model().spec.category)) {
                    o.lines.add("I didn't save it. A sparse reconstruction is not a procedural model, and reopening the file would rebuild the wrong shape.");
                    return o;
                }
                try {
                    store.save(session.model());
                    o.lines.add("Saved the " + session.model().title + ", version " + session.model().version
                            + ". Say open my saved " + session.model().spec.category + " to bring it back.");
                } catch (IOException e) {
                    o.lines.add("I couldn't save it on this device.");
                }
                return o;
            }
            case EXPORT: {
                Outcome o = new Outcome(Action.NONE, session.model());
                try {
                    File f = store.exportObj(session.model());
                    o.lines.add("Exported the model as an OBJ file, " + f.getName() + ", in my files. It's a concept model, in metres.");
                } catch (IOException e) {
                    o.lines.add("I couldn't export it.");
                }
                return o;
            }
            case OPEN: {
                ObjectModel m;
                try {
                    m = session.adopt(store.open(r.detail));
                } catch (IOException | RuntimeException e) {
                    m = null;
                }
                Outcome o = new Outcome(m == null ? Action.NONE : Action.BECOME, m);
                o.lines.add(m == null ? "I don't have a saved " + r.detail + " yet." : "Here's your saved " + m.title + ", version " + m.version + ".");
                return o;
            }
            case RESTORE: {
                Outcome o = new Outcome(Action.RESTORE, null);
                o.lines.add("Coming back to myself.");
                return o;
            }
            case UNKNOWN_OBJECT:
            default: {
                Outcome o = new Outcome(Action.NONE, null);
                o.lines.add("Tell me what to build and its measurements, or let me work them out: say build a two-storey house"
                        + " with a 20 foot roof.");
                return o;
            }
        }
    }

    /**
     * A new thing, designed from its measurements: research (when online) and the model's design brief with every
     * measurement, its part lines, then Ibiko builds and checks the geometry, and one fix when it doesn't hold together.
     */
    private Outcome create(ObjectSpec spec) {
        MeasuredDesign.Brain brain = designer;
        String request = spec.str("request", spec.str("title", spec.category));
        if (brain == null) {
            Outcome o = new Outcome(Action.NONE, null);
            o.lines.add("I need the model to design it: there are no ready-made 3D objects. Ask me again when the model has loaded.");
            return o;
        }
        List<Provenance> sources = new ArrayList<Provenance>();
        String facts = null;
        ReferenceResearch.Fetcher f = fetcher;
        if (f != null) {
            ReferenceResearch.Findings found = ReferenceResearch.research(spec.str("title", spec.category), f, System.currentTimeMillis());
            if (found != null) {
                sources.addAll(found.sources);
                facts = found.summary;
            }
        }
        MeasuredDesign.Designed d = MeasuredDesign.design(brain, request, facts, designTokens, capable);
        if (d == null || !d.read.ok()) {
            Outcome o = new Outcome(Action.NONE, session.model());
            o.lines.add(d == null ? "The model didn't write a design. Try again, or give me the main measurements."
                    : "The design's measurements didn't add up: " + String.join("; ", d.read.errors.subList(0, Math.min(3, d.read.errors.size())))
                    + ". Give me the main measurements and I'll build it.");
            return o;
        }
        ObjectSpec designed = spec.with("design", d.lines, false).with("brief", d.brief, false);
        ObjectModel m = session.create(designed, sources);
        if (m == null) {
            // the geometry didn't hold together: the model corrects the lines once, with what was wrong
            MeasuredDesign.Designed fixed = MeasuredDesign.fix(brain, d.lines, session.lastProblems(), designTokens);
            if (fixed != null && fixed.read.ok()) {
                designed = designed.with("design", fixed.lines, false);
                m = session.create(designed, sources);
                d = new MeasuredDesign.Designed(d.brief, fixed.lines, fixed.read);
            }
        }
        if (m == null) {
            Outcome o = new Outcome(Action.NONE, session.model());
            o.lines.add("I couldn't build a valid model from the design: " + first(session.lastProblems()) + ".");
            return o;
        }
        Outcome o = new Outcome(Action.BECOME, m);
        boolean feet = MeasuredDesign.inFeet(request, d.lines);
        o.lines.add("Designed and built the " + m.title + " from its measurements: " + MeasuredDesign.summary(d.read, feet));
        List<String> brief = MeasuredDesign.keyLines(d.brief, 6);
        if (!brief.isEmpty()) {
            o.lines.add("The measurements: " + String.join("; ", brief) + ".");
        }
        if (!d.read.warnings.isEmpty()) {
            o.lines.add("To check: " + String.join("; ", d.read.warnings.subList(0, Math.min(3, d.read.warnings.size()))) + ".");
        }
        if (!sources.isEmpty()) {
            o.lines.add("I read " + sources.size() + (sources.size() == 1 ? " reference" : " references") + " for the sizes.");
        }
        o.lines.add("Tell me any measurement to change (“make the roof 25 feet”), or say show me the parts.");
        return o;
    }

    private Outcome part(String detail) {
        ObjectModel m = session.model();
        if (m == null || detail == null || !detail.contains("|")) {
            Outcome o = new Outcome(Action.NONE, m);
            o.lines.add("There's no model on show to edit.");
            return o;
        }
        String[] bits = detail.split("\\|", 2);
        boolean structure = !"select".equals(bits[0]);
        if (structure) {
            session.rememberParts();
        }
        SceneCommands.Report report = SceneCommands.apply(m, bits[0], bits[1]);
        if (structure && !report.applied) {
            session.dropLastPartMemory();
        }
        if ("select".equals(bits[0]) && report.applied) {
            session.select(report.componentId);
        }
        Outcome o = new Outcome(report.applied ? Action.CHANGED : Action.NONE, m);
        o.lines.add(report.speech);
        return o;
    }

    /** A change to the design's measurements, made by the model, then built again (the last valid version stays). */
    private Outcome modify(ObjectSpec spec, String said) {
        MeasuredDesign.Brain brain = designer;
        ObjectModel now = session.model();
        if (brain == null || now == null || !now.spec.has("design")) {
            Outcome o = new Outcome(Action.NONE, now);
            o.lines.add(brain == null ? "I need the model to change the design. Ask me again when it has loaded." : "There's no designed model on show to change.");
            return o;
        }
        String change = spec.str("change", said == null ? "" : said);
        MeasuredDesign.Designed d = MeasuredDesign.change(brain, now.spec.str("design", ""), change, designTokens);
        if (d == null || !d.read.ok()) {
            Outcome o = new Outcome(Action.NONE, now);
            o.lines.add("I couldn't make that change to the measurements" + (d == null || d.read.errors.isEmpty() ? "" : ": " + d.read.errors.get(0))
                    + ". I kept version " + now.version + ".");
            return o;
        }
        ObjectModel m = session.modify(now.spec.with("design", d.lines, false).with("change", change, false));
        if (m == null) {
            Outcome o = new Outcome(Action.NONE, session.model());
            o.lines.add("That change wouldn't make a valid model (" + first(session.lastProblems()) + "), so I kept version " + now.version + ".");
            return o;
        }
        Outcome o = new Outcome(Action.CHANGED, m);
        o.lines.add("Done. Version " + m.version + ": " + MeasuredDesign.summary(d.read, MeasuredDesign.inFeet(change, d.lines)));
        return o;
    }

    /** "That's the front left wheel: 66 centimetres across." */
    public String describe(Component c) {
        float[] b = c.bounds();
        float w = b[3] - b[0], h = b[4] - b[1], d = b[5] - b[2];
        return "That's the " + c.name + ": " + size(Math.max(w, d)) + " across and " + size(h) + " high.";
    }

    private static String size(float m) {
        return m >= 1f ? String.format(Locale.US, "%.1f metres", m) : Math.round(m * 100) + " centimetres";
    }

    private static String parts(ObjectModel m) {
        java.util.LinkedHashMap<String, Integer> kinds = new java.util.LinkedHashMap<String, Integer>();
        for (Component c : m.components) {
            kinds.merge(c.kind, 1, Integer::sum);
        }
        List<String> out = new ArrayList<String>();
        for (java.util.Map.Entry<String, Integer> e : kinds.entrySet()) {
            if (out.size() == 6) {
                break;
            }
            out.add(e.getValue() > 1 ? e.getValue() + " " + plural(e.getKey()) : e.getKey());
        }
        return String.join(", ", out);
    }

    private static String plural(String k) {
        return k.endsWith("s") ? k : k.endsWith("y") ? k.substring(0, k.length() - 1) + "ies" : k + "s";
    }

    private static String viewLine(String v) {
        switch (v) {
            case "interior": return "Here's the inside, with the roof and front taken away.";
            case "plan": return "Here's the floor plan, from above.";
            case "exterior": return "The whole outside again.";
            case "rotate": return "Turning it round.";
            case "zoom in": return "Closer.";
            case "zoom out": return "Further out.";
            default: return "The " + v + " view.";
        }
    }

    private static String first(List<String> l) {
        return l == null || l.isEmpty() ? "it didn't validate" : l.get(0);
    }
}
