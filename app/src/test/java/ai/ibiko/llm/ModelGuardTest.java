package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A model too strong for the phone never stops the app: it is set aside and the strongest one that fits is used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ModelGuardTest {
    @Test
    public void aModelThatStoppedTheAppIsSetAsideAndAnotherTakesOver() throws Exception {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("guard-test", 0);
        prefs.edit().clear().commit();
        ModelGuard guard = new ModelGuard(prefs);
        File dir = Files.createTempDirectory("models").toFile();
        LocalModel model = new LocalModel(dir, ModelSpec.QWEN3_8B, true);
        for (ModelSpec m : new ModelSpec[]{ModelSpec.QWEN3_1_7B, ModelSpec.GEMMA_3_1B, ModelSpec.QWEN3_8B}) {
            java.io.RandomAccessFile f = new java.io.RandomAccessFile(new File(dir, m.file), "rw");
            f.setLength(m.bytes); // on the phone, whole (sparse: no real disk used)
            f.close();
        }
        // memory: Qwen3 8B wants 10 GB, so an 8 GB phone gets the strongest model on it that fits
        assertFalse(ModelGuard.fits(ModelSpec.QWEN3_8B, 8));
        assertTrue(ModelGuard.fits(ModelSpec.QWEN3_8B, 0)); // unknown memory: not judged
        assertEquals("it wants 10 GB of memory and this device has 8 GB", guard.why(ModelSpec.QWEN3_8B, 8));
        assertSame(ModelSpec.QWEN3_1_7B, guard.fallback(model, 8, ModelSpec.QWEN3_8B.file));
        // a load that never returned: the next start finds the marker, and that model is set aside
        guard.loading(ModelSpec.QWEN3_1_7B.file);
        assertEquals(ModelSpec.QWEN3_1_7B.file, new ModelGuard(prefs).crashedWhileLoading());
        assertNull(guard.crashedWhileLoading()); // said once
        assertEquals("it stopped the app while starting", guard.why(ModelSpec.QWEN3_1_7B, 8));
        assertSame(ModelSpec.GEMMA_3_1B, guard.fallback(model, 8, ModelSpec.QWEN3_8B.file)); // the next that fits
        // a load that returned leaves no mark
        guard.loading(ModelSpec.GEMMA_3_1B.file);
        guard.loaded();
        assertNull(guard.crashedWhileLoading());
        // the user choosing it again on purpose clears the mark
        guard.clear(ModelSpec.QWEN3_1_7B.file);
        assertNull(guard.why(ModelSpec.QWEN3_1_7B, 8));
        // nothing that fits on the phone: none
        guard.markTooStrong(ModelSpec.QWEN3_1_7B.file);
        guard.markTooStrong(ModelSpec.GEMMA_3_1B.file);
        assertNull(guard.fallback(model, 8, ModelSpec.QWEN3_8B.file));
    }

    @Test
    public void theMemorySettingNeverWaitsForALoadingModel() throws Exception {
        LocalModel m = new LocalModel(Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_4B, true);
        final java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1), done = new java.util.concurrent.CountDownLatch(1);
        Thread loader = new Thread(() -> {
            synchronized (m) { // as load() does for the whole of a long load
                holding.countDown();
                try {
                    done.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
        });
        loader.start();
        holding.await();
        long t0 = System.currentTimeMillis();
        m.context(4096); // what onResume does on the main thread: it must not wait for the lock
        assertTrue(System.currentTimeMillis() - t0 < 500);
        assertEquals(4096, m.localWindow());
        done.countDown();
        loader.join();
    }
}
