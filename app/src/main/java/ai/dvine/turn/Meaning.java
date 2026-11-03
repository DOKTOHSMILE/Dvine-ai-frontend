package ai.dvine.turn;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a message means, whatever its words. DVINE carries out a few things itself (carry on with the work, hand in
 * the result, get or remove the phone's model…), and each has one fixed wording its code knows ("continue", "give me
 * the result"). People say them in endless ways: "are you done?", "where are we on that", "keep at it", "free up the
 * model's space". So the brain reads each short message and names the command it means ({@link #prompt}), or none;
 * DVINE then acts on that command's own wording ({@link Command#canonical}), and remembers the phrasing
 * ({@link Memory}) so the next time it is understood at once, offline too. Anything that isn't one of these commands
 * goes on as the user wrote it. Pure Java.
 */
public final class Meaning {
    /** The commands DVINE carries out itself, each with the wording its code acts on. */
    public enum Command {
        CONTINUE("continue", true, "carry on with the work already under way"),
        RESULT("give me the result", true, "hand in the result or report of the work so far; asks whether it is done or how far it got"),
        DOWNLOAD_MODEL("download the model", false, "download or install the phone's own offline model"),
        REMOVE_MODEL("delete the model", false, "delete or uninstall the phone's own offline model, to free space"),
        DOWNLOAD_PROGRESS("how is the download going", false, "ask how far the model's download has got"),
        MODEL_OFF("turn off the model", false, "stop using the phone's own model"),
        MODEL_ON("turn on the model", false, "start using the phone's own model again"),
        NEW_CHAT("start a new chat", false, "start a new, empty chat"),
        SWITCH_IVIE("switch to Ivie", false, "talk to Ivie (graphics and 3D) from now on; only when the message is just about switching"),
        SWITCH_OBI("switch to Obi", false, "talk to Obi (chat and research) from now on; only when the message is just about switching"),
        SWITCH_IBIKO("switch to Ibiko", false, "talk to Ibiko (coding) from now on; only when the message is just about switching"),
        STOP("stop", false, "stop the reply or the work going on now"),
        AUTO_ON("turn auto-accept on", false, "let Ibiko run commands, pushes and pull requests without asking first"),
        AUTO_OFF("turn auto-accept off", false, "make Ibiko ask before running commands, pushes and pull requests"),
        CLEAR_WORKSPACE("clear Ibiko's workspace", false, "delete the cloned repositories, the sandbox and the files in Ibiko's workspace on the phone"),
        OPEN_SETTINGS("open Settings", false, "open the app's settings"),
        RETRY("ask again", false, "send the user's previous message again (the last reply failed or they want another try)"),
        NONE("", false, "anything else: a question, a request, chat, a new task");

        /** Carried out by DVINE's own action ({@code act}), not by running {@link #canonical} as a message. */
        public boolean direct() {
            return ordinal() >= NEW_CHAT.ordinal() && this != NONE;
        }

        /** The wording DVINE's code acts on. */
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
        StringBuilder b = new StringBuilder("You read one message a user sent to DVINE, a phone assistant, and say which of these"
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

    /** The command the reader named, or {@link Command#NONE} when it named none (or wrote something else). */
    public static Command read(String reply) {
        if (reply == null) {
            return Command.NONE;
        }
        String t = reply.replaceAll("(?s)<think>.*?(</think>|$)", "").trim().toLowerCase(Locale.US);
        int at = t.indexOf("command:");
        String name = (at >= 0 ? t.substring(at + 8) : t).trim().replaceAll("^[\"'`*\\s]+", "").replaceAll("[^a-z_].*$", "");
        for (Command c : Command.values()) {
            if (c.name().toLowerCase(Locale.US).equals(name)) {
                return c;
            }
        }
        return Command.NONE;
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
