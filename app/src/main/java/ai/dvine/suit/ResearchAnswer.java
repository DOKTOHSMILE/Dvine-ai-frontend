package ai.dvine.suit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.dvine.learn.Knowledge;

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

    /** The answer for the pilot: the sentences, then the sources. */
    public String shown() {
        return text + (sources.isEmpty() ? "" : "\n\nSources: " + String.join(", ", sources));
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
