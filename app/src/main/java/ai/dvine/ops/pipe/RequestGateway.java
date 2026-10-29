package ai.dvine.ops.pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ai.dvine.ops.recon.EditableScene;

/**
 * One reading for a sentence, whether it was typed or spoken. It does not run tools.
 * A scene id is copied from the live inventory or left empty.
 */
public final class RequestGateway {
    public StructuredActionPlan accept(String text, EditableScene scene) {
        String id = UUID.randomUUID().toString();
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            return new StructuredActionPlan(id, StructuredActionPlan.Operation.REJECT, "", "", false, false, false, 0f,
                    List.of(), "Empty request.");
        }
        String n = DirectQuestions.bare(raw);
        List<String> constraints = new ArrayList<String>();
        if (n.contains("but keep") || n.contains("keep it") || n.contains("don't delete") || n.contains("do not delete")) {
            constraints.add("keep");
        }
        if (DirectQuestions.clock(n)) {
            return plan(id, StructuredActionPlan.Operation.CLOCK, "", "", false, false, 1f, constraints, "The clock reading matches the device clock.");
        }
        if (DirectQuestions.modelIdentity(n)) {
            return plan(id, StructuredActionPlan.Operation.MODEL, "", "", false, false, 1f, constraints, "The name matches ModelSpec.");
        }
        if (DirectQuestions.abilities(n)) {
            return plan(id, StructuredActionPlan.Operation.ABILITIES, "", "", false, false, 1f, constraints, "The reply lists only registered capabilities.");
        }
        if (DirectQuestions.wantsLearn(n)) {
            return plan(id, StructuredActionPlan.Operation.LEARN, "", "", false, false, 0.8f, constraints, "A saved note is not a weight change.");
        }
        if (DirectQuestions.wantsPicker(n)) {
            return plan(id, StructuredActionPlan.Operation.PICK, DirectQuestions.pickerKind(n), "", false, false, 0.8f, constraints, "The bytes come from the picker.");
        }
        if (DirectQuestions.hypothetical(n)) {
            return plan(id, StructuredActionPlan.Operation.EXPLAIN, "", "", false, false, 0.7f, constraints, "Nothing on disk changes.");
        }
        boolean keep = constraints.contains("keep");
        if (n.matches(".*(remove|take out|detach|take off).*")) {
            String wanted = partPhrase(n);
            String found = find(scene, wanted);
            boolean clarify = found.isEmpty();
            return new StructuredActionPlan(id, StructuredActionPlan.Operation.DETACH, wanted, found, true, false, clarify, clarify ? 0.4f : 0.85f,
                    constraints, keep ? "The part stays in the scene, hidden or detached, and can be restored." : "The part is detached and can be restored.");
        }
        if (n.startsWith("delete") || n.contains(" delete ")) {
            return plan(id, StructuredActionPlan.Operation.DELETE, "", "", false, true, 0.7f, constraints, "The file is still there until a separate yes.");
        }
        if (n.contains("reconstruct") || n.contains("3d model") || n.contains("build a ")) {
            constraints.add("approximation-only");
            return plan(id, StructuredActionPlan.Operation.RECONSTRUCT, "", "", true, false, 0.5f, constraints, "The result is labelled approximation, not a measurement.");
        }
        if (DirectQuestions.news(n) || n.startsWith("what ") || n.startsWith("who ") || n.startsWith("why ") || n.startsWith("how ")) {
            return plan(id, StructuredActionPlan.Operation.ANSWER, "", "", false, false, 0.6f, constraints, "The answer is not treated as a scene edit.");
        }
        return plan(id, StructuredActionPlan.Operation.CHAT, "", "", false, false, 0.4f, constraints, "No tool runs until a later step accepts the plan.");
    }

    private static StructuredActionPlan plan(String id, StructuredActionPlan.Operation op, String subject, String target,
            boolean reversible, boolean confirm, float confidence, List<String> constraints, String verification) {
        return new StructuredActionPlan(id, op, subject, target, reversible, confirm, false, confidence, constraints, verification);
    }

    private static String partPhrase(String n) {
        if (n.contains("front tyre") || n.contains("front tire")) {
            return "front tyre";
        }
        if (n.contains("tyre") || n.contains("tire")) {
            return "tyre";
        }
        if (n.contains("roof")) {
            return "roof";
        }
        return "";
    }

    /** The inventory id, or empty. A missing part is not given a made-up id. */
    static String find(EditableScene scene, String phrase) {
        if (scene == null || phrase == null || phrase.isEmpty()) {
            return "";
        }
        String want = phrase.toLowerCase(Locale.US);
        EditableScene.Node hit = null;
        for (EditableScene.Node node : scene.nodes()) {
            String id = node.id.toLowerCase(Locale.US);
            String semantic = node.semantic == null ? "" : node.semantic.toLowerCase(Locale.US);
            if (id.contains("front") && id.contains("tyre") && want.contains("front")) {
                return node.id;
            }
            if (semantic.equals(want) || id.equals(want.replace(' ', '_'))) {
                hit = node;
            }
        }
        return hit == null ? "" : hit.id;
    }
}
