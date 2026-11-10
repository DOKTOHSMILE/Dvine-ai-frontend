package ai.ibiko.social.intel;

import java.util.ArrayList;
import java.util.List;

/**
 * The social capabilities the brain can discover and call (with the suit's code §SI), the same definitions for an
 * API model and the device's model: each with its parameters, what it returns, what it needs (network, the user's
 * sign-in) and whether a provider can serve it now. Pure Java.
 */
public final class SocialCapabilityRegistry {
    public static final String VERSION = "1.0";

    /** What a capability needs before it may run. */
    public enum Permission { NONE, PUBLIC_DATA, OWN_ACCOUNT }

    /** One capability. */
    public static final class Capability {
        public final String id, description, params, returns;
        public final Permission permission;
        public final boolean network;
        /** What kind of provider can serve it: "search", "public_profile", "own_profile", or "" for none. */
        public final String needs;

        Capability(String id, String description, String params, String returns, Permission permission, boolean network, String needs) {
            this.id = id;
            this.description = description;
            this.params = params;
            this.returns = returns;
            this.permission = permission;
            this.network = network;
            this.needs = needs;
        }
    }

    public static final List<Capability> ALL = new ArrayList<Capability>();

    static {
        add("social.post.investigate", "investigate a message or post: its claims, the people and events in it, the original"
                + " post if found, related posts and web evidence", "<the message or post text, or its link>", "investigation report",
                Permission.PUBLIC_DATA, true, "search");
        add("social.post.identify_claims", "list the claims in a message, factual or opinion", "<the message>", "claims",
                Permission.NONE, false, "");
        add("social.search.posts", "search public posts", "[platform] <words>", "posts with platform, author, date, engagement, link",
                Permission.PUBLIC_DATA, true, "search");
        add("social.search.similar", "find posts discussing the same subject as a message (ranked by meaning, duplicates removed)",
                "<the message or post>", "ranked posts", Permission.PUBLIC_DATA, true, "search");
        add("social.search.by_author", "the recent public posts of one account", "<platform> <username or link>", "posts",
                Permission.PUBLIC_DATA, true, "public_profile");
        add("social.sentiment.analyze", "what people say about a topic: positive, negative, neutral, themes, concerns, questions,"
                + " with the sample size and limits", "<topic>", "sentiment report", Permission.PUBLIC_DATA, true, "search");
        add("social.accounts.list", "the social accounts connected on this device and what each can do", "", "accounts",
                Permission.NONE, false, "");
        add("social.profile.read_own", "read and analyse the user's own connected profile", "<platform>", "profile with recent posts",
                Permission.OWN_ACCOUNT, true, "own_profile");
        add("social.profile.read_public", "read a public profile and its recent public posts", "<platform> <username or link>",
                "profile with recent posts", Permission.PUBLIC_DATA, true, "public_profile");
    }

    private static void add(String id, String d, String p, String r, Permission perm, boolean net, String needs) {
        ALL.add(new Capability(id, d, p, r, perm, net, needs));
    }

    private SocialCapabilityRegistry() {
    }

    public static Capability get(String id) {
        for (Capability c : ALL) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** Whether one of {@code providers} can serve it now, and if not, why. */
    public static String availability(Capability c, List<SocialProvider> providers) {
        if (c.needs.isEmpty()) {
            return "available";
        }
        List<String> why = new ArrayList<String>();
        for (SocialProvider p : providers) {
            boolean can = c.needs.equals("search") ? p.canSearch() : c.needs.equals("own_profile") ? p.canReadOwnProfile()
                    : p.canReadPublicProfile();
            if (can && p.available()) {
                return "available";
            }
            if (can) {
                why.add(p.platform() + ": " + p.unavailableReason());
            }
        }
        return "unavailable (" + (why.isEmpty() ? "no provider" : String.join("; ", why)) + ")";
    }

    /** What the brain is told: each capability, how to call it, and whether it can run now. */
    public static String forBrain(List<SocialProvider> providers) {
        StringBuilder b = new StringBuilder(" Social intelligence (call with \u00A7SI <capability> <arguments>; results come back"
                + " to you with their sources):");
        for (Capability c : ALL) {
            b.append(' ').append(c.id).append(c.params.isEmpty() ? "" : " " + c.params).append(": ").append(c.description);
            String a = availability(c, providers);
            if (!a.equals("available")) {
                b.append(" [").append(a).append(']');
            }
            b.append(';');
        }
        return b.toString();
    }

    /** The registry as JSON (for the dashboard, logs and tests). */
    public static String json(List<SocialProvider> providers) {
        StringBuilder b = new StringBuilder("{\"category\":\"social_media\",\"version\":\"" + VERSION + "\",\"capabilities\":[");
        for (int i = 0; i < ALL.size(); i++) {
            Capability c = ALL.get(i);
            b.append(i == 0 ? "" : ",").append("{\"id\":\"").append(c.id).append("\",\"description\":\"").append(esc(c.description))
                    .append("\",\"params\":\"").append(esc(c.params)).append("\",\"returns\":\"").append(esc(c.returns))
                    .append("\",\"permission\":\"").append(c.permission).append("\",\"network\":").append(c.network)
                    .append(",\"availability\":\"").append(esc(availability(c, providers))).append("\"}");
        }
        return b.append("]}").toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
