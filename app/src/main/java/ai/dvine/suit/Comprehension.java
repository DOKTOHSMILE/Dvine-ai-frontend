package ai.dvine.suit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Cortex's reading of a message, made by DVINE itself in microseconds before any model sees it: what kind of
 * act it is (a question, a request, a greeting, thanks, a correction, a feeling), whether it is negated, the
 * numbers, names, repositories, files and links in it, the words that carry its topic, and any arithmetic inside.
 * The reading goes to the model as one line ({@link #brief}), so a small model on the device knows what the user
 * means and what they refer to without spending its own thinking on it. It never runs a function: only the model
 * decides that. Pure Java.
 */
public final class Comprehension {
    /** What the message does. */
    public enum Act { QUESTION, REQUEST, GREETING, THANKS, CORRECTION, FEELING, STATEMENT }

    public final Act act;
    public final boolean negated;
    public final List<String> numbers = new ArrayList<String>();
    public final List<String> names = new ArrayList<String>();
    public final List<String> repos = new ArrayList<String>();
    public final List<String> files = new ArrayList<String>();
    public final List<String> links = new ArrayList<String>();
    public final List<String> topic = new ArrayList<String>();
    /** An arithmetic expression in the message and its exact value, or null. */
    public final String math, mathValue;

    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d[\\d,]*(?:\\.\\d+)?%?(?![\\w])");
    private static final Pattern LINK = Pattern.compile("https?://\\S+");
    private static final Pattern REPO = Pattern.compile("(?<![\\w./-])([A-Za-z0-9][\\w.-]{0,38})/([\\w.-]{1,100})(?![\\w/])");
    private static final Pattern FILE = Pattern.compile("(?<![\\w/])([\\w./-]+\\.(?:java|kt|py|js|ts|tsx|jsx|go|rs|c|cpp|h|cs|rb|php|swift"
            + "|md|txt|json|xml|yml|yaml|gradle|toml|html|css|sql|sh|pdf|docx|xlsx|csv|png|jpg|zip))\\b");
    private static final Pattern NAME = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\b");
    private static final Set<String> STOP = new java.util.HashSet<String>(java.util.Arrays.asList(
            "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "at", "for", "with", "is", "are", "was", "were",
            "be", "it", "this", "that", "these", "those", "i", "you", "me", "my", "your", "we", "our", "they", "them",
            "he", "she", "his", "her", "do", "does", "did", "can", "could", "would", "should", "will", "please", "what",
            "how", "why", "when", "where", "which", "who", "just", "so", "if", "then", "there", "here", "about", "from",
            "up", "out", "not", "no", "yes", "have", "has", "had", "want", "need", "like", "get", "make", "let"));

    private Comprehension(Act act, boolean negated, String math, String mathValue) {
        this.act = act;
        this.negated = negated;
        this.math = math;
        this.mathValue = mathValue;
    }

    /** Reads {@code text}. */
    public static Comprehension of(String text) {
        String t = text == null ? "" : text.trim();
        String low = t.toLowerCase(Locale.US);
        String asked = Arithmetic.asked(t);
        String math = null, value = null;
        if (asked != null) {
            math = asked;
            value = Arithmetic.show(Arithmetic.eval(asked));
        } else {
            // arithmetic inside a longer message ("I have 12 boxes of 24, how many?" is left to the model)
            Matcher m = Pattern.compile("(\\d[\\d,.]*\\s*[-+*/x×÷^]\\s*[\\d(][\\d,.()\\s+*/x×÷^-]*)").matcher(t);
            if (m.find() && Arithmetic.eval(m.group(1)) != null) {
                math = m.group(1).trim();
                value = Arithmetic.show(Arithmetic.eval(math));
            }
        }
        Comprehension c = new Comprehension(act(low), negated(low), math, value);
        collect(NUMBER, t, 0, c.numbers);
        collect(LINK, t, 0, c.links);
        String noLinks = LINK.matcher(t).replaceAll(" ");
        collect(FILE, noLinks, 1, c.files);
        Matcher r = REPO.matcher(noLinks);
        while (r.find()) {
            String repo = r.group(1) + "/" + r.group(2);
            if (!c.files.contains(repo) && !repo.matches("\\d+/\\d+") && !r.group(2).contains(".")) {
                c.repos.add(repo);
            }
        }
        Matcher n = NAME.matcher(noLinks);
        while (n.find() && c.names.size() < 6) {
            String name = n.group(1);
            String before = noLinks.substring(0, n.start()).replaceAll("\\s+$", "");
            if (before.isEmpty() || before.endsWith(".") || before.endsWith("!") || before.endsWith("?")) {
                if (!name.contains(" ")) {
                    continue; // a capital that only starts a sentence is not a name
                }
                name = name.substring(name.indexOf(' ') + 1); // "Ask Ada Lovelace": the first word only starts it
            }
            if (!STOP.contains(name.toLowerCase(Locale.US)) && !c.names.contains(name)) {
                c.names.add(name);
            }
        }
        Set<String> words = new LinkedHashSet<String>();
        for (String w : low.replaceAll("https?://\\S+", " ").split("[^\\p{L}\\p{N}'-]+")) {
            if (w.length() > 2 && !STOP.contains(w) && !w.matches("\\d+")) {
                words.add(w);
            }
        }
        for (String w : words) {
            if (c.topic.size() >= 6) {
                break;
            }
            c.topic.add(w);
        }
        return c;
    }

    static Act act(String low) {
        String t = low.replaceAll("[\\s.!]+$", "");
        if (t.matches("^(no|nope|wrong|that'?s wrong|not that|that'?s not (it|right|what i (meant|asked|wanted)))\\b.*")) {
            return Act.CORRECTION;
        }
        if (t.matches("^(hi|hello|hey|good (morning|afternoon|evening)|howdy|yo|hiya)\\b.{0,20}$")) {
            return Act.GREETING;
        }
        if (t.matches("^(thanks|thank you|thx|cheers|appreciate it)\\b.{0,30}$")) {
            return Act.THANKS;
        }
        if (t.matches(".*\\b(i('?m| am) (so )?(sad|tired|angry|happy|stressed|worried|frustrated|excited|upset))\\b.*")) {
            return Act.FEELING;
        }
        if (t.endsWith("?") || t.matches("^(what|who|whom|whose|when|where|why|how|which|is|are|was|were|do|does|did|can|could"
                + "|would|should|will|shall|may|might|have|has)\\b.*")) {
            return Act.QUESTION;
        }
        if (t.matches("^(please |pls |kindly )?(make|build|create|write|draw|show|find|search|look|open|run|fix|add|remove|delete"
                + "|change|update|send|post|clone|push|pull|merge|explain|tell|give|list|check|read|summari[sz]e|translate"
                + "|design|plan|help|start|stop|continue|go)\\b.*")) {
            return Act.REQUEST;
        }
        return Act.STATEMENT;
    }

    static boolean negated(String low) {
        return low.matches(".*\\b(not|don'?t|doesn'?t|didn'?t|never|no longer|won'?t|can'?t|isn'?t|aren'?t|without)\\b.*");
    }

    private static void collect(Pattern p, String t, int group, List<String> into) {
        Matcher m = p.matcher(t);
        while (m.find() && into.size() < 12) {
            String v = m.group(group);
            if (!into.contains(v)) {
                into.add(v);
            }
        }
    }

    /**
     * One line for the model: what the message is and what it refers to, with the exact value of any arithmetic.
     * Empty when there is nothing worth saying.
     */
    public String brief() {
        StringBuilder b = new StringBuilder();
        b.append("Ibiko's reading of the message: ").append(act.name().toLowerCase(Locale.US));
        if (negated) {
            b.append(", negated");
        }
        if (!topic.isEmpty()) {
            b.append("; about: ").append(String.join(", ", topic));
        }
        if (!names.isEmpty()) {
            b.append("; names: ").append(String.join(", ", names));
        }
        if (!repos.isEmpty()) {
            b.append("; repositories: ").append(String.join(", ", repos));
        }
        if (!files.isEmpty()) {
            b.append("; files: ").append(String.join(", ", files));
        }
        if (!links.isEmpty()) {
            b.append("; links: ").append(String.join(", ", links));
        }
        if (math != null && mathValue != null && !mathValue.isEmpty()) {
            b.append("; exact arithmetic: ").append(math).append(" = ").append(mathValue).append(" (use this value)");
        }
        return b.append('.').toString();
    }
}
