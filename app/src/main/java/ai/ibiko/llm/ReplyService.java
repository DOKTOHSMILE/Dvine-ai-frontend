package ai.ibiko.llm;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;


/**
 * Keeps the phone from freezing the brain while a reply is being written and the app is not on screen.
 * A quiet notification and a partial wake lock, dropped as soon as the reply is finished.
 */
public final class ReplyService extends Service {
    static final String ACTION_START = "ai.ibiko.action.KEEP_THINKING";
    static final String ACTION_STOP = "ai.ibiko.action.STOP_THINKING";

    private PowerManager.WakeLock wake;
    /** Keeps Wi-Fi awake too while a reply is on its way, so a minimised app doesn't lose the connection. */
    private android.net.wifi.WifiManager.WifiLock wifi;

    /** How many pieces of model work hold the service (a reply, a model loading, a message being read…). */
    private static int holders;

    public static void start(Context context) {
        hold(context);
    }

    public static void stop(Context context) {
        release(context);
    }

    /**
     * Model work starts: the app is kept in the foreground (full speed, not throttled or stopped when it leaves the
     * screen) until every piece of work that held it has let go.
     */
    public static synchronized void hold(Context context) {
        if (holders++ == 0) {
            try {
                context.startForegroundService(new Intent(context, ReplyService.class).setAction(ACTION_START));
            } catch (RuntimeException e) {
                // not allowed to start now (rare): the work still runs, only without the foreground guarantee
            }
        }
    }

    /** One piece of model work is done; the service stops when none is left. */
    public static synchronized void release(Context context) {
        if (holders <= 0) {
            holders = 0;
            return;
        }
        if (--holders == 0) {
            try {
                context.startService(new Intent(context, ReplyService.class).setAction(ACTION_STOP));
            } catch (RuntimeException ignored) {
                // already stopped
            }
        }
    }

    /** Whether any model work holds the service now. */
    public static synchronized boolean held() {
        return holders > 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ai.ibiko.main.KeepAlive.channel(this);
        boolean stopping = intent != null && ACTION_STOP.equals(intent.getAction());
        // one notification only, shared with the background work's (main/KeepAlive): "Ibiko is thinking"
        if (!stopping) {
            ai.ibiko.main.KeepAlive.mark("reply", "Ibiko is thinking", "The reply will be in the chat when you come back.", -2);
        }
        startForeground(ai.ibiko.main.KeepAlive.NOTE_ID, ai.ibiko.main.KeepAlive.note(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (stopping) {
            unlock();
            boolean shared = ai.ibiko.main.KeepAlive.running();
            ai.ibiko.main.KeepAlive.done(this, "reply");
            stopForeground(shared && ai.ibiko.main.KeepAlive.running() ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
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
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ibiko:reply");
        wake.acquire(30 * 60 * 1000L); // a long API answer (up to 10 minutes) and its tools fit
        // no high-performance Wi-Fi lock: it holds the radio at full power for the whole task and warms the device;
        // the foreground service already keeps the connection alive
    }

    private void unlock() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
        wake = null;
        if (wifi != null && wifi.isHeld()) {
            wifi.release();
        }
        wifi = null;
    }
}
