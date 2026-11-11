package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Ibiko 1 used like any other model, through its entry point: a message in, a reply out, no screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class Ibiko1ModelTest {
    @Test
    public void aMessageInAReplyOut() throws Exception {
        File data = Files.createTempDirectory("ibiko1").toFile();
        Plug.asset = path -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + path));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Ibiko1Model brain = new Ibiko1Model(RuntimeEnvironment.getApplication(), data);
        try {
            Ibiko1Model.Reply hi = brain.reply("Hello", 10000);
            assertNotNull(hi.text);
            assertTrue(hi.text, hi.text.contains("it's Ibiko"));
            Ibiko1Model.Reply capital = brain.reply("What is the capital of Ghana?", 15000);
            assertNotNull(capital.text);
            assertTrue(capital.text, capital.text.contains("Accra"));
            assertTrue("it shows how it worked", !capital.steps.isEmpty());
            assertNull(capital.code);
            assertEquals("ibiko-1-0.1", Ibiko1Model.ID);
        } finally {
            brain.close();
        }
    }
}
