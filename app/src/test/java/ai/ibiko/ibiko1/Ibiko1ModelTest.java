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

    /** Ibiko 1 and the device's model as one brain: memory first, the model for the rest, what it knew passed in. */
    @Test
    public void whatItDoesNotKnowItAsksTheDevicesModel() throws Exception {
        File data = Files.createTempDirectory("ibiko1").toFile();
        Plug.asset = path -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + path));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        final java.util.List<String> asked = new java.util.ArrayList<String>();
        Ibiko1Model brain = new Ibiko1Model(RuntimeEnvironment.getApplication(), data).withModel((system, q) -> {
            asked.add(q);
            return q.contains("zorblax") ? "A zorblax is a word made up for this test." : "I couldn't reach the network.";
        });
        try {
            Ibiko1Model.Reply known = brain.reply("What is the capital of Ghana?", 15000);
            assertTrue(known.text, known.text.contains("Accra"));
            assertTrue("memory answers first: the model isn't asked", asked.isEmpty());
            Ibiko1Model.Reply made = brain.reply("What is a zorblax?", 15000);
            assertNotNull(made.text);
            assertTrue(made.text, made.text.contains("made up for this test") && made.text.contains("From the device's model"));
            assertTrue(asked.get(0), asked.get(0).endsWith("Question: What is a zorblax?"));
            Ibiko1Model.Reply failed = brain.reply("What is a quuxleplat?", 15000);
            assertTrue("a failure is never passed off as an answer: " + failed.text, !failed.text.contains("couldn't reach"));
        } finally {
            brain.close();
        }
    }
}
