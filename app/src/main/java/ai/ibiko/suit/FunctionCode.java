package ai.ibiko.suit;

import java.util.Locale;

/**
 * The suit's functions, each behind a short machine code. Only the brain (the model in use) writes a code, on the
 * first line of its reply ("§SR weather in Lagos"), after reading what the pilot means. The pilot's own words are
 * never parsed for codes or command words: saying "stop", "new chat" or even typing "§NC" does nothing by itself.
 * The stop button and the other buttons are the pilot's direct controls. Pure Java.
 */
public enum FunctionCode {
    SR("SEARCH", "look something up on the web", "<words>"),
    SH("SHELL", "run one shell command on this device (the pilot approves it first)", "<command>"),
    TL("TOOL", "use a connector tool", "<tool> <argument>"),
    CA("CALC", "work out arithmetic exactly", "<expression>"),
    PH("PHOTO", "show a real photograph", "<subject>"),
    IM("IMAGE", "make a picture: an icon, a logo, an illustration, artwork or a picture of anything (not a real"
            + " photograph of something that exists: \u00A7PH finds those)", "<what it shows: subject, style, colours, layout>"),
    SO("SOCIAL", "the pilot's social accounts", "SEARCH <network>: <words> | TRENDING <network> | CONNECT <network> | POST <network>: <about, and any people to tag by name>"
            + " | COMMENT <network>: <post link> <what to say>"),
    SI("SOCIALINTEL", "social intelligence: investigate posts, find similar posts, public sentiment, profiles",
            "<capability> <arguments>"),
    SP("SCHEDULEPOST", "schedule a post on the pilot's own account, once or repeating; the pilot sees it and says yes first."
            + " LIST shows the scheduled posts", "<network> <yyyy-MM-dd HH:mm | HH:mm | in 2h> [daily|weekly|weekdays|every 6h]: <text> | LIST"),
    MF("MAKEFILE", "make a file of the last answer", "<pdf|docx|xlsx|csv|md|html|txt>"),
    AC("ACTION", "share, post or copy text through the device's apps (shown first; the pilot says yes)",
            "<post on <network>: text | share: text | copy: text>"),
    TM("TIME", "say the time and date", ""),
    NM("NAME", "remember what to call the pilot", "<name>"),
    UZ("UNZIP", "unzip the zip file just shared", ""),
    FZ("FINDZIP", "find zip files on this device", ""),
    NC("NEWCHAT", "start a new chat", ""),
    SW("SWITCH", "switch to another persona", "<ivie|obi|ibiko>"),
    HO("HANDOVER", "pass this message to the one whose work it is, who then answers it: ibiko for code, commands, files,"
            + " repositories, GitHub and the built-in Linux; ivie for stories, scripts, poems, drawings, design and 3D; obi for"
            + " conversation, questions, research, reports and plans. Only when it is not your own work", "<ivie|obi|ibiko>"),
    WK("WORK", "real work for the agents, not a reply: write, fix, build, run, test or audit code; work on files, a"
            + " repository or GitHub; run commands in the built-in Linux; build or audit an app's or a site's interface."
            + " The agent organisation plans it, splits it and carries it out", ""),
    WP("WRITEPLAN", "a long piece (a story, a script, a report, an essay): outline it, check the outline, then write it section by section", ""),
    TT("THINKTOGETHER", "a weighty decision or question: it is planned, checked against the real world and tested for what could break, then answered once", ""),
    RF("READFILES", "read the files in the workspace on this device (cloned repositories, projects) to answer this", ""),
    CO("CARRYON", "carry on with the unfinished work in this chat from where it stopped", ""),
    ST("STEER", "only while a job runs in the background: pass the user's instruction to that job", "<the instruction>"),
    YS("CONFIRM", "the pilot agrees to the action that is shown waiting for their yes: carry it out", ""),
    CX("CANCEL", "the pilot does not want the action that is shown waiting: drop it", ""),
    PM("PLANMODE", "Ibiko plans and asks before changing anything", ""),
    AO("AUTOON", "Ibiko runs commands, pushes and pull requests without asking", ""),
    AF("AUTOOFF", "Ibiko asks before commands, pushes and pull requests", ""),
    RT("RETRY", "ask the last question again", ""),
    OS("SETTINGS", "open Settings", "");

    /**
     * The app's functions as a document any model (or person) can read: docs/APP_FUNCTIONS.md is this text, checked by a
     * test so it never drifts from the code.
     */
    public static String document() {
        StringBuilder b = new StringBuilder("# Ibiko app functions\n\n"
                + "Generated from `suit/FunctionCode.java` (`FunctionCode.document()`); a test keeps this file in step. Do not edit by hand.\n\n"
                + "Every model that drives Ibiko (an API model, the offline model, Ibiko 1) controls the app the same way: it"
                + " writes one function code alone on the first line of its reply, then a short sentence for the user. Ibiko"
                + " carries the function out and shows the result. The user's own words never run a function; a chat's tools"
                + " (the tools button) decide which codes that chat may use.\n\n"
                + "Accepted forms on the first line: `\u00A7XX arguments`, `[[XX]] arguments`, `\u27E6XX\u27E7 arguments`.\n\n"
                + "| Code | Function | What it does | Arguments |\n|---|---|---|---|\n");
        for (FunctionCode c : values()) {
            b.append("| `").append(c.code()).append("` | ").append(c.function).append(" | ").append(c.does).append(" | ")
                    .append(c.args.isEmpty() ? "none" : "`" + c.args.replace("|", "\\|") + "`").append(" |\n");
        }
        b.append("\nExamples: `\u00A7SR weather in Lagos` \u00B7 `\u00A7SW ibiko` \u00B7 `\u00A7MF pdf` \u00B7 `\u00A7SP x 9am daily: Good morning`"
                + " \u00B7 `\u00A7AC post on x: We're live`.\n\nRules: one code a reply; arithmetic goes to `\u00A7CA`; never claim a function ran"
                + " (Ibiko runs it after the code and shows the result); shell commands, posts and shares wait for the user's yes.\n\n"
                + "## How to call each function\n\n");
        b.append(directions());
        return b.toString();
    }

    /**
     * The go-to sheet a model reads: where it is, and for every function when to write the code, the line to write,
     * and what Ibiko does next. {@link #document()} includes this, so docs/APP_FUNCTIONS.md stays the same text.
     */
    public static String directions() {
        StringBuilder b = new StringBuilder();
        b.append("Where you are: the Ibiko app on the user's phone. Linux is Alpine, one shell."
                + " This chat's files are /workspace. A clone lives in /workspace/repos/<name>."
                + " A private GitHub repository clones and pulls when Connectors, GitHub, is signed in"
                + " (a classic token needs the repo scope; a fine-grained token needs Contents)."
                + " Never put a token in a command, a URL, or git config. The app supplies it."
                + " One function a reply: its code alone on the first line, then one short sentence."
                + " Do not say the function already ran. A shell command, a post and a share wait for the user's yes.\n");
        for (FunctionCode c : values()) {
            b.append("- ").append(c.code());
            if (!c.args.isEmpty()) {
                b.append(' ').append(c.args);
            }
            b.append(" — ").append(use(c)).append('\n');
        }
        return b.toString();
    }

    /** When to write this code, the line to write, and what happens next. One line, for every function. */
    private static String use(FunctionCode c) {
        switch (c) {
            case SR:
                return "when they want the web searched. Write \u00A7SR weather in Lagos. Ibiko searches; answer from the pages.";
            case SH:
                return "one Linux command they approve first. Write \u00A7SH cd /workspace && git clone 'https://github.com/owner/name'."
                        + " A private repository uses the signed-in token; do not add it. Pull is \u00A7SH git -C /workspace/repos/name pull.";
            case TL:
                return "a connector tool that is on for this chat. Write \u00A7TL github.repos or \u00A7TL github.issues owner/name"
                        + " or \u00A7TL github.file owner/name/path. Skip a tool that was not listed.";
            case CA:
                return "exact arithmetic. Write \u00A7CA (12+7)*3. Do not work the sum out yourself.";
            case PH:
                return "a real photograph of something that exists. Write \u00A7PH a red panda. A picture to invent is \u00A7IM.";
            case IM:
                return "a picture to make (icon, logo, illustration, artwork), not a real photo. Write \u00A7IM a blue call icon, flat.";
            case SO:
                return "their social accounts. Write \u00A7SO SEARCH x: cats, \u00A7SO TRENDING x, \u00A7SO CONNECT reddit,"
                        + " \u00A7SO POST x: hello, or \u00A7SO COMMENT x: <link> nice. Networks: x, facebook, instagram, reddit.";
            case SI:
                return "social intelligence. Write \u00A7SI social.post.investigate <text>, \u00A7SI social.search.posts x cats,"
                        + " \u00A7SI social.search.similar <text>, \u00A7SI social.sentiment.analyze <topic>,"
                        + " \u00A7SI social.accounts.list, \u00A7SI social.profile.read_public x <user>,"
                        + " or \u00A7SI social.profile.read_own x.";
            case SP:
                return "schedule a post they confirm first. Write \u00A7SP x 9am daily: Good morning"
                        + " (time is yyyy-MM-dd HH:mm, HH:mm, 9am, or in 2h; repeat is daily, weekly, weekdays, or every 6h)."
                        + " \u00A7SP LIST shows what is scheduled.";
            case MF:
                return "a file of the last answer. Write \u00A7MF pdf (also docx, xlsx, csv, md, html, txt).";
            case AC:
                return "share, post or copy through the phone, shown first. Write \u00A7AC post on x: We're live,"
                        + " \u00A7AC share: hello, or \u00A7AC copy: hello.";
            case TM:
                return "the time and date on this phone. Write \u00A7TM.";
            case NM:
                return "what to call the user. Write \u00A7NM Valentine.";
            case UZ:
                return "unzip the zip they just shared. Write \u00A7UZ.";
            case FZ:
                return "find zip files on this phone. Write \u00A7FZ.";
            case NC:
                return "start a new chat. Write \u00A7NC.";
            case SW:
                return "switch persona. Write \u00A7SW ibiko (also ivie or obi).";
            case HO:
                return "pass this message to the persona whose work it is, and only when it is not yours."
                        + " Write \u00A7HO ibiko for code, commands, files, repositories and Linux;"
                        + " \u00A7HO ivie for stories, scripts, poems, drawings and design; \u00A7HO obi for questions, research and plans.";
            case WK:
                return "real code work for the agents: write, fix, build, run, test or audit, or work a repository."
                        + " Write \u00A7WK. The user's message is the task.";
            case WP:
                return "a long piece (story, script, report, essay): outline, then write section by section. Write \u00A7WP.";
            case TT:
                return "a weighty decision: plan it, check it, then answer once. Write \u00A7TT.";
            case RF:
                return "read the files in this chat's workspace to answer. Write \u00A7RF.";
            case CO:
                return "carry on with unfinished work in this chat. Write \u00A7CO.";
            case ST:
                return "only while a job is already running: pass an instruction to that job. Write \u00A7ST focus on the api first.";
            case YS:
                return "they agree to the action that is waiting. Write \u00A7YS.";
            case CX:
                return "they do not want the action that is waiting. Write \u00A7CX.";
            case PM:
                return "plan mode: ask before changing anything. Write \u00A7PM.";
            case AO:
                return "auto mode: commands, pushes and pull requests without asking. Write \u00A7AO.";
            case AF:
                return "auto mode off: ask before commands, pushes and pull requests. Write \u00A7AF.";
            case RT:
                return "ask the last question again. Write \u00A7RT.";
            case OS:
                return "open Settings. Write \u00A7OS.";
            default:
                return c.does;
        }
    }

    /** The mark that starts a code. */
    public static final String MARK = "§";

    /** The function's name inside the suit (the order kind it carries out). */
    public final String function;
    public final String does;
    public final String args;

    FunctionCode(String function, String does, String args) {
        this.function = function;
        this.does = does;
        this.args = args;
    }

    /** True when the function can't run without arguments (a file type defaults to PDF). */
    public boolean needsArgs() {
        return !args.isEmpty() && this != MF;
    }

    /** "§SR". */
    public String code() {
        return MARK + name();
    }

    /** One code the brain wrote: which function, and its arguments. */
    public static final class Call {
        public final FunctionCode code;
        public final String args;

        Call(FunctionCode code, String args) {
            this.code = code;
            this.args = args == null ? "" : args.trim();
        }

        @Override
        public String toString() {
            return code.code() + (args.isEmpty() ? "" : " " + args);
        }
    }

    /**
     * The code on the first line of a brain's reply, or null when the reply is only an answer. Accepts "§SR args",
     * "[[SR]] args" and "⟦SR⟧ args" (models copy brackets more reliably than the mark), never plain words.
     */
    public static Call parse(String reply) {
        int at = codeLine(reply);
        return at < 0 ? null : parseLine(reply.trim().split("\n", -1)[at].trim());
    }

    /** How far down a reply a code is still looked for: a brain sometimes writes a short sentence first. */
    static final int LOOK = 4;

    /** Which of the reply's first lines is a code (0 is the first), or -1. */
    static int codeLine(String reply) {
        if (reply == null) {
            return -1;
        }
        String[] lines = reply.trim().split("\n", -1);
        for (int i = 0; i < lines.length && i < LOOK; i++) {
            if (parseLine(lines[i].trim()) != null) {
                return i;
            }
        }
        return -1;
    }

    private static Call parseLine(String first) {
        // a code wrapped in markdown ("**§SR weather**", "`§NC`") is still the code
        first = first.replaceAll("^[*_`>\\s]+", "").replaceAll("[*_`\\s]+$", "");
        String head, rest;
        if (first.startsWith(MARK) && first.length() >= 3) {
            head = first.substring(1, 3);
            rest = first.substring(3);
        } else if (first.startsWith("[[") && first.length() >= 6 && first.startsWith("]]", 4)) {
            head = first.substring(2, 4);
            rest = first.substring(6);
        } else if (first.startsWith("\u27E6") && first.length() >= 4 && first.charAt(3) == '\u27E7') {
            head = first.substring(1, 3);
            rest = first.substring(4);
        } else {
            return null;
        }
        if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) {
            return null; // "\u00A7SRC" is not "\u00A7SR"
        }
        try {
            FunctionCode c = valueOf(head.toUpperCase(Locale.US));
            if (c.needsArgs() && rest.trim().isEmpty()) {
                return null; // a code that needs arguments, written without them
            }
            return new Call(c, rest.replaceFirst("^[\\s:]+", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A line that is a code or is shaped like one ("\u00A7XX …", "[[XX]] …"): never shown to the pilot. */
    private static final java.util.regex.Pattern SHAPED = java.util.regex.Pattern.compile(
            "(?m)^[*_`>\\s]*(?:\u00A7[A-Za-z]{2}\\b|\\[\\[[A-Za-z]{2}\\]\\]|\u27E6[A-Za-z]{2}\u27E7)[^\\n]*\\n?");

    /** The text with every code-shaped line taken out: what the conversation may show. */
    public static String hidden(String text) {
        return text == null ? null : SHAPED.matcher(text).replaceAll("");
    }

    /** True when a reply's text so far starts like a code (so the line can be held back while it streams). */
    public static boolean startsLikeCode(String soFar) {
        String t = soFar == null ? "" : soFar.replaceFirst("^\\s+", "");
        return t.startsWith(MARK) || t.startsWith("[[") || t.startsWith("⟦");
    }

    /** The reply without its code line (and with no other code-shaped line left in it). */
    public static String withoutCode(String reply) {
        if (reply == null) {
            return null;
        }
        return codeLine(reply) < 0 ? reply : hidden(reply).trim();
    }

    /**
     * What the brain is told about the suit. {@code tools}: the connector tools on for this chat ("- name <arg>:
     * what it does" a line), or empty. {@code allowed}: the codes this chat may use (null for all).
     */
    /** As {@link #instructions(String, java.util.Set)} in as few words as possible, for a small model on the device. */
    public static String lean(String tools, java.util.Set<FunctionCode> allowed) {
        StringBuilder b = new StringBuilder("\n\nWhen the user clearly wants one of these done, write its code alone on the first"
                + " line, then one short sentence; otherwise just answer:");
        for (FunctionCode c : values()) {
            if (allowed != null && !allowed.contains(c) || c == TL && (tools == null || tools.trim().isEmpty())) {
                continue;
            }
            String does = c.does.replaceAll("\\s*[(:;].*$", "");
            b.append(' ').append(c.code()).append(c.args.isEmpty() ? "" : " " + c.args).append(" ").append(does).append(';');
        }
        if (tools != null && !tools.trim().isEmpty()) {
            b.append(" Tools for ").append(TL.code()).append(":").append(tools);
        }
        return b.toString();
    }

    public static String instructions(String tools, java.util.Set<FunctionCode> allowed) {
        StringBuilder b = new StringBuilder("\n\nYou are the brain inside Ibiko, a suit of functions on this device."
                + " The user's words never run anything by themselves; you decide from what they mean and the"
                + " conversation. When the user clearly wants one of these done, write its code alone on the first line,"
                + " then a short sentence for them. Otherwise just answer, with no code line. Codes:");
        for (FunctionCode c : values()) {
            if (allowed != null && !allowed.contains(c)) {
                continue;
            }
            if (c == TL && (tools == null || tools.trim().isEmpty())) {
                continue;
            }
            b.append(' ').append(c.code()).append(c.args.isEmpty() ? "" : " " + c.args).append(" (").append(c.does).append(");");
        }
        if (tools != null && !tools.trim().isEmpty()) {
            b.append(" Tools for ").append(TL.code()).append(":").append(tools);
        }
        b.append(" For arithmetic, use ").append(CA.code()).append(" rather than working it out yourself.")
                .append(" Never claim a function ran: Ibiko runs it after your code and shows the result.");
        return b.toString();
    }
}
