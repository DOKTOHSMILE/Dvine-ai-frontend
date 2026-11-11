package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Fetch models: the provider's own list read in each shape, the ones that can't chat left out. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ModelListTest {
    @Test
    public void readsEachProvidersShape() {
        assertEquals(Arrays.asList("gpt-5", "gpt-6-luna"), ModelList.parse(
                "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-6-luna\"},{\"id\":\"text-embedding-3-small\"},{\"id\":\"gpt-5\"},{\"id\":\"whisper-1\"}]}"));
        assertEquals(Arrays.asList("claude-opus-5-5", "claude-sonnet-5-5"), ModelList.parse(
                "{\"data\":[{\"type\":\"model\",\"id\":\"claude-sonnet-5-5\"},{\"type\":\"model\",\"id\":\"claude-opus-5-5\"}],\"has_more\":false}"));
        assertEquals(Arrays.asList("gemini-2.5-flash"), ModelList.parse(
                "{\"models\":[{\"name\":\"models/gemini-2.5-flash\"},{\"name\":\"models/text-embedding-004\"}]}"));
        assertTrue(ModelList.parse("not json").isEmpty());
        assertEquals("https://api.openai.com/v1/models", ModelList.url("api.openai.com/v1/"));
    }

    @Test
    public void asksWithTheKeyAndSaysWhyItFailed() throws Exception {
        final Map<String, String>[] sent = new Map[1];
        ModelList.Getter ok = (url, headers, t) -> {
            sent[0] = headers;
            return new RemoteBrain.Response(200, new ByteArrayInputStream("{\"data\":[{\"id\":\"m1\"}]}".getBytes(StandardCharsets.UTF_8)));
        };
        assertEquals(Arrays.asList("m1"), ModelList.fetch(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "k", ok));
        assertEquals("k", sent[0].get("x-api-key"));
        assertTrue(sent[0].containsKey("anthropic-version"));
        ModelList.fetch(RemoteBrain.Protocol.OPENAI, "https://gw.example/v1", "k", ok);
        assertEquals("Bearer k", sent[0].get("Authorization"));
        ModelList.Getter refused = (url, headers, t) -> new RemoteBrain.Response(401,
                new ByteArrayInputStream("{\"error\":\"bad key\"}".getBytes(StandardCharsets.UTF_8)));
        try {
            ModelList.fetch(RemoteBrain.Protocol.OPENAI, "https://gw.example/v1", "k", refused);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("401") && e.getMessage().contains("bad key"));
        }
    }
}
