package ai.dvine.llm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;

import ai.dvine.R;

/**
 * Keeps the phone from freezing the brain while a reply is being written and the app is not on screen.
 * A quiet notification and a partial wake lock, dropped as soon as the reply is finished.
 */
public final class ReplyService extends Service {
    static final String ACTION_START = "ai.dvine.action.KEEP_THINKING";
    static final String ACTION_STOP = "ai.dvine.action.STOP_THINKING";
    private static final String CHANNEL = "reply";
    private static final int NOTE = 7003;

    private PowerManager.WakeLock wake;

    public static void start(Context context) {
        Intent intent = new Intent(context, ReplyService.class).setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, ReplyService.class).setAction(ACTION_STOP));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        channel(this);
        startForeground(NOTE, note(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            unlock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        lock();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unlock();
        super.onDestroy();
    }

    private void lock() {
        if (wake != null && wake.isHeld()) {
            return;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null) {
            return;
        }
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dvine:reply");
        wake.acquire(10 * 60 * 1000L);
    }

    private void unlock() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
        wake = null;
    }

    private static void channel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Reply", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shown while DVINE is still writing a reply");
        ch.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private static Notification note(Context c) {
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open == null) {
            open = new Intent();
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("DVINE is thinking")
                .setContentText("The reply will be in the chat when you come back.")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(tap)
                .build();
    }
}
