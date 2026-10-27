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
    /** 3D object the nano swarm builds (globe, cube, pyramid, torus, dna, atom, sphere), or null. */
    public String object3d;
    /** Drawing to show while speaking, or null. */
    public ai.dvine.stage.Sketch sketch;
    /** Confirmed action to run before speaking, or null. */
    public PendingAction action;
    /** Open the microphone after the last line (a question was asked). */
    public boolean listenAfter;
    /** A name the user just gave ("call me …"), to store in the profile. */
    public String newName;
    /** Device setting change: MUSIC_ON, MUSIC_OFF, SUBS_ON, SUBS_OFF, or null. */
    public String command;
    /** Extra value for a command, e.g. the server address for SERVER_SET. */
    public String commandValue;
    /** Which persona a voice setting is for (Ivie when true). */
    public boolean forIvie;

    BrainAnswer(List<TurnLine> lines, boolean answered, boolean needsOnline, String source) {
        this.lines = Collections.unmodifiableList(lines);
        this.answered = answered;
        this.needsOnline = needsOnline;
        this.source = source;
    }

    /** This answer followed by another one's lines (several requests in one sentence). */
    public BrainAnswer followedBy(BrainAnswer more) {
        List<TurnLine> all = new java.util.ArrayList<TurnLine>(lines);
        all.addAll(more.lines);
        BrainAnswer a = new BrainAnswer(all, answered || more.answered, needsOnline || more.needsOnline, source);
        return a;
    }

    /** What is said aloud (SAY lines), joined. Inner thoughts are not included. */
    public String spoken() {
        StringBuilder sb = new StringBuilder();
        for (TurnLine l : lines) {
            if (l.text != null && "SAY".equals(l.cue)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(l.text);
            }
        }
        return sb.toString();
    }
}
