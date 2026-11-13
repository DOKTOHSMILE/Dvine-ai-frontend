package ai.ibiko.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The official reference for a language or tool, looked up the way a developer checks the docs instead of guessing an
 * API: the entry for a name ("json.loads", "Array.prototype.map", "String.format"), from DevDocs, which serves the
 * languages' own documentation in one shape. Each language's index and each page read are kept on the device, so a
 * second lookup is instant and works offline: Ibiko builds its own library of what it has looked up. Pure Java: the
 * download is the caller's ({@link Fetch}).
 */
public final class DocsLookup {
    private DocsLookup() {
    }

    /** Downloads a URL's raw text (an index is a JSON file of a megabyte or more), or null. */
    public interface Fetch {
        String get(String url, int maxBytes);
    }

    static final String BASE = "https://documents.devdocs.io/";

    /** Language names as people say them → DevDocs' documentation sets (the versions current in 2026). */
    static final Map<String, String[]> SETS = new LinkedHashMap<String, String[]>();

    static {
        put("Python 3.13", "python~3.13", "python", "py", "python3");
        put("JavaScript", "javascript", "javascript", "js", "ecmascript");
        put("TypeScript", "typescript", "typescript", "ts");
        put("Node.js", "node", "node", "nodejs", "node.js");
        put("Java 21", "openjdk~21", "java", "openjdk", "jdk");
        put("Kotlin 2", "kotlin~2", "kotlin", "kt");
        put("Go", "go", "go", "golang");
        put("Rust", "rust", "rust", "rs");
        put("C", "c", "c");
        put("C++", "cpp", "cpp", "c++", "cxx");
        put("PHP", "php", "php");
        put("Ruby 3.4", "ruby~3.4", "ruby", "rb");
        put("Bash", "bash", "bash", "shell", "sh");
        put("Git", "git", "git");
        put("HTML", "html", "html");
        put("CSS", "css", "css");
        put("Web APIs (DOM)", "dom", "dom", "web", "browser");
        put("React", "react", "react", "jsx");
    }

    private static void put(String title, String slug, String... names) {
        for (String n : names) {
            SETS.put(n, new String[]{title, slug});
        }
    }

    /** {title, slug} for a language as named, or null. */
    static String[] set(String lang) {
        return lang == null ? null : SETS.get(lang.trim().toLowerCase(Locale.US));
    }

    /** One entry of a documentation set: its name and where it is ("library/json#json.loads"). */
    static final class Entry {
        final String name, path;

        Entry(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    /** The entries in a DevDocs index.json. */
    static List<Entry> entries(String indexJson) {
        List<Entry> out = new ArrayList<Entry>();
        try {
            Object root = ai.ibiko.brain.MiniJson.parse(indexJson);
            for (Object e : ai.ibiko.brain.MiniJson.list(ai.ibiko.brain.MiniJson.obj(root).get("entries"))) {
                Map<String, Object> m = ai.ibiko.brain.MiniJson.obj(e);
                String name = ai.ibiko.brain.MiniJson.str(m.get("name"), ""), path = ai.ibiko.brain.MiniJson.str(m.get("path"), "");
                if (!name.isEmpty() && !path.isEmpty()) {
                    out.add(new Entry(name, path));
                }
            }
        } catch (RuntimeException ignored) {
            // not an index: no entries
        }
        return out;
    }

    private static String norm(String s) {
        // "Array.prototype.map()" is "Array.map" in the docs; a parameter list is not part of the name
        return s.toLowerCase(Locale.US).replaceAll("\\([^)]*\\)", "").replace(".prototype.", ".").replaceAll("\\s+", " ").trim();
    }

    /** The entries that best match a query, best first: the name itself, then a member of that name, then a name starting or containing it. */
    static List<Entry> best(List<Entry> all, String query, int most) {
        String q = norm(query);
        List<Object[]> scored = new ArrayList<Object[]>();
        for (Entry e : all) {
            String n = norm(e.name);
            int s = n.equals(q) ? 100 : n.endsWith("." + q) || n.endsWith("#" + q) || n.endsWith("::" + q) ? 80
                    : n.startsWith(q) ? 60 : n.contains(q) ? 40 : 0;
            int dot = Math.max(q.lastIndexOf('.'), q.lastIndexOf("::"));
            if (s == 0 && dot > 0 && n.startsWith(q.substring(0, dot + 1))) {
                s = 10; // the same module or class ("json.load" beside "json.loads"): the names nearby
            }
            if (s == 0 && q.contains(" ")) {
                boolean all2 = true;
                for (String w : q.split(" ")) {
                    all2 &= n.contains(w);
                }
                s = all2 ? 30 : 0;
            }
            if (s > 0) {
                scored.add(new Object[]{e, s * 1000 - Math.min(999, n.length())}); // shorter names first among equals
            }
        }
        Collections.sort(scored, (a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
        List<Entry> out = new ArrayList<Entry>();
        for (int i = 0; i < scored.size() && i < most; i++) {
            out.add((Entry) scored.get(i)[0]);
        }
        return out;
    }

    /** The text of an entry: from its anchor in the page (or the page's start), tags taken out, about {@code chars} long. */
    static String excerpt(String html, String anchor, int chars) {
        if (html == null) {
            return "";
        }
        int at = 0;
        if (anchor != null && !anchor.isEmpty()) {
            int i = html.indexOf("id=\"" + anchor + "\"");
            if (i >= 0) {
                int open = html.lastIndexOf('<', i);
                at = open >= 0 ? open : i;
            }
        }
        String part = html.substring(at, Math.min(html.length(), at + chars * 4));
        String text = part.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|tr|dt|dd|pre)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").trim();
        if (text.length() > chars) {
            int cut = text.lastIndexOf(". ", chars);
            text = text.substring(0, cut > chars / 2 ? cut + 1 : chars) + " …";
        }
        return text;
    }

    /**
     * The reference entry for {@code query} in {@code lang}'s documentation, with the other close matches, or why there
     * is none. {@code cacheDir} keeps the indexes and pages (null: nothing is kept).
     */
    public static String lookup(String lang, String query, File cacheDir, Fetch fetch) {
        String[] set = set(lang);
        if (set == null) {
            List<String> known = new ArrayList<String>();
            for (String[] s : SETS.values()) {
                if (!known.contains(s[0])) {
                    known.add(s[0]);
                }
            }
            return "No documentation set for \"" + lang + "\". Known: " + String.join(", ", known) + ".";
        }
        if (query == null || query.trim().isEmpty()) {
            return "Say what to look up: a function, class, method or keyword.";
        }
        String index = cached(cacheDir, set[1] + "/index.json", BASE + set[1] + "/index.json", 8 * 1024 * 1024, fetch);
        List<Entry> all = entries(index);
        if (all.isEmpty()) {
            return "The " + set[0] + " documentation couldn't be read (no internet, and not kept on this phone yet).";
        }
        List<Entry> found = best(all, query, 6);
        if (found.isEmpty()) {
            return "Nothing named \"" + query + "\" in the " + set[0] + " documentation. Try the exact name (a function,"
                    + " class or method), or a shorter part of it.";
        }
        Entry top = found.get(0);
        int hash = top.path.indexOf('#');
        String page = hash < 0 ? top.path : top.path.substring(0, hash), anchor = hash < 0 ? null : top.path.substring(hash + 1);
        String html = cached(cacheDir, set[1] + "/" + page.replaceAll("[^\\w./-]", "_") + ".html", BASE + set[1] + "/" + page + ".html",
                2 * 1024 * 1024, fetch);
        StringBuilder b = new StringBuilder(set[0]).append(" reference, ").append(top.name).append(" (").append(page).append("):\n");
        String text = excerpt(html, anchor, 1800);
        b.append(text.isEmpty() ? "(the page couldn't be read)" : text);
        if (found.size() > 1) {
            List<String> others = new ArrayList<String>();
            for (int i = 1; i < found.size(); i++) {
                others.add(found.get(i).name);
            }
            b.append("\nAlso in the docs: ").append(String.join(", ", others));
        }
        b.append("\n(From DevDocs; kept on this phone.)");
        return b.toString();
    }

    /** A file from the cache, or downloaded and kept. */
    private static String cached(File cacheDir, String name, String url, int maxBytes, Fetch fetch) {
        File f = cacheDir == null ? null : new File(cacheDir, name);
        try {
            if (f != null && f.isFile() && f.length() > 0) {
                return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // read again from the web
        }
        String got = fetch == null ? null : fetch.get(url, maxBytes);
        // only what looks like the real thing is kept: one failed download kept would answer every lookup after it
        boolean right = got != null && (name.endsWith(".json") ? got.trim().startsWith("{") : got.contains("<"));
        if (right && f != null) {
            try {
                f.getParentFile().mkdirs();
                Files.write(f.toPath(), got.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // used this time, kept next time
            }
        }
        return got;
    }
}
