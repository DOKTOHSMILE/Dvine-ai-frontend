package ai.ibiko.main;

/**
 * The notice line at the top of the chat after the app went down: it shows that the last run crashed, says where
 * the report was saved (Download/Ibiko), copies it to the clipboard on tap, and forgets it once it has been shown.
 * The catching itself runs from {@link ai.ibiko.CrashLog}, installed when the app starts.
 *
 * <p>The screen it serves is the {@link Host}: it finds the notice line's views, hands over the saved report's
 * home (files dir), the app version it belongs to, and the prefs that remember it has already been shown.
 */
public final class CrashReport {

    /** What the notice needs from the screen it serves. */
    public interface Host {
        /** A view of the screen by id, or null when it isn't there. */
        <T extends android.view.View> T find(int id);

        /** The app, for the crash file, the clipboard, and the version string. */
        android.content.Context context();

        /** The app version a report must belong to, or "?" when it can't be read. */
        String version();

        /** The prefs that remember which report has already been shown once. */
        android.content.SharedPreferences prefs();
    }

    private final Host host;

    public CrashReport(Host host) {
        this.host = host;
    }

    /**
     * The crash catcher is installed when the app starts. It is here too, so a start that skips
     * {@link ai.ibiko.MainActivity}'s usual wiring (tests) still gets one.
     */
    public void install() {
        ai.ibiko.CrashLog.install(host.context());
    }

    /** The banner is shown only for a crash of this version that hasn't been shown yet. */
    public void bind() {
        android.view.View banner = host.find(ai.ibiko.R.id.banner_crash);
        if (banner == null) {
            return;
        }
        String report = freshCrash();
        // shown once after a crash: the report is in Download/Ibiko, and Copy puts it on the clipboard
        banner.setVisibility(report != null ? android.view.View.VISIBLE : android.view.View.GONE);
        android.widget.TextView detail = host.find(ai.ibiko.R.id.tv_crash_detail);
        if (detail != null && report != null) {
            String first = report.split("\n\n", 3).length > 1 ? report.split("\n\n", 3)[1].split("\n")[0] : "";
            detail.setText("Saved in Download/Ibiko" + (first.isEmpty() ? "" : " (" + first + ")") + ". Tap Copy to copy it.");
        }
        android.view.View copy = host.find(ai.ibiko.R.id.btn_copy_crash);
        if (copy != null) {
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager clips = (android.content.ClipboardManager) host.context()
                        .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (clips != null && report != null) {
                    clips.setPrimaryClip(android.content.ClipData.newPlainText("ibiko-crash", report));
                }
                clearCrash();
                banner.setVisibility(android.view.View.GONE);
            });
        }
        android.view.View dismiss = host.find(ai.ibiko.R.id.btn_dismiss);
        if (dismiss != null) {
            dismiss.setOnClickListener(v -> {
                clearCrash();
                banner.setVisibility(android.view.View.GONE);
            });
        }
    }

    /** A crash from this version, once. An old report, or one already shown, is removed. */
    private String freshCrash() {
        String report = takeCrashReport();
        if (report == null) {
            return null;
        }
        String version = host.version();
        if (!report.startsWith("Ibiko " + version + " ")) {
            clearCrash();
            return null;
        }
        android.content.SharedPreferences prefs = host.prefs();
        String seen = prefs == null ? "" : prefs.getString("crash_seen", "");
        String mark = String.valueOf(report.hashCode());
        if (mark.equals(seen)) {
            clearCrash();
            return null;
        }
        if (prefs != null) {
            prefs.edit().putString("crash_seen", mark).apply();
        }
        return report;
    }

    /** The report the last crash left in files/crash.txt, or null when there is none. */
    private String takeCrashReport() {
        java.io.File f = new java.io.File(host.context().getFilesDir(), "crash.txt");
        if (!f.exists()) {
            return null;
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private void clearCrash() {
        new java.io.File(host.context().getFilesDir(), "crash.txt").delete();
    }
}