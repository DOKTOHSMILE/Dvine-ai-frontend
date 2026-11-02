package ai.dvine.connect;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ai.dvine.brain.MiniJson;
import ai.dvine.llm.ProviderStore;

/**
 * The connectors the user switched on, kept on this phone: web search, the phone's calendar, GitHub, Notion and a
 * custom MCP server. Tokens are encrypted like the API keys ({@link ProviderStore#seal}). {@link #tools} is what
 * the brain is told it may use; {@link #run} carries one out.
 */
public final class ConnectorStore {
    public enum Kind {
        WEB("Web search", "Wikipedia, Wikidata and DuckDuckGo, for facts and anything recent."),
        CALENDAR("Phone calendar", "Your events for the next week, read from this phone's calendar."),
        GITHUB("GitHub", "Your repositories, issues and files; Ibiko can clone, pull, commit, push and open pull requests. Sign in with GitHub in the browser, or paste a token."),
        NOTION("Notion", "Search the pages you shared with a Notion integration. Needs its token."),
        MCP("Custom MCP server", "Any server that speaks the Model Context Protocol, over https. Its tools are found on Test.");

        public final String title, about;

        Kind(String title, String about) {
            this.title = title;
            this.about = about;
        }
    }

    private final Context context;
    private final SharedPreferences prefs, app;

    public ConnectorStore(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences("dvine_connectors", Context.MODE_PRIVATE);
        app = context.getSharedPreferences("dvine", Context.MODE_PRIVATE);
    }

    public boolean on(Kind k) {
        if (k == Kind.WEB) {
            return app.getBoolean("fresh_facts", true); // the switch the app already had for looking things up
        }
        return prefs.getBoolean(k.name() + ".on", false);
    }

    public void setOn(Kind k, boolean on) {
        if (k == Kind.WEB) {
            app.edit().putBoolean("fresh_facts", on).apply();
        } else {
            prefs.edit().putBoolean(k.name() + ".on", on).apply();
        }
    }

    public String token(Kind k) {
        return ProviderStore.open(prefs.getString(k.name() + ".token", ""));
    }

    public void setToken(Kind k, String token) {
        prefs.edit().putString(k.name() + ".token", ProviderStore.seal(token == null ? "" : token.trim())).apply();
    }

    /** The client ID of the user's GitHub OAuth app, for browser sign-in (public, not a secret). */
    public String githubClientId() {
        return prefs.getString("GITHUB.client", "");
    }

    public void setGithubClientId(String id) {
        prefs.edit().putString("GITHUB.client", id == null ? "" : id.trim()).apply();
    }

    /** The GitHub username the token belongs to, when known. */
    public String githubUser() {
        return prefs.getString("GITHUB.user", "");
    }

    public void setGithubUser(String user) {
        prefs.edit().putString("GITHUB.user", user == null ? "" : user.trim()).apply();
    }

    public String mcpUrl() {
        return prefs.getString("MCP.url", "");
    }

    public String mcpName() {
        String n = prefs.getString("MCP.name", "");
        return n.isEmpty() ? "mcp" : n;
    }

    public void setMcp(String url, String name) {
        prefs.edit().putString("MCP.url", url == null ? "" : url.trim())
                .putString("MCP.name", name == null ? "" : name.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9_-]", "")).apply();
    }

    /** The MCP server's tools found on the last Test (kept so the brain knows them without asking every time). */
    public List<Connectors.Tool> mcpTools() {
        List<Connectors.Tool> out = new ArrayList<Connectors.Tool>();
        try {
            for (Object o : MiniJson.list(MiniJson.parse(prefs.getString("MCP.tools", "[]")))) {
                java.util.Map<String, Object> m = MiniJson.obj(o);
                out.add(new Connectors.Tool(MiniJson.str(m.get("name"), ""), MiniJson.str(m.get("about"), ""), "JSON arguments"));
            }
        } catch (RuntimeException e) {
            // nothing kept
        }
        return out;
    }

    void keepMcpTools(List<Connectors.Tool> tools) {
        List<Object> list = new ArrayList<Object>();
        for (Connectors.Tool t : tools) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("name", t.name);
            m.put("about", t.about);
            list.add(m);
        }
        prefs.edit().putString("MCP.tools", MiniJson.write(list)).apply();
    }

    /** Git with GitHub for Ibiko, or null when the GitHub connector isn't on and ready. */
    public GitHubGit git() {
        return ready(Kind.GITHUB) ? new GitHubGit(Connectors.https(), token(Kind.GITHUB)) : null;
    }

    public boolean calendarAllowed() {
        return context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Whether a connector can be used now: on, with its token or permission. */
    public boolean ready(Kind k) {
        if (!on(k)) {
            return false;
        }
        switch (k) {
            case CALENDAR: return calendarAllowed();
            case GITHUB:
            case NOTION: return !token(k).isEmpty();
            case MCP: return mcpUrl().startsWith("https://") && !mcpTools().isEmpty();
            default: return true;
        }
    }

    /** The tools the brain may use now. */
    public List<Connectors.Tool> tools() {
        List<Connectors.Tool> t = new ArrayList<Connectors.Tool>();
        if (ready(Kind.WEB)) {
            t.add(new Connectors.Tool("web.search", "look a fact up on the web (Wikipedia, Wikidata, DuckDuckGo)", "question"));
        }
        if (ready(Kind.CALENDAR)) {
            t.add(new Connectors.Tool("calendar.week", "the user's calendar events for the next 7 days", ""));
        }
        if (ready(Kind.GITHUB)) {
            t.addAll(Connectors.githubTools());
        }
        if (ready(Kind.NOTION)) {
            t.addAll(Connectors.notionTools());
        }
        if (ready(Kind.MCP)) {
            t.addAll(mcpTools());
        }
        return t;
    }

    /**
     * Runs a tool (blocking: call off the main thread); the text the brain gets back. {@code web} looks a question
     * up (the app's own cited lookup).
     */
    public String run(String tool, String arg, java.util.function.Function<String, String> web) {
        try {
            if (tool.equals("web.search")) {
                String found = web.apply(arg);
                return found == null || found.trim().isEmpty() ? "Nothing reliable was found." : Connectors.clip(found);
            }
            if (tool.equals("calendar.week")) {
                return calendarWeek();
            }
            if (tool.startsWith("github.") && ready(Kind.GITHUB)) {
                return Connectors.github(Connectors.https(), token(Kind.GITHUB), tool, arg);
            }
            if (tool.startsWith("notion.") && ready(Kind.NOTION)) {
                return Connectors.notion(Connectors.https(), token(Kind.NOTION), tool, arg);
            }
            String prefix = mcpName() + ".";
            if (tool.startsWith(prefix) && ready(Kind.MCP)) {
                return new Connectors.Mcp(Connectors.https(), mcpUrl(), token(Kind.MCP)).call(tool.substring(prefix.length()), arg);
            }
            return "No connector has a tool called " + tool + ".";
        } catch (java.io.IOException | RuntimeException e) {
            return "The tool didn't work: " + (e.getMessage() == null ? "an error" : e.getMessage()) + ".";
        }
    }

    /** Test: for an MCP server, finds and keeps its tools; for GitHub and Notion, one small call. Null when it works. */
    public String test(Kind k) {
        try {
            switch (k) {
                case GITHUB:
                    Connectors.github(Connectors.https(), token(k), "github.repos", "");
                    return null;
                case NOTION:
                    Connectors.notion(Connectors.https(), token(k), "notion.search", "");
                    return null;
                case MCP: {
                    if (!mcpUrl().startsWith("https://")) {
                        return "the address must start with https://";
                    }
                    List<Connectors.Tool> found = new Connectors.Mcp(Connectors.https(), mcpUrl(), token(k)).tools(mcpName() + ".");
                    keepMcpTools(found);
                    return found.isEmpty() ? "the server has no tools" : null;
                }
                case CALENDAR:
                    return calendarAllowed() ? null : "calendar permission isn't given";
                default:
                    return null;
            }
        } catch (java.io.IOException | RuntimeException e) {
            return e.getMessage() == null ? "it didn't answer" : e.getMessage();
        }
    }

    /** The next 7 days from the phone's calendars (the instances, so repeating events show). */
    private String calendarWeek() {
        if (!calendarAllowed()) {
            return "Calendar permission isn't given.";
        }
        long now = System.currentTimeMillis(), end = now + 7L * 86_400_000L;
        android.net.Uri.Builder b = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(b, now);
        android.content.ContentUris.appendId(b, end);
        String[] cols = {android.provider.CalendarContract.Instances.TITLE, android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.ALL_DAY, android.provider.CalendarContract.Instances.EVENT_LOCATION};
        StringBuilder out = new StringBuilder();
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("EEE d MMM", Locale.UK),
                time = new java.text.SimpleDateFormat("HH:mm", Locale.UK);
        try (android.database.Cursor c = context.getContentResolver().query(b.build(), cols, null, null,
                android.provider.CalendarContract.Instances.BEGIN + " ASC")) {
            while (c != null && c.moveToNext() && out.length() < Connectors.RESULT_CHARS) {
                java.util.Date at = new java.util.Date(c.getLong(1));
                out.append("- ").append(day.format(at)).append(c.getInt(2) == 1 ? " (all day)" : " " + time.format(at)).append(": ")
                        .append(c.getString(0) == null ? "(no title)" : c.getString(0));
                String where = c.getString(3);
                if (where != null && !where.isEmpty()) {
                    out.append(" at ").append(where);
                }
                out.append('\n');
            }
        }
        return out.length() == 0 ? "No events in the next 7 days." : out.toString();
    }
}
