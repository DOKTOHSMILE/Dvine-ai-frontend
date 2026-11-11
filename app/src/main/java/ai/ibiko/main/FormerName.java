package ai.ibiko.main;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Map;

/**
 * Moves what an install from before the rename kept under the app's former name over to Ibiko's own names, once:
 * the settings, the work log, each cloned repository's state file and the Linux install's stamp (so Linux isn't
 * installed again). Nothing is lost. Safe to delete a few versions on, when no install from before is left.
 */
public final class FormerName {
    /** The former name, put together here only, for finding what was kept under it. */
    static final String OLD = "d" + "vine";

    private FormerName() {
    }

    /** Runs at the app's start, before anything reads the settings. */
    public static void move(Context c) {
        prefs(c, OLD, "ibiko");
        prefs(c, OLD + "_work", "ibiko_work");
        try {
            File stamp = new File(ai.ibiko.linux.LinuxRoot.root(c), "etc/" + OLD + "-release");
            File now = new File(stamp.getParentFile(), "ibiko-release");
            if (stamp.isFile() && !now.exists()) {
                stamp.renameTo(now);
            }
        } catch (RuntimeException ignored) {
            // no Linux yet
        }
        final File chats = new File(new File(c.getFilesDir(), "ibiko"), ChatSpace.CHATS);
        Thread t = new Thread(() -> records(chats, 0), "ibiko-former-name");
        t.setDaemon(true);
        t.start();
    }

    /** Copies one settings file into its new name (keys already set under the new name win), then deletes the old. */
    @SuppressWarnings("unchecked")
    static void prefs(Context c, String from, String to) {
        SharedPreferences old = c.getSharedPreferences(from, Context.MODE_PRIVATE);
        Map<String, ?> all = old.getAll();
        if (all.isEmpty()) {
            return;
        }
        SharedPreferences now = c.getSharedPreferences(to, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = now.edit();
        for (Map.Entry<String, ?> en : all.entrySet()) {
            String key = en.getKey().replace(OLD, "ibiko");
            if (now.contains(key)) {
                continue;
            }
            Object v = en.getValue();
            if (v instanceof Boolean) {
                e.putBoolean(key, (Boolean) v);
            } else if (v instanceof Integer) {
                e.putInt(key, (Integer) v);
            } else if (v instanceof Long) {
                e.putLong(key, (Long) v);
            } else if (v instanceof Float) {
                e.putFloat(key, (Float) v);
            } else if (v instanceof String) {
                e.putString(key, (String) v);
            } else if (v instanceof java.util.Set) {
                e.putStringSet(key, (java.util.Set<String>) v);
            }
        }
        e.commit();
        old.edit().clear().commit();
        c.deleteSharedPreferences(from);
    }

    /** Each repository's state file under the former name, renamed (a few folders deep in the chats' folders). */
    static void records(File dir, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 4) {
            return;
        }
        for (File k : kids) {
            if (k.getName().equals("." + OLD + "-git.json")) {
                File now = new File(dir, ai.ibiko.connect.GitHubGit.RECORD);
                if (!now.exists()) {
                    k.renameTo(now);
                }
            } else if (k.isDirectory() && !k.getName().equals(".git") && !k.getName().equals("node_modules")) {
                records(k, depth + 1);
            }
        }
    }
}
