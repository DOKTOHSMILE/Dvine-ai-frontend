package ai.dvine.agent;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a command in Termux, the full Linux on the device (python, node, gcc, java, git, pkg…), and brings back what
 * it printed and its exit code, through Termux's documented RUN_COMMAND service and its result PendingIntent. Termux
 * can't see DVINE's own files, so the project folder goes with the command as a .tar.gz on its stdin and is unpacked
 * into ~/dvine/<project> first; files the command makes stay in Termux (DVINE's copy changes through write_file).
 * Needs Termux (from F-Droid), "allow-external-apps = true" in ~/.termux/termux.properties, and DVINE allowed to run
 * commands in Termux (Android asks once).
 */
public final class TermuxRunner {
    public static final String PACKAGE = "com.termux";
    public static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String SERVICE = "com.termux.app.RunCommandService";
    static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    static final String HOME = "/data/data/com.termux/files/home";
    /** The most a project may come to (compressed) to go with a command: an intent's room. */
    static final int MOST_BYTES = 600_000;
    public static final long TIMEOUT_MS = 180_000L;
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private TermuxRunner() {
    }

    public static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean allowed(Context c) {
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Termux is there and DVINE may use it. */
    public static boolean usable(Context c) {
        return installed(c) && allowed(c);
    }

    /** The shell script that unpacks the project (when it came along) and runs the command in it. Pure Java. */
    static String script(String project, String command, boolean withFiles) {
        String dir = "$HOME/dvine/" + project.replaceAll("[^A-Za-z0-9._-]", "_");
        return "mkdir -p \"" + dir + "\" && cd \"" + dir + "\" || exit 97\n"
                + (withFiles ? "base64 -d | tar xzf - || { echo 'DVINE: the project files could not be unpacked' >&2; exit 98; }\n" : "")
                + command + "\n";
    }

    /** What the command printed and its exit code, for the model; blocking (call off the main thread). */
    public static String run(Context c, String command, File folder, String project) {
        if (!installed(c)) {
            return "Termux isn't installed on this device (it comes from F-Droid).";
        }
        if (!allowed(c)) {
            return "DVINE isn't allowed to run commands in Termux yet: Connectors › Termux › Allow.";
        }
        byte[] files = null;
        String note = "";
        try {
            files = folder != null && folder.isDirectory() ? TarGz.pack(folder, MOST_BYTES) : null;
            if (folder != null && folder.isDirectory() && files == null) {
                note = "(The project is too big to copy into Termux with each command, so it ran on Termux's copy as it was.)\n";
            }
        } catch (IOException e) {
            note = "(The project files couldn't be copied: " + e.getMessage() + ".)\n";
        }
        final Context app = c.getApplicationContext();
        final String action = "ai.dvine.TERMUX_RESULT." + NEXT.getAndIncrement();
        final Bundle[] result = new Bundle[1];
        final CountDownLatch done = new CountDownLatch(1);
        BroadcastReceiver back = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result[0] = intent.getBundleExtra("result");
                done.countDown();
            }
        };
        app.registerReceiver(back, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        try {
            Intent reply = new Intent(action).setPackage(app.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(app, action.hashCode(), reply,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Intent i = new Intent("com.termux.RUN_COMMAND");
            i.setClassName(PACKAGE, SERVICE);
            i.putExtra("com.termux.RUN_COMMAND_PATH", BASH);
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", script(project, command, files != null)});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            if (files != null) {
                i.putExtra("com.termux.RUN_COMMAND_STDIN", Base64.getMimeEncoder().encodeToString(files));
            }
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi);
            app.startForegroundService(i);
            if (!done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return note + "Termux didn't answer in " + TIMEOUT_MS / 1000 + " seconds: the command may still be running there.";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Stopped.";
        } catch (RuntimeException e) {
            return "Termux couldn't be asked (" + e.getMessage() + "). In Termux, set allow-external-apps = true in"
                    + " ~/.termux/termux.properties.";
        } finally {
            try {
                app.unregisterReceiver(back);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        return note + describe(result[0]);
    }

    /** Termux's result bundle as text: the output, the errors and the exit code. */
    static String describe(Bundle r) {
        if (r == null) {
            return "Termux gave no result.";
        }
        String err = r.getString("errmsg", "");
        if (r.getInt("err", -1) > 0 && !err.isEmpty()) {
            return "Termux couldn't run it: " + err;
        }
        return format(r.getString("stdout", ""), r.getString("stderr", ""), r.getInt("exitCode", -1));
    }

    /** The output for the model. Pure Java. */
    static String format(String out, String err, int code) {
        StringBuilder b = new StringBuilder();
        if (out != null && !out.trim().isEmpty()) {
            b.append(out.trim()).append('\n');
        }
        if (err != null && !err.trim().isEmpty()) {
            b.append("[stderr]\n").append(err.trim()).append('\n');
        }
        b.append("[exit code ").append(code).append(code == 0 ? ": it worked" : code == 127 ? ": not installed in Termux"
                + " (pkg install …)" : code == 98 ? ": the project files weren't unpacked" : "").append(']');
        return b.toString();
    }
}
