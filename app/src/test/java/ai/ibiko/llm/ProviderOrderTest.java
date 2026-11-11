package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The switching order: a primary and up to two backups. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ProviderOrderTest {
    @Test
    public void primaryThenBackupsAtMostThree() {
        ProviderStore s = new ProviderStore(RuntimeEnvironment.getApplication());
        assertEquals(Providers.ON_PHONE, s.active());
        assertTrue(s.order().isEmpty());
        s.setActive(Providers.ANTHROPIC);
        assertTrue(s.addBackup(Providers.OPENAI));
        assertTrue(s.addBackup(Providers.GEMINI));
        assertFalse(s.addBackup(Providers.OPENROUTER)); // three is the most
        assertEquals(Arrays.asList(Providers.ANTHROPIC, Providers.OPENAI, Providers.GEMINI), s.order());
        assertEquals(2, s.place(Providers.GEMINI));
        s.setActive(Providers.GEMINI); // a backup made primary moves to the front
        assertEquals(Arrays.asList(Providers.GEMINI, Providers.ANTHROPIC, Providers.OPENAI), s.order());
        s.removeBackup(Providers.ANTHROPIC);
        assertEquals(Arrays.asList(Providers.GEMINI, Providers.OPENAI), s.order());
        s.clear(Providers.GEMINI); // forgetting the primary hands the lead on
        assertEquals(Providers.ON_PHONE, s.active());
        assertEquals(Arrays.asList(Providers.OPENAI), s.order());
        assertTrue(s.autoSwitch());
        s.setAutoSwitch(false);
        assertFalse(s.autoSwitch());
    }

    @Test
    public void theIbikoServerSignsInWithThePhonesSessionNotAKey() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).edit().putString("server", "https://ibiko.example.org").apply();
        ProviderStore s = new ProviderStore(c);
        java.util.function.Supplier<String> was = ProviderStore.serverSession;
        try {
            ProviderStore.serverSession = null;
            assertEquals("https://ibiko.example.org/api/v1/llm", s.address(Providers.IBIKO_SERVER)); // the server the app uses
            assertEquals("qwen3-14b", s.model(Providers.IBIKO_SERVER));
            assertFalse(s.hasKey(Providers.IBIKO_SERVER));
            assertEquals(null, s.brain(Providers.IBIKO_SERVER)); // not signed in yet: not usable
            final String[] token = {"tok-1"};
            ProviderStore.serverSession = () -> token[0];
            assertTrue(s.hasKey(Providers.IBIKO_SERVER));
            RemoteBrain b = s.brain(Providers.IBIKO_SERVER);
            assertTrue(b != null);
            // each request asks for the live session: a renewed token is used at once
            final java.util.List<String> auth = new java.util.ArrayList<String>();
            RemoteBrain.Transport t = (url, h, body, timeoutMs) -> {
                auth.add(url + " " + h.get("Authorization"));
                return new RemoteBrain.Response(200, new java.io.ByteArrayInputStream(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Hi.\"}}]}\ndata: [DONE]\n".getBytes("UTF-8")));
            };
            RemoteBrain live = new RemoteBrain(RemoteBrain.Protocol.OPENAI, s.address(Providers.IBIKO_SERVER), "qwen3-14b",
                    s.key(Providers.IBIKO_SERVER), "", t).keySource(ProviderStore.serverSession);
            java.util.List<String[]> ask = new java.util.ArrayList<String[]>();
            ask.add(new String[]{"user", "hi"});
            assertEquals("Hi.", live.chat("s", ask, 48, 0.7f, null));
            token[0] = "tok-2";
            assertEquals("Hi.", live.chat("s", ask, 48, 0.7f, null));
            assertEquals(java.util.Arrays.asList("https://ibiko.example.org/api/v1/llm/chat/completions Bearer tok-1",
                    "https://ibiko.example.org/api/v1/llm/chat/completions Bearer tok-2"), auth);
            token[0] = null; // signed out: a clear reason, no request
            long[] waits = RemoteBrain.retryWaits;
            RemoteBrain.retryWaits = new long[]{0, 0};
            try {
                assertEquals(null, live.chat("s", ask, 48, 0.7f, null));
            } finally {
                RemoteBrain.retryWaits = waits;
            }
            assertEquals("the device couldn't sign in to the Ibiko server", live.problem());
            assertEquals(2, auth.size());
        } finally {
            ProviderStore.serverSession = was;
        }
    }
}
