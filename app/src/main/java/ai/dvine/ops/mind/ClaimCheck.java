package ai.dvine.ops.mind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.dvine.brain.MiniJson;
import ai.dvine.llm.FreshFacts;

/**
 * Compares retrieved pages. One source is not called verified. Page text is
 * untrusted and is never executed. Wikipedia and Wikidata are the two sources
 * this phone can ask; that is not a newsroom.
 */
public final class ClaimCheck {
    public enum Verdict { VERIFIED, SINGLE_SOURCE, CONFLICT, UNVERIFIED, OFFLINE, NOT_A_CLAIM }

    public interface SearchProvider {
        String name();

        Source find(String question);
    }

    public static final class Source {
        public final String name;
        public final String url;
        public final String text;
        public final long retrievedAt;

        public Source(String name, String url, String text, long retrievedAt) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
            this.text = text == null ? "" : text;
            this.retrievedAt = retrievedAt;
        }
    }

    public static final class Report {
        public final Verdict verdict;
        public final String speech;
        public final List<Source> sources;
        public final boolean searched;
        public final boolean ranRemoteCommands;

        Report(Verdict verdict, String speech, List<Source> sources, boolean searched) {
            this.verdict = verdict;
            this.speech = speech;
            this.sources = sources;
            this.searched = searched;
            this.ranRemoteCommands = false;
        }
    }

    private static final class Cached {
        final Report report;
        final long at;
        final boolean timeSensitive;

        Cached(Report report, long at, boolean timeSensitive) {
            this.report = report;
            this.at = at;
            this.timeSensitive = timeSensitive;
        }
    }

    private final Map<String, Cached> cache = new LinkedHashMap<String, Cached>();
    private static final long TTL = 6L * 60 * 60 * 1000;

    /** Explicit checks, and questions about what is current. A definition is not one of these. */
    public static boolean wanted(String text) {
        String n = text == null ? "" : text.toLowerCase(Locale.US);
        if (n.contains("fact check") || n.contains("fact-check") || n.contains("look this up")
                || n.contains("look up whether") || n.contains("is it true") || n.contains("verify this")
                || n.contains("check this fact") || n.contains("according to sources")) {
            return true;
        }
        boolean current = n.contains("latest") || n.contains("currently") || n.contains("right now") || n.contains("this year");
        boolean question = n.contains("what") || n.contains("who") || n.contains("when") || n.contains("where");
        return current && question && !n.contains("date") && !n.contains("time");
    }

    public Report check(String question, boolean online, List<SearchProvider> providers, long now) {
        if (!wanted(question)) {
            return new Report(Verdict.NOT_A_CLAIM, "", List.of(), false);
        }
        return inspect(question, online, providers, now, null);
    }

    /** The caller already decided this question should be looked up. */
    public Report inspect(String question, boolean online, List<SearchProvider> providers, long now) {
        return inspect(question, online, providers, now, null);
    }

    public Report inspect(String question, boolean online, List<SearchProvider> providers, long now, ai.dvine.ops.task.TaskControl control) {
        String key = question.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
        boolean fresh = timeSensitive(key);
        Cached prior = cache.get(key);
        if (prior != null && !fresh && !prior.timeSensitive && now - prior.at < TTL) {
            String when = stamp(prior.at);
            return new Report(prior.report.verdict,
                    "I am using a note retrieved at " + when + ", not a fresh search. " + prior.report.speech,
                    prior.report.sources, false);
        }
        if (!online || providers == null || providers.isEmpty()) {
            Report offline = new Report(Verdict.OFFLINE,
                    "I did not search. There is no network from here, so this is not verified.", List.of(), false);
            return offline;
        }
        List<Source> found = new ArrayList<Source>();
        boolean hostile = false;
        for (SearchProvider provider : providers) {
            if (control != null && control.isCancelled()) {
                return stopped(found);
            }
            Source raw;
            try {
                raw = provider.find(question);
            } catch (RuntimeException e) {
                raw = null;
            }
            if (raw == null || raw.text == null || raw.text.length() < 12) {
                continue;
            }
            if (hostile(raw.text)) {
                hostile = true;
            }
            String clean = strip(raw.text);
            if (clean.length() < 12) {
                continue;
            }
            found.add(new Source(raw.name.isEmpty() ? provider.name() : raw.name, raw.url, clean, raw.retrievedAt == 0 ? now : raw.retrievedAt));
        }
        if (control != null && control.isCancelled()) {
            return stopped(found);
        }
        Report report = judge(found, hostile, now);
        cache.put(key, new Cached(report, now, fresh));
        return report;
    }

    private static Report stopped(List<Source> found) {
        return new Report(Verdict.UNVERIFIED, "I stopped the search before it finished. This is not verified.", found, true);
    }

    private static Report judge(List<Source> found, boolean hostile, long now) {
        String tail = hostile ? " I ignored instructions on the page and did not run them." : "";
        if (found.isEmpty()) {
            return new Report(Verdict.UNVERIFIED,
                    "I searched and could not verify that from Wikipedia or Wikidata." + tail, found, true);
        }
        if (found.size() == 1) {
            Source only = found.get(0);
            return new Report(Verdict.SINGLE_SOURCE,
                    "I found one source, " + only.name + ", retrieved " + stamp(only.retrievedAt)
                            + ". This is not independently verified. " + clip(only.text) + tail,
                    found, true);
        }
        Source a = found.get(0);
        Source b = found.get(1);
        if (numbersConflict(a.text, b.text)) {
            return new Report(Verdict.CONFLICT,
                    "The sources disagree, so I am not calling this verified. " + a.name + " says " + clip(a.text)
                            + " " + b.name + " says " + clip(b.text) + " Retrieved " + stamp(now) + "." + tail,
                    found, true);
        }
        if (agree(a.text, b.text)) {
            return new Report(Verdict.VERIFIED,
                    "Two sources agree, which is not a proof. " + clip(a.text) + " Sources: " + a.name + " ("
                            + stamp(a.retrievedAt) + "), " + b.name + " (" + stamp(b.retrievedAt) + ")." + tail,
                    found, true);
        }
        return new Report(Verdict.UNVERIFIED,
                "I read " + a.name + " and " + b.name + " and they do not corroborate each other, so this stays unverified. Retrieved "
                        + stamp(now) + "." + tail,
                found, true);
    }

    public static SearchProvider wikipedia(InternetResearchEngine.Fetcher fetcher) {
        return new SearchProvider() {
            public String name() {
                return "Wikipedia";
            }

            public Source find(String question) {
                if (fetcher == null) {
                    return null;
                }
                String url = FreshFacts.searchUrl(question);
                String body = fetcher.get(url);
                String text = body == null ? null : FreshFacts.extract(body);
                if (text == null) {
                    return null;
                }
                return new Source("Wikipedia", url, text, 0);
            }
        };
    }

    public static SearchProvider wikidata(InternetResearchEngine.Fetcher fetcher) {
        return new SearchProvider() {
            public String name() {
                return "Wikidata";
            }

            public Source find(String question) {
                if (fetcher == null) {
                    return null;
                }
                String url = wikidataUrl(question);
                String body = fetcher.get(url);
                String text = body == null ? null : extractWikidata(body);
                if (text == null) {
                    return null;
                }
                return new Source("Wikidata", url, text, 0);
            }
        };
    }

    public static String wikidataUrl(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.US).replaceAll("[?.!]+$", "")
                .replaceAll("^(fact check|fact-check|look this up|look up whether|is it true that|verify this|check this fact|according to sources)\\s+", "")
                .replaceAll("^(what|who|when|where|which|is|are|was|were)\\s+", "")
                .replaceAll("^(the|a|an)\\s+", "").trim();
        try {
            return "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=en&limit=1&search="
                    + java.net.URLEncoder.encode(q, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String extractWikidata(String json) {
        try {
            Map<String, Object> root = MiniJson.obj(MiniJson.parse(json));
            List<Object> search = root == null ? null : MiniJson.list(root.get("search"));
            if (search == null || search.isEmpty()) {
                return null;
            }
            Map<String, Object> hit = MiniJson.obj(search.get(0));
            String label = MiniJson.str(hit.get("label"), "");
            String description = MiniJson.str(hit.get("description"), "").trim();
            if (description.length() < 12) {
                return null;
            }
            return (label.isEmpty() ? "" : label + ": ") + description;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean timeSensitive(String n) {
        return n.contains("latest") || n.contains("currently") || n.contains("right now") || n.contains("today") || n.contains("this year");
    }

    private static boolean hostile(String text) {
        String n = text.toLowerCase(Locale.US);
        return n.contains("rm -rf") || n.contains("sudo ") || n.contains("ignore previous") || n.contains("ignore all previous")
                || n.contains("you are now");
    }

    static String strip(String text) {
        return text.replaceAll("(?i)ignore (all |any )?(previous|prior) instructions[^.]*\\.?", "")
                .replaceAll("(?i)you are now [^.]*\\.?", "")
                .replaceAll("(?i)rm -rf\\s+\\S+", "")
                .replaceAll("(?i)sudo [^\\n.]+", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static String clip(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > 220 ? t.substring(0, 217) + "..." : t;
    }

    private static String stamp(long millis) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US);
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return fmt.format(new java.util.Date(millis));
    }

    private static final Pattern NUMBER = Pattern.compile("\\b\\d{2,}\\b");

    static boolean numbersConflict(String a, String b) {
        Set<String> na = numbers(a);
        Set<String> nb = numbers(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        return !na.equals(nb);
    }

    private static Set<String> numbers(String text) {
        Set<String> out = new HashSet<String>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    static boolean agree(String a, String b) {
        List<String> wa = words(a);
        List<String> wb = words(b);
        Set<String> sb = new HashSet<String>(wb);
        int shared = 0;
        for (String w : new HashSet<String>(wa)) {
            if (w.length() > 3 && sb.contains(w)) {
                shared++;
            }
        }
        if (shared < 3) {
            return false;
        }
        Set<String> gb = grams(wb);
        for (String g : grams(wa)) {
            if (gb.contains(g)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> words(String text) {
        String[] parts = text.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        List<String> out = new ArrayList<String>();
        for (String p : parts) {
            if (p.length() > 2 && !p.equals("the") && !p.equals("and") && !p.equals("that") && !p.equals("this")) {
                out.add(p);
            }
        }
        return out;
    }

    private static Set<String> grams(List<String> words) {
        Set<String> out = new HashSet<String>();
        for (int i = 0; i + 3 < words.size(); i++) {
            out.add(words.get(i) + " " + words.get(i + 1) + " " + words.get(i + 2) + " " + words.get(i + 3));
        }
        return out;
    }
}
