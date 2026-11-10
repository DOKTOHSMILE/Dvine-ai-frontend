package ai.dvine.suit;

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
    BD("BUILD", "model a thing in 3D", "<thing, with every size given>"),
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
        StringBuilder b = new StringBuilder("# DVINE app functions\n\n"
                + "Generated from `suit/FunctionCode.java` (`FunctionCode.document()`); a test keeps this file in step. Do not edit by hand.\n\n"
                + "Every model that drives DVINE (an API model, the offline model, DVINE 1) controls the app the same way: it"
                + " writes one function code alone on the first line of its reply, then a short sentence for the user. DVINE"
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
                + " (DVINE runs it after the code and shows the result); shell commands, posts and shares wait for the user's yes.\n");
        return b.toString();
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
        if (reply == null) {
            return null;
        }
        String first = reply.trim();
        int nl = first.indexOf('\n');
        if (nl >= 0) {
            first = first.substring(0, nl).trim();
        }
        String head, rest;
        if (first.startsWith(MARK) && first.length() >= 3) {
            head = first.substring(1, 3);
            rest = first.substring(3);
        } else if (first.startsWith("[[") && first.length() >= 6 && first.startsWith("]]", 4)) {
            head = first.substring(2, 4);
            rest = first.substring(6);
        } else if (first.startsWith("⟦") && first.length() >= 4 && first.charAt(3) == '⟧') {
            head = first.substring(1, 3);
            rest = first.substring(4);
        } else {
            return null;
        }
        if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) {
            return null; // "§SRC" is not "§SR"
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

    /** True when a reply's text so far starts like a code (so the line can be held back while it streams). */
    public static boolean startsLikeCode(String soFar) {
        String t = soFar == null ? "" : soFar.replaceFirst("^\\s+", "");
        return t.startsWith(MARK) || t.startsWith("[[") || t.startsWith("⟦");
    }

    /** The reply without its code line. */
    public static String withoutCode(String reply) {
        if (parse(reply) == null) {
            return reply;
        }
        String t = reply.trim();
        int nl = t.indexOf('\n');
        return nl < 0 ? "" : t.substring(nl + 1).trim();
    }

    /**
     * What the brain is told about the suit. {@code tools}: the connector tools on for this chat ("- name <arg>:
     * what it does" a line), or empty. {@code allowed}: the codes this chat may use (null for all).
     */
    public static String instructions(String tools, java.util.Set<FunctionCode> allowed) {
        StringBuilder b = new StringBuilder("\n\nYou are the brain inside DVINE, a suit of functions on this device."
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
                .append(" Never claim a function ran: DVINE runs it after your code and shows the result.");
        return b.toString();
    }
}
