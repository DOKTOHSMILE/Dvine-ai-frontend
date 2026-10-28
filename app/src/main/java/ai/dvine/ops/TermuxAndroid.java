package ai.dvine.ops;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the documented Termux RUN_COMMAND intent. Sending it does not prove the command finished;
 * only a connected result bridge may do that.
 */
public final class TermuxAndroid {
    public static final String ACTION = "com.termux.RUN_COMMAND";
    public static final String PACKAGE = "com.termux";

    private TermuxAndroid() {
    }

    public static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static Intent intent(List<String> argv) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalArgumentException("empty command");
        }
        Intent intent = new Intent();
        intent.setClassName(PACKAGE, "com.termux.app.RunCommandService");
        intent.setAction(ACTION);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", argv.get(0));
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", argv.subList(1, argv.size()).toArray(new String[0]));
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        return intent;
    }

    public static List<String> copy(List<String> argv) {
        return new ArrayList<String>(argv);
    }
}
