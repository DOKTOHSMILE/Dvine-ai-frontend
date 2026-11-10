package ai.ibiko.social.intel;

import java.util.ArrayList;
import java.util.List;

/**
 * A profile as the tools read it: only what the platform shows (a public profile) or what the user's own signed-in
 * account may see of itself. Nothing private, nothing hidden; a missing field stays empty. Pure Java.
 */
public final class SocialProfile {
    public final String platform, handle, name, bio, url;
    /** Followers, posts or karma; -1 when the source didn't say. */
    public final long followers, posts;
    /** True for the user's own account (read with their sign-in). */
    public final boolean own;
    /** The account's recent public posts, as read. */
    public final List<SocialPost> recent = new ArrayList<SocialPost>();
    /** What could not be read, and why (private, not supported, not signed in). */
    public final List<String> limits = new ArrayList<String>();

    public SocialProfile(String platform, String handle, String name, String bio, String url, long followers, long posts, boolean own) {
        this.platform = platform == null ? "" : platform;
        this.handle = handle == null ? "" : handle;
        this.name = name == null ? "" : name;
        this.bio = bio == null ? "" : bio;
        this.url = url == null ? "" : url;
        this.followers = followers;
        this.posts = posts;
        this.own = own;
    }

    public String text() {
        StringBuilder b = new StringBuilder(own ? "The user's own " : "Public ").append(platform).append(" profile");
        if (!handle.isEmpty()) {
            b.append(" @").append(handle);
        }
        b.append(":\n");
        if (!name.isEmpty()) {
            b.append("Name: ").append(name).append('\n');
        }
        if (!bio.isEmpty()) {
            b.append("Bio: ").append(bio).append('\n');
        }
        if (followers >= 0) {
            b.append("Followers/karma: ").append(followers).append('\n');
        }
        if (posts >= 0) {
            b.append("Posts: ").append(posts).append('\n');
        }
        if (!url.isEmpty()) {
            b.append("Link: ").append(url).append('\n');
        }
        if (!recent.isEmpty()) {
            b.append("Recent public posts (").append(recent.size()).append("):\n");
            for (SocialPost p : recent) {
                b.append("- ").append(p.line()).append('\n');
            }
        }
        for (String l : limits) {
            b.append("Limit: ").append(l).append('\n');
        }
        return b.toString().trim();
    }
}
