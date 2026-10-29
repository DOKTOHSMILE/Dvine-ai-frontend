package ai.dvine.create;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The 3D creation conversation: understands a request, researches when it
 * can, builds and validates the geometry, and tells the stage what the
 * particles should do, with honest words (what was assumed, that it is a
 * concept model, where references came from). Android-free; the slow part
 * ({@link #perform} with research) belongs off the UI thread.
 */
public final class CreationDirector {
    /** What the stage should do with the particles. */
    public enum Action { NONE, BECOME, CHANGED, RESTORE }

    public static final class Outcome {
        public final Action action;
        public final ObjectModel model;
        public final List<String> lines = new ArrayList<String>();
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

    /**
     * Is this a creation request? Fast; call on the UI thread. {@code objectShown}
     * says whether the particles are an object right now (edits and views only
     * apply then). Drawing and the built-in 3D figures ("draw a galaxy", "show
     * me a cube") stay with the drawing engine.
     */
    public ObjectRequestInterpreter.Request parse(String text, boolean objectShown) {
        String lower = ObjectRequestInterpreter.clean(text);
        if (lower.startsWith("draw") || lower.startsWith("sketch") || lower.startsWith("explain")) {
            return null;
        }
        ObjectRequestInterpreter.Request r = ObjectRequestInterpreter.interpret(text,
                objectShown && session.model() != null ? session.model().spec : null);
        if (r != null && r.kind == ObjectRequestInterpreter.Kind.UNKNOWN_OBJECT && ai.dvine.render.forms.Forms.objectFor(lower) != null) {
            return null; // "build a cube", "show me an atom": the built-in figures
        }
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
                boolean parts = r.kind == ObjectRequestInterpreter.Kind.UNDO ? session.undoParts() : session.redoParts();
                ObjectModel m = parts ? session.model() : r.kind == ObjectRequestInterpreter.Kind.UNDO ? session.undo() : session.redo();
                Outcome o = new Outcome(m == null ? Action.NONE : Action.CHANGED, m);
                o.lines.add(m == null ? "There's nothing to " + (r.kind == ObjectRequestInterpreter.Kind.UNDO ? "undo." : "redo.")
                        : (r.kind == ObjectRequestInterpreter.Kind.UNDO ? "Undone. " : "Redone. ") + (parts ? "The parts are as they were." : "Version " + m.version + "."));
                return o;
            }
            case PART:
                return part(r.detail);
            case IMAGE: {
                Outcome o = new Outcome(Action.NONE, session.model());
                o.lines.add("I don't have an image-generation model in this build, so I did not make a picture. Say build a motorcycle if you want an editable concept model.");
                return o;
            }
            case RECON: {
                Outcome o = new Outcome(Action.NONE, session.model());
                o.lines.add("Several photographs are not a reconstruction in this build. There is no photogrammetry backend on this phone, so I did not invent a measured mesh.");
                return o;
            }
            case SAVE: {
                Outcome o = new Outcome(Action.NONE, session.model());
                try {
                    store.save(session.model());
                    o.lines.add("Saved the " + session.model().title + ", version " + session.model().version
                            + ". Say open my saved " + session.model().spec.category + " to bring it back.");
                } catch (IOException e) {
                    o.lines.add("I couldn't save it on this phone.");
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
                String what = r.detail == null ? "that" : r.detail;
                o.lines.add("I can't model " + what + " properly yet. I can build houses, cars, motorcycles, tables, chairs, sofas, beds, shelves, lamps, mugs, "
                        + "bottles, laptops, phones, trees and bicycles, or something made of shapes: say build a " + firstWord(what)
                        + " from a box and a sphere.");
                return o;
            }
        }
    }

    private Outcome create(ObjectSpec spec) {
        List<Provenance> sources = new ArrayList<Provenance>();
        String summary = null;
        ReferenceResearch.Fetcher f = fetcher;
        if (f != null) {
            ReferenceResearch.Findings found = ReferenceResearch.research(spec.category, f, System.currentTimeMillis());
            if (found != null) {
                sources.addAll(found.sources);
                summary = found.summary;
            }
        }
        ObjectModel m = session.create(spec, sources);
        if (m == null) {
            Outcome o = new Outcome(Action.NONE, null);
            o.lines.add("I couldn't build a valid model of that: " + first(session.lastProblems()) + ".");
            return o;
        }
        Outcome o = new Outcome(Action.BECOME, m);
        o.lines.add("Building a " + m.title + ": " + parts(m) + ".");
        List<String> assumed = assumptions(spec);
        if (!assumed.isEmpty()) {
            o.lines.add("I assumed " + String.join(", ", assumed) + ". Tell me to change anything.");
        }
        if (summary != null) {
            o.lines.add("From my reading: " + summary);
        } else if (spec.category.matches("house|car|bicycle|motorcycle")) {
            o.lines.add("I couldn't look up references just now, so this uses typical proportions.");
        }
        if (spec.category.equals("house")) {
            o.lines.add("It's a visual concept, not a structural design.");
        } else if (spec.category.equals("car")) {
            o.lines.add("It's a visual model, not an engineering design.");
        } else if (spec.category.equals("motorcycle")) {
            o.lines.add("It's a procedural concept model with separate parts, not a measurement and not a rig.");
        }
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
        session.rememberParts();
        SceneCommands.Report report = SceneCommands.apply(m, bits[0], bits[1]);
        if (!report.applied) {
            session.dropLastPartMemory();
        }
        Outcome o = new Outcome(report.applied ? Action.CHANGED : Action.NONE, m);
        o.lines.add(report.speech);
        return o;
    }

    private Outcome modify(ObjectSpec spec, String said) {
        ObjectModel m = session.modify(spec);
        if (m == null) {
            Outcome o = new Outcome(Action.NONE, session.model());
            o.lines.add("That change wouldn't make a valid model (" + first(session.lastProblems()) + "), so I kept version "
                    + (session.model() == null ? 1 : session.model().version) + ".");
            return o;
        }
        Outcome o = new Outcome(Action.CHANGED, m);
        o.lines.add("Done. Version " + m.version + ": " + m.title + ".");
        return o;
    }

    /** The defaults used, in words: "2 floors, modern style, 12 metres wide, a flat roof". */
    static List<String> assumptions(ObjectSpec s) {
        List<String> out = new ArrayList<String>();
        if (s.category.equals("shapes") || s.category.equals("progress")) {
            return out;
        }
        for (String key : s.params().keySet()) {
            if (s.isExplicit(key) || out.size() == 5) {
                continue;
            }
            String v = s.str(key, "");
            switch (key) {
                case "floors": out.add(v + (v.equals("1") ? " floor" : " floors")); break;
                case "style": out.add(v + " style"); break;
                case "roof": out.add("a " + v + " roof"); break;
                case "width": out.add(size(s.num(key, 0f)) + " wide"); break;
                case "depth": out.add(size(s.num(key, 0f)) + " deep"); break;
                case "length": out.add(size(s.num(key, 0f)) + " long"); break;
                case "height": out.add(size(s.num(key, 0f)) + " high"); break;
                case "color": out.add(v); break;
                case "shape": out.add("a " + v + " shape"); break;
                default: break;
            }
        }
        return out;
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

    private static String firstWord(String s) {
        String[] w = s.trim().split(" ");
        return w.length == 0 ? "thing" : w[w.length - 1];
    }
}
