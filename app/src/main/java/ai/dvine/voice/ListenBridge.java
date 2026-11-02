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
 */
public final class ListenBridge implements RecognitionListener {
    /** Quiet after the last new word that ends a turn. */
    public static final long SETTLE_MS = 850;
    /** How long to wait for the final words once the turn is closed, before the partial words are used. */
    static final long FINAL_WAIT_MS = 700;
    private static volatile boolean onDeviceFailed;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private String partial = "";
    private boolean heardSomething, onDevice, delivered;
    private final Runnable late = () -> usePartial();
    private final Runnable settle = () -> {
        if (this.listening && !this.hold && partial.length() > 0) {
            finish();
            main.postDelayed(late, FINAL_WAIT_MS);
        }
    };
    private final Context context;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private ListenCallback callback;

    public ListenBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean available() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    /** Listening while the user holds a finger down: pauses don't end it (they let go to end it). */
    private boolean hold;

    public void start(final ListenCallback callback) {
        start(callback, false);
    }

    /** {@code hold}: keep listening through pauses; {@link #finish} ends it and delivers what was heard. */
    public void start(final ListenCallback callback, boolean hold) {
        this.hold = hold;
        stop();
        this.callback = callback;
        if (!available()) {
            callback.onError("Speech recognition isn't on this phone.");
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
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        if (hold) {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20000L);
        } else {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L);
        }
        listening = true;
        recognizer.startListening(intent);
    }

    /** The final words are slow: the partial words are the answer. */
    private void usePartial() {
        if (delivered || partial.isEmpty() || callback == null) {
            return;
        }
        delivered = true;
        String said = partial;
        ListenCallback cb = callback;
        stop();
        cb.onResult(said);
    }

    /** Ends listening and delivers the final words (instead of {@link #stop}, which drops them). */
    public void finish() {
        if (recognizer != null && listening) {
            recognizer.stopListening();
        }
    }

    public void stop() {
        listening = false;
        main.removeCallbacks(settle);
        main.removeCallbacks(late);
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
            if (cb != null) {
                main.post(() -> start(cb, h));
            }
            return;
        }
        listening = false;
        if (callback == null || delivered) {
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
        if (callback != null && !delivered) {
            delivered = true;
            String said = first(results);
            callback.onResult(said.isEmpty() ? partial : said);
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
