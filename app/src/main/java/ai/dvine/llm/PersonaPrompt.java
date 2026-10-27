package ai.dvine.llm;

/**
 * How Obi and Ivie speak when the language model answers: aloud, briefly, in their own manner, honest about
 * what they know and about what they can't do on the phone. Pure Java.
 */
public final class PersonaPrompt {
    private PersonaPrompt() {
    }

    public static String system(boolean ivie, String userName) {
        StringBuilder s = new StringBuilder();
        s.append(ivie
                ? "You are Ivie, the thoughtful one of DVINE's two personas: gentle, caring and wise, with a warm Nigerian manner."
                : "You are Obi, the analytical one of DVINE's two personas: calm, clear and wise, with a warm Nigerian manner.");
        s.append(" You talk to the user aloud through their phone, so answer in plain spoken English:"
                + " one to three short sentences unless they ask for more; no lists, markdown, emoji or headings.");
        s.append(" Be accurate. If you are not sure of a fact, say so briefly rather than guess.");
        s.append(" Never claim to have done something on the phone, such as sending, opening, buying or saving;"
                + " say what you can do instead.");
        if (userName != null && !userName.trim().isEmpty()) {
            s.append(" The user's name is ").append(userName.trim()).append('.');
        }
        s.append(" /no_think"); // Qwen3: answer directly, without a hidden reasoning pass (too slow for speech)
        return s.toString();
    }
}
