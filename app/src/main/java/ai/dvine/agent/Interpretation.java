package ai.dvine.agent;

import java.util.Locale;

/**
 * The brain's reading of what the user said, before anything is done about it: the kind of message (a question,
 * a request to do something, a prompt to create something, or chat), what it means in one sentence, the task it
 * calls for, its target (the thing to draw, the app to open, the topic…) and whether answering needs the internet.
 * DVINE then checks it has the ability ({@link Capabilities}) and either does it, researches and answers, or says
 * honestly that it can't. The brain writes it as five short lines ({@link #PROMPT}); {@link #parse} reads them,
 * forgiving the small model's slips. Pure Java.
 */
public final class Interpretation {
    public enum Intent { QUESTION, REQUEST, PROMPT, CHAT }

    /** The tasks DVINE knows; anything else is OTHER. */
    public enum Task { ANSWER, SEARCH, DRAW, BUILD, DESIGN, OPEN_APP, COPY, SHARE, SETTING, DEBATE, MOVE, DOWNLOAD_BRAIN, SWITCH, OTHER }

    public Intent intent = Intent.CHAT;
    public String means = "";
    public Task task = Task.ANSWER;
    public String target = "";
    public boolean internet;

    /** The interpreter's instructions: short, so a small model follows them and answers fast. */
    public static final String PROMPT = "You read what a user said to DVINE, a phone assistant, and classify it. Do not answer it."
            + " Reply with exactly five lines and nothing else:\n"
            + "INTENT: question | request | prompt | chat\n"
            + "MEANS: what the user wants, in one short sentence\n"
            + "TASK: answer | search | draw | build | design | open_app | copy | share | setting | debate | move | download_brain | switch | other\n"
            + "TARGET: the thing to draw or build, the app, the topic, the setting and value, the persona, or none\n"
            + "INTERNET: yes if answering needs current information from the internet (news, prices, weather, recent events,"
            + " people's latest roles, live facts), else no\n"
            + "Rules: draw = a picture or diagram of something; build = a 3D model of a thing; design = a building or room;"
            + " debate = two personas argue a topic; move = make a designed body move (walk, dance); setting = music,"
            + " subtitles, thoughts, voice; switch = talk to Ivie, Obi or Ibiko; search = look something up online;"
            + " answer = explain, chat or answer from knowledge. /no_think";

    public static Interpretation parse(String written) {
        Interpretation i = new Interpretation();
        if (written == null) {
            return null;
        }
        boolean any = false;
        for (String raw : written.split("\\r?\\n")) {
            String l = raw.trim().replaceAll("^[-*\\s]+", "");
            int c = l.indexOf(':');
            if (c < 0) {
                continue;
            }
            String key = l.substring(0, c).trim().toUpperCase(Locale.US), v = l.substring(c + 1).trim();
            String low = v.toLowerCase(Locale.US).replaceAll("[.\"']", "").trim();
            switch (key) {
                case "INTENT":
                    any = true;
                    i.intent = low.startsWith("question") ? Intent.QUESTION : low.startsWith("request") ? Intent.REQUEST
                            : low.startsWith("prompt") ? Intent.PROMPT : Intent.CHAT;
                    break;
                case "MEANS":
                    i.means = v;
                    break;
                case "TASK":
                    any = true;
                    i.task = task(low);
                    break;
                case "TARGET":
                    i.target = low.equals("none") || low.equals("n/a") || low.equals("-") ? "" : v.replaceAll("^[\"']|[\"'.]$", "").trim();
                    break;
                case "INTERNET":
                    i.internet = low.startsWith("yes") || low.startsWith("true");
                    break;
                default:
                    break;
            }
        }
        return any ? i : null;
    }

    static Task task(String low) {
        String t = low.replace(' ', '_').replaceAll("[^a-z_].*$", "");
        for (Task k : Task.values()) {
            if (k.name().toLowerCase(Locale.US).equals(t)) {
                return k;
            }
        }
        if (t.startsWith("open")) return Task.OPEN_APP;
        if (t.startsWith("download")) return Task.DOWNLOAD_BRAIN;
        return Task.OTHER;
    }

    /**
     * The interpretation as the plain command DVINE's own brain already knows ("draw a lion in 3D", "open whatsapp",
     * "music off"), so doing it goes through the same checked paths; null when it has none (answer, search, other).
     */
    public String command() {
        String t = target == null ? "" : target.trim();
        switch (task) {
            case DRAW:
                return t.isEmpty() ? null : "draw " + article(t) + " in 3d";
            case BUILD:
                return t.isEmpty() ? null : "build me " + article(t);
            case DESIGN:
                return t.isEmpty() ? null : "design me " + article(t);
            case OPEN_APP:
                return t.isEmpty() ? null : "open " + t.toLowerCase(Locale.US);
            case SETTING: {
                String s = t.toLowerCase(Locale.US);
                return s.matches(".*\\b(music|subtitles|captions|thoughts)\\b.*\\b(on|off)\\b.*") || s.matches(".*\\b(on|off)\\b.*\\b(music|subtitles|captions)\\b.*")
                        ? s.replaceAll("[:=]", " ").replaceAll("\\s+", " ").trim() : null;
            }
            case SWITCH:
                return t.isEmpty() ? null : "switch to " + t.toLowerCase(Locale.US);
            case DOWNLOAD_BRAIN:
                return "download your brain";
            case MOVE:
                return t.isEmpty() ? null : "make it " + t.toLowerCase(Locale.US);
            default:
                return null;
        }
    }

    private static String article(String t) {
        String l = t.toLowerCase(Locale.US);
        if (l.matches("^(a|an|the|my|some|two|three|\\d+)\\b.*") || l.endsWith("s")) {
            return t;
        }
        return ("aeiou".indexOf(l.charAt(0)) >= 0 ? "an " : "a ") + t;
    }

    /**
     * Whether the reading is about what was actually said: its target shares a word with the user's words (a small
     * brain sometimes answers about something else entirely, "draw a cat" read as "a two-story building"). A reading
     * with no target is grounded.
     */
    public boolean groundedIn(String said) {
        if (target.isEmpty() || said == null) {
            return true;
        }
        String s = " " + said.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9 ]", " ") + " ";
        for (String w : target.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9 ]", " ").split("\\s+")) {
            String stem = w.replaceAll("(es|s)$", "");
            if (stem.length() >= 3 && !stem.matches("the|and|for|with|some|that|this") && s.contains(" " + stem)) {
                return true;
            }
        }
        return false;
    }

    /** One line for the inner voice: "request · draw · a lion". */
    public String summary() {
        return intent.name().toLowerCase(Locale.US) + " · " + task.name().toLowerCase(Locale.US).replace('_', ' ')
                + (target == null || target.isEmpty() ? "" : " · " + target) + (internet ? " · needs the internet" : "");
    }
}
