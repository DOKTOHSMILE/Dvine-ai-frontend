package ai.dvine.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.dvine.llm.BrainOrder;

/** Connectors: GitHub, Notion and an MCP server against a fake network, and the brain's tool order. */
public class ConnectorsTest {
    private static final class Fake implements Connectors.Http {
        final List<String> urls = new ArrayList<String>(), bodies = new ArrayList<String>();
        final List<Map<String, String>> headers = new ArrayList<Map<String, String>>();
        final List<Connectors.Reply> replies = new ArrayList<Connectors.Reply>();

        Fake reply(int status, String body, String... header) {
            Map<String, String> h = new LinkedHashMap<String, String>();
            for (int i = 0; i + 1 < header.length; i += 2) {
                h.put(header[i], header[i + 1]);
            }
            replies.add(new Connectors.Reply(status, body, h));
            return this;
        }

        @Override
        public Connectors.Reply send(String method, String url, Map<String, String> h, byte[] body) {
            urls.add(method + " " + url);
            headers.add(h);
            bodies.add(body == null ? "" : new String(body, StandardCharsets.UTF_8));
            return replies.remove(0);
        }
    }

    @Test
    public void readsTheBrainsToolOrder() {
        String[] c = Connectors.parseCall("github.issues DOKTOHSMILE/Dvine-ai-frontend");
        assertEquals("github.issues", c[0]);
        assertEquals("DOKTOHSMILE/Dvine-ai-frontend", c[1]);
        assertEquals("", Connectors.parseCall("calendar.week")[1]);
        assertNull(Connectors.parseCall("  "));
        BrainOrder o = BrainOrder.read("DO TOOL notion.search launch plan");
        assertEquals(BrainOrder.Kind.TOOL, o.kind);
        assertEquals("notion.search launch plan", o.arg);
        String prompt = BrainOrder.prompt(Connectors.describe(Connectors.githubTools()));
        assertTrue(prompt.contains("DO TOOL"));
        assertTrue(prompt.contains("- github.issues <owner/repo>: the open issues of a repository"));
        assertTrue(prompt.endsWith("/no_think"));
        assertTrue(!BrainOrder.prompt("").contains("DO TOOL")); // no connectors on: the brain isn't told about tools
    }

    @Test
    public void gitHubIssuesAndRepos() throws IOException {
        Fake f = new Fake().reply(200, "[{\"number\":12,\"title\":\"Crash on start\"},{\"number\":13,\"title\":\"A pull\",\"pull_request\":{}}]")
                .reply(200, "[{\"full_name\":\"me/app\",\"private\":true,\"description\":\"My app\"}]");
        String issues = Connectors.github(f, "ghp_x", "github.issues", "https://github.com/me/app");
        assertEquals("- #12 Crash on start", issues.trim()); // a pull request isn't an issue
        assertEquals("GET https://api.github.com/repos/me/app/issues?state=open&per_page=15", f.urls.get(0));
        assertEquals("Bearer ghp_x", f.headers.get(0).get("Authorization"));
        assertTrue(Connectors.github(f, "ghp_x", "github.repos", "").contains("me/app (private): My app"));
        assertEquals("Say which repository, as owner/repo.", Connectors.github(new Fake(), "t", "github.issues", "not a repo"));
        try {
            Connectors.github(new Fake().reply(401, "{}"), "bad", "github.repos", "");
            assertTrue(false);
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("refused the token"));
        }
    }

    @Test
    public void notionSearch() throws IOException {
        Fake f = new Fake().reply(200, "{\"results\":[{\"url\":\"https://notion.so/p1\",\"last_edited_time\":\"2026-10-30T10:00:00Z\","
                + "\"properties\":{\"Name\":{\"type\":\"title\",\"title\":[{\"plain_text\":\"Launch plan\"}]}}}]}");
        String out = Connectors.notion(f, "secret_x", "notion.search", "launch");
        assertTrue(out, out.contains("Launch plan (https://notion.so/p1, edited 2026-10-30)"));
        assertTrue(f.bodies.get(0).contains("\"query\":\"launch\""));
        assertEquals("2022-06-28", f.headers.get(0).get("Notion-Version"));
    }

    @Test
    public void anMcpServerOverJsonAndEvents() throws IOException {
        Fake f = new Fake()
                .reply(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}", "Mcp-Session-Id", "s-42")
                .reply(202, "")
                .reply(200, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"add_task\","
                        + "\"description\":\"Adds a task\"},{\"name\":\"list_tasks\",\"description\":\"Lists tasks\"}]}}\n\n");
        Connectors.Mcp mcp = new Connectors.Mcp(f, "https://tasks.example/mcp", "tok");
        List<Connectors.Tool> tools = mcp.tools("tasks.");
        assertEquals(2, tools.size());
        assertEquals("tasks.add_task", tools.get(0).name);
        assertTrue(f.bodies.get(0).contains("\"method\":\"initialize\""));
        assertTrue(f.bodies.get(1).contains("notifications/initialized"));
        assertEquals("s-42", f.headers.get(2).get("Mcp-Session-Id")); // the session is kept
        assertEquals("Bearer tok", f.headers.get(2).get("Authorization"));

        f.reply(200, "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Added: buy milk\"}]}}");
        assertEquals("Added: buy milk", mcp.call("add_task", "{\"title\":\"buy milk\"}"));
        assertTrue(f.bodies.get(3).contains("\"arguments\":{\"title\":\"buy milk\"}"));
        f.reply(200, "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"3 tasks\"}]}}");
        mcp.call("list_tasks", "open ones");
        assertTrue(f.bodies.get(4).contains("\"arguments\":{\"query\":\"open ones\"}")); // plain words become a query
        f.reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"error\":{\"code\":-32602,\"message\":\"Unknown tool\"}}");
        try {
            mcp.call("nope", "");
            assertTrue(false);
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Unknown tool"));
        }
    }
}
