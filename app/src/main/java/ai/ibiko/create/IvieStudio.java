package ai.ibiko.create;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.ibiko.llm.DrawPrompt;
import ai.ibiko.render.field.Gltf;
import ai.ibiko.render.field.MeshExport;
import ai.ibiko.render.field.PartEdit;
import ai.ibiko.render.field.SolidSpec;

/**
 * Ivie's 3D studio for anything: the model plans a thing as named parts ("body | oval | red | middle centre | large",
 * {@link DrawPrompt}), the parts are built into a 3D shape ({@link SolidSpec}), and then it is worked on by name:
 * "take out the tyres", "make the wheels black", "make the roof bigger", "add a spoiler on the back", "show only
 * the engine", "put the tyres back", "undo" ({@link PartEdit}); and saved as a file other 3D software opens: GLB,
 * OBJ (with its MTL), STL for 3D printing, or PLY ({@link MeshExport}). Any model can do this, a small one on the
 * phone or one behind an API: it only writes lines of words. Pure Java; the model is behind {@link Model}.
 */
public final class IvieStudio {
    /** The model: instructions and a request in, what it wrote out (null when it failed). */
    public interface Model {
        String ask(String system, String prompt, int maxTokens);
    }

    /** What a step did: the shape now (null when nothing changed) and what to say. */
    public static final class Result {
        public final SolidSpec spec;
        public final String line;

        Result(SolidSpec spec, String line) {
            this.spec = spec;
            this.line = line;
        }
    }

    /** A file to save: its name, type and bytes (an OBJ comes with its MTL as {@code extra}). */
    public static final class Export {
        public final String name, mime;
        public final byte[] bytes;
        public final Export extra;
        public final MeshExport.Format format;

        Export(String name, String mime, byte[] bytes, Export extra, MeshExport.Format format) {
            this.name = name;
            this.mime = mime;
            this.bytes = bytes;
            this.extra = extra;
            this.format = format;
        }
    }

    private final Model model;
    private SolidSpec spec;
    private String subject;
    private final List<SolidSpec.Part> removed = new ArrayList<SolidSpec.Part>();
    private final List<SolidSpec> undo = new ArrayList<SolidSpec>();
    private final List<SolidSpec> redo = new ArrayList<SolidSpec>();

    public IvieStudio(Model model) {
        this.model = model;
    }

    /** Whether there is a model-drawn thing on show to edit or export. */
    public boolean has() {
        return spec != null && !spec.isEmpty();
    }

    public SolidSpec spec() {
        return spec;
    }

    public String subject() {
        return subject;
    }

    /** Forgets the thing (another kind of picture is on show now). */
    public void clear() {
        spec = null;
        subject = null;
        removed.clear();
        undo.clear();
        redo.clear();
    }

    // ------------------------------------------------------------ what the words ask for

    private static final String EXPORT = "(?s).*\\b(export|download|save (it |this |the model |the drawing )?(as|to|in)|send me the (file|model|mesh)"
            + "|as an? (glb|gltf|obj|stl|ply)( file)?|in (glb|gltf|obj|stl|ply)( format)?|for (3d )?print(ing)?|to blender|for blender|file of it)\\b.*";

    /** "export it as an STL", "save it for 3D printing", "send me the file". */
    public static boolean wantsExport(String said) {
        return said != null && said.toLowerCase(Locale.US).matches(EXPORT);
    }

    /** "undo" / "redo" (and "go back"). */
    public static String undoWord(String said) {
        String t = said == null ? "" : said.trim().toLowerCase(Locale.US).replaceAll("[.!?]+$", "");
        if (t.matches("^(undo|undo that|go back|take that back|undo the last change)$")) {
            return "undo";
        }
        return t.matches("^(redo|redo that)$") ? "redo" : null;
    }

    /** What to draw in "draw me a dragon", "build a guitar in 3d", "make a 3d model of a phoenix"; null otherwise. */
    public static String drawSubject(String said) {
        if (said == null) {
            return null;
        }
        String t = said.trim().toLowerCase(Locale.US).replaceAll("[.!?]+$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^(?:please |can you |could you )?(?:make|build|create|model|design|sculpt)(?: me)? (?:a |an )?3d (?:model |version )?of (?:a |an |the )?(.+)$").matcher(t);
        if (m.matches()) {
            return m.group(1).trim();
        }
        String s = DrawPrompt.subject(said);
        if (s == null) {
            s = DrawPrompt.formSubject(said);
        }
        return s;
    }

    // ------------------------------------------------------------ drawing

    /** The model plans the thing's parts; a thin or wrong plan is asked again once. */
    public Result draw(String what, String facts, int maxTokens) {
        String written = model.ask(DrawPrompt.system(), DrawPrompt.user(what, facts), maxTokens);
        SolidSpec plan = written == null ? null : DrawPrompt.read(written, what);
        if (!DrawPrompt.usable(plan, what)) {
            written = model.ask(DrawPrompt.system(), DrawPrompt.retry(what), maxTokens);
            plan = written == null ? null : DrawPrompt.read(written, what);
        }
        if (!DrawPrompt.usable(plan, what)) {
            return new Result(null, written == null ? "The model didn't answer, so I couldn't plan " + DrawPrompt.article(what) + "."
                    : "I couldn't get a good plan of " + DrawPrompt.article(what) + " from the model. Try describing it, or a simpler thing.");
        }
        clear();
        spec = plan.fixFrame();
        subject = what;
        return new Result(spec, "Here's " + DrawPrompt.article(what) + " in 3D: " + PartEdit.inventory(spec) + ". You can change a part ("
                + "“make the " + PartEdit.example(spec) + " bigger”, “take out the " + PartEdit.example(spec)
                + "”), add one, or export it as GLB, OBJ, STL or PLY.");
    }

    // ------------------------------------------------------------ editing parts

    /** A change to the parts of what is on show; null when the words aren't one (or nothing is on show). */
    public Result edit(String said, int maxTokens) {
        if (!has()) {
            return null;
        }
        String u = undoWord(said);
        if (u != null) {
            List<SolidSpec> from = u.equals("undo") ? undo : redo, to = u.equals("undo") ? redo : undo;
            if (from.isEmpty()) {
                return new Result(null, "There's nothing to " + u + ".");
            }
            to.add(spec);
            spec = from.remove(from.size() - 1);
            return new Result(spec, u.equals("undo") ? "Undone." : "Redone.");
        }
        PartEdit e = PartEdit.parse(said);
        if (e == null) {
            return null;
        }
        if (e.op == PartEdit.Op.ADD) {
            String lines = model.ask(DrawPrompt.system(), PartEdit.addPrompt(spec, subject, e.target), maxTokens);
            SolidSpec more = lines == null ? null : spec.extend(lines);
            if (more == null || more.parts.size() <= spec.parts.size()) {
                return new Result(null, "I couldn't work out how to add " + e.target + ".");
            }
            List<SolidSpec.Part> added = more.parts.subList(spec.parts.size(), more.parts.size());
            String names = PartEdit.inventory(new ArrayList<SolidSpec.Part>(added));
            change(more);
            return new Result(spec, "Added " + names + ".");
        }
        if (e.op == PartEdit.Op.RESTORE) {
            List<SolidSpec.Part> back = PartEdit.pick(removed, e.target);
            if (back.isEmpty()) {
                return new Result(null, removed.isEmpty() ? "Nothing has been taken out." : "I took out " + PartEdit.inventory(removed)
                        + "; which of those?");
            }
            SolidSpec out = spec.copy();
            out.parts.addAll(back);
            removed.removeAll(back);
            change(out);
            return new Result(spec, "Put back the " + PartEdit.inventory(back) + ".");
        }
        List<Integer> which = PartEdit.find(spec, e.target);
        if (which.isEmpty()) {
            // not a part name it knows: the model says which parts the words mean
            which = PartEdit.fromBrain(spec, model.ask(PartEdit.whichSystem(), PartEdit.whichPrompt(spec, subject, e.target), 120));
        }
        if (which.isEmpty()) {
            return new Result(null, "I can't find “" + e.target + "” on the " + subject + ". Its parts are " + PartEdit.inventory(spec) + ".");
        }
        String names = PartEdit.names(spec, which);
        List<SolidSpec.Part> out = new ArrayList<SolidSpec.Part>();
        SolidSpec next = e.apply(spec, which, out);
        if (next.parts.isEmpty()) {
            return new Result(null, "That would take out every part, so I left it.");
        }
        removed.addAll(out);
        change(next);
        switch (e.op) {
            case REMOVE: return new Result(spec, "Took out the " + names + ". Say “put the " + names + " back” to undo it.");
            case ONLY: return new Result(spec, "Showing only the " + names + ".");
            case COLOUR: return new Result(spec, "Recoloured the " + names + ".");
            case BIGGER: return new Result(spec, "Made the " + names + " bigger.");
            case SMALLER: return new Result(spec, "Made the " + names + " smaller.");
            case WIDER: return new Result(spec, "Made the " + names + " wider.");
            case TALLER: return new Result(spec, "Made the " + names + " taller.");
            default: return new Result(spec, "Done.");
        }
    }

    private void change(SolidSpec next) {
        undo.add(spec);
        redo.clear();
        spec = next;
    }

    // ------------------------------------------------------------ exporting

    /** The thing as a file in the format the words name (GLB when none): null when nothing is on show. */
    public Export export(String said) {
        if (!has()) {
            return null;
        }
        MeshExport.Format f = MeshExport.formatIn(said);
        String base = Gltf.fileName(subject == null ? "model" : subject).replaceAll("\\.glb$", "");
        switch (f) {
            case OBJ: {
                Export mtl = new Export(base + ".mtl", "text/plain", MeshExport.mtl(spec).getBytes(StandardCharsets.UTF_8), null, f);
                return new Export(base + ".obj", f.mime, MeshExport.obj(spec, subject, base + ".mtl").getBytes(StandardCharsets.UTF_8), mtl, f);
            }
            case STL:
                return new Export(base + ".stl", f.mime, MeshExport.stl(spec, subject), null, f);
            case PLY:
                return new Export(base + ".ply", f.mime, MeshExport.ply(spec), null, f);
            case GLB:
            default:
                return new Export(base + ".glb", f.mime, Gltf.glb(spec, subject), null, f);
        }
    }
}
