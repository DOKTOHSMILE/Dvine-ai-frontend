package ai.dvine.llm;

/**
 * The language model DVINE runs on the phone: Qwen3 1.7B (Apache 2.0, Alibaba's Qwen team), quantised to
 * 4 bits (Q4_K_M) as a GGUF file for llama.cpp. Downloaded once from Hugging Face at a pinned revision and
 * checked against its SHA-256 before it is ever loaded.
 */
public final class ModelSpec {
    public final String name, file, url, sha256, licence;
    public final long bytes;

    public ModelSpec(String name, String file, String url, long bytes, String sha256, String licence) {
        this.name = name;
        this.file = file;
        this.url = url;
        this.bytes = bytes;
        this.sha256 = sha256;
        this.licence = licence;
    }

    public static final ModelSpec QWEN3_1_7B = new ModelSpec("Qwen3 1.7B", "qwen3-1.7b-q4km.gguf",
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
            1107409472L, "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897", "Apache 2.0");

    /** The size in words, for speech ("1.1 gigabytes"). */
    public String sizeWords() {
        return String.format(java.util.Locale.US, "%.1f gigabytes", bytes / 1e9);
    }
}
