package ai.ibiko.net;

/**
 * Every download the app makes reports here (the Linux system, a GitHub clone, a download in the terminal), and one
 * sink shows it: on the device, a notification with its progress. Without a sink (tests) the reports go nowhere.
 * {@code key} tells transfers apart; a percent below 0 means "size not known yet". Pure Java.
 */
public final class Transfers {
    private Transfers() {
    }

    /** Where the reports go (the notification bar). */
    public interface Sink {
        void progress(String key, String title, String line, int percent);

        void end(String key, String title, String line, boolean ok);
    }

    private static volatile Sink sink;

    public static void use(Sink s) {
        sink = s;
    }

    public static void progress(String key, String title, String line, int percent) {
        Sink s = sink;
        if (s != null) {
            try {
                s.progress(key, title, line, Math.max(-1, Math.min(100, percent)));
            } catch (RuntimeException ignored) {
                // a notification that can't be shown never stops a download
            }
        }
    }

    public static void end(String key, String title, String line, boolean ok) {
        Sink s = sink;
        if (s != null) {
            try {
                s.end(key, title, line, ok);
            } catch (RuntimeException ignored) {
                // as above
            }
        }
    }
}
