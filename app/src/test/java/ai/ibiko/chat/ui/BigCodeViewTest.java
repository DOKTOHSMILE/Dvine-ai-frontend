package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** A large code file opens without crashing: by path, read off the main thread, shown as a list of lines. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BigCodeViewTest {
    static String big(int lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            b.append("    public void method").append(i).append("() { return; } // a line of code\n");
        }
        return b.toString();
    }

    @Test
    public void linesAreNumberedAndCut() {
        BigText.Lines l = BigText.of("a\nb\n");
        assertEquals(3, l.lines.size());
        assertEquals("  9  x", BigText.shown(8, 120, "x"));
        String huge = "y".repeat(BigText.LINE_SHOWN + 50);
        assertTrue(BigText.shown(0, 1, huge).endsWith("(50 more characters)"));
    }

    @Test
    public void binaryAndTooBigAreRefused() throws Exception {
        File bin = File.createTempFile("bin", ".dat");
        Files.write(bin.toPath(), new byte[] {1, 2, 0, 3});
        assertEquals("Not a text file.", BigText.read(bin).refused);
        File text = File.createTempFile("code", ".java");
        Files.write(text.toPath(), big(10).getBytes(StandardCharsets.UTF_8));
        assertNull(BigText.read(text).refused);
    }

    @Test
    public void aTwoMegabyteFileOpensAsAList() throws Exception {
        File f = new File(RuntimeEnvironment.getApplication().getCacheDir(), "Big.java");
        String code = big(30_000);
        Files.write(f.toPath(), code.getBytes(StandardCharsets.UTF_8));
        assertTrue("about 2 MB", f.length() > 1_500_000);
        Intent i = new Intent(RuntimeEnvironment.getApplication(), CodeViewActivity.class)
                .putExtra(CodeViewActivity.EXTRA_FILE, f.getPath()).putExtra(CodeViewActivity.EXTRA_LANG, "java");
        ActivityController<CodeViewActivity> c = Robolectric.buildActivity(CodeViewActivity.class, i).setup();
        CodeViewActivity a = c.get();
        ListView list = a.findViewById(ai.ibiko.R.id.code_lines);
        long until = System.currentTimeMillis() + 20000;
        while (list.getVisibility() != View.VISIBLE && System.currentTimeMillis() < until) {
            Thread.sleep(50);
            Shadows.shadowOf(a.getMainLooper()).idle();
        }
        assertEquals("shown as a list that draws only what is on screen", View.VISIBLE, list.getVisibility());
        assertEquals(30_000, list.getAdapter().getCount());
        TextView first = (TextView) list.getAdapter().getView(0, null, list);
        assertTrue(first.getText().toString(), first.getText().toString().contains("method0"));
        c.destroy();
    }

    @Test
    public void bigCodeFromTheChatTravelsAsAFile() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        CodeViewActivity.open(app, big(5000), "java");
        Intent sent = Shadows.shadowOf(app).getNextStartedActivity();
        assertNotNull(sent);
        assertNull("not in the Intent: that is what crashed", sent.getStringExtra(CodeViewActivity.EXTRA_CODE));
        assertTrue(new File(sent.getStringExtra(CodeViewActivity.EXTRA_FILE)).isFile());
        CodeViewActivity.open(app, "print(1)", "py");
        assertEquals("print(1)", Shadows.shadowOf(app).getNextStartedActivity().getStringExtra(CodeViewActivity.EXTRA_CODE));
    }
}
