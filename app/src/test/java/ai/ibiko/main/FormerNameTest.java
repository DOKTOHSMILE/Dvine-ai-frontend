package ai.ibiko.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** An install from before the rename keeps its settings and its clones' state under Ibiko's own names. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class FormerNameTest {
    @Test
    public void settingsAndRecordsMoveOverOnce() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        SharedPreferences old = c.getSharedPreferences(FormerName.OLD, Context.MODE_PRIVATE);
        old.edit().putString("voice_pack", "piper").putBoolean("fact_check", true).putInt("adv_workers", 6).commit();
        c.getSharedPreferences("ibiko", Context.MODE_PRIVATE).edit().putInt("adv_workers", 3).commit();
        File repo = new File(c.getFilesDir(), "ibiko/chats/c1/repos/app");
        repo.mkdirs();
        new File(repo, "." + FormerName.OLD + "-git.json").createNewFile();
        FormerName.prefs(c, FormerName.OLD, "ibiko");
        FormerName.records(new File(c.getFilesDir(), "ibiko/chats"), 0);
        SharedPreferences now = c.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
        assertEquals("piper", now.getString("voice_pack", null));
        assertTrue(now.getBoolean("fact_check", false));
        assertEquals("a setting made under the new name wins", 3, now.getInt("adv_workers", 0));
        assertTrue(c.getSharedPreferences(FormerName.OLD, Context.MODE_PRIVATE).getAll().isEmpty());
        assertTrue(new File(repo, ai.ibiko.connect.GitHubGit.RECORD).isFile());
        assertFalse(new File(repo, "." + FormerName.OLD + "-git.json").exists());
    }
}
