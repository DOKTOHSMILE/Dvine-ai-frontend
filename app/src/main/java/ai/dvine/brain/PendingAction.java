package ai.dvine.brain;

/** Something the user asked for that leaves the app. Runs only after a spoken yes. */
public final class PendingAction {
    public enum Kind {
        SHARE,
        COPY
    }

    public final Kind kind;
    public final String content;
    /** Target app key (whatsapp, x, facebook, instagram, linkedin, telegram) or null for the chooser. */
    public final String app;
    public final long createdAt;

    public PendingAction(Kind kind, String content, String app, long createdAt) {
        this.kind = kind;
        this.content = content;
        this.app = app;
        this.createdAt = createdAt;
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
            default: return app;
        }
    }
}
