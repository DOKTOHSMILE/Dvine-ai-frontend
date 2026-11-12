package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the pilot says about themselves, kept so every brain knows it: where they live, where and as what they work,
 * what they are building, the people they name ("my sister Ada"), what they like. Read from the pilot's own
 * statements only ("I live in Lagos", "I work at Andela as a designer", "my project is Ibiko"), never guessed, and
 * a newer statement replaces an older one. Given to the brain as a short line with each message. Pure Java; kept in a
 * file on the device, and forgotten with {@link #clear} or one at a time with {@link #forget}.
 */
public final class PilotProfile {
    /** Capitalised words, read case-sensitively even inside a case-insensitive rule ("Lagos", not "Lagos now"). */
    private static final String NAME = "(?-i:([A-Z][\\p{L}'-]+(?: [A-Z][\\p{L}'-]+){0,3}))";
    private static final String THING = "([\\p{L}\\p{N}][\\p{L}\\p{N} .&'/-]{1,60}?)";
    private static final String END = "\\s*(?:[.,!?;]|$| and | but | so | because )";

    private static final Object[][] RULES = {
            {"home", Pattern.compile("(?i)\\bi (?:live|stay|reside) in " + NAME + "(?![\\p{L}'-])")},
            {"home", Pattern.compile("(?i)\\bi'?m (?:based|living) in " + NAME + "(?![\\p{L}'-])")},
            {"from", Pattern.compile("(?i)\\bi'?m from " + NAME + "(?![\\p{L}'-])")},
            {"from", Pattern.compile("(?i)\\bi am from " + NAME + "(?![\\p{L}'-])")},
            {"works at", Pattern.compile("(?i)\\bi work (?:at|for) " + NAME + "(?![\\p{L}'-])")},
            {"works as", Pattern.compile("(?i)\\bi work as (?:an? )?" + THING + END)},
            {"works as", Pattern.compile("(?i)\\bi'?m an? (" + "(?:software |web |mobile |product |graphic |ui |ux |data )?"
                    + "(?:developer|engineer|designer|doctor|nurse|teacher|student|lawyer|writer|artist|manager|founder|"
                    + "entrepreneur|accountant|marketer|photographer|musician|farmer|trader|consultant|researcher|architect))\\b")},
            {"building", Pattern.compile("(?i)\\b(?:my (?:project|app|startup|company|business) is(?: called)?|i'?m (?:building|working on|making)) " + THING + END)},
            {"likes", Pattern.compile("(?i)\\bi (?:really )?(?:love|like|enjoy) " + THING + END)},
            {"birthday", Pattern.compile("(?i)\\bmy birthday is (?:on )?" + THING + END)},
            {"language", Pattern.compile("(?i)\\bi speak " + THING + END)},
    };
    private static final Pattern PERSON = Pattern.compile("(?i)\\bmy (wife|husband|son|daughter|brother|sister|mother|mum|mom|father|dad|"
            + "friend|boss|partner|girlfriend|boyfriend|cofounder|co-founder|colleague) (?:is (?:called |named )?)?" + "([A-Z][\\p{L}'-]+)");

    private final File file;
    private final Map<String, String> facts = new LinkedHashMap<String, String>();

    private static final Map<String, PilotProfile> OPEN = new java.util.HashMap<String, PilotProfile>();

    /** The one copy for this file, shared by the chat and the Memory screen so neither saves over the other. */
    public static synchronized PilotProfile at(File file) {
        String k = file.getAbsolutePath();
        PilotProfile p = OPEN.get(k);
        if (p == null) {
            p = new PilotProfile(file);
            OPEN.put(k, p);
        }
        return p;
    }

    public PilotProfile(File file) {
        this.file = file;
        load();
    }

    /**
     * Reads one of the pilot's own messages for what it says about them; true when something new was kept. A question
     * ("do I live in Lagos?") or a negation ("I don't live in Lagos") teaches nothing.
     */
    public synchronized boolean notice(String message) {
        String m = message == null ? "" : message.trim();
        if (m.isEmpty() || m.endsWith("?") || m.matches("(?is).*\\b(don'?t|do not|never|no longer|not)\\b.*")) {
            return false;
        }
        boolean changed = false;
        for (Object[] r : RULES) {
            Matcher x = ((Pattern) r[1]).matcher(m);
            if (x.find()) {
                changed |= put((String) r[0], x.group(1).trim());
            }
        }
        Matcher p = PERSON.matcher(m);
        while (p.find()) {
            String who = p.group(1).toLowerCase(java.util.Locale.US).replace("mum", "mother").replace("mom", "mother").replace("dad", "father");
            if (Character.isUpperCase(p.group(2).charAt(0))) {
                changed |= put(who, p.group(2));
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }

    private boolean put(String key, String value) {
        String v = value.replaceAll("\\s+", " ").trim();
        if (v.isEmpty() || v.equals(facts.get(key))) {
            return false;
        }
        facts.remove(key); // newest last
        facts.put(key, v);
        while (facts.size() > 40) {
            facts.remove(facts.keySet().iterator().next());
        }
        return true;
    }

    /** What it knows, as "key: value" lines. */
    public synchronized List<String> lines() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : facts.entrySet()) {
            out.add(e.getKey() + ": " + e.getValue());
        }
        return out;
    }

    public synchronized String get(String key) {
        return facts.get(key);
    }

    /** The line given to the brain with each message, or "" when nothing is known. */
    public synchronized String brief() {
        if (facts.isEmpty()) {
            return "";
        }
        return " What the user has told you about themselves (use it when it helps; don't repeat it back): "
                + String.join("; ", lines()) + ".";
    }

    public synchronized boolean forget(String key) {
        boolean gone = facts.remove(key) != null;
        if (gone) {
            save();
        }
        return gone;
    }

    public synchronized void clear() {
        facts.clear();
        save();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : ai.ibiko.learn.CodeFile.lines(file)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    facts.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (IOException e) {
            facts.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : facts.entrySet()) {
            b.append(e.getKey()).append('\t').append(e.getValue().replace('\n', ' ').replace('\t', ' ')).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), ai.ibiko.learn.CodeFile.encode(b.toString()));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }
}
