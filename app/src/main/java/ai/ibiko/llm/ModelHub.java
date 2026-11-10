package ai.ibiko.llm;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one language model and its download for the whole app process: the activity (which may be closed and
 * reopened) and the background download service share it, and listeners hear about every change on the main thread.
 */
public final class ModelHub {
    private static ModelHub instance;

    public final LocalModel model;
    public final DownloadStatus status = new DownloadStatus();
    volatile boolean cancel;
    volatile boolean running;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ModelHub(Context c) {
        model = new LocalModel(new File(c.getFilesDir(), "models"), chosen(c),
                LocalModel.cpuSupported(android.os.Build.SUPPORTED_ABIS, cpuinfo()));
    }

    static final String PREF = "offline_model";

    /** The offline model the user chose in Settings (the default until they choose). */
    static ModelSpec chosen(Context c) {
        return ModelSpec.byFile(c.getSharedPreferences("dvine", Context.MODE_PRIVATE).getString(PREF, ModelSpec.QWEN3_1_7B.file));
    }

    /** Bumped on every change of model, so a screen that loads it knows to load again. */
    public volatile int generation;

    /**
     * Switches the phone's model: a running download stops, the new choice is kept, and the old file is deleted when
     * {@code deleteOld} (else it stays for switching back). The app loads the new model once it is on the phone.
     */
    public void choose(Context c, ModelSpec next, boolean deleteOld) {
        if (next == null || next.file.equals(chosen().file)) {
            return;
        }
        stopDownload();
        chosen = next; // screens show the new model at once; the switch itself happens on the model's own thread
        c.getSharedPreferences("dvine", Context.MODE_PRIVATE).edit().putString(PREF, next.file).apply();
        status.reset();
        generation++;
        model.cancel(); // a reply in progress stops, so the switch isn't kept waiting
        worker.execute(() -> {
            model.switchTo(next, deleteOld); // after any load queued before it, before any queued after it
            changed();
        });
    }

    /**
     * The one thread the phone's model is loaded, switched and removed on: the model's lock is held for the whole of
     * a load (seconds, or minutes for a big model), so this never runs on the screen's thread, and in order.
     */
    public final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dvine-model");
        t.setDaemon(true);
        return t;
    });

    private volatile ModelSpec chosen;

    /** The model chosen (shown on screens), even while the switch to it is still under way. */
    public ModelSpec chosen() {
        ModelSpec c = chosen;
        return c != null ? c : model.spec;
    }

    public static synchronized ModelHub get(Context c) {
        if (instance == null) {
            instance = new ModelHub(c.getApplicationContext());
        }
        return instance;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    void changed() {
        main.post(() -> {
            for (Runnable r : listeners) {
                r.run();
            }
        });
    }

    public boolean downloading() {
        return running;
    }

    /** Starts (or resumes) the download in the background service; it keeps going when the app is minimised. */
    public void startDownload(Context c) {
        cancel = false;
        Intent i = new Intent(c, ModelDownloadService.class).setAction(ModelDownloadService.ACTION_START);
        c.startForegroundService(i);
    }

    /** Pauses the download; what has arrived is kept for a resume. */
    public void stopDownload() {
        cancel = true;
    }

    private static String cpuinfo() {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/cpuinfo")), "UTF-8");
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
