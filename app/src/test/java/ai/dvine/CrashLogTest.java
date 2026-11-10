package ai.dvine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ApplicationExitInfo;
import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CrashLogTest {
    @Test
    public void aCrashIsCaughtAndItsReportKeptWithTheVersionAndTheCause() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        final boolean[] passedOn = {false};
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> passedOn[0] = true); // stands in for Android's own
            java.lang.reflect.Field f = CrashLog.class.getDeclaredField("installed");
            f.setAccessible(true);
            f.set(null, false);
            CrashLog.install(c);
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),
                    new IllegalStateException("outer", new NullPointerException("the real cause")));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
        assertTrue("Android still closes the app as usual", passedOn[0]);
        String kept = new String(Files.readAllBytes(new File(c.getFilesDir(), "crash.txt").toPath()), StandardCharsets.UTF_8);
        assertTrue(kept, kept.startsWith("Ibiko " + BuildConfigInfo.version(c) + " on Android"));
        assertTrue(kept.contains("Crash on thread"));
        assertTrue(kept.contains("IllegalStateException: outer"));
        assertTrue("the cause is in it", kept.contains("Caused by: java.lang.NullPointerException: the real cause"));
    }

    @Test
    public void whatAndroidRecordsIsReadable() {
        // a tombstone is binary: its readable runs are the signal, the libraries and the functions
        byte[] tomb = "\u0001\u0002SIGSEGV\u0000\u0003libdvine_llm.so\u0000ggml_compute\u0005ab\u0000".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("SIGSEGV\nlibdvine_llm.so\nggml_compute\n", CrashLog.readable(tomb));
        assertTrue(CrashLog.worthKeeping(ApplicationExitInfo.REASON_CRASH_NATIVE));
        assertTrue(CrashLog.worthKeeping(ApplicationExitInfo.REASON_ANR));
        assertFalse("the user closing the app is not a crash", CrashLog.worthKeeping(ApplicationExitInfo.REASON_USER_REQUESTED));
        assertTrue(CrashLog.reasonName(ApplicationExitInfo.REASON_CRASH_NATIVE).startsWith("Native crash"));
        assertTrue(CrashLog.stamp(0).matches("\\d{8}-\\d{6}"));
    }
}
