package ai.ibiko.dvine1.formulas;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How DVINE 1 operates the app, like any other model: it reads the message and, when it is a clear command, answers
 * with one of the app's function codes on a line ("§SR weather in Lagos"), which the app then carries out through the
 * same gate every model uses. The codes are the ones documented in docs/APP_FUNCTIONS.md (generated from the app's
 * function table); DVINE 1 only writes a code the chat's tools allow. Pure Java.
 */
public final class Commands {
    private Commands() {
    }

    private static final Object[][] RULES = {
            {"NC", Pattern.compile("^(?:please )?(?:start|open|begin|make)? ?(?:a )?new (?:chat|conversation)$"), 0},
            {"OS", Pattern.compile("^(?:please )?open (?:the )?settings$"), 0},
            {"RT", Pattern.compile("^(?:please )?(?:retry|try (?:that )?again|ask (?:that )?again)$"), 0},
            {"TM", Pattern.compile("^what(?:'s| is) the (?:time|date)(?: now| today)?$|^what time is it(?: now)?$|^what(?:'s| is) today'?s date$"), 0},
            {"SW", Pattern.compile("^(?:please )?(?:switch|change|go) to (ivie|obi|ibiko)$"), 1},
            {"NM", Pattern.compile("^(?:please )?call me (.{1,40})$"), 1},
            {"SR", Pattern.compile("^(?:please )?(?:search(?: the web| online| google)? for|google|look up) (.{2,200})$"), 1},
            {"PH", Pattern.compile("^(?:please )?show(?: me)? (?:a |some )?(?:photos?|pictures?|images?) of (.{2,120})$"), 1},
            {"MF", Pattern.compile("^(?:please )?(?:make|save|export)(?: that| this| it)?(?: as| into| to)? (?:a |an )?(pdf|docx|xlsx|csv|md|html|txt)(?: (?:file )?of (?:that|this|it))?$"), 1},
            {"SP", Pattern.compile("^(?:please )?show(?: me)? (?:my |the )?scheduled posts$"), -1},
            {"AC", Pattern.compile("^(?:please )?(post on \\w+: .{1,2000}|share: .{1,2000}|copy: .{1,2000})$"), 1},
            // the rest of the app's controls (docs/APP_FUNCTIONS.md)
            {"YS", Pattern.compile("^(?:yes,? )?(?:go ahead|do it|confirm(?: it)?|send it|post it)$"), 0},
            {"CX", Pattern.compile("^(?:cancel(?: it| that)?|don'?t (?:send|post|do) (?:it|that)|drop it)$"), 0},
            {"PM", Pattern.compile("^(?:(?:turn on |switch to |use )?plan mode(?: on)?|plan (?:it )?first(?:,? then ask)?)$"), 0},
            {"AO", Pattern.compile("^(?:auto(?: mode)? on|(?:turn|switch) on auto(?: mode)?|(?:run|work) without asking(?: me)?)$"), 0},
            {"AF", Pattern.compile("^(?:auto(?: mode)? off|(?:turn|switch) off auto(?: mode)?|ask (?:me )?(?:first|before (?:running|commands|you run|pushing).*))$"), 0},
            {"CO", Pattern.compile("^(?:carry on|keep going|continue|go on|resume)(?: (?:with )?(?:the |my |your )?(?:task|work|job|where you (?:stopped|left off)))?$"), 0},
            {"CA", Pattern.compile("^(?:please )?(?:calculate|compute|work out) (.{1,200})$"), 1},
            {"UZ", Pattern.compile("^(?:please )?(?:unzip|extract) (?:the |that |this )?(?:zip )?(?:file)?$"), 0},
            {"FZ", Pattern.compile("^(?:please )?find (?:my |the |all )?zip(?: files?)?(?: on (?:my |this )?device)?$"), 0},
            {"RF", Pattern.compile("^(?:please )?read (?:the |my )?(?:workspace|project)(?: files)?$|^read the files in (?:the |my )?(?:workspace|project)$"), 0},
            {"TT", Pattern.compile("^(?:please )?think (?:it through |this through )?together(?: (?:about|on) .{2,400})?$"), 0},
            {"IM", Pattern.compile("^(?:please )?((?:generate|create|make|draw|design|paint|sketch)(?: me)? (?:a |an |some )?(?:[a-z]+ ){0,3}(?:image|picture|icon|logo|illustration|drawing|artwork|poster|wallpaper|sticker|avatar|cartoon|painting)s?\\b.*)$"), 1},
            {"WP", Pattern.compile("^(?:please )?write (?:me )?(?:a |an )(?:long |short |full )?(?:story|essay|report|article|speech|chapter|book|poem|letter)\\b.*$"), 0},
    };

    /** Real work on code for the agents: written, fixed, built, tested ("fix the login bug in my app"). */
    private static final Pattern WORK = Pattern.compile("^(?:please |can you |could you )?(?:write|create|build|make|fix|debug|refactor|test|code|implement|add|update|review|audit|deploy|push|commit)\\b.*\\b(?:code|app|apps|function|script|program|bug|bugs|feature|website|site|api|class|module|repo|repository|project|tests?|endpoint|page|screen|component|error|crash|build|pr|pull request|branch)\\b.*$");

    /**
     * The function code line for a message that is a clear command ("§SR weather in Lagos"), or null when it is a
     * question or anything else. {@code allowed}: whether the chat's tools allow a code ("SR").
     */
    public static String code(String message, java.util.function.Predicate<String> allowed) {
        String raw = message == null ? "" : message.trim().replaceAll("[.!]+$", "").replaceAll("\\s+", " ");
        String low = raw.toLowerCase(Locale.US).replaceAll("\\?+$", "");
        for (Object[] r : RULES) {
            Matcher m = ((Pattern) r[1]).matcher(low);
            if (!m.matches()) {
                continue;
            }
            String code = (String) r[0];
            if (allowed != null && !allowed.test(code)) {
                return null;
            }
            int g = (Integer) r[2];
            if (g < 0) {
                return "§" + code + " LIST";
            }
            if (g == 0) {
                return "§" + code;
            }
            // the argument as the user wrote it (names keep their capitals)
            String arg = raw.substring(Math.min(raw.length(), m.start(g)), Math.min(raw.length(), m.end(g))).replaceAll("\\?+$", "");
            return "§" + code + " " + ("SW".equals(code) || "MF".equals(code) ? arg.toLowerCase(Locale.US) : arg);
        }
        // a code task DVINE 1 can carry out itself: one shell command, approved by the pilot before it runs
        String shell = CodeTasks.command(message);
        if (shell != null && (allowed == null || allowed.test("SH"))) {
            return "§SH " + shell;
        }
        if (WORK.matcher(low).matches() && (allowed == null || allowed.test("WK"))) {
            return "§WK";
        }
        return null;
    }
}
