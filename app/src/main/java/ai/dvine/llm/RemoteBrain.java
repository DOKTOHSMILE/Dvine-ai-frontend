package ai.dvine.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.dvine.brain.MiniJson;

/**
 * A brain the user brings: Anthropic's Messages API, or any server that speaks the OpenAI chat-completions API
 * (OpenAI, Google Gemini's OpenAI endpoint, OpenRouter, Groq, a LiteLLM or self-hosted gateway). The reply streams
 * back piece by piece as server-sent events, so the voice starts after the first sentence just as with the brain on
 * the phone. The key goes only to the address the user chose, over https; a backup key is tried when the first is
 * refused or out of credit. Pure Java; the network is behind {@link Transport} so tests run without one.
 */
public final class RemoteBrain {
    public enum Protocol { OPENAI, ANTHROPIC }

    /** One request out, the response body back (the caller closes it). */
    public interface Transport {
        Response post(String url, Map<String, String> headers, byte[] body, int timeoutMs) throws IOException;
    }

    public static final class Response {
        public final int status;
        public final InputStream body;

        public Response(int status, InputStream body) {
            this.status = status;
            this.body = body;
        }
    }

    /** What the last call used, as the server reported it (-1 where it didn't say). */
    public static final class Used {
        public final int promptTokens, outputTokens;

        Used(int promptTokens, int outputTokens) {
            this.promptTokens = promptTokens;
            this.outputTokens = outputTokens;
        }
    }

    /** The Anthropic API version this client is written for (it sends no temperature: that version refuses one). */
    public static final String ANTHROPIC_VERSION = "2026-10-21";
    /** Claude models that take Anthropic's server-side fallback when a request is declined. */
    private static final String FALLBACK_MODELS = "^claude-(opus-5-5|opus-5|fable-5-1|sonnet-5-5)$";

    public final Protocol protocol;
    public final String baseUrl, model;
    private final String key, backupKey;
    private final Transport transport;
    private volatile String problem;
    private volatile Used used;
    private volatile boolean cancelled;

    // ------------------------------------------------------------ the user's settings for this provider (open, not fixed)

    /** "auto" (send images; drop them if the model refuses), "on" or "off". */
    private String vision = "auto";
    /** "auto" (known reasoning model names), "on" or "off". */
    private String reasoningMode = "auto";
    /** The most the model may write, in tokens; 0 lets DVINE decide per request. */
    private int maxOutput;
    private int timeoutMs = 180_000;
    /** Extra fields put into every request as they are (temperature, top_p, provider routing…), from JSON. */
    private Map<String, Object> extra = new LinkedHashMap<String, Object>();
    /** Models that refused an image this session: they get a note instead. */
    private static final java.util.Set<String> NO_VISION = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private boolean dropImages, dropFiles;
    /** Models that refused a document this session: they get its pages as pictures, or a note. */
    private static final java.util.Set<String> NO_FILES = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * The provider's advanced settings: images ("auto", "on", "off"), reasoning ("auto", "on", "off"), the most it may
     * write (0 = DVINE decides), the time it may take (seconds) and extra request fields as JSON ("" for none).
     */
    public RemoteBrain options(String vision, String reasoning, int maxOutput, int timeoutSeconds, String extraJson) {
        this.vision = vision == null || vision.isEmpty() ? "auto" : vision;
        this.reasoningMode = reasoning == null || reasoning.isEmpty() ? "auto" : reasoning;
        this.maxOutput = Math.max(0, maxOutput);
        this.timeoutMs = Math.max(10, timeoutSeconds <= 0 ? 180 : timeoutSeconds) * 1000;
        this.extra = new LinkedHashMap<String, Object>();
        if (extraJson != null && !extraJson.trim().isEmpty()) {
            try {
                this.extra.putAll(MiniJson.obj(MiniJson.parse(extraJson.trim())));
            } catch (RuntimeException e) {
                // not JSON: ignored (the providers sheet says so)
            }
        }
        return this;
    }

    /** Whether extra request fields are valid JSON (an object), for the providers sheet. */
    public static boolean validExtra(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true;
        }
        try {
            return MiniJson.parse(json.trim()) instanceof Map;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The file as the API takes it, or null when this provider takes no such file (then its text, pages or
     * frames go instead): Anthropic reads PDFs and plain text as documents; the OpenAI form takes a sound
     * (mp3, wav) as audio and any other file as a file part, which the gateway passes on or refuses.
     */
    private Map<String, Object> nativeFile(ai.dvine.chat.Attachments.Doc d, java.io.File f) {
        String mime = d.mime == null ? "application/octet-stream" : d.mime.toLowerCase(java.util.Locale.US);
        if (protocol == Protocol.ANTHROPIC && !mime.contains("pdf") && !mime.startsWith("text/")) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = java.nio.file.Files.readAllBytes(f.toPath());
        } catch (IOException | RuntimeException e) {
            return null;
        }
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        if (protocol == Protocol.ANTHROPIC) {
            Map<String, Object> src = new LinkedHashMap<String, Object>();
            if (mime.startsWith("text/")) {
                src.put("type", "text");
                src.put("media_type", "text/plain");
                src.put("data", new String(bytes, StandardCharsets.UTF_8));
            } else {
                src.put("type", "base64");
                src.put("media_type", "application/pdf");
                src.put("data", java.util.Base64.getEncoder().encodeToString(bytes));
            }
            part.put("type", "document");
            part.put("source", src);
            part.put("title", d.name);
            return part;
        }
        String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
        if (mime.equals("audio/mpeg") || mime.equals("audio/mp3") || mime.equals("audio/wav") || mime.equals("audio/x-wav")) {
            Map<String, Object> audio = new LinkedHashMap<String, Object>();
            audio.put("data", b64);
            audio.put("format", mime.contains("wav") ? "wav" : "mp3");
            part.put("type", "input_audio");
            part.put("input_audio", audio);
            return part;
        }
        Map<String, Object> file = new LinkedHashMap<String, Object>();
        file.put("filename", d.name);
        file.put("file_data", "data:" + mime + ";base64," + b64);
        part.put("type", "file");
        part.put("file", file);
        return part;
    }

    /** The words the phone took out of an attached file (kept beside it as ".txt"), or null. */
    private static String sidecar(ai.dvine.chat.Attachments.Doc d) {
        java.io.File t = new java.io.File(d.path + ".txt");
        try {
            return t.isFile() ? new String(java.nio.file.Files.readAllBytes(t.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean hasFiles(List<String[]> turns) {
        for (String[] t : turns) {
            if (t[1] != null && ai.dvine.chat.Attachments.FILE.matcher(t[1]).find()) {
                return true;
            }
        }
        return false;
    }

    /** Whether documents (PDFs) go to this model as files: when it takes images and hasn't refused one. */
    boolean takesFiles() {
        return seesImages() && !dropFiles && ("on".equals(vision) || !NO_FILES.contains(baseUrl + "|" + model));
    }

    private static boolean hasImages(List<String[]> turns) {
        for (String[] t : turns) {
            if (t[1] != null && ai.dvine.chat.Attachments.IMAGE.matcher(t[1]).find()) {
                return true;
            }
        }
        return false;
    }

    /** Whether images are sent to this model as images. */
    boolean seesImages() {
        return !"off".equals(vision) && !dropImages && ("on".equals(vision) || !NO_VISION.contains(baseUrl + "|" + model));
    }

    public RemoteBrain(Protocol protocol, String baseUrl, String model, String key, String backupKey, Transport transport) {
        this.protocol = protocol == null ? Protocol.OPENAI : protocol;
        this.baseUrl = normalise(baseUrl);
        this.model = model == null ? "" : model.trim();
        this.key = key == null ? "" : key.trim();
        this.backupKey = backupKey == null ? "" : backupKey.trim();
        this.transport = transport;
    }

    /** "api.openai.com/v1/" → "https://api.openai.com/v1"; a full ".../chat/completions" or ".../messages" is cut back. */
    public static String normalise(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim().replaceAll("/+$", "");
        if (!u.isEmpty() && !u.matches("(?i)^https?://.*")) {
            u = "https://" + u;
        }
        return u.replaceAll("(?i)/(chat/completions|messages)$", "");
    }

    /** Why the settings can't be used, or null when they can. Only https: the key must never travel in the clear. */
    public static String check(String baseUrl, String model, String key) {
        String u = normalise(baseUrl);
        if (u.isEmpty()) {
            return "the address is empty";
        }
        if (!u.toLowerCase(Locale.US).startsWith("https://")) {
            return "the address must start with https://, so your key isn't sent in the clear";
        }
        if (!u.matches("(?i)^https://[a-z0-9.-]+(:\\d+)?(/\\S*)?$")) {
            return "that isn't a web address";
        }
        if (model == null || model.trim().isEmpty()) {
            return "the model name is empty";
        }
        if (key == null || key.trim().isEmpty()) {
            return "the API key is empty";
        }
        return null;
    }

    public String problem() {
        return problem;
    }

    public Used used() {
        return used;
    }

    public String host() {
        return baseUrl.replaceAll("(?i)^https?://", "").replaceAll("/.*$", "");
    }

    /**
     * A model that reasons before it answers (GPT-5, the o-series, R1, "thinking" models) behind the OpenAI form: it
     * takes max_completion_tokens and no temperature, and needs room for its hidden reasoning.
     */
    boolean reasoning() {
        if (protocol != Protocol.OPENAI || model == null) {
            return false;
        }
        if (!"auto".equals(reasoningMode)) {
            return "on".equals(reasoningMode); // the user said so on the providers sheet
        }
        return model.toLowerCase(java.util.Locale.US).matches("(.*/)?(gpt-5.*|o[1-9](-.*)?|.*-r1.*|.*reason.*|.*thinking.*|qwq.*)");
    }

    /** Stops the reply being written (from any thread); the stream is abandoned at the next piece. */
    public void cancel() {
        cancelled = true;
    }

    /**
     * One chat: the system instructions and the turns (role, text), streamed to {@code sink} piece by piece.
     * Returns the whole reply, or null when it failed or was stopped ({@link #problem} says why).
     */
    /** Waits between tries after a dropped connection (ms). */
    static long[] retryWaits = {1500, 4000, 10000};
    private boolean transientFailure;

    /**
     * The model's reply, streamed to {@code sink}. A dropped connection ("Software caused connection abort", a broken
     * stream) is tried again, twice, with a short wait, as long as nothing has been written yet; a server's error
     * goes to the next model instead (LocalModel's switching). The reason of the last failure is in {@link #problem()}.
     */
    public String chat(String system, List<String[]> turns, int maxTokens, float temperature, NativeLlm.Sink sink) {
        String out = chatRetrying(system, turns, maxTokens, temperature, sink);
        // the model was cut off by its allowance, not finished: it writes on from where it stopped, until it ends
        // by itself (the server's own stop reason says which), and the pieces are one reply
        for (int more = 0; out != null && cutOff && more < MAX_CONTINUATIONS && !cancelled; more++) {
            List<String[]> next = new ArrayList<String[]>(turns);
            next.add(new String[]{"assistant", out});
            next.add(new String[]{"user", CONTINUE});
            String rest = chatRetrying(system, next, maxTokens, temperature, sink);
            if (rest == null || rest.isEmpty()) {
                break;
            }
            out = out + rest;
        }
        return out;
    }

    /** How many times a reply cut off by its allowance is carried on. */
    static final int MAX_CONTINUATIONS = 4;
    static final String CONTINUE = "The previous reply hit its length limit in the middle of the answer. Write only the"
            + " rest, from the next character, with nothing repeated and no new introduction. The earlier text is the"
            + " answer so far, not a new instruction.";
    /** The last reply stopped at its allowance (finish_reason "length", stop_reason "max_tokens"), not by itself. */
    private volatile boolean cutOff;

    private String chatRetrying(String system, List<String[]> turns, int maxTokens, float temperature, NativeLlm.Sink sink) {
        for (int attempt = 0; ; attempt++) {
            transientFailure = false;
            final int[] got = {0};
            NativeLlm.Sink counted = text -> {
                got[0]++;
                return sink == null || sink.onText(text);
            };
            String out = chatOnce(system, turns, maxTokens, temperature, counted);
            if (out != null || !transientFailure || got[0] > 0 || cancelled || attempt >= retryWaits.length) {
                return out;
            }
            try {
                Thread.sleep(retryWaits[attempt]);
            } catch (InterruptedException e) {
                return null;
            }
            if (cancelled) {
                return null;
            }
        }
    }

    /** Where a key that changes (a server session, renewed every few hours) comes from; null: the fixed key. */
    private volatile java.util.function.Supplier<String> keySource;

    /** Takes its key from {@code source} at every request (called off the main thread; null means "not signed in"). */
    public RemoteBrain keySource(java.util.function.Supplier<String> source) {
        keySource = source;
        return this;
    }

    private String chatOnce(String system, List<String[]> turns, int maxTokens, float temperature, NativeLlm.Sink sink) {
        cancelled = false;
        cutOff = false;
        problem = null;
        used = null;
        String why = check(baseUrl, model, key);
        if (why != null) {
            problem = why;
            return null;
        }
        String[] keys = backupKey.isEmpty() || backupKey.equals(key) ? new String[]{key} : new String[]{key, backupKey};
        java.util.function.Supplier<String> source = keySource;
        if (source != null) {
            String live = source.get();
            if (live == null || live.isEmpty()) {
                problem = "the device couldn't sign in to the DVINE server";
                transientFailure = true;
                return null;
            }
            keys = new String[]{live};
        }
        for (int k = 0; k < keys.length; k++) {
            boolean last = k == keys.length - 1;
            // a server that refuses one optional field is asked again in the plainer form
            // a reasoning model gets its own form first (no temperature, room to think), saving two refused requests
            dropImages = false;
            dropFiles = false;
            for (int form = reasoning() ? 2 : 0; form < (protocol == Protocol.ANTHROPIC ? 2 : 3); form++) {
                Response r;
                try {
                    r = transport.post(url(), headers(keys[k], form), body(system, turns, maxTokens, temperature, form), timeoutMs);
                } catch (IOException e) {
                    problem = "I couldn't reach " + host() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")");
                    transientFailure = PromptFit.retryReach(e.getMessage());
                    return null;
                }
                try (InputStream in = r.body) {
                    if ((r.status == 400 || r.status == 422) && takesFiles() && hasFiles(turns)) {
                        // a model that takes no documents: the same request with the pages as pictures (or a note)
                        NO_FILES.add(baseUrl + "|" + model);
                        dropFiles = true;
                        form--;
                        continue;
                    }
                    if ((r.status == 400 || r.status == 422) && !dropImages && seesImages() && (hasImages(turns) || hasFiles(turns))) {
                        // a model that can't see (DeepSeek and others): the same request with notes instead of images
                        NO_VISION.add(baseUrl + "|" + model);
                        dropImages = true;
                        form--;
                        continue;
                    }
                    if ((r.status == 400 || r.status == 422) && form < (protocol == Protocol.ANTHROPIC ? 1 : 2)) {
                        continue;
                    }
                    if ((r.status == 401 || r.status == 403 || r.status == 402 || r.status == 429) && !last) {
                        break; // this key is refused or out of credit: the backup key next
                    }
                    if (r.status < 200 || r.status >= 300) {
                        problem = failure(r.status, in, keys[k]);
                        return null;
                    }
                    return protocol == Protocol.ANTHROPIC ? readAnthropic(in, sink) : readOpenAi(in, sink);
                } catch (IOException e) {
                    problem = "the connection to " + host() + " broke";
                    transientFailure = true;
                    return null;
                }
            }
        }
        if (problem == null) {
            problem = "the server refused the request";
        }
        return null;
    }

    String url() {
        return baseUrl + (protocol == Protocol.ANTHROPIC ? "/messages" : "/chat/completions");
    }

    private Map<String, String> headers(String apiKey, int form) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Content-Type", "application/json");
        h.put("Accept", "text/event-stream");
        if (protocol == Protocol.ANTHROPIC) {
            h.put("x-api-key", apiKey);
            h.put("anthropic-version", ANTHROPIC_VERSION);
            if (form == 0 && model.matches(FALLBACK_MODELS)) {
                h.put("anthropic-beta", "server-side-fallback-2026-07-01");
            }
        } else {
            h.put("Authorization", "Bearer " + apiKey);
        }
        return h;
    }

    /**
     * The request. The brain asks for short answers (a one-line order is 48 tokens), but a thinking model spends
     * part of its allowance before it writes, so a remote call never gets fewer than 1024; a short call asks for
     * little effort. OpenAI forms: 0 with the usage report, 1 without it, 2 as the newer OpenAI reasoning models
     * want (max_completion_tokens, no temperature). Anthropic forms: 0 with the refusal fallback and the effort, 1 plain (for models that take neither).
     */
    byte[] body(String system, List<String[]> turns, int maxTokens, float temperature, int form) {
        Map<String, Object> req = new LinkedHashMap<String, Object>();
        req.put("model", model);
        String sys = system == null ? "" : system.replace("/no_think", "").trim();
        int allowance = maxOutput > 0 ? maxOutput : Math.max(1024, maxTokens);
        boolean small = maxTokens <= 256;
        List<Object> messages = new ArrayList<Object>();
        if (protocol == Protocol.ANTHROPIC) {
            req.put("max_tokens", (double) allowance);
            if (!sys.isEmpty()) {
                req.put("system", sys);
            }
            for (String[] t : merged(turns)) {
                messages.add(content(t[0], t[1]));
            }
            req.put("messages", messages);
            req.put("stream", Boolean.TRUE);
            if (small && form == 0) {
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("effort", "low");
                req.put("output_config", out);
            }
            if (form == 0 && model.matches(FALLBACK_MODELS)) {
                req.put("fallbacks", "default");
            }
            if (form == 0) {
                // prompt caching: what repeats from the last request (instructions, the chat so far, an agent's work)
                // is read from Anthropic's cache instead of again; a server that refuses it gets the plain form
                Map<String, Object> cache = new LinkedHashMap<String, Object>();
                cache.put("type", "ephemeral");
                req.put("cache_control", cache);
            }
        } else {
            if (!sys.isEmpty()) {
                messages.add(message("system", sys));
            }
            for (String[] t : turns) {
                messages.add(content("assistant".equals(t[0]) ? "assistant" : "user", t[1]));
            }
            req.put("messages", messages);
            req.put("stream", Boolean.TRUE);
            if (form == 2) {
                // a reasoning model's hidden thinking counts against this limit: without room for it, the visible
                // answer comes back empty ("the model didn't answer")
                int completion = allowance;
                if (reasoning()) {
                    completion = Math.min(32_000, Math.max(8_000, allowance * 3));
                }
                req.put("max_completion_tokens", (double) completion);
                if (small || reasoning()) {
                    req.put("reasoning_effort", small || allowance <= 4_000 ? "low" : "medium");
                }
            } else {
                req.put("max_tokens", (double) allowance);
                req.put("temperature", Math.round(temperature * 100) / 100.0);
            }
            if (form == 0) {
                Map<String, Object> opts = new LinkedHashMap<String, Object>();
                opts.put("include_usage", Boolean.TRUE);
                req.put("stream_options", opts);
            }
        }
        if (form < 2 || protocol == Protocol.ANTHROPIC) {
            req.putAll(extra); // the user's own request fields win (temperature, top_p, routing…)
        } else {
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                if (!e.getKey().equals("temperature") && !e.getKey().equals("max_tokens")) {
                    req.put(e.getKey(), e.getValue()); // the reasoning form refuses these two
                }
            }
        }
        return MiniJson.write(req).getBytes(StandardCharsets.UTF_8);
    }

    /** Anthropic wants turns to alternate, starting with the user: join neighbours with the same role. */
    static List<String[]> merged(List<String[]> turns) {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] t : turns) {
            String role = "assistant".equals(t[0]) ? "assistant" : "user";
            if (out.isEmpty() && role.equals("assistant")) {
                continue;
            }
            if (!out.isEmpty() && out.get(out.size() - 1)[0].equals(role)) {
                out.get(out.size() - 1)[1] = out.get(out.size() - 1)[1] + "\n\n" + t[1];
            } else {
                out.add(new String[]{role, t[1] == null ? "" : t[1]});
            }
        }
        return out;
    }

    /**
     * A message with any attached images ({@code [[image:path]]}) sent as real images, in this protocol's form;
     * without images it is plain text. A missing or unreadable image becomes the words "[an image that couldn't be
     * read]".
     */
    Map<String, Object> content(String role, String text) {
        java.util.List<String> images = new ArrayList<String>(ai.dvine.chat.Attachments.images(text));
        java.util.List<ai.dvine.chat.Attachments.Doc> docs = ai.dvine.chat.Attachments.files(text);
        if ((images.isEmpty() && docs.isEmpty()) || !"user".equals(role)) {
            return message(role, ai.dvine.chat.Attachments.withoutImages(text));
        }
        if (!seesImages()) {
            String noted = ai.dvine.chat.Attachments.IMAGE.matcher(text).replaceAll(
                    "[an image is attached, but this model can't see images: say so and work from the words]");
            for (ai.dvine.chat.Attachments.Doc d : docs) {
                String inside = sidecar(d);
                if (inside != null) {
                    noted += "\n\n" + ai.dvine.chat.Attachments.text(d.name, inside); // a text-only model still reads the words
                }
            }
            noted = ai.dvine.chat.Attachments.FILE.matcher(noted).replaceAll(
                    "[a file, $3, is attached, but this model can't take files: work from any text of it given here, or ask for the part that matters]");
            return message(role, noted);
        }
        List<Object> parts = new ArrayList<Object>();
        String words = ai.dvine.chat.Attachments.FILE.matcher(ai.dvine.chat.Attachments.IMAGE.matcher(text).replaceAll("")).replaceAll("").trim();
        for (ai.dvine.chat.Attachments.Doc d : docs) {
            java.io.File f = new java.io.File(d.path);
            Map<String, Object> part = takesFiles() && f.isFile() && f.length() <= 20L * 1024 * 1024 ? nativeFile(d, f) : null;
            if (part != null) {
                parts.add(part);
                continue;
            }
            // the model can't take this file: what the phone took out of it instead
            String inside = sidecar(d);
            if (inside != null) {
                words += "\n\n" + ai.dvine.chat.Attachments.text(d.name, inside);
            }
            if (!d.pages.isEmpty()) {
                images.addAll(d.pages); // a PDF's pages, a video's frames
                words += "\n[the pictures are " + (d.mime.startsWith("video/") ? "frames of the video " : "the pages of ") + d.name + ", in order]";
            }
            if (inside == null && d.pages.isEmpty()) {
                words += "\n[" + d.name + " (" + d.mime + ") is attached, but this model can't take that kind of file: say so and ask for"
                        + " what matters in it as text]";
            }
        }
        for (String path : images) {
            byte[] bytes;
            try {
                bytes = java.nio.file.Files.readAllBytes(new java.io.File(path).toPath());
            } catch (IOException | RuntimeException e) {
                words += "\n[an image that couldn't be read]";
                continue;
            }
            String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
            String type = path.toLowerCase(java.util.Locale.US).endsWith(".png") ? "image/png" : "image/jpeg";
            Map<String, Object> part = new LinkedHashMap<String, Object>();
            if (protocol == Protocol.ANTHROPIC) {
                Map<String, Object> src = new LinkedHashMap<String, Object>();
                src.put("type", "base64");
                src.put("media_type", type);
                src.put("data", b64);
                part.put("type", "image");
                part.put("source", src);
            } else {
                Map<String, Object> url = new LinkedHashMap<String, Object>();
                url.put("url", "data:" + type + ";base64," + b64);
                part.put("type", "image_url");
                part.put("image_url", url);
            }
            parts.add(part);
        }
        Map<String, Object> t = new LinkedHashMap<String, Object>();
        t.put("type", "text");
        t.put("text", words.isEmpty() ? "(see the image)" : words);
        parts.add(t);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", role);
        m.put("content", parts);
        return m;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    /** OpenAI events: "data: {json}" lines with choices[0].delta.content; "data: [DONE]" ends it. */
    String readOpenAi(InputStream in, NativeLlm.Sink sink) throws IOException {
        BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder all = new StringBuilder(), plain = new StringBuilder();
        String line;
        int out = 0;
        while ((line = lines.readLine()) != null) {
            if (cancelled) {
                problem = "stopped";
                return null;
            }
            String t = line.trim();
            if (!t.startsWith("data:")) {
                plain.append(line).append('\n'); // a server that ignored "stream" sends one JSON body
                continue;
            }
            String data = t.substring(5).trim();
            if (data.equals("[DONE]")) {
                break;
            }
            Map<String, Object> o = parse(data);
            if (o == null) {
                continue;
            }
            Map<String, Object> u = MiniJson.obj(o.get("usage"));
            if (!u.isEmpty()) {
                used = new Used((int) MiniJson.num(u.get("prompt_tokens"), -1), (int) MiniJson.num(u.get("completion_tokens"), -1));
            }
            List<Object> choices = MiniJson.list(o.get("choices"));
            if (choices.isEmpty()) {
                continue;
            }
            if ("length".equals(MiniJson.str(MiniJson.obj(choices.get(0)).get("finish_reason"), ""))) {
                cutOff = true;
            }
            String piece = MiniJson.str(MiniJson.obj(MiniJson.obj(choices.get(0)).get("delta")).get("content"), "");
            if (!piece.isEmpty()) {
                all.append(piece);
                out++;
                if (sink != null && !sink.onText(piece)) {
                    problem = "stopped";
                    return null;
                }
            }
        }
        if (all.length() == 0 && plain.length() > 0) {
            Map<String, Object> o = parse(plain.toString());
            List<Object> choices = o == null ? new ArrayList<Object>() : MiniJson.list(o.get("choices"));
            String text = choices.isEmpty() ? "" : MiniJson.str(MiniJson.obj(MiniJson.obj(choices.get(0)).get("message")).get("content"), "");
            cutOff = !choices.isEmpty() && "length".equals(MiniJson.str(MiniJson.obj(choices.get(0)).get("finish_reason"), ""));
            if (!text.isEmpty() && sink != null) {
                sink.onText(text);
            }
            all.append(text);
        }
        if (used == null && out > 0) {
            used = new Used(-1, out);
        }
        if (all.length() == 0) {
            problem = "the server sent no answer";
            return null;
        }
        return all.toString();
    }

    /**
     * Anthropic events: text arrives in content_block_delta (text_delta); thinking blocks are skipped; the input
     * tokens come in message_start and the output tokens in message_delta, with the stop reason (a refusal is said
     * as one).
     */
    String readAnthropic(InputStream in, NativeLlm.Sink sink) throws IOException {
        BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder all = new StringBuilder();
        String line, stop = null;
        int input = -1, output = -1;
        while ((line = lines.readLine()) != null) {
            if (cancelled) {
                problem = "stopped";
                return null;
            }
            String t = line.trim();
            if (!t.startsWith("data:")) {
                continue;
            }
            Map<String, Object> o = parse(t.substring(5).trim());
            if (o == null) {
                continue;
            }
            String type = MiniJson.str(o.get("type"), "");
            if (type.equals("message_start")) {
                Map<String, Object> u = MiniJson.obj(MiniJson.obj(o.get("message")).get("usage"));
                input = (int) MiniJson.num(u.get("input_tokens"), -1);
                if (input >= 0) {
                    // with caching, the cached part is counted apart: the prompt is all three
                    input += (int) MiniJson.num(u.get("cache_read_input_tokens"), 0) + (int) MiniJson.num(u.get("cache_creation_input_tokens"), 0);
                }
            } else if (type.equals("content_block_delta")) {
                Map<String, Object> d = MiniJson.obj(o.get("delta"));
                if ("text_delta".equals(MiniJson.str(d.get("type"), ""))) {
                    String piece = MiniJson.str(d.get("text"), "");
                    if (!piece.isEmpty()) {
                        all.append(piece);
                        if (sink != null && !sink.onText(piece)) {
                            problem = "stopped";
                            return null;
                        }
                    }
                }
            } else if (type.equals("message_delta")) {
                stop = MiniJson.str(MiniJson.obj(o.get("delta")).get("stop_reason"), stop);
                output = (int) MiniJson.num(MiniJson.obj(o.get("usage")).get("output_tokens"), output);
            } else if (type.equals("error")) {
                problem = "the server stopped: " + MiniJson.str(MiniJson.obj(o.get("error")).get("message"), "an error");
                return null;
            } else if (type.equals("message_stop")) {
                break;
            }
        }
        used = new Used(input, output);
        cutOff = "max_tokens".equals(stop);
        if ("refusal".equals(stop) && all.length() == 0) {
            problem = "the model declined to answer that";
            return null;
        }
        if (all.length() == 0) {
            problem = "the server sent no answer";
            return null;
        }
        return all.toString();
    }

    private static Map<String, Object> parse(String json) {
        try {
            return MiniJson.obj(MiniJson.parse(json));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** What went wrong, in words, from the status and the server's own message (never a key). */
    private String failure(int status, InputStream in, String usedKey) {
        String said = "";
        try {
            byte[] b = readUpTo(in, 4096);
            Map<String, Object> o = parse(new String(b, StandardCharsets.UTF_8));
            if (o != null) {
                said = MiniJson.str(MiniJson.obj(o.get("error")).get("message"), MiniJson.str(o.get("message"), ""));
            }
        } catch (IOException e) {
            // no readable message
        }
        boolean busy = busy(status, said);
        if (busy) {
            transientFailure = true; // waited out and asked again (chatRetrying), not given up on
        }
        String what = busy ? "the model is busy right now"
                : status == 401 || status == 403 ? "the API key was refused"
                : status == 402 ? "the account is out of credit"
                : status == 404 ? "the address or the model name wasn't found"
                : status == 429 ? "the server says too many requests, or the account is out of credit"
                : status >= 500 ? "the server had a problem" : "the server said no";
        for (String k : new String[]{usedKey, key, backupKey}) {
            if (k != null && k.length() > 3) {
                said = said.replace(k, "…");
            }
        }
        return what + " (" + status + (said.isEmpty() ? "" : ": " + (said.length() > 160 ? said.substring(0, 160) + "…" : said)) + ")";
    }

    /**
     * A refusal that passes: the model is busy, overloaded or rate-limited ("That model is busy right now", 429, 503,
     * 529), worth waiting a moment and asking again, unlike a wrong key or no credit.
     */
    static boolean busy(int status, String said) {
        String m = said == null ? "" : said.toLowerCase(java.util.Locale.ROOT);
        if (m.matches("(?s).*\\b(insufficient|quota|credit|billing|invalid|incorrect|unauthori[sz]ed|permission|forbidden region)\\b.*")) {
            return false; // money or the key: waiting won't help
        }
        if (status == 503 || status == 529 || status == 502 || status == 504) {
            return true;
        }
        return (status == 403 || status == 429 || status == 500 || status == 408)
                && m.matches("(?s).*\\b(busy|overloaded|over capacity|capacity|rate.?limit|too many requests|try again|temporarily|unavailable)\\b.*");
    }

    private static byte[] readUpTo(InputStream in, int max) throws IOException {
        byte[] buf = new byte[max];
        int n = 0, r;
        while (n < max && (r = in.read(buf, n, max - n)) > 0) {
            n += r;
        }
        return java.util.Arrays.copyOf(buf, n);
    }

    /**
     * The real network, https only. {@code sockets} trusts an extra certificate authority (the user pasted it for a
     * gateway on a private CA), on top of the phone's own; null uses only the phone's.
     */
    public static Transport https(javax.net.ssl.SSLSocketFactory sockets) {
        return (url, headers, body, timeoutMs) -> {
            java.net.URL u = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(u.getProtocol())) {
                throw new IOException("only https is allowed");
            }
            javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) u.openConnection();
            if (sockets != null) {
                c.setSSLSocketFactory(sockets);
            }
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(6_000);
            c.setReadTimeout(timeoutMs);
            for (Map.Entry<String, String> h : headers.entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
            c.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream o = c.getOutputStream()) {
                o.write(body);
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(status, in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in);
        };
    }
}
