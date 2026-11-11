package ai.ibiko.main;

import android.content.Context;

/**
 * What Ibiko gets ready while its splash shows, so nothing waits for it once the chat is in use: the voice (loaded
 * and warmed by DvineApp), Ibiko 1's built-in facts and its dictionary and concept tables, the chats file, and this
 * chat's folder. It runs once per start, on a low-priority thread; the splash stays at most {@link #SPLASH_MS}.
 */
public final class Warmup {
    /** The longest the splash waits for the essentials before the chat shows anyway. */
    public static final long SPLASH_MS = 1200;

    private static volatile boolean ready, started;
    private static long since;

    private Warmup() {
    }

    /** Starts the work once (from the app's start). */
    public static synchronized void start(final Context app) {
        if (started) {
            return;
        }
        started = true;
        since = android.os.SystemClock.uptimeMillis();
        Thread t = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                // the chats file: the screen opens on it first
                ai.ibiko.chat.ChatBook.open(new java.io.File(app.getFilesDir(), "chats.json"));
            } catch (Throwable ignored) {
                // the screen opens it itself
            }
            ready = true; // the essentials: the rest goes on behind the chat
            try {
                ai.ibiko.dvine1.Dvine1.warm(app.getFilesDir());
            } catch (Throwable ignored) {
                // Ibiko 1 loads them on its first question instead
            }
        }, "ibiko-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** True when the splash may go: the essentials are ready, or it has waited long enough. */
    public static boolean done() {
        return ready || !started || android.os.SystemClock.uptimeMillis() - since > SPLASH_MS;
    }
}
