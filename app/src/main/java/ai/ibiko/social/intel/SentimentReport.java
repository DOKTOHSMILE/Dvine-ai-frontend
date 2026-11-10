package ai.ibiko.social.intel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a sample of public posts says about a topic: how many lean positive, negative or neutral, the themes that
 * repeat, the concerns and the questions people ask, with the sample's size, platforms and period always stated.
 * It describes the posts read, never "what everyone thinks". The scoring is a word list with negation (fast, on the
 * device, no model needed); the selected brain writes the reading of it. Pure Java.
 */
public final class SentimentReport {
    public final String topic;
    public int positive, negative, neutral;
    public final List<String> themes = new ArrayList<String>();
    public final List<String> concerns = new ArrayList<String>();
    public final List<String> questions = new ArrayList<String>();
    public final List<String> positiveSamples = new ArrayList<String>(), negativeSamples = new ArrayList<String>();
    public final Set<String> platforms = new LinkedHashSet<String>();
    public String period = "";
    public final List<String> limitations = new ArrayList<String>();

    SentimentReport(String topic) {
        this.topic = topic == null ? "" : topic;
    }

    public int total() {
        return positive + negative + neutral;
    }

    private static final Set<String> GOOD = words("good great love loved excellent amazing awesome best happy like likes"
            + " liked nice useful helpful impressive fantastic brilliant fast easy beautiful wonderful recommend win support"
            + " glad thanks perfect cool solid reliable improved better smooth exciting excited");
    private static final Set<String> BAD = words("bad terrible awful hate hated worst poor slow broken bug bugs buggy expensive"
            + " overpriced scam fake fail failed failure useless disappointing disappointed angry annoying problem problems"
            + " issue issues crash crashes wrong worse lie lies unfair dangerous risk concern concerned worried waste");
    private static final Set<String> NEGATE = words("not no never isn't wasn't don't doesn't didn't can't cannot won't hardly");
    private static final Set<String> STOP = words("the a an and or but to of in on at for with is are was were be it this"
            + " that these those i you me my your we our they them he she his her do does did can could would should will"
            + " just so if then there here about from up out have has had what how why when where which who people get got"
            + " like really very also more than all some any one not no its it's i'm you're they're");

    private static Set<String> words(String s) {
        return new java.util.HashSet<String>(java.util.Arrays.asList(s.split(" ")));
    }

    /** -1, 0 or 1 for one post: the words, with a negation flipping the next word or two. */
    static int score(String text) {
        String[] w = (text == null ? "" : text.toLowerCase(Locale.US)).split("[^a-z']+");
        int s = 0, flip = 0;
        for (String x : w) {
            if (NEGATE.contains(x)) {
                flip = 2;
                continue;
            }
            int v = GOOD.contains(x) ? 1 : BAD.contains(x) ? -1 : 0;
            s += flip > 0 ? -v : v;
            if (flip > 0) {
                flip--;
            }
        }
        return Integer.signum(s);
    }

    /** The report for these posts. */
    public static SentimentReport of(String topic, List<SocialPost> posts) {
        SentimentReport r = new SentimentReport(topic);
        Set<String> topicWords = new java.util.HashSet<String>();
        for (String t : (topic == null ? "" : topic.toLowerCase(Locale.US)).split("[^a-z0-9']+")) {
            topicWords.add(t);
        }
        Map<String, Integer> freq = new java.util.HashMap<String, Integer>();
        String first = null, last = null;
        for (SocialPost p : posts) {
            r.platforms.add(p.platform);
            if (!p.date.isEmpty()) {
                first = first == null || p.date.compareTo(first) < 0 ? p.date : first;
                last = last == null || p.date.compareTo(last) > 0 ? p.date : last;
            }
            int s = score(p.content);
            String quote = p.content.length() > 160 ? p.content.substring(0, 160) + "…" : p.content;
            if (s > 0) {
                r.positive++;
                if (r.positiveSamples.size() < 3) {
                    r.positiveSamples.add(quote);
                }
            } else if (s < 0) {
                r.negative++;
                if (r.negativeSamples.size() < 3) {
                    r.negativeSamples.add(quote);
                }
                for (String w : p.content.toLowerCase(Locale.US).split("[^a-z']+")) {
                    if (BAD.contains(w) && !r.concerns.contains(w) && r.concerns.size() < 6) {
                        r.concerns.add(w);
                    }
                }
            } else {
                r.neutral++;
            }
            for (String sentence : p.content.split("(?<=[.!?])\\s+")) {
                if (sentence.trim().endsWith("?") && r.questions.size() < 5 && sentence.length() < 200) {
                    r.questions.add(sentence.trim());
                }
            }
            Set<String> once = new java.util.HashSet<String>();
            for (String w : p.content.toLowerCase(Locale.US).split("[^a-z0-9']+")) {
                if (w.length() > 3 && !STOP.contains(w) && !topicWords.contains(w) && once.add(w)) {
                    freq.merge(w, 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<String, Integer>> e = new ArrayList<Map.Entry<String, Integer>>(freq.entrySet());
        e.sort((a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> x : e) {
            if (x.getValue() < 2 || r.themes.size() >= 8) {
                break;
            }
            r.themes.add(x.getKey() + " (" + x.getValue() + " posts)");
        }
        r.period = first == null ? "dates not given by the sources" : first.equals(last) ? first : first + " to " + last;
        r.limitations.add("A sample of " + r.total() + " posts found by search, not everyone's opinion.");
        r.limitations.add("Word-list scoring: sarcasm and context can be misread; the reading below weighs the posts themselves.");
        if (r.total() < 10) {
            r.limitations.add("Fewer than 10 posts: too few to call a trend.");
        }
        return r;
    }

    public String text() {
        StringBuilder b = new StringBuilder("Sentiment sample for “").append(topic).append("”: ").append(total())
                .append(" posts from ").append(platforms.isEmpty() ? "no platform" : String.join(", ", platforms))
                .append("; period: ").append(period).append(".\n");
        if (total() > 0) {
            b.append(String.format(Locale.US, "Leaning positive %d (%d%%), negative %d (%d%%), neutral %d (%d%%).%n",
                    positive, 100 * positive / total(), negative, 100 * negative / total(), neutral, 100 * neutral / total()));
        }
        if (!themes.isEmpty()) {
            b.append("Repeated themes: ").append(String.join(", ", themes)).append(".\n");
        }
        if (!concerns.isEmpty()) {
            b.append("Concern words: ").append(String.join(", ", concerns)).append(".\n");
        }
        for (String q : questions) {
            b.append("Question asked: ").append(q).append('\n');
        }
        for (String q : positiveSamples) {
            b.append("Positive example: ").append(q).append('\n');
        }
        for (String q : negativeSamples) {
            b.append("Negative example: ").append(q).append('\n');
        }
        for (String l : limitations) {
            b.append("Limitation: ").append(l).append('\n');
        }
        return b.toString().trim();
    }
}
