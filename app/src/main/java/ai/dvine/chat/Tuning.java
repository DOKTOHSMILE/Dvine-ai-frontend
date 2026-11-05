package ai.dvine.chat;

import android.content.SharedPreferences;

/**
 * The limits DVINE works within, as settings rather than numbers fixed in the code (Settings › Advanced). Each has a
 * sensible default and a safe range; a value out of range is brought back into it.
 */
public final class Tuning {
    /** One setting: its key, name, explanation, default and range. */
    public static final class Knob {
        public final String key, name, about;
        public final int def, min, max;

        Knob(String key, String name, String about, int def, int min, int max) {
            this.key = key;
            this.name = name;
            this.about = about;
            this.def = def;
            this.min = min;
            this.max = max;
        }

        /**
         * The values a slider stops at: every whole number for a short range (up to 100 apart), otherwise round
         * numbers (1, 1.5, 2, 3, 4, 5, 6, 8 times a power of ten) so a small value is as easy to pick as a big one,
         * with the lowest, the default and the highest always among them.
         */
        public int[] stops() {
            java.util.TreeSet<Integer> out = new java.util.TreeSet<Integer>();
            if (max - min <= 100) {
                for (int v = min; v <= max; v++) {
                    out.add(v);
                }
            } else {
                for (long p = 1; p <= max; p *= 10) {
                    for (double f : new double[]{1, 1.5, 2, 3, 4, 5, 6, 8}) {
                        long v = Math.round(f * p);
                        if (v >= min && v <= max) {
                            out.add((int) v);
                        }
                    }
                }
                out.add(min);
                out.add(def);
                out.add(max);
            }
            int[] a = new int[out.size()];
            int i = 0;
            for (int v : out) {
                a[i++] = v;
            }
            return a;
        }

        /** The stop nearest {@code value}. */
        public int stopFor(int value) {
            int[] st = stops();
            int best = 0;
            for (int i = 1; i < st.length; i++) {
                if (Math.abs(st[i] - value) < Math.abs(st[best] - value)) {
                    best = i;
                }
            }
            return best;
        }

        /** The value as shown beside its slider ("8,192", "Automatic" for a 0 that means automatic). */
        public String shown(int value) {
            return value == 0 && name.contains("0 = automatic") ? "Automatic" : String.format(java.util.Locale.US, "%,d", value);
        }

        public int get(SharedPreferences p) {
            int v = p == null ? def : p.getInt(key, def);
            return Math.max(min, Math.min(max, v));
        }
    }

    public static final Knob CHAT_TOKENS = new Knob("adv_chat_tokens", "Everyday reply length, tokens",
            "How long an ordinary answer may be (API models get at least 1,024).", 160, 64, 8000);
    public static final Knob BIG_TOKENS = new Knob("adv_big_tokens", "Long reply length, tokens",
            "For real work: writing, research, analysis.", 1500, 256, 32000);
    public static final Knob AGENT_STEPS = new Knob("adv_agent_steps", "Agent steps per task",
            "How many tool steps Ibiko (or a persona reading files) may take before reporting back.", 30, 3, 100);
    public static final Knob AGENT_TOKENS = new Knob("adv_agent_tokens", "Agent step length, tokens",
            "How much the model may write in one step (a whole file or a full report needs more).", 4000, 256, 32000);
    public static final Knob PASTE_CHARS = new Knob("adv_paste_chars", "Paste becomes a file at, characters",
            "A longer paste turns into an attachment card.", 1500, 200, 100000);
    public static final Knob IMAGE_SIDE = new Knob("adv_image_side", "Image size sent, pixels",
            "The long side of an attached photo.", 1568, 256, 4096);
    public static final Knob WEB_RESULTS = new Knob("adv_web_results", "Web results per search",
            "How many results a search lists.", 8, 1, 20);
    public static final Knob WEB_PAGES = new Knob("adv_web_pages", "Pages read per search",
            "How many of the results are opened and read.", 3, 0, 10);

    public static final Knob WORKERS = new Knob("adv_workers", "Mini agent workers at once",
            "How many mini agents work side by side on a heavy job (each takes one part and reports when it finishes)."
                    + " The device's own model is taken by one request at a time, so extra workers wait for it between"
                    + " their steps; an API model answers them all at once.", 4, 1, 8);

    public static final Knob CLONE_FILES = new Knob("adv_clone_files", "Most files in a clone",
            "A repository with more files than this isn't cloned onto the device (a bigger number needs more storage and time).",
            5000, 100, 50000);

    public static final Knob OFFLINE_CONTEXT = new Knob("adv_offline_context", "Offline model memory, tokens",
            "How much of the conversation and the work the device's own model keeps in mind. More lets Ibiko work offline and"
                    + " remember longer chats, but takes more of the device's memory and starts slower.", 8192, 1024, 32768);
    public static final Knob SLOW_SPEED = new Knob("adv_slow_speed", "Offline model is slow below, tokens a second",
            "When the device's own model writes slower than this, DVINE suggests a smaller size or less memory.", 4, 1, 50);

    public static final Knob MODEL_THREADS = new Knob("adv_model_threads", "Offline model threads (0 = automatic)",
            "How many of the device's cores the offline model uses. Automatic takes the fast cores only (more threads on slow"
                    + " cores makes it slower). Compare with Settings \u203A Test the speed.", 0, 0, 8);

    public static final Knob[] ALL = {CHAT_TOKENS, BIG_TOKENS, AGENT_STEPS, AGENT_TOKENS, PASTE_CHARS, IMAGE_SIDE, WEB_RESULTS, WEB_PAGES,
            WORKERS, CLONE_FILES, OFFLINE_CONTEXT, SLOW_SPEED, MODEL_THREADS};

    /** The threads for the phone's own model: the setting, or the fast cores when it is automatic. */
    public static int modelThreads(android.content.SharedPreferences p) {
        int set = MODEL_THREADS.get(p);
        return set > 0 ? set : ai.dvine.llm.LocalModel.threads(Runtime.getRuntime().availableProcessors());
    }

    private Tuning() {
    }
}
