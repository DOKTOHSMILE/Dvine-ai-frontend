package ai.ibiko.social;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The social networks Ibiko can use with the user's own accounts, signed in on the phone through the site itself (no
 * API keys): where to sign in, which cookie says an account is signed in, the sites whose cookies belong to it, and
 * how to search it. Pure Java.
 */
public enum Platform {
    FACEBOOK("Facebook", "https://m.facebook.com/login/", "https://m.facebook.com/", new String[]{"c_user"},
            new String[]{"https://www.facebook.com", "https://m.facebook.com", "https://facebook.com"}),
    X("X", "https://x.com/i/flow/login", "https://x.com/home", new String[]{"auth_token"},
            new String[]{"https://x.com", "https://twitter.com"}),
    INSTAGRAM("Instagram", "https://www.instagram.com/accounts/login/", "https://www.instagram.com/", new String[]{"sessionid"},
            new String[]{"https://www.instagram.com", "https://instagram.com"}),
    REDDIT("Reddit", "https://www.reddit.com/login/", "https://www.reddit.com/", new String[]{"reddit_session"},
            new String[]{"https://www.reddit.com", "https://reddit.com", "https://old.reddit.com"}),
    /**
     * The user's own Telegram, through Telegram Web: signed in with the QR code or the phone number. Its sign-in is
     * kept in the page's own storage, not in a cookie ({@link SocialWeb#signedInNow}); one account.
     */
    TELEGRAM("Telegram", "https://web.telegram.org/k/", "https://web.telegram.org/k/", new String[0],
            new String[]{"https://web.telegram.org"});

    public final String shown, login, home;
    final String[] session;
    /** The sites whose cookies are this network's (kept per account, cleared when switching). */
    public final String[] sites;

    Platform(String shown, String login, String home, String[] session, String[] sites) {
        this.shown = shown;
        this.login = login;
        this.home = home;
        this.session = session;
        this.sites = sites;
    }

    /** Whether a site's cookie line ("a=1; c_user=42") holds a signed-in session. */
    public boolean signedIn(String cookies) {
        if (cookies == null) {
            return false;
        }
        for (String part : cookies.split(";")) {
            String p = part.trim();
            for (String s : session) {
                if (p.startsWith(s + "=") && p.length() > s.length() + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The newest posts about something, as the site shows them to a signed-in person. */
    public String searchUrl(String query) {
        String q = enc(query == null ? "" : query.trim());
        switch (this) {
            case FACEBOOK:
                return "https://m.facebook.com/search/posts/?q=" + q;
            case X:
                return "https://x.com/search?q=" + q + "&src=typed_query&f=live";
            case TELEGRAM: {
                // a chat or channel by its name (@lagosnews, t.me/lagosnews); otherwise the chat list
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|t\\.me/|@)([A-Za-z][A-Za-z0-9_]{4,31})$")
                        .matcher(query == null ? "" : query.trim());
                return m.find() && (query.trim().startsWith("@") || query.contains("t.me/"))
                        ? "https://web.telegram.org/k/#@" + m.group(1) : home;
            }
            case INSTAGRAM:
                // Instagram's web search is by hashtag: the words joined
                String tag = query == null ? "" : query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
                return "https://www.instagram.com/explore/tags/" + enc(tag) + "/";
            default:
                return "https://www.reddit.com/search/?q=" + q + "&sort=new";
        }
    }

    /** What is trending or popular on the network now, as the site shows it to a signed-in person. */
    public String trendingUrl() {
        switch (this) {
            case X:
                return "https://x.com/explore/tabs/trending";
            case REDDIT:
                return "https://www.reddit.com/r/popular/";
            case INSTAGRAM:
                return "https://www.instagram.com/explore/";
            case TELEGRAM:
                return home; // the chats, newest first, with their latest messages
            default:
                return "https://m.facebook.com/watch/";
        }
    }

    /** Whether a link belongs to this network (only its own pages are opened with the account). */
    public boolean owns(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        for (String s : sites) {
            if (u.equals(s) || u.startsWith(s + "/") || u.startsWith(s + "?")) {
                return true;
            }
        }
        return false;
    }

    /** "x", "twitter", "fb", "ig"… as a network, or null. */
    public static Platform named(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toLowerCase(Locale.ROOT).replace(" ", "")) {
            case "facebook": case "fb": return FACEBOOK;
            case "x": case "twitter": case "x(twitter)": return X;
            case "instagram": case "ig": case "insta": return INSTAGRAM;
            case "reddit": return REDDIT;
            case "telegram": case "tg": return TELEGRAM;
            default: return null;
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }
}
