package ai.dvine.agent;

import java.util.Locale;

import ai.dvine.render.field.ShapeCommand;

/**
 * Moving the drawing on show, said in words DVINE's own reader doesn't know ("make it do cartwheels", "can I see it
 * from above", "let it go round like a wheel"): the brain picks the one command it means from DVINE's list
 * ({@link ShapeCommand#CANONICAL}), DVINE carries that out and keeps the words for next time. Pure Java.
 */
public final class ViewIntent {
    private ViewIntent() {
    }

    private static final String WORDS = ".*\\b(rotat\\w*|spin\\w*|turn\\w*|revolv\\w*|twirl\\w*|swivel\\w*|roll\\w*|tumbl\\w*|pivot\\w*|orbit\\w*|"
            + "flip\\w*|tilt\\w*|twist\\w*|cartwheel\\w*|somersault\\w*|wheel|round|around|angle|view|side|top|bottom|underneath|above|"
            + "behind|back of|front of|zoom\\w*|closer|further|move\\w*|shift|slide|still|freeze|stop|pause|faster|slower|upside|"
            + "direction|orientation|axis|horizontal\\w*|vertical\\w*|bigger|smaller|size)\\b.*";

    /** Whether the words could be about moving or turning the thing on show (and so worth asking the brain). */
    public static boolean looksLikeOne(String said) {
        return said != null && said.length() < 140 && said.toLowerCase(Locale.US).matches(WORDS);
    }

    public static String system() {
        StringBuilder b = new StringBuilder("A 3D drawing is on screen. The user said something about moving or viewing it."
                + " Answer with exactly one of these commands, the one they mean, and nothing else; answer none if it is"
                + " not about moving or viewing the drawing:\n");
        for (String c : ShapeCommand.CANONICAL) {
            b.append(c).append('\n');
        }
        return b.append("/no_think").toString();
    }

    /** The command the brain chose, checked against the list (so only a real command is ever run), or null. */
    public static String read(String written) {
        if (written == null) {
            return null;
        }
        String w = written.replaceAll("(?s)<think>.*?</think>", "").toLowerCase(Locale.US).replaceAll("[\"'`.*]", " ")
                .replaceAll("\\s+", " ").trim();
        String best = null;
        for (String c : ShapeCommand.CANONICAL) {
            if (w.equals(c) || w.startsWith(c + " ") || w.contains(" " + c) && best == null) {
                if (best == null || c.length() > best.length()) {
                    best = c;
                }
            }
        }
        if (best == null) {
            for (String c : ShapeCommand.CANONICAL) {
                if (w.contains(c) && (best == null || c.length() > best.length())) {
                    best = c;
                }
            }
        }
        return best != null && ShapeCommand.parse(best) != null ? best : null;
    }
}
