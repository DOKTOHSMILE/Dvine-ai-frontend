package ai.ibiko.llm;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a code a small model on the device wrote fits what the user said. A 1-4B model reads the list of the suit's
 * codes and picks one too readily: plan mode for "Hello", a share for "Can you write Java?", automatic commands for a
 * question. A code from the device's model is carried out only when the user's own words ask for that kind of thing;
 * otherwise it is dropped and the rest of the reply is shown as an answer. Harmless look-ups (search, time, sums, a
 * tool, thinking together, passing to the job) always fit. API models are trusted to read meaning and are not checked.
 * Pure Java.
 */
public final class CodeFit {
    private CodeFit() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile("(?is).*\\b(" + regex + ")\\b.*");
    }

    static final Pattern SHARE = p("share|post|tweet|publish|copy( it| this| that)?|send (it|this|that) to|whatsapp|twitter|facebook"
            + "|instagram|linkedin|telegram|tiktok|schedule");
    static final Pattern SOCIAL = p("social|post|tweet|trend\\w*|followers?|comments?|sentiment|profile|whatsapp|twitter|facebook"
            + "|instagram|linkedin|telegram|tiktok|x\\.com");
    static final Pattern PICTURE = p("draw|picture|image|photo\\w*|logo|icon|illustration|artwork|paint\\w*|sketch|wallpaper|poster");
    static final Pattern FILE = p("pdf|docx?|word (document|file)|excel|spreadsheet|xlsx|csv|markdown|txt|file|download|export|save (it|this|that)");
    static final Pattern SHELL = p("run|install|command|terminal|shell|bash|execute|ls|git|pip|npm|python3?|node|gcc|version|compile|uninstall");
    static final Pattern WORK = p("code|coding|program|app|website|web ?page|page|site|html|css|javascript|java|kotlin|python|script"
            + "|build|fix|bug|repo|repository|project|function|class|test|deploy|refactor|audit|commit|push|pull request|landing");
    static final Pattern WRITING = p("write|story|essay|report|article|script|book|chapter|letter|speech|proposal|plan");
    static final Pattern FILES = p("files?|folder|repo|repository|project|workspace|codebase|source");
    static final Pattern CARRY = p("continue|carry on|go on|keep going|resume|finish (it|the job|the work)|where (you|we) (stopped|left off)");
    static final Pattern NEW_CHAT = p("new chat|start over|fresh chat|new conversation|clear (the )?chat");
    static final Pattern NAME = p("call me|my name is|i am called|i'?m called");
    static final Pattern ZIP = p("zip|archive|unzip|extract");
    static final Pattern PLAN = p("plan mode|plan first|make a plan|planning mode|ask (me )?before");
    static final Pattern AUTO = p("auto( mode)?|without asking|don'?t ask|stop asking|ask (me )?(first|before)|always ask");
    static final Pattern AGAIN = p("again|retry|try again|redo|one more time");
    static final Pattern SETTINGS = p("settings?|preferences|options");
    static final Pattern YES_NO = Pattern.compile("(?is)^\\s*(yes|yeah|yep|ok(ay)?|sure|go ahead|do it|send it|confirm|no|nope|cancel|"
            + "stop|don'?t|never mind)\\b.*");

    /** Whether the order fits the user's words (always true for a null order or an empty message's harmless codes). */
    public static boolean fits(BrainOrder order, String userSaid) {
        if (order == null) {
            return true;
        }
        String t = userSaid == null ? "" : userSaid.toLowerCase(Locale.US);
        switch (order.kind) {
            case SEARCH: case TIME: case CALC: case TOOL: case THINKTOGETHER: case HANDOVER: case SWITCH: case SAY: case GREET:
            case CANNOT:
                return true;
            case STAGE: case SCHEDULEPOST:
                return SHARE.matcher(t).matches();
            case SOCIAL: case SOCIALINTEL:
                return SOCIAL.matcher(t).matches();
            case PHOTO: case IMAGE:
                return PICTURE.matcher(t).matches();
            case MAKEFILE:
                return FILE.matcher(t).matches();
            case SHELL:
                return SHELL.matcher(t).matches();
            case WORK:
                return WORK.matcher(t).matches();
            case WRITEPLAN:
                return WRITING.matcher(t).matches();
            case READFILES:
                return FILES.matcher(t).matches();
            case CARRYON:
                return CARRY.matcher(t).matches();
            case NEWCHAT:
                return NEW_CHAT.matcher(t).matches();
            case NAME:
                return NAME.matcher(t).matches();
            case UNZIP: case FINDZIP:
                return ZIP.matcher(t).matches();
            case PLANMODE:
                return PLAN.matcher(t).matches();
            case AUTOON: case AUTOOFF:
                return AUTO.matcher(t).matches();
            case RETRY:
                return AGAIN.matcher(t).matches();
            case SETTINGS:
                return SETTINGS.matcher(t).matches();
            case CONFIRM: case CANCEL:
                // a yes or a no of the user's own; "say yes or no" (the question read back, or heard by the mic) is neither
                return YES_NO.matcher(t).matches() && !t.matches("(?is).*\\byes\\b.*\\bor\\b.*\\bno\\b.*");
            default:
                return false;
        }
    }
}
