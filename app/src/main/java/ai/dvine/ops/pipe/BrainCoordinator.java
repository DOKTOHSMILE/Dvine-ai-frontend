package ai.dvine.ops.pipe;

import java.util.Locale;

import ai.dvine.ops.mind.ClaimCheck;
import ai.dvine.ops.recon.EditableScene;

/**
 * One typed reading for text and for voice. It does not run tools and it is
 * not a second brain. Ibiko is a role for coding talk, under this same reading.
 */
public final class BrainCoordinator {
    public enum Kind { ANSWER, CLARIFY, RESEARCH, EXECUTE_ACTION, DEBATE, UNSUPPORTED }

    public static final class Decision {
        public final Kind kind;
        /** "obi", "ivie", or "ibiko" when one should speak. Empty when the user named nobody and the words are not a coding task. */
        public final String persona;
        public final StructuredActionPlan plan;

        Decision(Kind kind, String persona, StructuredActionPlan plan) {
            this.kind = kind;
            this.persona = persona == null ? "" : persona;
            this.plan = plan;
        }
    }

    public Decision decide(String text, EditableScene scene) {
        StructuredActionPlan plan = new RequestGateway().accept(text, scene);
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty() || plan.operation == StructuredActionPlan.Operation.REJECT) {
            return new Decision(Kind.UNSUPPORTED, "", plan);
        }
        PersonaAddress.Address who = new PersonaAddress().read(raw);
        if (who.unknown != null) {
            return new Decision(Kind.CLARIFY, "", plan);
        }
        String persona = who.persona == null ? "" : who.persona;
        String n = raw.toLowerCase(Locale.US);
        if (persona.isEmpty()) {
            persona = role(n);
        }
        if (n.contains("debate")) {
            return new Decision(Kind.DEBATE, persona, plan);
        }
        if (ClaimCheck.wanted(raw)) {
            return new Decision(Kind.RESEARCH, persona, plan);
        }
        switch (plan.operation) {
            case DETACH:
            case DELETE:
            case RECONSTRUCT:
            case PICK:
            case LEARN:
            case STORAGE:
                return new Decision(Kind.EXECUTE_ACTION, persona, plan);
            case CLOCK:
            case MODEL:
            case ABILITIES:
            case ANSWER:
            case EXPLAIN:
            case CHAT:
            case RESEARCH:
                return new Decision(plan.operation == StructuredActionPlan.Operation.RESEARCH ? Kind.RESEARCH : Kind.ANSWER, persona, plan);
            default:
                return new Decision(Kind.ANSWER, persona, plan);
        }
    }

    static boolean coding(String n) {
        return n.matches(".*(fix (this|the|a) bug|stack trace|compile|gradle|termux|write a (function|class|script)|debug this|source code|git commit|git status).*")
                || n.matches(".*\\bssh\\b.*");
    }

    /** Empty when the words do not pick a role. Naming someone still wins. */
    static String role(String n) {
        if (coding(n)) {
            return "ibiko";
        }
        if (n.matches(".*(what could go wrong|is this safe|is it safe|the risks?|should i trust|what are the dangers|what if this fails).*")) {
            return "obi";
        }
        if (n.matches(".*(how will (people|they|he|she) feel|talk me through|comfort me|encourage me|i'm worried|i am worried).*")) {
            return "ivie";
        }
        return "";
    }
}
