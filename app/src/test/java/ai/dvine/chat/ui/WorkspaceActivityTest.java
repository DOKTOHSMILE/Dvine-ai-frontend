package ai.dvine.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The drawer's Workspace: Ibiko's folders open, a text file shows in the code viewer, Back goes up. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WorkspaceActivityTest {
    @Test
    public void foldersOpenAndFilesShow() throws Exception {
        File ws = new File(RuntimeEnvironment.getApplication().getFilesDir(), "ibiko/harvana/app");
        ws.mkdirs();
        Files.write(new File(ws, "Main.java").toPath(), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        new File(ws.getParentFile(), ".git").mkdirs();
        ActivityController<WorkspaceActivity> c = Robolectric.buildActivity(WorkspaceActivity.class).create().start().resume().visible();
        View root = c.get().getWindow().getDecorView();
        tap(root, "harvana");
        assertNull("git's own folder isn't listed", text(c.get().getWindow().getDecorView(), ".git"));
        tap(c.get().getWindow().getDecorView(), "app");
        tap(c.get().getWindow().getDecorView(), "Main.java");
        Intent shown = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertNotNull(shown);
        assertEquals("class Main {}\n", shown.getStringExtra(CodeViewActivity.EXTRA_CODE));
        assertEquals("java", shown.getStringExtra(CodeViewActivity.EXTRA_LANG));
        c.get().onBackPressed(); // up to harvana
        assertNotNull(text(c.get().getWindow().getDecorView(), "app"));
        c.pause().stop().destroy();
    }

    private static void tap(View root, String name) {
        View t = text(root, name);
        assertNotNull(name, t);
        while (!t.hasOnClickListeners()) {
            t = (View) t.getParent();
        }
        t.performClick();
    }

    private static View text(View v, String s) {
        if (v instanceof TextView && s.contentEquals(((TextView) v).getText())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View f = text(((ViewGroup) v).getChildAt(i), s);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }
}
