package ai.dvine.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import ai.dvine.turn.TurnLine;
import ai.dvine.voice.SentenceStream;

/**
 * Walking a reply: the lines in turn, the speaking of each one, and the token that tells a stale
 * walk from the live one.
 *
 * <p>{@link #play} puts a list of lines on and starts the walk; each {@link #step} shows the next
 * line (a spoken one goes to the pane, the stage and the voice, sentence by sentence), then posts
 * itself for the next. When the lines run out the stage is told the reply is done and the pending
 * {@code afterPlay} action runs once the voice has gone quiet, so the microphone never hears
 * DVINE's own words. A new message bumps the token, which leaves every queued step of the old walk
 * doing nothing.
 *
 * <p>{@link #stopPlay} is what every new piece of work calls first: it bumps the token, drops the
 * pending action, clears the handler's queue, stops the voice and ends any dictation that was only
 * running as part of the reply.
 *
 * <p>The screen side lives in {@link ai.dvine.MainActivity}: it implements {@link Host} and hands
 * over the stage, the pane, the voice and the few screen actions a walk needs.
 */
public final class PlayStep {

    /** What a walk needs from the screen it serves. */
    public interface Host {
        /** The main-thread handler the walk posts its steps on. */
        android.os.Handler handler();

        /** The screen itself, for the model's own connection checks. */
        android.content.Context context();

        /** The stage: the face being played, the mode, the subtitle, the thought. */
        ai.dvine.stage.StageView stage();

        /** The pane of the chat, or null when there is none. */
        ai.dvine.stage.ChatPane chatPane();

        /** The voice speaking the lines, or null when there is none. */
        ai.dvine.voice.TalkBridge talk();

        /** The microphone, or null when there is none. */
        ai.dvine.voice.ListenBridge listen();

        /** Whether the device is online (the model's own connection failures are said plainly). */
        boolean online();

        /** Whether the reply is read aloud as it is written. */
        boolean speakReply();

        /** Whether the microphone is on its own recording (dictation), not part of the reply. */
        boolean dictating();

        /** End a reply that is on show: drop its lines, tell the model to stop, reset the walk. */
        void endThink();

        /** Keep the screen awake while the reply plays. */
        void keepAwake(boolean on);

        /** After a walk that ended, once the voice is quiet: switch to the persona the reply named. */
        void switchFace(String persona, boolean fromTap);

        /** A live line from the stream that has already been read out is not read again on show. */
        boolean spokenLive(TurnLine line);

        /** Remember a line that was read out while it was written. */
        void markSpokenLive(TurnLine line);

        /** The reply is still being written (the walk waits for it to close). */
        boolean streaming();

        /** The reply stopped being written. */
        void clearStreaming();

        /** A new reply starts being written. */
        void setStreaming();

        /** The walk's token, for a step queued before the walk moved on. */
        int playToken();

        /** A step queued before the walk moved on does nothing. */
        boolean samePlay(int token);

        /** End whatever walk is running, without touching the screen: the reply was interrupted. */
        void cancelWalk();
    }

    private final Host host;

    /** The lines being walked, and how far along they are. */
    private List<TurnLine> lines = new ArrayList<TurnLine>();
    private int index;
    /** Bumped by every new walk: a step carrying an older token does nothing. */
    private int playId;
    /** What to do once the lines are all shown (a persona switch, a follow-up). */
    private Runnable afterPlay;
    /** Lines already read out while they were being written (not read again when shown). */
    private final Set<TurnLine> spokenLive = Collections.newSetFromMap(
            new IdentityHashMap<TurnLine, Boolean>());

    public PlayStep(Host host) {
        this.host = host;
    }

    /** The lines held by "pause": everything from the line on show to the end. */
    public List<TurnLine> pauseHeld() {
        if (index >= lines.size()) {
            return null;
        }
        return new ArrayList<TurnLine>(lines.subList(Math.max(0, index - 1), lines.size()));
    }

    /** The walk's token. A step queued with an older one does nothing. */
    public int token() {
        return playId;
    }

    public int index() {
        return index;
    }

    public List<TurnLine> lines() {
        return lines;
    }

    /** True while a line is still waiting to be shown. */
    public boolean walking() {
        return index < lines.size();
    }

    /** What to run once this walk finishes (a persona switch). */
    public void setAfter(Runnable done) {
        afterPlay = done;
    }

    /** Leave every queued step of this walk inert, without stopping the voice. */
    public void abandon() {
        playId++;
        afterPlay = null;
    }

    /**
     * Walk a list that is still being written. The caller has already ended the previous walk; this does not
     * stop the voice again.
     */
    public void follow(List<TurnLine> live) {
        lines = live == null ? new ArrayList<TurnLine>() : live;
        index = 0;
        step(playId);
    }

    /** The words the voice says for a line: the model's own connect failures are said plainly. */
    public String spokenLine(String line) {
        if (line != null && line.contains("unable to connect to my database") && host.online()) {
            return "I don't have that in my notes.";
        }
        return line == null ? "" : line;
    }

    /** Show these lines in order, then run {@code done} once the voice has gone quiet. */
    public void play(List<TurnLine> next, Runnable done) {
        stopPlay();
        lines = next;
        index = 0;
        afterPlay = done;
        step(playId);
    }

    /** A reply stopped being written: its walk may finish now. */
    public void streamEnded() {
        host.clearStreaming();
    }

    /** A reply is being written: its walk waits for the stream to close before it finishes. */
    public void streamStarted() {
        host.setStreaming();
    }

    /** The pending walk is over: the lines shown so far are history, not a walk in progress. */
    public void cancelWalk() {
        playId++;
        afterPlay = null;
        host.clearStreaming();
    }

    /** End whatever is on show: the token bump leaves every queued step of the old walk inert. */
    public void stopPlay() {
        playId++;
        afterPlay = null;
        host.clearStreaming();
        android.os.Handler handler = host.handler();
        handler.removeCallbacksAndMessages(null);
        ai.dvine.voice.TalkBridge talk = host.talk();
        if (talk != null) {
            talk.stop();
        }
        // an active dictation session is the user's recording: it ends only on its own button, never with the playback
        ai.dvine.voice.ListenBridge listen = host.listen();
        if (listen != null && listen.isListening() && !host.dictating()) {
            listen.stop();
            host.stage().setListening(false);
        }
    }

    /** Play the next line, or finish the walk when the lines run out. */
    public void step(final int token) {
        if (!host.samePlay(token)) {
            return;
        }
        if (index >= lines.size()) {
            if (host.streaming()) {
                host.handler().postDelayed(() -> step(token), 120);
                return;
            }
            host.keepAwake(false);
            host.stage().setMode(ai.dvine.stage.StageMode.DONE);
            Runnable after = afterPlay;
            afterPlay = null;

            final Runnable done = after;
            if (done != null) {
                ai.dvine.voice.TalkBridge talk = host.talk();
                if (talk == null) {
                    done.run();
                } else {
                    // the microphone waits until the voice has finished, so it doesn't hear DVINE's own words
                    talk.whenQuiet(() -> host.handler().postDelayed(() -> {
                        if (host.samePlay(token)) {
                            done.run();
                        }
                    }, 250));
                }
            }
            return;
        }
        final TurnLine line = lines.get(index);
        index++;
        final String who = host.stage().agent();
        host.stage().ensureFace(who, () -> {
            if (!host.samePlay(token)) {
                return;
            }
            ai.dvine.voice.TalkBridge talk = host.talk();
            if (talk != null) {
                talk.setRobot("ibiko".equals(who));
            }
            if ("SAY".equals(line.cue)) {
                host.stage().setMode(ai.dvine.stage.StageMode.SPEAKING);
                host.stage().setThought(null);
                host.stage().setSubtitle(spokenLine(line.text));
                ai.dvine.stage.ChatPane chatPane = host.chatPane();
                if (chatPane != null) {
                    chatPane.setLive(null);
                }
                if (chatPane != null && line.text != null && !line.text.isEmpty()) {
                    chatPane.assistant(who, spokenLine(line.text));
                }
                if (host.speakReply() && talk != null && !host.spokenLive(line)
                        && line.text != null) {
                    SentenceStream cut = new SentenceStream();
                    boolean ivieVoice = "ivie".equals(who);
                    for (String sentence : cut.add(spokenLine(line.text))) {
                        talk.queue(ivieVoice, sentence);
                    }
                    for (String sentence : cut.finish()) {
                        talk.queue(ivieVoice, sentence);
                    }
                }
                host.handler().post(() -> step(token));
            } else if (line.text != null && line.text.length() > 0) {
                host.stage().setThought(line.text);
                host.stage().setMode(ai.dvine.stage.StageMode.THINKING);
                host.stage().setSubtitle("");
                host.handler().post(() -> step(token));
            } else {
                host.stage().setThought(null);
                host.stage().setMode(ai.dvine.stage.StageMode.THINKING);
                host.handler().postDelayed(() -> step(token), 520);
            }
        });
    }
}