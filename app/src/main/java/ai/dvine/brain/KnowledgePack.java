package ai.dvine.brain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The basic-knowledge file shipped with the app and refreshed from the server. */
public final class KnowledgePack {
    public static final String DEFAULT_OFFLINE_LINE =
            "I'm unable to connect to my database to get more information. Please connect to the internet and ask me again.";

    public static final class Intent {
        public final String id;
        public final List<String> patterns;
        public final List<String> obi;
        public final List<String> ivie;

        Intent(String id, List<String> patterns, List<String> obi, List<String> ivie) {
            this.id = id;
            this.patterns = patterns;
            this.obi = obi;
            this.ivie = ivie;
        }
    }

    public static final class Fact {
        public final List<String> keys;
        public final String answer;

        Fact(List<String> keys, String answer) {
            this.keys = keys;
            this.answer = answer;
        }
    }

    public static final class Word {
        public final String word;
        public final String meaning;

        Word(String word, String meaning) {
            this.word = word;
            this.meaning = meaning;
        }
    }

    public static final class Wisdom {
        public final List<String> tags;
        public final String text;
        public final String source;

        Wisdom(List<String> tags, String text, String source) {
            this.tags = tags;
            this.text = text;
            this.source = source;
        }
    }

    public static final class Unit {
        public final String from;
        public final String to;
        public final double factor;
        public final double offset;

        Unit(String from, String to, double factor, double offset) {
            this.from = from;
            this.to = to;
            this.factor = factor;
            this.offset = offset;
        }
    }

    public final int version;
    public final String language;
    public final String offlineLine;
    public final List<Intent> intents;
    public final List<Fact> facts;
    public final List<Word> words;
    public final List<Wisdom> wisdom;
    public final List<Unit> units;

    private KnowledgePack(int version, String language, String offlineLine, List<Intent> intents,
                          List<Fact> facts, List<Word> words, List<Wisdom> wisdom, List<Unit> units) {
        this.version = version;
        this.language = language;
        this.offlineLine = offlineLine;
        this.intents = Collections.unmodifiableList(intents);
        this.facts = Collections.unmodifiableList(facts);
        this.words = Collections.unmodifiableList(words);
        this.wisdom = Collections.unmodifiableList(wisdom);
        this.units = Collections.unmodifiableList(units);
    }

    /** Parses and validates a pack. Throws IllegalArgumentException when it is not usable. */
    public static KnowledgePack parse(String json) {
        Map<String, Object> root = MiniJson.obj(MiniJson.parse(json));
        int schema = (int) MiniJson.num(root.get("schemaVersion"), -1);
        if (schema != 1) {
            throw new IllegalArgumentException("unsupported pack schema " + schema);
        }
        int version = (int) MiniJson.num(root.get("version"), -1);
        if (version < 1) {
            throw new IllegalArgumentException("pack has no version");
        }
        List<Intent> intents = new ArrayList<Intent>();
        for (Object o : MiniJson.list(root.get("intents"))) {
            Map<String, Object> m = MiniJson.obj(o);
            List<String> patterns = lower(MiniJson.strings(m.get("patterns")));
            List<String> obi = MiniJson.strings(m.get("obi"));
            List<String> ivie = MiniJson.strings(m.get("ivie"));
            if (patterns.isEmpty() || (obi.isEmpty() && ivie.isEmpty())) {
                continue;
            }
            intents.add(new Intent(MiniJson.str(m.get("id"), ""), patterns,
                    obi.isEmpty() ? ivie : obi, ivie.isEmpty() ? obi : ivie));
        }
        List<Fact> facts = new ArrayList<Fact>();
        for (Object o : MiniJson.list(root.get("facts"))) {
            Map<String, Object> m = MiniJson.obj(o);
            List<String> keys = lower(MiniJson.strings(m.get("keys")));
            String answer = MiniJson.str(m.get("answer"), "");
            if (!keys.isEmpty() && answer.length() > 0) {
                facts.add(new Fact(keys, answer));
            }
        }
        List<Word> words = new ArrayList<Word>();
        for (Object o : MiniJson.list(root.get("words"))) {
            Map<String, Object> m = MiniJson.obj(o);
            String w = MiniJson.str(m.get("word"), "").toLowerCase(Locale.US).trim();
            String meaning = MiniJson.str(m.get("meaning"), "");
            if (w.length() > 0 && meaning.length() > 0) {
                words.add(new Word(w, meaning));
            }
        }
        List<Wisdom> wisdom = new ArrayList<Wisdom>();
        for (Object o : MiniJson.list(root.get("wisdom"))) {
            Map<String, Object> m = MiniJson.obj(o);
            String text = MiniJson.str(m.get("text"), "");
            if (text.length() > 0) {
                wisdom.add(new Wisdom(lower(MiniJson.strings(m.get("tags"))), text, MiniJson.str(m.get("source"), "")));
            }
        }
        List<Unit> units = new ArrayList<Unit>();
        for (Object o : MiniJson.list(root.get("units"))) {
            Map<String, Object> m = MiniJson.obj(o);
            String from = MiniJson.str(m.get("from"), "");
            String to = MiniJson.str(m.get("to"), "");
            double factor = MiniJson.num(m.get("factor"), 0);
            if (from.length() > 0 && to.length() > 0 && factor != 0) {
                units.add(new Unit(from, to, factor, MiniJson.num(m.get("offset"), 0)));
            }
        }
        String line = MiniJson.str(root.get("offlineLine"), DEFAULT_OFFLINE_LINE);
        return new KnowledgePack(version, MiniJson.str(root.get("language"), "en"), line,
                intents, facts, words, wisdom, units);
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<String>();
        for (String s : in) {
            out.add(s.toLowerCase(Locale.US).trim());
        }
        return out;
    }
}
