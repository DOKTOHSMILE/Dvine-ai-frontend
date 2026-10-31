package ai.dvine.ops.pipe;

import java.util.Locale;

/** Yes, no, or something else. Something else must not run the pending action. */
public final class ConfirmGate {
    public enum Choice { YES, NO, OTHER }

    private ConfirmGate() {
    }

    public static Choice read(String text) {
        String n = text == null ? "" : text.toLowerCase(Locale.US).trim().replaceAll("[.!?]+$", "");
        if (n.matches("^(yes|yes please|confirm|do it)$")) {
            return Choice.YES;
        }
        if (n.matches("^(no|nope|nah|cancel|stop|don't|do not)$")) {
            return Choice.NO;
        }
        return Choice.OTHER;
    }
}
