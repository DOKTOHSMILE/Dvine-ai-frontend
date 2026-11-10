package ai.dvine.main;

/**
 * The brain's SOCIAL order: connect, help, write a post, or search and trending. The screen still speaks, opens
 * the sign-in pages, and runs the search.
 */
public final class SocialFlow {

    /** What the screen does for one social order. */
    public interface Host {
        /** The persona speaking. */
        String agent();

        void say(String line);

        void openAccounts();

        void openLogin(ai.dvine.social.Platform platform);

        /** True when this network already has an account in use. */
        boolean signedIn(ai.dvine.social.Platform platform);

        /** True when any network has an account in use. */
        boolean anySignedIn();

        boolean online();

        /** The model writes the post. {@code tokens} is how long the reply may be. */
        void writePost(String prompt, int tokens);

        /** The model writes several versions of a post for {@code platform} about {@code words}; the pilot picks one. */
        default void writeVersions(ai.dvine.social.Platform platform, String words) {
        }

        /** A comment under the post at {@code link} on {@code app} ("facebook", "x"): read it, write one, ask first. */
        default void writeComment(String app, String link, String what) {
        }

        int chatTokens();

        void search(String question, String call);
    }

    /** The three pieces of an order argument: verb, network, and the words after the colon. */
    public static final class Parts {
        public final String verb, net, words;

        Parts(String verb, String net, String words) {
            this.verb = verb;
            this.net = net;
            this.words = words;
        }
    }

    private final Host host;

    public SocialFlow(Host host) {
        this.host = host;
    }

    /** "post facebook: our sale" is verb post, network facebook, words "our sale". */
    public static Parts parts(String arg) {
        String a = arg == null ? "" : arg.trim();
        String verb = a.contains(" ") ? a.substring(0, a.indexOf(' ')) : a;
        String rest = a.length() > verb.length() ? a.substring(verb.length()).trim() : "";
        String net = rest.contains(":") ? rest.substring(0, rest.indexOf(':')).trim() : rest.split("\\s+")[0];
        String words = rest.contains(":") ? rest.substring(rest.indexOf(':') + 1).trim() : "";
        return new Parts(verb, net, words);
    }

    public void run(String question, String arg) {
        Parts p = parts(arg);
        ai.dvine.social.Platform platform = ai.dvine.social.Platform.named(p.net);
        switch (p.verb) {
            case "connect": {
                if (platform == null) {
                    host.openAccounts();
                    host.say("Here are your social accounts: sign in to any of them.");
                    return;
                }
                boolean has = host.signedIn(platform);
                host.say(has ? "You're already signed in to " + platform.shown + ". Here are your accounts: add another,"
                        + " rename one, or switch it off in Connectors." : "Sign in to " + platform.shown + " on the page I'm opening. Your password"
                        + " goes only to " + platform.shown + "; I keep the sign-in on this device, encrypted.");
                if (has) {
                    host.openAccounts();
                } else {
                    host.openLogin(platform);
                }
                return;
            }
            case "help":
                host.say("Sign in to your accounts first: menu, Connectors, then Facebook, X, Instagram or Reddit"
                        + " (or say \u201cconnect my Facebook\u201d). Then ask me: \u201csearch X for the fuel price\u201d, \u201cwhat are people"
                        + " saying on Reddit about Flutter\u201d, \u201ccheck trending posts on X\u201d, or \u201cwrite a post for Facebook about"
                        + " our sale\u201d, then \u201cpost it on Facebook\u201d to open Facebook with it ready. I also check facts against what people post.");
                return;
            case "comment": {
                java.util.regex.Matcher link = java.util.regex.Pattern.compile("https?://\\S+").matcher(p.words);
                if (!link.find()) {
                    host.say("Send me the link of the post to comment under (Share, then Copy link).");
                    return;
                }
                host.writeComment(p.net, link.group(), (p.words.substring(0, link.start()) + p.words.substring(link.end())).trim());
                return;
            }
            case "post": {
                if (platform == ai.dvine.social.Platform.FACEBOOK || platform == ai.dvine.social.Platform.X) {
                    host.writeVersions(platform, p.words); // several versions to choose from, then posted from the account
                    return;
                }
                String where = platform == null ? "social media" : platform.shown;
                host.writePost("Write one ready-to-post " + where + " post" + (p.words.isEmpty() ? "" : " about " + p.words)
                        + ": the post text only, natural and engaging, the right length for " + where + ", with a few hashtags if"
                        + " they suit it. Then one short line: say \u201cpost it on " + where + "\u201d and I'll open " + where + " with it ready.",
                        host.chatTokens());
                return;
            }
            default: {
                boolean trending = "trending".equals(p.verb);
                String where = platform == null ? "social media" : platform.shown;
                boolean none = platform == null ? !host.anySignedIn() : !host.signedIn(platform);
                if (none) {
                    host.say("I need you signed in to " + where + " first: open the menu, then Connectors,"
                            + " and sign in there. Your password goes only to the site.");
                    return;
                }
                if (!host.online()) {
                    host.say("I need the network to read " + where + ".");
                    return;
                }
                String n = platform == null ? "" : platform.name().toLowerCase(java.util.Locale.ROOT);
                host.search(question, trending ? "social.trending " + n
                        : "social.search " + (n.isEmpty() ? "" : n + ": ") + (p.words.isEmpty() ? question : p.words));
            }
        }
    }
}
