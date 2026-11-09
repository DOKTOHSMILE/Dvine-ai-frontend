package ai.dvine.dvine1;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * DVINE 1's answer from research, with no language model: the sentences from the web results that best answer the
 * question, scored by how many of its meaning words they carry (synonyms and endings folded, as {@link Knowledge}
 * reads them) and by whether they hold the kind of thing asked for (a year for "when", a number for "how many", a
 * name for "who"), in their own words, with where they came from. Nothing is made up: when no sentence answers,
 * there is no answer. Pure Java.
 */
public final class ResearchAnswer {
    public final String text;
    public final List<String> sources;

    private ResearchAnswer(String text, List<String> sources) {
        this.text = text;
        this.sources = sources;
    }

    /** An answer of one text from one source. */
    public static ResearchAnswer of(String text, String source) {
        List<String> src = new ArrayList<String>();
        if (source != null && !source.isEmpty()) {
            src.add(source);
        }
        return new ResearchAnswer(text, src);
    }

    /** The answer for the pilot: the sentences, then the sources, as one block. */
    public String shown() {
        return text + (sources.isEmpty() ? "" : "\nSource: " + String.join(", ", sources));
    }

    private static final Pattern ASKS_WHAT = Pattern.compile("(?i)^\\s*(?:what|who)(?:'s| is| are| was| were)\\s+(.+?)\\s*\\??$");
    private static final Pattern ASKS_ABOUT = Pattern.compile("(?i)^\\s*(?:(?:tell me |say )?what (?:do|did) you (?:know|learn(?:ed|t)?) about|tell me about|define|explain|describe|what does|what do)\\s+(.+?)(?:\\s+mean)?\\s*[?.!]*$");

    /**
     * What a "what is X" / "who is X" / "tell me about X" question is about ("a pig" → "pig"), or null for any other
     * question. These are answered from the subject's own encyclopedia entry first, not from pages that merely
     * mention the word ("pig-butchering", "Pig Latin", "guinea pig").
     */
    public static String subject(String question) {
        String q = question == null ? "" : question.trim();
        Matcher m = ASKS_WHAT.matcher(q);
        if (!m.matches()) {
            m = ASKS_ABOUT.matcher(q);
            if (!m.matches()) {
                return null;
            }
        }
        String s = m.group(1).replaceFirst("(?i)^(?:a|an|the)\\s+", "").replaceAll("[?.!]+$", "").trim();
        if (s.isEmpty() || s.split("\\s+").length > 6 || s.matches("(?i).*\\b(you|your|i|me|my|it|this|that|they|we)\\b.*")) {
            return null; // about the pilot, DVINE or "it": not an encyclopedia subject
        }
        return s;
    }

    /** The encyclopedia address of a subject's short summary (Wikipedia follows "pigs" to "Pig" by itself). */
    public static String summaryUrl(String subject) {
        String t = subject.trim().replace(' ', '_');
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        try {
            return "https://en.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(t, "UTF-8").replace("+", "_");
        } catch (java.io.UnsupportedEncodingException e) {
            return "https://en.wikipedia.org/api/rest_v1/page/summary/" + t;
        }
    }

    /** The answer from a subject's summary (its first two or three sentences), or null for a list of meanings or nothing. */
    public static ResearchAnswer fromSummary(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            java.util.Map<String, Object> m = Json.obj(Json.parse(json));
            String type = Json.str(m.get("type"), "");
            String extract = Json.str(m.get("extract"), "").replaceAll("\\s+", " ").trim();
            if (!"standard".equals(type) || extract.length() < 20) {
                return null; // a disambiguation page or nothing found
            }
            StringBuilder b = new StringBuilder();
            int n = 0;
            for (String sentence : extract.split("(?<=[.!?])\\s+(?=[A-Z])")) {
                if (n == 3 || b.length() + sentence.length() > 480 && n > 0) {
                    break;
                }
                b.append(n == 0 ? "" : " ").append(sentence);
                n++;
            }
            List<String> src = new ArrayList<String>();
            src.add("en.wikipedia.org");
            return new ResearchAnswer(b.toString(), src);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final Pattern SOURCE = Pattern.compile("\\[(\\d+)] [^\\n]*? — (https?://[^\\s]+)");
    private static final Pattern YEAR = Pattern.compile("\\b(1[0-9]{3}|20[0-9]{2})\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d[\\d,.]*\\b");
    private static final Pattern NAME = Pattern.compile("\\b[A-Z][a-z]+ [A-Z][a-z]+");
    private static final Pattern PLACE = Pattern.compile("\\b(in|at|near|from) [A-Z][a-z]+");

    /** The best answer in {@code research} (WebSearch.research's text), or null when none of it answers. */
    public static ResearchAnswer from(String question, String research) {
        if (research == null || research.trim().isEmpty()) {
            return null;
        }
        List<String> want = Knowledge.meaning(question);
        if (want.isEmpty()) {
            return null;
        }
        Knowledge.Kind kind = Knowledge.kind(question);
        // the sources, by their number
        java.util.Map<String, String> hosts = new java.util.HashMap<String, String>();
        Matcher sm = SOURCE.matcher(research);
        while (sm.find()) {
            hosts.put(sm.group(1), host(sm.group(2)));
        }
        List<Scored> all = new ArrayList<Scored>();
        String from = "";
        int order = 0;
        for (String line : research.split("\n")) {
            String l = line.trim();
            Matcher head = Pattern.compile("^From \\[(\\d+)] .*:$").matcher(l);
            if (head.matches()) {
                from = hosts.containsKey(head.group(1)) ? hosts.get(head.group(1)) : "";
                continue;
            }
            Matcher hit = Pattern.compile("^\\[(\\d+)] ").matcher(l);
            if (hit.find()) {
                from = hosts.containsKey(hit.group(1)) ? hosts.get(hit.group(1)) : "";
                continue; // a result's title line
            }
            if (l.startsWith("Web results for")) {
                continue;
            }
            for (String s : l.split("(?<=[.!?])\\s+(?=[A-Z\"“])")) {
                String t = s.replaceAll("\\s+", " ").replaceAll("\\s*…$", "").trim();
                if (t.length() < 25 || t.length() > 400) {
                    continue;
                }
                double score = score(want, kind, t);
                if (score > 0) {
                    all.add(new Scored(t, from, score, order++));
                }
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        String subject = subject(question);
        if (subject != null) {
            // "what is a pig": only sentences that say what the subject itself is, never one where the word is part
            // of another name ("Pig Latin is", "the guinea pig is", "a pig-butchering scam is")
            String head = Pattern.quote(subject.toLowerCase(java.util.Locale.US).replaceAll("s$", ""));
            Pattern defines = Pattern.compile("(?i)^(?:the |a |an )?" + head + "(?:e?s)?(?:\\s*\\([^)]*\\))?(?:,[^,]{0,80},)?\\s+"
                    + "(?:is|are|was|were|refers to|means)\\b.*");
            List<Scored> keep = new ArrayList<Scored>();
            for (Scored sc : all) {
                if (defines.matcher(sc.text).matches()) {
                    keep.add(sc);
                }
            }
            if (keep.isEmpty()) {
                return null; // nothing says what it is: better no answer than a wrong one
            }
            all = keep;
        }
        java.util.Collections.sort(all, (a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : Integer.compare(a.order, b.order));
        if (all.get(0).score < 0.34) {
            return null; // nothing in it is about the question
        }
        List<String> picked = new ArrayList<String>();
        Set<String> sources = new LinkedHashSet<String>();
        for (Scored s : all) {
            if (picked.size() >= 3 || s.score < all.get(0).score * 0.6) {
                break;
            }
            boolean repeat = false;
            for (String p : picked) {
                if (overlap(p, s.text) > 0.7) {
                    repeat = true;
                    break;
                }
            }
            if (!repeat) {
                picked.add(s.text.endsWith(".") || s.text.endsWith("!") || s.text.endsWith("?") ? s.text : s.text + ".");
                if (!s.from.isEmpty()) {
                    sources.add(s.from);
                }
            }
        }
        return new ResearchAnswer(String.join(" ", picked), new ArrayList<String>(sources));
    }

    /** How well a sentence answers: the share of the question's meaning it carries, plus the kind of thing asked. */
    static double score(List<String> want, Knowledge.Kind kind, String sentence) {
        List<String> has = Knowledge.meaning(sentence);
        int hit = 0;
        for (String w : want) {
            for (String h : has) {
                if (w.equals(h) || (w.length() >= 4 && h.length() >= 4 && (w.startsWith(h) || h.startsWith(w)))) {
                    hit++;
                    break;
                }
            }
        }
        if (hit == 0) {
            return 0;
        }
        double s = (double) hit / want.size();
        boolean fits;
        switch (kind) {
            case WHEN:
                fits = YEAR.matcher(sentence).find();
                break;
            case COUNT:
                fits = NUMBER.matcher(sentence).find();
                break;
            case WHO:
                fits = NAME.matcher(sentence).find();
                break;
            case WHERE:
                fits = PLACE.matcher(sentence).find();
                break;
            case WHAT:
                fits = sentence.matches("(?s).*\\b(is|are|was|were|means|refers to)\\b.*");
                break;
            default:
                fits = true;
        }
        return s + (fits ? 0.25 : 0) - (sentence.length() > 260 ? 0.05 : 0);
    }

    private static double overlap(String a, String b) {
        List<String> x = Knowledge.meaning(a), y = Knowledge.meaning(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String w : x) {
            if (y.contains(w)) {
                n++;
            }
        }
        return (double) n / Math.min(x.size(), y.size());
    }

    static String host(String url) {
        String h = url.replaceFirst("^https?://", "");
        int slash = h.indexOf('/');
        h = slash < 0 ? h : h.substring(0, slash);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private static final class Scored {
        final String text, from;
        final double score;
        final int order;

        Scored(String text, String from, double score, int order) {
            this.text = text;
            this.from = from;
            this.score = score;
            this.order = order;
        }
    }
}
