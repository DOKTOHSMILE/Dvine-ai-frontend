package ai.dvine.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The only authorization for pause, resume, cancel and stop. A word in a chat, a model reply, code, a document,
 * a terminal log or DVine's own speech is not permission. A button is. Command mode is, for the one thing the user
 * says next. Pure Java.
 */
public final class DvineCommandGateway {
    public enum Source {
        USER_BUTTON, USER_COMMAND, USER_CHAT, MODEL_TEXT, GENERATED_CODE, DOCUMENT, TERMINAL, TTS, AGENT
    }

    public enum Action {
        PAUSE, RESUME, CANCEL, STOP
    }

    /** One request. {@code hint} is what the caller thinks; the gateway does not trust it from untrusted sources. */
    public static final class Intent {
        public final Source source;
        public final Action hint;
        public final String text;
        public final boolean confirmed;

        public Intent(Source source, Action hint, String text, boolean confirmed) {
            this.source = source == null ? Source.USER_CHAT : source;
            this.hint = hint;
            this.text = text == null ? "" : text;
            this.confirmed = confirmed;
        }

        public static Intent button(Action action) {
            return new Intent(Source.USER_BUTTON, action, "", true);
        }

        public static Intent chat(String text) {
            return new Intent(Source.USER_CHAT, null, text, false);
        }

        public static Intent command(String text) {
            return new Intent(Source.USER_COMMAND, null, text, false);
        }

        public static Intent model(String text) {
            return new Intent(Source.MODEL_TEXT, null, text, false);
        }

        public static Intent code(String text) {
            return new Intent(Source.GENERATED_CODE, null, text, false);
        }

        public static Intent document(String text) {
            return new Intent(Source.DOCUMENT, null, text, false);
        }

        public static Intent terminal(String text) {
            return new Intent(Source.TERMINAL, null, text, false);
        }

        public static Intent spokenByDvine(String text) {
            return new Intent(Source.TTS, null, text, false);
        }

        public static Intent agent(String text) {
            return new Intent(Source.AGENT, null, text, false);
        }
    }

    /** Tasks that exist. The gateway never stops every one of them because a sentence was vague. */
    public static final class Board {
        public String currentId;
        public final List<String> ids = new ArrayList<String>();
        public final List<String> names = new ArrayList<String>();
        public final List<Boolean> destructive = new ArrayList<Boolean>();

        public void add(String id, String name, boolean destructive) {
            ids.add(id);
            names.add(name == null ? "" : name);
            this.destructive.add(destructive);
        }
    }

    public static final class Result {
        public final boolean accepted;
        public final boolean needsConfirmation;
        public final boolean clarify;
        public final Action action;
        public final String targetId;
        public final String reason;

        Result(boolean accepted, boolean needsConfirmation, boolean clarify, Action action, String targetId, String reason) {
            this.accepted = accepted;
            this.needsConfirmation = needsConfirmation;
            this.clarify = clarify;
            this.action = action;
            this.targetId = targetId;
            this.reason = reason;
        }

        static Result reject(String reason) {
            return new Result(false, false, false, null, null, reason);
        }
    }

    private static final DvineCommandGateway APP = new DvineCommandGateway();
    private boolean armed;

    public static DvineCommandGateway app() {
        return APP;
    }

    /** The next user utterance may be a control. One utterance, then it is off. Speaking into the mic does not arm this. */
    public void arm() {
        armed = true;
    }

    public void disarm() {
        armed = false;
    }

    public boolean armed() {
        return armed;
    }

    public Result submit(Intent intent) {
        return submit(intent, new Board());
    }

    public Result submit(Intent intent, Board board) {
        if (intent == null) {
            return Result.reject("no command");
        }
        if (untrusted(intent.source)) {
            return Result.reject("that source cannot control a task");
        }
        if (intent.source == Source.USER_CHAT) {
            return Result.reject("ordinary conversation is not a command");
        }
        if (intent.source == Source.USER_BUTTON) {
            if (intent.hint == null) {
                return Result.reject("the button did not name an action");
            }
            return new Result(true, false, false, intent.hint, board == null ? null : board.currentId, "button");
        }
        if (!armed) {
            return Result.reject("command mode is not on");
        }
        armed = false;
        return fromWords(intent.text, intent.confirmed, board == null ? new Board() : board);
    }

    private static boolean untrusted(Source source) {
        return source == Source.MODEL_TEXT || source == Source.GENERATED_CODE || source == Source.DOCUMENT
                || source == Source.TERMINAL || source == Source.TTS || source == Source.AGENT;
    }

    /** What an armed command means. A question, a quote, or a JSON blob is not one. */
    private Result fromWords(String text, boolean confirmed, Board board) {
        String raw = text == null ? "" : text.trim();
        if (raw.startsWith("{") || raw.startsWith("[")) {
            return Result.reject("a document is not a command");
        }
        String n = raw.toLowerCase(Locale.US).replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        if (n.isEmpty() || n.indexOf('?') >= 0 || n.startsWith("what ") || n.startsWith("why ") || n.startsWith("how ")
                || n.startsWith("if ") || n.contains("someone told") || n.contains("told me") || n.contains("the word ")
                || n.contains("what happens") || n.contains("if i ") || n.contains(" said ") || n.startsWith("say ")) {
            return Result.reject("that is conversation, not a command");
        }
        Action action;
        String rest;
        if (n.matches("^(please )?(pause|hold on|hold)\\b.*")) {
            action = Action.PAUSE;
            rest = n.replaceFirst("^(please )?(pause|hold on|hold)\\s*", "");
        } else if (n.matches("^(please )?resume\\b.*") || n.matches("^(please )?(carry on|continue)$")) {
            action = Action.RESUME;
            rest = n.replaceFirst("^(please )?(resume|carry on|continue)\\s*", "");
        } else if (n.matches("^(please )?cancel\\b.*")) {
            action = Action.CANCEL;
            rest = n.replaceFirst("^(please )?cancel\\s*", "");
        } else if (n.matches("^(please )?stop\\b.*")) {
            action = Action.STOP;
            rest = n.replaceFirst("^(please )?stop\\s*", "");
        } else {
            return Result.reject("not a control command");
        }
        String name = rest.replaceFirst("^(the |that |this |my )?", "").trim();
        if (name.isEmpty() || name.equals("it") || name.equals("that") || name.equals("this")
                || name.equals("current") || name.startsWith("current ") || name.startsWith("this task")
                || name.startsWith("that and ") || name.equals("that task")) {
            name = null;
        } else {
            int and = name.indexOf(" and ");
            if (and > 0) {
                name = name.substring(0, and).trim();
            }
        }
        String target = pick(board, action, name);
        if ("ambiguous".equals(target)) {
            return new Result(true, false, true, action, null, "which task");
        }
        if ("missing".equals(target)) {
            return Result.reject("no task by that name");
        }
        if (target != null && destructive(board, target) && !confirmed) {
            return new Result(false, true, false, action, target, "confirm");
        }
        return new Result(true, false, false, action, target, "command");
    }

    private static String pick(Board board, Action action, String name) {
        if (board.ids.isEmpty()) {
            return name == null ? board.currentId : "missing";
        }
        if (name == null) {
            if (action == Action.RESUME) {
                if (board.ids.size() > 1) {
                    return "ambiguous";
                }
                return board.ids.get(0);
            }
            if (board.currentId != null) {
                return board.currentId;
            }
            return board.ids.size() == 1 ? board.ids.get(0) : "ambiguous";
        }
        String want = name.toLowerCase(Locale.US);
        String found = null;
        for (int i = 0; i < board.ids.size(); i++) {
            String label = board.names.get(i).toLowerCase(Locale.US);
            if (label.contains(want) || want.contains(label) || shares(label, want)) {
                if (found != null) {
                    return "ambiguous";
                }
                found = board.ids.get(i);
            }
        }
        return found == null ? "missing" : found;
    }

    private static boolean shares(String label, String want) {
        for (String w : want.split(" ")) {
            if (w.length() >= 4 && label.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean destructive(Board board, String id) {
        for (int i = 0; i < board.ids.size(); i++) {
            if (board.ids.get(i).equals(id) && board.destructive.get(i)) {
                return true;
            }
        }
        return false;
    }
}
