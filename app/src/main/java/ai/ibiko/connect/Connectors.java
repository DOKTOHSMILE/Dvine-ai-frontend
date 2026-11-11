package ai.ibiko.connect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.ibiko.brain.MiniJson;

/**
 * Connectors: services the brain can use to answer, as in Claude. Each offers tools by name ("github.issues"), the
 * brain asks for one in its one-line order ("DO TOOL github.issues owner/repo"), Ibiko runs it, and the brain
 * writes the answer from what came back. Built in: GitHub (a personal access token), Notion (an integration token),
 * and any MCP server (the Model Context Protocol, over https, with an optional token). The phone's calendar and web
 * search are added by the app, which holds the Android parts. A tool's result is data for the brain, never
 * instructions to Ibiko. Pure Java; the network is behind {@link Http} so tests run without one.
 */
public final class Connectors {
    private Connectors() {
    }

    /** One tool the brain may use. */
    public static final class Tool {
        public final String name, about, argument;

        public Tool(String name, String about, String argument) {
            this.name = name;
            this.about = about;
            this.argument = argument == null ? "" : argument;
        }

        /** "github.issues <owner/repo>: the open issues of a repository". */
        public String line() {
            return name + (argument.isEmpty() ? "" : " <" + argument + ">") + ": " + about;
        }
    }

    /** A request out, the status and body back. */
    public interface Http {
        Reply send(String method, String url, Map<String, String> headers, byte[] body) throws IOException;
    }

    public static final class Reply {
        public final int status;
        public final String body;
        public final Map<String, String> headers;

        public Reply(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body == null ? "" : body;
            this.headers = headers == null ? new LinkedHashMap<String, String>() : headers;
        }

        String header(String name) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }

    /** The most a result gives the brain (characters), so it fits beside the question. */
    public static final int RESULT_CHARS = 3500;

    /** "DO TOOL github.issues owner/repo" read into the tool's name and its argument; null when it isn't one. */
    public static String[] parseCall(String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            return null;
        }
        String t = arg.trim();
        int sp = t.indexOf(' ');
        String name = (sp < 0 ? t : t.substring(0, sp)).toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]", "");
        return name.isEmpty() ? null : new String[]{name, sp < 0 ? "" : t.substring(sp + 1).trim()};
    }

    /** The tools as lines for the brain's instructions. */
    public static String describe(List<Tool> tools) {
        StringBuilder b = new StringBuilder();
        for (Tool t : tools) {
            b.append("\n- ").append(t.line());
        }
        return b.toString();
    }

    static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() <= RESULT_CHARS ? t : t.substring(0, RESULT_CHARS) + "\n…(cut short)";
    }

    // ------------------------------------------------------------ GitHub

    public static List<Tool> githubTools() {
        List<Tool> t = new ArrayList<Tool>();
        t.add(new Tool("github.repos", "every repository this account can push and pull (own, organisation and collaborator), 100 per page", "page number, or empty for the first"));
        t.add(new Tool("github.search", "search public repositories", "words"));
        t.add(new Tool("github.issues", "the open issues of a repository", "owner/repo"));
        t.add(new Tool("github.file", "read a file from a repository", "owner/repo/path"));
        return t;
    }

    public static String github(Http http, String token, String tool, String arg) throws IOException {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Accept", "application/vnd.github+json");
        h.put("X-GitHub-Api-Version", "2022-11-28");
        h.put("User-Agent", "Ibiko");
        if (token != null && !token.isEmpty()) {
            h.put("Authorization", "Bearer " + token);
        }
        String base = "https://api.github.com";
        StringBuilder out = new StringBuilder();
        switch (tool) {
            case "github.repos": {
                if (token != null && !token.isEmpty()) {
                    try {
                        out.append(GitHubLogin.access(http, token)).append("\n");
                    } catch (IOException e) {
                        if (String.valueOf(e.getMessage()).contains("refused the token")) {
                            throw e; // a refused token lists nothing: say so once, without a second call
                        }
                        out.append(e.getMessage()).append("\n");
                    }
                } else {
                    out.append("Not signed in: public repositories can be cloned, but push and pull of private ones needs Connectors \u203A GitHub.\n");
                }
                int page = 1;
                String pageArg = arg == null ? "" : arg.trim();
                try {
                    if (!pageArg.isEmpty()) {
                        page = Math.max(1, Integer.parseInt(pageArg));
                    }
                } catch (NumberFormatException ignored) {
                    page = 1;
                }
                String url = base + "/user/repos?sort=pushed&per_page=100&page=" + page
                        + "&affiliation=owner,collaborator,organization_member";
                Reply r = ok(http.send("GET", url, h, null), "GitHub");
                int n = 0;
                for (Object o : MiniJson.list(MiniJson.parse(r.body))) {
                    Map<String, Object> m = MiniJson.obj(o);
                    boolean priv = m.get("private") == Boolean.TRUE;
                    boolean push = MiniJson.obj(m.get("permissions")).get("push") == Boolean.TRUE;
                    out.append("- ").append(MiniJson.str(m.get("full_name"), ""))
                            .append(priv ? " (private)" : " (public)")
                            .append(push ? ", push" : ", read")
                            .append(": ").append(MiniJson.str(m.get("description"), "no description")).append('\n');
                    n++;
                }
                if (n == 0) {
                    out.append(page == 1 ? "No repositories on this account.\n" : "No more repositories on page " + page + ".\n");
                } else if (n == 100) {
                    out.append("Page ").append(page).append(" has 100 repositories. Ask github.repos ").append(page + 1).append(" for the next page.\n");
                } else {
                    out.append(n).append(" repositor").append(n == 1 ? "y" : "ies").append(" on page ").append(page).append(".\n");
                }
                break;
            }
            case "github.search": {
                Reply r = ok(http.send("GET", base + "/search/repositories?per_page=8&q=" + enc(arg), h, null), "GitHub");
                for (Object o : MiniJson.list(MiniJson.obj(MiniJson.parse(r.body)).get("items"))) {
                    Map<String, Object> m = MiniJson.obj(o);
                    out.append("- ").append(MiniJson.str(m.get("full_name"), "")).append(" (").append((long) MiniJson.num(m.get("stargazers_count"), 0))
                            .append(" stars): ").append(MiniJson.str(m.get("description"), "")).append('\n');
                }
                break;
            }
            case "github.issues": {
                String repo = arg.replaceAll("^https?://github.com/", "").replaceAll("/+$", "");
                if (!repo.matches("[\\w.-]+/[\\w.-]+")) {
                    return "Say which repository, as owner/repo.";
                }
                Reply r = ok(http.send("GET", base + "/repos/" + repo + "/issues?state=open&per_page=15", h, null), "GitHub");
                for (Object o : MiniJson.list(MiniJson.parse(r.body))) {
                    Map<String, Object> m = MiniJson.obj(o);
                    if (m.containsKey("pull_request")) {
                        continue;
                    }
                    out.append("- #").append((long) MiniJson.num(m.get("number"), 0)).append(' ').append(MiniJson.str(m.get("title"), "")).append('\n');
                }
                if (out.length() == 0) {
                    out.append("No open issues in ").append(repo).append('.');
                }
                break;
            }
            case "github.file": {
                java.util.regex.Matcher f = java.util.regex.Pattern.compile("^([\\w.-]+/[\\w.-]+)/(.+)$").matcher(arg.replaceAll("^https?://github.com/", ""));
                if (!f.matches()) {
                    return "Say which file, as owner/repo/path.";
                }
                Map<String, String> raw = new LinkedHashMap<String, String>(h);
                raw.put("Accept", "application/vnd.github.raw+json");
                Reply r = ok(http.send("GET", base + "/repos/" + f.group(1) + "/contents/" + f.group(2).replace(" ", "%20"), raw, null), "GitHub");
                out.append(r.body);
                break;
            }
            default:
                return "GitHub has no tool called " + tool + ".";
        }
        String listed = out.length() == 0 ? "Nothing found." : out.toString();
        int cap = "github.file".equals(tool) || "github.repos".equals(tool) ? 100_000 : RESULT_CHARS;
        return clip(listed, cap);
    }

    static String clip(String s, int n) {
        String t = s == null ? "" : s.trim();
        return t.length() <= n ? t : t.substring(0, n) + "\n…(cut short)";
    }

    // ------------------------------------------------------------ Notion

    public static List<Tool> notionTools() {
        List<Tool> t = new ArrayList<Tool>();
        t.add(new Tool("notion.search", "search the pages shared with Ibiko's Notion integration", "words"));
        return t;
    }

    public static String notion(Http http, String token, String tool, String arg) throws IOException {
        if (!"notion.search".equals(tool)) {
            return "Notion has no tool called " + tool + ".";
        }
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Authorization", "Bearer " + token);
        h.put("Notion-Version", "2022-06-28");
        h.put("Content-Type", "application/json");
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("query", arg);
        q.put("page_size", 10.0);
        Reply r = ok(http.send("POST", "https://api.notion.com/v1/search", h, MiniJson.write(q).getBytes(StandardCharsets.UTF_8)), "Notion");
        StringBuilder out = new StringBuilder();
        for (Object o : MiniJson.list(MiniJson.obj(MiniJson.parse(r.body)).get("results"))) {
            Map<String, Object> m = MiniJson.obj(o);
            out.append("- ").append(notionTitle(m)).append(" (").append(MiniJson.str(m.get("url"), "")).append(", edited ")
                    .append(MiniJson.str(m.get("last_edited_time"), "").replaceAll("T.*$", "")).append(")\n");
        }
        return clip(out.length() == 0 ? "No pages matched. Pages must be shared with the integration in Notion." : out.toString());
    }

    private static String notionTitle(Map<String, Object> page) {
        for (Object v : MiniJson.obj(page.get("properties")).values()) {
            Map<String, Object> prop = MiniJson.obj(v);
            if ("title".equals(MiniJson.str(prop.get("type"), ""))) {
                StringBuilder t = new StringBuilder();
                for (Object part : MiniJson.list(prop.get("title"))) {
                    t.append(MiniJson.str(MiniJson.obj(part).get("plain_text"), ""));
                }
                if (t.length() > 0) {
                    return t.toString();
                }
            }
        }
        StringBuilder t = new StringBuilder(); // a database's title
        for (Object part : MiniJson.list(page.get("title"))) {
            t.append(MiniJson.str(MiniJson.obj(part).get("plain_text"), ""));
        }
        return t.length() == 0 ? "Untitled" : t.toString();
    }

    // ------------------------------------------------------------ MCP

    /**
     * A Model Context Protocol server over Streamable HTTP: initialize, then list or call its tools. The reply may
     * be JSON or server-sent events; the session id the server gives is sent back on every later request.
     */
    public static final class Mcp {
        private final Http http;
        private final String url, token;
        private String session;
        private int id;

        public Mcp(Http http, String url, String token) {
            this.http = http;
            this.url = url;
            this.token = token == null ? "" : token.trim();
        }

        private Map<String, Object> rpc(String method, Object params, boolean notification) throws IOException {
            Map<String, Object> req = new LinkedHashMap<String, Object>();
            req.put("jsonrpc", "2.0");
            if (!notification) {
                req.put("id", (double) ++id);
            }
            req.put("method", method);
            if (params != null) {
                req.put("params", params);
            }
            Map<String, String> h = new LinkedHashMap<String, String>();
            h.put("Content-Type", "application/json");
            h.put("Accept", "application/json, text/event-stream");
            h.put("MCP-Protocol-Version", "2025-06-18");
            if (!token.isEmpty()) {
                h.put("Authorization", "Bearer " + token);
            }
            if (session != null) {
                h.put("Mcp-Session-Id", session);
            }
            Reply r = http.send("POST", url, h, MiniJson.write(req).getBytes(StandardCharsets.UTF_8));
            String sid = r.header("Mcp-Session-Id");
            if (sid != null) {
                session = sid;
            }
            if (notification) {
                return null;
            }
            if (r.status == 401 || r.status == 403) {
                throw new IOException("the MCP server refused the token (" + r.status + ")");
            }
            if (r.status < 200 || r.status >= 300) {
                throw new IOException("the MCP server said " + r.status);
            }
            Map<String, Object> msg = message(r.body);
            Map<String, Object> err = MiniJson.obj(msg.get("error"));
            if (!err.isEmpty()) {
                throw new IOException("the MCP server: " + MiniJson.str(err.get("message"), "an error"));
            }
            return MiniJson.obj(msg.get("result"));
        }

        /** The JSON-RPC reply in a body that is plain JSON, or the last data event of a stream. */
        static Map<String, Object> message(String body) throws IOException {
            String b = body.trim();
            if (b.startsWith("{")) {
                return MiniJson.obj(MiniJson.parse(b));
            }
            String last = null;
            for (String line : b.split("\n")) {
                if (line.startsWith("data:")) {
                    String d = line.substring(5).trim();
                    if (d.startsWith("{") && d.contains("\"id\"")) {
                        last = d;
                    }
                }
            }
            if (last == null) {
                throw new IOException("the MCP server's reply wasn't readable");
            }
            return MiniJson.obj(MiniJson.parse(last));
        }

        private void open() throws IOException {
            if (session != null || id > 0) {
                return;
            }
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put("protocolVersion", "2025-06-18");
            p.put("capabilities", new LinkedHashMap<String, Object>());
            Map<String, Object> client = new LinkedHashMap<String, Object>();
            client.put("name", "Ibiko");
            client.put("version", "1");
            p.put("clientInfo", client);
            rpc("initialize", p, false);
            rpc("notifications/initialized", null, true);
        }

        /** The server's tools: name and description. */
        public List<Tool> tools(String prefix) throws IOException {
            open();
            List<Tool> out = new ArrayList<Tool>();
            for (Object o : MiniJson.list(rpc("tools/list", new LinkedHashMap<String, Object>(), false).get("tools"))) {
                Map<String, Object> t = MiniJson.obj(o);
                String name = MiniJson.str(t.get("name"), "");
                if (!name.isEmpty()) {
                    String about = MiniJson.str(t.get("description"), "").replaceAll("\\s+", " ");
                    out.add(new Tool(prefix + name, about.length() > 140 ? about.substring(0, 140) + "…" : about, "JSON arguments"));
                }
            }
            return out;
        }

        /** Calls a tool with its arguments (a JSON object, or plain words given as {"query": …}); the text it returned. */
        public String call(String tool, String argument) throws IOException {
            open();
            Object args;
            String a = argument == null ? "" : argument.trim();
            try {
                args = a.startsWith("{") ? MiniJson.parse(a) : null;
            } catch (RuntimeException e) {
                args = null;
            }
            if (args == null) {
                Map<String, Object> q = new LinkedHashMap<String, Object>();
                if (!a.isEmpty()) {
                    q.put("query", a);
                }
                args = q;
            }
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put("name", tool);
            p.put("arguments", args);
            Map<String, Object> res = rpc("tools/call", p, false);
            StringBuilder out = new StringBuilder();
            for (Object c : MiniJson.list(res.get("content"))) {
                Map<String, Object> m = MiniJson.obj(c);
                if ("text".equals(MiniJson.str(m.get("type"), ""))) {
                    out.append(MiniJson.str(m.get("text"), "")).append('\n');
                }
            }
            if (Boolean.TRUE.equals(res.get("isError"))) {
                return "The tool reported an error: " + clip(out.toString());
            }
            return clip(out.length() == 0 ? "The tool returned nothing to read." : out.toString());
        }
    }

    // ------------------------------------------------------------ helpers

    private static Reply ok(Reply r, String who) throws IOException {
        if (r.status == 401 || r.status == 403) {
            throw new IOException(who + " refused the token (" + r.status + ")");
        }
        if (r.status == 404) {
            throw new IOException(who + " didn't find that (404)");
        }
        if (r.status < 200 || r.status >= 300) {
            throw new IOException(who + " said " + r.status);
        }
        return r;
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    /** The real network: https only, 15 seconds to connect, 30 to answer, at most 2 MB back. */
    public static Http https() {
        return (method, url, headers, body) -> {
            java.net.URL u = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(u.getProtocol())) {
                throw new IOException("only https is allowed");
            }
            javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) u.openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            for (Map.Entry<String, String> h : headers.entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream o = c.getOutputStream()) {
                    o.write(body);
                }
            }
            int status = c.getResponseCode();
            Map<String, String> got = new LinkedHashMap<String, String>();
            for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty()) {
                    got.put(e.getKey(), e.getValue().get(0));
                }
            }
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) {
                try (InputStream i = in) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = i.read(b)) > 0 && buf.size() < 2_000_000) {
                        buf.write(b, 0, n);
                    }
                }
            }
            return new Reply(status, new String(buf.toByteArray(), StandardCharsets.UTF_8), got);
        };
    }
}
