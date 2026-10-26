package ai.dvine.create;

/**
 * Where something used for an object came from: a reference page that was
 * read, or an asset that was imported. A reference informs the design; it is
 * not a verified measurement, and the record says so.
 */
public final class Provenance {
    public final String kind; // "reference" or "asset" or "default"
    public final String source;
    public final long retrievedAt;
    public final String license;
    public final String note;

    public Provenance(String kind, String source, long retrievedAt, String license, String note) {
        this.kind = kind;
        this.source = source;
        this.retrievedAt = retrievedAt;
        this.license = license == null ? "unknown" : license;
        this.note = note == null ? "" : note;
    }
}
