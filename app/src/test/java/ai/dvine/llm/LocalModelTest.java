package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    }

    @Test
    public void personasSpeakInTheirOwnManner() {
        String obi = PersonaPrompt.system(false, "Val"), ivie = PersonaPrompt.system(true, null);
        assertTrue(obi.startsWith("You are Obi") && obi.contains("Val") && obi.endsWith("/no_think"));
        assertTrue(ivie.startsWith("You are Ivie") && !ivie.contains("user's name"));
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
        assertEquals(4, LocalModel.threads(8));
        assertEquals(2, LocalModel.threads(2));
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
        assertTrue(ModelSpec.QWEN3_1_7B.url.startsWith("https://huggingface.co/") && ModelSpec.QWEN3_1_7B.sha256.length() == 64);
        assertEquals("1.1 gigabytes", ModelSpec.QWEN3_1_7B.sizeWords());
    }
}
