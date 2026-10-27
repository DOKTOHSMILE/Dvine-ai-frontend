package ai.dvine.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a reply streamed piece by piece into sentences to speak, so the voice starts after the first sentence
 * rather than after the whole answer. Strips the marks a voice would read out (asterisks, hashes, backticks).
 * A long first clause is let go at a comma so the first words come quickly. Pure Java.
 */
public final class SpeechChunker {
    private final StringBuilder held = new StringBuilder();
    private boolean first = true;

    /** Adds streamed text; returns the sentences now complete (often none). */
    public List<String> add(String piece) {
        held.append(clean(piece));
        List<String> out = new ArrayList<String>();
        while (true) {
            int cut = cut();
            if (cut < 0) {
                break;
            }
            String s = held.substring(0, cut).trim();
            held.delete(0, cut);
            if (!s.isEmpty()) {
                out.add(s);
                first = false;
            }
        }
        return out;
    }

    /** What is left at the end of the reply. */
    public String flush() {
        String s = held.toString().trim();
        held.setLength(0);
        return s;
    }

    /** The end of the next sentence to speak (exclusive), or -1. */
    private int cut() {
        int n = held.length();
        for (int i = 0; i < n; i++) {
            char c = held.charAt(i);
            boolean end = c == '.' || c == '!' || c == '?' || c == '\n';
            // a sentence ends at a stop followed by a space ("3.5" and "e.g" don't), and is not a tiny fragment
            if (end && i + 1 < n && (Character.isWhitespace(held.charAt(i + 1)) || c == '\n')) {
                if (i + 1 >= 12 || c == '\n') {
                    return i + 1;
                }
            }
            if (first && c == ',' && i >= 60 && i + 1 < n && held.charAt(i + 1) == ' ') {
                return i + 1;
            }
        }
        return -1;
    }

    static String clean(String s) {
        return s.replace("**", "").replace("*", "").replace("#", "").replace("`", "").replace("—", ", ");
    }
}
