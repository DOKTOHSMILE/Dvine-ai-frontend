package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** The on-device model's Java side: speaking in sentences, the persona prompt, a verified resumable download, the CPU check. */
public class LocalModelTest {
    @Test
    public void repliesAreSpokenSentenceBySentence() {
        SpeechChunker c = new SpeechChunker();
        List<String> said = new ArrayList<String>();
        for (String piece : new String[]{"A heli", "copter is an **aircraft**. It flies", " with a spinning rotor, which gives lift. ", "Pilots steer it"}) {
            said.addAll(c.add(piece));
        }
        assertEquals(2, said.size());
        assertEquals("A helicopter is an aircraft.", said.get(0));
        assertEquals("It flies with a spinning rotor, which gives lift.", said.get(1));
        assertEquals("Pilots steer it", c.flush());
        // numbers and tiny fragments don't cut a sentence
        SpeechChunker d = new SpeechChunker();
        assertTrue(d.add("It costs 3.5 dollars. ").size() == 1);
        assertTrue(new SpeechChunker().add("Hi. ").isEmpty());
        // a long first clause lets the voice start at a comma
        List<String> early = new SpeechChunker().add("When the sunlight passes through the air of the whole atmosphere, the blue light scatters most");
        assertEquals(1, early.size());
        List<String> sooner = new SpeechChunker().add("Please tell me the short version of this, and then stop");
        assertEquals(1, sooner.size());
        List<String> semi = new SpeechChunker().add("Please tell me the short version of this; and then stop");
        assertEquals(1, semi.size());
    }

    @Test
    public void personasSpeakInTheirOwnManner() {
        String obi = PersonaPrompt.system(false, "Val"), ivie = PersonaPrompt.system(true, null);
        assertTrue(obi.startsWith("You are Ibiko") && obi.contains("Val") && obi.endsWith("/no_think"));
        assertTrue(ivie.startsWith("You are Ibiko") && !ivie.contains("user's name"));
        assertTrue(obi.contains("no lists, markdown") && obi.contains("Never claim to have done"));
    }

    private static ModelSpec spec(byte[] data) throws Exception {
        String sha = ModelDownloader.hex(MessageDigest.getInstance("SHA-256").digest(data));
        return new ModelSpec("test", "m.gguf", "https://example.invalid/m.gguf", data.length, sha, "MIT");
    }

    @Test
    public void downloadsResumeAndAreVerified() throws Exception {
        byte[] data = new byte[3 << 20];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        ModelSpec s = spec(data);
        File dir = Files.createTempDirectory("dvine-model").toFile();
        File target = new File(dir, s.file);
        // the first try breaks after 1 MB
        ModelDownloader.Source flaky = from -> new ByteArrayInputStream(data, (int) from, data.length - (int) from) {
            int given;

            @Override
            public synchronized int read(byte[] b, int off, int len) {
                if (given >= 1 << 20) {
                    throw new RuntimeException("network gone");
                }
                int n = super.read(b, off, Math.min(len, 1 << 16));
                given += Math.max(n, 0);
                return n;
            }
        };
        try {
            ModelDownloader.fetch(s, target, flaky, null);
        } catch (RuntimeException expected) {
            // the read failed mid-way
        }
        assertFalse(target.exists());
        assertTrue(new File(target.getPath() + ".part").length() >= 1 << 20);
        // the second try resumes from where the part file ends
        final long[] resumedAt = {-1};
        ModelDownloader.Source good = from -> {
            resumedAt[0] = from;
            return new ByteArrayInputStream(data, (int) from, data.length - (int) from);
        };
        assertEquals(ModelDownloader.Result.DONE, ModelDownloader.fetch(s, target, good, null));
        assertTrue(resumedAt[0] >= 1 << 20);
        assertTrue(java.util.Arrays.equals(data, Files.readAllBytes(target.toPath())));
        // a file that doesn't match its checksum is deleted, never kept
        byte[] bad = data.clone();
        bad[100] ^= 1;
        File other = new File(dir, "bad.gguf");
        assertEquals(ModelDownloader.Result.BAD_FILE,
                ModelDownloader.fetch(s, other, from -> new ByteArrayInputStream(bad, (int) from, bad.length - (int) from), null));
        assertFalse(other.exists() || new File(other.getPath() + ".part").exists());
        // the progress callback can stop it, keeping the part for later
        File third = new File(dir, "third.gguf");
        assertEquals(ModelDownloader.Result.STOPPED, ModelDownloader.fetch(s, third, good, (done, total) -> false));
        assertTrue(new File(third.getPath() + ".part").isFile());
        // a network error is reported, not thrown
        assertEquals(ModelDownloader.Result.FAILED, ModelDownloader.fetch(s, new File(dir, "x.gguf"), from -> {
            throw new IOException("offline");
        }, null));
    }

    @Test
    public void modelStateAndHardware() throws Exception {
        String cpu = "processor\t: 0\nFeatures\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp asimddp\n";
        assertTrue(LocalModel.cpuSupported(new String[]{"arm64-v8a", "armeabi-v7a"}, cpu));
        assertFalse(LocalModel.cpuSupported(new String[]{"arm64-v8a"}, "Features\t: fp asimd aes\n"));
        assertFalse(LocalModel.cpuSupported(new String[]{"x86_64"}, cpu));
        int t = LocalModel.threads(8);
        assertTrue(t >= 2 && t <= 4);
        // the fast cores only: a phone's clusters, by each core's top speed (kHz)
        assertEquals(2, LocalModel.fastCores(new long[]{2000000, 2000000, 2000000, 2000000, 2000000, 2000000, 2200000, 2200000})); // Helio G99: 2 fast + 6 slow
        assertEquals(4, LocalModel.fastCores(new long[]{1800000, 1800000, 1800000, 1800000, 2600000, 2600000, 2600000, 3000000})); // 1 prime + 3 big + 4 small
        assertEquals(4, LocalModel.fastCores(new long[]{2000000, 2000000, 2000000, 2000000, 2000000, 2000000, 2000000, 2000000})); // all alike: at most 4
        assertEquals(0, LocalModel.fastCores(new long[0])); // unknown: the old rule decides
        File dir = Files.createTempDirectory("dvine-model").toFile();
        LocalModel m = new LocalModel(dir, ModelSpec.QWEN3_1_7B, true);
        assertEquals(LocalModel.State.NOT_INSTALLED, m.state());
        assertFalse(m.ready());
        assertEquals(null, m.reply("sys", "hello", 10, null));
        assertEquals(LocalModel.State.UNSUPPORTED, new LocalModel(dir, ModelSpec.QWEN3_1_7B, false).state());
        // it remembers only the last six exchanges
        for (int i = 0; i < 10; i++) {
            m.remember("q" + i, "a" + i);
        }
        assertEquals(12, m.historySize());
        // an edited message: the model's memory goes back to just before it (q7 and the exchanges after it go)
        // the workload: how full the last reply left the window; nearly full is cleared down to a summary
        org.junit.Assert.assertEquals(0f, m.workload(), 0f);
        m.usage.record(new UsageLog.Call("test", 4096, 600, 2800, 100, 120, false, 1000, System.currentTimeMillis()));
        org.junit.Assert.assertTrue(m.workload() > 0.85f);
        org.junit.Assert.assertTrue(m.historyText().startsWith("User: q4\nAssistant: a4\n"));
        m.rewindTo("q7");
        assertEquals(6, m.historySize()); // q4..q6 remain
        m.rewindTo("never asked");
        assertEquals(6, m.historySize()); // a message it doesn't remember changes nothing
        m.compact("The user is planning a trip to Abuja.");
        assertEquals(2, m.historySize());
        org.junit.Assert.assertTrue(m.historyText().contains("Assistant: The user is planning a trip to Abuja."));
        assertTrue(ModelSpec.QWEN3_1_7B.url.startsWith("https://huggingface.co/") && ModelSpec.QWEN3_1_7B.sha256.length() == 64);
        assertEquals("1.1 gigabytes", ModelSpec.QWEN3_1_7B.sizeWords());
    }

    @Test
    public void otherOfflineModelsCanBeChosen() throws Exception {
        java.util.Set<String> files = new java.util.HashSet<String>();
        for (ModelSpec m : ModelSpec.ALL) {
            // each pinned to a revision, checked by its SHA-256, a 4-bit GGUF, its own file
            assertTrue(m.url, m.url.matches("https://huggingface\\.co/[^/]+/[^/]+/resolve/[0-9a-f]{40}/.+\\.gguf"));
            assertTrue(m.weights, m.sha256.matches("[0-9a-f]{64}") && m.bytes > 300_000_000L && files.add(m.file));
            assertTrue(m.url.contains("Q4_K_M"));
        }
        assertEquals(9, ModelSpec.ALL.length);
        // families in sizes, as Claude comes as Haiku, Sonnet and Opus
        assertEquals(java.util.Arrays.asList("Qwen3", "Llama 3.2", "Gemma 3", "Phi-4"), ModelSpec.families());
        assertEquals(java.util.Arrays.asList(ModelSpec.QWEN3_0_6B, ModelSpec.QWEN3_1_7B, ModelSpec.QWEN3_4B, ModelSpec.QWEN3_8B),
                ModelSpec.sizes("Qwen3"));
        assertEquals("Qwen3 1.7B", ModelSpec.QWEN3_1_7B.weights);
        assertEquals(ModelSpec.WEIGHTS, ModelSpec.QWEN3_1_7B.weights);
        assertSame(ModelSpec.QWEN3_4B, ModelSpec.byFile("qwen3-4b-q4km.gguf"));
        assertSame(ModelSpec.QWEN3_1_7B, ModelSpec.byFile("gone.gguf")); // an unknown choice falls back to the default
        // "/no_think" is Qwen3's switch; other models would only read it as text
        assertEquals("Be brief. /no_think", ModelSpec.QWEN3_4B.prompt("Be brief. /no_think"));
        assertEquals("Be brief.", ModelSpec.LLAMA_3_2_3B.prompt("Be brief. /no_think"));

        File dir = Files.createTempDirectory("dvine-models").toFile();
        LocalModel m = new LocalModel(dir, ModelSpec.QWEN3_1_7B, true);
        m.remember("q", "a");
        File old = new File(dir, ModelSpec.QWEN3_1_7B.file);
        Files.write(old.toPath(), new byte[]{1, 2, 3});
        m.switchTo(ModelSpec.GEMMA_3_4B, false);
        assertSame(ModelSpec.GEMMA_3_4B, m.spec);
        assertEquals(new File(dir, ModelSpec.GEMMA_3_4B.file), m.file());
        assertEquals(LocalModel.State.NOT_INSTALLED, m.state()); // not downloaded yet
        assertEquals(0, m.historySize()); // a new model starts a fresh conversation
        assertTrue(old.isFile()); // kept for switching back
        m.switchTo(ModelSpec.QWEN3_1_7B, false);
        m.switchTo(ModelSpec.PHI_4_MINI, true);
        assertFalse(old.isFile()); // deleted when asked
        // any model on the phone can be deleted to free the space, whole or part-downloaded
        File other = new File(dir, ModelSpec.QWEN3_4B.file), part = new File(dir, ModelSpec.GEMMA_3_1B.file + ".part");
        Files.write(other.toPath(), new byte[1000]);
        Files.write(part.toPath(), new byte[500]);
        assertEquals(1000, m.onPhone(ModelSpec.QWEN3_4B));
        assertEquals(500, m.onPhone(ModelSpec.GEMMA_3_1B));
        assertTrue(m.delete(ModelSpec.QWEN3_4B) && m.delete(ModelSpec.GEMMA_3_1B));
        assertFalse(other.exists() || part.exists());
        assertSame(ModelSpec.PHI_4_MINI, m.spec); // deleting another model leaves the one in use alone
    }

    @Test
    public void memoryIsASettingAndSlownessIsSaidOnce() throws Exception {
        File dir = Files.createTempDirectory("dvine-memory").toFile();
        LocalModel m = new LocalModel(dir, ModelSpec.QWEN3_4B, true);
        assertEquals(8192, m.localWindow()); // 8,192 tokens unless the user picks another size
        assertEquals(8192, ai.ibiko.chat.Tuning.OFFLINE_CONTEXT.get(null));
        assertFalse(m.context(4096)); // nothing loaded: the size is used at the next load
        assertEquals(4096, m.localWindow());
        m.context(500);
        assertEquals(LocalModel.LOCAL_WINDOW, m.localWindow()); // never below the smallest that works

        m.context(8192);
        assertEquals(null, m.slowAdvice(4)); // nothing measured yet
        m.noteSpeed(40, 20_000); // 2 tokens a second
        m.noteSpeed(40, 20_000);
        assertEquals(null, m.slowAdvice(4)); // two replies are not enough to judge
        m.noteSpeed(5, 20_000); // a short reply doesn't count
        assertEquals(null, m.slowAdvice(4));
        m.noteSpeed(60, 20_000);
        String advice = m.slowAdvice(4);
        assertTrue(advice, advice.startsWith("Qwen3 4B is running slowly on this device (2.3 tokens a second)"));
        assertTrue(advice, advice.contains("a smaller size, Qwen3 1.7B") && advice.contains("Offline model memory, 4,096 instead of 8,192")
                && advice.contains("an API model"));
        assertEquals(null, m.slowAdvice(4)); // said once
        m.context(4096); // the user took less memory: still slow, so it is said again, without the memory tip
        String again = m.slowAdvice(4);
        assertTrue(again, again.contains("Qwen3 1.7B") && !again.contains("Offline model memory"));
        m.context(8192);
        assertEquals(null, m.slowAdvice(1)); // fast enough for a lower bar
    }

    @Test
    public void whatThePhonesModelTakesOverFitsItsMemory() {
        String task = "Task: read the code in app/ and explain it.\n";
        StringBuilder work = new StringBuilder(task);
        for (int i = 0; i < 5000; i++) {
            work.append("result line ").append(i).append('\n');
        }
        String fitted = LocalModel.fit(work.toString(), 6000);
        assertTrue(fitted.length() <= 6000);
        assertTrue(fitted.startsWith(task)); // the task stays
        assertTrue(fitted.endsWith("result line 4999\n")); // and the latest work
        assertTrue(fitted.contains("earlier work left out to fit the device's model"));
        assertEquals("short", LocalModel.fit("short", 6000)); // what fits is untouched
    }
}
