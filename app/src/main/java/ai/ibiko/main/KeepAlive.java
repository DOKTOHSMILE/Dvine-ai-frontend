package ai.ibiko.main;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/**
 * Keeps Ibiko running in the background until the pilot closes it: a quiet notification ("Ibiko is running") with a
 * Close button, and a foreground service the system restarts if it has to stop it. Work, replies, downloads and the
 * shell carry on with the screen off or another app open. Closing: the notification's Close, or swiping Ibiko away
 * from the recent apps. Settings › Advanced › Keep running in the background turns it off.
 */
public final class KeepAlive extends Service {
    static final String CHANNEL = "running";
    static final int NOTE = 7010;
    /** The one notification's id (other services in front share it, so only one shows). */
    public static final int NOTE_ID = NOTE;
    static final String ACTION_CLOSE = "ai.ibiko.action.CLOSE";
    /** Sent to the open screens when the pilot closes Ibiko from the notification. */
    public static final String CLOSED = "ai.ibiko.action.CLOSED";
    public static final String PREF = "keep_running";

    /** True while this service runs: then Ibiko shows one notification only, this one, with what is going on now. */
    static volatile boolean running;

    /** What is going on now (a reply being written, a clone, a download), newest last: shown in the one notification. */
    static final class Status {
        final String title, line;
        final int percent;

        Status(String title, String line, int percent) {
            this.title = title;
            this.line = line;
            this.percent = percent;
        }
    }

    private static final java.util.LinkedHashMap<String, Status> now = new java.util.LinkedHashMap<String, Status>();

    /**
     * Shows work in the one running notification instead of a notification of its own. {@code percent} -1 is a moving
     * bar, -2 no bar. False when Ibiko isn't running in the background: the caller shows its own notification then.
     */
    public static boolean status(Context c, String key, String title, String line, int percent) {
        if (!running) {
            return false;
        }
        synchronized (now) {
            now.remove(key);
            now.put(key, new Status(title, line, percent));
        }
        refresh(c);
        return true;
    }

    /** The work under {@code key} ended: the notification goes back to the next thing, or to "Ibiko is running". */
    public static void done(Context c, String key) {
        boolean had;
        synchronized (now) {
            had = now.remove(key) != null;
        }
        if (had && running) {
            refresh(c);
        }
    }

    public static boolean running() {
        return running;
    }

    private static void refresh(Context c) {
        try {
            c.getSystemService(NotificationManager.class).notify(NOTE, note(c));
        } catch (RuntimeException ignored) {
            // no notification permission: the work goes on without it
        }
    }

    /** Starts it (from a screen in front): only while the setting is on. */
    public static void start(Context c) {
        SharedPreferences p = c.getSharedPreferences("dvine", Context.MODE_PRIVATE);
        if (!p.getBoolean(PREF, true) || p.getBoolean("closed_by_pilot", false)) {
            return;
        }
        try {
            c.startForegroundService(new Intent(c, KeepAlive.class));
        } catch (RuntimeException e) {
            // not allowed from here (the app is not in front): the next time a screen opens starts it
        }
    }

    /** The pilot closed Ibiko (or turned the setting off): stop for good, until Ibiko is opened again. */
    public static void stop(Context c) {
        c.stopService(new Intent(c, KeepAlive.class));
    }

    /** Opening Ibiko again clears an earlier Close. */
    public static void opened(Context c) {
        c.getSharedPreferences("dvine", Context.MODE_PRIVATE).edit().putBoolean("closed_by_pilot", false).apply();
        start(c);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CLOSE.equals(intent.getAction())) {
            close();
            return START_NOT_STICKY;
        }
        channel(this);
        try {
            startForeground(NOTE, note(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            running = true;
        } catch (RuntimeException e) {
            stopSelf(); // not allowed to run in front now: nothing held
            return START_NOT_STICKY;
        }
        // stopped by the system (memory): started again when it can
        return START_STICKY;
    }

    /** The notification's Close: every screen closes, the service stops, and nothing starts it until Ibiko is opened. */
    private void close() {
        running = false;
        getSharedPreferences("dvine", MODE_PRIVATE).edit().putBoolean("closed_by_pilot", true).apply();
        sendBroadcast(new Intent(CLOSED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** Swiped away from the recent apps: the pilot closed it. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        running = false;
        getSharedPreferences("dvine", MODE_PRIVATE).edit().putBoolean("closed_by_pilot", true).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static void channel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Ibiko running", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("Shown while Ibiko keeps running in the background");
        ch.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** The one notification: what is going on now (with its bar), or "Ibiko is running". */
    public static Notification note(Context c) {
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open == null) {
            open = new Intent();
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent close = PendingIntent.getService(c, 11, new Intent(c, KeepAlive.class).setAction(ACTION_CLOSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Status top = null;
        int more;
        synchronized (now) {
            for (Status st : now.values()) {
                top = st;
            }
            more = Math.max(0, now.size() - 1);
        }
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_launcher_monochrome) // the eyes, in one colour
                .setOnlyAlertOnce(true);
        if (top == null) {
            b.setContentTitle("Ibiko is running").setContentText("Ready in the background. Tap to open.");
        } else {
            b.setContentTitle(top.percent >= 0 ? top.title + " \u00B7 " + top.percent + "%" : top.title)
                    .setContentText(top.line);
            if (top.percent != -2) {
                b.setProgress(100, Math.max(0, top.percent), top.percent < 0);
            }
            if (more > 0) {
                b.setSubText(more + " more running");
            }
        }
        return b
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(tap)
                .addAction(new Notification.Action.Builder(null, "Close Ibiko", close).build())
                .build();
    }

    /** Asks once to be left out of battery saving, so the system doesn't freeze Ibiko in the background. */
    public static void askBatteryOnce(Activity a) {
        SharedPreferences p = a.getSharedPreferences("dvine", Context.MODE_PRIVATE);
        if (p.getBoolean("battery_asked", false) || !p.getBoolean(PREF, true) || "robolectric".equals(android.os.Build.FINGERPRINT)) {
            return;
        }
        android.os.PowerManager pm = a.getSystemService(android.os.PowerManager.class);
        if (pm == null || pm.isIgnoringBatteryOptimizations(a.getPackageName())) {
            return;
        }
        p.edit().putBoolean("battery_asked", true).apply();
        try {
            a.startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:" + a.getPackageName())));
        } catch (RuntimeException e) {
            // no such screen on this device
        }
    }
}
