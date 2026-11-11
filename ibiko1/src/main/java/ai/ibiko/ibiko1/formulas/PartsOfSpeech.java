package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Grammar, the parts of speech: which word in a sentence is a noun, a pronoun, a verb, an adjective, an adverb, a
 * preposition, a conjunction, a determiner, an interjection or a number. Ibiko 1 reads every sentence with this formula
 * and learns from it (what a word was each time it was sure), so it tags new words better the more it reads. Pure
 * Java.
 *
 * <pre>
 * 1  CLOSED WORDS   pronouns, determiners, prepositions, conjunctions, auxiliaries, interjections: a fixed list
 * 2  NUMBERS        "12", "3.5", "twelve", "first"                                  → numeral
 * 3  LEARNED        what this word was most of the times Ibiko was sure (Wiktionary and usage)
 * 4  ENDINGS        -ly adverb · -tion -ness -ment -ity -ship -ism -er(s) noun · -ous -ful -ive -able -al -ic -less
 *                   adjective · -ize -ify -ate verb · -ed -ing verb (or adjective before a noun)
 * 5  NEIGHBOURS     after a determiner or adjective: noun (or adjective when a noun follows) · after "to" or a modal:
 *                   verb · after a subject pronoun: verb · a capitalised word inside a sentence: proper noun
 * 6  OTHERWISE      noun (most new words are)
 * </pre>
 */
public final class PartsOfSpeech {
    private PartsOfSpeech() {
    }

    public enum Tag { NOUN, PROPER_NOUN, PRONOUN, VERB, AUXILIARY, ADJECTIVE, ADVERB, PREPOSITION, CONJUNCTION, DETERMINER,
        INTERJECTION, NUMERAL, PUNCTUATION }

    /** One word read: its text, its part of speech, and which rule decided it (how sure). */
    public static final class Word {
        public final String text;
        public final Tag tag;
        /** 1 closed list or number, 3 learned, 4 ending, 5 neighbours, 6 guessed. Lower is surer. */
        public final int rule;

        Word(String text, Tag tag, int rule) {
            this.text = text;
            this.tag = tag;
            this.rule = rule;
        }

        /** Sure enough to learn from (a closed word, a number, a learned word, a clear ending or neighbour). */
        public boolean sure() {
            return rule <= 5;
        }

        @Override
        public String toString() {
            return text + "/" + tag.name().toLowerCase(Locale.US);
        }
    }

    /** What Ibiko has learned about words: the part of speech it most often was, or null. */
    public interface Known {
        Tag of(String word);
    }

    static final Set<String> PRONOUNS = set("i", "me", "my", "mine", "myself", "you", "your", "yours", "yourself", "yourselves",
            "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "we", "us", "our", "ours",
            "ourselves", "they", "them", "their", "theirs", "themselves", "who", "whom", "whose", "which", "what", "this",
            "that", "these", "those", "someone", "somebody", "something", "anyone", "anybody", "anything", "everyone",
            "everybody", "everything", "nobody", "nothing", "one");
    static final Set<String> SUBJECTS = set("i", "you", "he", "she", "it", "we", "they", "who");
    static final Set<String> DETERMINERS = set("a", "an", "the", "every", "each", "some", "any", "no", "all", "both", "either",
            "neither", "many", "much", "few", "several", "most", "more", "less", "another", "other", "such");
    static final Set<String> PREPOSITIONS = set("in", "on", "at", "by", "for", "with", "about", "against", "between", "into",
            "through", "during", "before", "after", "above", "below", "to", "from", "up", "down", "of", "off", "over",
            "under", "near", "around", "across", "behind", "beside", "beyond", "inside", "outside", "since", "until",
            "toward", "towards", "upon", "within", "without", "via", "per", "like", "among", "along", "despite");
    static final Set<String> CONJUNCTIONS = set("and", "but", "or", "nor", "so", "yet", "because", "although", "though",
            "while", "whereas", "if", "unless", "when", "whenever", "where", "wherever", "whether", "than", "as", "once");
    static final Set<String> AUXILIARIES = set("be", "am", "is", "are", "was", "were", "been", "being", "have", "has", "had",
            "having", "do", "does", "did", "will", "would", "shall", "should", "can", "could", "may", "might", "must", "ought");
    static final Set<String> MODALS = set("will", "would", "shall", "should", "can", "could", "may", "might", "must");
    static final Set<String> INTERJECTIONS = set("hi", "hello", "hey", "oh", "ah", "wow", "ouch", "oops", "hmm", "yes", "no",
            "okay", "ok", "please", "thanks", "bye", "alas", "hurray", "yay");
    static final Set<String> ADVERBS = set("not", "very", "too", "also", "just", "only", "even", "still", "already", "always",
            "never", "often", "sometimes", "usually", "here", "there", "now", "then", "today", "tomorrow", "yesterday", "soon",
            "again", "quite", "almost", "rather", "really", "well", "how", "why", "away", "back", "ever", "perhaps", "maybe");
    static final Set<String> NUMBER_WORDS = set("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
            "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand", "million",
            "billion", "first", "second", "third", "fourth", "fifth", "tenth", "hundredth");

    private static Set<String> set(String... w) {
        return new HashSet<String>(Arrays.asList(w));
    }

    /** The words of a sentence, each with its part of speech. {@code learned} may be null. */
    public static List<Word> tag(String sentence, Known learned) {
        List<String> words = new ArrayList<String>();
        for (String w : (sentence == null ? "" : sentence).trim().split("(?<=\\S)(?=[,.!?;:])|\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        Tag[] tags = new Tag[words.size()];
        int[] rule = new int[words.size()];
        // pass 1: what each word is on its own
        for (int i = 0; i < words.size(); i++) {
            String raw = words.get(i), w = lower(raw);
            Tag t;
            int r;
            if (w.matches("[,.!?;:\"'()\\-]+")) {
                t = Tag.PUNCTUATION;
                r = 1;
            } else if (w.matches("\\d[\\d,.:/%-]*") || NUMBER_WORDS.contains(w) && !"one".equals(w) && !"second".equals(w)) {
                t = Tag.NUMERAL;
                r = 1;
            } else if ((t = closed(w)) != null) {
                r = 1;
            } else if (learned != null && (t = learned.of(w)) != null) {
                r = 3;
            } else if ((t = ending(w)) != null) {
                r = 4;
            } else {
                t = Tag.NOUN;
                r = 6;
            }
            tags[i] = t;
            rule[i] = r;
        }
        // pass 2: the neighbours settle what the word alone could not
        for (int i = 0; i < words.size(); i++) {
            if (rule[i] < 4) {
                continue;
            }
            String raw = words.get(i), w = lower(raw);
            String prev = i > 0 ? lower(words.get(i - 1)) : "";
            Tag prevTag = i > 0 ? tags[i - 1] : null;
            Tag nextTag = i + 1 < words.size() ? tags[i + 1] : null;
            boolean sentenceStart = i == 0 || prevTag == Tag.PUNCTUATION && words.get(i - 1).matches("[.!?]");
            if (!sentenceStart && Character.isUpperCase(raw.charAt(0))) {
                tags[i] = Tag.PROPER_NOUN;
                rule[i] = 5;
            } else if ("to".equals(prev) && !w.endsWith("ly") || MODALS.contains(prev) || prevTag == Tag.PRONOUN && SUBJECTS.contains(prev)
                    && rule[i] >= 4) {
                tags[i] = Tag.VERB;
                rule[i] = 5;
            } else if ((prevTag == Tag.DETERMINER || prevTag == Tag.ADJECTIVE || prevTag == Tag.NUMERAL
                    || prevTag == Tag.PRONOUN && prev.matches("my|your|his|her|its|our|their"))
                    && rule[i] >= 5) {
                tags[i] = nextTag == Tag.NOUN && rule[i] == 6 ? Tag.ADJECTIVE : Tag.NOUN;
                rule[i] = 5;
            } else if (prevTag == Tag.AUXILIARY && (w.endsWith("ing") || w.endsWith("ed")) && rule[i] >= 4) {
                tags[i] = Tag.VERB;
                rule[i] = 5;
            } else if (tags[i] == Tag.VERB && w.endsWith("ed") && nextTag == Tag.NOUN && (prevTag == Tag.DETERMINER)) {
                tags[i] = Tag.ADJECTIVE; // "the painted door"
                rule[i] = 5;
            }
        }
        List<Word> out = new ArrayList<Word>();
        for (int i = 0; i < words.size(); i++) {
            out.add(new Word(words.get(i), tags[i], rule[i]));
        }
        return out;
    }

    /** A closed-class word's part of speech, or null. */
    static Tag closed(String w) {
        if (AUXILIARIES.contains(w)) {
            return Tag.AUXILIARY;
        }
        if (DETERMINERS.contains(w) && !"no".equals(w)) {
            return Tag.DETERMINER;
        }
        if (PRONOUNS.contains(w)) {
            return Tag.PRONOUN;
        }
        if (PREPOSITIONS.contains(w)) {
            return Tag.PREPOSITION;
        }
        if (CONJUNCTIONS.contains(w)) {
            return Tag.CONJUNCTION;
        }
        if (ADVERBS.contains(w)) {
            return Tag.ADVERB;
        }
        if (INTERJECTIONS.contains(w)) {
            return Tag.INTERJECTION;
        }
        return null;
    }

    /** What a word's ending says it is, or null. */
    static Tag ending(String w) {
        if (w.length() < 4) {
            return null;
        }
        if (w.endsWith("ly") && !w.endsWith("ply") && !w.matches("(family|only|early|daily|weekly|monthly|yearly|holy|ugly|lovely|friendly|lonely|silly|jelly|belly|rally|ally|fly|reply|supply)")) {
            return Tag.ADVERB;
        }
        if (w.matches(".+(tion|sion|ness|ment|ity|ship|ism|ance|ence|hood|dom|ist|ery|ure|age)s?")) {
            return Tag.NOUN;
        }
        if (w.matches(".+(ous|ful|ive|able|ible|al|ic|less|ish|ary|ent|ant|y)") && !w.endsWith("ey")) {
            return Tag.ADJECTIVE;
        }
        if (w.matches(".+(ize|ise|ify|ate)")) {
            return Tag.VERB;
        }
        if (w.matches(".+(ed|ing)")) {
            return Tag.VERB;
        }
        return null;
    }

    private static String lower(String w) {
        return w.toLowerCase(Locale.US).replaceAll("^[\"'(]+|[\"')]+$", "");
    }

    /** "The/determiner moon/noun orbits/verb …": a sentence read word by word, for showing how Ibiko reads it. */
    public static String explain(String sentence, Known learned) {
        StringBuilder b = new StringBuilder();
        for (Word w : tag(sentence, learned)) {
            if (w.tag == Tag.PUNCTUATION) {
                continue;
            }
            b.append(b.length() == 0 ? "" : " ").append(w);
        }
        return b.toString();
    }

    /**
     * The parts of a simple sentence: the subject (the words before the first verb), the verb, and the object or
     * complement (the words after it). A grammar formula for comprehension: "The moon orbits the earth" → [the moon |
     * orbits | the earth]. Null when no verb is found.
     */
    public static String[] clause(List<Word> words) {
        int v = -1;
        for (int i = 0; i < words.size(); i++) {
            Tag t = words.get(i).tag;
            if (t == Tag.VERB || t == Tag.AUXILIARY) {
                v = i;
                break;
            }
        }
        if (v <= 0) {
            return null;
        }
        int end = v;
        while (end + 1 < words.size() && (words.get(end + 1).tag == Tag.VERB || words.get(end + 1).tag == Tag.AUXILIARY
                || words.get(end + 1).tag == Tag.ADVERB && end + 2 < words.size() && words.get(end + 2).tag == Tag.VERB)) {
            end++;
        }
        return new String[] { join(words, 0, v), join(words, v, end + 1), join(words, end + 1, words.size()) };
    }

    private static String join(List<Word> words, int from, int to) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (words.get(i).tag == Tag.PUNCTUATION) {
                continue;
            }
            b.append(b.length() == 0 ? "" : " ").append(words.get(i).text);
        }
        return b.toString();
    }
}
