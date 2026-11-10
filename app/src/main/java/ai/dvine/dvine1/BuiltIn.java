package ai.dvine.dvine1;

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
        SYMBOL("symbol", "\\b(chemical symbol|symbol)\\b", "The chemical symbol of %s is %s."),
        NUMBER("atomic number", "\\b(atomic number|proton number|how many protons)\\b", "The atomic number of %s is %s.");

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

    public BuiltIn(InputStream tsv) {
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
                    capitalOf.put(p[2].trim().toLowerCase(Locale.US), key);
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
        About about = null;
        for (About a : About.values()) {
            if (a.asks.matcher(q).find()) {
                about = a;
                break;
            }
        }
        if (about == null) {
            return null;
        }
        String thing = longest(q, facts);
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
        if (about == About.LANDLOCKED) {
            return "yes".equals(values.get(0)) ? name + " is landlocked: it has no coastline." : name + " is not landlocked: it has a coastline.";
        }
        if (about == About.NEIGHBOUR && values.size() > 1) {
            return name + " borders " + String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.get(values.size() - 1) + ".";
        }
        if (about == About.POPULATION || about == About.AREA) {
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
