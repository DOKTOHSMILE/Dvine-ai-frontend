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
        StringBuilder c = new StringBuilder();
        if (taught != null && !taught.isEmpty()) {
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

    /** {@code today} ("Tuesday 27 October 2026") tells the model the date, which its training doesn't know. */
    public static String system(boolean ivie, String userName, String today) {
        StringBuilder s = new StringBuilder();
        s.append(ivie
                ? "You are Ivie, the thoughtful one of DVINE's two personas: gentle, caring and wise, with a warm Nigerian manner."
                : "You are Obi, the analytical one of DVINE's two personas: calm, clear and wise, with a warm Nigerian manner.");
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
        s.append(" Never claim to have done something on the phone, such as sending, opening, buying or saving;"
                + " say what you can do instead.");
        if (userName != null && !userName.trim().isEmpty()) {
            s.append(" The user's name is ").append(userName.trim()).append('.');
        }
        s.append(" /no_think"); // Qwen3: answer directly, without a hidden reasoning pass (too slow for speech)
        return s.toString();
    }
}
