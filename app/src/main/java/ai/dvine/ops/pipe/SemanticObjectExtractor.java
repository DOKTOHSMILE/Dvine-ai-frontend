package ai.dvine.ops.pipe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticObjectExtractor {
    private static final Pattern PART = Pattern.compile("(front[- ]left tyre|front[- ]left tire|front wheel|front tyre|front tire|front rim|roof|windows|fork|brake)");

    public String target(String n) {
        Matcher m = PART.matcher(n);
        if (m.find()) {
            return canonical(m.group(1));
        }
        if (n.contains("motorcycle image") || n.contains("image of the motorcycle") || n.contains("motorcycle")) {
            return "motorcycle";
        }
        if (n.contains("project folder") || n.contains("folder")) {
            return "folder";
        }
        if (n.contains("file")) {
            return "file";
        }
        if (n.contains("log")) {
            return "logs";
        }
        return "";
    }

    public String parent(String n) {
        if (n.contains("motorcycle")) {
            return "motorcycle";
        }
        if (n.contains("car")) {
            return "car";
        }
        return "";
    }

    public static String canonical(String phrase) {
        String p = phrase.toLowerCase(Locale.US).replace('-', ' ').trim();
        if (p.contains("front") && p.contains("tyre") || p.contains("front") && p.contains("tire")) {
            if (p.contains("left")) {
                return "front_left_tyre";
            }
            return "front_tyre";
        }
        if (p.contains("rim")) {
            return p.contains("left") ? "front_left_rim" : "front_rim";
        }
        if (p.contains("front wheel")) {
            return "front_wheel";
        }
        if (p.contains("roof")) {
            return "roof";
        }
        if (p.contains("window")) {
            return "windows";
        }
        if (p.contains("fork")) {
            return "front_fork";
        }
        if (p.contains("brake")) {
            return "front_brake";
        }
        return p.replace(' ', '_');
    }
}
