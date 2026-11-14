package ai.ibiko.suit;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The tools one chat page lets its brain use, chosen by the pilot from the tools button beside the microphone. Each
 * persona has its own list (Obi talks, looks things up and writes; Ivie makes pictures and 3D; Ibiko codes on GitHub
 * and the device), and each page keeps its own choice, with the GitHub repositories Ibiko may work on. The brain is
 * told only the codes of the tools that are on ({@link #allowedCodes}), so a tool that is off cannot be used. The
 * suit's own controls (new chat, switch persona, retry, settings, time, calculator) are always there. Pure Java.
 */
public final class ChatTools {
    /** A tool a chat can switch on or off. */
    public enum Tool {
        GITHUB("GitHub", "Clone, change and push your repositories", null),
        SHELL("Linux shell", "Run commands on this device", FunctionCode.SH),
        WEB("Web search", "Look up news, prices, anything recent", FunctionCode.SR),
        CONNECTORS("Connectors", "Gmail, calendar, Notion and more", FunctionCode.TL),
        PHOTOS("Photos", "Find and show real photographs", FunctionCode.PH),
        IMAGES("Images", "Make pictures: icons, logos, illustrations", FunctionCode.IM),
        FILES("Make files", "PDF, Word, sheets, web pages", FunctionCode.MF),
        SHARE("Share and copy", "Through your apps, after your yes", FunctionCode.AC),
        SOCIAL("Social accounts", "Trends, posts, comments, schedules", FunctionCode.SO),
        WORKERS("Workers", "On for every task: the work is split into parts that run at once", null),
        DEEP("Deep thinking", "Think 1: plan, check and revise first (slower). Think 2 is on the Think button.", null);

        public final String title, about;
        /** The function the tool opens, or null when it isn't a single code (GitHub, deep thinking). */
        public final FunctionCode code;

        Tool(String title, String about, FunctionCode code) {
            this.title = title;
            this.about = about;
            this.code = code;
        }
    }

    /** The suit's own controls: always available, whatever tools are on. */
    static final Set<FunctionCode> ALWAYS = EnumSet.of(FunctionCode.CA, FunctionCode.TM, FunctionCode.NM, FunctionCode.NC,
            FunctionCode.RT, FunctionCode.OS, FunctionCode.WK, FunctionCode.WP, FunctionCode.TT, FunctionCode.RF,
            FunctionCode.CO, FunctionCode.YS, FunctionCode.CX);

    /**
     * The tools on offer, in the order the sheet shows them. The chat is a group: Obi, Ivie and Ibiko are all in it, so
     * every tool is there whoever answers.
     */
    public static List<Tool> offered(String persona) {
        return new ArrayList<Tool>(java.util.Arrays.asList(Tool.values()));
    }

    private final String persona;
    private final Set<Tool> on = EnumSet.noneOf(Tool.class);
    private final Set<String> repos = new LinkedHashSet<String>();
    /** 0 ordinary (one answer), 1 Think 1 (plan, check, revise), 2 Think 2 (several models, one answer). */
    private int think;

    private ChatTools(String persona) {
        this.persona = persona;
    }

    /** A page's tools as they start: everything its persona offers is on, except deep thinking. */
    public static ChatTools defaults(String persona) {
        ChatTools c = new ChatTools(persona);
        for (Tool t : offered(persona)) {
            if (t != Tool.DEEP) {
                c.on.add(t);
            }
        }
        return c;
    }

    public boolean isOn(Tool t) {
        if (t == Tool.DEEP) {
            return think > 0; // Think 1 and Think 2 both count as on; ordinary is off
        }
        return t == Tool.WORKERS || on.contains(t);
    }

    /** Switches a tool the persona offers; one it doesn't offer stays off. */
    public void set(Tool t, boolean enabled) {
        if (t == Tool.WORKERS && !enabled) {
            return; // workers stay on for every task
        }
        if (t == Tool.DEEP) {
            think(enabled ? 1 : 0); // the tools card is Think 1, or ordinary; Think 2 is chosen on the Think button
            return;
        }
        if (enabled && offered(persona).contains(t)) {
            on.add(t);
        } else {
            on.remove(t);
        }
    }

    /** The GitHub repositories chosen for this page ("owner/name"), in the order chosen. */
    public List<String> repos() {
        return new ArrayList<String>(repos);
    }

    public void setRepos(List<String> chosen) {
        repos.clear();
        for (String r : chosen == null ? new ArrayList<String>() : chosen) {
            String t = r == null ? "" : r.trim();
            if (t.matches("[\\w.-]+/[\\w.-]+")) {
                repos.add(t);
            }
        }
    }

    /** Workers run on every task (the pilot's rule): always on; the Workers setting decides how many at once. */
    public boolean workers() {
        return true;
    }

    public boolean deep() {
        return think == 1;
    }

    /** Think 2: several API models on one question, then one answer. */
    public boolean round() {
        return think == 2;
    }

    /** 0 ordinary, 1 Think 1, 2 Think 2. */
    public int think() {
        return think;
    }

    /** The words on the Think button. */
    public String thinkLabel() {
        return think == 2 ? "Think 2" : think == 1 ? "Think 1" : "Ordinary";
    }

    /** Sets the thinking level. Think 1 is also the Deep thinking tool; the other levels turn that tool off. */
    public void think(int level) {
        think = level >= 2 ? 2 : level == 1 ? 1 : 0;
        if (think == 1 && offered(persona).contains(Tool.DEEP)) {
            on.add(Tool.DEEP);
        } else {
            on.remove(Tool.DEEP);
        }
    }

    /** The codes this page's brain is told about: the suit's controls and the tools that are on. */
    public Set<FunctionCode> allowedCodes() {
        Set<FunctionCode> s = EnumSet.copyOf(ALWAYS);
        for (Tool t : on) {
            if (t.code != null) {
                s.add(t.code);
            }
            if (t == Tool.GITHUB) {
                s.add(FunctionCode.TL); // GitHub's own tools go through the connector code
            }
        }
        if (on.contains(Tool.SOCIAL)) {
            s.add(FunctionCode.SI); // the social intelligence capabilities
            s.add(FunctionCode.SP); // posts scheduled once or repeating
            s.add(FunctionCode.AC); // a post or reply now, shown first
        }
        if (on.contains(Tool.SHELL)) {
            s.add(FunctionCode.PM);
            s.add(FunctionCode.AO);
            s.add(FunctionCode.AF);
            s.add(FunctionCode.UZ);
            s.add(FunctionCode.FZ);
        }
        return s;
    }

    /** The repositories chosen for this page while GitHub is on (empty: no limit). */
    public List<String> chosenRepos() {
        return on.contains(Tool.GITHUB) ? repos() : new ArrayList<String>();
    }

    /** A line for the brain about what this page works on, or "" (the repositories chosen for Ibiko). */
    public String brief() {
        if (!on.contains(Tool.GITHUB) || repos.isEmpty()) {
            return "";
        }
        return "The pilot chose these GitHub repositories for this chat: " + String.join(", ", repos)
                + ". Work only on them: do not list, clone, read or change any other repository unless the pilot names it.";
    }

    /** Kept as one line: "on:WEB,FILES;repos:me/app,me/lib;think:1". */
    public String write() {
        List<String> names = new ArrayList<String>();
        for (Tool t : on) {
            names.add(t.name());
        }
        return "on:" + String.join(",", names) + ";repos:" + String.join(",", repos) + (think == 0 ? "" : ";think:" + think);
    }

    /** Reads {@link #write}; a missing or broken line gives the persona's defaults. */
    public static ChatTools read(String persona, String line) {
        if (line == null || !line.startsWith("on:")) {
            return defaults(persona);
        }
        ChatTools c = new ChatTools(persona);
        for (String part : line.split(";")) {
            if (part.startsWith("on:")) {
                for (String n : part.substring(3).split(",")) {
                    try {
                        if (!n.isEmpty()) {
                            c.set(Tool.valueOf(n), true);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // a tool from an older version: left out
                    }
                }
            } else if (part.startsWith("repos:")) {
                List<String> r = new ArrayList<String>();
                for (String n : part.substring(6).split(",")) {
                    r.add(n);
                }
                c.setRepos(r);
            } else if (part.startsWith("think:")) {
                try {
                    c.think(Integer.parseInt(part.substring(6).trim()));
                } catch (NumberFormatException ignored) {
                    // an older line: the Deep thinking tool, if it was on, already set Think 1
                }
            }
        }
        return c;
    }
}
