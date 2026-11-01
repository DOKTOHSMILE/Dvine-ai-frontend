package ai.dvine.llm;

/**
 * The brain reads the user and answers with one order. DVINE carries out only an order it has a function for.
 */
public final class BrainOrder {
    public enum Kind {
        SAY, PHOTO, UNZIP, FINDZIP, NAME, GREET, TIME, SHELL, CANNOT
    }

    public final Kind kind;
    public final String arg;
    public final String speech;

    private BrainOrder(Kind kind, String arg, String speech) {
        this.kind = kind;
        this.arg = arg == null ? "" : arg;
        this.speech = speech == null ? "" : speech;
    }

    public static String prompt() {
        return "You are the brain. The user speaks only to you. DVINE does not answer them."
                + " Read their words and reply with exactly one line."
                + " If they want a job this phone can do, reply DO and one code:"
                + " DO PHOTO <subject>, DO UNZIP, DO FINDZIP, DO NAME <call me>,"
                + " DO GREET, DO TIME, DO SHELL <one command>."
                + " If they are just talking, reply SAY and one short sentence."
                + " If the job is not one of those codes, reply CANNOT and a short reason."
                + " No other words.";
    }

    public static BrainOrder read(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String line = raw.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) {
            line = line.substring(0, nl).trim();
        }
        String upper = line.toUpperCase(java.util.Locale.US);
        if (upper.startsWith("CANNOT")) {
            String why = line.length() > 6 ? line.substring(6).trim() : "";
            return new BrainOrder(Kind.CANNOT, "", why);
        }
        if (upper.startsWith("SAY ")) {
            return new BrainOrder(Kind.SAY, "", line.substring(4).trim());
        }
        if (upper.startsWith("DO ")) {
            String rest = line.substring(3).trim();
            int sp = rest.indexOf(' ');
            String code = (sp < 0 ? rest : rest.substring(0, sp)).toUpperCase(java.util.Locale.US);
            String arg = sp < 0 ? "" : rest.substring(sp + 1).trim();
            if ("PHOTO".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.PHOTO, arg, "");
            }
            if ("UNZIP".equals(code)) {
                return new BrainOrder(Kind.UNZIP, "", "");
            }
            if ("FINDZIP".equals(code)) {
                return new BrainOrder(Kind.FINDZIP, "", "");
            }
            if ("NAME".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.NAME, arg, "");
            }
            if ("GREET".equals(code)) {
                return new BrainOrder(Kind.GREET, "", "");
            }
            if ("TIME".equals(code)) {
                return new BrainOrder(Kind.TIME, "", "");
            }
            if ("SHELL".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.SHELL, arg, "");
            }
            return new BrainOrder(Kind.CANNOT, code, "That isn't a function I have.");
        }
        return new BrainOrder(Kind.SAY, "", ActionTags.strip(raw).trim());
    }

    /**
     * The model's order wins when it names a function. If it only talked, a job this phone already has
     * (a name, a zip, the time) is still carried out.
     */
    public static BrainOrder decide(String user, String modelRaw) {
        BrainOrder order = read(modelRaw);
        if (order != null && order.kind != Kind.SAY) {
            return order;
        }
        String said = user == null ? "" : user.trim();
        if (ai.dvine.brain.Onboarding.introduce(said) != null) {
            return read("DO NAME " + said);
        }
        String t = said.toLowerCase(java.util.Locale.US);
        if (t.contains("unzip") && t.contains("zip")) {
            return read("DO UNZIP");
        }
        if ((t.contains("find") || t.contains("search") || t.contains("where")) && t.contains("zip")) {
            return read("DO FINDZIP");
        }
        if (t.contains("what time") || t.contains("what's the time")) {
            return read("DO TIME");
        }
        return order;
    }
}
