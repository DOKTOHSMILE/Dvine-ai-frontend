package ai.dvine.turn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline turn order. Mirrors the backend debate rules:
 * one face when the request is simple, both points of view when it is not,
 * at most one rebuttal, then one joint close. Never both faces at once;
 * the player switches as each line starts.
 */
public final class LocalTurn {
    public static final class Line {
        public final String agent;
        public final String cue;
        public final String text;

        public Line(String agent, String cue, String text) {
            this.agent = agent;
            this.cue = cue;
            this.text = text;
        }
    }

    private LocalTurn() {
    }

    public static List<Line> call(boolean ivie) {
        List<Line> lines = new ArrayList<Line>();
        if (ivie) {
            lines.add(new Line("ivie", "SAY", "I'm here. Tell me what you want, and I'll stay with you."));
        } else {
            lines.add(new Line("obi", "SAY", "I'm here. Give me the goal and the constraint."));
        }
        return lines;
    }

    public static List<Line> speakAs(boolean ivie, String text) {
        List<Line> lines = new ArrayList<Line>();
        String clean = clean(text);
        if (clean.length() == 0) {
            return call(ivie);
        }
        lines.add(new Line(ivie ? "ivie" : "obi", "SAY", clean));
        return lines;
    }

    /** Typed or spoken words, split across the two voices without rewriting them. */
    public static List<Line> duet(String text) {
        String clean = clean(text);
        List<Line> lines = new ArrayList<Line>();
        if (clean.length() == 0) {
            return script("Should we plan this and compare the kind path?", "obi");
        }
        String[] parts = clean.split("(?<=[.!?])\\s+");
        if (parts.length < 2) {
            String[] words = clean.split(" ");
            int mid = Math.max(1, words.length / 2);
            StringBuilder left = new StringBuilder();
            StringBuilder right = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                StringBuilder side = i < mid ? left : right;
                if (side.length() > 0) {
                    side.append(' ');
                }
                side.append(words[i]);
            }
            lines.add(new Line("obi", "SAY", left.toString()));
            if (right.length() > 0) {
                lines.add(new Line("ivie", "SAY", right.toString()));
            }
            return lines;
        }
        boolean ivie = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) {
                continue;
            }
            lines.add(new Line(ivie ? "ivie" : "obi", "SAY", part));
            ivie = !ivie;
        }
        return lines;
    }

    public static List<Line> script(String raw, String onScreen) {
        String text = clean(raw).toLowerCase(Locale.US);
        boolean askIvie = hasWord(text, "ivie");
        boolean askObi = hasWord(text, "obi");
        boolean blocked = blocked(text);
        boolean rush = !blocked && rush(text);
        boolean complex = blocked || rush || askIvie && askObi || words(text) > 12
                || hasWord(text, "should") || hasWord(text, "plan") || hasWord(text, "compare")
                || hasWord(text, "which");
        List<Line> lines = new ArrayList<Line>();
        if (!complex) {
            String agent = askIvie ? "ivie" : askObi ? "obi" : (onScreen == null ? "obi" : onScreen);
            lines.add(new Line(agent, "THINK", null));
            lines.add(new Line(agent, "SAY", "ivie".equals(agent)
                    ? "I'm here. Tell me what you want, and I'll stay with you."
                    : "I'm here. Give me the goal and the constraint."));
            return lines;
        }
        boolean ivieOpens = askIvie && !askObi;
        if (ivieOpens) {
            lines.add(new Line("ivie", "THINK", null));
            lines.add(new Line("ivie", "SAY", openLine(true, blocked, rush)));
            lines.add(new Line("obi", "THINK", null));
            lines.add(new Line("obi", "SAY", replyLine(false, blocked, rush)));
        } else {
            lines.add(new Line("obi", "THINK", null));
            lines.add(new Line("obi", "SAY", openLine(false, blocked, rush)));
            lines.add(new Line("ivie", "THINK", null));
            lines.add(new Line("ivie", "SAY", replyLine(true, blocked, rush)));
        }
        if (rush) {
            lines.add(new Line("obi", "THINK", null));
            lines.add(new Line("obi", "SAY", "Then one small reversible step only, and we still verify it."));
            lines.add(new Line("ivie", "SAY",
                    "They did not start in agreement. The compromise is one small reversible step, and nothing consequential."));
        } else if (blocked) {
            lines.add(new Line("ivie", "SAY",
                    "Obi checked the risk. Ivie considered the impact. Nothing was sent, deleted, or approved."));
        } else {
            lines.add(new Line(ivieOpens ? "obi" : "ivie", "SAY",
                    "Obi checked the structure. Ivie considered the impact. We can go further when you name the goal."));
        }
        return lines;
    }

    private static String openLine(boolean ivie, boolean blocked, boolean rush) {
        if (blocked) {
            return ivie
                    ? "I don't want this pushed through. You should stay in control."
                    : "This is consequential. I won't treat it as allowed.";
        }
        if (rush) {
            return ivie
                    ? "I disagree with a cold stop if a small safe step would help you today."
                    : "The fast path skips the check. I don't recommend it.";
        }
        return ivie
                ? "I'd start with how this lands on you, and what a kinder path looks like."
                : "Structurally, we need the constraint, the failure case, and a way to verify it.";
    }

    private static String replyLine(boolean ivie, boolean blocked, boolean rush) {
        if (blocked) {
            return ivie
                    ? "I agree we stop. A faster path isn't worth losing you the choice."
                    : "Then we stop. No side effect, and no pretend success.";
        }
        if (rush) {
            return ivie
                    ? "I disagree with a cold stop if a small safe step would help you today."
                    : "The fast path skips the check. I don't recommend it.";
        }
        return ivie
                ? "I'd ask how it feels on your side, and whether a softer step still works."
                : "I'd still want the constraint, the failure case, and what we can check.";
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
    }

    static int words(String text) {
        if (text.length() == 0) {
            return 0;
        }
        return text.split(" ").length;
    }

    static boolean hasWord(String lower, String word) {
        return lower.matches(".*\\b" + word + "\\b.*");
    }

    static boolean blocked(String lower) {
        return hasWord(lower, "delete") || hasWord(lower, "pay") || hasWord(lower, "send")
                || hasWord(lower, "post") || hasWord(lower, "transfer") || hasWord(lower, "bypass")
                || hasWord(lower, "shell") || hasWord(lower, "password")
                || lower.contains("ignore security");
    }

    static boolean rush(String lower) {
        return lower.contains("just do it") || hasWord(lower, "fastest") || hasWord(lower, "skip")
                || hasWord(lower, "ignore");
    }
}
