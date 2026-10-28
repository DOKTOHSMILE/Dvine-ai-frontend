package ai.dvine.ops.recon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named parts. A local edit copies the scene and changes only the targeted nodes. */
public final class EditableScene {
    public static final class Node {
        public final String id;
        public String semantic;
        public boolean visible = true;
        public boolean removed;
        public String material;
        public float scale = 1f;
        public final String solidLine;

        public Node(String id, String semantic, String material, String solidLine) {
            this.id = id;
            this.semantic = semantic;
            this.material = material;
            this.solidLine = solidLine;
        }

        public Node copy() {
            Node n = new Node(id, semantic, material, solidLine);
            n.visible = visible;
            n.removed = removed;
            n.scale = scale;
            return n;
        }
    }

    public static final class EditReport {
        public final boolean applied;
        public final String speech;
        public final List<String> changed;
        public final List<String> preserved;

        public EditReport(boolean applied, String speech, List<String> changed, List<String> preserved) {
            this.applied = applied;
            this.speech = speech;
            this.changed = changed;
            this.preserved = preserved;
        }
    }

    private final List<Node> nodes = new ArrayList<Node>();
    private final List<EditableScene> undo = new ArrayList<EditableScene>();
    public String label = "";
    public boolean approximation;

    public void add(Node node) {
        nodes.add(node);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public EditableScene copy() {
        EditableScene s = new EditableScene();
        s.label = label;
        s.approximation = approximation;
        for (Node n : nodes) {
            s.nodes.add(n.copy());
        }
        return s;
    }

    public Node byId(String id) {
        for (Node n : nodes) {
            if (n.id.equals(id)) {
                return n;
            }
        }
        return null;
    }

    public EditReport removeKeeping(List<String> targets, List<String> protect, String describe) {
        if (targets == null || targets.isEmpty()) {
            return new EditReport(false, "I couldn't tell which part to change, so I left the model as it is.", List.of(), ids());
        }
        for (String t : targets) {
            if (protect != null && protect.contains(t)) {
                return new EditReport(false, "That would change a part you asked me to leave alone, so I didn't.", List.of(), ids());
            }
            if (byId(t) == null) {
                return new EditReport(false, "I couldn't find " + t.replace('_', ' ') + " as its own part, so I left the model unchanged.", List.of(), ids());
            }
        }
        undo.add(copy());
        List<String> changed = new ArrayList<String>();
        Map<String, Node> before = snapshot();
        for (String t : targets) {
            Node n = byId(t);
            n.removed = true;
            n.visible = false;
            changed.add(t);
        }
        List<String> broken = new ArrayList<String>();
        for (Node n : nodes) {
            if (targets.contains(n.id)) {
                continue;
            }
            Node was = before.get(n.id);
            if (was == null || was.removed != n.removed || was.visible != n.visible || was.scale != n.scale || !was.material.equals(n.material)) {
                broken.add(n.id);
            }
        }
        if (!broken.isEmpty()) {
            EditableScene back = undo.remove(undo.size() - 1);
            nodes.clear();
            nodes.addAll(back.nodes);
            return new EditReport(false, "The edit would have changed other parts, so I rolled it back.", List.of(), ids());
        }
        String speech = describe == null ? "I removed " + words(changed) + " and left the rest in place." : describe;
        return new EditReport(true, speech, changed, protectedIntact(protect));
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        EditableScene back = undo.remove(undo.size() - 1);
        nodes.clear();
        nodes.addAll(back.nodes);
        return true;
    }

    public String solidText() {
        StringBuilder b = new StringBuilder();
        for (Node n : nodes) {
            if (!n.removed && n.solidLine != null) {
                b.append(n.solidLine).append('\n');
            }
        }
        return b.toString();
    }

    public String manifest() {
        StringBuilder b = new StringBuilder();
        b.append("Object: ").append(label).append('\n');
        if (approximation) {
            b.append("Note: approximation from limited views.\n");
        }
        for (Node n : nodes) {
            b.append("- ").append(n.id).append(n.removed ? " (removed)" : " -> ").append(n.removed ? "" : "present").append('\n');
        }
        return b.toString();
    }

    private Map<String, Node> snapshot() {
        Map<String, Node> m = new LinkedHashMap<String, Node>();
        for (Node n : nodes) {
            m.put(n.id, n.copy());
        }
        return m;
    }

    private List<String> ids() {
        List<String> ids = new ArrayList<String>();
        for (Node n : nodes) {
            ids.add(n.id);
        }
        return ids;
    }

    private List<String> protectedIntact(List<String> protect) {
        List<String> ok = new ArrayList<String>();
        if (protect == null) {
            return ok;
        }
        for (String id : protect) {
            Node n = byId(id);
            if (n != null && !n.removed && n.visible) {
                ok.add(id);
            }
        }
        return ok;
    }

    private static String words(List<String> ids) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                b.append(i == ids.size() - 1 ? " and " : ", ");
            }
            b.append(ids.get(i).replace('_', ' '));
        }
        return b.toString();
    }

    public static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }
}
