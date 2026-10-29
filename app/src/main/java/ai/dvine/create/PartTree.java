package ai.dvine.create;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Collapsible rows for the parts on show. Groups come from the real parent ids. */
public final class PartTree {
    public static final int MISS = -2;

    public static final class Row {
        public final String id;
        public final String label;
        public final int componentId;
        public final int depth;
        public final boolean group;
        public final boolean expanded;
        public final boolean visible;
        public final boolean locked;
        public final boolean selected;
        public final float x, y, width, height;

        Row(String id, String label, int componentId, int depth, boolean group, boolean expanded, boolean visible,
                boolean locked, boolean selected, float x, float y, float width, float height) {
            this.id = id;
            this.label = label;
            this.componentId = componentId;
            this.depth = depth;
            this.group = group;
            this.expanded = expanded;
            this.visible = visible;
            this.locked = locked;
            this.selected = selected;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public final List<Row> rows;
    public final float x, y, width, height;

    private PartTree(List<Row> rows, float x, float y, float width, float height) {
        this.rows = rows;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public static PartTree layout(ObjectModel model, int selectedId, Set<String> collapsed, float x, float y, float width, float rowH) {
        List<Row> rows = new ArrayList<Row>();
        if (model == null || width < 8f || rowH < 4f) {
            return new PartTree(rows, x, y, width, 0f);
        }
        Map<String, List<Component>> groups = new LinkedHashMap<String, List<Component>>();
        for (Component c : model.components) {
            String parent = SceneCommands.parentId(c);
            if (!groups.containsKey(parent)) {
                groups.put(parent, new ArrayList<Component>());
            }
            groups.get(parent).add(c);
        }
        float top = y;
        boolean rootOpen = collapsed == null || !collapsed.contains("model");
        rows.add(row("model", model.title, -1, 0, true, rootOpen, true, false, false, x, top, width, rowH));
        top += rowH;
        if (rootOpen) {
            for (Map.Entry<String, List<Component>> e : groups.entrySet()) {
                String gid = e.getKey();
                boolean open = collapsed == null || !collapsed.contains(gid);
                rows.add(row(gid, gid, -1, 1, true, open, true, false, false, x, top, width, rowH));
                top += rowH;
                if (!open) {
                    continue;
                }
                for (Component c : e.getValue()) {
                    String label = c.name;
                    if (!c.retained) {
                        label += "  deleted";
                    } else if (!c.visible) {
                        label += "  detached";
                    }
                    if (c.locked) {
                        label += "  locked";
                    }
                    rows.add(row(SceneCommands.stableId(c), label, c.id, 2, false, false, c.visible && c.retained, c.locked,
                            c.id == selectedId, x, top, width, rowH));
                    top += rowH;
                }
            }
        }
        rows.add(row("history-undo", "undo", -1, 0, false, false, true, false, false, x, top, width, rowH));
        top += rowH;
        rows.add(row("history-redo", "redo", -1, 0, false, false, true, false, false, x, top, width, rowH));
        return new PartTree(rows, x, y, width, top - y);
    }

    private static Row row(String id, String label, int componentId, int depth, boolean group, boolean expanded, boolean visible,
            boolean locked, boolean selected, float x, float y, float width, float height) {
        return new Row(id, label, componentId, depth, group, expanded, visible, locked, selected, x, y, width, height);
    }

    /** Component id, or {@link #MISS} when the point misses. A group row returns -1. */
    public int hit(float px, float py) {
        Row row = rowAt(px, py);
        if (row == null) {
            return MISS;
        }
        return row.group ? -1 : row.componentId;
    }

    /** Group id to collapse or expand, or null. */
    public String toggle(float px, float py) {
        Row row = rowAt(px, py);
        if (row == null || !row.group) {
            return null;
        }
        return row.id;
    }

    public Row rowAt(float px, float py) {
        if (px < x || px > x + width) {
            return null;
        }
        for (Row row : rows) {
            if (py >= row.y && py < row.y + row.height) {
                return row;
            }
        }
        return null;
    }

    /** "undo" or "redo" when the point is on that row. */
    public String command(float px, float py) {
        Row row = rowAt(px, py);
        if (row == null || !row.id.startsWith("history-")) {
            return null;
        }
        return row.id.substring("history-".length());
    }

    public String text() {
        StringBuilder b = new StringBuilder();
        for (Row row : rows) {
            for (int i = 0; i < row.depth; i++) {
                b.append("  ");
            }
            b.append(row.group ? (row.expanded ? "v " : "> ") : "- ");
            b.append(row.label.toLowerCase(Locale.US));
            if (row.selected) {
                b.append(" selected");
            }
            b.append('\n');
        }
        return b.toString();
    }
}
