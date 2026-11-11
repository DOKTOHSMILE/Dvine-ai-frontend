package ai.ibiko.chat.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * The notification bar's side of {@link ai.ibiko.net.Transfers}: each download (the Linux system, a GitHub clone, a
 * download in the terminal) shows its title, its latest line and a bar with the percent (a moving bar while the size
 * is not known), and when it ends, a short "done" that clears itself. Registered once by the app at start.
 */
public final class DownloadNotice implements ai.ibiko.net.Transfers.Sink {
    static final String CHANNEL = "downloads";
    private final Context app;

    public DownloadNotice(Context c) {
        app = c.getApplicationContext();
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Progress of downloads: Linux, packages in the terminal, repositories");
        ch.setShowBadge(false);
        app.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    static int id(String key) {
        return 7300 + Math.abs(key.hashCode() % 500);
    }

    private PendingIntent open() {
        Intent i = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (i == null) {
            i = new Intent();
        }
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(app, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void progress(String key, String title, String line, int percent) {
        if (ai.ibiko.main.KeepAlive.status(app, "dl:" + key, title, line, percent < 0 ? -1 : percent)) {
            return; // shown in Ibiko's one notification
        }
        Notification n = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_stat_download)
                .setContentTitle(percent >= 0 ? title + " · " + percent + "%" : title)
                .setContentText(line)
                .setProgress(100, Math.max(0, percent), percent < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(open())
                .build();
        app.getSystemService(NotificationManager.class).notify(id(key), n);
    }

    @Override
    public void end(String key, String title, String line, boolean ok) {
        if (ai.ibiko.main.KeepAlive.running()) {
            ai.ibiko.main.KeepAlive.done(app, "dl:" + key);
            app.getSystemService(NotificationManager.class).cancel(id(key)); // one begun before Ibiko ran in the background
            if (ok) {
                return; // a finished download says so in the chat; only a stopped one gets a notification of its own
            }
        }
        Notification n = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_stat_download)
                .setContentTitle((ok ? "Done: " : "Stopped: ") + title)
                .setContentText(line)
                .setAutoCancel(true)
                .setTimeoutAfter(ok ? 6000 : 20000)
                .setOnlyAlertOnce(true)
                .setContentIntent(open())
                .build();
        app.getSystemService(NotificationManager.class).notify(id(key), n);
    }
}
