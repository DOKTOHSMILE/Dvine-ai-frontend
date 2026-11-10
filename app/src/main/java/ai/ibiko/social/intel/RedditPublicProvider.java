package ai.ibiko.social.intel;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.ibiko.brain.MiniJson;

/**
 * Reddit through its public JSON API (no key, no sign-in): search across Reddit, and public user profiles with their
 * karma, description and recent posts, each with author, date, score, comments and link. Pure Java; the network is
 * behind {@link Fetch} so tests run without one.
 */
public final class RedditPublicProvider implements SocialProvider {
    /** Reads one URL; null when it couldn't. */
    public interface Fetch {
        String get(String url) throws IOException;
    }

    private final Fetch fetch;

    public RedditPublicProvider(Fetch fetch) {
        this.fetch = fetch;
    }

    @Override
    public String platform() {
        return "Reddit";
    }

    @Override
    public boolean available() {
        return fetch != null;
    }

    @Override
    public String unavailableReason() {
        return available() ? "" : "no network";
    }

    @Override
    public boolean canSearch() {
        return true;
    }

    @Override
    public boolean canReadPublicProfile() {
        return true;
    }

    @Override
    public boolean canReadOwnProfile() {
        return false; // the user's own account is read with their sign-in (the account provider)
    }

    @Override
    public List<SocialPost> search(String query, int most) throws IOException {
        String url = "https://www.reddit.com/search.json?sort=relevance&t=year&limit=" + Math.max(5, Math.min(50, most))
                + "&q=" + URLEncoder.encode(query == null ? "" : query, "UTF-8");
        return posts(json(fetch.get(url)));
    }

    /** Reddit's answer as JSON, or an error saying why there is none (a refused or rate-limited request is HTML). */
    static String json(String body) throws IOException {
        String t = body == null ? "" : body.trim();
        if (!t.startsWith("{") && !t.startsWith("[")) {
            throw new IOException(t.isEmpty() ? "Reddit didn't answer" : "Reddit refused the request (blocked or rate-limited)");
        }
        return t;
    }

    static List<SocialPost> posts(String json) {
        List<SocialPost> out = new ArrayList<SocialPost>();
        if (json == null) {
            return out;
        }
        Map<String, Object> root = MiniJson.obj(MiniJson.parse(json));
        for (Object o : MiniJson.list(MiniJson.obj(root.get("data")).get("children"))) {
            Map<String, Object> d = MiniJson.obj(MiniJson.obj(o).get("data"));
            String title = MiniJson.str(d.get("title"), "");
            String body = MiniJson.str(d.get("selftext"), MiniJson.str(d.get("body"), ""));
            String text = (title + (body.isEmpty() ? "" : ". " + body)).trim();
            if (text.isEmpty()) {
                continue;
            }
            long when = (long) MiniJson.num(d.get("created_utc"), -1);
            String sub = MiniJson.str(d.get("subreddit"), "");
            out.add(new SocialPost("Reddit" + (sub.isEmpty() ? "" : " r/" + sub), "u/" + MiniJson.str(d.get("author"), "?"),
                    text.length() > 1200 ? text.substring(0, 1200) : text,
                    when > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date(when * 1000)) : "",
                    "https://www.reddit.com" + MiniJson.str(d.get("permalink"), ""),
                    (long) MiniJson.num(d.get("score"), -1), (long) MiniJson.num(d.get("num_comments"), -1), -1, 0));
        }
        return out;
    }

    @Override
    public SocialProfile readPublic(String handle, int recent) throws IOException {
        String h = (handle == null ? "" : handle).replaceAll("^(?i)(@|u/|/u/|https?://(www\\.)?reddit\\.com/(user|u)/)", "").replaceAll("/.*$", "");
        String about = json(fetch.get("https://www.reddit.com/user/" + URLEncoder.encode(h, "UTF-8") + "/about.json"));
        Map<String, Object> d = MiniJson.obj(MiniJson.obj(MiniJson.parse(about)).get("data"));
        if (d.isEmpty()) {
            SocialProfile none = new SocialProfile("Reddit", h, "", "", "https://www.reddit.com/user/" + h, -1, -1, false);
            none.limits.add("No public Reddit profile named u/" + h + " (or it is suspended or private).");
            return none;
        }
        Map<String, Object> sub = MiniJson.obj(d.get("subreddit"));
        SocialProfile p = new SocialProfile("Reddit", MiniJson.str(d.get("name"), h), MiniJson.str(sub.get("title"), ""),
                MiniJson.str(sub.get("public_description"), ""), "https://www.reddit.com/user/" + h,
                (long) MiniJson.num(d.get("total_karma"), MiniJson.num(d.get("link_karma"), -1)), -1, false);
        if (recent > 0) {
            p.recent.addAll(posts(json(fetch.get("https://www.reddit.com/user/" + URLEncoder.encode(h, "UTF-8")
                    + "/submitted.json?limit=" + Math.min(25, recent)))));
        }
        return p;
    }

    @Override
    public SocialProfile readOwn(int recent) {
        SocialProfile p = new SocialProfile("Reddit", "", "", "", "", -1, -1, true);
        p.limits.add("The user's own Reddit account is read with their sign-in (Social accounts).");
        return p;
    }

}
