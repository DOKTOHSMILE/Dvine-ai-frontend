package ai.dvine.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

public final class ListenBridge implements RecognitionListener {
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

    public void start(final ListenCallback callback) {
        stop();
        this.callback = callback;
        if (!available()) {
            callback.onError("Speech recognition isn't on this phone.");
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        recognizer.startListening(intent);
    }

    public void stop() {
        listening = false;
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
        listening = false;
        if (callback == null) {
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
        if (callback != null) {
            callback.onResult(first(results));
        }
    }

    @Override
    public void onPartialResults(Bundle partial) {
        if (callback == null) {
            return;
        }
        String text = first(partial);
        if (text.length() > 0) {
            callback.onPartial(text);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
}
