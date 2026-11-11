package ai.ibiko.dvine1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What DVINE 1 knows the moment it is installed, before it has learned or looked up anything: facts that ship inside
 * the app ({@code assets/knowledge/world_facts.tsv}: the countries from mledoze/countries, ODbL, see
 * {@code assets/knowledge/WORLD_FACTS_LICENSE.txt}, and the 118 elements; {@code tools/build_world_facts.py} can
 * rebuild it from Wikidata). One line a fact: the thing, what about it, the value. A question is answered
 * by finding the thing it names and what it asks about that thing, however it is worded ("capital of Nigeria",
 * "what's Nigeria's capital city", "which country has Abuja as its capital"). Read once, on first use. Pure Java.
 */
public final class BuiltIn {
    /** What can be asked about a thing, the words that ask it, and how the answer is said. */
    enum About {
        CAPITAL("capital", "\\b(capital|capital city|seat of government)\\b", "The capital of %s is %s."),
        POPULATION("population", "\\b(population|how many people|number of people|inhabitants|populous)\\b", "%s has a population of about %s."),
        CURRENCY("currency", "\\b(currency|money|legal tender)\\b", "The currency of %s is the %s."),
        CONTINENT("continent", "\\b(continent|which part of the world)\\b", "%s is in %s."),
        LANGUAGE("official language", "\\b(official languages?|languages?|speak|spoken)\\b", "The official language of %s is %s."),
        AREA("area", "\\b(area|how (big|large)|size|square (kilometres|kilometers|km))\\b", "%s covers about %s square kilometres."),
        DEMONYM("demonym", "\\b(what (are|is) (the )?(people|person|someone|citizens?) (from|of|in)|demonym|nationality)\\b",
                "Someone from %s is %s."),
        NEIGHBOUR("neighbour", "\\b(border(s|ing)?|neighbou?rs?|next to|surround)\\b", "%s borders %s."),
        LANDLOCKED("landlocked", "\\b(landlocked|coast(line)?|access to the sea|by the sea)\\b", "%s"),
        /** The year a population figure is from: said with the figure, never asked for on its own. */
        POPULATION_YEAR("population year", "(?!)", "%s %s"),
        SYMBOL("symbol", "\\b(chemical symbol|symbol)\\b", "The chemical symbol of %s is %s."),
        NUMBER("atomic number", "\\b(atomic number|proton number|how many protons)\\b", "The atomic number of %s is %s."),
        // country_more.tsv
        CALLING_CODE("calling code", "\\b(calling code|dialling code|dialing code|country code|phone code|international code)\\b",
                "The calling code of %s is %s."),
        DOMAIN("internet domain", "\\b(internet domain|domain name|top level domain|tld|web domain|domain extension)\\b",
                "The internet domain of %s is %s."),
        REGION("region", "\\b(region|subregion|sub region)\\b", "%s is in %s."),
        ISO("iso code", "\\b(iso code|iso codes|iso 3166|two letter code|three letter code|country abbreviation)\\b",
                "The ISO codes of %s are %s."),
        UN("un member", "\\b(un member|member of the un|member of the united nations|united nations member|in the un)\\b", "%s"),
        // elements_more.tsv
        MASS("atomic mass", "\\b(atomic mass|atomic weight|relative atomic mass|molar mass)\\b", "The atomic mass of %s is %s."),
        CATEGORY("element category", "\\b(category|what kind of element|what type of element|metal or|a metal|metalloid|noble gas|halogen)\\b",
                "%s is a %s."),
        PHASE("phase", "\\b(phase|state of matter|solid liquid or gas|at room temperature)\\b", "%s is a %s at room temperature."),
        DISCOVERED("discovered by", "\\b(discovered|discoverer|discovery|who found)\\b", "%s was discovered by %s."),
        MELT("melting point", "\\b(melting point|melts at|melt at|melting temperature)\\b", "%s melts at %s."),
        BOIL("boiling point", "\\b(boiling point|boils at|boil at|boiling temperature)\\b", "%s boils at %s."),
        DENSITY("density", "\\b(density|how dense)\\b", "The density of %s is %s."),
        CONFIG("electron configuration", "\\b(electron configuration|electronic configuration|electron arrangement)\\b",
                "The electron configuration of %s is %s."),
        GROUP_PERIOD("group and period", "\\b(which group|what group|which period|what period|group number|period number|in the periodic table)\\b",
                "%s is in %s of the periodic table."),
        // local_facts.tsv: the planets
        ORDER("order from the sun", "\\b(from the sun|planet number|order of the planets|position in the solar system)\\b",
                "%s is the %s planet from the Sun."),
        DIAMETER("diameter", "\\b(diameter|how wide|how big across|how big)\\b", "%s is about %s kilometres across."),
        ORBIT("orbital period", "\\b(orbital period|year on|long is a year|orbit the sun|round the sun|around the sun|revolve)\\b",
                "A year on %s lasts about %s."),
        PLANET("planet type", "\\b(what (kind|type|sort) of planet|gas giant|ice giant|rocky planet|terrestrial planet)\\b", "%s is %s.");

        final String name;
        final java.util.regex.Pattern asks;
        final String said;

        About(String name, String asks, String said) {
            this.name = name;
            this.asks = java.util.regex.Pattern.compile(asks);
            this.said = said;
        }

        static About named(String n) {
            for (About a : values()) {
                if (a.name.equals(n)) {
                    return a;
                }
            }
            return null;
        }
    }

    /** thing (lower case) → what is known about it; the thing's own spelling; and capital → country, for the reverse. */
    private final Map<String, Map<About, List<String>>> facts = new LinkedHashMap<String, Map<About, List<String>>>();
    private final Map<String, String> shown = new LinkedHashMap<String, String>();
    private final Map<String, String> capitalOf = new LinkedHashMap<String, String>();
    private int count;

    /** One or several knowledge files, read in order (a capital already known to a country stays that country's). */
    public BuiltIn(InputStream... tsvs) {
        if (tsvs == null) {
            return; // no file: nothing known
        }
        for (InputStream t : tsvs) {
            read(t);
        }
    }

    private void read(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                About a = p.length == 3 ? About.named(p[1].trim()) : null;
                if (a == null || p[0].trim().isEmpty() || p[2].trim().isEmpty()) {
                    continue;
                }
                String key = p[0].trim().toLowerCase(Locale.US);
                Map<About, List<String>> of = facts.get(key);
                if (of == null) {
                    of = new LinkedHashMap<About, List<String>>();
                    facts.put(key, of);
                    shown.put(key, p[0].trim());
                }
                List<String> values = of.get(a);
                if (values == null) {
                    values = new ArrayList<String>();
                    of.put(a, values);
                }
                if (!values.contains(p[2].trim()) && values.size() < 20) {
                    values.add(p[2].trim());
                    count++;
                }
                if (a == About.CAPITAL) {
                    capitalOf.putIfAbsent(p[2].trim().toLowerCase(Locale.US), key); // Abuja stays Nigeria's, not the FCT's
                }
            }
        } catch (IOException e) {
            // what was read so far is kept
        }
    }

    /** How many facts shipped with the app. */
    public int size() {
        return count;
    }

    /** The answer to a question about something it shipped with, as a sentence; null when it has none. */
    public String answer(String question) {
        if (question == null || facts.isEmpty()) {
            return null;
        }
        String q = " " + question.toLowerCase(Locale.US).replaceAll("[’']s\\b", " ").replaceAll("[^\\p{L}\\p{Nd} -]", " ").replaceAll("\\s+", " ") + " ";
        String thing = longest(q, facts);
        About about = null;
        // the first kind of question the words ask that this thing has an answer for ("how big" is a country's area
        // and a planet's diameter; "mercury" is an element and a planet)
        for (About a : About.values()) {
            if (a.asks.matcher(q).find()) {
                if (about == null) {
                    about = a;
                }
                List<String> v = thing == null ? null : facts.get(thing).get(a);
                if (v != null && !v.isEmpty()) {
                    about = a;
                    break;
                }
            }
        }
        if (about == null) {
            return null;
        }
        if (thing == null && about == About.CAPITAL) {
            String city = longest(q, capitalOf); // "Abuja is the capital of which country?"
            if (city != null) {
                String country = capitalOf.get(city);
                return facts.get(country).get(About.CAPITAL).get(0) + " is the capital of " + shown.get(country) + ".";
            }
        }
        List<String> values = thing == null ? null : facts.get(thing).get(about);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String name = shown.get(thing);
        if (about == About.UN) {
            return "yes".equals(values.get(0)) ? name + " is a member of the United Nations." : name + " is not a member of the United Nations.";
        }
        if (about == About.CATEGORY || about == About.PHASE || about == About.DISCOVERED || about == About.DIAMETER
                || about == About.MELT || about == About.BOIL) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1); // the sentence starts with it
        }
        if (about == About.DISCOVERED && values.get(0).matches("(?i).*(\\bbc\\b|ancient|prehistoric|antiquity|middle east|^\\d).*")) {
            return name + " has been known since " + values.get(0) + "."; // iron, gold, copper: known since ancient times
        }
        if (about == About.CATEGORY) {
            String kind = values.get(0);
            return name + " is " + (kind.matches("(?i)^[aeiou].*") ? "an " : "a ") + kind + ".";
        }
        if (about == About.LANDLOCKED) {
            return "yes".equals(values.get(0)) ? name + " is landlocked: it has no coastline." : name + " is not landlocked: it has a coastline.";
        }
        if (about == About.NEIGHBOUR && values.size() > 1) {
            return name + " borders " + String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.get(values.size() - 1) + ".";
        }
        if (about == About.POPULATION) {
            List<String> year = facts.get(thing).get(About.POPULATION_YEAR);
            String said = String.format(Locale.US, about.said, name, grouped(values.get(0)));
            return year == null || year.isEmpty() ? said : said.substring(0, said.length() - 1) + " (" + year.get(0) + " estimate).";
        }
        if (about == About.AREA) {
            return String.format(Locale.US, about.said, name, grouped(values.get(0)));
        }
        if (values.size() > 1 && (about == About.LANGUAGE || about == About.CURRENCY || about == About.CONTINENT || about == About.CAPITAL)) {
            String all = String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.get(values.size() - 1);
            return about == About.LANGUAGE ? "The official languages of " + name + " are " + all + "."
                    : about == About.CAPITAL ? name + " has more than one capital: " + all + "."
                    : about == About.CURRENCY ? "The currencies of " + name + " are the " + all + "." : name + " is in " + all + ".";
        }
        return String.format(Locale.US, about.said, name, values.get(0));
    }

    /** What a sentence can be judged on, and the words that show it states one (G8). */
    private static final java.util.Map<About, java.util.regex.Pattern> STATES = new java.util.EnumMap<About, java.util.regex.Pattern>(About.class);

    static {
        STATES.put(About.CAPITAL, java.util.regex.Pattern.compile("\\bcapital\\b"));
        STATES.put(About.CURRENCY, java.util.regex.Pattern.compile("\\b(currency|legal tender)\\b"));
        STATES.put(About.LANGUAGE, java.util.regex.Pattern.compile("\\bofficial languages?\\b"));
        STATES.put(About.CONTINENT, java.util.regex.Pattern.compile("\\b(continent|is in|lies in|located in|is part of)\\b"));
        STATES.put(About.SYMBOL, java.util.regex.Pattern.compile("\\bsymbol\\b"));
        STATES.put(About.NUMBER, java.util.regex.Pattern.compile("\\batomic number\\b"));
    }

    private final Map<About, java.util.Set<String>> allValues = new java.util.EnumMap<About, java.util.Set<String>>(About.class);

    private synchronized java.util.Set<String> valuesOf(About a) {
        java.util.Set<String> v = allValues.get(a);
        if (v == null) {
            v = new java.util.HashSet<String>();
            for (Map<About, List<String>> of : facts.values()) {
                List<String> l = of.get(a);
                if (l != null) {
                    v.addAll(l);
                }
            }
            allValues.put(a, v);
        }
        return v;
    }

    private static String plain(String s) {
        return " " + s.toLowerCase(Locale.US).replaceAll("[\u2019']s\\b", " ").replaceAll("[^\\p{L}\\p{Nd} -]", " ").replaceAll("\\s+", " ") + " ";
    }

    /** Whether a value stands in the sentence as whole words (a symbol, case and all). */
    private static boolean says(About a, String sentence, String q, String value) {
        if (a == About.SYMBOL) {
            return java.util.regex.Pattern.compile("(?<![\\p{L}])" + java.util.regex.Pattern.quote(value) + "(?![\\p{L}])").matcher(sentence).find();
        }
        return q.contains(plain(value));
    }

    /**
     * G8 (formulas/Truth): one sentence judged against what shipped with the app. TRUE when it names the right value,
     * FALSE when it names another value of the same kind (another capital, currency, continent, symbol or number) for
     * the one thing it is about, UNKNOWN otherwise, or when it names more than one thing.
     */
    public ai.ibiko.dvine1.formulas.Truth.Checked check(String sentence) {
        ai.ibiko.dvine1.formulas.Truth.Checked unknown = ai.ibiko.dvine1.formulas.Truth.Checked.unknown(sentence);
        if (sentence == null || facts.isEmpty() || !ai.ibiko.dvine1.formulas.Truth.claims(sentence)) {
            return unknown;
        }
        String q = plain(sentence);
        if (q.matches(".* (was|were|had been) .*")) {
            return unknown; // the past: a capital or a currency that changed
        }
        List<String> named = new ArrayList<String>();
        for (String n : facts.keySet()) {
            if (n.length() >= 3 && q.contains(" " + n + " ")) {
                named.add(n);
            }
        }
        named.removeIf(n -> {
            for (String o : named) {
                if (!o.equals(n) && o.contains(n)) {
                    return true; // "Sudan" inside "South Sudan"
                }
            }
            return false;
        });
        if (named.size() != 1) {
            return unknown;
        }
        String thing = named.get(0);
        for (Map.Entry<About, java.util.regex.Pattern> e : STATES.entrySet()) {
            About a = e.getKey();
            List<String> right = facts.get(thing).get(a);
            if (right == null || right.isEmpty() || !e.getValue().matcher(q).find()) {
                continue;
            }
            for (String v : right) {
                if (says(a, sentence, q, v)) {
                    return new ai.ibiko.dvine1.formulas.Truth.Checked(ai.ibiko.dvine1.formulas.Truth.Verdict.TRUE, sentence, null, "built-in reference");
                }
            }
            String claimed = a == About.CAPITAL ? claimedCapital(q, thing) : null;
            if (claimed != null) {
                return new ai.ibiko.dvine1.formulas.Truth.Checked(ai.ibiko.dvine1.formulas.Truth.Verdict.FALSE, sentence,
                        answer("what is the capital of " + shown.get(thing)), "built-in reference");
            }
            for (String other : valuesOf(a)) {
                if (!right.contains(other) && !plain(other).equals(" " + thing + " ") && says(a, sentence, q, other)) {
                    String fix = answer("what is the " + a.name + " of " + shown.get(thing));
                    return new ai.ibiko.dvine1.formulas.Truth.Checked(ai.ibiko.dvine1.formulas.Truth.Verdict.FALSE, sentence,
                            fix, "built-in reference");
                }
            }
        }
        return unknown;
    }

    private static final java.util.Set<String> NOT_A_PLACE = new java.util.HashSet<String>(java.util.Arrays.asList("it", "this", "that",
            "which", "what", "city", "town", "here", "there", "its", "the", "a", "an", "and", "also", "now", "today", "so", "fact", "in"));

    /** The city a sentence names as a country's capital ("Lagos is the capital of Nigeria"), or null. */
    static String claimedCapital(String q, String thing) {
        String t = java.util.regex.Pattern.quote(thing), w = "([\\p{L}-]+(?: [\\p{L}-]+)?)";
        String[] forms = {"capital (?:city )?of (?:the )?" + t + " is (?:the city of )?" + w,
                " " + t + " capital (?:city )?is (?:the city of )?" + w, w + " is (?:the |its )?capital (?:city )?of (?:the )?" + t};
        for (String f : forms) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(f).matcher(q);
            if (m.find()) {
                java.util.List<String> words = new java.util.ArrayList<String>(java.util.Arrays.asList(m.group(1).trim().split(" ")));
                words.removeIf(NOT_A_PLACE::contains);
                if (!words.isEmpty()) {
                    return String.join(" ", words);
                }
            }
        }
        return null;
    }

    /** The longest known name that stands as whole words in the question, so "South Sudan" is not read as "Sudan". */
    private static String longest(String q, Map<String, ?> names) {
        String best = null;
        for (String n : names.keySet()) {
            if (n.length() >= 3 && (best == null || n.length() > best.length()) && q.contains(" " + n + " ")) {
                best = n;
            }
        }
        return best;
    }

    /** 223804632 → "223,804,632"; 923768.0 → "923,768". */
    static String grouped(String number) {
        try {
            return String.format(Locale.US, "%,d", Math.round(Double.parseDouble(number)));
        } catch (NumberFormatException e) {
            return number;
        }
    }
}
