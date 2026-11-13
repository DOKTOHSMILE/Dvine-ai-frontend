package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.ibiko.brain.MiniJson;

/** The brain the user brings: OpenAI-compatible and Anthropic APIs, against a fake server. */
public class RemoteBrainTest {
    /** A fake server: answers in turn, and keeps what it was sent. */
    private static final class Fake implements RemoteBrain.Transport {
        final List<String> urls = new ArrayList<String>(), bodies = new ArrayList<String>();
        final List<Map<String, String>> headers = new ArrayList<Map<String, String>>();
        final List<Object[]> replies = new ArrayList<Object[]>();

        Fake reply(int status, String body) {
            replies.add(new Object[]{status, body});
            return this;
        }

        @Override
        public RemoteBrain.Response post(String url, Map<String, String> h, byte[] body, int timeoutMs) {
            urls.add(url);
            headers.add(h);
            bodies.add(new String(body, StandardCharsets.UTF_8));
            Object[] r = replies.remove(0);
            return new RemoteBrain.Response((Integer) r[0], new ByteArrayInputStream(((String) r[1]).getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static List<String[]> ask(String q) {
        List<String[]> t = new ArrayList<String[]>();
        t.add(new String[]{"user", q});
        return t;
    }

    private static final String OPENAI_STREAM = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n"
            + "data: {\"choices\":[{\"delta\":{\"content\":\" there.\"}}]}\n\n"
            + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":31,\"completion_tokens\":3}}\n\ndata: [DONE]\n";

    @Test
    public void streamsAnOpenAiReplyPieceByPiece() {
        Fake f = new Fake().reply(200, OPENAI_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "openrouter.ai/api/v1/", "openai/gpt-5-mini", "sk-test", "", f);
        List<String> pieces = new ArrayList<String>();
        assertEquals("Hello there.", b.chat("Be brief. /no_think", ask("hi"), 48, 0.7f, t -> pieces.add(t)));
        assertEquals(2, pieces.size()); // spoken as it arrives
        assertEquals("https://openrouter.ai/api/v1/chat/completions", f.urls.get(0));
        assertEquals("Bearer sk-test", f.headers.get(0).get("Authorization"));
        Map<String, Object> req = MiniJson.obj(MiniJson.parse(f.bodies.get(0)));
        assertEquals("openai/gpt-5-mini", req.get("model"));
        // a reasoning model: room for its hidden thinking, or the visible answer comes back empty
        assertEquals(16_384.0, MiniJson.num(req.get("max_completion_tokens"), 0), 0);
        assertFalse(f.bodies.get(0).contains("temperature"));
        assertFalse(f.bodies.get(0).contains("no_think"));
        assertEquals(31, b.used().promptTokens);
        assertEquals(3, b.used().outputTokens);
    }

    @Test
    public void asksAgainInThePlainerFormsWhenAFieldIsRefused() {
        Fake f = new Fake().reply(400, "{\"error\":{\"message\":\"Unknown field stream_options\"}}")
                .reply(400, "{\"error\":{\"message\":\"Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens'\"}}")
                .reply(200, OPENAI_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-4.1-mini", "sk-a", "", f);
        assertEquals("Hello there.", b.chat("s", ask("hi"), 48, 0.7f, null));
        assertTrue(f.bodies.get(0).contains("stream_options"));
        assertFalse(f.bodies.get(1).contains("stream_options"));
        assertTrue(f.bodies.get(2).contains("max_completion_tokens"));
        assertFalse(f.bodies.get(2).contains("temperature"));
    }

    @Test
    public void triesTheBackupKeyAndNeverRepeatsAKey() {
        Fake f = new Fake().reply(429, "{\"error\":{\"message\":\"quota\"}}").reply(200, OPENAI_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "sk-first", "sk-second", f);
        assertNotNull(b.chat("s", ask("hi"), 48, 0.7f, null));
        assertEquals("Bearer sk-second", f.headers.get(1).get("Authorization"));

        Fake bad = new Fake().reply(401, "{\"error\":{\"message\":\"Incorrect API key provided: sk-secret-123\"}}");
        RemoteBrain c = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "sk-secret-123", "", bad);
        assertNull(c.chat("s", ask("hi"), 48, 0.7f, null));
        assertTrue(c.problem().startsWith("the API key was refused (401"));
        assertFalse(c.problem().contains("sk-secret-123")); // the key is never repeated back
    }

    @Test
    public void onlyHttpsAndAModelAndAKey() {
        assertNotNull(RemoteBrain.check("http://example.com/v1", "m", "k"));
        assertNotNull(RemoteBrain.check("https://example.com/v1", "", "k"));
        assertNotNull(RemoteBrain.check("https://example.com/v1", "m", ""));
        assertNull(RemoteBrain.check("brewkeg.dev/v1", "gpt-5-mini", "k"));
        assertEquals("https://brewkeg.dev/v1", RemoteBrain.normalise("brewkeg.dev/v1/chat/completions"));
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "http://plain.example/v1", "m", "k", "", new Fake());
        assertNull(b.chat("s", ask("hi"), 48, 0.7f, null));
        assertTrue(b.problem().contains("https"));
    }

    private static final String ANTHROPIC_STREAM = "event: message_start\n"
            + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"usage\":{\"input_tokens\":120,\"output_tokens\":1}}}\n\n"
            + "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}\n\n"
            + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"\"}}\n\n"
            + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"SAY Hi,\"}}\n\n"
            + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\" Valentine.\"}}\n\n"
            + "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":9}}\n\n"
            + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n";

    @Test
    public void streamsAClaudeReplyAndSkipsThinking() {
        Fake f = new Fake().reply(200, ANTHROPIC_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "sk-ant-x", "", f);
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[]{"assistant", "a greeting nobody asked for"}); // can't start with the assistant
        turns.add(new String[]{"user", "hello"});
        turns.add(new String[]{"user", "it's Valentine"}); // two in a row are joined
        assertEquals("SAY Hi, Valentine.", b.chat("You are the brain. /no_think", turns, 48, 0.7f, null));
        assertEquals("https://api.anthropic.com/v1/messages", f.urls.get(0));
        Map<String, String> h = f.headers.get(0);
        assertEquals("sk-ant-x", h.get("x-api-key"));
        assertEquals("2026-10-21", h.get("anthropic-version"));
        assertNull(h.get("Authorization"));
        Map<String, Object> req = MiniJson.obj(MiniJson.parse(f.bodies.get(0)));
        assertFalse(req.containsKey("temperature")); // the 2026-10-21 API refuses it
        assertEquals("You are the brain.", req.get("system"));
        List<Object> msgs = MiniJson.list(req.get("messages"));
        assertEquals(1, msgs.size());
        assertEquals("hello\n\nit's Valentine", MiniJson.obj(msgs.get(0)).get("content"));
        assertEquals("default", req.get("fallbacks")); // the refusal fallback, on for this model
        assertEquals("server-side-fallback-2026-07-01", h.get("anthropic-beta"));
        assertEquals("low", MiniJson.obj(req.get("output_config")).get("effort")); // a one-line order: little thinking
        assertEquals("ephemeral", MiniJson.obj(req.get("cache_control")).get("type")); // what repeats is read from the cache
        assertEquals(120, b.used().promptTokens);
        assertEquals(9, b.used().outputTokens);
    }

    @Test
    public void aClaudeModelWithoutTheFallbackIsAskedPlainly() {
        String refused = "{\"type\":\"error\",\"error\":{\"message\":\"effort not supported\"}}";
        Fake f = new Fake().reply(400, refused).reply(400, refused).reply(200, ANTHROPIC_STREAM).reply(200, ANTHROPIC_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://gw.example.com/v1", "claude-haiku-4-5", "k", "", f);
        assertNotNull(b.chat("s", ask("hi"), 48, 0.7f, null));
        assertFalse(f.bodies.get(0).contains("fallbacks"));
        assertTrue("the middle form keeps the cache", f.bodies.get(1).contains("cache_control"));
        assertFalse(f.bodies.get(2).contains("output_config"));
        assertFalse(f.bodies.get(2).contains("cache_control")); // the plain form, for a server that refuses extras
        // the next message starts with the form that worked: no refused request first, every time
        assertNotNull(b.chat("s", ask("again"), 48, 0.7f, null));
        assertEquals(4, f.bodies.size());
        assertFalse(f.bodies.get(3).contains("cache_control"));
    }

    @Test
    public void aBigClaudeModelThinksAsHardAsTheCallNeeds() {
        Fake f = new Fake().reply(200, ANTHROPIC_STREAM).reply(200, ANTHROPIC_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k", "", f);
        b.effortFor(TaskSize.Tier.FAST);
        assertNotNull(b.chat("s", ask("hi"), 2048, 0.7f, null));
        assertTrue(f.bodies.get(0), f.bodies.get(0).contains("\"effort\":\"low\""));
        b.effortFor(TaskSize.Tier.STRONG);
        assertNotNull(b.chat("s", ask("audit the repo"), 8000, 0.7f, null));
        assertTrue(f.bodies.get(1), f.bodies.get(1).contains("\"effort\":\"high\""));
        assertTrue("the prompt is cached", f.bodies.get(1).contains("cache_control"));
    }

    @Test
    public void aRefusalIsSaidAsOne() {
        String refused = "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10}}}\n"
                + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"refusal\"},\"usage\":{\"output_tokens\":0}}\n"
                + "data: {\"type\":\"message_stop\"}\n";
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k", "",
                new Fake().reply(200, refused));
        assertNull(b.chat("s", ask("hi"), 48, 0.7f, null));
        assertEquals("the model declined to answer that", b.problem());
    }

    @Test
    public void theModelAnswersThroughTheApiWhenOnlineAndRemembersIt() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("models").toFile();
        LocalModel m = new LocalModel(dir, ModelSpec.QWEN3_1_7B, true);
        assertFalse(m.ready());
        final boolean[] online = {true};
        Fake f = new Fake().reply(200, OPENAI_STREAM).reply(200, OPENAI_STREAM);
        m.useRemote(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "", f), 400_000, () -> online[0]);
        assertTrue(m.ready());
        assertFalse(m.localReady());
        assertEquals(400_000, m.window());
        assertEquals("Hello there.", m.reply("sys", "hi", 48, null));
        UsageLog.Call c = m.usage.last();
        assertTrue(c.measured);
        assertEquals(31, c.system + c.conversation + c.message);
        assertEquals(3, c.reply);
        assertTrue(c.brain.contains("gpt-5-mini"));
        // the next question carries the conversation
        m.reply("sys", "and again", 48, null);
        assertTrue(f.bodies.get(1).contains("Hello there."));
        online[0] = false; // offline: the API isn't used, and with no brain on the phone there is none
        assertFalse(m.ready());
        assertNull(m.reply("sys", "hi", 48, null));
        assertEquals(2, f.urls.size());
    }

    @Test(timeout = 20000)
    public void aCallCancelledWhileWaitingForTheModelNeverRuns() throws Exception {
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        final java.util.concurrent.CountDownLatch inFirst = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        RemoteBrain.Transport slow = (url, headers, body, timeoutMs) -> {
            calls.incrementAndGet();
            inFirst.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new RemoteBrain.Response(200, new java.io.ByteArrayInputStream(OPENAI_STREAM.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        };
        m.useRemote(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "", slow), 400_000, () -> true);
        Thread first = new Thread(() -> m.reply("sys", "first", 48, null));
        first.start();
        inFirst.await(); // the first call holds the model
        final String[] second = {"not run"};
        Thread waiting = new Thread(() -> second[0] = m.reply("sys", "second", 48, null));
        waiting.start();
        while (waiting.getState() != Thread.State.BLOCKED) {
            Thread.sleep(5); // the second call waits for the model
        }
        m.cancel(); // the user moved on
        release.countDown();
        first.join();
        waiting.join();
        assertNull("a call cancelled while it waited returns nothing", second[0]);
        assertEquals("and never reaches the model", 1, calls.get());
        assertEquals("Hello there.", m.reply("sys", "third", 48, null)); // a call after the cancel runs
    }

    private static List<String> names(String... n) {
        return java.util.Arrays.asList(n);
    }

    @Test
    public void switchesToTheBackupWhenThePrimaryFailsAndLetsItRest() throws Exception {
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        Fake first = new Fake().reply(401, "{\"error\":{\"message\":\"bad key\"}}");
        Fake second = new Fake().reply(200, OPENAI_STREAM).reply(200, OPENAI_STREAM);
        List<RemoteBrain> chain = new ArrayList<RemoteBrain>();
        chain.add(new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k1", "", first));
        chain.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k2", "", second));
        m.useRemotes(chain, names("Anthropic (Claude)", "OpenAI"), java.util.Arrays.asList(1_000_000, 400_000), () -> true);
        assertEquals(2, m.remoteCount());
        assertEquals("Hello there.", m.reply("sys", "hi", 48, null));
        String note = m.takeSwitchNote();
        assertTrue(note, note.startsWith("Anthropic (Claude) didn't answer (the API key was refused"));
        assertTrue(note.endsWith("so OpenAI did."));
        assertNull(m.takeSwitchNote()); // said once
        assertTrue(m.usage.last().brain.contains("OpenAI"));
        // the primary rests: the next question goes straight to the backup
        assertEquals("Hello there.", m.reply("sys", "again", 48, null));
        assertEquals(1, first.urls.size());
        assertEquals(2, second.urls.size());
        assertEquals(400_000, m.window());
    }

    @Test
    public void aReplyAlreadyStartedIsNotSwitchedMidSentence() throws Exception {
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        // the first starts speaking, then the stream breaks off with an error event and nothing more
        Fake first = new Fake().reply(200, "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n");
        Fake second = new Fake().reply(200, OPENAI_STREAM);
        List<RemoteBrain> chain = new ArrayList<RemoteBrain>();
        chain.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://a.example/v1", "m1", "k1", "", first));
        chain.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://b.example/v1", "m2", "k2", "", second));
        m.useRemotes(chain, names("A", "B"), java.util.Arrays.asList(1000, 1000), () -> true);
        // "Hel" arrives and the stream ends: that counts as an answer, so B is never asked
        assertEquals("Hel", m.reply("sys", "hi", 48, null));
        assertEquals(0, second.urls.size());
    }

    @Test
    public void everyProviderFailingLeavesTheReasonAndNoAnswer() throws Exception {
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        List<RemoteBrain> chain = new ArrayList<RemoteBrain>();
        chain.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://a.example/v1", "m1", "k1", "", new Fake().reply(500, "{}")));
        chain.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://b.example/v1", "m2", "k2", "", new Fake().reply(429, "{}")));
        chain.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://c.example/v1", "m3", "k3", "", new Fake().reply(404, "{}")));
        m.useRemotes(chain, names("A", "B", "C"), java.util.Arrays.asList(1000, 1000, 1000), () -> true);
        assertNull(m.reply("sys", "hi", 48, null)); // no brain on the phone either
        assertTrue(m.problem().contains("(404"));
        assertEquals(3, m.usage.failedToday(System.currentTimeMillis()));
    }

    @Test
    public void simpleQuestionsToTheFastModelRealWorkToTheStrongOne() throws Exception {
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        Fake strong = new Fake().reply(200, OPENAI_STREAM).reply(200, OPENAI_STREAM);
        Fake fast = new Fake().reply(200, OPENAI_STREAM).reply(500, "{}");
        List<RemoteBrain> brains = new ArrayList<RemoteBrain>(), fasts = new ArrayList<RemoteBrain>();
        brains.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5", "k", "", strong));
        fasts.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "", fast));
        m.useRemotes(brains, fasts, names("OpenAI"), java.util.Arrays.asList(400_000), () -> true);
        m.reply("sys", "what's the capital of Nigeria?", 48, null); // a one-line order: fast
        assertEquals(1, fast.urls.size());
        assertEquals(0, strong.urls.size());
        assertTrue(m.usage.last().brain.contains("gpt-5-mini"));
        m.reply("sys", "write a business plan", 1500, null); // real work: strong
        assertEquals(1, strong.urls.size());
        assertTrue(m.usage.last().brain.contains("(gpt-5)"));
        m.reply("sys", "and the time?", 48, null); // the fast model fails: the same provider's strong one answers
        assertEquals(2, fast.urls.size());
        assertEquals(2, strong.urls.size());
        assertNull(m.takeSwitchNote() == null ? null : null);
        m.coding = true; // Ibiko: strong even for a short call
        Fake strong2 = new Fake().reply(200, OPENAI_STREAM);
        List<RemoteBrain> b2 = new ArrayList<RemoteBrain>(), f2 = new ArrayList<RemoteBrain>();
        b2.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5", "k", "", strong2));
        Fake fast2 = new Fake();
        f2.add(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "", fast2));
        m.useRemotes(b2, f2, names("OpenAI"), java.util.Arrays.asList(400_000), () -> true);
        m.task("sys", "list files", 2000, null);
        assertEquals(1, strong2.urls.size());
        assertEquals(0, fast2.urls.size());
    }

    @Test
    public void reasoningModelsGetRoomToThinkOnTheFirstRequest() {
        RemoteBrain gpt5 = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5", "k", "", null);
        assertTrue(gpt5.reasoning());
        String body = new String(gpt5.body("sys", java.util.Collections.singletonList(new String[]{"user", "hi"}), 2000, 0.7f, 2),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body, body.contains("\"max_completion_tokens\":16384") && body.contains("\"reasoning_effort\":\"medium\""));
        assertTrue(body, !body.contains("temperature"));
        RemoteBrain plain = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-4.1", "k", "", null);
        org.junit.Assert.assertFalse(plain.reasoning());
        assertTrue(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-5-mini", "k", "", null).reasoning());
        assertTrue(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", "deepseek/deepseek-r1", "k", "", null).reasoning());
    }

    @Test
    public void aModelThatCantSeeGetsTheSameRequestWithANoteInstead() throws Exception {
        java.io.File img = java.io.File.createTempFile("pic", ".jpg");
        java.nio.file.Files.write(img.toPath(), new byte[]{1, 2, 3});
        Fake f = new Fake().reply(400, "{\"error\":{\"message\":\"unknown variant `image_url`\"}}").reply(200, OPENAI_STREAM);
        RemoteBrain ds = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat", "k", "", f);
        assertEquals("Hello there.", ds.chat("s", ask("What is this?\n\n" + ai.ibiko.chat.Attachments.image(img.getPath())), 48, 0.7f, null));
        assertTrue(f.bodies.get(0), f.bodies.get(0).contains("image_url") && f.bodies.get(0).contains("AQID"));
        assertFalse(f.bodies.get(1).contains("image_url"));
        assertTrue(f.bodies.get(1), f.bodies.get(1).contains("this model can't see images"));
        // remembered: the next image goes as a note straight away
        Fake g = new Fake().reply(200, OPENAI_STREAM);
        RemoteBrain again = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat", "k", "", g);
        again.chat("s", ask("And this?\n\n" + ai.ibiko.chat.Attachments.image(img.getPath())), 48, 0.7f, null);
        assertEquals(1, g.bodies.size());
        assertFalse(g.bodies.get(0).contains("image_url"));
        // a model that sees gets the image as an image (Anthropic form)
        Fake h = new Fake().reply(200, "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"A cat.\"}}\n\n");
        RemoteBrain claude = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k", "", h);
        claude.chat("s", ask("What is this?\n\n" + ai.ibiko.chat.Attachments.image(img.getPath())), 48, 0.7f, null);
        assertTrue(h.bodies.get(0), h.bodies.get(0).contains("\"type\":\"image\"") && h.bodies.get(0).contains("\"media_type\":\"image/jpeg\""));
    }

    @Test
    public void theProvidersOwnSettingsWin() {
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-reasoner", "k", "", null)
                .options("off", "on", 4000, 60, "{\"temperature\": 0.3, \"top_p\": 0.9}");
        assertTrue(b.reasoning());
        String body = new String(b.body("s", ask("hi"), 48, 0.7f, 2), StandardCharsets.UTF_8);
        assertTrue(body, body.contains("\"max_completion_tokens\":4000") && body.contains("\"top_p\":0.9"));
        assertFalse(body, body.contains("temperature")); // the reasoning form refuses it
        RemoteBrain plain = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5", "k", "", null)
                .options("auto", "off", 0, 180, "{\"temperature\": 0.2}");
        assertFalse(plain.reasoning()); // the user said it isn't one
        String b0 = new String(plain.body("s", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8);
        assertTrue(b0, b0.contains("\"temperature\":0.2"));
        assertTrue(RemoteBrain.validExtra("{\"a\":1}") && RemoteBrain.validExtra("") && !RemoteBrain.validExtra("temperature=1"));
    }

    @Test
    public void aPdfGoesAsADocumentThenAsPagesThenAsANote() throws Exception {
        java.io.File pdf = java.io.File.createTempFile("doc", ".pdf");
        java.nio.file.Files.write(pdf.toPath(), "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        java.io.File page = java.io.File.createTempFile("page1", ".jpg");
        java.nio.file.Files.write(page.toPath(), new byte[]{9, 9});
        String msg = "Summarise this\n\n" + ai.ibiko.chat.Attachments.file(pdf.getPath(), "application/pdf", "report.pdf",
                java.util.Collections.singletonList(page.getPath()));
        // Claude: a document block
        Fake a = new Fake().reply(200, "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Ok.\"}}\n\n");
        new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k", "", a).chat("s", ask(msg), 48, 0.7f, null);
        assertTrue(a.bodies.get(0), a.bodies.get(0).contains("\"type\":\"document\"") && a.bodies.get(0).contains("\"media_type\":\"application/pdf\""));
        // OpenAI form: a file part; refused, then the pages as pictures
        Fake o = new Fake().reply(400, "{\"error\":{\"message\":\"file parts not supported\"}}").reply(200, OPENAI_STREAM);
        RemoteBrain gw = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://gw.example.com/v1", "some-vision-model", "k", "", o);
        assertEquals("Hello there.", gw.chat("s", ask(msg), 48, 0.7f, null));
        assertTrue(o.bodies.get(0), o.bodies.get(0).contains("\"type\":\"file\"") && o.bodies.get(0).contains("\"filename\":\"report.pdf\""));
        assertTrue(o.bodies.get(1), !o.bodies.get(1).contains("\"type\":\"file\"") && o.bodies.get(1).contains("image_url")
                && o.bodies.get(1).contains("the pages of report.pdf"));
        // a text-only model: a note
        Fake t = new Fake().reply(200, OPENAI_STREAM);
        new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat", "k", "", t).options("off", "auto", 0, 180, "")
                .chat("s", ask(msg), 48, 0.7f, null);
        assertTrue(t.bodies.get(0), t.bodies.get(0).contains("a file, report.pdf, is attached, but this model can't take files"));
        assertEquals("Summarise this\n\n[a document: report.pdf]", ai.ibiko.chat.Attachments.withoutImages(msg));
    }

    @Test
    public void anyFileGoesAsItselfOrAsWhatThePhoneTookOutOfIt() throws Exception {
        java.io.File xlsx = java.io.File.createTempFile("sales", ".xlsx");
        java.nio.file.Files.write(xlsx.toPath(), new byte[]{1, 2, 3});
        java.nio.file.Files.write(new java.io.File(xlsx.getPath() + ".txt").toPath(), "sheet1:\nMonth\tSales\nJan\t120".getBytes(StandardCharsets.UTF_8));
        String mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String msg = "Total the sales\n\n" + ai.ibiko.chat.Attachments.file(xlsx.getPath(), mime, "sales.xlsx", new ArrayList<String>());
        // the OpenAI form: the spreadsheet itself; refused: its cells as text
        Fake o = new Fake().reply(400, "{\"error\":{\"message\":\"unsupported file\"}}").reply(200, OPENAI_STREAM);
        new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://gw2.example.com/v1", "m", "k", "", o).chat("s", ask(msg), 48, 0.7f, null);
        assertTrue(o.bodies.get(0), o.bodies.get(0).contains("\"filename\":\"sales.xlsx\"") && o.bodies.get(0).contains("spreadsheetml"));
        assertTrue(o.bodies.get(1), o.bodies.get(1).contains("Jan\\t120") && !o.bodies.get(1).contains("\"type\":\"file\""));
        // Anthropic takes no spreadsheets: straight to the text, no refused request
        Fake a = new Fake().reply(200, "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"120.\"}}\n\n");
        new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k", "", a).chat("s", ask(msg), 48, 0.7f, null);
        assertEquals(1, a.bodies.size());
        assertTrue(a.bodies.get(0), a.bodies.get(0).contains("Jan\\t120") && !a.bodies.get(0).contains("\"document\""));
        // a sound to the OpenAI form goes as audio
        java.io.File mp3 = java.io.File.createTempFile("note", ".mp3");
        java.nio.file.Files.write(mp3.toPath(), new byte[]{7});
        Fake s3 = new Fake().reply(200, OPENAI_STREAM);
        new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-4o-audio-preview", "k", "", s3).chat("s",
                ask("Transcribe\n\n" + ai.ibiko.chat.Attachments.file(mp3.getPath(), "audio/mpeg", "note.mp3", new ArrayList<String>())), 48, 0.7f, null);
        assertTrue(s3.bodies.get(0), s3.bodies.get(0).contains("\"type\":\"input_audio\"") && s3.bodies.get(0).contains("\"format\":\"mp3\""));
        // a text-only model still gets the words
        Fake t = new Fake().reply(200, OPENAI_STREAM);
        new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat", "k", "", t).options("off", "auto", 0, 180, "")
                .chat("s", ask(msg), 48, 0.7f, null);
        assertTrue(t.bodies.get(0), t.bodies.get(0).contains("Jan\\t120"));
    }

    @Test
    public void aDroppedConnectionIsTriedAgain() {
        long[] waits = RemoteBrain.retryWaits;
        RemoteBrain.retryWaits = new long[]{0, 0};
        try {
            final int[] calls = {0};
            RemoteBrain.Transport flaky = (url, h, body, timeoutMs) -> {
                if (calls[0]++ < 2) {
                    throw new java.io.IOException("Software caused connection abort");
                }
                return new RemoteBrain.Response(200, new ByteArrayInputStream(OPENAI_STREAM.getBytes(StandardCharsets.UTF_8)));
            };
            RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://brewkeg.dev/v1", "gpt-4.1", "k", "", flaky);
            assertEquals("Hello there.", b.chat("s", ask("hi"), 48, 0.7f, null));
            assertEquals(3, calls[0]);
            calls[0] = -100; // always failing: gives up after two more tries, and says why
            assertNull(b.chat("s", ask("hi"), 48, 0.7f, null));
            assertTrue(b.problem(), b.problem().startsWith("I couldn't reach brewkeg.dev"));
        } finally {
            RemoteBrain.retryWaits = waits;
        }
    }

    @Test
    public void aReplyCutOffByItsLimitIsCarriedOnUntilItEnds() {
        final java.util.List<String> sent = new java.util.ArrayList<String>();
        final String[] parts = {
                "data: {\"choices\":[{\"delta\":{\"content\":\"The audit found three \"}}]}\n"
                        + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\ndata: [DONE]\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"problems, all fixed.\"}}]}\n"
                        + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\ndata: [DONE]\n"};
        final int[] calls = {0};
        RemoteBrain.Transport t = (url, h, body, timeoutMs) -> {
            sent.add(new String(body, StandardCharsets.UTF_8));
            return new RemoteBrain.Response(200, new ByteArrayInputStream(parts[Math.min(calls[0]++, 1)].getBytes(StandardCharsets.UTF_8)));
        };
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://brewkeg.dev/v1", "gpt-4.1", "k", "", t);
        assertEquals("The audit found three problems, all fixed.", b.chat("s", ask("audit"), 1500, 0.7f, null));
        assertEquals(2, calls[0]); // stopped by itself the second time: no third request
        assertTrue(sent.get(1), sent.get(1).contains("The audit found three ") && sent.get(1).contains("hit its length limit"));
    }

    @Test
    public void aOneLineOrderThatFillsItsCapIsNotAskedAgain() {
        final int[] calls = {0};
        RemoteBrain.Transport t = (url, h, body, timeoutMs) -> {
            calls[0]++;
            String stream = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi.\"}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\ndata: [DONE]\n";
            return new RemoteBrain.Response(200, new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)));
        };
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-4.1-mini", "k", "", t);
        assertEquals("Hi.", b.chat("s", ask("hi"), 48, 0.7f, null));
        assertEquals(1, calls[0]);
    }

    @Test
    public void anthropicMaxTokensIsCarriedOnToo() {
        final String[] parts = {
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Part one, \"}}\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"},\"usage\":{\"output_tokens\":5}}\n"
                        + "data: {\"type\":\"message_stop\"}\n",
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"part two.\"}}\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}\n"
                        + "data: {\"type\":\"message_stop\"}\n"};
        final int[] calls = {0};
        RemoteBrain.Transport t = (url, h, body, timeoutMs) ->
                new RemoteBrain.Response(200, new ByteArrayInputStream(parts[Math.min(calls[0]++, 1)].getBytes(StandardCharsets.UTF_8)));
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-sonnet-5-5", "k", "", t);
        assertEquals("Part one, part two.", b.chat("s", ask("write"), 1500, 0.7f, null));
        assertEquals(2, calls[0]);
    }

    /** Large thinking models behind any OpenAI form get room to think; a small plain model keeps its small allowance. */
    @Test
    public void largeThinkingModelsGetRoomAndSmallOnesStaySmall() {
        String[] big = {"google/gemini-2.5-pro", "x-ai/grok-4", "openai/gpt-oss-120b", "qwen/qwen3-235b-a22b",
                "z-ai/glm-4.6", "deepseek/deepseek-v3.1-terminus", "moonshotai/kimi-k2-thinking", "gemini-3-pro-preview"};
        for (String m : big) {
            RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", m, "k", "", null);
            assertTrue(m, b.thinks());
            String body = new String(b.body("sys", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8);
            assertTrue(m + " " + body, body.contains("\"max_tokens\":16384"));
        }
        for (String m : new String[]{"openai/gpt-4o-mini", "meta-llama/llama-3.1-8b-instruct", "mistralai/mistral-small"}) {
            RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", m, "k", "", null);
            assertFalse(m, b.thinks());
            assertTrue(m, new String(b.body("sys", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8).contains("\"max_tokens\":48"));
        }
        RemoteBrain off = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", "qwen/qwen3-32b", "k", "", null)
                .options("auto", "off", 0, 0, "");
        assertFalse("the user turned reasoning off", off.thinks());
    }

    /** All of the allowance went on thinking and nothing was written: asked once more with room, not a failure. */
    @Test
    public void anAnswerLostToThinkingIsAskedAgainWithRoom() {
        Fake f = new Fake().reply(200, "data: {\"choices\":[{\"delta\":{\"reasoning\":\"hmm\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n")
                .reply(200, OPENAI_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "k", "", f);
        String said = b.chat("sys", ask("hi"), 100, 0.7f, null);
        assertEquals(b.problem() + " " + f.bodies, "Hello there.", said);
        assertEquals(2, f.bodies.size());
        assertTrue(f.bodies.get(1), f.bodies.get(1).contains("\"max_tokens\":4096"));
    }

    /** A large model may think for minutes before its first word: the connection waits ten minutes by default. */
    @Test
    public void theConnectionWaitsForASlowThinker() {
        final int[] waited = {0};
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://x.ai/v1", "grok-4", "k", "", (url, h, body, t) -> {
            waited[0] = t;
            return new RemoteBrain.Response(200, new ByteArrayInputStream(OPENAI_STREAM.getBytes(StandardCharsets.UTF_8)));
        });
        b.chat("sys", ask("hi"), 100, 0.7f, null);
        assertEquals(600_000, waited[0]);
        b.options("auto", "auto", 0, 180, "");
        b.chat("sys", ask("hi"), 100, 0.7f, null);
        assertEquals("a value the user typed is kept", 180_000, waited[0]);
    }

    /** Claude Opus 5.5 always thinks: a short-answer max_tokens came back empty. Claude in an OpenAI form takes no temperature. */
    @Test
    public void claudeOpusThatAlwaysThinksGetsRoomAndNoTemperature() {
        for (String m : new String[]{"claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5", "claude-fable-5-1", "claude-haiku-5-5"}) {
            RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", m, "k", "", null);
            String body = new String(b.body("sys", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8);
            assertTrue(m + " " + body, body.contains("\"max_tokens\":16384"));
            assertFalse(m, body.contains("temperature"));
        }
        RemoteBrain old = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-4-6", "k", "", null);
        assertTrue("thinks only when asked: keeps the short allowance",
                new String(old.body("sys", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8).contains("\"max_tokens\":48"));
        // a custom OpenAI-form endpoint serving Claude (OpenRouter, a proxy)
        RemoteBrain custom = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://openrouter.ai/api/v1", "anthropic/claude-opus-5.5", "k", "", null);
        String body = new String(custom.body("sys", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8);
        assertTrue(body, body.contains("\"max_tokens\":16384"));
        assertFalse("Opus 4.7 and later refuse temperature", body.contains("temperature"));
        RemoteBrain gpt = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-4.1", "k", "", null);
        assertTrue(new String(gpt.body("sys", ask("hi"), 48, 0.7f, 0), StandardCharsets.UTF_8).contains("temperature"));
    }

    /** A custom gateway in Anthropic's format: named by its address, or found by a "not found" on the OpenAI path. */
    @Test
    public void aCustomGatewayInAnthropicsFormatIsFound() {
        assertEquals(RemoteBrain.Protocol.ANTHROPIC, RemoteBrain.formatOf("https://relay.example.com/v1/messages"));
        assertEquals(RemoteBrain.Protocol.ANTHROPIC, RemoteBrain.formatOf("https://gw.example.com/anthropic/v1"));
        assertEquals(RemoteBrain.Protocol.OPENAI, RemoteBrain.formatOf("https://openrouter.ai/api/v1"));

        // Auto, the address says nothing: the OpenAI path is not there, so the Anthropic one is asked, and remembered
        Fake f = new Fake().reply(404, "{\"error\":{\"message\":\"Not Found\"}}").reply(200, ANTHROPIC_STREAM)
                .reply(200, ANTHROPIC_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://claude-relay.example.com/v1", "claude-opus-5-5", "sk-relay", "", f)
                .autoFormat(true);
        assertNotNull(b.chat("sys", ask("hi"), 100, 0.7f, null));
        assertEquals("https://claude-relay.example.com/v1/chat/completions", f.urls.get(0));
        assertEquals("https://claude-relay.example.com/v1/messages", f.urls.get(1));
        assertEquals("a relay reads the key as a bearer token", "Bearer sk-relay", f.headers.get(1).get("Authorization"));
        assertEquals("sk-relay", f.headers.get(1).get("x-api-key"));
        assertTrue(f.bodies.get(1), f.bodies.get(1).contains("\"max_tokens\":16384"));
        RemoteBrain again = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://claude-relay.example.com/v1", "claude-opus-5-5",
                "sk-relay", "", f).autoFormat(true);
        assertEquals(RemoteBrain.Protocol.ANTHROPIC, again.protocol);
        again.chat("sys", ask("hi"), 100, 0.7f, null);
        assertEquals("the next request goes straight to the format that worked", 3, f.urls.size());

        // Anthropic itself gets no bearer header (it would read it as a different kind of sign-in)
        Fake direct = new Fake().reply(200, ANTHROPIC_STREAM);
        new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "sk-ant", "", direct)
                .chat("sys", ask("hi"), 100, 0.7f, null);
        assertNull(direct.headers.get(0).get("Authorization"));

        // a fixed format never switches
        Fake fixed = new Fake().reply(404, "{}");
        assertNull(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://other.example.com/v1", "claude-opus-5-5", "k", "", fixed)
                .chat("sys", ask("hi"), 100, 0.7f, null));
        assertEquals(1, fixed.urls.size());
    }

    @Test(timeout = 20000)
    public void aStoppedCallStopsCountingAsWorkAtOnce() throws Exception {
        LocalModel m = new LocalModel(java.nio.file.Files.createTempDirectory("models").toFile(), ModelSpec.QWEN3_1_7B, true);
        final java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        m.useRemote(new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", "k", "",
                (url, headers, body, timeoutMs) -> {
                    inside.countDown();
                    try {
                        release.await(); // a network read that is slow to give up
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    throw new java.io.IOException("gone");
                }), 400_000, () -> true);
        Thread t = new Thread(() -> m.reply("sys", "hi", 48, null));
        t.start();
        assertTrue(inside.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue("the call is running", m.working());
        m.cancel();
        assertFalse("Stop: the screen treats it as over at once", m.working());
        release.countDown();
        t.join(10000);
        assertFalse(m.working());
    }

    @Test
    public void qwen4ThinksAsHardAsTheMessageNeedsOnItsOwnScale() throws Exception {
        RemoteBrain q = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
                "qwen4-max-preview", "k", "", new Fake());
        assertTrue("Qwen4 is a thinking model", q.reasoning());
        java.util.List<String[]> turns = ask("hi");
        q.effortFor(TaskSize.Tier.FAST);
        String quick = new String(q.body("s", turns, 2048, 0.7f, 2), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(quick, quick.contains("\"reasoning_effort\":\"low\""));
        q.effortFor(TaskSize.Tier.STRONG);
        String work = new String(q.body("s", turns, 2048, 0.7f, 2), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("its top level is xhigh, not high: " + work, work.contains("\"reasoning_effort\":\"xhigh\""));
    }
}
