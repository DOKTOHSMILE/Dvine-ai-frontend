package ai.ibiko.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import ai.ibiko.bridge.Probe;

/** The model's sizes come from its GGUF header, the tokenizer list skipped, and size the context before a load. */
public class GgufProbeTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private void u32(long v) {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) v).array(), 0, 4);
    }

    private void u64(long v) {
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array(), 0, 8);
    }

    private void str(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        u64(b.length);
        out.write(b, 0, b.length);
    }

    private void num(String key, long v) {
        str(key);
        u32(4);
        u32(v);
    }

    private byte[] header() {
        u32(0x46554747);
        u32(3);
        u64(0);
        u64(9);
        str("general.architecture");
        u32(8);
        str("qwen3");
        str("tokenizer.ggml.tokens"); // a string array, skipped
        u32(9);
        u32(8);
        u64(3);
        str("a");
        str("bb");
        str("ccc");
        str("tokenizer.ggml.scores"); // a float array, skipped
        u32(9);
        u32(6);
        u64(2);
        u32(0);
        u32(0);
        num("qwen3.context_length", 40960);
        num("qwen3.block_count", 28);
        num("qwen3.embedding_length", 1024);
        num("qwen3.attention.head_count", 16);
        num("qwen3.attention.head_count_kv", 8);
        num("qwen3.attention.key_length", 128);
        return out.toByteArray();
    }

    @Test
    public void readsTheSizesAndSkipsTheWordList() throws Exception {
        Probe p = GgufProbe.read(new ByteArrayInputStream(header()));
        assertEquals(40960, p.contextLength);
        assertEquals(28, p.nLayer);
        assertEquals(1024, p.nEmbd);
        assertEquals(8, p.nHeadKv);
        assertEquals(128 * 8, p.nEmbdK);
        assertEquals(64 * 8, p.nEmbdV);
        assertEquals(2, p.nGqa);
    }

    @Test
    public void notAModelIsUnknown() throws Exception {
        assertEquals(0, GgufProbe.read(new ByteArrayInputStream("hello, not a model".getBytes())).contextLength);
        File f = File.createTempFile("model", ".gguf");
        Files.write(f.toPath(), new byte[]{1, 2, 3});
        assertEquals(0, GgufProbe.of(f).contextLength);
        assertEquals(0, GgufProbe.of(new File("/no/such/model.gguf")).contextLength);
        f.delete();
    }

    @Test
    public void aHeaderSizesTheContextToTheFreeMemory() throws Exception {
        Probe p = GgufProbe.read(new ByteArrayInputStream(header()));
        long weights = 600L * 1024 * 1024;
        EngineConfig roomy = EngineContext.decide(p, null, weights, 6L * 1024 * 1024 * 1024, 2, false);
        assertEquals(EngineContext.MAX_CTX, roomy.nCtx);
        EngineConfig tight = EngineContext.decide(p, null, weights, 2000L * 1024 * 1024, 2, false);
        assertTrue(tight.nCtx >= EngineContext.MIN_CTX && tight.nCtx < roomy.nCtx);
        assertTrue(tight.reason.contains("free memory"));
        EngineConfig none = EngineContext.decide(p, null, weights, 1200L * 1024 * 1024, 2, false);
        assertEquals("nothing fits: the load keeps its own fallback", 0, none.nCtx);
    }
}
