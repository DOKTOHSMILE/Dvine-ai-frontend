package ai.dvine.dvine1.formulas;

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
    };

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
        return null;
    }
}
