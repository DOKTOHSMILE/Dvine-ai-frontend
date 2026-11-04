package ai.dvine;

import android.app.Application;

/**
 * The app as a whole. It installs the crash catcher before any screen, service or thread starts, so a crash anywhere
 * leaves a report in Download/DVINE. It also collects, off the main thread, any native crash or freeze from the last
 * run that the catcher could not see.
 */
public final class DvineApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        if (!"robolectric".equals(android.os.Build.FINGERPRINT)) {
            // Google search with no key: google.com read in a browser on the device that isn't shown
            ai.dvine.net.WebSearch.browser = (q, most) -> ai.dvine.net.GoogleWeb.search(this, q, most);
        }
        Thread t = new Thread(() -> {
            try {
                CrashLog.collectExits(this);
            } catch (Throwable ignored) {
                // never let the report keep the app from starting
            }
        }, "dvine-exits");
        t.setDaemon(true);
        t.start();
    }
}
