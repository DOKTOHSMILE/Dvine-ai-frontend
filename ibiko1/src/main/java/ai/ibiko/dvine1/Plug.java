package ai.ibiko.dvine1;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything DVINE 1 needs from the app it runs in, in one place: the web, the workers board, what the pilot is into,
 * and an icon for its notifications. The app fills these in at start ({@code DvineApp}); without them DVINE 1 still
 * runs, offline and quietly. This is what makes the folder detachable: nothing else in it reaches outside.
 */
public final class Plug {
    private Plug() {
    }

    /** The web: a page's text by address, and a search's results for a question (null when out of reach). */
    public interface Web {
        String get(String url);

        String search(String question);
    }

    /** The workers board (who is working, on what, now). */
    public interface Workers {
        String start(String role, String task, String brain);

        void step(String code, String what);

        void end(String code, boolean done);
    }

    /** A file that ships inside the app (its assets), by path; null when the app gave none. */
    public static volatile java.util.function.Function<String, java.io.InputStream> asset = path -> null;

    /** No web: Ibiko 1 answers from what it knows. */
    public static final Web NONE = new Web() {
        @Override
        public String get(String url) {
            return null;
        }

        @Override
        public String search(String question) {
            return null;
        }
    };

    public static volatile Web web = NONE;

    public static volatile Workers workers = new Workers() {
        @Override
        public String start(String role, String task, String brain) {
            return "";
        }

        @Override
        public void step(String code, String what) {
        }

        @Override
        public void end(String code, boolean done) {
        }
    };

    /** What the pilot has said they're into (their work, project, likes), for the learning sessions. */
    public static volatile java.util.function.Supplier<List<String>> interests = ArrayList::new;

    /** The small icon for DVINE 1's notifications. */
    public static volatile int icon = android.R.drawable.stat_notify_sync;
}
