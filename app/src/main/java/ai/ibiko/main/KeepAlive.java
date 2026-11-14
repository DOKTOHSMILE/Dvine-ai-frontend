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
import android.os.PowerManager;

/**
 * Keeps Ibiko working in the background while it has work: a reply being written, a clone, a download. One quiet notification says what is going on (with its bar) and has a Close button; when the work ends the service stops and the notification goes. Idle, nothing runs and nothing shows: there is no work to keep, and the chats are saved. Leaving the app (Home, the screen off, or swiping it away) does not pause that work. Closing is the notification's Close button.
 */
public final class KeepAlive extends Service {
    static final String CHANNEL = "running-work";
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

    /** How long the phone stays awake for one stretch of work. A new start refreshes it. */
    public static final long AWAKE_MS = 6L * 60 * 60 * 1000;

    private PowerManager.WakeLock wake;

    private static final java.util.LinkedHashMap<String, Status> now = new java.util.LinkedHashMap<String, Status>();

    /** Starts the service when work is already recorded and it is not up yet (the screen just left). */
    public static void ensure(Context c) {
        if (!allowed(c) || idle()) {
            return;
        }
        if (running) {
            refresh(c);
            return;
        }
        start(c);
    }

    /**
     * Shows work in the one running notification instead of a notification of its own. {@code percent} -1 is a moving bar, -2 no bar. False when Ibiko isn't running in the background: the caller shows its own notification then.
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

    /** Work keeps running in the background until the pilot taps Close. It is not a setting that can be turned off. */
    static boolean allowed(Context c) {
        SharedPreferences p = c.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
        return !p.getBoolean("closed_by_pilot", false);
    }

    /** Starts it for work in hand; false when the pilot closed Ibiko, or it is not allowed from here. */
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

    /** The pilot closed Ibiko from the notification: stop until Ibiko is opened again. */
    public static void stop(Context c) {
        c.stopService(new Intent(c, KeepAlive.class));
    }

    /** Opening Ibiko again clears an earlier Close. Nothing starts: the service runs only while there is work. */
    public static void opened(Context c) {
        c.getSharedPreferences("ibiko", Context.MODE_PRIVATE).edit()
                .putBoolean("closed_by_pilot", false).apply();
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
        lock();
        return START_STICKY; // brought back if the system drops it while work is still going
    }

    /** The notification's Close: every screen closes, the service stops, and nothing starts it until Ibiko is opened. */
    private void close() {
        running = false;
        synchronized (now) {
            now.clear();
        }
        getSharedPreferences("ibiko", MODE_PRIVATE).edit().putBoolean("closed_by_pilot", true)
                .apply();
        sendBroadcast(new Intent(CLOSED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /**
     * The app left the recent apps. Work that is still going stays: Home and a swipe both used to freeze the process, which paused every task. Idle, there is nothing to keep.
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!idle()) {
            try {
                startForeground(NOTE, note(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } catch (RuntimeException ignored) {
                // already in front, or not allowed this moment: the wake lock still holds the work
            }
            lock();
            return;
        }
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        unlock();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void channel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Ibiko working",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(
                "Shown while Ibiko works in the background, so the phone does not pause the task");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** The one notification: what is going on now (with its bar), or "Ibiko is running". */
    public static Notification note(Context c) {
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open == null) {
            open = new Intent();
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 10, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent close = PendingIntent.getService(c, 11,
                new Intent(c, KeepAlive.class).setAction(ACTION_CLOSE),
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
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (top == null) {
            b.setContentTitle("Ibiko is working").setContentText("Tap to open.");
        } else {
            b.setContentTitle(
                    top.percent >= 0 ? top.title + " \u00B7 " + top.percent + "%" : top.title)
                    .setContentText(top.line);
            if (top.percent != -2) {
                b.setProgress(100, Math.max(0, top.percent), top.percent < 0);
            }
            if (more > 0) {
                b.setSubText(more + " more running");
            }
        }
        return b.setOngoing(true).setShowWhen(false).setContentIntent(tap)
                .addAction(new Notification.Action.Builder(null, "Close Ibiko", close).build())
                .build();
    }

    /** The phone stays awake for the work. Released when the service stops. */
    private void lock() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null) {
            return;
        }
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ibiko:work");
        wake.setReferenceCounted(false);
        wake.acquire(AWAKE_MS);
    }

    private void unlock() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
        wake = null;
    }

    /** Asks once to be left out of battery saving, so the system doesn't freeze Ibiko in the background. */
    public static void askBatteryOnce(Activity a) {
        SharedPreferences p = a.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
        if (p.getBoolean("battery_asked", false)
                || "robolectric".equals(android.os.Build.FINGERPRINT)) {
            return;
        }
        android.os.PowerManager pm = a.getSystemService(android.os.PowerManager.class);
        if (pm == null || pm.isIgnoringBatteryOptimizations(a.getPackageName())) {
            return;
        }
        p.edit().putBoolean("battery_asked", true).apply();
        try {
            a.startActivity(new Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:" + a.getPackageName())));
        } catch (RuntimeException e) {
            // no such screen on this device
        }
    }
}
