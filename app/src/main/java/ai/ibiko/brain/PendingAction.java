package ai.ibiko.brain;

/** Something the user asked for that leaves the app. Runs only after a spoken yes. */
public final class PendingAction {
    public enum Kind {
        SHARE,
        COPY,
        /** A comment under someone's post, at {@link #target} (the post's link). */
        COMMENT
    }

    public final Kind kind;
    public final String content;
    /** Target app key (whatsapp, x, facebook, instagram, linkedin, telegram) or null for the chooser. */
    public final String app;
    public final long createdAt;
    /** People the pilot named to tag in the post (each typed as @Name and picked from the network's list). */
    public final java.util.List<String> tags;
    /** For a comment: the link of the post it goes under; else null. */
    public final String target;

    public PendingAction(Kind kind, String content, String app, long createdAt) {
        this(kind, content, app, createdAt, null, null);
    }

    public PendingAction(Kind kind, String content, String app, long createdAt, java.util.List<String> tags, String target) {
        this.kind = kind;
        this.content = content;
        this.app = app;
        this.createdAt = createdAt;
        this.tags = tags == null ? new java.util.ArrayList<String>() : new java.util.ArrayList<String>(tags);
        this.target = target;
    }

    public String appName() {
        if (app == null) {
            return "your share menu";
        }
        switch (app) {
            case "whatsapp": return "WhatsApp";
            case "x": return "X";
            case "facebook": return "Facebook";
            case "instagram": return "Instagram";
            case "linkedin": return "LinkedIn";
            case "telegram": return "Telegram";
            case "reddit": return "Reddit";
            default: return app;
        }
    }
}
