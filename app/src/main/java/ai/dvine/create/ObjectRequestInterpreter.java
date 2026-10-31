package ai.dvine.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what the user says into an object request: create something new,
 * change the object on show, look at it a certain way, undo/redo, save,
 * open, export, or come back to human form. Parameters the user states are
 * explicit; the rest are defaults the engine assumes and reports.
 */
public final class ObjectRequestInterpreter {
    public enum Kind { CREATE, MODIFY, VIEW, UNDO, REDO, SAVE, OPEN, EXPORT, RESTORE, PART, INSPECT, IMAGE, RECON, POSE, LIBRARY, UNKNOWN_OBJECT }

    public static final class Request {
        public final Kind kind;
        public final ObjectSpec spec;
        /** For MODIFY: what changed, in words. For VIEW: the view (front, side, rear, top, interior, plan, rotate, zoom in, zoom out, exterior). */
        public final String detail;

        Request(Kind kind, ObjectSpec spec, String detail) {
            this.kind = kind;
            this.spec = spec;
            this.detail = detail;
        }
    }

    private static final Pattern CREATE = Pattern.compile(
            "^(?:(?:please|can you|could you|now|ok|okay) )*(design|create|build|make|model|construct|generate|show me|give me|turn into|transform into|become)"
                    + "(?: me| us)?(?: a| an| the| some)? (.+)$");
    private static final String[][] NOUNS = {
            {"house", "house|home|villa|bungalow|mansion|duplex|cottage|building"},
            {"car", "car|vehicle|sedan|saloon|suv|jeep|sports car|truck"},
            {"table", "dining table|coffee table|table|desk"},
            {"chair", "armchair|chair|stool"},
            {"sofa", "sofa|couch|settee"},
            {"bed", "bed"},
            {"bookshelf", "bookshelf|bookcase|shelf|shelves"},
            {"lamp", "lamp|desk lamp"},
            {"mug", "mug|cup"},
            {"bottle", "bottle"},
            {"laptop", "laptop|notebook computer|computer"},
            {"phone", "smartphone|phone|mobile"},
            {"tree", "tree"},
            {"bicycle", "bicycle|bike"},
            {"motorcycle", "motorcycle|motorbike"},
            {"person", "person|human|humanoid"},
            {"progress", "progress bar"}};
    private static final String COLORS = "black|white|red|blue|green|yellow|grey|gray|silver|orange|purple|gold|oak|walnut|pine|wood|metal|glass";
    private static final String NUM = "(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)";

    private ObjectRequestInterpreter() {
    }

    /** A request while no object is on show (current == null) or about the one that is. */
    public static Request interpret(String said, ObjectSpec current) {
        String t = clean(said);
        if (t.isEmpty()) {
            return null;
        }
        if (t.matches("^(raise|lift|lower|put down) (your |the |my )?(left|right) (arm|forearm).*")
                || t.matches("^(raise|lift|lower) (both arms|your arms).*")
                || t.matches("^(arms up|hands up|put your arms down|arms down|wave|look up|tilt your head|chin up)$")) {
            return new Request(Kind.POSE, current, t);
        }
        if (t.matches("^(what have i saved|list my (saved )?(models|designs)|show my saved models)$")) {
            return new Request(Kind.LIBRARY, current, null);
        }
        if (t.matches("^(return|go back|change back|turn back|come back)( to)?( your)?( normal| human| original)?( form| self| shape| body)?( please)?$")
                || t.matches("^(be|become) (human|yourself|normal) again$|^restore yourself$|^back to normal$")) {
            return current == null ? null : new Request(Kind.RESTORE, current, null);
        }
        if (current != null) {
            Request r = viewOrHistory(t, current);
            if (r != null) {
                return r;
            }
            ObjectSpec m = modify(t, current);
            if (m != null && !m.equals(current)) {
                return new Request(Kind.MODIFY, m, t);
            }
            Request part = partRequest(t, current);
            if (part != null) {
                return part;
            }
            if (t.matches("^(what parts( are there)?|list( the)? parts|show( me)? the parts)$")) {
                return new Request(Kind.INSPECT, current, null);
            }
        }
        if (reconstructionAsk(t)) {
            return new Request(Kind.RECON, null, null);
        }
        Matcher open = Pattern.compile("^(?:open|load|reopen|show me) (?:my |the )?(?:saved )?(.+?)(?: design| model)?( again)?$").matcher(t);
        if (open.matches() && open.group(1).matches("(saved |last )?(house|car|table|chair|sofa|bed|bookshelf|lamp|mug|bottle|laptop|phone|tree|bicycle|motorcycle|design|model|object)")
                && t.matches(".*\\b(open|load|reopen|saved|again)\\b.*")) {
            return new Request(Kind.OPEN, null, open.group(1).replaceAll("^(saved |last )", ""));
        }
        Matcher c = CREATE.matcher(t);
        if (!c.matches()) {
            return null;
        }
        String verb = c.group(1);
        String rest = c.group(2);
        if (imageAsk(rest)) {
            return new Request(Kind.IMAGE, null, rest);
        }
        if (reconstructionAsk(t)) {
            return new Request(Kind.RECON, null, null);
        }
        String category = categoryOf(rest);
        if (category == null) {
            List<String> shapes = shapes(rest);
            if (!shapes.isEmpty() && rest.matches(".*\\b(from|with|out of|using|of)\\b.*")) {
                ObjectSpec s = ObjectSpec.of("shapes").with("shapes", String.join(",", shapes), true)
                        .with("title", rest.replaceAll("\\b(from|with|out of|using|of)\\b.*", "").trim(), true);
                s = colour(rest, s);
                return new Request(Kind.CREATE, s, null);
            }
            // only a clear request to build something is taken as an object we can't model yet
            if (!verb.matches("design|create|build|model|construct|generate|turn into|transform into|become")
                    || rest.matches("^(your|yourself|it|me|us|my|a drawing|a picture|a sketch)\\b.*|.*\\b(drawing|picture|sketch|explanation)\\b.*")) {
                return null;
            }
            return new Request(Kind.UNKNOWN_OBJECT, null, rest.replaceAll("^(a|an|the) ", ""));
        }
        return new Request(Kind.CREATE, details(rest, defaults(category)), null);
    }

    /** A picture request is not a mesh. The word "editable" is what asks for geometry. */
    static boolean imageAsk(String text) {
        return text != null && text.matches(".*\\b(picture|photo|photograph|image) of\\b.*") && !text.contains("editable");
    }

    /** Several photographs are not sent to the procedural builder. */
    static boolean reconstructionAsk(String text) {
        if (text == null) {
            return false;
        }
        boolean photos = text.contains("photograph") || text.contains("photos") || text.contains("these photo");
        return photos && (text.contains("reconstruct") || text.contains("photogrammetry") || text.contains("3d"));
    }

    static Request partRequest(String t, ObjectSpec s) {
        String n = t.replace("tire", "tyre");
        if (n.matches("^(select|choose|highlight) (the )?.+$")) {
            return new Request(Kind.PART, s, "select|" + n.replaceFirst("^(select|choose|highlight) (the )?", ""));
        }
        if (n.matches("^(restore|return) the deleted .+$")) {
            return new Request(Kind.PART, s, "restore-deleted|" + n.replaceFirst("^(restore|return) the deleted ", ""));
        }
        if (n.matches("^(put|bring|place) (the )?.+ back$") || n.matches("^(restore|return) (the )?.+$")) {
            String phrase = n.replaceFirst("^(put|bring|place|restore|return) (the )?", "").replaceFirst(" back$", "");
            return new Request(Kind.PART, s, "restore|" + phrase);
        }
        if (n.matches("^yes,? delete (the )?.+$")) {
            return new Request(Kind.PART, s, "delete-yes|" + n.replaceFirst("^yes,? delete (the )?", ""));
        }
        if (n.matches("^delete (the )?.+$")) {
            return new Request(Kind.PART, s, "delete|" + n.replaceFirst("^delete (the )?", ""));
        }
        if (n.matches(".*(remove|detach|take off|take out).*")) {
            return new Request(Kind.PART, s, "detach|" + partName(n));
        }
        return null;
    }

    static String partName(String n) {
        if (n.contains("front tyre")) {
            return "front tyre";
        }
        if (n.contains("rear tyre")) {
            return "rear tyre";
        }
        if (n.contains("front rim")) {
            return "front rim";
        }
        if (n.contains("rear rim")) {
            return "rear rim";
        }
        if (n.contains("tyre")) {
            return "tyre";
        }
        if (n.contains("rim")) {
            return "rim";
        }
        return "";
    }

    static String clean(String said) {
        return said == null ? "" : said.toLowerCase(Locale.US).replace('’', '\'').replaceAll("[?!.,]+", " ")
                .replaceAll("^(hey )?(obi|ivie|eva)\\s+", "").replaceAll("\\s+", " ").trim();
    }

    static String categoryOf(String text) {
        for (String[] n : NOUNS) {
            if (Pattern.compile("\\b(" + n[1] + ")s?\\b").matcher(text).find()) {
                return n[0];
            }
        }
        return null;
    }

    /** The engine's defaults for a category (all assumed, none explicit). */
    static ObjectSpec defaults(String category) {
        ObjectSpec s = ObjectSpec.of(category);
        switch (category) {
            case "house":
                return s.with("floors", 2, false).with("style", "modern", false).with("width", 12, false).with("depth", 9, false)
                        .with("roof", "flat", false);
            case "car":
                return s.with("style", "sedan", false).with("color", "silver", false);
            case "table":
                return s.with("shape", "rectangular", false).with("width", 1.6, false).with("height", 0.75, false);
            default:
                return s;
        }
    }

    /** Reads what the request says about the object (floors, extras, sizes, colour, style). */
    static ObjectSpec details(String t, ObjectSpec s) {
        Matcher f = Pattern.compile(NUM + "[- ]?(storey|story|stories|storeys|floor|floors|level|levels)").matcher(t);
        if (f.find()) {
            s = s.with("floors", number(f.group(1)), true);
        } else if (t.contains("bungalow") || t.contains("single storey") || t.contains("one storey")) {
            s = s.with("floors", 1, true);
        }
        for (String extra : new String[]{"garage", "pool", "balcony", "fence", "garden", "arms"}) {
            if (t.matches(".*\\b" + extra + "s?\\b.*") || extra.equals("pool") && t.contains("swimming")) {
                s = s.with(extra, true, true);
            }
        }
        if (t.contains("armchair")) {
            s = s.with("arms", true, true);
        }
        Matcher roof = Pattern.compile("\\b(flat|gable|gabled|pitched|hip|hipped)( roof)").matcher(t);
        if (roof.find()) {
            String r = roof.group(1);
            s = s.with("roof", r.startsWith("hip") ? "hip" : r.equals("flat") ? "flat" : "gable", true);
        }
        Matcher style = Pattern.compile("\\b(modern|traditional|classic|minimalist|suv|sports|sedan)\\b").matcher(t);
        if (style.find()) {
            String st = style.group(1);
            s = s.with("style", st.equals("classic") ? "traditional" : st.equals("minimalist") ? "modern" : st, true);
            if (s.category.equals("house") && !s.isExplicit("roof")) {
                s = s.with("roof", st.equals("modern") || st.equals("minimalist") ? "flat" : "gable", false);
            }
        }
        if (t.contains("jeep")) s = s.with("style", "suv", true);
        if (t.contains("sports car")) s = s.with("style", "sports", true);
        if (t.matches(".*\\bround\\b.*")) s = s.with("shape", "round", true);
        Matcher shelves = Pattern.compile(NUM + " shelves").matcher(t);
        if (shelves.find()) s = s.with("shelves", number(shelves.group(1)), true);
        Matcher dim = Pattern.compile(NUM + " ?(m|metres|meters|metre|meter|cm|centimetres|centimeters|ft|feet|foot) (wide|long|tall|high|deep)").matcher(t);
        while (dim.find()) {
            float v = number(dim.group(1));
            String u = dim.group(2);
            v = u.startsWith("c") ? v / 100f : u.startsWith("f") ? v * 0.3048f : v;
            String what = dim.group(3);
            String key = what.equals("wide") ? "width" : what.equals("long") ? (s.category.equals("car") ? "length" : "width")
                    : what.equals("deep") ? "depth" : "height";
            s = s.with(key, v, true);
        }
        if (t.matches(".*\\b(big|large|larger|bigger) wheels\\b.*")) s = s.with("wheel", s.num("wheel", 0.66f) * 1.25f, true);
        return colour(t, s);
    }

    private static ObjectSpec colour(String t, ObjectSpec s) {
        Matcher col = Pattern.compile("\\b(" + COLORS + ")\\b").matcher(t);
        if (col.find()) {
            String cname = col.group(1).equals("gray") ? "grey" : col.group(1);
            s = s.with("color", cname, true);
        }
        return s;
    }

    /** A change to the object on show, or null when the words are not about it. */
    static ObjectSpec modify(String t, ObjectSpec s) {
        Matcher storeys = Pattern.compile("(?:make it|make the house|change it to|i want) (?:a )?" + NUM + "[- ]?(storey|story|storeys|stories|floors?|levels?)").matcher(t);
        if (storeys.find()) {
            return s.with("floors", Math.max(1, Math.min(4, Math.round(number(storeys.group(1))))), true);
        }
        if (t.matches("^add (a|another|one more) (floor|storey|story|level)$")) {
            return s.with("floors", Math.min(4, s.count("floors", 2) + 1), true);
        }
        if (t.matches("^remove (a|one|the top) (floor|storey|story|level)$")) {
            return s.with("floors", Math.max(1, s.count("floors", 2) - 1), true);
        }
        Matcher add = Pattern.compile("^(add|put|include|give it) (a |an |some |the )?(garage|pool|swimming pool|balcony|fence|garden|arms)").matcher(t);
        if (add.find()) {
            return s.with(add.group(3).replace("swimming ", ""), true, true);
        }
        Matcher remove = Pattern.compile("^(remove|take away|delete|no|without) (the |a )?(garage|pool|swimming pool|balcony|fence|garden|arms)").matcher(t);
        if (remove.find()) {
            return s.with(remove.group(3).replace("swimming ", ""), false, true);
        }
        Matcher roof = Pattern.compile("(change|make|give it|use) (the )?(roof|it).*\\b(flat|gable|gabled|pitched|hip|hipped)\\b|^(flat|gable|pitched|hip) roof$").matcher(t);
        if (roof.find() || t.matches(".*\\bchange the roof\\b.*")) {
            String r = t.contains("flat") ? "flat" : t.contains("hip") ? "hip" : t.contains("gable") || t.contains("pitched") ? "gable"
                    : s.str("roof", "flat").equals("flat") ? "gable" : "flat";
            return s.with("roof", r, true);
        }
        Matcher wheels = Pattern.compile("^make the wheels (bigger|larger|smaller)$").matcher(t);
        if (wheels.find()) {
            return s.with("wheel", s.num("wheel", 0.66f) * (wheels.group(1).equals("smaller") ? 0.8f : 1.25f), true);
        }
        Matcher size = Pattern.compile("^make (it|the \\w+) (a bit |much |a little )?(wider|narrower|longer|shorter|taller|higher|lower|bigger|larger|smaller)$").matcher(t);
        if (size.find()) {
            String w = size.group(3);
            float k = w.matches("wider|longer|taller|higher|bigger|larger") ? 1.2f : 1f / 1.2f;
            String key = w.matches("wider|narrower") ? "width" : w.matches("longer|shorter") && s.category.equals("car") ? "length"
                    : w.matches("longer|shorter") ? "depth" : w.matches("taller|higher|lower") ? "height" : "scale";
            if (key.equals("scale")) {
                ObjectSpec out = s;
                for (String d : new String[]{"width", "depth", "length", "height"}) {
                    if (s.has(d)) out = out.with(d, s.num(d, 1f) * k, true);
                }
                return out.with("width", s.num("width", base(s, "width")) * k, true);
            }
            if (key.equals("height") && s.category.equals("house")) {
                return s.with("floors", Math.max(1, Math.min(4, s.count("floors", 2) + (k > 1 ? 1 : -1))), true);
            }
            return s.with(key, s.num(key, base(s, key)) * k, true);
        }
        Matcher paint = Pattern.compile("^(change|paint|make|colou?r) (it|the \\w+|the body)?( colou?r)?( to)?( be)? ?(" + COLORS + ")$").matcher(t);
        if (paint.find()) {
            String c = paint.group(6).equals("gray") ? "grey" : paint.group(6);
            return s.with("color", c, true);
        }
        if (t.matches("^make it round$")) return s.with("shape", "round", true);
        if (t.matches("^(add )?more shelves$")) return s.with("shelves", s.count("shelves", 5) + 1, true);
        return null;
    }

    private static Request viewOrHistory(String t, ObjectSpec s) {
        if (t.matches("^undo( that| the last change)?$|^go back a step$")) return new Request(Kind.UNDO, s, null);
        if (t.matches("^redo( that)?$")) return new Request(Kind.REDO, s, null);
        if (t.matches("^(save|keep)( it| this| the design| the model)?( please)?$")) return new Request(Kind.SAVE, s, null);
        if (t.matches("^export( it| the model)?( as obj)?$")) return new Request(Kind.EXPORT, s, null);
        Matcher v = Pattern.compile("^(show( me)?|let me see|view|look at)( the)? (front|side|rear|back|top|interior|inside|floor plan|plan|outside|exterior)( view)?$")
                .matcher(t);
        if (v.find()) {
            String w = v.group(4);
            return new Request(Kind.VIEW, s, w.equals("back") ? "rear" : w.equals("inside") ? "interior" : w.equals("floor plan") ? "plan"
                    : w.equals("outside") ? "exterior" : w);
        }
        if (t.matches("^(front|side|rear|top) view$")) return new Request(Kind.VIEW, s, t.replace(" view", ""));
        if (t.matches("^(rotate|turn|spin)( it| the \\w+)?( around)?$")) return new Request(Kind.VIEW, s, "rotate");
        if (t.matches("^zoom in$|^closer$")) return new Request(Kind.VIEW, s, "zoom in");
        if (t.matches("^zoom out$|^further$")) return new Request(Kind.VIEW, s, "zoom out");
        return null;
    }

    private static float base(ObjectSpec s, String key) {
        switch (s.category) {
            case "house": return key.equals("width") ? 12f : key.equals("depth") ? 9f : 3f;
            case "car": return key.equals("length") ? 4.6f : key.equals("width") ? 1.85f : 1.45f;
            case "table": return key.equals("width") ? 1.6f : key.equals("depth") ? 0.9f : 0.75f;
            default: return 1f;
        }
    }

    static List<String> shapes(String t) {
        List<String> out = new ArrayList<String>();
        Matcher m = Pattern.compile("(?:" + NUM + " |an? )?(spheres?|balls?|boxes|box|cubes?|cylinders?|cones?|rings?|tori|torus)").matcher(t);
        while (m.find()) {
            int n = m.group(1) == null ? 1 : Math.max(1, Math.min(6, Math.round(number(m.group(1)))));
            String w = m.group(2);
            String shape = w.startsWith("sphere") || w.startsWith("ball") ? "sphere" : w.startsWith("box") || w.startsWith("cube") ? "box"
                    : w.startsWith("cylinder") ? "cylinder" : w.startsWith("cone") ? "cone" : "torus";
            for (int i = 0; i < n; i++) {
                out.add(shape);
            }
        }
        return out;
    }

    static float number(String w) {
        switch (w) {
            case "one": return 1;
            case "two": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            case "seven": return 7;
            case "eight": return 8;
            case "nine": return 9;
            case "ten": return 10;
            default: return Float.parseFloat(w);
        }
    }
}
