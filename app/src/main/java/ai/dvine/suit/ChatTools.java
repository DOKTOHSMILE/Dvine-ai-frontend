package ai.dvine.suit;

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
        GITHUB("GitHub", "Work on the repositories you choose", null),
        SHELL("Commands on this device", "Open the shell", FunctionCode.SH),
        WEB("Web search", "Look things up on the web", FunctionCode.SR),
        CONNECTORS("Connectors", "Gmail, docs, calendar, Notion and others you connected", FunctionCode.TL),
        PHOTOS("Photos", "Show real photographs", FunctionCode.PH),
        DESIGN3D("3D design", "Model things in 3D", FunctionCode.BD),
        FILES("Make files", "PDF, Word, spreadsheets and more", FunctionCode.MF),
        SHARE("Share, post or copy", "Through the device's apps (you approve each)", FunctionCode.AC),
        SOCIAL("Social accounts", "Search, trending, posts now or scheduled, and replies you approve", FunctionCode.SO),
        DEEP("Deep thinking", "Plan, draft, check and revise before answering (slower, more calls)", null);

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
            FunctionCode.SW, FunctionCode.RT, FunctionCode.OS);

    /** The tools a persona offers, in the order the sheet lists them. */
    public static List<Tool> offered(String persona) {
        List<Tool> t = new ArrayList<Tool>();
        String p = persona == null ? "" : persona.toLowerCase(Locale.US);
        if (p.equals("ibiko")) {
            t.add(Tool.GITHUB);
            t.add(Tool.SHELL);
            t.add(Tool.WEB);
            t.add(Tool.CONNECTORS);
            t.add(Tool.FILES);
        } else if (p.equals("ivie")) {
            t.add(Tool.PHOTOS);
            t.add(Tool.DESIGN3D);
            t.add(Tool.WEB);
            t.add(Tool.FILES);
            t.add(Tool.SHARE);
        } else {
            t.add(Tool.WEB);
            t.add(Tool.CONNECTORS);
            t.add(Tool.FILES);
            t.add(Tool.SHARE);
            t.add(Tool.SOCIAL);
        }
        t.add(Tool.DEEP);
        return t;
    }

    private final String persona;
    private final Set<Tool> on = EnumSet.noneOf(Tool.class);
    private final Set<String> repos = new LinkedHashSet<String>();

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
        return on.contains(t);
    }

    /** Switches a tool the persona offers; one it doesn't offer stays off. */
    public void set(Tool t, boolean enabled) {
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

    public boolean deep() {
        return on.contains(Tool.DEEP);
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

    /** A line for the brain about what this page works on, or "" (the repositories chosen for Ibiko). */
    public String brief() {
        if (!on.contains(Tool.GITHUB) || repos.isEmpty()) {
            return "";
        }
        return "The pilot chose these GitHub repositories for this chat: " + String.join(", ", repos)
                + ". Work on them unless told otherwise.";
    }

    /** Kept as one line: "on:WEB,FILES;repos:me/app,me/lib". */
    public String write() {
        List<String> names = new ArrayList<String>();
        for (Tool t : on) {
            names.add(t.name());
        }
        return "on:" + String.join(",", names) + ";repos:" + String.join(",", repos);
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
            }
        }
        return c;
    }
}
