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

    public static final ModelSpec QWEN3_1_7B = new ModelSpec("DVINE brain", "qwen3-1.7b-q4km.gguf", // the file name stays: installed copies keep working
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
            1107409472L, "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897", "Apache 2.0");

    /**
     * Where to fetch the file, in order: the project's own copies first (add a URL here once a copy is hosted, for
     * example as a GitHub release asset on DVINE's own repository), the original last. Every copy is checked
     * against the same SHA-256, so a mirror can never hand the phone a different file.
     */
    public static final String[] MIRRORS = {};

    /** This file's sources: the mirrors, then its original URL. */
    public String[] sources() {
        String[] out = java.util.Arrays.copyOf(MIRRORS, MIRRORS.length + 1);
        out[MIRRORS.length] = url;
        return out;
    }

    /** The size in words, for speech ("1.1 gigabytes"). */
    public String sizeWords() {
        return String.format(java.util.Locale.US, "%.1f gigabytes", bytes / 1e9);
    }
}
