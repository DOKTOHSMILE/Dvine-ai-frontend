package ai.dvine.social.intel;

import java.util.Locale;

/**
 * The privacy rules every social capability passes through. Before: a request for private messages, an inbox, a
 * private account's hidden content, passwords, or a person's home address, phone or email is refused. After: any
 * token or cookie is removed, and on another person's profile phone numbers and email addresses are masked. Pure Java.
 */
public final class SocialPrivacyGuard {
    private SocialPrivacyGuard() {
    }

    /** Why a request is refused, or null when it may run. */
    public static String refuse(String capability, String args) {
        String a = (args == null ? "" : args).toLowerCase(Locale.US);
        if (a.matches("(?s).*(/messages|/direct/|/inbox|/dm\\b|/chat/|messages/t/|\\bdms?\\b|direct message|private message|inbox).*")) {
            return "Private messages are never read.";
        }
        if (a.matches("(?s).*\\b(password|passcode|2fa|two.factor|login code|session|cookie|token)\\b.*")) {
            return "Credentials are never read or shared.";
        }
        if (a.matches("(?s).*\\b(home address|where (s?he|they) lives?|phone number|email address|ip address|date of birth|ssn|social security)\\b.*")
                && !"social.profile.read_own".equals(capability)) {
            return "Hidden personal details (home, phone, email, date of birth) are not looked up.";
        }
        if (a.matches("(?s).*\\b(bypass|get around|see private|unlock private|hidden posts)\\b.*")) {
            return "Private accounts and hidden content stay private.";
        }
        return null;
    }

    /** The result as the brain may see it: no credentials, and for another person's profile no phone or email. */
    public static String clean(String text, boolean ownAccount) {
        String t = text == null ? "" : text;
        t = t.replaceAll("(?i)\\b(sessionid|auth_token|c_user|reddit_session|ct0|csrftoken|xs|access_token|bearer)\\s*[=:]\\s*\\S+", "[removed]");
        t = t.replaceAll("\\b(?:ghp|gho|sk|xox[abp])[-_][A-Za-z0-9_-]{8,}\\b", "[removed]");
        if (!ownAccount) {
            t = t.replaceAll("[\\w.+-]+@[\\w-]+\\.[\\w.]+", "[email hidden]");
            t = t.replaceAll("(?<![\\w])\\+?\\d[\\d ()-]{8,}\\d(?![\\w])", "[phone hidden]");
        }
        return t;
    }
}
