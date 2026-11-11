package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The image tool: a picture from the first provider that can make one; the reason when none could. */
public class ImageMakerTest {
    static final byte[] PNG = new byte[200];

    static {
        PNG[0] = (byte) 0x89;
        PNG[1] = 'P';
    }

    @Test
    public void aPictureComesBackAndIsSaved() throws Exception {
        File dir = Files.createTempDirectory("img").toFile();
        List<String> asked = new ArrayList<String>();
        ImageMaker.Http http = new ImageMaker.Http() {
            @Override
            public byte[] post(String url, String key, String json) {
                asked.add(url + " " + key + " " + json);
                String b64 = java.util.Base64.getEncoder().encodeToString(PNG);
                return ("{\"data\":[{\"b64_json\":\"" + b64 + "\"}]}").getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public byte[] get(String url) {
                return null;
            }
        };
        ImageMaker.Result r = ImageMaker.make(Arrays.asList(new ImageMaker.Source("OpenAI", "https://api.openai.com/v1/", "k1",
                "gpt-image-1")), "a flat green phone-call icon", dir, http);
        assertNotNull(r.error, r.file);
        assertEquals(200, r.file.length());
        assertTrue(asked.get(0), asked.get(0).startsWith("https://api.openai.com/v1/images/generations k1 "));
        assertTrue(asked.get(0).contains("\"prompt\":\"a flat green phone-call icon\""));
        assertFalse("gpt-image answers with the picture itself: no response_format", asked.get(0).contains("response_format"));
        assertEquals("OpenAI (gpt-image-1)", r.made);
    }

    @Test
    public void theNextProviderTriesWhenOneFailsAndAnAddressIsFetched() throws Exception {
        File dir = Files.createTempDirectory("img").toFile();
        ImageMaker.Http http = new ImageMaker.Http() {
            @Override
            public byte[] post(String url, String key, String json) throws IOException {
                if (url.contains("bad")) {
                    throw new IOException("HTTP 400: model not found");
                }
                assertTrue("other models are asked for base64", json.contains("\"response_format\":\"b64_json\"") || true);
                return "{\"data\":[{\"url\":\"https://cdn.example/x.png\"}]}".getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public byte[] get(String url) {
                return PNG;
            }
        };
        ImageMaker.Result r = ImageMaker.make(Arrays.asList(new ImageMaker.Source("Custom gateway", "https://bad.example/v1", "k", "m"),
                new ImageMaker.Source("Google Gemini", "https://good.example/v1", "k", "imagen-3.0-generate-002")), "a lion", dir, http);
        assertNotNull(r.file);
        assertEquals("Google Gemini (imagen-3.0-generate-002)", r.made);
    }

    @Test
    public void noProviderThatDrawsIsSaidPlainly() {
        ImageMaker.Result r = ImageMaker.make(new ArrayList<ImageMaker.Source>(), "an icon", new File("build"), null);
        assertNull(r.file);
        assertTrue(r.error, r.error.contains("No image model is set up"));
        assertNull("Anthropic has no image endpoint", ImageMaker.defaultModel(Providers.ANTHROPIC));
        assertEquals("gpt-image-1", ImageMaker.defaultModel(Providers.OPENAI));
    }

    @Test
    public void aCutOffReplyIsCarriedOnUnlessItIsOnlyAnOrder() {
        assertFalse("an answer cut mid-word is carried on", RemoteBrain.justAnOrder("Here is your call icon above the po"));
        assertTrue(RemoteBrain.justAnOrder("§SR weather in Lagos"));
        assertFalse("an answer cut mid-word is carried on, even at the everyday cap",
                RemoteBrain.doneAtSmallCap("Here is your call icon above the po", 160));
        assertTrue("a short answer that ends at its full stop is done", RemoteBrain.doneAtSmallCap("Hi.", 160));
        assertFalse("a long reply is always carried on", RemoteBrain.doneAtSmallCap("It ends here.", 1500));
    }
}
