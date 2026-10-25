package ai.dvine.turn;

import java.util.List;

/** Either a voice switch, or a conversation the faces share. */
public final class TurnRoute {
    public final boolean switchOnly;
    public final boolean ivie;
    public final List<TurnLine> lines;

    public TurnRoute(boolean switchOnly, boolean ivie, List<TurnLine> lines) {
        this.switchOnly = switchOnly;
        this.ivie = ivie;
        this.lines = lines;
    }
}
