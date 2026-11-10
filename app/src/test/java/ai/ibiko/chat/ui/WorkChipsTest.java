package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import ai.ibiko.chat.WorkLog;

/** A reply's own work: kept with the chat and read back whole; a reply that produced nothing keeps nothing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WorkChipsTest {
    @Test
    public void aRunIsWrittenAndReadBackWhole() {
        WorkChips.Run r = new WorkChips.Run();
        r.changes.add(new WorkChips.Change("app/Main.java", "modified", 2, 1, Arrays.asList("@@ 1", "-old", "+new", "+more")));
        r.pullRequests.add(new WorkLog.Entry("#7 Say hello", "https://github.com/me/app/pull/7", "me/app", "", 5));
        r.artifacts.add(new WorkLog.Entry("car.glb", "content://x/car.glb", "3D model", "model/gltf-binary", 6));
        WorkChips.Run back = WorkChips.read(WorkChips.write(r));
        assertEquals(1, back.changes.size());
        assertEquals("app/Main.java", back.changes.get(0).path);
        assertEquals(2, back.changes.get(0).added);
        assertEquals(1, back.changes.get(0).removed);
        assertEquals(Arrays.asList("@@ 1", "-old", "+new", "+more"), back.changes.get(0).lines);
        assertEquals("#7 Say hello", back.pullRequests.get(0).title);
        assertEquals("model/gltf-binary", back.artifacts.get(0).mime);
        assertEquals(6, back.artifacts.get(0).at);
    }

    @Test
    public void onlyWhatThisReplyLoggedIsKept() {
        Context c = RuntimeEnvironment.getApplication();
        WorkLog log = new WorkLog(c);
        log.clear();
        log.pullRequest("1", "Older", "https://github.com/me/app/pull/1", "me/app");
        long since = System.currentTimeMillis() + 1;
        File ws = new File(c.getFilesDir(), "ibiko");
        assertNull(WorkChips.capture(c, ws, Collections.<String>emptyList(), since)); // nothing new: no pills
        WorkChips.Run none = WorkChips.load(c, "missing-id");
        assertTrue(none.isEmpty());
        String id = WorkChips.capture(c, ws, Collections.<String>emptyList(), 0);
        assertEquals(1, WorkChips.load(c, id).pullRequests.size());
        log.clear();
    }
}
