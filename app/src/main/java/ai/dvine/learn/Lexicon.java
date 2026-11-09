package ai.dvine.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DVINE 1's own dictionary: words it met while being used, each with its part of speech and meaning, learned from
 * Wiktionary in a learning session. It answers "what does X mean" and "define X", and tells the {@link
 * ai.dvine.suit.Grammar} formulas which words are verbs. Kept in a file on the device.
 */
public final class Lexicon {
    public static final class Word {
        public final String word, part, meaning;

        Word(String word, String part, String meaning) {
            this.word = word;
            this.part = part;
            this.meaning = meaning;
        }

        /** "Ubiquitous (adjective): being everywhere at once." */
        public String said() {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1) + " (" + part.toLowerCase(Locale.US) + "): " + meaning;
        }
    }

    private final File file;
    private final Map<String, Word> words = new LinkedHashMap<String, Word>();

    public Lexicon(File file) {
        this.file = file;
        load();
    }

    public synchronized int size() {
        return words.size();
    }

    public synchronized Word get(String word) {
        return words.get(key(word));
    }

    public synchronized boolean knows(String word) {
        return words.containsKey(key(word));
    }

    static String key(String w) {
        return w == null ? "" : w.trim().toLowerCase(Locale.US);
    }

    /** Keeps a word read from a Wiktionary definition reply; true when it was new. */
    public synchronized boolean learn(String word, String json) {
        Word w = fromWiktionary(word, json);
        if (w == null || words.containsKey(key(word))) {
            return false;
        }
        words.put(key(word), w);
        save();
        return true;
    }

    /** The word asked about in "what does X mean", "define X", "meaning of X", or null. */
    public static String asked(String question) {
        String q = question == null ? "" : question.trim().toLowerCase(Locale.US).replaceAll("[?.!]+$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^(?:what does|what do)\\s+(?:the word\\s+)?[\"“]?([a-z'-]+)[\"”]?\\s+mean$|^define\\s+(?:the word\\s+)?[\"“]?([a-z'-]+)[\"”]?$"
                        + "|^(?:what is |what's )?the meaning of\\s+(?:the word\\s+)?[\"“]?([a-z'-]+)[\"”]?$").matcher(q);
        if (!m.matches()) {
            return null;
        }
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return m.group(i);
            }
        }
        return null;
    }

    /** Where Wiktionary keeps a word's definitions. */
    public static String url(String word) {
        try {
            return "https://en.wiktionary.org/api/rest_v1/page/definition/" + java.net.URLEncoder.encode(key(word), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "https://en.wiktionary.org/api/rest_v1/page/definition/" + key(word);
        }
    }

    /** The first English meaning in a Wiktionary definition reply, or null. */
    static Word fromWiktionary(String word, String json) {
        if (json == null || word == null || word.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> m = ai.dvine.brain.MiniJson.obj(ai.dvine.brain.MiniJson.parse(json));
            for (Object o : ai.dvine.brain.MiniJson.list(m.get("en"))) {
                Map<String, Object> usage = ai.dvine.brain.MiniJson.obj(o);
                String part = ai.dvine.brain.MiniJson.str(usage.get("partOfSpeech"), "");
                for (Object d : ai.dvine.brain.MiniJson.list(usage.get("definitions"))) {
                    String text = ai.dvine.brain.MiniJson.str(ai.dvine.brain.MiniJson.obj(d).get("definition"), "")
                            .replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("\\s+", " ").trim();
                    if (text.length() > 3 && !part.isEmpty()) {
                        return new Word(key(word), part, text.endsWith(".") ? text : text + ".");
                    }
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length == 3) {
                    words.put(p[0], new Word(p[0], p[1], p[2]));
                }
            }
        } catch (IOException e) {
            words.clear();
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Word w : words.values()) {
            b.append(w.word).append('\t').append(w.part).append('\t').append(w.meaning.replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }
}
