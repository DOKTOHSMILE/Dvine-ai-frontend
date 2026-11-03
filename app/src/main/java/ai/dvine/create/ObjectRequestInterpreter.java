package ai.dvine.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what the user says into an object request: create something new,
 * change the object on show, look at it a certain way, undo/redo, save,
 * open, export, or come back to human form. There are no built-in objects: a
 * new thing is designed by the model with its measurements (see
 * {@link MeasuredDesign}), and a change is made to those measurements.
 */
public final class ObjectRequestInterpreter {
    public enum Kind { CREATE, MODIFY, VIEW, UNDO, REDO, SAVE, OPEN, EXPORT, RESTORE, PART, INSPECT, IMAGE, RECON, LIBRARY, UNKNOWN_OBJECT }

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
    /** Words for physical things, so "make a table" can be a 3D table; no shapes come with them (the model designs it). */
    private static final String THINGS = "house|home|villa|bungalow|mansion|duplex|cottage|building|office|shop|"
            + "church|school|tower|bridge|garage|shed|car|vehicle|truck|bus|bike|bicycle|motorcycle|motorbike|boat|ship|plane|"
            + "aeroplane|airplane|drone|robot|table|desk|chair|stool|sofa|couch|bed|shelf|bookshelf|cabinet|wardrobe|lamp|mug|"
            + "cup|bottle|laptop|computer|phone|tree|fence|gate|stairs|staircase|room|kitchen|pool|stadium|tank|machine";
    private static final String NUM = "(\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten)";

    private ObjectRequestInterpreter() {
    }

    /** A request while no object is on show (current == null) or about the one that is. */
    public static Request interpret(String said, ObjectSpec current) {
        String t = clean(said);
        if (t.isEmpty()) {
            return null;
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
        if (open.matches() && t.matches(".*\\b(saved|again)\\b.*|^(open|load|reopen) .*")
                && !open.group(1).matches(".*\\b(app|settings|file|folder|chat|camera|browser|link)\\b.*")) {
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
        // a clear request to build something: the model designs it with its measurements and DVINE builds that
        boolean buildVerb = verb.matches("design|build|model|construct|turn into|transform into|become");
        boolean physical = rest.matches(".*\\b(3d|3-d|three[- ]d|mesh|model|" + THINGS + ")s?\\b.*");
        if (rest.matches("^(your|yourself|it|me|us|my|a drawing|a picture|a sketch)\\b.*|.*\\b(drawing|picture|sketch|explanation|plan for|list|table of|"
                + "spreadsheet|document|report|pdf|website|app|function|script|program|playlist|budget)\\b.*")
                || !(buildVerb || physical) || verb.matches("show me|give me") && !rest.matches(".*\\b(3d|3-d|mesh|model)\\b.*")) {
            return null;
        }
        String what = rest.replaceAll("^(a|an|the) ", "").replaceAll("\\b(3d|3-d|three[- ]d)\\s*", "").trim();
        String title = what.replaceAll("\\s+(with|that|which|where|on|of|for)\\b.*$", "").trim();
        return new Request(Kind.CREATE, ObjectSpec.of(category(title)).with("title", title, true).with("request", said.trim(), true), null);
    }

    /**
     * A build from any words (the brain's DO BUILD order: "a family house for five people, 2 storeys"): the model
     * designs it. There are no built-in objects.
     */
    public static Request build(String request) {
        String r = request == null ? "" : request.trim();
        String what = r.replaceFirst("(?i)^(please |can you |could you |i want (you to )?|i need |make me |build me |design me )+", "")
                .replaceFirst("(?i)^(design|create|build|make|model|construct|generate)\\s+(me |us )?", "")
                .replaceFirst("(?i)^(a|an|the)\\s+", "").replaceAll("(?i)\\b(3d|3-d|three[- ]d)\\s*", "").trim();
        String title = what.replaceAll("(?i)[.;:]\\s.*$", "").replaceAll("(?i)\\s+(with|that|which|where|on|of|for)\\b.*$", "").trim();
        if (title.isEmpty()) {
            title = "design";
        }
        return new Request(Kind.CREATE, ObjectSpec.of(category(title.toLowerCase(Locale.US))).with("title", title, true).with("request", r, true), null);
    }

    /** One word for the kind of thing ("two-storey building" → "building"), for saving and opening it. */
    static String category(String title) {
        Matcher m = Pattern.compile("\\b(" + THINGS + ")s?\\b").matcher(title);
        String c = m.find() ? m.group(1) : title.replaceAll(".*\\s", "");
        return c.isEmpty() ? "design" : c;
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

    /** The part named after the verb ("take off the front tyre" → "front tyre", "remove the windows" → "windows"). */
    static String partName(String n) {
        return n.replaceFirst("^.*?\\b(remove|detach|take off|take out)\\s+", "").replaceFirst("^(the|a|an|all the|all)\\s+", "")
                .replaceFirst("\\s+\\b(but|and|leaving|leave|keep|keeping|while|so|for now|from)\\b.*$", "").trim();
    }

    static String clean(String said) {
        return said == null ? "" : said.toLowerCase(Locale.US).replace('’', '\'').replaceAll("[?!.,]+", " ")
                .replaceAll("^(hey )?(obi|ivie|eva)\\s+", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * A change to the object on show ("make the roof 25 feet", "add two more windows at the back", "paint the walls
     * blue"): the model changes the design's measurements and DVINE builds it again. Null when the words aren't one.
     */
    static ObjectSpec modify(String t, ObjectSpec s) {
        if (!s.has("design")) {
            return null;
        }
        if (t.matches("^(make|change|set|increase|decrease|reduce|raise|lower|add|give it|extend|widen|narrow|shorten|lengthen|"
                + "enlarge|shrink|move|paint|colou?r|replace|use|double|halve|turn the|swap|resize|scale|stretch)\\b.+")
                || t.matches(".*\\b(should be|must be|needs to be|instead)\\b.*")) {
            return s.with("change", t, true);
        }
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
