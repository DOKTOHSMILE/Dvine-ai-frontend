package ai.dvine.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** GitHub's device sign-in: the code, waiting for approval, slowing down, and a refusal. */
public class GitHubLoginTest {
    private static Connectors.Http replies(final List<String> sent, final String... bodies) {
        final int[] n = {0};
        return (method, url, headers, body) -> {
            sent.add(method + " " + url + " " + (body == null ? "" : new String(body, StandardCharsets.UTF_8)));
            return new Connectors.Reply(200, bodies[Math.min(n[0]++, bodies.length - 1)], null);
        };
    }

    @Test
    public void codeThenPendingThenToken() throws Exception {
        List<String> sent = new ArrayList<String>();
        GitHubLogin login = new GitHubLogin(replies(sent,
                "{\"device_code\":\"dev1\",\"user_code\":\"WDJB-MJHT\",\"verification_uri\":\"https://github.com/login/device\",\"expires_in\":900,\"interval\":5}",
                "{\"error\":\"authorization_pending\"}",
                "{\"error\":\"slow_down\",\"interval\":10}",
                "{\"access_token\":\"gho_abc\",\"token_type\":\"bearer\",\"scope\":\"repo,read:user\"}"), "Ov23liX");
        GitHubLogin.Code code = login.start();
        assertEquals("WDJB-MJHT", code.userCode);
        assertEquals(5, code.intervalSeconds);
        assertTrue(sent.get(0), sent.get(0).startsWith("POST https://github.com/login/device/code client_id=Ov23liX&scope=repo+read%3Auser"));
        GitHubLogin.Poll p = login.poll(code, 5);
        assertNull(p.token);
        assertEquals(5, p.waitSeconds);
        p = login.poll(code, 5);
        assertEquals(10, p.waitSeconds);
        p = login.poll(code, 10);
        assertEquals("gho_abc", p.token);
        assertTrue(sent.get(1).contains("device_code=dev1") && sent.get(1).contains("grant_type=urn%3Aietf"));
    }

    @Test
    public void refusalAndMissingClientIdSayWhy() throws Exception {
        try {
            new GitHubLogin(replies(new ArrayList<String>(), "{}"), "").start();
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("client ID"));
        }
        GitHubLogin login = new GitHubLogin(replies(new ArrayList<String>(),
                "{\"device_code\":\"d\",\"user_code\":\"U\",\"interval\":5,\"expires_in\":900}", "{\"error\":\"access_denied\"}"), "id");
        GitHubLogin.Code code = login.start();
        try {
            login.poll(code, 5);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cancelled"));
        }
        assertTrue(GitHubLogin.newTokenUrl().contains("scopes=repo"));
    }
}
