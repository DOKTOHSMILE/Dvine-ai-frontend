package ai.dvine.dvine1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentence shapes: how DVINE 1 learns new ways a fact is written. When a sentence holds a fact it already knows (a
 * subject and its value), the two words on each side of the value become a shape with the value masked ("was born in
 * {V} ,"). Each shape is then tried on everything it reads; it is promoted to a reading rule once it has proved right,
 * and pruned when it keeps being wrong or goes unused.
 *
 * <pre>
 * S1  PII first      e-mail, phone number, web address, card or long number → {EMAIL} {PHONE} {URL} {NUMBER}
 * S2  value          a proper noun, a quoted phrase, a number or date, or a noun phrase after a known verb
 * S3  shape          up to 2 words left + {V} + up to 2 words right; at least 2 real words in all;
 *                    never from a question or a negative sentence
 * S4  caps           200 shapes, 4000 pattern characters in all
 * S5  promote        seen >= 6, hit/seen >= 0.5, rejected/seen < 0.4
 * S6  prune          seen >= 20 and hit/seen < 0.25; or unused 30 days once 60 days old
 * </pre>
 */
public final class Shapes {
    private Shapes() {
    }

    public static final int MOST = 200, CHARS = 4000, SIDE = 2;
    public static final long DAY = 24L * 60 * 60 * 1000;

    /** One shape: the relation it reads, the words before and after the value, and its record. */
    public static final class Rule {
        public final String relation, left, right;
        public int seen, hit, rejected;
        public long born, used;

        public Rule(String relation, String left, String right, long born) {
            this.relation = relation;
            this.left = left;
            this.right = right;
            this.born = born;
            this.used = born;
        }

        public String pattern() {
            return (left + " {V} " + right).trim();
        }

        /** S5. */
        public boolean promoted() {
            return seen >= 6 && hit >= 0.5 * seen && rejected < 0.4 * seen;
        }

        /** S6. */
        public boolean prune(long now) {
            return seen >= 20 && hit < 0.25 * seen || now - born > 60 * DAY && now - used > 30 * DAY;
        }

        /** The value this shape reads in a sentence, or null. */
        public String read(String sentence) {
            String s = " " + words(sentence) + " ";
            String l = left.isEmpty() ? " " : " " + left + " ", r = right.isEmpty() ? " " : " " + right + " ";
            int i = s.toLowerCase(Locale.US).indexOf(l.toLowerCase(Locale.US));
            if (i < 0) {
                return null;
            }
            int from = i + l.length();
            int j = right.isEmpty() ? -1 : s.toLowerCase(Locale.US).indexOf(r.toLowerCase(Locale.US), from);
            String v = (right.isEmpty() ? s.substring(from) : j < 0 ? "" : s.substring(from, j + 1)).trim();
            if (right.isEmpty()) {
                v = v.replaceAll("^(\\S+(?: \\S+){0,3}).*$", "$1"); // a value at the end: at most 4 words
            }
            return value(v) ? v : null;
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL = Pattern.compile("(?i)\\b(https?://|www\\.)\\S+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ()-]{7,}\\d");
    private static final Pattern LONG = Pattern.compile("\\b\\d{9,}\\b");

    /** S1: personal details replaced before anything is kept. */
    public static String mask(String text) {
        String t = text == null ? "" : text;
        t = EMAIL.matcher(t).replaceAll("{EMAIL}");
        t = URL.matcher(t).replaceAll("{URL}");
        t = LONG.matcher(t).replaceAll("{NUMBER}");
        t = PHONE.matcher(t).replaceAll("{PHONE}");
        return t;
    }

    /** S2: a value worth reading: a name, a quote, a number or date, or a short noun phrase. */
    public static boolean value(String v) {
        if (v == null) {
            return false;
        }
        String t = v.trim();
        if (t.isEmpty() || t.length() > 60 || t.contains("{") || t.split(" ").length > 6) {
            return false;
        }
        return t.matches("\"[^\"]{1,58}\"|“[^”]{1,58}”") // quoted
                || t.matches("\\d[\\d,./:-]*( (BC|AD|BCE|CE))?|(?i)(january|february|march|april|may|june|july|august|september"
                        + "|october|november|december) \\d{1,2}(, \\d{4})?|\\d{1,2} (?i)(january|february|march|april|may|june|july"
                        + "|august|september|october|november|december)( \\d{4})?") // number or date
                || t.matches("\\p{Lu}[\\p{L}.'-]*( (of|the|de|da|van|von|and|&|\\p{Lu}[\\p{L}.'-]*))*") // proper noun
                || t.matches("(?i)(the |a |an )?[\\p{L}-]+( [\\p{L}-]+){0,2}") && !PartsOfSpeech.PRONOUNS.contains(t.toLowerCase(Locale.US));
    }

    /** S3: a shape around {@code value} in {@code sentence} for {@code relation}, or null when none is allowed. */
    public static Rule shape(String sentence, String value, String relation, long now) {
        String s = words(mask(sentence));
        if (s.isEmpty() || value == null || question(s) || negative(s)) {
            return null;
        }
        String v = words(value);
        int i = s.toLowerCase(Locale.US).indexOf(v.toLowerCase(Locale.US));
        if (i < 0 || v.isEmpty() || !value(value.trim())) {
            return null;
        }
        String[] before = s.substring(0, i).trim().isEmpty() ? new String[0] : s.substring(0, i).trim().split(" ");
        String[] after = s.substring(i + v.length()).trim().isEmpty() ? new String[0] : s.substring(i + v.length()).trim().split(" ");
        String left = tail(before, SIDE), right = head(after, SIDE);
        int real = 0;
        for (String w : (left + " " + right).trim().split(" ")) {
            if (!w.isEmpty() && w.matches("[\\p{L}']+") && !w.startsWith("{")) {
                real++;
            }
        }
        if (real < 2 || (left + right).contains("{")) {
            return null; // wildcard-heavy, or a personal detail beside the value
        }
        return new Rule(relation, left.toLowerCase(Locale.US), right.toLowerCase(Locale.US), now);
    }

    /** S4: the rules kept, best first, within 200 rules and 4000 pattern characters. */
    public static List<Rule> capped(List<Rule> rules) {
        List<Rule> sorted = new ArrayList<Rule>(rules);
        sorted.sort((a, b) -> a.promoted() != b.promoted() ? (a.promoted() ? -1 : 1) : Integer.compare(b.hit, a.hit) != 0
                ? Integer.compare(b.hit, a.hit) : Long.compare(b.used, a.used));
        List<Rule> kept = new ArrayList<Rule>();
        int chars = 0;
        for (Rule r : sorted) {
            if (kept.size() >= MOST || chars + r.pattern().length() > CHARS) {
                continue;
            }
            kept.add(r);
            chars += r.pattern().length();
        }
        return kept;
    }

    public static boolean question(String s) {
        return s.trim().endsWith("?") || s.trim().toLowerCase(Locale.US).matches("(who|what|when|where|why|how|which|is|are|do|does|did|can|could|will|would)\\b.*");
    }

    public static boolean negative(String s) {
        return (" " + s.toLowerCase(Locale.US) + " ").matches(".*\\b(not|never|no|n't|neither|nor|none)\\b.*") || s.toLowerCase(Locale.US).contains("n't");
    }

    /** Words separated by single spaces, with commas and full stops as words of their own. */
    static String words(String s) {
        Matcher m = Pattern.compile("([,;:])").matcher(s == null ? "" : s);
        return m.replaceAll(" $1 ").replaceAll("\\.(\\s|$)", " . ").replaceAll("\\s+", " ").trim();
    }

    private static String tail(String[] w, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, w.length - n); i < w.length; i++) {
            b.append(b.length() == 0 ? "" : " ").append(w[i]);
        }
        return b.toString();
    }

    private static String head(String[] w, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(n, w.length); i++) {
            b.append(b.length() == 0 ? "" : " ").append(w[i]);
        }
        return b.toString();
    }
}
