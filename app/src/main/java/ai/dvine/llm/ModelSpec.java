package ai.dvine.llm;

/**
 * A language model DVINE can run on the phone, quantised to 4 bits (Q4_K_M) as a GGUF file for llama.cpp. The user
 * picks one in Settings ({@link #ALL}; Qwen3 1.7B by default). Each is downloaded once from Hugging Face at a pinned
 * revision and checked against its SHA-256 before it is ever loaded.
 */
public final class ModelSpec {
    public final String name, file, url, sha256, licence;
    public final long bytes;
    /** The weights by their own name ("Qwen3 4B"), their family ("Qwen3") and size ("4B"), and a line about them. */
    public final String weights, family, size, about;
    /** The phone's memory it wants, in gigabytes (a guide shown with the choice). */
    public final int ramGb;
    /** Qwen3 reasons in a hidden block unless told "/no_think"; other models would only read those words as text. */
    public final boolean thinks;

    public ModelSpec(String name, String file, String url, long bytes, String sha256, String licence) {
        this(name, file, url, bytes, sha256, licence, "Qwen3", "1.7B", "", 4, true);
    }

    public ModelSpec(String name, String file, String url, long bytes, String sha256, String licence, String family, String size,
                     String about, int ramGb, boolean thinks) {
        this.name = name;
        this.file = file;
        this.url = url;
        this.bytes = bytes;
        this.sha256 = sha256;
        this.licence = licence;
        this.family = family;
        this.size = size;
        this.weights = family + " " + size;
        this.about = about;
        this.ramGb = ramGb;
        this.thinks = thinks;
    }

    /** The weights of the default model. The spoken name "DVINE model" is not a different model. */
    public static final String WEIGHTS = "Qwen3 1.7B";

    private static final String HF = "https://huggingface.co/unsloth/";

    // Each family comes in sizes, as Claude comes as Haiku, Sonnet and Opus: smaller is faster and lighter,
    // bigger answers better and needs more memory.
    public static final ModelSpec QWEN3_0_6B = new ModelSpec("Ibiko model", "qwen3-0.6b-q4km.gguf",
            HF + "Qwen3-0.6B-GGUF/resolve/50968a4468ef4233ed78cd7c3de230dd1d61a56b/Qwen3-0.6B-Q4_K_M.gguf",
            396705472L, "ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a", "Apache 2.0",
            "Qwen3", "0.6B", "Fastest and smallest: quick replies, simple questions.", 2, true);
    public static final ModelSpec QWEN3_1_7B = new ModelSpec("Ibiko model", "qwen3-1.7b-q4km.gguf", // the file name stays: installed copies keep working
            HF + "Qwen3-1.7B-GGUF/resolve/d7f544eead698dbd1f15126ef60b45a1e1933222/Qwen3-1.7B-Q4_K_M.gguf",
            1107409472L, "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897", "Apache 2.0",
            "Qwen3", "1.7B", "Fast and light. The default.", 4, true);
    public static final ModelSpec QWEN3_4B = new ModelSpec("Ibiko model", "qwen3-4b-q4km.gguf",
            HF + "Qwen3-4B-GGUF/resolve/22c9fc8a8c7700b76a1789366280a6a5a1ad1120/Qwen3-4B-Q4_K_M.gguf",
            2497281312L, "f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a", "Apache 2.0",
            "Qwen3", "4B", "Balanced: much better reasoning and code, slower.", 6, true);
    public static final ModelSpec QWEN3_8B = new ModelSpec("Ibiko model", "qwen3-8b-q4km.gguf",
            HF + "Qwen3-8B-GGUF/resolve/a6adef130ffb23ddaf1a62fec9dced968c9bc482/Qwen3-8B-Q4_K_M.gguf",
            5027784512L, "120307ba529eb2439d6c430d94104dabd578497bc7bfe7e322b5d9933b449bd4", "Apache 2.0",
            "Qwen3", "8B", "Strongest: the best answers here, for devices with plenty of memory; slow.", 10, true);
    public static final ModelSpec LLAMA_3_2_1B = new ModelSpec("Ibiko model", "llama-3.2-1b-instruct-q4km.gguf",
            HF + "Llama-3.2-1B-Instruct-GGUF/resolve/b69aef112e9f895e6f98d7ae0949f72ff09aa401/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            807694368L, "3f5a22426976ab26cfe84dba63c1d08391717abb1af893e10f1b2968d862dcc1", "Llama 3.2 Community Licence",
            "Llama 3.2", "1B", "Meta's small Llama: fast everyday chat.", 3, false);
    public static final ModelSpec LLAMA_3_2_3B = new ModelSpec("Ibiko model", "llama-3.2-3b-instruct-q4km.gguf",
            HF + "Llama-3.2-3B-Instruct-GGUF/resolve/e7d0997e49c9cb00d88b4c1a6a16aa894b0bbc31/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            2019377600L, "6c99cc00ae910f6a532a80022cb4bc1939094527a089c29294b841c0bd87f74d", "Llama 3.2 Community Licence",
            "Llama 3.2", "3B", "Meta's Llama: good everyday chat and writing.", 6, false);
    public static final ModelSpec GEMMA_3_1B = new ModelSpec("Ibiko model", "gemma-3-1b-it-q4km.gguf",
            HF + "gemma-3-1b-it-GGUF/resolve/f0b45be0aac41bd6a100a4b5734cad5f67255bfb/gemma-3-1b-it-Q4_K_M.gguf",
            806058272L, "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd", "Gemma Terms of Use",
            "Gemma 3", "1B", "Google's small Gemma: fast, light writing.", 3, false);
    public static final ModelSpec GEMMA_3_4B = new ModelSpec("Ibiko model", "gemma-3-4b-it-q4km.gguf",
            HF + "gemma-3-4b-it-GGUF/resolve/5a3566e716d80f709ed7b79817eaf7733d2a1fce/gemma-3-4b-it-Q4_K_M.gguf",
            2489894016L, "04a43a22e8d2003deda5acc262f68ec1005fa76c735a9962a8c77042a74a7d19", "Gemma Terms of Use",
            "Gemma 3", "4B", "Google's Gemma: natural writing, many languages.", 6, false);
    public static final ModelSpec PHI_4_MINI = new ModelSpec("Ibiko model", "phi-4-mini-instruct-q4km.gguf",
            HF + "Phi-4-mini-instruct-GGUF/resolve/78eb92a46fc37e6b524df991ed9aca9bc6aa7b80/Phi-4-mini-instruct-Q4_K_M.gguf",
            2491874272L, "88c00229914083cd112853aab84ed51b87bdf6b9ce42f532d8c85c7c63b1730a", "MIT",
            "Phi-4", "mini", "Microsoft's Phi: maths and step-by-step reasoning.", 6, false);

    /** Every model on offer, family by family, smallest first. */
    public static final ModelSpec[] ALL = {QWEN3_0_6B, QWEN3_1_7B, QWEN3_4B, QWEN3_8B, LLAMA_3_2_1B, LLAMA_3_2_3B, GEMMA_3_1B, GEMMA_3_4B,
            PHI_4_MINI};

    /** The families, in the order shown. */
    public static java.util.List<String> families() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (ModelSpec m : ALL) {
            if (!out.contains(m.family)) {
                out.add(m.family);
            }
        }
        return out;
    }

    /** One family's sizes, smallest first. */
    public static java.util.List<ModelSpec> sizes(String family) {
        java.util.List<ModelSpec> out = new java.util.ArrayList<ModelSpec>();
        for (ModelSpec m : ALL) {
            if (m.family.equals(family)) {
                out.add(m);
            }
        }
        return out;
    }

    /** The model kept under {@code file}, or the default when there is none (a choice from an older build). */
    public static ModelSpec byFile(String file) {
        for (ModelSpec m : ALL) {
            if (m.file.equals(file)) {
                return m;
            }
        }
        return QWEN3_1_7B;
    }

    /** The system prompt as this model should read it: "/no_think" only for a model that understands it. */
    public String prompt(String system) {
        return system == null || thinks ? system : system.replace("/no_think", "").trim();
    }
    public static final String QUANT = "Q4_K_M";
    public static final String ENGINE = "llama.cpp";

    /** One sentence a person can check against the file on the phone. */
    public String runningModel() {
        return weights + ", " + QUANT + " GGUF, through " + ENGINE + ". The file is " + file + ", " + sizeWords()
                + ", licence " + licence + ".";
    }

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
