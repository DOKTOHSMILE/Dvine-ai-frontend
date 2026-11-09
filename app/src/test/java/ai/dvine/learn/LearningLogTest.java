package ai.dvine.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.dvine.brain.MiniJson;

/** What DVINE keeps from the API models, the examples it gives its own model, and the training export. */
public class LearningLogTest {
    private static List<String[]> ask(String q) {
        List<String[]> t = new ArrayList<String[]>();
        t.add(new String[]{"user", "Hi"});
        t.add(new String[]{"assistant", "Hello!"});
        t.add(new String[]{"user", "Some notes\n\nThe user says: " + q});
        return t;
    }

    @Test
    public void recordsAndFindsTheClosestAnswer() throws Exception {
        File dir = Files.createTempDirectory("learn").toFile();
        LearningLog log = new LearningLog(dir);
        log.record("You are Ivie. /no_think", ask("How do I boil an egg perfectly?"), "Put it in boiling water for 9 minutes.", "Anthropic (x)", "FAST", 1);
        log.record("sys", ask("What is the capital of Nigeria?"), "Abuja.", "OpenAI (y)", "FAST", 2);
        log.record("sys", ask("open the camera"), "DO PHOTO", "OpenAI (y)", "FAST", 3);
        log.record("sys", ask("nothing"), "  ", "OpenAI (y)", "FAST", 4); // empty replies aren't kept
        assertEquals(3, log.count());

        String ex = log.examplesFor("how long to boil an egg", 1, 700);
        assertTrue(ex, ex.contains("boil an egg perfectly") && ex.contains("9 minutes"));
        assertTrue(ex, !ex.contains("Abuja"));
        assertEquals("", log.examplesFor("open the camera now", 2, 1400)); // orders aren't imitated
        assertEquals("", log.examplesFor("quantum chromodynamics lattice", 2, 1400));
    }

    @Test
    public void exportsChatFormatForFineTuning() throws Exception {
        File dir = Files.createTempDirectory("learn").toFile();
        LearningLog log = new LearningLog(dir);
        log.record("You are Obi. /no_think", ask("Tell me a joke"), "Why did the chicken cross the road?", "a", "FAST", 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(1, log.exportForTraining(out));
        String line = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        List<Object> msgs = MiniJson.list(MiniJson.obj(MiniJson.parse(line)).get("messages"));
        assertEquals(5, msgs.size());
        Map<String, Object> first = MiniJson.obj(msgs.get(0)), last = MiniJson.obj(msgs.get(4));
        assertEquals("system", first.get("role"));
        assertEquals("You are Obi.", first.get("content"));
        assertEquals("assistant", last.get("role"));
        assertEquals("Why did the chicken cross the road?", last.get("content"));
        log.clear();
        assertEquals(0, log.count());
    }
}
