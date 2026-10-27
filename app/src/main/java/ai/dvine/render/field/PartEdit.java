package ai.dvine.render.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A change to one part (or some parts) of the thing DVINE built: "take out the tyres", "remove the left wing",
 * "make the wheels black", "make the roof bigger", "add a spoiler on the back", "show only the engine", "put the
 * tyres back". The thing is a set of named parts ({@link SolidSpec}), so DVINE knows what a tyre is in it: the
 * words are matched to the parts by name, by what they are called in everyday words ({@link #KINDS}: a tyre is a
 * wheel, a windscreen is a window…), and by where they are (left, right, front, back, top, bottom). When nothing
 * matches, the brain is asked which parts the words mean. The rest of the thing stays exactly as it was. Pure Java.
 */
public final class PartEdit {
    public enum Op { REMOVE, ONLY, RESTORE, COLOUR, BIGGER, SMALLER, WIDER, TALLER, ADD }

    public final Op op;
    /** The words naming the parts ("tyres", "left wing"), or for ADD the thing to add ("a spoiler on the back"). */
    public final String target;
    public final Integer colour;

    private PartEdit(Op op, String target, Integer colour) {
        this.op = op;
        this.target = target;
        this.colour = colour;
    }

    private static final Pattern REMOVE = Pattern.compile("^(?:please )?(?:take out|take off|remove|delete|hide|get rid of|cut off|drop|lose)"
            + " (?:all )?(?:of )?(?:the |its |his |her |their )?(.+?)(?: from (?:it|the .+))?$");
    private static final Pattern ONLY = Pattern.compile("^(?:please )?(?:show|keep|leave) (?:me )?(?:only|just) (?:the |its )?(.+)$|^only (?:the )?(.+)$");
    private static final Pattern RESTORE = Pattern.compile("^(?:please )?(?:put|bring|add) (?:the |its )?(.+?) back(?: on)?$|^(?:put|bring) back (?:the )?(.+)$");
    private static final Pattern COLOUR = Pattern.compile("^(?:please )?(?:make|paint|colour|color|turn) (?:the |its )?(.+?) (?:in )?([a-z]+)$");
    private static final Pattern SIZE = Pattern.compile("^(?:please )?make (?:the |its )?(.+?) (bigger|larger|smaller|tinier|wider|longer|taller|higher|thinner|narrower)$");
    private static final Pattern ADD = Pattern.compile("^(?:please )?(?:add|put|attach|give it|fit) (?:a |an |some |two |four )?(.+)$");

    /** The change the words ask for, or null when they aren't about the parts of what's on show. */
    public static PartEdit parse(String said) {
        if (said == null) {
            return null;
        }
        String t = said.toLowerCase(Locale.US).trim().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
        Matcher m;
        if ((m = RESTORE.matcher(t)).matches()) {
            return new PartEdit(Op.RESTORE, first(m), null);
        }
        if ((m = ONLY.matcher(t)).matches()) {
            return new PartEdit(Op.ONLY, first(m), null);
        }
        if ((m = REMOVE.matcher(t)).matches() && !m.group(1).matches("(it|that|this|everything|all|the drawing)")) {
            return new PartEdit(Op.REMOVE, m.group(1), null);
        }
        if ((m = SIZE.matcher(t)).matches() && !m.group(1).matches("(it|that|this)")) {
            String w = m.group(2);
            Op op = w.matches("bigger|larger") ? Op.BIGGER : w.matches("smaller|tinier|thinner|narrower") ? Op.SMALLER
                    : w.matches("wider|longer") ? Op.WIDER : Op.TALLER;
            return new PartEdit(op, m.group(1), null);
        }
        if ((m = COLOUR.matcher(t)).matches() && !m.group(1).matches("(it|that|this)")) {
            Integer c = DrawSpec.Palette.colour(m.group(2));
            if (c != null) {
                return new PartEdit(Op.COLOUR, m.group(1), c);
            }
        }
        if ((m = ADD.matcher(t)).matches() && !m.group(1).matches("^(it|that|this|me)\\b.*") && !t.contains(" back")) {
            return new PartEdit(Op.ADD, m.group(1), null);
        }
        return null;
    }

    private static String first(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return m.group(i).trim();
            }
        }
        return "";
    }

    /** What parts are called in everyday words: each row is one kind, its names (singular). */
    static final String[][] KINDS = {
            {"wheel", "tyre", "tire", "rim", "hub", "hubcap", "wheel assembly"},
            {"window", "windscreen", "windshield", "glass", "pane"},
            {"door", "gate", "hatch"},
            {"light", "lamp", "headlight", "headlamp", "taillight", "bulb", "lantern"},
            {"wing", "fin"},
            {"leg", "foot", "paw", "hoof"},
            {"arm", "hand", "claw"},
            {"head", "skull", "face"},
            {"eye"},
            {"ear"},
            {"tail"},
            {"roof", "top", "lid", "cap"},
            {"body", "hull", "chassis", "frame", "trunk", "torso"},
            {"engine", "motor"},
            {"propeller", "rotor", "blade", "fan"},
            {"cabin", "cockpit", "cab"},
            {"chimney", "stack"},
            {"antenna", "aerial", "mast", "pole"},
            {"seat", "saddle", "chair"},
            {"handle", "handlebar", "knob"},
            {"base", "stand", "pedestal", "platform"},
            {"spoiler"},
            {"bumper"},
            {"mirror"},
            {"horn"},
            {"leaf", "leaves"},
            {"branch"},
            {"petal", "flower"},
    };

    /** The indices of the parts these words name: by name, kind, number and place; empty when none. */
    public static List<Integer> find(SolidSpec spec, String words) {
        return find(spec, words, false);
    }

    /** As {@link #find(SolidSpec, String)}; {@code wide} takes the whole kind at once (wheels with their hubcaps). */
    static List<Integer> find(SolidSpec spec, String words, boolean wide) {
        List<Integer> out = new ArrayList<Integer>();
        if (spec == null || words == null) {
            return out;
        }
        String w = " " + words.toLowerCase(Locale.US).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim() + " ";
        String side = w.contains(" left ") ? "left" : w.contains(" right ") ? "right" : null;
        String depth = w.contains(" front ") ? "front" : w.contains(" back ") || w.contains(" rear ") ? "back" : null;
        String height = w.contains(" top ") || w.contains(" upper ") ? "top" : w.contains(" bottom ") || w.contains(" lower ") ? "bottom" : null;
        String noun = w.replaceAll(" (the|a|an|all|both|its|two|four|left|right|front|back|rear|top|bottom|upper|lower|side|one|ones|of|on|and) ", " ")
                .replaceAll("\\s+", " ").trim();
        // the whole phrase first ("front legs" are the parts called front leg), then the word, then its kind
        String phrase = singular(w.replaceAll(" (the|a|an|all|both|its|two|four|of|on|and) ", " ").replaceAll("\\s+", " ").trim());
        if (!phrase.equals(singular(noun)) && !phrase.isEmpty()) {
            for (int i = 0; i < spec.parts.size(); i++) {
                if (matches(spec.parts.get(i).name.toLowerCase(Locale.US), phrase)) {
                    out.add(i);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        List<String> all = names(noun);
        List<String> own = wide ? all : all.subList(0, Math.min(2, all.size()));
        for (List<String> names : java.util.Arrays.asList(own, all)) {
            for (int i = 0; i < spec.parts.size(); i++) {
                String pn = spec.parts.get(i).name.toLowerCase(Locale.US);
                for (String n : names) {
                    if (matches(pn, n)) {
                        out.add(i);
                        break;
                    }
                }
            }
            if (!out.isEmpty()) {
                break;
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        // where they are: left / right, front / back, top / bottom, relative to the whole thing
        float[] c = centre(spec);
        List<Integer> placed = new ArrayList<Integer>();
        for (int i : out) {
            SolidSpec.Part p = spec.parts.get(i);
            boolean ok = (side == null || (side.equals("left") ? p.x < c[0] - 0.5f : p.x > c[0] + 0.5f))
                    && (depth == null || (depth.equals("front") ? p.z > c[2] + 0.5f || p.x > c[0] + 0.5f && c[3] > c[5] : p.z < c[2] - 0.5f || p.x < c[0] - 0.5f && c[3] > c[5]))
                    && (height == null || (height.equals("top") ? p.y < c[1] : p.y > c[1]));
            if (ok) {
                placed.add(i);
            }
        }
        return placed.isEmpty() && side == null && depth == null && height == null ? out : placed;
    }

    /** All the names a word may go by: itself, its singular, and the other names of its kind. */
    static List<String> names(String noun) {
        List<String> out = new ArrayList<String>();
        if (noun.isEmpty()) {
            return out;
        }
        String one = singular(noun);
        out.add(noun);
        out.add(one);
        for (String[] kind : KINDS) {
            for (String k : kind) {
                if (one.equals(k) || one.endsWith(" " + k) || noun.equals(k + "s")) {
                    for (String o : kind) {
                        if (!out.contains(o)) {
                            out.add(o);
                        }
                    }
                }
            }
        }
        return out;
    }

    static String singular(String w) {
        if (w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("ves")) {
            return w.substring(0, w.length() - 3) + "f";
        }
        if (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes")) {
            return w.substring(0, w.length() - 2);
        }
        return w.endsWith("s") && !w.endsWith("ss") ? w.substring(0, w.length() - 1) : w;
    }

    private static boolean matches(String partName, String name) {
        String pn = " " + partName.replaceAll("[^a-z ]", " ") + " ";
        String one = singular(name);
        return pn.contains(" " + name + " ") || pn.contains(" " + one + " ") || pn.contains(" " + one + "s ")
                || singular(partName.trim()).equals(one);
    }

    /** The middle of the thing and its size across (x), up (y) and deep (z). */
    private static float[] centre(SolidSpec s) {
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (SolidSpec.Part p : s.parts) {
            minX = Math.min(minX, p.x - p.w / 2);
            maxX = Math.max(maxX, p.x + p.w / 2);
            minY = Math.min(minY, p.y - p.h / 2);
            maxY = Math.max(maxY, p.y + p.h / 2);
            minZ = Math.min(minZ, p.z - p.d / 2);
            maxZ = Math.max(maxZ, p.z + p.d / 2);
        }
        return new float[]{(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2, maxX - minX, maxY - minY, maxZ - minZ};
    }

    /**
     * The thing after the change, given the parts it applies to ({@code which}); {@code removed} holds what was taken
     * out, so it can be put back. ADD is done by {@link SolidSpec#extend} with the brain's new lines instead.
     */
    public SolidSpec apply(SolidSpec spec, List<Integer> which, List<SolidSpec.Part> removed) {
        SolidSpec out = new SolidSpec().inFrameOf(spec);
        for (int i = 0; i < spec.parts.size(); i++) {
            SolidSpec.Part p = spec.parts.get(i);
            boolean hit = which.contains(i);
            switch (op) {
                case REMOVE:
                    if (hit) {
                        removed.add(p);
                        continue;
                    }
                    break;
                case ONLY:
                    if (!hit) {
                        removed.add(p);
                        continue;
                    }
                    break;
                case COLOUR:
                    if (hit) {
                        p = SolidSpec.with(p, colour, 1f, 1f, 1f);
                    }
                    break;
                case BIGGER:
                    if (hit) {
                        p = SolidSpec.with(p, null, 1.3f, 1.3f, 1.3f);
                    }
                    break;
                case SMALLER:
                    if (hit) {
                        p = SolidSpec.with(p, null, 0.75f, 0.75f, 0.75f);
                    }
                    break;
                case WIDER:
                    if (hit) {
                        p = SolidSpec.with(p, null, 1.35f, 1f, p.axis == 0 ? 1f : 1.2f);
                    }
                    break;
                case TALLER:
                    if (hit) {
                        p = SolidSpec.with(p, null, 1f, 1.35f, 1f);
                    }
                    break;
                default:
                    break;
            }
            out.parts.add(p);
        }
        return out;
    }

    /** The parts as a short list for the brain ("body, wheel ×4, cabin, light ×2"). */
    public static String inventory(SolidSpec spec) {
        java.util.LinkedHashMap<String, Integer> count = new java.util.LinkedHashMap<String, Integer>();
        for (SolidSpec.Part p : spec.parts) {
            count.merge(p.name, 1, Integer::sum);
        }
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
            b.append(b.length() == 0 ? "" : ", ").append(e.getKey()).append(e.getValue() > 1 ? " ×" + e.getValue() : "");
        }
        return b.toString();
    }

    /** The brain's answer to "which of these parts are the …?" as part names (comma or line separated). */
    public static List<Integer> fromBrain(SolidSpec spec, String written) {
        List<Integer> out = new ArrayList<Integer>();
        if (written == null) {
            return out;
        }
        for (String n : written.toLowerCase(Locale.US).replaceAll("(?s)<think>.*?</think>", "").split("[,\\n;]")) {
            String name = n.replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
            if (name.isEmpty() || name.equals("none")) {
                continue;
            }
            for (int i = 0; i < spec.parts.size(); i++) {
                if (spec.parts.get(i).name.equalsIgnoreCase(name) && !out.contains(i)) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    /** The parts' names as said ("front wheel and back wheel", "wheels"). */
    public static String names(SolidSpec spec, List<Integer> which) {
        java.util.LinkedHashMap<String, Integer> count = new java.util.LinkedHashMap<String, Integer>();
        for (int i : which) {
            String n = spec.parts.get(i).name.replaceAll("^(far|near) ", "");
            count.merge(n, 1, Integer::sum);
        }
        // "front leg, back leg" → "legs" when they are all the same kind of thing
        java.util.Set<String> last = new java.util.LinkedHashSet<String>();
        for (String n : count.keySet()) {
            String[] w = n.split(" ");
            last.add(w[w.length - 1]);
        }
        if (count.size() >= 2 && last.size() == 1) {
            return plural(last.iterator().next());
        }
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
            b.append(k == 0 ? "" : k == count.size() - 1 ? " and " : ", ").append(e.getValue() > 1 ? plural(e.getKey()) : e.getKey());
            k++;
        }
        return b.toString();
    }

    static String plural(String w) {
        if (w.endsWith("s") || w.endsWith("x") || w.endsWith("ch") || w.endsWith("sh")) {
            return w + "es";
        }
        if (w.endsWith("y") && !w.matches(".*[aeiou]y$")) {
            return w.substring(0, w.length() - 1) + "ies";
        }
        if (w.endsWith("f")) {
            return w.substring(0, w.length() - 1) + "ves";
        }
        return w + "s";
    }

    /** The parts in {@code from} these words name (to put back what was taken out). */
    public static List<SolidSpec.Part> pick(List<SolidSpec.Part> from, String words) {
        SolidSpec s = new SolidSpec();
        s.parts.addAll(from);
        List<SolidSpec.Part> out = new ArrayList<SolidSpec.Part>();
        String w = words == null ? "" : words.toLowerCase(Locale.US).trim();
        if (w.matches("^(it|them|everything|all|all of it|all of them|the rest|those|that)$")) {
            out.addAll(from);
            return out;
        }
        for (int i : find(s, words, true)) {
            out.add(from.get(i));
        }
        return out;
    }

    /** Short list of parts ("wheel ×4, hubcap ×2"). */
    public static String inventory(List<SolidSpec.Part> parts) {
        SolidSpec s = new SolidSpec();
        s.parts.addAll(parts);
        return inventory(s);
    }

    /** A part worth naming as an example of what can be changed ("wheels", "ears"). */
    public static String example(SolidSpec spec) {
        java.util.LinkedHashMap<String, Integer> count = new java.util.LinkedHashMap<String, Integer>();
        for (SolidSpec.Part p : spec.parts) {
            String[] w = p.name.split(" ");
            count.merge(w[w.length - 1], 1, Integer::sum);
        }
        String best = null;
        for (java.util.Map.Entry<String, Integer> e : count.entrySet()) {
            if (e.getValue() > 1 && !e.getKey().equals("body")) {
                return plural(e.getKey());
            }
            if (best == null && !e.getKey().equals("body")) {
                best = e.getKey();
            }
        }
        return best == null ? spec.parts.get(0).name : best;
    }

    /** Asking the brain which parts a word means. */
    public static String whichSystem() {
        return "You match a word to parts of a 3D model. Answer only with the matching part names from the list,"
                + " separated by commas, or none. /no_think";
    }

    public static String whichPrompt(SolidSpec spec, String subject, String words) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
        for (SolidSpec.Part p : spec.parts) {
            names.add(p.name);
        }
        return "The model is " + (subject == null ? "a thing" : subject) + ". Its parts: " + String.join(", ", names)
                + ".\nWhich of these parts are \"" + words + "\"?";
    }

    /** Asking the brain for the new part lines when the user adds something ("add a spoiler"). */
    public static String addPrompt(SolidSpec spec, String subject, String what) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
        for (SolidSpec.Part p : spec.parts) {
            names.add(p.name);
        }
        return "This model of " + (subject == null ? "a thing" : subject) + " already has these parts: " + String.join(", ", names)
                + ".\nAdd " + what + " to it. Write only the new part lines, placed relative to the parts it already has"
                + " (for example 'above body' or 'right of head'). Do not repeat the existing parts.";
    }
}
