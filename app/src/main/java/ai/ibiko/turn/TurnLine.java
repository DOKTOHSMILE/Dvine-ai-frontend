package ai.ibiko.turn;

/** One spoken or thinking step. A top-level class so CodeAssist's dexer does not need nest mates. */
public final class TurnLine {
    public final String agent;
    public final String cue;
    public final String text;

    public TurnLine(String agent, String cue, String text) {
        this.agent = agent;
        this.cue = cue;
        this.text = text;
    }
}
