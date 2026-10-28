package ai.dvine.ops.recon;

public final class AssetGenerationService {
    private final ProceduralGeometryBuilder procedural = new ProceduralGeometryBuilder();

    public EditableScene procedural(String label) {
        return procedural.forLabel(label);
    }

    public ProceduralGeometryBuilder proceduralBuilder() {
        return procedural;
    }
}
