package ai.ibiko.social;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A request about the user's social accounts. Only two of these become {@code social.*} connector tools
 * ({@code ConnectorStore}): a lookup ({@link #parse}) is {@code social.search} or {@code social.trending},
 * and a link to one page is {@code social.open}. The others are not connectors and must not be described as
 * if a tool resolved them:
 * <ul>
 * <li>{@link #connect} opens the in-app sign-in page. There is no {@code social.connect} tool.</li>
 * <li>{@link #compose} only drafts words. There is no {@code social.compose} tool; posting still waits for a yes.</li>
 * <li>{@link #help} is a spoken explanation. There is no {@code social.help} tool.</li>
 * </ul>
 * Networks a lookup can actually resolve: Facebook, X and Reddit (search and a real trending page).
 * Instagram search resolves only as a hashtag page; its trending page is Explore, not a trends list.
 * Telegram has no word search and no trending page. A Telegram lookup resolves only for a chat named
 * {@code @name} or {@code t.me/name} ({@code social.search} / {@code social.open}). Anything else stays unresolved.
 * Pure Java.
 */
public final class SocialAsk {
    public final Platform platform;
    public final String query;
    /** What is trending or popular on the network ("check trending posts on X"): its Trending page, not a search. */
    public final boolean trending;

    private SocialAsk(Platform platform, String query, boolean trending) {
        this.platform = platform;
        this.query = query;
        this.trending = trending;
    }

    static final String NET = "(x|twitter|reddit|facebook|fb|instagram|ig|insta|telegram|tg|social media|socials)";
    /** "check trending post on x", "what's trending on twitter", "popular on reddit", "X trends". */
    private static final Pattern TRENDING = Pattern.compile("(?i)^(?:please |can you |could you )?(?:check|show(?: me)?|see|get|look at|"
            + "what(?:'s| is| are)|whats|tell me)?\\s*(?:the |what(?:'s| is) )?(?:trending|popular|hot|top|viral)(?:\\s+(?:posts?|topics?|tweets?|news|now|today|things|stuff))*"
            + "(?:\\s+right now| now| today)?\\s+(?:on|in)\\s+" + NET + "(?:\\s+(?:now|today|right now))?[.?!]*$");
    private static final Pattern TRENDS = Pattern.compile("(?i)^(?:check |show(?: me)? )?(?:the )?" + NET + "\\s+(?:trends|trending)(?:\\s+(?:now|today))?[.?!]*$");

    private static final Pattern[] FORMS = {
            // search X for Q / check reddit for Q / look on facebook for Q
            Pattern.compile("(?i)^(?:please |can you |could you )?(?:search|check|look (?:on|through|at)|scan|browse)\\s+(?:on\\s+)?" + NET
                    + "\\s+(?:for|about|on)\\s+(.+?)[.?!]*$"),
            // what are people saying (on X) about Q (on X)
            Pattern.compile("(?i)^(?:what(?:'s| is| are)|whats)\\s+(?:people|everyone|folks|users)\\s+(?:saying|posting|talking about|tweeting)"
                    + "(?:\\s+on\\s+" + NET + ")?\\s+(?:about\\s+|on\\s+|regarding\\s+)?(.+?)(?:\\s+on\\s+" + NET + ")?[.?!]*$"),
            // what is X saying about Q
            Pattern.compile("(?i)^(?:what(?:'s| is)|whats)\\s+" + NET + "\\s+saying\\s+(?:about|on)\\s+(.+?)[.?!]*$"),
            // look up / find / search Q on X
            Pattern.compile("(?i)^(?:please |can you |could you )?(?:look up|find|search(?: for)?|check)\\s+(.+?)\\s+(?:on|in)\\s+" + NET
                    + "(?:\\s+(?:for me|please))?[.?!]*$"),
    };

    /** "connect my Facebook", "sign in to X": the network, or null. Not a connector — the sign-in page, not {@code social.connect}. */
    public static Platform connect(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)^(?:please |can you |could you |help me )?(?:connect|link|add|sign in to|sign into|log in to|log into|login to|set up)"
                + "\\s+(?:my |the |a )?" + NET + "(?:\\s+account)?(?:\\s+(?:to|with|on) (?:dvine|you|the app|here))?[.!?]*$").matcher(message.trim());
        return m.matches() && !m.group(1).toLowerCase(java.util.Locale.ROOT).startsWith("social") ? Platform.named(m.group(1)) : null;
    }

    /** "how do I access social media": an explanation, not a {@code social.help} tool (there isn't one). */
    public static boolean help(String message) {
        return message != null && message.trim().matches("(?i)^(?:how (?:do|can) i (?:access|use|get to|open|connect)|what can you do (?:with|on)|"
                + "how do you (?:use|access)|where (?:are|is))\\s+(?:my |the )?(?:social media|socials|social accounts?|social media (?:functions|features|accounts))\\b.*");
    }

    /** "generate a new post for Facebook about our sale": a draft only. Not a {@code social.compose} tool; nothing is posted. */
    public static String[] compose(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)^(?:please |can you |could you )?(?:generate|write|create|make|draft|compose|give me)\\s+(?:me\\s+)?(?:a |an )?(?:new |short |good )?"
                + "(?:post|tweet|caption|status|update)\\s+(?:for|on|to) (?:my )?" + NET + "(?:\\s+(?:about|on|for|announcing|saying) (.+?))?[.!?]*$").matcher(message.trim());
        if (!m.matches()) {
            return null;
        }
        Platform p = Platform.named(m.group(1));
        return new String[]{p == null ? "social media" : p.shown, m.group(2) == null ? "" : m.group(2).trim()};
    }

    /** The request, or null when the message isn't one. */
    public static SocialAsk parse(String message) {
        if (message == null) {
            return null;
        }
        String t = message.trim();
        Matcher tr = TRENDING.matcher(t);
        Matcher ts = TRENDS.matcher(t);
        if (tr.matches() || ts.matches()) {
            String n = (tr.matches() ? tr.group(1) : ts.group(1)).toLowerCase(java.util.Locale.ROOT);
            return new SocialAsk(n.startsWith("social") ? null : Platform.named(n), "", true);
        }
        for (int i = 0; i < FORMS.length; i++) {
            Matcher m = FORMS[i].matcher(t);
            if (!m.matches()) {
                continue;
            }
            String net, q;
            if (i == 0 || i == 2) {
                net = m.group(1);
                q = m.group(2);
            } else if (i == 1) {
                net = m.group(1) != null ? m.group(1) : m.group(3);
                q = m.group(2);
                if (net == null) {
                    continue; // "what are people saying about …" with no network: an ordinary question
                }
            } else {
                q = m.group(1);
                net = m.group(2);
            }
            q = q == null ? "" : q.trim().replaceAll("^[\"“']+|[\"”']+$", "");
            if (q.isEmpty()) {
                return null;
            }
            String n = net.toLowerCase(java.util.Locale.ROOT);
            return new SocialAsk(n.startsWith("social") ? null : Platform.named(n), q, false);
        }
        return null;
    }

    /**
     * The {@code social.*} tool that can carry this lookup out, or null when none can.
     * {@code social.search} and {@code social.trending} are the only lookup tools. Telegram word search and
     * Telegram trending return null (the chat list is not a search and not a trends page). A Telegram
     * {@code @name} or {@code t.me/name} is {@code social.search}.
     */
    public String connector() {
        if (platform == Platform.TELEGRAM) {
            if (trending || !telegramChat(query)) {
                return null;
            }
            return "social.search";
        }
        return trending ? "social.trending" : "social.search";
    }

    /** A Telegram chat the connector can open ({@code @name} or {@code t.me/name}), not a free-text search. */
    public static boolean telegramChat(String query) {
        if (query == null) {
            return false;
        }
        String q = query.trim();
        return q.matches("(?i)^@[A-Za-z][A-Za-z0-9_]{4,31}$")
                || q.matches("(?i)^(?:https?://)?t\\.me/[A-Za-z][A-Za-z0-9_]{4,31}/?$");
    }
}
