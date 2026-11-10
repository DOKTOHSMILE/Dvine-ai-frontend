package ai.ibiko.chat;

import android.os.Handler;

import java.util.Collections;
import java.util.List;

import ai.ibiko.voice.SentenceStream;

/**
 * A reply while the model writes it: the words show in the chat as they come (a few times a second), and when the
 * reply is to be spoken each finished sentence is read out at once, so the voice starts after the first sentence
 * instead of after the whole answer. {@link #piece} runs on the model's thread; {@link Out} is called on the main one.
 */
public final class LiveReply {
    /** Where the reply goes; checked on the main thread. */
    public interface Out {
        /** Still the reply the user is waiting for (not stopped or replaced). */
        boolean current();

        void speak(String sentence);

        void show(String soFar);
    }

    private final Handler main;
    private final boolean speak;
    private final Out out;
    private final StringBuilder text = new StringBuilder();
    private final SentenceStream sentences = new SentenceStream();
    private long shown;
    private volatile boolean spoke;

    public LiveReply(Handler main, boolean speak, Out out) {
        this.main = main;
        this.speak = speak;
        this.out = out;
    }

    public void piece(String t) {
        final String sofar;
        synchronized (text) {
            text.append(t);
            sofar = text.toString();
        }
        final List<String> ready = speak ? sentences.add(t) : Collections.<String>emptyList();
        long now = System.currentTimeMillis();
        final boolean show = now - shown > 90;
        if (show) {
            shown = now;
        }
        if (ready.isEmpty() && !show) {
            return;
        }
        main.post(() -> {
            if (!out.current()) {
                return;
            }
            for (String sentence : ready) {
                out.speak(sentence);
                spoke = true;
            }
            if (show) {
                out.show(sofar);
            }
        });
    }

    /** The model finished: the last sentences are read out; true when the reply was spoken this way. */
    public boolean end(String all) {
        final List<String> rest = speak ? sentences.finish() : Collections.<String>emptyList();
        boolean any = speak && (spoke || !rest.isEmpty());
        main.post(() -> {
            if (!out.current()) {
                return;
            }
            for (String sentence : rest) {
                out.speak(sentence);
            }
        });
        return any && all != null && !all.trim().isEmpty();
    }
}
