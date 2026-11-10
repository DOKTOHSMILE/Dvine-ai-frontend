package ai.ibiko.library;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Developer documentation kept on the device for coding with no internet: sets from DevDocs (devdocs.io, an open
 * collection of the official docs: Kotlin, Java, Python, JavaScript, MDN's HTML and CSS, Google's Go and Dart, and
 * more; Android's own docs are not among them), downloaded once while online and indexed into SQLite full-text search. The
 * model searches it with docs.search and opens a page with docs.read, online or off. Each set keeps its own licence
 * (shown on devdocs.io).
 */
public final class DevDocs {
    /** A set that can be downloaded: its DevDocs slug ("python~3.14"), name, version and download size. */
    public static final class Set {
        public final String slug, name, release;
        public final long bytes;

        Set(String slug, String name, String release, long bytes) {
            this.slug = slug;
            this.name = name;
            this.release = release == null ? "" : release;
            this.bytes = bytes;
        }

        public String shown() {
            return name + (release.isEmpty() ? "" : " " + release);
        }
    }

    /** One search result: the set, the page, its title and the words around the match. */
    public static final class Hit {
        public final String slug, path, title, around;

        Hit(String slug, String path, String title, String around) {
            this.slug = slug;
            this.path = path;
            this.title = title;
            this.around = around;
        }
    }

    /** Reports how far a download has got (0..1) and what it is doing. */
    public interface Progress {
        void at(float done, String what);
    }

    /** The sets offered, in this order, each the newest version DevDocs has. */
    static final String[] OFFERED = {"kotlin", "openjdk", "python", "javascript", "typescript", "html", "css", "dom", "node",
            "react", "go", "dart", "rust", "c", "cpp", "sqlite", "postgresql", "git", "bash", "docker", "http"};

    static final String CATALOG = "https://devdocs.io/docs.json";
    static final String FILES = "https://documents.devdocs.io/";

    private final Context context;
    private SQLiteDatabase db;

    public DevDocs(Context c) {
        context = c.getApplicationContext();
    }

    private synchronized SQLiteDatabase db() {
        if (db == null) {
            File f = new File(context.getFilesDir(), "devdocs.db");
            db = SQLiteDatabase.openOrCreateDatabase(f, null);
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS pages USING fts4(slug, path, title, body)");
            db.execSQL("CREATE TABLE IF NOT EXISTS sets (slug TEXT PRIMARY KEY, name TEXT, release TEXT, bytes INTEGER, pages INTEGER, done INTEGER)");
        }
        return db;
    }

    // ---- what is offered and what is kept

    /** The sets DevDocs offers now (online), the newest version of each of {@link #OFFERED}; kept for offline. */
    public List<Set> catalog() throws IOException {
        String json = get(CATALOG);
        java.nio.file.Files.write(new File(context.getFilesDir(), "devdocs-catalog.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
        return parseCatalog(json);
    }

    /** The catalog last seen (offline), or none. */
    public List<Set> lastCatalog() {
        File f = new File(context.getFilesDir(), "devdocs-catalog.json");
        try {
            return f.isFile() ? parseCatalog(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                    : new ArrayList<Set>();
        } catch (IOException | RuntimeException e) {
            return new ArrayList<Set>();
        }
    }

    /** The offered sets in DevDocs' list (it lists the newest version of each first). Pure Java. */
    static List<Set> parseCatalog(String json) {
        List<Set> out = new ArrayList<Set>();
        Map<String, Set> first = new HashMap<String, Set>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                String slug = o.optString("slug");
                String base = slug.contains("~") ? slug.substring(0, slug.indexOf('~')) : slug;
                if (!first.containsKey(base)) {
                    first.put(base, new Set(slug, o.optString("name", base), o.optString("release", ""), o.optLong("db_size", 0)));
                }
            }
        } catch (org.json.JSONException e) {
            return out;
        }
        for (String s : OFFERED) {
            if (first.containsKey(s)) {
                out.add(first.get(s));
            }
        }
        return out;
    }

    /** The sets kept on the device (indexed in full). */
    public synchronized List<Set> installed() {
        List<Set> out = new ArrayList<Set>();
        try (Cursor c = db().rawQuery("SELECT slug, name, release, bytes FROM sets WHERE done = 1 ORDER BY name", null)) {
            while (c.moveToNext()) {
                out.add(new Set(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
            }
        }
        return out;
    }

    public boolean has(String slug) {
        for (Set s : installed()) {
            if (s.slug.equals(slug)) {
                return true;
            }
        }
        return false;
    }

    /** Deletes a set from the device. */
    public synchronized void remove(String slug) {
        db().delete("pages", "slug = ?", new String[]{slug});
        db().delete("sets", "slug = ?", new String[]{slug});
    }

    // ---- downloading

    /** Downloads a set and indexes it (blocking: call off the main thread); the number of pages kept. */
    public int download(Set set, Progress progress) throws IOException {
        remove(set.slug); // a half-done earlier try is started again
        Map<String, String> titles = titles(set.slug);
        HttpURLConnection c = (HttpURLConnection) new URL(FILES + set.slug + "/db.json").openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(60_000);
        c.setRequestProperty("User-Agent", "Ibiko");
        if (c.getResponseCode() != 200) {
            throw new IOException("DevDocs answered " + c.getResponseCode());
        }
        final long total = Math.max(1, c.getContentLengthLong() > 0 ? c.getContentLengthLong() : set.bytes);
        final long[] read = {0};
        final int[] pages = {0};
        SQLiteDatabase d = db();
        d.beginTransaction();
        try (InputStream raw = c.getInputStream();
             InputStream counted = new java.io.FilterInputStream(raw) {
                 @Override
                 public int read() throws IOException {
                     int b = super.read();
                     if (b >= 0) {
                         read[0]++;
                     }
                     return b;
                 }

                 @Override
                 public int read(byte[] b, int off, int len) throws IOException {
                     int n = super.read(b, off, len);
                     if (n > 0) {
                         read[0] += n;
                     }
                     return n;
                 }
             };
             BufferedReader in = new BufferedReader(new InputStreamReader(counted, StandardCharsets.UTF_8), 1 << 16)) {
            ContentValues v = new ContentValues();
            DocsStream.each(in, (path, html) -> {
                String body = DocsStream.text(html);
                if (body.length() < 20) {
                    return;
                }
                v.clear();
                v.put("slug", set.slug);
                v.put("path", path);
                String title = titles.get(path);
                v.put("title", title != null ? title : path.replace('/', ' ').replace('_', ' '));
                v.put("body", body.length() > 60_000 ? body.substring(0, 60_000) : body);
                d.insert("pages", null, v);
                if (++pages[0] % 200 == 0 && progress != null) {
                    progress.at(Math.min(0.99f, read[0] / (float) total), pages[0] + " pages");
                }
            });
            ContentValues s = new ContentValues();
            s.put("slug", set.slug);
            s.put("name", set.name);
            s.put("release", set.release);
            s.put("bytes", total);
            s.put("pages", pages[0]);
            s.put("done", 1);
            d.insertWithOnConflict("sets", null, s, SQLiteDatabase.CONFLICT_REPLACE);
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
            c.disconnect();
        }
        if (progress != null) {
            progress.at(1f, pages[0] + " pages");
        }
        return pages[0];
    }

    /** Each page's title from the set's index ("Array.prototype.map"), by its path. */
    private Map<String, String> titles(String slug) throws IOException {
        Map<String, String> out = new HashMap<String, String>();
        try {
            org.json.JSONArray e = new org.json.JSONObject(get(FILES + slug + "/index.json")).optJSONArray("entries");
            for (int i = 0; e != null && i < e.length(); i++) {
                org.json.JSONObject o = e.getJSONObject(i);
                String page = DocsStream.pageOf(o.optString("path"));
                if (!out.containsKey(page)) {
                    out.put(page, o.optString("name"));
                }
            }
        } catch (org.json.JSONException ignored) {
            // titles from the paths
        }
        return out;
    }

    /** Adds one page directly (tests, or a set built on the device). */
    synchronized void put(String slug, String name, String path, String title, String html) {
        ContentValues v = new ContentValues();
        v.put("slug", slug);
        v.put("path", path);
        v.put("title", title);
        v.put("body", DocsStream.text(html));
        db().insert("pages", null, v);
        ContentValues s = new ContentValues();
        s.put("slug", slug);
        s.put("name", name);
        s.put("release", "");
        s.put("bytes", 0);
        s.put("pages", 1);
        s.put("done", 1);
        db().insertWithOnConflict("sets", null, s, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---- searching and reading (offline)

    /**
     * Pages matching {@code words}, those with the words in the title first; "python: list sort" searches only that
     * set. Works with no internet.
     */
    public synchronized List<Hit> search(String words, int most) {
        String q = words == null ? "" : words.trim();
        String only = null;
        int colon = q.indexOf(':');
        if (colon > 0 && colon < 14) {
            String set = q.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            for (Set s : installed()) {
                if (s.slug.startsWith(set) || s.name.toLowerCase(Locale.ROOT).startsWith(set)) {
                    only = s.slug;
                    q = q.substring(colon + 1).trim();
                    break;
                }
            }
        }
        String match = matchOf(q);
        List<Hit> out = new ArrayList<Hit>();
        if (match.isEmpty()) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String where : new String[]{"title MATCH ?", "pages MATCH ?"}) {
            String sql = "SELECT slug, path, title, snippet(pages, '', '', '…', 3, 30) FROM pages WHERE " + where
                    + (only != null ? " AND slug = ?" : "") + " LIMIT " + (most * 2);
            String[] args = only != null ? new String[]{match, only} : new String[]{match};
            try (Cursor c = db().rawQuery(sql, args)) {
                while (c.moveToNext() && out.size() < most) {
                    if (seen.add(c.getString(0) + "|" + c.getString(1))) {
                        out.add(new Hit(c.getString(0), c.getString(1), c.getString(2), c.getString(3).replaceAll("\\s+", " ").trim()));
                    }
                }
            } catch (RuntimeException e) {
                // a query the index can't read: no results
            }
        }
        return out;
    }

    /** The words as a full-text query: each word (letters, digits, dots, underscores) as a prefix, all required. */
    static String matchOf(String q) {
        StringBuilder b = new StringBuilder();
        for (String w : (q == null ? "" : q).split("[^A-Za-z0-9_]+")) {
            if (w.length() < 2) {
                continue;
            }
            b.append(b.length() == 0 ? "" : " ").append(w.toLowerCase(Locale.ROOT)).append('*');
        }
        return b.toString();
    }

    /** One page's text, up to {@code most} characters, or null when it isn't kept. */
    public synchronized String read(String slug, String path, int most) {
        try (Cursor c = db().rawQuery("SELECT title, body FROM pages WHERE slug = ? AND path = ? LIMIT 1",
                new String[]{slug, DocsStream.pageOf(path)})) {
            if (!c.moveToNext()) {
                return null;
            }
            String body = c.getString(1);
            return c.getString(0) + " (" + slug + ")\n\n" + (body.length() > most ? body.substring(0, most) + "\n…" : body);
        }
    }

    /** What a model gets for a search: the results, each with how to open it. */
    public String forModel(String words, int most) {
        List<Hit> hits = search(words, most);
        if (installed().isEmpty()) {
            return "No developer docs are kept on this device yet. The user can download them: menu › Connectors › Offline developer docs.";
        }
        if (hits.isEmpty()) {
            return "Nothing in the offline docs matches “" + words + "”. Try other words, or the name of the function or class.";
        }
        StringBuilder b = new StringBuilder("From the offline developer docs (official documentation, kept on this device):\n");
        for (Hit h : hits) {
            b.append("- ").append(h.title).append(" [").append(h.slug).append(' ').append(h.path).append("]: ").append(h.around).append('\n');
        }
        b.append("Open one with docs.read and the [set page] shown.");
        return b.toString();
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Ibiko");
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1 << 15];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
