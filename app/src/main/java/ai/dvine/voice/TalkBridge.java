package ai.dvine.voice;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/** One utterance at a time. Pitch stays close to natural. */
public final class TalkBridge extends UtteranceProgressListener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    private boolean ready;
    private int generation;
    private TalkDone pending;

    public TalkBridge(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> ready = status == TextToSpeech.SUCCESS);
        tts.setOnUtteranceProgressListener(this);
    }

    public void speak(boolean ivie, String text, TalkDone done) {
        final int token = ++generation;
        pending = done;
        if (!ready || text == null || text.trim().isEmpty()) {
            main.postDelayed(() -> finish(token), 280);
            return;
        }
        tts.setLanguage(Locale.getDefault());
        tts.setPitch(ivie ? 1.05f : 0.92f);
        tts.setSpeechRate(0.97f);
        String id = (ivie ? "IVIE-" : "OBI-") + token;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
    }

    public void stop() {
        generation++;
        pending = null;
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        stop();
        if (tts != null) {
            tts.shutdown();
        }
    }

    @Override
    public void onStart(String utteranceId) {
    }

    @Override
    public void onDone(String utteranceId) {
        finish(utteranceId);
    }

    @Override
    public void onError(String utteranceId) {
        finish(utteranceId);
    }

    private void finish(String utteranceId) {
        int token = 0;
        int dash = utteranceId == null ? -1 : utteranceId.lastIndexOf('-');
        if (dash >= 0) {
            try {
                token = Integer.parseInt(utteranceId.substring(dash + 1));
            } catch (NumberFormatException ignored) {
                token = -1;
            }
        }
        final int heard = token;
        main.post(() -> finish(heard));
    }

    private void finish(int token) {
        if (token != generation || pending == null) {
            return;
        }
        TalkDone done = pending;
        pending = null;
        done.onDone();
    }
}
