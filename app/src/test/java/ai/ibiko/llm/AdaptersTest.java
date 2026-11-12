package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** The device's model evolves by a trained adapter laid over its frozen weights, put in place and taken back. */
public class AdaptersTest {
    @Test
    public void anAdapterSitsBesideItsModelAndCanBeTakenBack() throws Exception {
        File dir = Files.createTempDirectory("models").toFile();
        File model = new File(dir, "qwen3-1.7b-q4_0.gguf");
        assertEquals("qwen3-1.7b-q4_0.adapter.gguf", Adapters.of(model).getName());
        File notOne = new File(dir, "notes.txt");
        Files.write(notOne.toPath(), "not a gguf".getBytes(StandardCharsets.UTF_8));
        assertFalse("only a GGUF is put in place", Adapters.install(model, notOne));
        File first = new File(dir, "first.gguf"), second = new File(dir, "second.gguf");
        Files.write(first.toPath(), "GGUF first".getBytes(StandardCharsets.UTF_8));
        Files.write(second.toPath(), "GGUF second".getBytes(StandardCharsets.UTF_8));
        assertTrue(Adapters.install(model, first));
        assertTrue(Adapters.install(model, second));
        assertEquals("GGUF second", new String(Files.readAllBytes(Adapters.of(model).toPath()), StandardCharsets.UTF_8));
        assertTrue("the one before is kept once", Adapters.rollBack(model));
        assertEquals("GGUF first", new String(Files.readAllBytes(Adapters.of(model).toPath()), StandardCharsets.UTF_8));
        assertTrue("and with none before, the model runs bare", Adapters.rollBack(model));
        assertFalse(Adapters.of(model).exists());
    }

    @Test
    public void theTrainingSetIsWhatIbikoKnowsToBeRight() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        ai.ibiko.ibiko1.Store.knowledge(new File(files, "brain")).confirm("What is the capital of Nigeria?", "Abuja is the capital of Nigeria.");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n = Adapters.trainingSet(files, null, out);
        assertEquals(1, n);
        String line = out.toString("UTF-8").trim();
        assertTrue(line, line.startsWith("{\"messages\":[") && line.contains("What is the capital of Nigeria?")
                && line.contains("\"assistant\"") && line.contains("Abuja is the capital of Nigeria."));
    }
}
