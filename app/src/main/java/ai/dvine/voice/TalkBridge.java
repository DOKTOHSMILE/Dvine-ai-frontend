package ai.dvine.voice;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One utterance at a time. Prefers Nigerian English voices (en-NG), then other
 * African and British English, and speaks slowly and gently. Obi takes the
 * deeper voice and Ivie the warmer one when two voices exist.
 */
public final class TalkBridge extends UtteranceProgressListener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    private boolean ready;
    private int generation;
    private TalkDone pending;
    private Voice obiVoice;
    private Voice ivieVoice;
    private Listener listener;

    /** Lets the ambience swell while a line is spoken. */
    public interface Listener {
        void onSpeaking(boolean ivie, boolean speaking);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public TalkBridge(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                pickVoices();
            }
        });
        tts.setOnUtteranceProgressListener(this);
    }

    public void speak(boolean ivie, String text, TalkDone done) {
        final int token = ++generation;
        pending = done;
        if (!ready || text == null || text.trim().isEmpty()) {
            main.postDelayed(() -> finish(token), 280);
            return;
        }
        Voice v = ivie ? ivieVoice : obiVoice;
        if (v != null) {
            tts.setVoice(v);
        } else {
            tts.setLanguage(new Locale("en", "NG"));
        }
        tts.setPitch(ivie ? 1.02f : 0.86f);
        tts.setSpeechRate(ivie ? 0.90f : 0.86f);
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

    /** Orders offline voices: en-NG first, then other African English, then en-GB, then any English. */
    private void pickVoices() {
        Set<Voice> all;
        try {
            all = tts.getVoices();
        } catch (RuntimeException e) {
            return;
        }
        if (all == null) {
            return;
        }
        List<Voice> ranked = new ArrayList<Voice>();
        for (Voice v : all) {
            Locale l = v.getLocale();
            if (l != null && "en".equals(l.getLanguage()) && !v.isNetworkConnectionRequired()) {
                ranked.add(v);
            }
        }
        if (ranked.isEmpty()) {
            return;
        }
        Collections.sort(ranked, (a, b) -> {
            int ra = rank(a);
            int rb = rank(b);
            return ra != rb ? ra - rb : a.getName().compareTo(b.getName());
        });
        int best = rank(ranked.get(0));
        List<Voice> top = new ArrayList<Voice>();
        for (Voice v : ranked) {
            if (rank(v) == best) {
                top.add(v);
            }
        }
        obiVoice = pickGender(top, "male", 0);
        ivieVoice = pickGender(top, "female", top.size() > 1 ? 1 : 0);
    }

    private static int rank(Voice v) {
        String c = v.getLocale().getCountry();
        if ("NG".equals(c)) return 0;
        if ("GH".equals(c) || "KE".equals(c) || "ZA".equals(c) || "TZ".equals(c)) return 1;
        if ("GB".equals(c)) return 2;
        return 3;
    }

    private static Voice pickGender(List<Voice> voices, String gender, int fallback) {
        for (Voice v : voices) {
            String n = v.getName().toLowerCase(Locale.US);
            Set<String> f = v.getFeatures();
            if ((f != null && f.contains("gender=" + gender)) || n.contains("-" + gender) || n.contains("_" + gender)) {
                return v;
            }
        }
        return voices.get(Math.min(fallback, voices.size() - 1));
    }

    @Override
    public void onStart(String utteranceId) {
        if (listener != null && utteranceId != null) {
            final boolean ivie = utteranceId.startsWith("IVIE");
            main.post(() -> listener.onSpeaking(ivie, true));
        }
    }

    @Override
    public void onDone(String utteranceId) {
        if (listener != null) {
            main.post(() -> listener.onSpeaking(false, false));
        }
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
