package ai.ibiko.llm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps a model that is too strong for the phone from stopping the app. Three guards:
 * <ul>
 *   <li>before loading, the model's memory need is checked against the phone's memory ({@link #fits});</li>
 *   <li>a marker is written while a model loads and cleared when the load returns; a marker found at the next start
 *       means the phone stopped the app while loading that model (out of memory), so it is not loaded again
 *       ({@link #crashedWhileLoading});</li>
 *   <li>a model that fails to load for lack of memory is set aside too.</li>
 * </ul>
 * In each case Ibiko falls back to the strongest model already on the phone that does fit ({@link #fallback}), and
 * says so. Choosing the model again in Settings clears its mark: the user may know better (another app was open).
 */
public final class ModelGuard {
    static final String LOADING = "model_loading", TOO_STRONG = "models_too_strong";
    private final SharedPreferences prefs;

    public ModelGuard(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Writes the marker, synchronously: it must be on disk before the load that might stop the app. */
    public void loading(String file) {
        prefs.edit().putString(LOADING, file).commit();
    }

    /** The load returned (well or not): the app was not stopped by it. */
    public void loaded() {
        prefs.edit().remove(LOADING).commit();
    }

    /**
     * At start-up: the model whose load never returned last time (the phone stopped the app while loading it), now
     * marked too strong; null when the last load returned.
     */
    public String crashedWhileLoading() {
        String f = prefs.getString(LOADING, null);
        if (f != null) {
            markTooStrong(f);
            prefs.edit().remove(LOADING).commit();
        }
        return f;
    }

    public boolean tooStrong(String file) {
        return prefs.getStringSet(TOO_STRONG, new HashSet<String>()).contains(file);
    }

    public void markTooStrong(String file) {
        Set<String> s = new HashSet<String>(prefs.getStringSet(TOO_STRONG, new HashSet<String>()));
        s.add(file);
        prefs.edit().putStringSet(TOO_STRONG, s).commit();
    }

    /** The user chose this model on purpose: it is tried again. */
    public void clear(String file) {
        Set<String> s = new HashSet<String>(prefs.getStringSet(TOO_STRONG, new HashSet<String>()));
        if (s.remove(file)) {
            prefs.edit().putStringSet(TOO_STRONG, s).commit();
        }
    }

    /** Whether the model's memory need is within the phone's ({@code ramGb} 0 or less: unknown, so yes). */
    public static boolean fits(ModelSpec m, long ramGb) {
        return ramGb <= 0 || m.ramGb <= ramGb;
    }

    /**
     * The model to use instead: the strongest one already on the phone (the biggest file) that fits its memory and
     * hasn't been set aside; null when there is none.
     */
    public ModelSpec fallback(LocalModel model, long ramGb, String instead) {
        ModelSpec best = null;
        for (ModelSpec m : ModelSpec.ALL) {
            if (m.file.equals(instead) || tooStrong(m.file) || !fits(m, ramGb) || !model.has(m)) {
                continue;
            }
            if (best == null || m.bytes > best.bytes) {
                best = m;
            }
        }
        return best;
    }

    /** Why the model can't be used here, in words, or null when it can. */
    public String why(ModelSpec m, long ramGb) {
        if (tooStrong(m.file)) {
            return "it stopped the app while starting";
        }
        if (!fits(m, ramGb)) {
            return "it wants " + m.ramGb + " GB of memory and this device has " + ramGb + " GB";
        }
        return null;
    }

    /** The phone's memory in whole gigabytes (0 when unknown). */
    public static long ramGb(Context c) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return 0;
            }
            android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return Math.round(info.totalMem / 1e9);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
