package ai.dvine.brain;

import java.util.Collections;
import java.util.List;

import ai.dvine.turn.TurnLine;

/** What the offline brain decided to say, and whether it needs the server. */
public final class BrainAnswer {
    public final List<TurnLine> lines;
    public final boolean answered;
    public final boolean needsOnline;
    /** Short label for the activity card, e.g. "CALC" or "PACK". */
    public final String source;

    BrainAnswer(List<TurnLine> lines, boolean answered, boolean needsOnline, String source) {
        this.lines = Collections.unmodifiableList(lines);
        this.answered = answered;
        this.needsOnline = needsOnline;
        this.source = source;
    }

    /** All spoken text joined, for tests and logs. */
    public String spoken() {
        StringBuilder sb = new StringBuilder();
        for (TurnLine l : lines) {
            if (l.text != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(l.text);
            }
        }
        return sb.toString();
    }
}
