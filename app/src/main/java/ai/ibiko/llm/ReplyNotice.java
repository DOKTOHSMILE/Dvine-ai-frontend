package ai.ibiko.llm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * A reply that finished while DVINE was off screen: it is already saved in its chat, and this notification says so
 * with its first words. Tapping it opens the chat where it waits.
 */
public final class ReplyNotice {
    static final String CHANNEL = "replies";
    static final int ID = 7010;

    private ReplyNotice() {
    }

    public static void show(Context c, String who, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Replies", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("A reply that finished while Ibiko was in the background");
            nm.createNotificationChannel(ch);
        }
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (open == null) {
            open = new Intent();
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String t = text == null ? "" : text.trim();
        String name = "Ibiko"; //ing(1);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_send)
                .setContentTitle(name + " replied")
                .setContentText(t.length() > 120 ? t.substring(0, 120) + "…" : t)
                .setStyle(new Notification.BigTextStyle().bigText(t.length() > 600 ? t.substring(0, 600) + "…" : t))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build();
        try {
            nm.notify(ID, n);
        } catch (SecurityException noPermission) {
            // notifications are off: the reply is in the chat all the same
        }
    }

    public static void clear(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(ID);
        }
    }
}
