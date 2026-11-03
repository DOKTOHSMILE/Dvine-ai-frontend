package ai.dvine.social;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A request to look something up on the user's social accounts: "search X for …", "what are people saying about … on
 * Reddit", "check Facebook for …", "… on social media". The network (null: every signed-in one) and what to look for.
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

    static final String NET = "(x|twitter|reddit|facebook|fb|instagram|ig|insta|social media|socials)";
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
}
