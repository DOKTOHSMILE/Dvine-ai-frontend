package ai.dvine.llm;

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
        model = new LocalModel(new File(c.getFilesDir(), "models"), ModelSpec.QWEN3_1_7B,
                LocalModel.cpuSupported(android.os.Build.SUPPORTED_ABIS, cpuinfo()));
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
