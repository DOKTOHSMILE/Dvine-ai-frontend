package ai.dvine.llm;

/**
 * The brains the user can choose, as on the AI providers sheet: the one on this phone, and the APIs. Each API has
 * its address and a model to start with; every field can be changed, and "Custom gateway" takes any
 * OpenAI-compatible address (OpenRouter, LiteLLM, a self-hosted server). Pure Java.
 */
public enum Providers {
    ON_PHONE("On this phone", null, "", "", 0,
            "DVINE's own brain, " + ModelSpec.WEIGHTS + ". Works offline after a one-time download."),
    ANTHROPIC("Anthropic (Claude)", RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", 1_000_000,
            "Claude models. Key from console.anthropic.com."),
    OPENAI("OpenAI", RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", 400_000,
            "GPT models. Key from platform.openai.com."),
    GEMINI("Google Gemini", RemoteBrain.Protocol.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash",
            1_000_000, "Gemini through Google's OpenAI-compatible endpoint. Key from aistudio.google.com."),
    OPENROUTER("OpenRouter", RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-5-mini", 400_000,
            "Hundreds of models behind one key. Key from openrouter.ai."),
    CUSTOM("Custom gateway", RemoteBrain.Protocol.OPENAI, "", "", 128_000,
            "OpenAI-compatible: OpenRouter, LiteLLM, or a self-hosted endpoint.");

    public final String title, address, model, about;
    public final RemoteBrain.Protocol protocol;
    /** The model's context window in tokens, for the Usage screen (changeable for a custom model). */
    public final int window;

    Providers(String title, RemoteBrain.Protocol protocol, String address, String model, int window, String about) {
        this.title = title;
        this.protocol = protocol;
        this.address = address;
        this.model = model;
        this.window = window;
        this.about = about;
    }

    public boolean api() {
        return protocol != null;
    }

    /** Other models worth offering for this provider in the model picker (the user can type any). */
    public String[] suggestions() {
        switch (this) {
            case ANTHROPIC:
                return new String[]{"claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1"};
            case OPENAI:
                return new String[]{"gpt-5-mini", "gpt-5", "gpt-4.1-mini", "gpt-4o-mini"};
            case GEMINI:
                return new String[]{"gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite"};
            case OPENROUTER:
                return new String[]{"openai/gpt-5-mini", "anthropic/claude-sonnet-5-5", "google/gemini-2.5-flash",
                        "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-chat"};
            default:
                return new String[0];
        }
    }

    public static Providers named(String name) {
        for (Providers p : values()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return ON_PHONE;
    }
}
