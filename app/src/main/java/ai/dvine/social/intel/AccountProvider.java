package ai.dvine.social.intel;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.social.Platform;
import ai.dvine.social.SocialAccounts;
import ai.dvine.social.SocialWeb;

/**
 * Facebook, X, Instagram or Reddit through the user's own account, signed in on this device (no API key): search,
 * a public profile page, and the user's own profile. Reads go through {@link SocialWeb}, so the per-account limits,
 * the pause on a site's warning and the encrypted sign-in all still apply; no cookie or token ever reaches the
 * result. A page read this way has the words but rarely a reliable author, date or count, so those stay empty.
 */
public final class AccountProvider implements SocialProvider {
    private final Context context;
    private final Platform platform;

    public AccountProvider(Context context, Platform platform) {
        this.context = context.getApplicationContext();
        this.platform = platform;
    }

    @Override
    public String platform() {
        return platform.shown;
    }

    private SocialAccounts.Account account() {
        return SocialWeb.accounts(context).inUse(platform);
    }

    @Override
    public boolean available() {
        SocialAccounts.Account a = account();
        return a != null && a.signedIn && SocialWeb.accounts(context).on(platform, true);
    }

    @Override
    public String unavailableReason() {
        if (account() == null) {
            return "no " + platform.shown + " account signed in on this device (menu → Social accounts)";
        }
        return available() ? "" : platform.shown + " is switched off or signed out";
    }

    @Override
    public boolean canSearch() {
        return platform != Platform.TELEGRAM;
    }

    @Override
    public boolean canReadPublicProfile() {
        return platform != Platform.TELEGRAM;
    }

    @Override
    public boolean canReadOwnProfile() {
        return platform != Platform.TELEGRAM;
    }

    @Override
    public List<SocialPost> search(String query, int most) {
        return lines(SocialWeb.search(context, platform, query, 6000), platform.searchUrl(query), most);
    }

    /** The posts in a labelled page: each readable line between the page's markers, as a post with no author or date. */
    static List<SocialPost> lines(String labelled, String url, int most) {
        List<SocialPost> out = new ArrayList<SocialPost>();
        String body = labelled == null ? "" : labelled;
        int a = body.indexOf("---\n"), b = body.lastIndexOf("\n---");
        if (a >= 0 && b > a) {
            body = body.substring(a + 4, b);
        } else {
            return out; // not a page: a limit or a sign-in note, not posts
        }
        String platform = labelled.replaceFirst("(?s)^From (\\S+).*", "$1");
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.length() >= 25 && t.split("\\s+").length >= 4) {
                out.add(new SocialPost(platform, "", t, "", url, -1, -1, -1, 0));
            }
            if (out.size() >= most) {
                break;
            }
        }
        return out;
    }

    static String profileUrl(Platform p, String handle) {
        String h = handle.replaceAll("^@", "");
        switch (p) {
            case X: return "https://x.com/" + h;
            case INSTAGRAM: return "https://www.instagram.com/" + h + "/";
            case FACEBOOK: return "https://m.facebook.com/" + h;
            case REDDIT: return "https://www.reddit.com/user/" + h + "/";
            default: return p.home;
        }
    }

    @Override
    public SocialProfile readPublic(String handle, int recent) {
        String h = (handle == null ? "" : handle.trim()).replaceAll("^https?://[^/]+/(user/|u/)?", "").replaceAll("[/?#].*$", "").replaceAll("^@", "");
        SocialProfile p = new SocialProfile(platform.shown, h, "", "", profileUrl(platform, h), -1, -1, false);
        if (h.isEmpty()) {
            p.limits.add("No username given.");
            return p;
        }
        String page = SocialWeb.open(context, p.url, 6000);
        p.recent.addAll(lines(page, p.url, Math.max(1, recent)));
        if (p.recent.isEmpty()) {
            p.limits.add(page.length() > 300 ? page.substring(0, 300) : page);
        }
        if (page.toLowerCase(java.util.Locale.US).matches("(?s).*(this account is private|private account|these posts are protected).*")) {
            p.recent.clear();
            p.limits.add("The account is private: only what it shows publicly can be read, and nothing more is tried.");
        }
        return p;
    }

    @Override
    public SocialProfile readOwn(int recent) {
        SocialAccounts.Account a = account();
        String handle = a == null ? "" : a.label.trim();
        if (a != null && !handle.matches("@?[A-Za-z0-9._]{2,40}") && (platform == Platform.FACEBOOK || platform == Platform.REDDIT)) {
            // the account is named by a person's name, not a username: the network's own "my profile" address, with
            // the sign-in, opens it all the same
            String me = platform == Platform.FACEBOOK ? "https://m.facebook.com/me/" : "https://www.reddit.com/user/me/";
            SocialProfile own = new SocialProfile(platform.shown, handle, handle, "", me, -1, -1, true);
            String page = SocialWeb.open(context, me, 8000);
            own.recent.addAll(lines(page, me, Math.max(1, recent)));
            if (own.recent.isEmpty()) {
                own.limits.add(page.length() > 300 ? page.substring(0, 300) : page);
            }
            own.limits.add("Read with the user's own sign-in as the profile page shows it; private insights need the platform's business API.");
            return own;
        }
        if (a == null || !handle.matches("@?[A-Za-z0-9._]{2,40}")) {
            SocialProfile p = new SocialProfile(platform.shown, handle, "", "", "", -1, -1, true);
            p.limits.add(a == null ? unavailableReason()
                    : "Name the account by its username in Social accounts (now “" + handle + "”), so Ibiko can open its profile.");
            return p;
        }
        SocialProfile pub = readPublic(handle, recent);
        SocialProfile own = new SocialProfile(platform.shown, pub.handle, pub.name, pub.bio, pub.url, pub.followers, pub.posts, true);
        own.recent.addAll(pub.recent);
        own.limits.addAll(pub.limits);
        own.limits.add("Read with the user's own sign-in as the profile page shows it; private insights need the platform's business API.");
        return own;
    }
}
