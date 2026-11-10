package ai.ibiko.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * While a reply is read aloud, a notification in the panel says who is reading and the sentence it is on, with Pause
 * (Resume when paused) and Stop, so reading can be controlled from outside the app. Gone when reading ends; swiping
 * it away stops the reading.
 */
public final class ReadAloudNotice {
    public static final String CHANNEL = "read_aloud";
    public static final String PAUSE = "ai.ibiko.READ_PAUSE", RESUME = "ai.ibiko.READ_RESUME", STOP = "ai.ibiko.READ_STOP";
    static final int ID = 7301;

    private ReadAloudNotice() {
    }

    /** Shows or updates the notification; {@code reading} false takes it away. */
    public static void show(Context c, boolean reading, boolean paused, String sentence, String who, Class<?> open) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (!reading) {
            nm.cancel(ID);
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Reading aloud", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Pause or stop a reply being read aloud");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Notification.Action toggle = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, paused ? ai.ibiko.R.drawable.ic_speaker : ai.ibiko.R.drawable.ic_pause),
                paused ? "Resume" : "Pause", action(c, paused ? RESUME : PAUSE)).build();
        Notification.Action stop = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, ai.ibiko.R.drawable.ic_stop), "Stop", action(c, STOP)).build();
        PendingIntent tap = PendingIntent.getActivity(c, 0, new Intent(c, open).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String line = sentence == null ? "" : sentence.trim();
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_speaker)
                .setContentTitle(paused ? who + " · paused" : who + " is reading aloud")
                .setContentText(line)
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1))
                .addAction(toggle)
                .addAction(stop)
                .setContentIntent(tap)
                .setDeleteIntent(action(c, STOP))
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        try {
            nm.notify(ID, n);
        } catch (SecurityException noPermission) {
            // notifications are off for the app: reading goes on, controlled in the app
        }
    }

    static PendingIntent action(Context c, String what) {
        return PendingIntent.getBroadcast(c, what.hashCode(), new Intent(what).setPackage(c.getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
