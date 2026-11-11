package ai.ibiko.llm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ibiko's image tool: any brain (an API model, the device's model, Ibiko 1) asks for a picture with §IM and a
 * description, and it is made by an image model on one of the pilot's own API providers (the first in their order
 * that offers one): OpenAI (gpt-image-1), Google Gemini (Imagen), or a custom gateway or Ibiko server that has the
 * OpenAI images endpoint. The picture is saved on the device and shown in the chat. Anthropic, DeepSeek and
 * OpenRouter have no image endpoint and are passed over. Pure Java: the network is behind {@link Http}.
 */
public final class ImageMaker {
    private ImageMaker() {
    }

    /** A picture made (its file) or why not. */
    public static final class Result {
        public final File file;
        public final String error, made;

        Result(File file, String error, String made) {
            this.file = file;
            this.error = error;
            this.made = made;
        }
    }

    /** One provider that can make pictures: where, with which key and model. */
    public static final class Source {
        public final String name, address, key, model;

        public Source(String name, String address, String key, String model) {
            this.name = name;
            this.address = address;
            this.key = key;
            this.model = model;
        }
    }

    /** The network: a JSON POST with a key, and a GET for a picture given as an address. */
    public interface Http {
        byte[] post(String url, String key, String json) throws IOException;

        byte[] get(String url) throws IOException;
    }

    /** The image model a provider uses by default, or null when it has no image endpoint. */
    public static String defaultModel(Providers p) {
        switch (p) {
            case OPENAI:
                return "gpt-image-1";
            case GEMINI:
                return "imagen-3.0-generate-002";
            case CUSTOM:
            case DVINE_SERVER:
                return "gpt-image-1";
            default:
                return null;
        }
    }

    /** The providers that can make pictures, in the pilot's order, with the image model each uses. */
    public static List<Source> sources(ProviderStore store, String chosenModel) {
        List<Source> out = new ArrayList<Source>();
        for (Providers p : store.order()) {
            String m = defaultModel(p);
            String key = store.key(p);
            if (m == null || key == null || key.trim().isEmpty() || store.address(p).trim().isEmpty()) {
                continue;
            }
            out.add(new Source(p.title, store.address(p), key, chosenModel != null && !chosenModel.trim().isEmpty() ? chosenModel.trim() : m));
        }
        return out;
    }

    /** The request body for one picture. gpt-image models always answer with the picture itself (base64). */
    static String body(String model, String prompt, String size) {
        Map<String, Object> req = new LinkedHashMap<String, Object>();
        req.put("model", model);
        req.put("prompt", prompt);
        req.put("n", 1.0);
        req.put("size", size);
        if (!model.startsWith("gpt-image")) {
            req.put("response_format", "b64_json");
        }
        return ai.ibiko.brain.MiniJson.write(req);
    }

    /**
     * Makes one picture of {@code prompt}, trying each source in turn until one works, and saves it in
     * {@code outDir}. The reply names what went wrong when none could.
     */
    public static Result make(List<Source> sources, String prompt, File outDir, Http http) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return new Result(null, "There was nothing to draw.", null);
        }
        if (sources.isEmpty()) {
            return new Result(null, "No image model is set up. Add OpenAI or Google Gemini (or a gateway with an images"
                    + " endpoint) under Settings › Models › AI providers; Anthropic, DeepSeek and OpenRouter can't make"
                    + " pictures.", null);
        }
        StringBuilder why = new StringBuilder();
        for (Source s : sources) {
            try {
                String url = s.address.replaceAll("/+$", "") + "/images/generations";
                byte[] got = http.post(url, s.key, body(s.model, prompt.trim(), "1024x1024"));
                Object parsed = ai.ibiko.brain.MiniJson.parse(new String(got, StandardCharsets.UTF_8));
                Map<String, Object> o = ai.ibiko.brain.MiniJson.obj(parsed);
                List<Object> data = ai.ibiko.brain.MiniJson.list(o.get("data"));
                if (data.isEmpty()) {
                    Map<String, Object> err = ai.ibiko.brain.MiniJson.obj(o.get("error"));
                    throw new IOException(ai.ibiko.brain.MiniJson.str(err.get("message"), "no picture came back"));
                }
                Map<String, Object> first = ai.ibiko.brain.MiniJson.obj(data.get(0));
                String b64 = ai.ibiko.brain.MiniJson.str(first.get("b64_json"), "");
                byte[] picture = !b64.isEmpty() ? java.util.Base64.getDecoder().decode(b64)
                        : http.get(ai.ibiko.brain.MiniJson.str(first.get("url"), ""));
                if (picture == null || picture.length < 64) {
                    throw new IOException("the picture came back empty");
                }
                outDir.mkdirs();
                File f = new File(outDir, "image-" + System.currentTimeMillis() + ".png");
                try (OutputStream out = new FileOutputStream(f)) {
                    out.write(picture);
                }
                return new Result(f, null, s.name + " (" + s.model + ")");
            } catch (IOException | RuntimeException e) {
                why.append(why.length() == 0 ? "" : "; ").append(s.name).append(": ")
                        .append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        return new Result(null, "The picture couldn't be made (" + why + ").", null);
    }

    /** The web, for the app. */
    public static final Http WEB = new Http() {
        @Override
        public byte[] post(String url, String key, String json) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(180000); // a picture can take a minute or two
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + key);
            try (OutputStream o = c.getOutputStream()) {
                o.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            byte[] b = read(in);
            if (code >= 400) {
                String said = new String(b, StandardCharsets.UTF_8);
                try {
                    said = ai.ibiko.brain.MiniJson.str(ai.ibiko.brain.MiniJson.obj(ai.ibiko.brain.MiniJson.obj(
                            ai.ibiko.brain.MiniJson.parse(said)).get("error")).get("message"), said);
                } catch (RuntimeException ignored) {
                    // not JSON
                }
                throw new IOException("HTTP " + code + ": " + (said.length() > 200 ? said.substring(0, 200) : said));
            }
            return b;
        }

        @Override
        public byte[] get(String url) throws IOException {
            if (!url.startsWith("https://")) {
                throw new IOException("the picture's address isn't https");
            }
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            return read(c.getInputStream());
        }
    };

    static byte[] read(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try (InputStream i = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = i.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return b.toByteArray();
        }
    }
}
