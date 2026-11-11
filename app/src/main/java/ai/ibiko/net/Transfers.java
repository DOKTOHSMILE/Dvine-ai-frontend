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
    /** More than the notification: the bar on the chat screen listens here, and is removed when that screen closes. */
    private static final java.util.List<Sink> more = new java.util.concurrent.CopyOnWriteArrayList<Sink>();

    public static void use(Sink s) {
        sink = s;
    }

    public static void listen(Sink s) {
        if (s != null && !more.contains(s)) {
            more.add(s);
        }
    }

    public static void unlisten(Sink s) {
        more.remove(s);
    }

    public static void progress(String key, String title, String line, int percent) {
        int p = Math.max(-1, Math.min(100, percent));
        tell(s -> s.progress(key, title, line, p));
    }

    public static void end(String key, String title, String line, boolean ok) {
        tell(s -> s.end(key, title, line, ok));
    }

    private static void tell(java.util.function.Consumer<Sink> c) {
        Sink s = sink;
        if (s != null) {
            try {
                c.accept(s);
            } catch (RuntimeException ignored) {
                // a notification that can't be shown never stops a download
            }
        }
        for (Sink e : more) {
            try {
                c.accept(e);
            } catch (RuntimeException ignored) {
                // the on-screen bar never stops a download
            }
        }
    }
}
