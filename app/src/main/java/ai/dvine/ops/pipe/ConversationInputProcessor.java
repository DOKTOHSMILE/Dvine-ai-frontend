package ai.dvine.ops.pipe;

public final class ConversationInputProcessor {
    public SemanticTypes.Decomposition begin(String text, String modality, double confidence) {
        SemanticTypes.Decomposition d = new SemanticTypes.Decomposition();
        d.original = text == null ? "" : text.trim();
        d.modality = modality == null ? "text" : modality;
        d.transcriptConfidence = confidence;
        return d;
    }
}
