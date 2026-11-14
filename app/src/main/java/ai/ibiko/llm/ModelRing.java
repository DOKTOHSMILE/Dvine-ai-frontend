package ai.ibiko.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which models should think on one question: the one the user selected, then others of a similar size that the
 * server actually offers, preferably a different family so the notes are not three copies. At most three. Pure Java.
 */
public final class ModelRing {
    public enum Band { SMALL, MID, LARGE }

    private ModelRing() {
    }

    /** Small (mini, haiku, flash-lite), mid (sonnet, flash), or large (opus, pro, a flagship). */
    public static Band band(String id) {
        String m = id == null ? "" : id.toLowerCase(Locale.US);
        if (m.matches(".*(\\bmini\\b|\\bnano\\b|\\bhaiku\\b|flash-lite|\\blite\\b|\\bsmall\\b|\\btiny\\b|[-/](1|3|4|7|8)b\\b).*")) {
            return Band.SMALL;
        }
        if (m.matches(".*(opus|reasoner|-pro\\b|pro-|large|70b|72b|405b).*")
                || m.matches(".*gpt-5(?!.*mini).*") || m.matches(".*gpt-4\\.1(?!.*mini).*")) {
            return Band.LARGE;
        }
        return Band.MID;
    }

    /** A short family name, so two models from one maker are not both picked when another family is there. */
    public static String family(String id) {
        String m = id == null ? "" : id.toLowerCase(Locale.US);
        if (m.contains("claude") || m.contains("anthropic")) {
            return "claude";
        }
        if (m.contains("gemini") || m.contains("google")) {
            return "gemini";
        }
        if (m.contains("llama") || m.contains("meta")) {
            return "llama";
        }
        if (m.contains("qwen")) {
            return "qwen";
        }
        if (m.contains("deepseek")) {
            return "deepseek";
        }
        if (m.contains("grok")) {
            return "grok";
        }
        if (m.contains("gpt") || m.contains("openai")) {
            return "gpt";
        }
        int slash = m.lastIndexOf('/');
        String tail = slash >= 0 ? m.substring(slash + 1) : m;
        int dash = tail.indexOf('-');
        return dash > 0 ? tail.substring(0, dash) : tail;
    }

    /**
     * The selected model, then up to two others from {@code offered} in the same band (a neighbour band if the same
     * band has nobody else), different families first. The selected model is kept even when the list does not name it.
     */
    public static List<String> choose(String selected, List<String> offered) {
        List<String> out = new ArrayList<String>();
        String mine = selected == null ? "" : selected.trim();
        if (mine.isEmpty()) {
            return out;
        }
        out.add(mine);
        Band want = band(mine);
        String mineFamily = family(mine);
        take(out, offered, want, mineFamily, true);
        if (out.size() < 3) {
            take(out, offered, want, mineFamily, false);
        }
        if (out.size() < 3) {
            Band near = want == Band.LARGE ? Band.MID : want == Band.SMALL ? Band.MID : Band.LARGE;
            take(out, offered, near, mineFamily, true);
        }
        return out;
    }

    private static void take(List<String> out, List<String> offered, Band want, String mineFamily, boolean otherFamily) {
        if (offered == null) {
            return;
        }
        for (String id : offered) {
            if (out.size() >= 3 || id == null || id.trim().isEmpty() || out.contains(id.trim())) {
                continue;
            }
            String m = id.trim();
            if (band(m) != want) {
                continue;
            }
            if (otherFamily && family(m).equals(mineFamily)) {
                continue;
            }
            out.add(m);
        }
    }
}
