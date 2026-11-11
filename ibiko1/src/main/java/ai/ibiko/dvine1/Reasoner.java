package ai.ibiko.dvine1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko 1's reasoning over what it knows: a question that one fact can't answer is broken into steps, each step run on
 * its built-in facts, and the steps put together. The steps are the operators of compositional question answering
 * (as in KQA Pro's KoPL): find the things named, filter a set by a property, relate (neighbours, the country of a
 * capital), read a property, compare, rank (most, least), count, list and compute (how many times, the difference,
 * per square kilometre). Every answer says the values it rests on, so the reasoning can be checked; a step it can't
 * run says what is missing instead of guessing; a question built on a false premise ("why is Ghana bigger than
 * Nigeria?") is answered with what is true.
 */
public final class Reasoner {
    /** An answer and the steps that reached it (shown while it thinks). */
    public static final class Result {
        public final String text;
        public final List<String> steps;

        Result(String text, List<String> steps) {
            this.text = text;
            this.steps = steps;
        }
    }

    /** The kinds of thing it knows: countries, chemical elements, planets. */
    enum Kind {
        COUNTRY("country", "countries"), ELEMENT("element", "elements"), PLANET("planet", "planets");

        final String one, many;

        Kind(String one, String many) {
            this.one = one;
            this.many = many;
        }
    }

    private final BuiltIn k;

    public Reasoner(BuiltIn k) {
        this.k = k;
    }

    // ---- reading the question

    private static final String MORE = "more|most|bigger|biggest|larger|largest|higher|highest|heavier|heaviest|denser|densest"
            + "|greater|greatest|hotter|hottest|wider|widest|longer|longest|populous|further|farthest|furthest";
    private static final String LESS = "less|least|fewer|fewest|smaller|smallest|lower|lowest|lighter|lightest|tinier|tiniest"
            + "|colder|coldest|narrower|closer|closest|nearer|nearest";
    private static final Pattern COMPARATIVE = Pattern.compile("\\b(" + MORE + "|" + LESS + ")\\b|\\bcompare\\b|\\bversus\\b|\\bvs\\b"
            + "|\\bdifference\\b|\\bhow many times\\b");
    private static final Pattern SUPERLATIVE = Pattern.compile("\\b(most|least|fewest|biggest|largest|highest|heaviest|densest"
            + "|greatest|hottest|widest|longest|smallest|lowest|lightest|tiniest|coldest|closest|nearest|farthest|furthest)\\b");

    /** Which way the question ranks: true for most, bigger, heavier; false for least, smaller, lighter. */
    static boolean wantsMore(String q) {
        Matcher m = Pattern.compile("\\b(" + MORE + ")\\b|\\b(" + LESS + ")\\b").matcher(q);
        return !m.find() || m.group(1) != null;
    }

    /** The numeric property a comparison is about, for this kind of thing; null when none fits. */
    static BuiltIn.About measure(String q, Kind kind) {
        if (q.matches(".*\\b(populous|population|people|inhabitants|residents)\\b.*")) {
            return BuiltIn.About.POPULATION;
        }
        if (q.matches(".*\\bmelting\\b.*|.*\\bmelts?\\b.*")) {
            return BuiltIn.About.MELT;
        }
        if (q.matches(".*\\bboiling\\b.*|.*\\bboils?\\b.*")) {
            return BuiltIn.About.BOIL;
        }
        if (q.matches(".*\\b(dense|denser|densest|density)\\b.*")) {
            return BuiltIn.About.DENSITY;
        }
        if (q.matches(".*\\batomic number\\b.*|.*\\bprotons\\b.*")) {
            return BuiltIn.About.NUMBER;
        }
        if (q.matches(".*\\b(heavier|heaviest|lighter|lightest|mass|weight|weighs)\\b.*")) {
            return kind == Kind.ELEMENT ? BuiltIn.About.MASS : null;
        }
        if (q.matches(".*\\b(diameter|wider|widest|across)\\b.*")) {
            return kind == Kind.PLANET ? BuiltIn.About.DIAMETER : kind == Kind.COUNTRY ? BuiltIn.About.AREA : null;
        }
        if (q.matches(".*\\b(area|land|territory|square)\\b.*")) {
            return kind == Kind.COUNTRY ? BuiltIn.About.AREA : null;
        }
        if (q.matches(".*\\b(bigger|biggest|larger|largest|smaller|smallest|size|tinier|tiniest|big|large|small)\\b.*")) {
            return kind == Kind.COUNTRY ? BuiltIn.About.AREA : kind == Kind.PLANET ? BuiltIn.About.DIAMETER
                    : kind == Kind.ELEMENT ? BuiltIn.About.MASS : null;
        }
        if (q.matches(".*\\b(closer|closest|nearer|nearest|further|farthest|furthest)\\b.*") && kind == Kind.PLANET) {
            return BuiltIn.About.ORDER;
        }
        return null;
    }

    /** What a thing is: a country, an element or a planet (mercury is an element and a planet: the words decide). */
    Kind kindOf(String key, String q) {
        // a country is a sovereign state: a member of the United Nations in its facts (not a state of Nigeria, not a
        // territory with a capital of its own)
        List<String> un = k.get(key, BuiltIn.About.UN);
        boolean country = !un.isEmpty() && "yes".equals(un.get(0));
        boolean element = !k.get(key, BuiltIn.About.SYMBOL).isEmpty() || !k.get(key, BuiltIn.About.MASS).isEmpty();
        boolean planet = !k.get(key, BuiltIn.About.ORDER).isEmpty();
        if (planet && (q.contains("planet") || !element)) {
            return Kind.PLANET;
        }
        if (element) {
            return Kind.ELEMENT;
        }
        return country ? Kind.COUNTRY : null;
    }

    /** The kind a question is about by its words ("which country", "the heaviest element"); null when it doesn't say. */
    static Kind kindAsked(String q) {
        if (q.matches(".*\\b(countries|country|nations?|states)\\b.*")) {
            return Kind.COUNTRY;
        }
        if (q.matches(".*\\b(elements?|metals?|gases|gas)\\b.*")) {
            return Kind.ELEMENT;
        }
        if (q.matches(".*\\bplanets?\\b.*")) {
            return Kind.PLANET;
        }
        return null;
    }

    /** A value read as a number for ranking: the order of a planet ("third") is 3. */
    static double valueOf(BuiltIn.About a, String v) {
        if (a == BuiltIn.About.ORDER) {
            List<String> order = Arrays.asList("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth");
            int i = order.indexOf(v == null ? "" : v.trim().toLowerCase(Locale.US));
            return i < 0 ? Double.NaN : i + 1;
        }
        return BuiltIn.number(v);
    }

    private double value(String key, BuiltIn.About a) {
        List<String> v = k.get(key, a);
        return v.isEmpty() ? Double.NaN : valueOf(a, v.get(0));
    }

    /** A measure said for people: "about 35.1 million people", "1,064 °C", "238,533 square kilometres". */
    private String said(String key, BuiltIn.About a) {
        List<String> v = k.get(key, a);
        if (v.isEmpty()) {
            return "unknown";
        }
        String raw = v.get(0);
        switch (a) {
            case POPULATION: {
                List<String> year = k.get(key, BuiltIn.About.POPULATION_YEAR);
                return "about " + big(BuiltIn.number(raw)) + " people" + (year.isEmpty() ? "" : " as of " + year.get(0));
            }
            case AREA:
                return (BuiltIn.number(raw) < 10 ? raw : BuiltIn.grouped(raw)) + " square kilometres";
            case DIAMETER:
                return raw + " km across";
            case ORDER:
                return raw + " from the Sun";
            case MASS:
                return "atomic mass " + raw;
            case DENSITY:
                return raw;
            default:
                return raw;
        }
    }

    /** A big number in words: 223,804,632 is "223.8 million". */
    static String big(double n) {
        if (Double.isNaN(n)) {
            return "an unknown number of";
        }
        if (Math.abs(n) >= 1e9) {
            String b = String.format(Locale.US, "%.2f", n / 1e9).replaceAll("0+$", "").replaceAll("\\.$", "");
            return b + " billion";
        }
        if (Math.abs(n) >= 1e6) {
            return trim(n / 1e6) + " million";
        }
        return BuiltIn.grouped(String.valueOf((long) Math.round(n)));
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static String times(double r) {
        return r >= 10 ? String.valueOf(Math.round(r)) : String.format(Locale.US, "%.1f", r);
    }

    /** The comparative a measure is said with ("more people", "bigger", "heavier"). */
    private static String comparative(BuiltIn.About a, boolean more) {
        switch (a) {
            case POPULATION:
                return more ? "more people than" : "fewer people than";
            case AREA:
            case DIAMETER:
                return more ? "bigger than" : "smaller than";
            case MASS:
                return more ? "heavier than" : "lighter than";
            case DENSITY:
                return more ? "denser than" : "less dense than";
            case MELT:
                return more ? "a higher melting point than" : "a lower melting point than";
            case BOIL:
                return more ? "a higher boiling point than" : "a lower boiling point than";
            case ORDER:
                return more ? "further from the Sun than" : "closer to the Sun than";
            case NUMBER:
                return more ? "a higher atomic number than" : "a lower atomic number than";
            default:
                return more ? "more than" : "less than";
        }
    }

    /** "has" or "is" before the comparative. */
    private static String verb(BuiltIn.About a) {
        return a == BuiltIn.About.POPULATION || a == BuiltIn.About.MELT || a == BuiltIn.About.BOIL || a == BuiltIn.About.NUMBER
                ? "has" : "is";
    }

    // ---- the reasoning

    /** The answer when the question needs more than one fact put together; null when one fact (or none) is all it is. */
    public Result answer(String question) {
        if (question == null || k == null || k.size() == 0) {
            return null;
        }
        String q = BuiltIn.plainQuestion(question);
        if (q.isEmpty()) {
            return null;
        }
        Result r = chain(q);
        if (r == null) {
            r = density(q);
        }
        if (r == null) {
            r = compare(q);
        }
        if (r == null) {
            r = rankOrCountOrList(q);
        }
        if (r == null) {
            r = each(q);
        }
        if (r == null) {
            r = wholePlace(q);
        }
        return r;
    }

    /** The things named in the question that are of one kind (the first named decides the kind). */
    private List<String> named(String q, Kind[] kindOut) {
        List<String> all = k.mentions(q);
        List<String> out = new ArrayList<String>();
        Kind asked = kindAsked(q);
        for (String m : all) {
            Kind kd = kindOf(m, q);
            if (kd == null) {
                continue;
            }
            if (asked != null && kd != asked && !(asked == Kind.PLANET && !k.get(m, BuiltIn.About.ORDER).isEmpty())) {
                continue;
            }
            if (kindOut[0] == null) {
                kindOut[0] = asked != null && !k.get(m, BuiltIn.About.ORDER).isEmpty() && asked == Kind.PLANET ? Kind.PLANET : kd;
            }
            if (kindOf(m, q) == kindOut[0] || (kindOut[0] == Kind.PLANET && !k.get(m, BuiltIn.About.ORDER).isEmpty())) {
                if (!out.contains(m)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /**
     * Compare: two or more things named and a comparative ("which is bigger, Nigeria or Ghana", "is gold heavier than
     * silver", "how many times more people does Nigeria have than Ghana", "why is Ghana bigger than Nigeria").
     */
    Result compare(String q) {
        if (!COMPARATIVE.matcher(q).find()) {
            return null;
        }
        Kind[] kind = new Kind[1];
        List<String> things = named(q, kind);
        if (things.size() < 2 || kind[0] == null) {
            return null;
        }
        BuiltIn.About a = measure(q, kind[0]);
        if (a == null) {
            return null;
        }
        List<String> steps = new ArrayList<String>();
        steps.add("Breaking the question into steps");
        steps.add("Finding " + names(things));
        steps.add("Reading each one's " + a.name);
        List<String> missing = new ArrayList<String>();
        for (String t : things) {
            if (Double.isNaN(value(t, a))) {
                missing.add(name(t));
            }
        }
        if (!missing.isEmpty()) {
            return new Result("I can't compare them: I don't have the " + a.name + " of " + String.join(" and ", missing) + ".", steps);
        }
        steps.add("Comparing");
        boolean more = wantsMore(q);
        if (a == BuiltIn.About.ORDER) {
            more = !q.matches(".*\\b(closer|closest|nearer|nearest)\\b.*"); // further = a later order
        }
        List<String> ranked = new ArrayList<String>(things);
        final BuiltIn.About by = a;
        Collections.sort(ranked, Comparator.comparingDouble((String t) -> value(t, by)).reversed());
        String top = ranked.get(0), last = ranked.get(ranked.size() - 1);
        StringBuilder why = new StringBuilder();
        for (String t : things) {
            why.append(why.length() == 0 ? "" : "; ").append(cap(name(t))).append(": ").append(said(t, a));
        }
        if (things.size() == 2) {
            String x = things.get(0), y = things.get(1);
            double vx = value(x, a), vy = value(y, a);
            if (vx == vy) {
                return new Result(name(x) + " and " + name(y) + " are the same: " + why + ".", steps);
            }
            String winner = more ? top : last, loser = winner.equals(x) ? y : x;
            double vw = value(winner, a), vl = value(loser, a);
            String head = cap(name(winner)) + " " + verb(a) + " " + comparative(a, more) + " " + name(loser);
            String ratio = "";
            if (a != BuiltIn.About.ORDER && a != BuiltIn.About.MELT && a != BuiltIn.About.BOIL && vl > 0 && vw > 0) {
                double r = Math.max(vw, vl) / Math.min(vw, vl);
                if (r >= 1.1) {
                    ratio = " (about " + times(r) + " times " + (a == BuiltIn.About.POPULATION ? "as many" : "as much") + ")";
                } else {
                    ratio = " (only about " + Math.round((r - 1) * 100) + "% apart)";
                }
            }
            if (q.matches(".*\\bdifference\\b.*") && a == BuiltIn.About.POPULATION) {
                ratio = ": a difference of about " + big(Math.abs(vx - vy)) + " people";
            } else if (q.matches(".*\\bdifference\\b.*") && (a == BuiltIn.About.MELT || a == BuiltIn.About.BOIL)) {
                ratio = ": a difference of about " + trim(Math.abs(vx - vy)) + " °C";
            }
            // a yes-or-no or a "why" question checks the claim it was asked with
            boolean yesNo = q.matches("^(is|are|does|do|has|have|was)\\b.*");
            boolean whyQ = q.startsWith("why ");
            boolean claimed = q.indexOf(" " + x + " ") < q.indexOf(" " + y + " ") ? winner.equals(x) : winner.equals(y);
            if (q.matches("^(is|does|has|why)\\b.*") && q.indexOf(" " + x + " ") >= 0) {
                claimed = winner.equals(x); // "is X bigger than Y": the claim is about X, the first named
            }
            String text = head + ratio + ". " + why + ".";
            if (yesNo) {
                text = (claimed ? "Yes. " : "No. ") + text;
            } else if (whyQ && !claimed) {
                text = "It isn't. " + text; // a false premise is corrected, not explained
            }
            steps.add("Checking the result against the numbers");
            return new Result(text, steps);
        }
        String winner = more ? top : last;
        List<String> order = new ArrayList<String>(ranked);
        if (!more) {
            Collections.reverse(order);
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            list.append(i == 0 ? "" : ", then ").append(name(order.get(i))).append(" (").append(said(order.get(i), a)).append(")");
        }
        return new Result(cap(name(winner)) + " " + verb(a) + " the " + superlativeWord(a, more) + " of these. In order: " + list
                + ".", steps);
    }

    /** Population density: people per square kilometre, from the population and the area. */
    Result density(String q) {
        if (!q.matches(".*\\b(population density|people per square|density of population|how densely|per square kilomet)\\w*.*")) {
            return null;
        }
        Kind[] kind = new Kind[1];
        List<String> things = named(q, kind);
        if (things.isEmpty() || kind[0] != Kind.COUNTRY) {
            return null;
        }
        List<String> steps = new ArrayList<String>(Arrays.asList("Breaking the question into steps", "Reading the population",
                "Reading the area", "Dividing the people by the land"));
        StringBuilder out = new StringBuilder();
        for (String t : things) {
            double p = value(t, BuiltIn.About.POPULATION), ar = value(t, BuiltIn.About.AREA);
            if (Double.isNaN(p) || Double.isNaN(ar) || ar <= 0) {
                out.append(out.length() == 0 ? "" : " ").append("I don't have both the population and the area of ")
                        .append(name(t)).append(".");
                continue;
            }
            out.append(out.length() == 0 ? "" : " ").append(cap(name(t))).append(" has about ").append(Math.round(p / ar))
                    .append(" people per square kilometre (").append(said(t, BuiltIn.About.POPULATION)).append(" on ")
                    .append(said(t, BuiltIn.About.AREA)).append(").");
        }
        return new Result(out.toString(), steps);
    }

    /** A set to rank, count or list: its kind, narrowed by a continent, region, currency, language, border or coast. */
    private static final class Filtered {
        final List<String> members = new ArrayList<String>();
        final List<String> said = new ArrayList<String>();
    }

    private Filtered filter(String q, Kind kind) {
        Filtered f = new Filtered();
        String place = null, region = null, currency = null, language = null, border = null;
        Boolean landlocked = null;
        // the longest continent or region named
        for (String key : k.keys()) {
            for (String c : k.get(key, BuiltIn.About.CONTINENT)) {
                String lc = c.toLowerCase(Locale.US);
                if ((" " + q + " ").contains(" " + lc + " ") && (place == null || lc.length() > place.length())) {
                    place = lc;
                }
            }
            for (String r : k.get(key, BuiltIn.About.REGION)) {
                String lr = r.toLowerCase(Locale.US);
                if ((" " + q + " ").contains(" " + lr + " ") && (region == null || lr.length() > region.length())) {
                    region = lr;
                }
            }
            for (String c : k.get(key, BuiltIn.About.CURRENCY)) {
                String lc = c.toLowerCase(Locale.US);
                String last = lc.replaceAll(".* ", "");
                if (q.matches(".*\\b(use|uses|using|currency|pay|pays)\\b.*") && ((" " + q + " ").contains(" " + lc + " ")
                        || (" " + q + " ").contains(" " + last + " ")) && (currency == null || lc.length() > currency.length())) {
                    currency = (" " + q + " ").contains(" " + lc + " ") ? lc : last;
                }
            }
            for (String l : k.get(key, BuiltIn.About.LANGUAGE)) {
                String ll = l.toLowerCase(Locale.US);
                if (q.matches(".*\\b(speak|speaks|spoken|language|languages)\\b.*") && (" " + q + " ").contains(" " + ll + " ")) {
                    language = ll;
                }
            }
        }
        if (region != null && place != null && region.contains(place)) {
            place = null; // "western africa" is the narrower one
        }
        Matcher b = Pattern.compile("\\b(border|borders|bordering|neighbou?rs? of|next to)\\b").matcher(q);
        if (b.find() && kind == Kind.COUNTRY) {
            List<String> m = k.mentions(q.substring(b.end()));
            if (!m.isEmpty()) {
                border = m.get(0);
            }
        }
        if (q.matches(".*\\blandlocked\\b.*")) {
            landlocked = true;
        } else if (q.matches(".*\\b(coast|coastline|by the sea|access to the sea)\\b.*")) {
            landlocked = false;
        }
        for (String key : k.keys()) {
            if (kindOf(key, kind == Kind.PLANET ? "planet" : "") != kind) {
                continue;
            }
            if (place != null && !lower(k.get(key, BuiltIn.About.CONTINENT)).contains(place)) {
                continue;
            }
            if (region != null && !lower(k.get(key, BuiltIn.About.REGION)).contains(region)) {
                continue;
            }
            if (currency != null && !anyContains(lower(k.get(key, BuiltIn.About.CURRENCY)), currency)) {
                continue;
            }
            if (language != null && !lower(k.get(key, BuiltIn.About.LANGUAGE)).contains(language)) {
                continue;
            }
            if (border != null && !lower(k.get(border, BuiltIn.About.NEIGHBOUR)).contains(key)) {
                continue;
            }
            if (landlocked != null) {
                List<String> v = k.get(key, BuiltIn.About.LANDLOCKED);
                if (v.isEmpty() || "yes".equals(v.get(0)) != landlocked) {
                    continue;
                }
            }
            f.members.add(key);
        }
        if (place != null) {
            f.said.add("are in " + cap(place));
        }
        if (region != null) {
            f.said.add("are in " + capWords(region));
        }
        if (currency != null) {
            f.said.add("use the " + currency);
        }
        if (language != null) {
            f.said.add("have " + cap(language) + " as an official language");
        }
        if (border != null) {
            f.said.add("border " + name(border));
        }
        if (landlocked != null) {
            f.said.add(landlocked ? "are landlocked" : "have a coastline");
        }
        return f;
    }

    /**
     * Rank ("the most populous country in Africa", "the heaviest element", "the biggest planet"), count ("how many
     * countries use the euro") or list ("which countries border Ghana and are landlocked") a filtered set.
     */
    Result rankOrCountOrList(String q) {
        Kind kind = kindAsked(q);
        boolean count = q.matches("^(how many|what number of|count)\\b.*");
        boolean superlative = SUPERLATIVE.matcher(q).find();
        boolean list = q.matches("^(which|what|list|name|tell me)\\b.*") && q.matches(".*\\b(countries|elements|planets|nations)\\b.*");
        if (kind == null || (!count && !superlative && !list)) {
            return null;
        }
        Kind[] named = new Kind[1];
        List<String> mentioned = named(q, named);
        Filtered f = filter(q, kind);
        boolean narrowed = !f.said.isEmpty();
        if (!superlative && !count && !narrowed) {
            return null; // "which countries …" with nothing to narrow by: not this
        }
        if (superlative && mentioned.size() >= 2) {
            return null; // the things are named: a comparison
        }
        List<String> steps = new ArrayList<String>();
        steps.add("Breaking the question into steps");
        String that = String.join(" and ", f.said);
        steps.add("Gathering the " + kind.many + (narrowed ? " that " + that : ""));
        String set = kind.many + (narrowed ? " that " + that : "");
        if (f.members.isEmpty()) {
            return new Result("I don't know of any " + set + ".", steps);
        }
        if (superlative) {
            BuiltIn.About a = measure(q, kind);
            if (a == null) {
                return null;
            }
            boolean more = wantsMore(q);
            if (a == BuiltIn.About.ORDER) {
                more = !q.matches(".*\\b(closer|closest|nearer|nearest)\\b.*");
            }
            steps.add("Reading each one's " + a.name);
            List<String> known = new ArrayList<String>();
            for (String m : f.members) {
                if (!Double.isNaN(value(m, a))) {
                    known.add(m);
                }
            }
            if (known.isEmpty()) {
                return new Result("I don't have the " + a.name + " of the " + set + ".", steps);
            }
            steps.add("Ranking them");
            final BuiltIn.About by = a;
            Collections.sort(known, Comparator.comparingDouble((String t) -> value(t, by)));
            if (more) {
                Collections.reverse(known);
            }
            String first = known.get(0);
            String text = "Of the " + known.size() + " " + set + (narrowed ? "" : " I know") + ", " + name(first) + " " + verb(a)
                    + " the " + superlativeWord(a, more) + ": " + said(first, a) + ".";
            int next = 1;
            if (known.size() > 1 && value(known.get(1), a) == value(first, a)) {
                text = text.substring(0, text.length() - 1) + ", tied with " + name(known.get(1)) + ".";
                next = 2;
            }
            // a claim about one of them ("is Ghana the biggest country in Africa?") is checked, a false one corrected
            if (mentioned.size() == 1 && q.matches("^(is|was|are|why)\\b.*") && known.contains(mentioned.get(0))) {
                boolean right = mentioned.get(0).equals(first);
                text = (q.startsWith("why") ? (right ? "" : "It isn't. ") : right ? "Yes. " : "No. ") + text;
            }
            if (known.size() > next) {
                boolean two = known.size() > next + 1;
                text += " Next " + (two ? "come " : "comes ") + name(known.get(next)) + " (" + said(known.get(next), a) + ")"
                        + (two ? " and " + name(known.get(next + 1)) + " (" + said(known.get(next + 1), a) + ")" : "") + ".";
            }
            return new Result(text, steps);
        }
        steps.add(count ? "Counting them" : "Listing them");
        List<String> names = new ArrayList<String>();
        for (String m : f.members) {
            names.add(name(m));
        }
        String shown = names.size() <= 12 ? join(names)
                : String.join(", ", names.subList(0, 12)) + " and " + (names.size() - 12) + " more";
        if (count) {
            String does = names.size() == 1 ? that.replaceAll("\\bare\\b", "is").replaceAll("\\buse\\b", "uses")
                    .replaceAll("\\bhave\\b", "has").replaceAll("\\bborder\\b", "borders") : that;
            return new Result("Of the " + kind.many + " I know, " + names.size() + " " + does + ": " + shown + ".", steps);
        }
        return new Result(cap(set) + ": " + shown + " (" + names.size() + ").", steps);
    }

    private static String superlativeWord(BuiltIn.About a, boolean more) {
        switch (a) {
            case POPULATION:
                return more ? "most people" : "fewest people";
            case AREA:
            case DIAMETER:
                return more ? "biggest" : "smallest";
            case MASS:
                return more ? "heaviest" : "lightest";
            case DENSITY:
                return more ? "densest" : "least dense";
            case MELT:
                return more ? "highest melting point" : "lowest melting point";
            case BOIL:
                return more ? "highest boiling point" : "lowest boiling point";
            case ORDER:
                return more ? "furthest from the Sun" : "closest to the Sun";
            default:
                return more ? "most" : "least";
        }
    }

    /**
     * A chain: a thing named by another fact ("the currency of the country whose capital is Accra", "what continent is
     * the country with the capital Nairobi in", "which country's capital is Abuja and what is its population").
     */
    Result chain(String q) {
        Matcher m = Pattern.compile("\\b(?:country|nation|one)\\s+(?:whose|with|that has|which has|having|has)\\s+(?:the\\s+|a\\s+|its\\s+)?"
                + "(capital|currency|calling code|internet domain|capital city)\\s+(?:is\\s+|of\\s+|as\\s+)?(.+?)(?:\\s+(?:and|is|in|what|which|has)\\b.*)?$")
                .matcher(q);
        if (!m.find()) {
            return null;
        }
        String by = m.group(1).startsWith("capital") ? "capital" : m.group(1);
        String rest = m.group(2).trim();
        String country = null;
        List<String> steps = new ArrayList<String>();
        steps.add("Breaking the question into steps");
        if ("capital".equals(by)) {
            for (int words = Math.min(4, rest.split(" ").length); words >= 1 && country == null; words--) {
                String city = String.join(" ", Arrays.asList(rest.split(" ")).subList(0, words));
                country = k.countryOfCapital(city);
            }
        } else {
            BuiltIn.About a = BuiltIn.About.named(by);
            for (String key : k.keys()) {
                for (String v : k.get(key, a)) {
                    String lv = v.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{Nd} ]", " ").trim();
                    if (!lv.isEmpty() && (" " + rest + " ").contains(" " + lv + " ")) {
                        country = key;
                    }
                }
            }
        }
        steps.add("Finding the country whose " + by + " is that");
        if (country == null) {
            return new Result("I don't know a country whose " + by + " is " + rest + ".", steps);
        }
        // what is asked about that country: the question without the clause that named it
        String asked = (q.substring(0, m.start()) + " " + (m.end(2) < q.length() ? q.substring(m.end(2)) : "")).trim();
        BuiltIn.About want = null;
        for (BuiltIn.About a : BuiltIn.About.values()) {
            if (a != BuiltIn.About.POPULATION_YEAR && a.asks.matcher(asked).find() && !a.name.startsWith(by)) {
                want = a;
                break;
            }
        }
        String first = "The country whose " + by + " is " + capWords(rest) + " is " + name(country) + ".";
        if (want == null) {
            return new Result(first, steps);
        }
        steps.add("Reading its " + want.name);
        String said = k.answer(want.name.equals("capital") ? "capital of " + name(country) : want.name + " of " + name(country));
        if (said == null) {
            return new Result(first + " I don't have its " + want.name + ".", steps);
        }
        return new Result(first + " " + said, steps);
    }

    /** One property of several things ("the capitals of Ghana and Togo", "populations of Nigeria, Ghana and Kenya"). */
    Result each(String q) {
        if (!q.matches(".*\\b(and|or)\\b.*")) {
            return null;
        }
        // "the capitals of Ghana and Togo": the plural asks the same as the singular
        q = q.replaceAll("\\bcapitals\\b", "capital").replaceAll("\\bpopulations\\b", "population")
                .replaceAll("\\bcurrencies\\b", "currency").replaceAll("\\bcontinents\\b", "continent")
                .replaceAll("\\bareas\\b", "area").replaceAll("\\bsymbols\\b", "symbol");
        Kind[] kind = new Kind[1];
        List<String> things = named(q, kind);
        if (things.size() == 1) {
            return several(q, things.get(0)); // "the capital and currency of Ghana"
        }
        if (things.size() < 2) {
            return null;
        }
        BuiltIn.About want = null;
        for (BuiltIn.About a : BuiltIn.About.values()) {
            if (a != BuiltIn.About.POPULATION_YEAR && a.asks.matcher(q).find()) {
                boolean any = false;
                for (String t : things) {
                    any |= !k.get(t, a).isEmpty();
                }
                if (any) {
                    want = a;
                    break;
                }
            }
        }
        if (want == null) {
            return null;
        }
        List<String> steps = new ArrayList<String>(Arrays.asList("Breaking the question into steps", "Finding " + names(things),
                "Reading each one's " + want.name));
        if (q.matches(".*\\b(combined|together|total|altogether|in all|sum)\\b.*")
                && (want == BuiltIn.About.POPULATION || want == BuiltIn.About.AREA)) {
            double sum = 0;
            StringBuilder parts = new StringBuilder();
            for (String t : things) {
                double v = value(t, want);
                if (Double.isNaN(v)) {
                    return new Result("I can't add them up: I don't have the " + want.name + " of " + name(t) + ".", steps);
                }
                sum += v;
                parts.append(parts.length() == 0 ? "" : "; ").append(cap(name(t))).append(": ").append(said(t, want));
            }
            steps.add("Adding them up");
            String total = want == BuiltIn.About.POPULATION ? "about " + big(sum) + " people"
                    : BuiltIn.grouped(String.valueOf(Math.round(sum))) + " square kilometres";
            return new Result("Together, " + names(things) + " have " + total + ". " + parts + ".", steps);
        }
        StringBuilder out = new StringBuilder();
        for (String t : things) {
            String said = k.answer(want.name + " of " + name(t));
            out.append(out.length() == 0 ? "" : "\n").append(said != null ? said : "I don't have the " + want.name + " of " + name(t) + ".");
        }
        return new Result(out.toString(), steps);
    }

    /**
     * A question about a continent or region as if it were a country: its population or area is its countries' added
     * up; it has no capital (that premise is corrected); its currencies and languages are the most common among them.
     */
    Result wholePlace(String q) {
        if (!k.mentions(q).isEmpty() || kindAsked(q) != null && !q.matches(".*\\b(of|in)\\b.*")) {
            return null;
        }
        Filtered f = filter(q, Kind.COUNTRY);
        if (f.said.isEmpty() || !f.said.get(0).startsWith("are in ") || f.members.isEmpty()) {
            return null;
        }
        String place = f.said.get(0).substring("are in ".length());
        BuiltIn.About asked = null;
        for (BuiltIn.About a : new BuiltIn.About[] {BuiltIn.About.POPULATION, BuiltIn.About.AREA, BuiltIn.About.CAPITAL,
                BuiltIn.About.CURRENCY, BuiltIn.About.LANGUAGE}) {
            if (a.asks.matcher(q).find()) {
                asked = a;
                break;
            }
        }
        if (asked == null) {
            return null;
        }
        List<String> steps = new ArrayList<String>(Arrays.asList("Breaking the question into steps",
                "Gathering the countries that are in " + place));
        int n = f.members.size();
        if (asked == BuiltIn.About.POPULATION || asked == BuiltIn.About.AREA) {
            steps.add("Adding up each one's " + asked.name);
            double sum = 0;
            int missing = 0;
            for (String m : f.members) {
                double v = value(m, asked);
                if (Double.isNaN(v)) {
                    missing++;
                } else {
                    sum += v;
                }
            }
            String total = asked == BuiltIn.About.POPULATION ? "about " + big(sum) + " people"
                    : "about " + BuiltIn.grouped(String.valueOf(Math.round(sum))) + " square kilometres";
            return new Result(place + " is not one country: its " + n + " countries together have " + total
                    + (missing > 0 ? " (" + missing + " without a figure left out)" : "") + ".", steps);
        }
        java.util.Map<String, Integer> tally = new java.util.LinkedHashMap<String, Integer>();
        for (String m : f.members) {
            for (String v : k.get(m, asked == BuiltIn.About.CAPITAL ? BuiltIn.About.CURRENCY : asked)) {
                tally.merge(v, 1, Integer::sum);
            }
        }
        if (asked == BuiltIn.About.CAPITAL) {
            return new Result(place + " is a continent or region, not a country, so it has no capital of its own. It has " + n
                    + " countries, each with its own capital: ask me about any of them.", steps);
        }
        steps.add("Counting what each country uses");
        List<java.util.Map.Entry<String, Integer>> top = new ArrayList<java.util.Map.Entry<String, Integer>>(tally.entrySet());
        top.sort((x, y) -> y.getValue() - x.getValue());
        StringBuilder most = new StringBuilder();
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            most.append(i == 0 ? "" : i == Math.min(3, top.size()) - 1 ? " and " : ", ").append(top.get(i).getKey())
                    .append(" (").append(top.get(i).getValue()).append(")");
        }
        return new Result(place + " has no single " + asked.name + ": its " + n + " countries use " + tally.size() + " between them."
                + " The most common: " + most + ".", steps);
    }

    /** Several properties of one thing ("the capital and currency of Ghana"); null when only one is asked. */
    private Result several(String q, String thing) {
        if (!q.matches(".*\\band\\b.*")) {
            return null;
        }
        List<BuiltIn.About> asked = new ArrayList<BuiltIn.About>();
        for (BuiltIn.About a : BuiltIn.About.values()) {
            if (a != BuiltIn.About.POPULATION_YEAR && a.asks.matcher(q).find() && !k.get(thing, a).isEmpty()) {
                asked.add(a);
            }
        }
        if (asked.size() < 2) {
            return null;
        }
        List<String> steps = new ArrayList<String>(Arrays.asList("Breaking the question into steps", "Finding " + name(thing)));
        StringBuilder out = new StringBuilder();
        for (BuiltIn.About a : asked) {
            steps.add("Reading its " + a.name);
            String said = k.answer(a.name + " of " + k.name(thing));
            if (said != null) {
                out.append(out.length() == 0 ? "" : "\n").append(said);
            }
        }
        return out.length() == 0 ? null : new Result(out.toString(), steps);
    }

    // ---- words

    private String names(List<String> things) {
        List<String> n = new ArrayList<String>();
        for (String t : things) {
            n.add(name(t));
        }
        return join(n);
    }

    /** A thing's name as said in a sentence: planets with a capital ("Mercury"), elements as they are ("gold"). */
    private String name(String key) {
        String n = k.name(key);
        return !k.get(key, BuiltIn.About.ORDER).isEmpty() ? cap(n) : n;
    }

    private static String join(List<String> n) {
        if (n.size() <= 1) {
            return n.isEmpty() ? "" : n.get(0);
        }
        return String.join(", ", n.subList(0, n.size() - 1)) + " and " + n.get(n.size() - 1);
    }

    private static List<String> lower(List<String> v) {
        List<String> out = new ArrayList<String>();
        for (String s : v) {
            out.add(s.toLowerCase(Locale.US));
        }
        return out;
    }

    private static boolean anyContains(List<String> values, String part) {
        for (String v : values) {
            if ((" " + v + " ").contains(" " + part + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String capWords(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            b.append(b.length() == 0 ? "" : " ").append(cap(w));
        }
        return b.toString();
    }
}
