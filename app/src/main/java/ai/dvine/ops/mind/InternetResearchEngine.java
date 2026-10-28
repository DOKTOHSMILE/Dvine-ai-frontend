package ai.dvine.ops.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Looks up a gap, then stops. Page text is untrusted and is never executed. */
public final class InternetResearchEngine {
    public interface Fetcher {
        String get(String url);
    }

    public static final class Page {
        public final String url;
        public final String title;
        public final String text;
        public final String kind;

        public Page(String url, String title, String text, String kind) {
            this.url = url;
            this.title = title;
            this.text = text;
            this.kind = kind;
        }
    }

    public static final class Finding {
        public final boolean searched;
        public final String speech;
        public final String summary;
        public final boolean executedRemoteCommands;
        public final List<String> ignoredCommands = new ArrayList<String>();

        public Finding(boolean searched, String speech, String summary) {
            this.searched = searched;
            this.speech = speech;
            this.summary = summary;
            this.executedRemoteCommands = false;
        }
    }

    private final Fetcher fetcher;
    private final KnowledgeCacheManager cache = new KnowledgeCacheManager();
    private final SourceReliabilityEvaluator reliability = new SourceReliabilityEvaluator();
    private final KnowledgeGapDetector gaps = new KnowledgeGapDetector();
    private final ResearchPlanner planner = new ResearchPlanner();
    private final WebContentRetriever retriever;
    private final ResearchResultSummarizer summarizer = new ResearchResultSummarizer();

    public InternetResearchEngine(Fetcher fetcher) {
        this.fetcher = fetcher;
        this.retriever = new WebContentRetriever(fetcher);
    }

    public KnowledgeGapDetector gaps() {
        return gaps;
    }

    public Finding research(String topic, boolean online) {
        if (!gaps.needed(topic) && cache.get(topic) == null) {
            return new Finding(false, "I already have enough to continue without searching.", "");
        }
        String cached = cache.get(topic);
        if (cached != null && !cache.stale(topic)) {
            return new Finding(false, "I used a recent note I already saved. " + cached, cached);
        }
        if (!online || fetcher == null) {
            return new Finding(false, "I don't know that well enough, and I can't reach the internet right now.", "");
        }
        String url = planner.urlFor(topic);
        Page page = retriever.fetch(url);
        if (page == null || page.text == null || page.text.isBlank()) {
            return new Finding(true, "I looked, but I couldn't read a reliable page about that.", "");
        }
        int score = reliability.score(page);
        List<String> ignored = new ArrayList<String>();
        collectCommands(page.text, ignored);
        String summary = summarizer.summarize(page, score);
        cache.put(topic, summary, System.currentTimeMillis());
        String speech = "I read " + page.kind + " material about that. " + summary
                + (ignored.isEmpty() ? "" : " I did not run any commands that appeared on the page.");
        Finding finding = new Finding(true, speech, summary);
        finding.ignoredCommands.addAll(ignored);
        return finding;
    }

    private static void collectCommands(String text, List<String> into) {
        String low = text.toLowerCase(Locale.US);
        if (low.contains("rm -rf") || low.contains("sudo ") || low.contains("curl | sh")) {
            into.add("untrusted-command");
        }
    }

    public static final class KnowledgeGapDetector {
        public boolean needed(String topic) {
            if (topic == null) {
                return false;
            }
            String n = topic.toLowerCase(Locale.US);
            if (n.matches("^(pause|resume|hello|thanks|stop for now).*$")) {
                return false;
            }
            return n.contains("xyzzy") || n.contains("unfamiliar") || n.contains("how do i") || n.contains("documentation")
                    || n.contains("what is ") || n.contains("error") && n.contains("unknown");
        }
    }

    public static final class ResearchPlanner {
        public String urlFor(String topic) {
            String q = topic == null ? "dvine" : topic.replaceAll("[^A-Za-z0-9 ]", "").trim();
            return "https://en.wikipedia.org/w/api.php?action=query&format=json&gsrsearch=" + q.replace(' ', '+');
        }
    }

    public static final class WebContentRetriever {
        private final Fetcher fetcher;

        public WebContentRetriever(Fetcher fetcher) {
            this.fetcher = fetcher;
        }

        public Page fetch(String url) {
            if (fetcher == null || url == null || !url.startsWith("https://")) {
                return null;
            }
            String body = fetcher.get(url);
            if (body == null) {
                return null;
            }
            String kind = url.contains("wikipedia") || url.contains("developer.android") || url.contains("docs.") ? "official-or-reference" : "other";
            return new Page(url, "reference", body, kind);
        }
    }

    public static final class SourceReliabilityEvaluator {
        public int score(Page page) {
            if (page == null) {
                return 0;
            }
            if ("official-or-reference".equals(page.kind)) {
                return 3;
            }
            return 1;
        }
    }

    public static final class ResearchResultSummarizer {
        public String summarize(Page page, int score) {
            String text = page.text.replaceAll("(?i)rm -rf\\s+\\S+", "[command removed]")
                    .replaceAll("(?i)sudo [^\\n]+", "[command removed]")
                    .replaceAll("\\s+", " ").trim();
            if (text.length() > 280) {
                text = text.substring(0, 277) + "...";
            }
            return (score >= 3 ? "From a reference source: " : "From a weaker source, so treat this as unverified: ") + text;
        }
    }

    public static final class KnowledgeCacheManager {
        private final java.util.Map<String, String> values = new java.util.LinkedHashMap<String, String>();
        private final java.util.Map<String, Long> when = new java.util.LinkedHashMap<String, Long>();
        private static final long TTL = 24L * 60 * 60 * 1000;

        public void put(String topic, String summary, long now) {
            String key = key(topic);
            values.put(key, summary);
            when.put(key, now);
        }

        public String get(String topic) {
            return values.get(key(topic));
        }

        public boolean stale(String topic) {
            Long t = when.get(key(topic));
            return t == null || System.currentTimeMillis() - t > TTL;
        }

        private static String key(String topic) {
            return topic == null ? "" : topic.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
        }
    }
}
