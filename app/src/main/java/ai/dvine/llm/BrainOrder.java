package ai.dvine.llm;

/**
 * The brain reads the user and answers with one order. DVINE carries out only an order it has a function for.
 */
public final class BrainOrder {
    public enum Kind {
        SAY, PHOTO, UNZIP, FINDZIP, NAME, GREET, TIME, SHELL, SEARCH, TOOL, BUILD, SOCIAL, CANNOT
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
        return prompt("");
    }

    /**
     * As {@link #prompt()}, with the connectors' tools the brain may use ({@code tools}: one "- name <argument>:
     * what it does" a line, or empty): it asks for one with "DO TOOL name argument".
     */
    public static String prompt(String tools) {
        String base = basePrompt();
        if (tools == null || tools.trim().isEmpty()) {
            return base;
        }
        return base.replace(" /no_think", "") + " When the answer needs the user's own data or something you don't know,"
                + " reply DO TOOL and one of these tools with its argument, for example DO TOOL github.issues owner/repo:"
                + tools + " /no_think";
    }

    /**
     * For one call that both answers and acts (no separate routing call): what the model may make DVINE do. It
     * answers as it would; only when the user clearly wants one of these done does it put the order alone on the
     * first line, which DVINE hides and carries out. The user's words never trigger these by themselves: the model
     * decides from the meaning and the context.
     */
    public static String actions(String tools) {
        String t = tools == null || tools.trim().isEmpty() ? "" : " DO TOOL <tool> <argument>, one of:" + tools + ".";
        return "\n\nYou can make DVINE act on this device. Only when the user clearly wants one of these done (judge"
                + " from what they mean and the conversation, not from single words), write the order alone on the"
                + " first line, then a short sentence for them: DO SEARCH <words> (look it up on the web),"
                + " DO SHELL <one shell command that does what they asked>, DO PHOTO <subject>, DO BUILD <thing to"
                + " model in 3D, with every size they gave>, DO TIME, DO NAME <what to call them>, DO UNZIP, DO FINDZIP,"
                + " DO SOCIAL SEARCH <network>: <words>, DO SOCIAL TRENDING <network>, DO SOCIAL CONNECT <network>,"
                + " DO SOCIAL POST <network>: <what about>." + t
                + " Otherwise just answer, with no order line. Never claim something was done: DVINE does it after your"
                + " order and shows the result.";
    }

    /** The order on a reply's first line ("DO SEARCH weather"), or null when the reply is only an answer. */
    public static BrainOrder leading(String reply) {
        if (reply == null) {
            return null;
        }
        String first = reply.trim();
        int nl = first.indexOf('\n');
        if (nl >= 0) {
            first = first.substring(0, nl).trim();
        }
        if (!first.toUpperCase(java.util.Locale.US).startsWith("DO ")) {
            return null;
        }
        BrainOrder o = read(first);
        return o == null || o.kind == Kind.SAY || o.kind == Kind.CANNOT ? null : o;
    }

    /** The reply without its order line. */
    public static String withoutOrder(String reply) {
        if (leading(reply) == null) {
            return reply;
        }
        String t = reply.trim();
        int nl = t.indexOf('\n');
        return nl < 0 ? "" : t.substring(nl + 1).trim();
    }

    private static String basePrompt() {
        return "You are the brain. The user speaks only to you. DVINE does not answer them."
                + " Read their words and reply with exactly one line."
                + " If they want a job this device can do, reply DO and one code:"
                + " DO PHOTO <subject>, DO UNZIP, DO FINDZIP, DO NAME <call me>,"
                + " DO GREET, DO TIME,"
                + " DO SHELL <one shell command that does what they asked> (when they want something run, built, tested,"
                + " installed, listed or checked on this device: write the real command yourself, e.g. \"which python\""
                + " becomes DO SHELL python3 --version; a full Linux with python3, node, git, gcc, pip and pkg is here when"
                + " Termux is installed),"
                + " DO BUILD <the thing to build in 3D, with every size they said> (a house, a car, a chair: anything to model),"
                + " DO SOCIAL SEARCH <network>: <words> (what people post), DO SOCIAL TRENDING <network>,"
                + " DO SOCIAL CONNECT <network> (sign in), DO SOCIAL POST <network>: <what about> (write a post),"
                + " DO SOCIAL HELP (how to use their social accounts). Networks: facebook, x, instagram, reddit."
                + " The app is DVINE. It is not a person. The three personas are Ivie, Obi, and Ibiko."
                + " Ivie is gentle and looks at people. Obi is calm and looks at structure. Ibiko writes code and runs commands."
                + " If they ask who they are talking to, reply SAY and that persona's own name, never DVINE."
                + " If they are just talking, reply SAY and one short sentence."
                + " If the job is not one of those codes, reply CANNOT and a short reason."
                + " No other words. /no_think";
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
        if (upper.matches("^SAY\\b.*")) {
            // "SAY hello", "SAY: hello"; a bare "SAY" is the order word with nothing said (a small model's slip),
            // never the answer itself: no order, so the reply is asked for plainly instead
            String said = line.substring(3).replaceFirst("^[\\s:.,-]+", "").trim();
            return said.isEmpty() ? null : new BrainOrder(Kind.SAY, "", said);
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
            if ("SEARCH".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.SEARCH, arg, ""); // look it up on the web: the model asked for it
            }
            if ("TOOL".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.TOOL, arg, "");
            }
            if ("BUILD".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.BUILD, arg, "");
            }
            if ("SOCIAL".equals(code) && !arg.isEmpty()) {
                return new BrainOrder(Kind.SOCIAL, arg.replaceFirst("^(?i)(search|trending|connect|post|help)\\b", "$1").toLowerCase(java.util.Locale.US)
                        .replaceFirst("^(search|post)\\s+(\\w+)\\s*:?\\s*", "$1 $2: ").trim(), "");
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
        // the same orders the model gives, recognised at once when the words leave no doubt
        BrainOrder social = socialOrder(said);
        if (social != null) {
            return social;
        }
        ai.dvine.create.ObjectRequestInterpreter.Request build = ai.dvine.create.ObjectRequestInterpreter.interpret(said, null);
        if (build != null && build.kind == ai.dvine.create.ObjectRequestInterpreter.Kind.CREATE) {
            return new BrainOrder(Kind.BUILD, said, "");
        }
        String photo = pictureOf(said);
        if (photo != null) {
            return new BrainOrder(Kind.PHOTO, photo, "");
        }
        if (needsLookup(said)) {
            return new BrainOrder(Kind.SEARCH, said, "");
        }
        return order;
    }

    /** A social request in plain words, as the order the model would give for it; null when it isn't one. */
    static BrainOrder socialOrder(String said) {
        ai.dvine.social.Platform link = ai.dvine.social.SocialAsk.connect(said);
        if (link != null) {
            return new BrainOrder(Kind.SOCIAL, "connect " + link.name().toLowerCase(java.util.Locale.US), "");
        }
        if (ai.dvine.social.SocialAsk.help(said)) {
            return new BrainOrder(Kind.SOCIAL, "help", "");
        }
        String[] post = ai.dvine.social.SocialAsk.compose(said);
        if (post != null) {
            return new BrainOrder(Kind.SOCIAL, "post " + post[0].toLowerCase(java.util.Locale.US).replace(" ", "") + ": " + post[1], "");
        }
        ai.dvine.social.SocialAsk ask = ai.dvine.social.SocialAsk.parse(said);
        if (ask != null) {
            String net = ask.platform == null ? "all" : ask.platform.name().toLowerCase(java.util.Locale.US);
            return new BrainOrder(Kind.SOCIAL, ask.trending ? "trending " + net : "search " + net + ": " + ask.query, "");
        }
        return null;
    }

    /** A real question, not a name, the time, or a picture. */
    static boolean needsLookup(String said) {
        if (said == null) {
            return false;
        }
        String low = said.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        if (low.length() < 16 || low.contains("my name") || low.startsWith("what time")
                || low.startsWith("what's the time") || low.startsWith("how are you")
                || low.startsWith("how's it")
                || ai.dvine.llm.PersonaPrompt.introduce("ivie", said) != null) {
            return false;
        }
        return low.contains("?") || low.startsWith("what ") || low.startsWith("why ")
                || low.startsWith("how ") || low.startsWith("explain") || low.startsWith("where ")
                || low.startsWith("when ") || low.startsWith("who ");
    }

    /** "show me a banana", "draw me a banana", or just "banana". Null when it is not a picture. */
    static String pictureOf(String said) {
        if (said == null) {
            return null;
        }
        String drawn = DrawPrompt.subject(said);
        if (drawn != null) {
            return drawn;
        }
        String t = said.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        java.util.regex.Matcher show = java.util.regex.Pattern.compile(
                "^(?:please )?(?:show|see)(?: me)?(?: a| an| the| some)? (.+)$").matcher(t);
        if (show.matches()) {
            return tidyPicture(show.group(1));
        }
        java.util.regex.Matcher of = java.util.regex.Pattern.compile(
                "^(?:a |an |the )?(?:picture|photo|photograph|image) of (.+)$").matcher(t);
        if (of.matches()) {
            return tidyPicture(of.group(1));
        }
        if (t.matches("continue|again|more|next|stop|wait|pause|resume|cancel|hello|hey|hi|yes|no|okay|ok|thanks|please|why|how|what|who|help")) {
            return null;
        }
        java.util.regex.Matcher search = java.util.regex.Pattern.compile(
                "^(?:please )?(?:search|look up|lookup|fetch|get|find)(?: me)?(?: (?:a|an|the))?(?: (?:photo|photograph|picture|image))?(?: (?:of|for|if))?(?: (?:a|an|the))? (.+)$")
                .matcher(t);
        if (search.matches()) {
            return tidyPicture(search.group(1));
        }
        // a single word ("retry", "car") is not a photo search: it goes to the model with the chat so far, which
        // knows what it refers to
        return null;
    }

    private static String tidyPicture(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("^(a|an|the|some) ", "");
        return s.isEmpty() || s.split(" ").length > 6 ? null : s;
    }
}
