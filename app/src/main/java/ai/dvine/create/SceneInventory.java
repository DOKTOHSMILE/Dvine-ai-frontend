package ai.dvine.create;

/**
 * The parts of the model on show. Ids come from those parts. Nothing is added
 * because a motorcycle usually has it.
 */
public final class SceneInventory {
    private SceneInventory() {
    }

    public static String text(ObjectModel model, int selectedId) {
        if (model == null || model.components.isEmpty()) {
            return "There's no model on show.";
        }
        StringBuilder b = new StringBuilder();
        b.append(model.title).append(", a procedural concept model, not a measurement.\n");
        for (Component c : model.components) {
            b.append("- ").append(c.name).append(" (").append(SceneCommands.stableId(c)).append(", part of ")
                    .append(SceneCommands.parentId(c)).append(")");
            if (c.id == selectedId) {
                b.append(", selected");
            }
            if (!c.retained) {
                b.append(", marked deleted");
            } else if (!c.visible) {
                b.append(", detached");
            }
            if (c.locked) {
                b.append(", locked");
            }
            b.append('\n');
        }
        return b.toString().trim();
    }
}
