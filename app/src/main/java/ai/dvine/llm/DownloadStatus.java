package ai.dvine.llm;

import java.util.Locale;

/**
 * Where the model download is: its phase, bytes so far, a smoothed speed and the time left, and the same facts
 * in words for the screen, the notification and speech. Thread-safe; pure Java.
 */
public final class DownloadStatus {
    public enum Phase { IDLE, CONNECTING, DOWNLOADING, DONE, PAUSED, FAILED, BAD_FILE }

    private Phase phase = Phase.IDLE;
    private long done, total, markTime, markDone;
    private double speed; // bytes per second, smoothed

    public synchronized void begin(long total, long already, long nowMs) {
        this.total = total;
        this.done = already;
        markTime = nowMs;
        markDone = already;
        speed = 0;
        phase = Phase.CONNECTING;
    }

    public synchronized void update(long done, long nowMs) {
        this.done = done;
        phase = Phase.DOWNLOADING;
        long dt = nowMs - markTime;
        if (dt >= 500) {
            double now = (done - markDone) * 1000.0 / dt;
            speed = speed <= 0 ? now : speed * 0.7 + now * 0.3;
            markTime = nowMs;
            markDone = done;
        }
    }

    public synchronized void finish(Phase result) {
        phase = result;
        if (result == Phase.DONE) {
            done = total;
        }
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized boolean active() {
        return phase == Phase.CONNECTING || phase == Phase.DOWNLOADING;
    }

    public synchronized int percent() {
        return total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
    }

    public synchronized long etaSeconds() {
        return speed <= 1 || total <= done ? -1 : (long) ((total - done) / speed);
    }

    /** "540 MB of 1.1 GB". */
    public synchronized String sizeText() {
        return mb(done) + " of " + String.format(Locale.US, "%.1f GB", total / 1e9);
    }

    /** "540 MB of 1.1 GB · 3.2 MB/s · 3 min left" (or the phase when it isn't downloading). */
    public synchronized String detail() {
        switch (phase) {
            case CONNECTING:
                return done > 0 ? "resuming at " + mb(done) + "…" : "connecting…";
            case DONE:
                return "downloaded and verified";
            case PAUSED:
                return "paused at " + sizeText() + "; say continue the download";
            case FAILED:
                return "stopped at " + sizeText() + "; it resumes from there";
            case BAD_FILE:
                return "the file didn't match its checksum and was deleted";
            case IDLE:
                return "";
            default:
                StringBuilder s = new StringBuilder(sizeText());
                if (speed > 1) {
                    s.append(" · ").append(String.format(Locale.US, "%.1f MB/s", speed / 1e6));
                }
                long eta = etaSeconds();
                if (eta >= 0) {
                    s.append(" · ").append(etaShort(eta)).append(" left");
                }
                return s.toString();
        }
    }

    /** What the persona says when asked how far it is. */
    public synchronized String spoken() {
        switch (phase) {
            case CONNECTING:
                return "It's just starting.";
            case DOWNLOADING: {
                long eta = etaSeconds();
                return "It's " + percent() + " percent done" + (eta >= 0 ? ", about " + etaWords(eta) + " to go." : ".");
            }
            case DONE:
                return "The download is finished and verified.";
            case PAUSED:
                return "It's paused at " + percent() + " percent. Say continue the download to carry on.";
            case FAILED:
                return "It stopped at " + percent() + " percent. Say continue the download to carry on from there.";
            case BAD_FILE:
                return "The last download was damaged, so I deleted it. Say download your brain to try again.";
            default:
                return "There's no download running.";
        }
    }

    static String mb(long bytes) {
        return bytes >= 1e9 ? String.format(Locale.US, "%.2f GB", bytes / 1e9) : String.format(Locale.US, "%d MB", Math.round(bytes / 1e6));
    }

    static String etaShort(long s) {
        return s < 60 ? s + " s" : s < 3600 ? (s + 30) / 60 + " min" : String.format(Locale.US, "%.1f h", s / 3600.0);
    }

    static String etaWords(long s) {
        if (s < 60) {
            return "less than a minute";
        }
        long m = (s + 30) / 60;
        if (m < 60) {
            return m == 1 ? "a minute" : m + " minutes";
        }
        long h = m / 60, r = m % 60;
        return (h == 1 ? "an hour" : h + " hours") + (r >= 5 ? " and " + r + " minutes" : "");
    }
}
