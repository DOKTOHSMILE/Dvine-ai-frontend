package ai.dvine.turn;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The personas' names as speech recognition hears them: "Evie", "Ivy", "Eevee" are Ivie; "Obie", "Obee" (and "obey"
 * where it can only be a name) are Obi. Applied to what was heard before anything reads it. Pure Java.
 */
public final class Names {
    private Names() {
    }

    private static final Pattern IVIE = Pattern.compile("\\b(evie|evey|evee|eevee|eevie|ivee|ivey|ivy|ivi|iffy|evi)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OBI = Pattern.compile("\\b(obie|obee|oby|obbie|obi's|o\\.b\\.)\\b", Pattern.CASE_INSENSITIVE);
    /** "obey" only where a name is: at the start, after a greeting or a switch, or alone at the end after a comma. */
    private static final Pattern OBEY = Pattern.compile(
            "(^|\\b(?:switch to|switch back to|talk to|speak to|speak with|hey|hi|hello|ask|tell|with|call|bring|and)\\s+)obey\\b|,\\s*obey$",
            Pattern.CASE_INSENSITIVE);

    public static String normalize(String heard) {
        if (heard == null) {
            return null;
        }
        String s = IVIE.matcher(heard).replaceAll("Ivie");
        s = OBI.matcher(s).replaceAll("Obi");
        java.util.regex.Matcher m = OBEY.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String lead = m.group(1) != null ? m.group(1) : ", ";
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(m.group(1) != null ? lead + "Obi" : ", Obi"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Pattern SWITCH = Pattern.compile(
            "^(?:(?:can you |could you |please |let me |i want to |i'd like to |i would like to )?"
                    + "(?:switch(?: back| over)? to|change to|go to|talk to|speak to|speak with|chat with|communicate with|bring(?: in| out| me)?|"
                    + "call|get|show me|i want|give me|where is|where's)\\s+)(ivie|obi)\\b(.*)$");

    /**
     * The persona a request asks to bring forward ("switch to Ivie", "communicate with Ivie your other half", "let me
     * talk to Obi"), or null. A request with more to say ("talk to Ivie about the weather") isn't only a switch.
     */
    public static String switchTarget(String said) {
        if (said == null) {
            return null;
        }
        String t = said.toLowerCase(Locale.US).replaceAll("[.!?,]+", " ").replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher m = SWITCH.matcher(t);
        if (!m.matches()) {
            return null;
        }
        String rest = m.group(2).trim();
        if (rest.split(" ").length > 4 || rest.matches("^(about|and|to|that|if|what|how|why|when) .*")) {
            return null;
        }
        return m.group(1);
    }
}
