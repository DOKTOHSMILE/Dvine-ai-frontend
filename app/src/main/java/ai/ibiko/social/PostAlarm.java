package ai.ibiko.social;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * The pilot's scheduled posts ({@link PostPlan}): kept on the device, one alarm for the next one due. When a post is
 * due, a notification brings it back with its text; "Post now" opens the network's app with the text ready on the
 * pilot's own account, where they press post. A repeating post is planned again; a single one is done. The alarm is
 * set again after the device restarts.
 */
public final class PostAlarm extends BroadcastReceiver {
    static final String PREFS = "ibiko_posts";
    static final String KEY = "plans";
    static final String CHANNEL = "scheduled_posts";
    static final String DUE = "ai.ibiko.POST_DUE";

    public static synchronized List<PostPlan> plans(Context c) {
        return PostPlan.readAll(prefs(c).getString(KEY, ""));
    }

    /** Keeps a plan the pilot said yes to, and sets the alarm. */
    public static synchronized void add(Context c, PostPlan p) {
        List<PostPlan> all = plans(c);
        all.add(p);
        save(c, all);
        arm(c);
    }

    /** Cancels a plan; true when there was one. */
    public static synchronized boolean cancel(Context c, String id) {
        List<PostPlan> all = plans(c);
        boolean gone = all.removeIf(p -> p.id.equals(id));
        save(c, all);
        arm(c);
        return gone;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void save(Context c, List<PostPlan> all) {
        prefs(c).edit().putString(KEY, PostPlan.writeAll(all)).apply();
    }

    /** One alarm, for the plan due first (or none). */
    static void arm(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        PendingIntent fire = PendingIntent.getBroadcast(c, 0, new Intent(c, PostAlarm.class).setAction(DUE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long first = Long.MAX_VALUE;
        for (PostPlan p : plans(c)) {
            first = Math.min(first, p.at);
        }
        if (first == Long.MAX_VALUE) {
            am.cancel(fire);
            return;
        }
        // allowed while the device idles; Android may move it by a few minutes to save battery
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, first, fire);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        final PendingResult later = goAsync(); // posting waits for the page: off the main thread, the receiver kept alive
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                due(app, System.currentTimeMillis());
            } finally {
                later.finish();
            }
        }, "ibiko-post-due").start();
    }

    /** Brings back every plan that is due, plans the repeating ones again, and sets the next alarm. */
    static synchronized void due(Context c, long now) {
        List<PostPlan> keep = new ArrayList<PostPlan>();
        for (PostPlan p : plans(c)) {
            if (p.at > now) {
                keep.add(p);
                continue;
            }
            postOrNotify(c, p);
            PostPlan next = p.next(now, TimeZone.getDefault());
            if (next != null) {
                keep.add(next);
            }
        }
        save(c, keep);
        arm(c);
    }

    /**
     * A post that is due: approved when it was scheduled, so Ibiko posts it from the signed-in account and says how it
     * went; when it can't post there itself, the reminder that opens the app with it ready, as before.
     */
    static void postOrNotify(Context c, PostPlan p) {
        Platform net = SocialPoster.direct(p.network);
        if (net != null && SocialPoster.ready(c, net) && android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            SocialPoster.Result r = SocialPoster.post(c, net, p.text);
            if (r.posted) {
                tell(c, p, net.shown + ": " + r.said);
                return;
            }
        }
        notifyDue(c, p);
    }

    /** A quiet notification with what happened to a scheduled post. */
    private static void tell(Context c, PostPlan p, String what) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Scheduled posts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("A post you scheduled is ready to go out");
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_send)
                .setContentTitle(what)
                .setContentText(p.text)
                .setStyle(new Notification.BigTextStyle().bigText(what + "\n\n" + p.text))
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(p.id.hashCode(), n);
        } catch (SecurityException noPermission) {
            // notifications off: the action log has it
        }
    }

    private static void notifyDue(Context c, PostPlan p) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Scheduled posts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("A post you scheduled is ready to go out");
            nm.createNotificationChannel(ch);
        }
        ai.ibiko.brain.PendingAction a = new ai.ibiko.brain.PendingAction(ai.ibiko.brain.PendingAction.Kind.SHARE, p.text, p.network,
                System.currentTimeMillis());
        PendingIntent post = PendingIntent.getActivity(c, p.id.hashCode(), ai.ibiko.act.Actions.shareIntent(c, a),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_send)
                .setContentTitle("Your " + a.appName() + " post is due")
                .setContentText(p.text)
                .setStyle(new Notification.BigTextStyle().bigText(p.text + "\n\n" + p.repeatWords() + ". Tap to post it from your account."))
                .setContentIntent(post)
                .addAction(new Notification.Action.Builder(null, "Post now", post).build())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        try {
            nm.notify(p.id.hashCode(), n);
        } catch (SecurityException noPermission) {
            // notifications are off for Ibiko: the post waits in the list
        }
    }
}
