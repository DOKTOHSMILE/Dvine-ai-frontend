package ai.dvine.ops.pipe;

import java.util.Locale;

import ai.dvine.llm.DrawPrompt;

/**
 * Words in, one machine operation out. A draw or a design is a photograph lookup.
 * A known object the user asked to build stays with the procedural builder.
 */
public final class MachinePlan {
    public enum Op { LOOK_UP_PHOTOS, OTHER }

    public final Op op;
    public final String subject;

    private MachinePlan(Op op, String subject) {
        this.op = op;
        this.subject = subject == null ? "" : subject;
    }

    public static MachinePlan read(String said) {
        String draw = DrawPrompt.subject(said);
        if (draw != null) {
            return new MachinePlan(Op.LOOK_UP_PHOTOS, draw);
        }
        String t = said == null ? "" : said.trim().toLowerCase(Locale.US);
        if (t.matches("^(?:please |can you |could you |will you )?(?:design)(?: me| us)?(?: a| an| the| some)? (.+?)(?: please)?[.!?]*$")) {
            String subject = t.replaceFirst("^(?:please |can you |could you |will you )?(?:design)(?: me| us)?(?: a| an| the| some)? ", "")
                    .replaceAll("[.!?]+$", "").trim();
            if (!subject.isEmpty()) {
                return new MachinePlan(Op.LOOK_UP_PHOTOS, subject);
            }
        }
        String form = DrawPrompt.formSubject(said);
        if (form != null && !procedural(form)) {
            return new MachinePlan(Op.LOOK_UP_PHOTOS, form);
        }
        return new MachinePlan(Op.OTHER, "");
    }

    /** A named object the procedural builder can edit. A pen is not one of these. */
    public static boolean procedural(String subject) {
        return subject != null && subject.matches(
                ".*\\b(house|home|car|table|chair|sofa|bed|bookshelf|lamp|mug|bottle|laptop|phone|tree|bicycle|bike|motorcycle|motorbike|person|human|humanoid)\\b.*");
    }
}
