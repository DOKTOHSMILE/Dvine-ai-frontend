package ai.ibiko.bridge;

/**
 * The sizes read from a GGUF header. Zero means the file did not say.
 * The engine uses these to size the context without loading the weights.
 */
public final class Probe {
    public final int contextLength;
    public final int nLayer;
    public final int nEmbd;
    public final int nHead;
    public final int nHeadKv;
    public final int nEmbdK;
    public final int nEmbdV;
    public final int nGqa;

    public Probe(int contextLength, int nLayer, int nEmbd, int nHead, int nHeadKv,
                 int nEmbdK, int nEmbdV, int nGqa) {
        this.contextLength = contextLength;
        this.nLayer = nLayer;
        this.nEmbd = nEmbd;
        this.nHead = nHead;
        this.nHeadKv = nHeadKv;
        this.nEmbdK = nEmbdK;
        this.nEmbdV = nEmbdV;
        this.nGqa = nGqa;
    }

    /** No header was read. */
    public static Probe unknown() {
        return new Probe(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
