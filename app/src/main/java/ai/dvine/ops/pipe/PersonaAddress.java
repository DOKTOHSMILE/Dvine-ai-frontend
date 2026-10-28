package ai.dvine.ops.pipe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The three personas in the app are Obi, Ivie, and Ibiko. "EV" is how speech often hears Ivie.
 * "Aka" is not a persona in this codebase.
 */
public final class PersonaAddress {
    private static final Pattern LEAD = Pattern.compile(
            "^(?:hey |hi |hello |ok |okay )?(obi|obie|obey|ivie|evie|eva|ev|ivy|ivee|ibiko|ibeko|ebiko|ibico|aka|akah|aca)\\b[,!:]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    public static final class Address {
        /** "obi", "ivie", "ibiko", or null when the sentence names nobody. */
        public final String persona;
        /** A name the user used that is not one of the three, or null. */
        public final String unknown;
        /** The request with the name taken off. */
        public final String request;

        Address(String persona, String unknown, String request) {
            this.persona = persona;
            this.unknown = unknown;
            this.request = request == null ? "" : request;
        }

        public String clarify() {
            return "I don't have a persona named " + unknown + ". The three are Obi, Ivie, and Ibiko.";
        }
    }

    public Address read(String text) {
        String raw = text == null ? "" : text.trim();
        Matcher m = LEAD.matcher(raw);
        if (!m.matches()) {
            return new Address(null, null, raw);
        }
        String token = m.group(1).toLowerCase(Locale.US);
        String rest = m.group(2) == null ? "" : m.group(2).trim();
        String known = canonical(token);
        if (known == null) {
            return new Address(null, display(token), rest);
        }
        return new Address(known, null, rest.isEmpty() ? raw : rest);
    }

    /** Null when the token is not a known persona (including Aka). */
    public static String canonical(String token) {
        switch (token) {
            case "obi":
            case "obie":
            case "obey":
                return "obi";
            case "ivie":
            case "evie":
            case "eva":
            case "ev":
            case "ivy":
            case "ivee":
                return "ivie";
            case "ibiko":
            case "ibeko":
            case "ebiko":
            case "ibico":
                return "ibiko";
            default:
                return null;
        }
    }

    private static String display(String token) {
        if (token.length() == 0) {
            return token;
        }
        return Character.toUpperCase(token.charAt(0)) + token.substring(1);
    }
}
