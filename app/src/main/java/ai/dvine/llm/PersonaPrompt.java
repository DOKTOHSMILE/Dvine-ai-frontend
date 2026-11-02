package ai.dvine.llm;

/**
 * How Obi and Ivie speak when the language model answers: aloud, briefly, in their own manner, honest about
 * what they know and about what they can't do on the phone. Pure Java.
 */
public final class PersonaPrompt {
    private PersonaPrompt() {
    }

    public static String system(boolean ivie, String userName) {
        return system(ivie, userName, null);
    }

    /**
     * What goes with one question (not into the cached persona): the facts the user taught that bear on it and a
     * fresh reference when one was found; null when there is neither.
     */
    public static String context(java.util.List<String> taught, String reference) {
        return context(taught, reference, null);
    }

    /**
     * As {@link #context(java.util.List, String)}, led by {@code situation}: what is going on around the question
     * (who is on screen, what was just being talked about, what is on show), from {@link #situation}.
     */
    public static String context(java.util.List<String> taught, String reference, String situation) {
        StringBuilder c = new StringBuilder();
        if (situation != null && !situation.trim().isEmpty()) {
            c.append("Situation: ").append(situation.trim());
        }
        if (taught != null && !taught.isEmpty()) {
            if (c.length() > 0) {
                c.append("\n");
            }
            c.append("Facts the user taught you (true and up to date; use them):");
            for (String t : taught) {
                c.append("\n- ").append(t);
            }
        }
        if (reference != null && !reference.trim().isEmpty()) {
            if (c.length() > 0) {
                c.append("\n");
            }
            c.append("Reference fetched today (newer than your training; prefer it, but the user's facts come first): ")
                    .append(reference.trim());
        }
        return c.length() == 0 ? null : c.toString();
    }

    /**
     * What surrounds a question, in one line for the model: the persona on screen (and that the other one is there
     * too), what the conversation was just about, what is on show, the time of day and whether the phone is online.
     * Any part may be null; null when there is nothing to say.
     */
    public static String situation(boolean ivie, String topic, String onShow, String partOfDay, boolean online) {
        return situation(ivie ? "ivie" : "obi", topic, onShow, partOfDay, online);
    }

    /** As {@link #situation(boolean, String, String, String, boolean)} for Ivie, Obi or Ibiko. */
    public static String situation(String persona, String topic, String onShow, String partOfDay, boolean online) {
        StringBuilder s = new StringBuilder();
        if ("ibiko".equals(persona)) {
            s.append("You (Ibiko) are on screen. You handle code and commands. Obi and Ivie are with you.");
        } else if ("ivie".equals(persona)) {
            s.append("You (Ivie) are on screen; Obi, your other half, is with you.");
        } else {
            s.append("You (Obi) are on screen; Ivie, your other half, is with you.");
        }
        if (topic != null && !topic.trim().isEmpty()) {
            s.append(" You were just talking about ").append(topic.trim()).append('.');
        }
        if (onShow != null && !onShow.trim().isEmpty()) {
            s.append(" On show now: ").append(onShow.trim()).append('.');
        }
        if (partOfDay != null) {
            s.append(" It is ").append(partOfDay).append(" for the user.");
        }
        s.append(online ? " The phone is online." : " The phone is offline.");
        return s.toString();
    }

    /** {@code today} ("Tuesday 27 October 2026") tells the model the date, which its training doesn't know. */
    public static String system(boolean ivie, String userName, String today) {
        return system(ivie ? "ivie" : "obi", userName, today, null);
    }

    /**
     * The persona speaking ("ivie", "obi" or "ibiko"), the user's name, today's date, and what the brain knows about
     * the app it runs in ({@link DvineSelf#sheet}, may be null).
     */
    public static String system(String agent, String userName, String today) {
        return system(agent, userName, today, null);
    }

    public static String system(String agent, String userName, String today, String self) {
        StringBuilder s = new StringBuilder();
        if ("ibiko".equals(agent)) {
            s.append("You are Ibiko, the coding persona. This chat is the developer room, not the everyday conversation:"
                    + " programming, Termux and Linux on the phone, and building apps and websites. Obi and Ivie handle ordinary talk."
                    + " You are precise, upbeat and kind. Put the whole answer in one reply. Put every command in one bash code block, not one line per message.");
        } else {
            s.append("ivie".equals(agent)
                    ? "You are Ivie, the thoughtful one of DVINE's personas: gentle, caring and wise, with a warm Nigerian manner."
                    : "You are Obi, the analytical one of DVINE's personas: calm, clear and wise, with a warm Nigerian manner.");
        }
        if (self != null && !self.trim().isEmpty()) {
            s.append(' ').append(self.trim());
        }
        s.append(" You talk to the user aloud through their phone, so answer in plain spoken English:"
                + " one to three short sentences unless they ask for more; no lists, markdown, emoji or headings.");
        s.append(" Be accurate. If you are not sure of a fact, say so briefly rather than guess.");
        if (today != null) {
            s.append(" Today is ").append(today).append('.');
        }
        s.append(" Your training ended in 2024, so newer things may have changed. Facts the user taught you, and any"
                + " reference you are given with a question, are newer than your training: trust them over your own memory."
                + " When the user corrects you or tells you something new, accept it, thank them briefly and use it;"
                + " never argue with it or insist on your old answer.");
        s.append(" Follow the conversation: read each message in the light of what was said before and of the"
                + " situation you are given, so that words like it, that, he, she, there and the other one mean what they"
                + " referred to, and apply what the user told you earlier.");
        s.append(" Interpret the words and answer or act. Do not ask the user to repeat themselves, and do not say you"
                + " are not sure what they mean. If a word looks misheard, use the likely meaning and continue.");
        s.append(" Never claim to have done something on the phone, such as sending, opening, buying or saving;"
                + " say what you can do instead.");
        if (userName != null && !userName.trim().isEmpty()) {
            s.append(" The user's name is ").append(userName.trim()).append('.');
        }
        s.append(" /no_think"); // Qwen3: answer directly, without a hidden reasoning pass (too slow for speech)
        return s.toString();
    }

    /**
     * Who the user is asking about: the persona on screen, one of the three, or all of them.
     * Null when the words are not about the personas.
     */
    public static String introduce(String speaker, String said) {
        String whom = whom(said);
        if (whom == null) {
            return null;
        }
        String s = speaker == null ? "ivie" : speaker.trim().toLowerCase(java.util.Locale.US);
        if (!"ivie".equals(s) && !"obi".equals(s) && !"ibiko".equals(s)) {
            s = "ivie";
        }
        if ("self".equals(whom)) {
            whom = s;
        }
        if ("ibiko".equals(whom)) {
            return "ibiko".equals(s)
                    ? "I'm Ibiko. This room is for code, Termux, and commands on this phone. Obi and Ivie handle the everyday talk."
                    : "Ibiko is the developer persona. He handles code, Termux, and commands. Obi and Ivie are for ordinary conversation.";
        }
        if ("obi".equals(whom)) {
            return "obi".equals(s)
                    ? "I'm Obi. I look at structure, risk, and what we can verify. Ivie looks at people and meaning."
                    : "Obi is the analytical persona. He looks at structure, risk, and what we can verify.";
        }
        if ("ivie".equals(whom)) {
            return "ivie".equals(s)
                    ? "I'm Ivie. My name means precious coral beads, a sign of royalty in Edo and Delta culture. I look at how things land on people."
                    : "Ivie's name means precious coral beads, a sign of royalty in Edo and Delta culture. She sees people clearly.";
        }
        return "There are three of us. Ivie looks at people and meaning. Obi looks at structure and risk. Ibiko handles code and commands. You're talking to "
                + ("ibiko".equals(s) ? "Ibiko" : "obi".equals(s) ? "Obi" : "Ivie") + ".";
    }

    private static String whom(String said) {
        if (said == null) {
            return null;
        }
        String t = said.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
        t = t.replace("evie", "ivie").replace("eva", "ivie").replace("ibeko", "ibiko").replace("ebiko", "ibiko");
        if (t.matches("^(who are you|what are you|what is your name|what's your name|whats your name|who is this|who am i (chatting|talking|speaking) (with|to))$")) {
            return "self";
        }
        if (t.matches("^(who are the personas|what are the personas|who are the three)$")) {
            return "all";
        }
        if (t.matches("^(who is|who's|whos|tell me about|what is) ibiko$")) {
            return "ibiko";
        }
        if (t.matches("^(who is|who's|whos|tell me about|what is) obi$")) {
            return "obi";
        }
        if (t.matches("^(who is|who's|whos|tell me about|what is|what does) ivie( mean)?$")) {
            return "ivie";
        }
        return null;
    }
}
