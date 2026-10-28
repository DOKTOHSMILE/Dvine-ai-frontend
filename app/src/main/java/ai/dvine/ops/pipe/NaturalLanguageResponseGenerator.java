package ai.dvine.ops.pipe;

public final class NaturalLanguageResponseGenerator {
    public String say(SemanticTypes.ToolResult result, String fallback) {
        if (result != null && result.speech != null && !result.speech.isEmpty()) {
            return result.speech;
        }
        return fallback == null ? "I couldn't complete that." : fallback;
    }
}
