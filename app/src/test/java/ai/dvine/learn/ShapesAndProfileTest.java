package ai.dvine.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** The brain box learns shapes (a learned action works for new words), and the pilot profile. */
public class ShapesAndProfileTest {
    @Test
    public void aLearnedShapeWorksForNewWords() throws Exception {
        File f = new File(Files.createTempDirectory("bb").toFile(), "box.tsv");
        BrainBox b = new BrainBox(f);
        b.learn("What's the weather in Lagos?", "§SR weather in Lagos");
        assertEquals("§SR weather in Abuja", b.recall("what's the weather in Abuja"));
        assertEquals("kept on the device", "§SR weather in Port Harcourt", new BrainBox(f).recall("What's the weather in Port Harcourt?"));
        assertNull("another shape", b.recall("tell me about Abuja"));

        b.learn("show me a photo of a lion", "§PH a lion");
        assertEquals("§PH a red panda", b.recall("show me a photo of a red panda"));

        b.learn("run ls -la in the shell", "§SH ls -la");
        assertNull("a shell command never comes from a shape", b.recall("run rm -rf / in the shell"));

        assertTrue(b.forget("what's the weather in Kano"));
        assertNull("the shape is forgotten with its mistake", b.recall("what's the weather in Abuja"));
    }

    @Test
    public void thePilotProfileKeepsWhatThePilotSaysAboutThemselves() throws Exception {
        File f = new File(Files.createTempDirectory("pp").toFile(), "pilot.tsv");
        PilotProfile p = new PilotProfile(f);
        assertTrue(p.notice("I live in Lagos and I work at Andela."));
        assertEquals("Lagos", p.get("home"));
        assertEquals("Andela", p.get("works at"));
        p.notice("I'm a software developer, and my sister Ada is visiting.");
        assertEquals("software developer", p.get("works as"));
        assertEquals("Ada", p.get("sister"));
        p.notice("I'm building DVINE, an AI app.");
        assertEquals("DVINE", p.get("building"));
        assertFalse("a question teaches nothing", p.notice("Do I live in Abuja?"));
        assertFalse("nor a negation", p.notice("I don't live in Abuja."));
        assertTrue(p.notice("I live in Abuja now."));
        assertEquals("the newer statement wins", "Abuja", new PilotProfile(f).get("home"));
        assertTrue(p.brief().contains("home: Abuja"));
        assertTrue(p.forget("sister"));
        assertFalse(p.brief().contains("Ada"));
        p.clear();
        assertEquals("", p.brief());
    }
}
