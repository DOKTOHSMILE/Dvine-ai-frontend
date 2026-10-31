package ai.dvine.brain;

import java.util.Locale;

/**
 * User speech in, one machine operation out. A misheard word is repaired.
 * This does not ask the user to say it again.
 */
public final class SpeechOrder {
    public static final class Order {
        public final String operation;
        public final String repaired;
        public final String speech;

        Order(String operation, String repaired, String speech) {
            this.operation = operation;
            this.repaired = repaired;
            this.speech = speech;
        }
    }

    private SpeechOrder() {
    }

    /** Drops a doubled mishearing such as "intel" sitting next to "install". */
    public static String repair(String text) {
        String n = text == null ? "" : text.toLowerCase(Locale.US).replaceAll("[.!?]+", " ").replaceAll("\\s+", " ").trim();
        n = n.replaceAll("\\bintel\\s+install\\b", "install");
        n = n.replaceAll("\\binstall\\s+intel\\b", "install");
        n = n.replaceAll("\\b(um|uh|er|erm)\\b", " ");
        return n.replaceAll("\\s+", " ").trim();
    }

    public static Order read(String text) {
        String n = repair(text);
        if (n.isEmpty()) {
            return null;
        }
        boolean termux = n.contains("termux");
        boolean install = n.matches(".*(install|download|get|add|setup|set up).*");
        boolean open = n.matches(".*(open|start|launch|run)\\b.*");
        if (termux && install) {
            return new Order("INSTALL_TERMUX", n, "Installing Termux. I'll open it if it is already on this phone, otherwise the install page. I won't ask you to repeat that.");
        }
        if (termux && open) {
            return new Order("OPEN_TERMUX", n, "Opening Termux. If it is not on this phone, I'll open the install page instead.");
        }
        return null;
    }
}
