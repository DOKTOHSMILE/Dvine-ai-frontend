package ai.dvine.llm;

/**
 * The bridge to llama.cpp (app/src/main/cpp). The library is loaded on first use; when it can't be
 * (a phone without 64-bit ARM, or a build without the native part) {@link #available()} says so and
 * the personas carry on with the offline brain.
 */
public final class NativeLlm {
    /** Receives the reply as it is written, whole characters only; return false to stop. */
    public interface Sink {
        boolean onText(String text);
    }

    private static Boolean loaded;
    private static String why;

    private NativeLlm() {
    }

    public static synchronized boolean available() {
        if (loaded == null) {
            try {
                System.loadLibrary("dvine_llm");
                loaded = true;
            } catch (UnsatisfiedLinkError | SecurityException e) {
                loaded = false;
                why = e.getMessage();
            }
        }
        return loaded;
    }

    /** Loads the library from a file path instead (desktop tests of the same bridge). */
    static synchronized boolean loadFrom(String path) {
        try {
            System.load(path);
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            loaded = false;
            why = e.getMessage();
        }
        return loaded;
    }

    /** Why the library did not load (null when it did or was never tried). */
    public static synchronized String loadError() {
        return why;
    }

    static native long nLoad(String path, int contextTokens, int threads);

    static native String nReply(long handle, String system, String[] roles, String[] texts, int maxTokens,
                                float temperature, Sink sink);

    static native String nError(long handle);

    static native void nCancel(long handle);

    static native void nFree(long handle);
}
