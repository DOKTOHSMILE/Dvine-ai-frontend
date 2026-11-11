package ai.ibiko.linux;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the terminal prints and recognises a download in it, whoever typed the command (the pilot or Ibiko):
 * apk fetching and installing packages, wget and curl, git cloning or pulling, pip and npm installing. It reports a
 * title, the latest line and a percent when the program prints one, to {@link ai.ibiko.net.Transfers}; when the
 * program has printed nothing like a download for a few seconds, the download is reported finished. Pure Java.
 */
public final class DownloadWatch {
    static final long QUIET_MS = 4000;
    static final String KEY = "terminal";
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]|\u001B\\][^\u0007]*\u0007");
    private static final Pattern PERCENT = Pattern.compile("(?<![\\d.])(\\d{1,3})(?:\\.\\d+)?\\s?%");
    private static final Pattern APK_STEP = Pattern.compile("^\\((\\d+)/(\\d+)\\)\\s+(Installing|Upgrading|Purging)\\s+(\\S+)");
    private static final Pattern CURL_ROW = Pattern.compile("^\\s*(\\d{1,3})\\s+[\\d.]+[kMG]?\\s+(\\d{1,3})\\s+[\\d.]+[kMG]?\\s+");
    private static final Pattern PIP_BAR = Pattern.compile("([\\d.]+)/([\\d.]+)\\s*(kB|MB|GB)");

    private final StringBuilder line = new StringBuilder();
    private String title;
    private String last = "";
    private int percent = -1;
    private long lastSeen;

    /** What the terminal printed; {@code now} in milliseconds. */
    public synchronized void feed(String text, long now) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                look(line.toString(), now);
                line.setLength(0);
            } else if (line.length() < 400) {
                line.append(c);
            }
        }
        if (line.length() > 0) {
            look(line.toString(), now); // a progress bar redrawn in place, with no line end yet
        }
    }

    /** Called now and then: a download the terminal stopped talking about is over. True when it just ended. */
    public synchronized boolean tick(long now) {
        if (title != null && now - lastSeen > QUIET_MS) {
            ai.ibiko.net.Transfers.end(KEY, title, done(), true);
            title = null;
            percent = -1;
            return true;
        }
        return false;
    }

    /** The download under way, or null. */
    public synchronized String current() {
        return title;
    }

    private String done() {
        return last.isEmpty() ? "Finished" : "Finished: " + last;
    }

    private void look(String raw, long now) {
        String s = ANSI.matcher(raw).replaceAll("").trim();
        if (s.isEmpty()) {
            return;
        }
        String low = s.toLowerCase(Locale.US);
        String name = null;
        int pct = -1;
        Matcher apk = APK_STEP.matcher(s);
        Matcher curl = CURL_ROW.matcher(s);
        if (apk.find()) {
            name = "Installing packages";
            pct = Math.round(100f * Integer.parseInt(apk.group(1)) / Math.max(1, Integer.parseInt(apk.group(2))));
            s = apk.group(3) + " " + apk.group(4);
        } else if (low.startsWith("fetch http")) {
            name = "Updating the package list";
        } else if (low.startsWith("cloning into")) {
            name = "Cloning " + s.replaceAll("(?i)^cloning into\\s+'?([^'.]+)'?.*$", "$1");
        } else if (low.startsWith("receiving objects") || low.startsWith("resolving deltas") || low.startsWith("remote: counting")
                || low.startsWith("remote: compressing") || low.startsWith("updating files")) {
            name = title != null && title.startsWith("Cloning") ? title : "Fetching with git";
        } else if (low.startsWith("collecting ") || low.startsWith("downloading ") && (low.contains(".whl") || low.contains(".tar.gz"))) {
            name = "Installing Python packages";
        } else if (low.startsWith("connecting to") || low.startsWith("saving to") || low.startsWith("--20") && low.contains("http")) {
            name = "Downloading " + fileOf(s);
        } else if (low.contains("% total") && low.contains("received")) {
            name = "Downloading";
        } else if (curl.find() && title != null) {
            name = title;
            pct = Integer.parseInt(curl.group(2));
        } else if (low.startsWith("npm http fetch") || low.contains("idealtree") || low.contains("reify:")) {
            name = "Installing npm packages";
        } else if (title != null && (low.contains("eta") || low.contains("/s") || PIP_BAR.matcher(s).find() || PERCENT.matcher(s).find())) {
            name = title; // more of the same download
        }
        if (name == null) {
            return;
        }
        if (pct < 0) {
            Matcher m = PERCENT.matcher(s);
            int found = -1;
            while (m.find()) {
                found = Integer.parseInt(m.group(1));
            }
            if (found >= 0 && found <= 100) {
                pct = found;
            } else {
                Matcher bar = PIP_BAR.matcher(s);
                if (bar.find()) {
                    double a = Double.parseDouble(bar.group(1)), b = Double.parseDouble(bar.group(2));
                    pct = b > 0 ? (int) Math.round(100 * a / b) : -1;
                }
            }
        }
        if (title == null || !title.equals(name) && !(name.startsWith("Fetching") && title.startsWith("Cloning"))) {
            title = name;
            percent = -1;
        }
        if (pct >= 0) {
            percent = Math.min(100, pct);
        }
        last = s.length() > 90 ? s.substring(0, 90) + "…" : s;
        lastSeen = now;
        ai.ibiko.net.Transfers.progress(KEY, title, last, percent);
    }

    private static String fileOf(String s) {
        Matcher m = Pattern.compile("https?://\\S*?/([^/\\s'\"]+?)(?:[?'\"]|\\s|$)").matcher(s);
        if (m.find() && !m.group(1).isEmpty()) {
            return m.group(1);
        }
        Matcher saving = Pattern.compile("(?i)saving to:?\\s*[‘'\"]?([^’'\"]+)").matcher(s);
        return saving.find() ? saving.group(1).replaceAll(".*/", "") : "a file";
    }
}
