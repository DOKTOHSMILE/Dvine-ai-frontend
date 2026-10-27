package ai.dvine.voice;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One utterance at a time, in each persona's own voice. Voices are chosen by
 * {@link VoiceTuning} (Nigerian English first, a different voice for each
 * persona) and can be tuned by voice; inner thoughts use the same voice,
 * a little softer, lower and slower.
 */
public final class TalkBridge extends UtteranceProgressListener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;
    private boolean ready;
    private int generation;
    private TalkDone pending;
    private Listener listener;
    private final VoiceTuning[] tuning = {VoiceTuning.defaults(false), VoiceTuning.defaults(true)};
    private List<VoiceTuning.Candidate> ranked = new ArrayList<VoiceTuning.Candidate>();
    private final java.util.Map<String, Voice> byName = new java.util.HashMap<String, Voice>();
    private final java.util.Map<String, String> genders = new java.util.HashMap<String, String>();
    /** "male" or "female" when the phone has no voice of that gender (a persona borrows one), else null. */
    private volatile String missing;

    /** Lets the ambience swell while a line is spoken. */
    public interface Listener {
        void onSpeaking(boolean ivie, boolean speaking);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Told each word as the voice starts saying it (drives the mouth shapes). */
    public interface WordListener {
        void onWord(String word);
    }

    private WordListener words;
    private volatile String speaking = "";

    public void setWordListener(WordListener l) {
        words = l;
    }

    public TalkBridge(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                main.post(this::loadVoices);
            }
        });
        tts.setOnUtteranceProgressListener(this);
    }

    /** Restores saved tuning (from preferences); null keeps the defaults. */
    public void setTuning(boolean ivie, VoiceTuning t) {
        if (t != null) {
            tuning[ivie ? 1 : 0] = t;
        }
    }

    public VoiceTuning tuning(boolean ivie) {
        return tuning[ivie ? 1 : 0];
    }

    /** Applies a voice command for one persona. Returns true if it changed. */
    public boolean adjust(boolean ivie, String command) {
        String other = tuning[ivie ? 0 : 1].voice;
        return tuning[ivie ? 1 : 0].apply(command, ivie, ranked, other);
    }

    private volatile boolean robot;

    /** Ibiko's voice: the male voice, brighter and a little quicker. */
    public void setRobot(boolean on) {
        robot = on;
    }

    public void speak(boolean ivie, String text, TalkDone done) {
        say(ivie, text, done, false);
    }

    /** The inner-thought voice: same voice, softer, a touch lower and slower. */
    public void whisper(boolean ivie, String text, TalkDone done) {
        say(ivie, text, done, true);
    }

    private void say(boolean ivie, String text, TalkDone done, boolean thought) {
        final int token = ++generation;
        pending = done;
        if (!ready || text == null || text.trim().isEmpty()) {
            main.postDelayed(() -> finish(token), 280);
            return;
        }
        VoiceTuning t = tuning[ivie ? 1 : 0];
        Voice v = t.voice == null ? null : byName.get(t.voice);
        if (v != null) {
            tts.setVoice(v);
        }
        String g = t.voice == null ? "" : genders.getOrDefault(t.voice, "");
        float pitch = t.pitchFor(ivie, g);
        float bright = robot && !ivie ? 1.25f : 1f, quick = robot && !ivie ? 1.06f : 1f;
        tts.setPitch((thought ? pitch * t.thoughtPitch() / Math.max(0.01f, t.pitch) : pitch) * bright);
        tts.setSpeechRate((thought ? t.thoughtRate() : t.rate) * quick);
        speaking = text;
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, thought ? VoiceTuning.THOUGHT_VOLUME : 1f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, (ivie ? "IVIE-" : "OBI-") + token);
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

    /** Reads the engine's English voices, ranks them and fills in any persona without a saved voice. */
    private void loadVoices() {
        Set<Voice> all;
        try {
            all = tts.getVoices();
        } catch (RuntimeException e) {
            return;
        }
        if (all == null) {
            return;
        }
        List<VoiceTuning.Candidate> list = new ArrayList<VoiceTuning.Candidate>();
        for (Voice v : all) {
            Locale l = v.getLocale();
            if (l == null || !"en".equals(l.getLanguage())) {
                continue;
            }
            byName.put(v.getName(), v);
            String g = VoiceTuning.genderOf(v.getName(), v.getFeatures());
            genders.put(v.getName(), g);
            list.add(new VoiceTuning.Candidate(v.getName(), l.getCountry(), v.isNetworkConnectionRequired(), g));
        }
        ranked = VoiceTuning.rank(list);
        missing = VoiceTuning.missing(ranked);
        String[] picks = VoiceTuning.pick(ranked);
        if (tuning[0].voice == null || !byName.containsKey(tuning[0].voice)) {
            tuning[0].voice = picks[0];
        }
        if (tuning[1].voice == null || !byName.containsKey(tuning[1].voice)) {
            tuning[1].voice = picks[1];
        }
        if (picks[0] == null) {
            Locale ng = new Locale("en", "NG");
            if (tts.isLanguageAvailable(ng) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(ng);
            }
        }
    }

    /** "male" or "female" when the phone has no voice of that gender, else null (known once voices load). */
    public String missingGender() {
        return missing;
    }

    /** The voice each persona speaks with and its gender, for "test your voices". */
    public String describe(boolean ivie) {
        VoiceTuning t = tuning[ivie ? 1 : 0];
        String g = t.voice == null ? "" : genders.getOrDefault(t.voice, "");
        return (t.voice == null ? "the phone's default voice" : t.voice) + (g.isEmpty() ? "" : " (" + g + ")");
    }

    @Override
    public void onStart(String utteranceId) {
        if (listener != null && utteranceId != null) {
            final boolean ivie = utteranceId.startsWith("IVIE");
            main.post(() -> listener.onSpeaking(ivie, true));
        }
    }

    @Override
    public void onRangeStart(String utteranceId, int start, int end, int frame) {
        String text = speaking;
        if (words != null && start >= 0 && end <= text.length() && start < end) {
            final String w = text.substring(start, end);
            main.post(() -> words.onWord(w));
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
