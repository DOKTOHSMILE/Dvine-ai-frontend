package ai.ibiko.social.intel;

/**
 * One post as the social tools found it. A field the source didn't give is empty (or -1 for a count), never guessed:
 * a page read with the user's account has words but often no author or date; Reddit's public API gives all of them.
 * Pure Java.
 */
public final class SocialPost {
    public final String platform, author, content, date, url;
    /** Likes or score, comments, shares; -1 when the source didn't say. */
    public final long likes, comments, shares;
    /** How close it is to what was asked (0 to 1), set by the matcher; 0 before matching. */
    public final double relevance;

    public SocialPost(String platform, String author, String content, String date, String url, long likes, long comments,
            long shares, double relevance) {
        this.platform = platform == null ? "" : platform;
        this.author = author == null ? "" : author;
        this.content = content == null ? "" : content.trim();
        this.date = date == null ? "" : date;
        this.url = url == null ? "" : url;
        this.likes = likes;
        this.comments = comments;
        this.shares = shares;
        this.relevance = relevance;
    }

    public SocialPost withRelevance(double r) {
        return new SocialPost(platform, author, content, date, url, likes, comments, shares, r);
    }

    /** One line for the model: platform, author, date, engagement, the words, the link. */
    public String line() {
        StringBuilder b = new StringBuilder("[").append(platform).append(']');
        if (!author.isEmpty()) {
            b.append(" ").append(author);
        }
        if (!date.isEmpty()) {
            b.append(" (").append(date).append(')');
        }
        if (likes >= 0 || comments >= 0) {
            b.append(" {").append(likes >= 0 ? likes + " likes" : "").append(likes >= 0 && comments >= 0 ? ", " : "")
                    .append(comments >= 0 ? comments + " comments" : "").append('}');
        }
        if (relevance > 0) {
            b.append(String.format(java.util.Locale.US, " relevance %.2f", relevance));
        }
        b.append(": ").append(content.length() > 400 ? content.substring(0, 400) + "…" : content);
        if (!url.isEmpty()) {
            b.append(" <").append(url).append('>');
        }
        return b.toString();
    }
}
