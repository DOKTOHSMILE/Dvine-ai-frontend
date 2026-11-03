package ai.dvine;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The crash catcher. From the moment the app starts it catches every crash on any thread, then writes a report and
 * lets Android close the app as usual. The report holds the version, the phone, the error with its causes, and the
 * app's own recent log.
 *
 * Each report is written twice: to {@code files/crash.txt}, for the banner on the next start, and to the phone's
 * Downloads folder as {@code Download/DVINE/dvine-crash-….txt}, where the user can open and copy it. Crashes the Java
 * catcher can't see are collected on the next start from Android's own record of how the app last closed: a crash in
 * the native model engine, the app freezing (ANR), or the system killing it for memory. Writing to Downloads needs no
 * permission on Android 10 and later.
 */
public final class CrashLog {
    static final String FOLDER = "DVINE";
    private static volatile boolean installed;

    private CrashLog() {
    }

    /** Catches every crash from now on (once per process; later calls do nothing). */
    public static synchronized void install(final Context context) {
        if (installed) {
            return;
        }
        installed = true;
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String report = report(app, "Crash on thread \"" + thread.getName() + "\"", stack(error), log());
                keep(app, report);
                toDownloads(app, "dvine-crash-" + stamp(System.currentTimeMillis()) + ".txt", report);
            } catch (Throwable ignored) {
                // the report must never stop the app from closing
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /**
     * On start: the ways the app closed since the last look that the Java catcher could not see (a native crash, a
     * freeze, the system ending it for memory), each written to Downloads. Call off the main thread. Returns how many.
     */
    public static int collectExits(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return 0;
        }
        SharedPreferences prefs = context.getSharedPreferences("dvine", Context.MODE_PRIVATE);
        long seen = prefs.getLong("exit_seen", 0L);
        long newest = seen;
        int saved = 0;
        List<ApplicationExitInfo> exits;
        try {
            exits = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
        } catch (RuntimeException e) {
            return 0;
        }
        for (ApplicationExitInfo e : exits) {
            newest = Math.max(newest, e.getTimestamp());
            if (e.getTimestamp() <= seen || !worthKeeping(e.getReason())) {
                continue;
            }
            String trace = "";
            try (InputStream in = e.getTraceInputStream()) {
                if (in != null) {
                    byte[] raw = readAll(in, 4 << 20);
                    // an ANR trace is text; a native crash is a tombstone (binary): its readable parts are the backtrace
                    trace = e.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE ? readable(raw)
                            : new String(raw, StandardCharsets.UTF_8);
                }
            } catch (IOException | RuntimeException ignored) {
                // no trace kept
            }
            String what = reasonName(e.getReason()) + " at " + new Date(e.getTimestamp())
                    + "\nProcess: " + e.getProcessName() + " (pid " + e.getPid() + ")"
                    + "\nAndroid says: " + (e.getDescription() == null ? "(nothing)" : e.getDescription())
                    + "\nMemory at the end: " + e.getPss() / 1024 + " MB PSS, " + e.getRss() / 1024 + " MB RSS";
            String report = report(context, what, trace.isEmpty() ? "(Android kept no trace)" : trace, null);
            if (toDownloads(context, "dvine-" + slug(e.getReason()) + "-" + stamp(e.getTimestamp()) + ".txt", report) != null) {
                saved++;
            }
            if (e.getReason() != ApplicationExitInfo.REASON_CRASH) {
                keep(context, report); // the banner shows it (a Java crash is already kept by the catcher)
            }
        }
        prefs.edit().putLong("exit_seen", newest).apply();
        return saved;
    }

    /** A Java crash is caught by {@link #install}; these are the ones it can't see, and Java crashes it missed. */
    static boolean worthKeeping(int reason) {
        return reason == ApplicationExitInfo.REASON_CRASH_NATIVE || reason == ApplicationExitInfo.REASON_ANR
                || reason == ApplicationExitInfo.REASON_LOW_MEMORY || reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
                || reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE || reason == ApplicationExitInfo.REASON_CRASH;
    }

    static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "Native crash (the model engine or a system library)";
            case ApplicationExitInfo.REASON_ANR:
                return "The app froze (not responding)";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "Closed by Android for memory";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "Closed by Android for using too much";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "Failed to start";
            case ApplicationExitInfo.REASON_CRASH:
                return "Crash";
            default:
                return "Closed (reason " + reason + ")";
        }
    }

    private static String slug(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "native-crash";
            case ApplicationExitInfo.REASON_ANR:
                return "freeze";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "low-memory";
            default:
                return "crash";
        }
    }

    /** The whole report: what happened, on which version and phone, the trace and the app's recent log. */
    static String report(Context c, String what, String trace, String log) {
        StringBuilder b = new StringBuilder();
        b.append("DVINE ").append(BuildConfigInfo.version(c)).append(" on Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(", ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(", ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?")
                .append(")\n");
        Runtime rt = Runtime.getRuntime();
        b.append("Time: ").append(new Date()).append("\n");
        b.append("Java memory: ").append((rt.totalMemory() - rt.freeMemory()) >> 20).append(" of ").append(rt.maxMemory() >> 20)
                .append(" MB\n\n");
        b.append(what).append("\n\n").append(trace == null ? "" : trace.trim()).append("\n");
        if (log != null && !log.trim().isEmpty()) {
            b.append("\n---- recent app log ----\n").append(log.trim()).append("\n");
        }
        return b.toString();
    }

    static String stack(Throwable error) {
        StringWriter s = new StringWriter();
        error.printStackTrace(new PrintWriter(s));
        return s.toString();
    }

    /** The app's own last log lines (no permission needed for its own process); empty when it can't be read. */
    static String log() {
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-v", "time", "-t", "400",
                    "--pid=" + android.os.Process.myPid()).redirectErrorStream(true).start();
            byte[] raw = readAll(p.getInputStream(), 1 << 20);
            p.destroy();
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** The banner's copy, on the phone (replaced by the next one). */
    static void keep(Context c, String report) {
        File out = new File(c.getFilesDir(), "crash.txt");
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(out)) {
            o.write(report.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // the Downloads copy still has it
        }
    }

    /** Writes {@code text} to Download/DVINE/{@code name}; the file's address, or null when it couldn't be written. */
    public static Uri toDownloads(Context c, String name, String text) {
        ContentResolver r = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
        try {
            Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                return null;
            }
            try (OutputStream o = r.openOutputStream(uri)) {
                if (o == null) {
                    return null;
                }
                o.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return uri;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The printable runs (4+ characters) of a binary tombstone: the signal, the library names, the functions. */
    static String readable(byte[] raw) {
        StringBuilder out = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (byte x : raw) {
            int ch = x & 0xff;
            if (ch >= 0x20 && ch < 0x7f) {
                run.append((char) ch);
            } else {
                if (run.length() >= 4) {
                    out.append(run).append('\n');
                }
                run.setLength(0);
            }
        }
        if (run.length() >= 4) {
            out.append(run).append('\n');
        }
        return out.toString();
    }

    private static byte[] readAll(InputStream in, int most) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && b.size() < most) {
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    static String stamp(long at) {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(at));
    }
}
