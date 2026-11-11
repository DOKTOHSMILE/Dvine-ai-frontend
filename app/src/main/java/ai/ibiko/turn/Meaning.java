package ai.ibiko.turn;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a message means, whatever its words. Ibiko carries out a few things itself (carry on with the work, hand in
 * the result, get or remove the phone's model…), and each has one fixed wording its code knows ("continue", "give me
 * the result"). People say them in endless ways: "are you done?", "where are we on that", "keep at it", "free up the
 * model's space". So the brain reads each short message and names the command it means ({@link #prompt}), or none;
 * Ibiko then acts on that command's own wording ({@link Command#canonical}), and remembers the phrasing
 * ({@link Memory}) so the next time it is understood at once, offline too. Anything that isn't one of these commands
 * goes on as the user wrote it. Pure Java.
 */
public final class Meaning {
    /** The commands Ibiko carries out itself, each with the wording its code acts on. */
    public enum Command {
        CONTINUE("continue", true, "carry on with the work already under way"),
        RESULT("give me the result", true, "hand in the result or report of the work so far; asks whether it is done or how far it got"),
        DOWNLOAD_MODEL("download the model", false, "download or install the device's own offline model"),
        REMOVE_MODEL("delete the model", false, "delete or uninstall the device's own offline model, to free space"),
        DOWNLOAD_PROGRESS("how is the download going", false, "ask how far the model's download has got"),
        MODEL_OFF("turn off the model", false, "stop using the device's own model"),
        MODEL_ON("turn on the model", false, "start using the device's own model again"),
        NEW_CHAT("start a new chat", false, "start a new, empty chat"),
        SWITCH_IVIE("switch to Ivie", false, "not used: there is one assistant, Ibiko"),
        SWITCH_OBI("switch to Obi", false, "not used: there is one assistant, Ibiko"),
        SWITCH_IBIKO("switch to Ibiko", false, "talk to Ibiko (coding) from now on; only when the message is just about switching"),
        STOP("stop", false, "stop the reply or the work going on now"),
        AUTO_ON("turn auto-accept on", false, "let Ibiko run commands, pushes and pull requests without asking first"),
        AUTO_OFF("turn auto-accept off", false, "accept edits: Ibiko changes files but asks before running commands, pushes and pull requests"),
        MODE_PLAN("switch to plan mode", false, "make Ibiko only read and write a plan, asking before it changes anything"),
        CLEAR_WORKSPACE("clear Ibiko's workspace", false, "delete the cloned repositories, the sandbox and the files in Ibiko's workspace on the device"),
        OPEN_SETTINGS("open Settings", false, "open the app's settings"),
        RETRY("ask again", false, "send the user's previous message again (the last reply failed or they want another try)"),
        NONE("", false, "anything else: a question, a request, chat, a new task");

        /** Carried out by Ibiko's own action ({@code act}), not by running {@link #canonical} as a message. */
        public boolean direct() {
            return ordinal() >= NEW_CHAT.ordinal() && this != NONE;
        }

        /** The wording Ibiko's code acts on. */
        public final String canonical;
        /** Only meaningful while there is work (or a reply) to carry on with. */
        public final boolean needsWork;
        final String about;

        Command(String canonical, boolean needsWork, String about) {
            this.canonical = canonical;
            this.needsWork = needsWork;
            this.about = about;
        }
    }

    private Meaning() {
    }

    /** The longest message read for a command: commands are short; anything longer is a task or a question. */
    public static final int MOST_CHARS = 160;

    /** Whether the message is short enough to be one of the commands (a long one goes straight on). */
    public static boolean worthReading(String text) {
        String t = text == null ? "" : text.trim();
        return !t.isEmpty() && t.length() <= MOST_CHARS && !t.contains("\n") && !t.contains("```");
    }

    /** The reader's instructions: the commands and what each means; a small model follows them. */
    public static String prompt(boolean work) {
        StringBuilder b = new StringBuilder("You read one message a user sent to Ibiko, a device assistant, and say which of these"
                + " commands it means, whatever its words. Do not answer it. Reply with one line, COMMAND: and the name.\n");
        for (Command c : Command.values()) {
            if (c.needsWork && !work) {
                continue; // nothing is under way: carrying on can't be what they mean
            }
            b.append(c.name().toLowerCase(Locale.US)).append(": ").append(c.about).append('\n');
        }
        b.append("Choose none unless the message plainly means one of the others; a question about a topic, a new task or"
                + " chat is none. /no_think");
        return b.toString();
    }

    /** The command the reader named, or {@link Command#NONE} when it named none (or wrote a real answer). */
    public static Command read(String reply) {
        if (reply == null) {
            return Command.NONE;
        }
        // A working answer is not a command, even when it starts with a trigger word ("Stop. I'll rewrite the
        // login."). Only a reply that is just "COMMAND: name" is acted on.
        String t = reply.replaceAll("(?s)<think>.*?(</think>|$)", "").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?is)^\\s*command:\\s*(?:\\*\\*)?([a-z_]+)(?:\\*\\*)?\\s*[.!?]*\\s*$").matcher(t);
        if (!m.matches()) {
            return Command.NONE;
        }
        String name = m.group(1).toLowerCase(Locale.US);
        for (Command c : Command.values()) {
            if (c.name().toLowerCase(Locale.US).equals(name)) {
                return c;
            }
        }
        return Command.NONE;
    }

    /**
     * Whether the user's own words really are this command. A classifier that latches onto "stop" inside a task
     * ("stop the null in the login and keep going") must not stop the work. {@link Command#STOP},
     * {@link Command#NEW_CHAT} and {@link Command#CLEAR_WORKSPACE} have to be the whole message.
     */
    public static boolean userMeant(String text, Command c) {
        if (c == null || c == Command.NONE) {
            return false;
        }
        String t = key(text);
        if (c == Command.STOP) {
            return t.length() <= 60 && !taskWords(t)
                    && t.matches("^(please )?(stop|stop it|stop now|cancel|cancel it|abort|halt|never mind|nevermind|shut up|be quiet|quiet)( please)?$");
        }
        if (c == Command.NEW_CHAT) {
            return t.length() <= 48 && t.matches(".*(new chat|start over|fresh chat|clear the chat|clear chat).*");
        }
        if (c == Command.CLEAR_WORKSPACE) {
            return t.length() <= 80 && (t.matches(".*(clear|delete|wipe|reset).*(workspace|repo|repos|clone|clones|files).*")
                    || t.matches(".*(workspace|clones).*(clear|delete|wipe).*"));
        }
        return true;
    }

    private static boolean taskWords(String t) {
        return t.matches(".*(bug|error|crash|code|file|class|method|function|login|build|fix|repo|line|null|exception|api).*");
    }

    /** A message as remembered: lower case, without punctuation or extra spaces ("Are you done??" → "are you done"). */
    public static String key(String text) {
        return text == null ? "" : text.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N}' ]+", " ").replaceAll("\\s+", " ").trim();
    }

    /** The phrasings already understood, kept on the phone (most recent first, at most {@link #KEEP}). */
    public static final class Memory {
        public static final int KEEP = 300;
        private final LinkedHashMap<String, Command> known = new LinkedHashMap<String, Command>(16, 0.75f, true);

        public synchronized Command get(String text) {
            return known.get(key(text));
        }

        /** Remembers what a phrasing meant (a command, or none so it isn't read again). */
        public synchronized void put(String text, Command c) {
            String k = key(text);
            if (k.isEmpty()) {
                return;
            }
            known.put(k, c);
            while (known.size() > KEEP) {
                known.remove(known.keySet().iterator().next());
            }
        }

        /** The phrasings understood (lower case, as remembered) and what each meant, oldest first. */
        public synchronized java.util.List<String[]> entries() {
            java.util.List<String[]> out = new java.util.ArrayList<String[]>();
            for (Map.Entry<String, Command> e : known.entrySet()) {
                out.add(new String[]{e.getKey(), e.getValue().name()});
            }
            return out;
        }

        public synchronized boolean forget(String text) {
            return known.remove(key(text)) != null;
        }

        public synchronized void forgetAll() {
            known.clear();
        }

        /** As "phrase\tCOMMAND" lines, for saving. */
        public synchronized String write() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Command> e : known.entrySet()) {
                b.append(e.getKey()).append('\t').append(e.getValue().name()).append('\n');
            }
            return b.toString();
        }

        public static Memory read(String saved) {
            Memory m = new Memory();
            if (saved == null) {
                return m;
            }
            for (String line : saved.split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                try {
                    m.known.put(line.substring(0, tab), Command.valueOf(line.substring(tab + 1).trim()));
                } catch (IllegalArgumentException ignored) {
                    // a command this build doesn't have
                }
            }
            return m;
        }
    }
}
