package ai.dvine.llm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;

import ai.dvine.R;

/**
 * Downloads the language model in the background (a foreground service, so it carries on when the app is
 * minimised or the screen is off), with a notification showing the progress, speed and time left and a Cancel
 * button. Holds a partial wake lock and a Wi-Fi lock only while downloading.
 */
public final class ModelDownloadService extends Service {
    static final String ACTION_START = "ai.dvine.action.DOWNLOAD_MODEL";
    static final String ACTION_CANCEL = "ai.dvine.action.CANCEL_MODEL_DOWNLOAD";
    private static final String CHANNEL = "model_download";
    private static final int PROGRESS_ID = 7001, RESULT_ID = 7002;

    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private long lastNote;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final ModelHub hub = ModelHub.get(this);
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            hub.stopDownload();
            if (!hub.running) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        channel(this);
        startForeground(PROGRESS_ID, progress(this, hub.status), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (hub.running) {
            return START_NOT_STICKY;
        }
        hub.running = true;
        hub.cancel = false;
        lock();
        final java.io.File part = new java.io.File(hub.model.file().getPath() + ".part");
        hub.status.begin(hub.model.spec.bytes, part.isFile() ? part.length() : 0L, System.currentTimeMillis());
        hub.changed();
        Thread t = new Thread(() -> {
            ModelDownloader.Result r = hub.model.download(ModelDownloader.https(hub.model.spec.url), (done, total) -> {
                hub.status.update(done, System.currentTimeMillis());
                long now = System.currentTimeMillis();
                if (now - lastNote >= 1000) {
                    lastNote = now;
                    manager().notify(PROGRESS_ID, progress(this, hub.status));
                    hub.changed();
                }
                return !hub.cancel;
            });
            hub.status.finish(r == ModelDownloader.Result.DONE ? DownloadStatus.Phase.DONE
                    : r == ModelDownloader.Result.STOPPED ? DownloadStatus.Phase.PAUSED
                    : r == ModelDownloader.Result.BAD_FILE ? DownloadStatus.Phase.BAD_FILE : DownloadStatus.Phase.FAILED);
            hub.running = false;
            unlock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            manager().notify(RESULT_ID, result(this, hub.status));
            hub.changed();
            stopSelf();
        }, "dvine-model-download");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unlock();
        super.onDestroy();
    }

    private NotificationManager manager() {
        return getSystemService(NotificationManager.class);
    }

    static void channel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Language model download", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Progress of DVINE's on-device language model download");
        ch.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private static PendingIntent openApp(Context c) {
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open == null) {
            open = new Intent();
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The ongoing notification: percent in the title, size · speed · time left, a bar, Pause. */
    static Notification progress(Context c, DownloadStatus s) {
        Intent cancel = new Intent(c, ModelDownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(c, 1, cancel, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean waiting = s.phase() != DownloadStatus.Phase.DOWNLOADING;
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("Downloading DVINE's language model · " + s.percent() + "%")
                .setContentText(s.detail())
                .setProgress(100, s.percent(), waiting)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp(c))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_stat_download), "Pause",
                        cancelPi).build())
                .build();
    }

    /** The notification left when it ends: ready, paused or stopped; tapping it opens DVINE. */
    static Notification result(Context c, DownloadStatus s) {
        boolean ok = s.phase() == DownloadStatus.Phase.DONE;
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(ok ? "DVINE's language model is ready" : "Language model download " + (s.phase() == DownloadStatus.Phase.PAUSED ? "paused" : "stopped"))
                .setContentText(ok ? "Open DVINE and ask anything, even offline." : s.detail())
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .build();
    }

    @SuppressWarnings("deprecation")
    private void lock() {
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dvine:model-download");
            wake.acquire(4 * 60 * 60 * 1000L);
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dvine:model-download");
                wifi.acquire();
            }
        } catch (RuntimeException e) {
            // no locks: the download still runs, it may just slow down with the screen off
        }
    }

    private void unlock() {
        try {
            if (wake != null && wake.isHeld()) {
                wake.release();
            }
            if (wifi != null && wifi.isHeld()) {
                wifi.release();
            }
        } catch (RuntimeException e) {
            // already released
        }
        wake = null;
        wifi = null;
    }
}
