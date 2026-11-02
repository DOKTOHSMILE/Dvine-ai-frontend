package ai.dvine.llm;

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

import ai.dvine.brain.MiniJson;

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
        // a reasoning model: room for its hidden thinking on the first request, so it never comes back empty
        assertEquals(8000.0, MiniJson.num(req.get("max_completion_tokens"), 0), 0);
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
        assertEquals(120, b.used().promptTokens);
        assertEquals(9, b.used().outputTokens);
    }

    @Test
    public void aClaudeModelWithoutTheFallbackIsAskedPlainly() {
        Fake f = new Fake().reply(400, "{\"type\":\"error\",\"error\":{\"message\":\"effort not supported\"}}").reply(200, ANTHROPIC_STREAM);
        RemoteBrain b = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-haiku-4-5", "k", "", f);
        assertNotNull(b.chat("s", ask("hi"), 48, 0.7f, null));
        assertFalse(f.bodies.get(0).contains("fallbacks"));
        assertFalse(f.bodies.get(1).contains("output_config"));
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
        assertTrue(body, body.contains("\"max_completion_tokens\":8000") && body.contains("\"reasoning_effort\":\"low\""));
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
        assertEquals("Hello there.", ds.chat("s", ask("What is this?\n\n" + ai.dvine.chat.Attachments.image(img.getPath())), 48, 0.7f, null));
        assertTrue(f.bodies.get(0), f.bodies.get(0).contains("image_url") && f.bodies.get(0).contains("AQID"));
        assertFalse(f.bodies.get(1).contains("image_url"));
        assertTrue(f.bodies.get(1), f.bodies.get(1).contains("this model can't see images"));
        // remembered: the next image goes as a note straight away
        Fake g = new Fake().reply(200, OPENAI_STREAM);
        RemoteBrain again = new RemoteBrain(RemoteBrain.Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat", "k", "", g);
        again.chat("s", ask("And this?\n\n" + ai.dvine.chat.Attachments.image(img.getPath())), 48, 0.7f, null);
        assertEquals(1, g.bodies.size());
        assertFalse(g.bodies.get(0).contains("image_url"));
        // a model that sees gets the image as an image (Anthropic form)
        Fake h = new Fake().reply(200, "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"A cat.\"}}\n\n");
        RemoteBrain claude = new RemoteBrain(RemoteBrain.Protocol.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5", "k", "", h);
        claude.chat("s", ask("What is this?\n\n" + ai.dvine.chat.Attachments.image(img.getPath())), 48, 0.7f, null);
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
}
