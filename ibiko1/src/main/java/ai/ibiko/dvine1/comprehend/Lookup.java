package ai.ibiko.dvine1.comprehend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Questions answered from the dictionary and common sense alone, offline: what a word means, words for it, its
 * opposite, what a thing is used for, what it can do, its parts, where it is found, what it is made of. A sentence, or
 * null when the question is not one of these or nothing is known. Pure Java.
 */
public final class Lookup {
    private final Dictionary dictionary;
    private final Concepts concepts;

    public Lookup(Dictionary dictionary, Concepts concepts) {
        this.dictionary = dictionary;
        this.concepts = concepts;
    }

    private static final String A = "(?:(?:a|an|the|some)\\s+)?";
    private static final String X = "['\"“]?([a-z][a-z' -]{0,30}?)['\"”]?";
    private static final Pattern MEAN = Pattern.compile("^(?:what does|what do)\\s+(?:the word\\s+)?" + X + "\\s+mean$"
            + "|^(?:define|definition of|meaning of|what is the meaning of|what's the meaning of|what is the definition of)\\s+(?:the word\\s+)?" + X + "$");
    private static final Pattern SYNONYM = Pattern.compile("^(?:what is |what's |give me |tell me )?(?:a |an |another )?(?:synonyms?|other words?|another word|word)\\s+(?:of|for)\\s+" + X + "$");
    private static final Pattern OPPOSITE = Pattern.compile("^(?:what is |what's )?(?:the )?(?:opposite|antonym)\\s+of\\s+" + X + "$");
    private static final Pattern USED_FOR = Pattern.compile("^what (?:is|are)\\s+" + A + X + "\\s+(?:used )?for$|^what do (?:you|people|we) use\\s+" + A + X + "\\s+for$");
    private static final Pattern CAN_DO = Pattern.compile("^what can\\s+" + A + X + "\\s+do$");
    private static final Pattern PARTS = Pattern.compile("^(?:what are )?(?:the )?parts of\\s+" + A + X + "$|^what does\\s+" + A + X + "\\s+have$");
    private static final Pattern WHERE = Pattern.compile("^where (?:would|can|do|could) (?:you|i|we|one) (?:find|get|see)\\s+" + A + X + "$|^where (?:is|are)\\s+" + A + X + "\\s+(?:usually|normally|found|kept)$");
    private static final Pattern MADE_OF = Pattern.compile("^what (?:is|are)\\s+" + A + X + "\\s+made (?:of|from)$");

    public String answer(String question) {
        if (question == null || dictionary == null) {
            return null;
        }
        String q = question.toLowerCase(Locale.US).replace('’', '\'').replaceAll("[?.!]+$", "").replaceAll("\\s+", " ").trim();
        Matcher m;
        if ((m = MEAN.matcher(q)).find()) {
            return meaning(group(m));
        }
        if ((m = SYNONYM.matcher(q)).find()) {
            String w = group(m);
            List<String> words = new ArrayList<String>();
            for (Dictionary.Sense s : dictionary.senses(w)) {
                for (String y : s.synonyms) {
                    if (!words.contains(y) && words.size() < 8) {
                        words.add(y);
                    }
                }
            }
            return words.isEmpty() ? null : "Words that can mean " + w + ": " + String.join(", ", words) + ".";
        }
        if ((m = OPPOSITE.matcher(q)).find()) {
            String w = group(m);
            for (Dictionary.Sense s : dictionary.senses(w)) {
                if (!s.opposites.isEmpty()) {
                    return "The opposite of " + w + " is " + String.join(" or ", s.opposites) + ".";
                }
            }
            return null;
        }
        if (concepts == null || !concepts.present()) {
            return null;
        }
        if ((m = USED_FOR.matcher(q)).find()) {
            return list(group(m), "UsedFor", "is used for", 5);
        }
        if ((m = CAN_DO.matcher(q)).find()) {
            return list(group(m), "CapableOf", "can", 5);
        }
        if ((m = PARTS.matcher(q)).find()) {
            String w = group(m);
            List<String> parts = concepts.of(w, "HasPart", 10);
            for (String h : concepts.of(w, "HasA", 6)) {
                if (!parts.contains(h) && parts.size() < 12) {
                    parts.add(h);
                }
            }
            return parts.isEmpty() ? null : article(w) + " has " + join(parts) + ".";
        }
        if ((m = WHERE.matcher(q)).find()) {
            String w = group(m);
            List<String> at = concepts.of(w, "AtLocation", 5);
            return at.isEmpty() ? null : "You would usually find " + article(w).toLowerCase(Locale.US) + " in or at " + join(at) + ".";
        }
        if ((m = MADE_OF.matcher(q)).find()) {
            return list(group(m), "MadeOf", "is made of", 4);
        }
        return null;
    }

    /** A word's meanings, up to three, each with its part of speech. */
    public String meaning(String word) {
        List<Dictionary.Sense> senses = dictionary.senses(word);
        if (senses.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Dictionary.Sense s : senses) {
            if (n == 3) {
                break;
            }
            b.append(n == 0 ? "" : " ").append(n + 1).append(". (").append(s.pos).append(") ").append(cap(s.definition)).append('.');
            if (n == 0 && !s.synonyms.isEmpty()) {
                b.append(" Also: ").append(String.join(", ", s.synonyms.subList(0, Math.min(4, s.synonyms.size())))).append('.');
            }
            n++;
        }
        return cap(senses.get(0).word) + ": " + b;
    }

    private String list(String w, String rel, String verb, int most) {
        List<String> ends = concepts.of(w, rel, most);
        return ends.isEmpty() ? null : article(w) + " " + verb + " " + join(ends) + ".";
    }

    private static String group(Matcher m) {
        for (int i = m.groupCount(); i >= 1; i--) {
            if (m.group(i) != null) {
                return m.group(i).trim();
            }
        }
        return "";
    }

    private static String join(List<String> all) {
        List<String> xs = new ArrayList<String>(); // "seat" and "seats" said once
        for (String x : all) {
            if (!xs.contains(x) && !xs.contains(x + "s") && !(x.endsWith("s") && xs.contains(x.substring(0, x.length() - 1)))) {
                xs.add(x);
            }
        }
        if (xs.size() <= 1) {
            return xs.isEmpty() ? "" : xs.get(0);
        }
        return String.join(", ", xs.subList(0, xs.size() - 1)) + " and " + xs.get(xs.size() - 1);
    }

    private static String article(String w) {
        return (w.matches("^[aeiou].*") ? "An " : "A ") + w;
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
