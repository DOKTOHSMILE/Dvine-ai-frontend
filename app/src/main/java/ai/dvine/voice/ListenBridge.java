package ai.dvine.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * The microphone. To answer quickly it (1) uses the phone's on-device recognizer when there is one (no round trip to
 * a server; it falls back to the usual one if the language isn't downloaded), (2) asks for a short end-of-speech
 * silence, and (3) doesn't wait for the recognizer's own, slower end-of-speech decision: once the words have stopped
 * changing for {@link #SETTLE_MS}, it closes the turn, and if the final words are slow to come, the last partial
 * words are used. Holding the mic keeps listening through pauses, as before.
 * <p>
 * A <em>continuous</em> session — the record button, tapped once — never ends by itself. Whenever a stretch closes
 * (the recognizer's own end-of-speech, a long silence, a hiccup) the words are handed over and the next stretch
 * starts straight away, so only tapping the record button again ({@link #finish}) ends the turn. Silence is not an
 * error there: only {@link #MAX_STRIKES} quiet failures in a row, or a dead microphone, stop the session.
 */
public final class ListenBridge implements RecognitionListener {
    /** Quiet after the last new word that ends a turn. */
    public static final long SETTLE_MS = 850;
    /** How long to wait for the final words once the turn is closed, before the partial words are used. */
    static final long FINAL_WAIT_MS = 700;
    /** A beat between one stretch ending and the next starting, so a restart is never a tight loop. */
    static final long RESTART_MS = 300;
    /** Stretches that end with nothing heard, in a row, before a continuous session gives up. */
    static final int MAX_STRIKES = 5;
    private static volatile boolean onDeviceFailed;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private String partial = "";
    private boolean heardSomething, onDevice, delivered;
    private int strikes;
    private final Runnable late = () -> usePartial();
    private final Context context;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean continuous;
    /** The record button was tapped again: end the turn after the words in flight are delivered. */
    private boolean stopping;
    private ListenCallback callback;

    public ListenBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean available() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    /** Listening while the user holds a finger down: pauses don't end it (they let go to end it). */
    private boolean hold;
    /* Declared after the fields they read: a field initializer may not name one declared below it. */
    private final Runnable settle = () -> {
        if (this.listening && partial.length() > 0 && (continuous || !this.hold)) {
            // close this stretch; the final words (or `late`, if they are slow) deliver what was heard
            if (recognizer != null) {
                recognizer.stopListening();
            }
            main.postDelayed(late, FINAL_WAIT_MS);
        }
    };
    /** The restart after a stretch of quiet or a hiccup: a beat first, so it never becomes a tight loop. */
    private final Runnable again = () -> resume();

    public void start(final ListenCallback callback) {
        start(callback, false);
    }

    /** {@code hold}: keep listening through pauses; {@link #finish} ends it and delivers what was heard. */
    public void start(final ListenCallback callback, boolean hold) {
        start(callback, hold, false);
    }

    /**
     * {@code hold}: keep listening through pauses. {@code continuous}: never end on its own — whenever the phone's
     * recognizer closes the session (its own end-of-speech, a long silence, a hiccup) the words are handed over and
     * the microphone starts straight up again, so only {@link #finish} (the record button) or a real failure ends
     * the turn.
     */
    public void start(final ListenCallback callback, boolean hold, boolean continuous) {
        this.hold = hold;
        stop();
        this.callback = callback;
        this.continuous = continuous;
        this.stopping = false;
        this.strikes = 0;
        if (!available()) {
            this.continuous = false;
            callback.onError("Speech recognition isn't on this device.");
            return;
        }
        partial = "";
        heardSomething = false;
        delivered = false;
        onDevice = !onDeviceFailed && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        try {
            recognizer = onDevice ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    : SpeechRecognizer.createSpeechRecognizer(context);
        } catch (RuntimeException e) {
            onDeviceFailed = true;
            onDevice = false;
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        }
        recognizer.setRecognitionListener(this);
        listening = true;
        recognizer.startListening(intent());
    }

    /**
     * The recognizer's own question: how long it waits before deciding you have stopped talking. A continuous
     * session asks for the short silence (each stretch ends quickly and is restarted), a held mic for the long one.
     */
    private Intent intent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        if (hold && !continuous) {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20000L);
        } else {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L);
        }
        return intent;
    }

    /** Opens the next listening stretch without tearing the recognizer down (continuous sessions only). */
    private void resume() {
        if (!continuous || stopping || callback == null) {
            return;
        }
        partial = "";
        heardSomething = false;
        delivered = false;
        listening = true;
        if (recognizer == null) {
            ListenCallback cb = callback;
            start(cb, hold, true);
            return;
        }
        try {
            recognizer.startListening(intent());
        } catch (RuntimeException e) {
            ListenCallback cb = callback;
            stop();
            if (cb != null) {
                start(cb, hold, true);
            }
        }
    }

    /** Schedules the next stretch of a continuous session, a beat from now. */
    private void restart() {
        main.removeCallbacks(again);
        main.postDelayed(again, RESTART_MS);
    }

    /** Whether this session keeps going by itself (the record button's session). */
    public boolean continuous() {
        return continuous;
    }

    /** The final words are slow: the partial words are the answer. */
    private void usePartial() {
        if (delivered || partial.isEmpty() || callback == null) {
            return;
        }
        delivered = true;
        String said = partial;
        ListenCallback cb = callback;
        if (continuous && !stopping) {
            // the words belong to the middle of the conversation: hand them over, then keep listening
            strikes = 0;
            cb.onResult(said);
            if (recognizer != null) {
                try {
                    recognizer.cancel();
                } catch (RuntimeException ignored) {
                    // already gone
                }
            }
            restart();
            return;
        }
        stop();
        cb.onResult(said);
    }

    /** Ends listening and delivers the final words (instead of {@link #stop}, which drops them). */
    public void finish() {
        stopping = true;
        main.removeCallbacks(again);
        if (recognizer != null && listening) {
            recognizer.stopListening();
        }
    }

    public void stop() {
        listening = false;
        main.removeCallbacks(settle);
        main.removeCallbacks(late);
        main.removeCallbacks(again);
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
    }

    public boolean isListening() {
        return listening;
    }

    private static String first(Bundle results) {
        if (results == null) {
            return "";
        }
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty() || list.get(0) == null) {
            return "";
        }
        return list.get(0).trim();
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
    }

    @Override
    public void onBeginningOfSpeech() {
        heardSomething = true;
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onError(int error) {
        main.removeCallbacks(settle);
        if (onDevice && !heardSomething && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
                || error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
            // the on-device recognizer can't do it here (no language pack): use the usual one from now on
            onDeviceFailed = true;
            ListenCallback cb = callback;
            boolean h = hold;
            boolean c = continuous;
            if (cb != null) {
                main.post(() -> start(cb, h, c));
            }
            return;
        }
        listening = false;
        if (callback == null) {
            return;
        }
        if (continuous && !stopping) {
            // quiet, a hiccup or a long silence is normal here: keep the session going
            if (delivered) {
                restart();
                return;
            }
            if (!partial.isEmpty()) {
                delivered = true;
                strikes = 0;
                callback.onResult(partial);
                restart();
                return;
            }
            if (++strikes >= MAX_STRIKES) {
                continuous = false;
                strikes = 0;
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    callback.onError("I didn't catch that.");
                } else if (error != SpeechRecognizer.ERROR_CLIENT) {
                    callback.onError("Microphone stopped.");
                } else {
                    callback.onError("Microphone stopped.");
                }
                return;
            }
            restart();
            return;
        }
        if (delivered) {
            return;
        }
        if (!partial.isEmpty() && !hold) {
            delivered = true; // it heard words, then stumbled: use them rather than "I didn't catch that"
            callback.onResult(partial);
            return;
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            callback.onError("I didn't catch that.");
        } else if (error != SpeechRecognizer.ERROR_CLIENT) {
            callback.onError("Microphone stopped.");
        }
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        main.removeCallbacks(settle);
        main.removeCallbacks(late);
        if (callback != null && !delivered) {
            delivered = true;
            String said = first(results);
            String text = said.isEmpty() ? partial : said;
            if (!text.isEmpty()) {
                strikes = 0;
            }
            callback.onResult(text);
        }
        if (continuous && !stopping && callback != null) {
            restart();
        }
    }

    @Override
    public void onPartialResults(Bundle partial) {
        if (callback == null) {
            return;
        }
        String text = first(partial);
        if (text.length() > 0) {
            heardSomething = true;
            strikes = 0;
            if (!text.equals(this.partial)) {
                this.partial = text;
                main.removeCallbacks(settle);
                main.postDelayed(settle, SETTLE_MS);
            }
            callback.onPartial(text);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
}