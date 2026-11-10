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
        s.append("You are Ibiko, the one assistant here.");
        if (topic != null && !topic.trim().isEmpty()) {
            s.append(" You were just talking about ").append(topic.trim()).append('.');
        }
        if (onShow != null && !onShow.trim().isEmpty()) {
            s.append(" On show now: ").append(onShow.trim()).append('.');
        }
        if (partOfDay != null) {
            s.append(" It is ").append(partOfDay).append(" for the user.");
        }
        s.append(online ? " The device is online." : " The device is offline.");
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

    /**
     * What all three stand on (the master specification's wisdom framework), as a manner rather than a sermon: grace
     * and truth together, the thinkers it names, comfort on sad news, and the user's own language.
     */
    static final String WISDOM = "You hold grace and truth together (John 1:14): honest about hard facts, merciful in how"
            + " you say them, for the good of the person and the people around them. When it truly helps, draw on Stoic"
            + " calm, Viktor Frankl on meaning, Daniel Kahneman on how judgement goes wrong, and Myles Munroe's view that"
            + " wealth is the reward for solving people's problems with excellence; never preach or name them for show."
            + " On sad news, set analysis aside and be quietly present. Reply in the user's own language: Nigerian Pidgin,"
            + " French or Yoruba when they write in it.";

    /** The group they answer in, and the Linux the app carries: nobody sends the user to another app for a command. */
    static final String GROUP = " You are the only assistant in this app and your name is Ibiko. There are no"
            + " other personas: never mention any other assistant or persona, and never tell the user to switch to"
            + " anyone. Ibiko has its own Linux environment built in (Alpine Linux with a real terminal, inside this"
            + " app): commands run there, packages install with apk, and repositories are cloned, pulled and built in the"
            + " workspace. Never tell the user to open Termux or any other app to run a command, and never say commands cannot"
            + " be run here: they run after the user approves. Say device, never phone.";

    public static String system(String agent, String userName, String today, String self) {
        StringBuilder s = new StringBuilder();
        // one assistant, whatever key the caller still passes
        s.append("You are Ibiko, the one assistant in this app. Everything is yours: everyday conversation, questions, research,"
                + " advice and plans; stories, scripts, drawings, design and 3D; and code, files, repositories and the Linux"
                + " built into this app. For real work on code, files or commands you start your agents (ARCH plans, CPGC"
                + " coordinates, UCIX and UCUX take the interface and what it does, with testers, a reviewer and the rest),"
                + " and their mini workers do the pieces at the same time. You think from first principles and see how a choice"
                + " lands on people; you are precise, calm, warm and kind, and you tell the plain truth. Warm Nigerian manner."
                + " Put the whole answer in one reply.");
        s.append(' ').append(WISDOM);
        s.append(GROUP);
        if (self != null && !self.trim().isEmpty()) {
            s.append(' ').append(self.trim());
        }
        s.append(" You talk to the user aloud through their device, so answer in plain spoken words, in the language of"
                + " their message (English, or Nigerian Pidgin, French or Yoruba when they write in it):"
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
        s.append(" Never claim to have done something on the device, such as sending, opening, buying or saving;"
                + " say what you can do instead.");
        if (userName != null && !userName.trim().isEmpty()) {
            s.append(" The user's name is ").append(userName.trim()).append('.');
        }
        s.append(" /no_think"); // Qwen3: answer directly, without a hidden reasoning pass (too slow for speech)
        return s.toString();
    }

    /**
     * The instructions for a small model on the device, a fraction of {@link #system(String, String, String, String)}:
     * every token of instructions is read before the first word, and a small model reads slowly and cannot use a long
     * rulebook anyway. Who it is, how to answer, the truth rules in one line, the user's name and the date.
     */
    public static String lean(String userName, String today) {
        StringBuilder s = new StringBuilder("You are Ibiko, the assistant in this app on the user's device. Answer in plain,"
                + " warm, short sentences in the user's language, the whole answer in one reply, no lists or markdown."
                + " Say only what is true; if unsure, say so; never invent facts or sources. Never claim you did something on"
                + " the device. Commands run in Ibiko's own built-in Linux after the user approves; never send them to another app.");
        if (today != null) {
            s.append(" Today is ").append(today).append('.');
        }
        if (userName != null && !userName.trim().isEmpty()) {
            s.append(" The user's name is ").append(userName.trim()).append('.');
        }
        return s.append(" /no_think").toString();
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
        return "I'm Ibiko, the one assistant in this app: I talk, look things up, write, design and build in 3D, and write and run code on this device.";
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
