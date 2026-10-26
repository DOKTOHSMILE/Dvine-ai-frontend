package ai.dvine;

import android.content.Context;
import android.content.pm.PackageManager;

/** App version for crash reports, without enabling BuildConfig generation. */
final class BuildConfigInfo {
    private BuildConfigInfo() {
    }

    static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
