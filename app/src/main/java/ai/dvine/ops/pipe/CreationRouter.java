package ai.dvine.ops.pipe;

/**
 * One label for what a sentence is asking to create or edit.
 * An unavailable capability is reported unavailable. It does not run a tool.
 */
public final class CreationRouter {
    public enum Capability {
        CHAT, IMAGE_GENERATION, THREE_D_GENERATION, MULTI_VIEW_RECONSTRUCTION,
        ASSET_IMPORT, SCENE_EDIT, SCENE_INSPECTION, OTHER_TOOL
    }

    public static final class Route {
        public final Capability capability;
        public final boolean available;
        public final String operation;
        public final String subject;
        public final String note;

        Route(Capability capability, boolean available, String operation, String subject, String note) {
            this.capability = capability;
            this.available = available;
            this.operation = operation == null ? "" : operation;
            this.subject = subject == null ? "" : subject;
            this.note = note == null ? "" : note;
        }
    }

    private final CapabilityRegistry registry = new CapabilityRegistry();

    public Route classify(String text) {
        String n = DirectQuestions.bare(text == null ? "" : text);
        if (n.isEmpty()) {
            return new Route(Capability.CHAT, true, "REJECT", "", "Empty request.");
        }
        boolean photos = n.contains("photograph") || n.contains("photos") || n.contains("several photo") || n.contains("these photo");
        boolean rebuild = n.contains("reconstruct") || n.contains("photogrammetry");
        if (photos && (rebuild || n.contains("3d") || n.contains("model"))) {
            return new Route(Capability.MULTI_VIEW_RECONSTRUCTION, false, "RECONSTRUCT", "",
                    registry.get("photogrammetry").honest);
        }
        boolean picture = n.matches(".*\\b(picture|photo|photograph|image) of\\b.*");
        if (picture && !n.contains("editable")) {
            return new Route(Capability.IMAGE_GENERATION, false, "IMAGE", subject(n),
                    registry.get("image-generation").honest);
        }
        if (n.matches(".*(remove|detach|take off|take out|put the .+ back|restore the ).*")) {
            return new Route(Capability.SCENE_EDIT, true, n.contains("back") || n.contains("restore") ? "RESTORE" : "DETACH",
                    part(n), "The part stays in the model until a confirmed delete. Geometry is not rewritten.");
        }
        if (n.startsWith("delete") || n.contains(" delete ")) {
            return new Route(Capability.SCENE_EDIT, true, "DELETE", part(n), "A delete waits for a separate yes.");
        }
        if (n.matches(".*(build|make|design|create|model|editable).*(motorcycle|motorbike|3d|model).*")
                || n.matches(".*(motorcycle|motorbike).*(build|editable|3d).*")) {
            return new Route(Capability.THREE_D_GENERATION, true, "CREATE", "motorcycle",
                    "A procedural concept model with separate parts. Not a measurement and not a rig.");
        }
        return new Route(Capability.CHAT, true, "CHAT", "", "No creation tool runs.");
    }

    private static String part(String n) {
        if (n.contains("front tyre") || n.contains("front tire")) {
            return "front tyre";
        }
        if (n.contains("rear tyre") || n.contains("rear tire")) {
            return "rear tyre";
        }
        if (n.contains("tyre") || n.contains("tire")) {
            return "tyre";
        }
        return "";
    }

    private static String subject(String n) {
        if (n.contains("motorcycle") || n.contains("motorbike")) {
            return "motorcycle";
        }
        return "";
    }
}
