package ai.dvine.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reversible edits on a validated model. A part is hidden or marked deleted;
 * its mesh is not rewritten and its centroid stays where the generator put it.
 * This does not measure a photograph.
 */
public final class SceneCommands {
    public static final class Report {
        public final boolean applied;
        public final boolean clarify;
        /** Empty unless one real part was named. Never invented. */
        public final String targetId;
        public final String parentId;
        public final float[] pose;
        public final String speech;

        Report(boolean applied, boolean clarify, String targetId, String parentId, float[] pose, String speech) {
            this.applied = applied;
            this.clarify = clarify;
            this.targetId = targetId == null ? "" : targetId;
            this.parentId = parentId == null ? "" : parentId;
            this.pose = pose;
            this.speech = speech == null ? "" : speech;
        }
    }

    private SceneCommands() {
    }

    public static Report apply(ObjectModel model, String op, String phrase) {
        if (model == null) {
            return miss("There's no model on show, so I changed nothing.");
        }
        List<Component> hits = match(model, phrase);
        if (hits.isEmpty()) {
            return miss("I couldn't find that part on this model, so I left it unchanged.");
        }
        if (hits.size() > 1) {
            return new Report(false, true, "", "", null,
                    "Which one: " + names(hits) + "? I didn't change either.");
        }
        Component part = hits.get(0);
        String id = stableId(part);
        String parent = parentId(part);
        float[] pose = centroid(part);
        if (part.locked) {
            return new Report(false, false, id, parent, pose,
                    "The " + part.name + " is locked, so I left it where it is.");
        }
        if ("detach".equals(op)) {
            if (!part.visible && part.retained) {
                return new Report(false, false, id, parent, pose,
                        "The " + part.name + " is already detached. Say put the " + part.name + " back.");
            }
            part.visible = false;
            part.retained = true;
            return new Report(true, false, id, parent, pose,
                    "I detached the " + part.name + " and kept it. It is not deleted. Say put the " + part.name
                            + " back to restore it. This is a procedural model, not a measurement.");
        }
        if ("restore".equals(op)) {
            if (!part.retained) {
                return new Report(false, false, id, parent, pose,
                        "The " + part.name + " was marked deleted. Say restore the deleted " + part.name
                                + " if you want those triangles back. I have not wiped them from this session.");
            }
            if (part.visible) {
                return new Report(false, false, id, parent, pose, "The " + part.name + " is already on the model.");
            }
            part.visible = true;
            return new Report(true, false, id, parent, pose,
                    "The " + part.name + " is back in its original place. I did not move its geometry.");
        }
        if ("restore-deleted".equals(op)) {
            part.retained = true;
            part.visible = true;
            return new Report(true, false, id, parent, centroid(part),
                    "The " + part.name + " is back. The triangles were still in this session; I did not rebuild them from a photo.");
        }
        if ("delete".equals(op)) {
            return new Report(false, false, id, parent, pose,
                    "Deleting the " + part.name + " would take it off the model. I have not deleted it. Say yes, delete the "
                            + part.name + " to confirm, or remove the " + part.name + " but keep it to detach it.");
        }
        if ("delete-yes".equals(op)) {
            part.visible = false;
            part.retained = false;
            return new Report(true, false, id, parent, pose,
                    "I took the " + part.name + " off and marked it deleted. The triangles are still in this session until the model is rebuilt. "
                            + "Say restore the deleted " + part.name + " to put them back. This is not a wiped file.");
        }
        return miss("I didn't recognise that edit, so I left the model unchanged.");
    }

    /** One part, or every part of that kind when the phrase is only "tyre" or "rim". */
    public static List<Component> match(ObjectModel model, String phrase) {
        List<Component> hits = new ArrayList<Component>();
        if (model == null || phrase == null) {
            return hits;
        }
        String p = phrase.toLowerCase(Locale.US).replace("tire", "tyre").trim();
        if (p.isEmpty()) {
            return hits;
        }
        for (Component c : model.components) {
            if (c.name.equals(p)) {
                hits.add(c);
            }
        }
        if (!hits.isEmpty() || !(p.equals("tyre") || p.equals("rim") || p.equals("wheel"))) {
            return hits;
        }
        for (Component c : model.components) {
            if (c.name.contains(p)) {
                hits.add(c);
            }
        }
        return hits;
    }

    public static String stableId(Component c) {
        if (c == null) {
            return "";
        }
        if ("front tyre".equals(c.name)) {
            return "tyre-front-1";
        }
        if ("rear tyre".equals(c.name)) {
            return "tyre-rear-1";
        }
        if ("front rim".equals(c.name)) {
            return "rim-front-1";
        }
        if ("rear rim".equals(c.name)) {
            return "rim-rear-1";
        }
        return c.name.toLowerCase(Locale.US).replace(' ', '-');
    }

    public static String parentId(Component c) {
        if (c == null) {
            return "";
        }
        if (c.name.startsWith("front") && (c.name.contains("tyre") || c.name.contains("rim"))) {
            return "wheel-front";
        }
        if (c.name.startsWith("rear") && (c.name.contains("tyre") || c.name.contains("rim"))) {
            return "wheel-rear";
        }
        return "frame";
    }

    public static float[] centroid(Component c) {
        float x = 0f, y = 0f, z = 0f;
        int n = c.mesh.pos.length / 3;
        for (int i = 0; i < n; i++) {
            x += c.mesh.pos[i * 3];
            y += c.mesh.pos[i * 3 + 1];
            z += c.mesh.pos[i * 3 + 2];
        }
        float k = 1f / Math.max(1, n);
        return new float[]{x * k, y * k, z * k};
    }

    private static Report miss(String speech) {
        return new Report(false, false, "", "", null, speech);
    }

    private static String names(List<Component> parts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                b.append(i == parts.size() - 1 ? " or " : ", ");
            }
            b.append(parts.get(i).name);
        }
        return b.toString();
    }
}
