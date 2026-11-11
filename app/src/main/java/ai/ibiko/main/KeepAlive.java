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
 * Keeps Ibiko working in the background while it has work: a reply being written, a clone, a download. One quiet
 * notification says what is going on (with its bar) and has a Close button; when the work ends the service stops and
 * the notification goes. Idle, nothing runs and nothing shows: there is no work to keep, and the chats are saved.
 * Closing: the notification's Close, or swiping Ibiko away from the recent apps. Settings › Coding turns it off.
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
        if (!allowed(c)) {
            return false;
        }
        mark(key, title, line, percent);
        if (running) {
            refresh(c);
            return true;
        }
        if (start(c)) {
            return true;
        }
        synchronized (now) {
            now.remove(key);
        }
        return false;
    }

    /** Records work without starting this service (a service that is in front already shows the one notification). */
    public static void mark(String key, String title, String line, int percent) {
        synchronized (now) {
            now.remove(key);
            now.put(key, new Status(title, line, percent));
        }
    }

    static boolean idle() {
        synchronized (now) {
            return now.isEmpty();
        }
    }

    /** The work under {@code key} ended: the notification goes back to the next thing, or to "Ibiko is running". */
    public static void done(Context c, String key) {
        boolean had;
        synchronized (now) {
            had = now.remove(key) != null;
        }
        if (!had) {
            return;
        }
        if (idle()) {
            // nothing left to keep: the service stops and its notification goes
            if (running) {
                stop(c);
            }
            try {
                c.getSystemService(NotificationManager.class).cancel(NOTE);
            } catch (RuntimeException ignored) {
                // nothing to cancel
            }
        } else if (running) {
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

    /** Whether work may be kept running in the background: the setting is on and the pilot hasn't closed Ibiko. */
    static boolean allowed(Context c) {
        SharedPreferences p = c.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
        return p.getBoolean(PREF, true) && !p.getBoolean("closed_by_pilot", false);
    }

    /** Starts it for work in hand; false when it may not run (the setting is off, or not allowed from here). */
    static boolean start(Context c) {
        if (!allowed(c)) {
            return false;
        }
        try {
            c.startForegroundService(new Intent(c, KeepAlive.class));
            return true;
        } catch (RuntimeException e) {
            return false; // not allowed from here (the app is not in front)
        }
    }

    /** The pilot closed Ibiko (or turned the setting off): stop for good, until Ibiko is opened again. */
    public static void stop(Context c) {
        c.stopService(new Intent(c, KeepAlive.class));
    }

    /** Opening Ibiko again clears an earlier Close. Nothing starts: the service runs only while there is work. */
    public static void opened(Context c) {
        c.getSharedPreferences("ibiko", Context.MODE_PRIVATE).edit().putBoolean("closed_by_pilot", false).apply();
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
        } catch (RuntimeException e) {
            stopSelf(); // not allowed to run in front now: nothing held
            return START_NOT_STICKY;
        }
        if (idle()) {
            // the work ended before it started (or the system restarted it with none): nothing to keep
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        return START_NOT_STICKY; // only for work in hand: never restarted idle
    }

    /** The notification's Close: every screen closes, the service stops, and nothing starts it until Ibiko is opened. */
    private void close() {
        running = false;
        synchronized (now) {
            now.clear();
        }
        getSharedPreferences("ibiko", MODE_PRIVATE).edit().putBoolean("closed_by_pilot", true).apply();
        sendBroadcast(new Intent(CLOSED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** Swiped away from the recent apps: the pilot closed it. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        running = false;
        getSharedPreferences("ibiko", MODE_PRIVATE).edit().putBoolean("closed_by_pilot", true).apply();
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

    public static void channel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Ibiko working", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("Shown only while Ibiko works in the background: what it is doing");
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
            b.setContentTitle("Ibiko is working").setContentText("Tap to open.");
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
        SharedPreferences p = a.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
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
