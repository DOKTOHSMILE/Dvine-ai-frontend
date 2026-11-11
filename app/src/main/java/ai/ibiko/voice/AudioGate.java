package ai.ibiko.voice;

/**
 * One sound at a time across the whole app: whatever starts to speak or play takes the gate, and what had it is
 * silenced first (a reply being read, the voice tried in Settings, a video or a recording played in the chat, the
 * microphone opening). Nothing Ibiko plays ever overlaps. Pure Java.
 */
public final class AudioGate {
    private AudioGate() {
    }

    /** Something that makes sound and can be silenced. */
    public interface Owner {
        void silence();
    }

    private static Owner now;

    /** {@code o} starts making sound: the one before it is silenced. */
    public static void take(Owner o) {
        Owner before;
        synchronized (AudioGate.class) {
            before = now;
            now = o;
        }
        if (before != null && before != o) {
            before.silence();
        }
    }

    /** {@code o} has finished: the gate is free. */
    public static synchronized void leave(Owner o) {
        if (now == o) {
            now = null;
        }
    }

    /** Silences whatever is playing (the microphone opens, the app closes). */
    public static void silenceAll() {
        take(null);
    }

    public static synchronized Owner holder() {
        return now;
    }
}
