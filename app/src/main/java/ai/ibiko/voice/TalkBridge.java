package ai.ibiko.voice;

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

    /** Told when reading starts, pauses, carries on or ends (for the notification with Pause and Stop). */
    public interface StateListener {
        /** {@code reading}: something is being read or is paused; {@code text}: the sentence it is on. */
        void onState(boolean reading, boolean paused, String text, boolean ivie);
    }

    private StateListener state;

    public void setStateListener(StateListener l) {
        state = l;
    }

    /** A sentence waiting in the queue, or being read: kept so a pause can carry on from it. */
    private static final class Line {
        final boolean ivie;
        final String text, id;

        Line(boolean ivie, String text, String id) {
            this.ivie = ivie;
            this.text = text;
            this.id = id;
        }
    }

    private final List<Line> lines = new ArrayList<Line>();
    /** What {@link #speak} is reading (one sentence with its callback), for a pause. */
    private boolean sayIvie, sayThought;
    private String sayText;
    /** Paused: what to read again on {@link #resume}. */
    private boolean paused;
    private List<Line> pausedLines = new ArrayList<Line>();
    private String pausedSay;
    private boolean pausedSayIvie, pausedSayThought;
    private TalkDone pausedDone;
    private Runnable pausedQuiet;
    private boolean lastIvie;

    public boolean paused() {
        return paused;
    }

    /** Whether something is being read (or waits, paused). */
    public boolean reading() {
        return paused || queued > 0 || pending != null;
    }

    /**
     * Pauses reading: the phone's voice can't stop mid-word and carry on, so it stops and keeps the sentence it was
     * on and those after it; {@link #resume} reads them from that sentence. False when nothing was being read.
     */
    public boolean pause() {
        if (paused || !(queued > 0 || pending != null)) {
            return false;
        }
        pausedLines = new ArrayList<Line>(lines);
        pausedSay = pending != null ? sayText : null;
        pausedSayIvie = sayIvie;
        pausedSayThought = sayThought;
        pausedDone = pending;
        pausedQuiet = whenQuiet;
        generation++;
        pending = null;
        queued = 0;
        whenQuiet = null;
        lines.clear();
        if (tts != null) {
            tts.stop();
        }
        neural.stop();
        paused = true;
        tell(true, true, pausedSay != null ? pausedSay : pausedLines.isEmpty() ? "" : pausedLines.get(0).text, lastIvie);
        return true;
    }

    /** Carries on from the sentence it was paused on. */
    public boolean resume() {
        if (!paused) {
            return false;
        }
        paused = false;
        List<Line> again = pausedLines;
        Runnable quiet = pausedQuiet;
        pausedLines = new ArrayList<Line>();
        pausedQuiet = null;
        if (pausedSay != null) {
            String t = pausedSay;
            pausedSay = null;
            TalkDone d = pausedDone;
            pausedDone = null;
            say(pausedSayIvie, t, d, pausedSayThought, true);
        }
        for (Line l : again) {
            queue(l.ivie, l.text, true);
        }
        if (quiet != null) {
            whenQuiet(quiet);
        }
        if (pausedSay == null && again.isEmpty() && quiet == null) {
            tell(false, false, "", lastIvie);
        }
        return true;
    }

    /** True from asking the voice to speak until its first sound (the model is still loading or making it). */
    private volatile boolean preparing, sounding;
    private final java.util.List<java.util.function.Consumer<Boolean>> preparingListeners =
            new java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<Boolean>>();

    /** Told true while the voice gets ready to say something (the player's ring), false once it sounds or stops. */
    public void addPreparingListener(java.util.function.Consumer<Boolean> l) {
        preparingListeners.add(l);
    }

    public boolean preparing() {
        return preparing;
    }

    private void preparing(final boolean now) {
        if (preparing == now) {
            return;
        }
        preparing = now;
        for (final java.util.function.Consumer<Boolean> l : preparingListeners) {
            main.post(() -> l.accept(now));
        }
    }

    private void tell(final boolean reading, final boolean isPaused, final String text, final boolean ivie) {
        if (reading) {
            sounding = true;
            preparing(false);
        } else {
            sounding = false;
            preparing(false);
        }
        main.post(() -> {
            if (reading && !isPaused) {
                holdFocus();
            } else if (!reading) {
                dropFocus();
                AudioGate.leave(gate);
            }
        });
        final StateListener l = state;
        if (l != null) {
            main.post(() -> l.onState(reading, isPaused, text, ivie));
        }
        for (final StateListener o : stateListeners) {
            main.post(() -> o.onState(reading, isPaused, text, ivie));
        }
    }

    /** Nothing left to read: told after a moment, so the gap between two sentences doesn't count as the end. */
    private final Runnable endCheck = () -> {
        if (!reading()) {
            tell(false, false, "", lastIvie);
        }
    };

    private void maybeEnded() {
        main.removeCallbacks(endCheck);
        main.postDelayed(endCheck, 1200);
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

    /** The online voices (the most human-sounding) may be used: Settings › Voice › Natural voices. */
    private final boolean natural;

    private final android.content.SharedPreferences prefs;

    public TalkBridge(Context context) {
        filesDirCache = context.getFilesDir();
        prefs = context.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
        natural = prefs.getBoolean("natural_voices", false);
        // Settings › Voice › Speech engine: another text-to-speech app installed on the device (its voices), or the default
        String engine = prefs.getString("tts_engine", "");
        TextToSpeech.OnInitListener init = status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                main.post(this::loadVoices);
            }
        };
        tts = engine == null || engine.isEmpty() ? new TextToSpeech(context.getApplicationContext(), init)
                : new TextToSpeech(context.getApplicationContext(), init, engine);
        tts.setOnUtteranceProgressListener(this);
        neural = NeuralVoice.shared(context.getFilesDir()); // the one engine, kept on standby: loaded once for the app
        audio = context.getSystemService(android.media.AudioManager.class);
        refreshVoice();
    }

    /**
     * A human voice downloaded into the app (downloaded with the app files on the first start; Settings › Voice): every
     * reply is spoken with it, on the device, at its own natural pitch; until it is in place, the device's voice speaks.
     */
    private final NeuralVoice neural;

    /** Every sentence this bridge gives the shared voice comes back here as it starts and ends. */
    private final NeuralVoice.Events voiceEvents = new NeuralVoice.Events() {
        @Override
        public void onStart(String id) {
            TalkBridge.this.onStart(id);
        }

        @Override
        public void onDone(String id) {
            TalkBridge.this.onDone(id);
        }

        @Override
        public void onError(String id) {
            String why = neural.failed();
            if (why != null && !toldProblem && problems != null) {
                toldProblem = true; // said once: one voice only, so nothing else reads in its place
                main.post(() -> problems.accept(why));
            }
            TalkBridge.this.onError(id);
        }
    };

    /** Told once when the chosen voice couldn't start (the screen shows it). */
    private java.util.function.Consumer<String> problems;
    private boolean toldProblem;

    public void setProblemListener(java.util.function.Consumer<String> l) {
        problems = l;
    }

    /** The app-wide gate: only one thing speaks or plays at a time. */
    private final AudioGate.Owner gate = this::stop;

    private void takeOver() {
        if (AudioGate.holder() != gate) {
            AudioGate.take(gate);
        }
    }

    /** The device's audio focus: other apps' sound pauses while Ibiko speaks; a call pauses Ibiko. */
    private final android.media.AudioManager audio;
    private android.media.AudioFocusRequest focus;
    private boolean pausedByFocus;

    private void holdFocus() {
        if (audio == null || focus != null) {
            return;
        }
        focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> main.post(() -> {
                    if (change == android.media.AudioManager.AUDIOFOCUS_LOSS) {
                        stop(); // another app took the sound for good
                    } else if (change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pausedByFocus = pause(); // a call or a short sound: Ibiko waits
                    } else if (change == android.media.AudioManager.AUDIOFOCUS_GAIN && pausedByFocus) {
                        pausedByFocus = false;
                        resume();
                    }
                }), main).build();
        try {
            audio.requestAudioFocus(focus);
        } catch (RuntimeException ignored) {
            // no focus to be had: it still speaks
        }
    }

    private void dropFocus() {
        if (audio != null && focus != null && !paused) {
            try {
                audio.abandonAudioFocusRequest(focus);
            } catch (RuntimeException ignored) {
                // already gone
            }
            focus = null;
        }
    }

    /** The player's speed (1x, 1.25x, 1.5x, 2x, 0.75x), on top of the setting: from the next sentence. */
    private volatile float playerSpeed = 1f;

    public void setPlayerSpeed(float s) {
        playerSpeed = Math.max(0.5f, Math.min(2.5f, s));
    }

    public float playerSpeed() {
        return playerSpeed;
    }

    /** More listeners for reading starting, pausing and ending (the floating player, the notification). */
    private final java.util.List<StateListener> stateListeners = new java.util.concurrent.CopyOnWriteArrayList<StateListener>();

    public void addStateListener(StateListener l) {
        stateListeners.add(l);
    }

    public void removeStateListener(StateListener l) {
        stateListeners.remove(l);
    }
    private String neuralKey = "";

    /** Picks up the voice chosen in Settings (call when the chat comes back from Settings). */
    public void refreshVoice() {
        String key = prefs.getString("voice_pack", "kokoro");
        VoicePacks.Pack p = VoicePacks.byKey(key == null ? "" : key);
        String want = p != null && VoicePacks.installed(filesDir(), p) ? p.key : "";
        if (!want.equals(neuralKey)) {
            neuralKey = want;
            neural.use(want.isEmpty() ? null : p);
        }
    }

    private java.io.File filesDirCache;

    private java.io.File filesDir() {
        return filesDirCache;
    }

    /** The downloaded voice is speaking (loaded and chosen). */
    public boolean human() {
        return neural != null && !neuralKey.isEmpty() && neural.ready();
    }

    /**
     * The human voice is chosen: every sentence goes to it (one that comes while it loads waits for it). The
     * device's voice never speaks in its place, not even when it fails to start: one voice only.
     */
    private boolean humanChosen() {
        return neural != null && !neuralKey.isEmpty(); // one voice: chosen means it speaks, or nothing does
    }

    /** The speaker in the downloaded pack: a woman's or a man's voice, as chosen in Settings. */
    private int speakerFor(boolean ivie) {
        VoicePacks.Pack p = VoicePacks.byKey(neuralKey); // the chosen pack, even while it is still loading
        if (p == null) {
            return 0;
        }
        String named = prefs.getString(ivie ? "voice_human_woman" : "voice_human_man", "");
        return p.speaker(!ivie, named).id;
    }

    /** Opens the device's page for downloading voices (Google's voice data: the higher-quality voices). */
    public static void installVoices(android.app.Activity a) {
        try {
            a.startActivity(new android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .setPackage("com.google.android.tts"));
        } catch (RuntimeException noGoogle) {
            try {
                a.startActivity(new android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
            } catch (RuntimeException none) {
                a.startActivity(new android.content.Intent("com.android.settings.TTS_SETTINGS"));
            }
        }
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

    /**
     * Ibiko's voice is Obi's pitch and pace shifted by the robot constants in {@link VoiceTuning}: a little
     * brighter, a little slower — the bright little robot next to Obi's baritone, without the sharp, hurried
     * edge the system voice used to have.
     */
    private float bright(boolean ivie) {
        return 1f; // one assistant, one voice: no brightened "robot" pitch on top of the voice chosen
    }

    private float quick(boolean ivie) {
        return 1f;
    }

    /**
     * There are no personas: every line is spoken in one voice, a woman's or a man's as chosen in Settings › Voice
     * (voice_gender). The flag callers pass for who is speaking is not used.
     */
    private boolean woman() {
        return "female".equals(prefs.getString("voice_gender", "male"));
    }

    private volatile boolean robot;
    private volatile float rateScale = 1f;

    /** Settings › Voice › Speaking speed (0.85, 1 or 1.2), on top of each persona's own pace. */
    public void setRateScale(float scale) {
        rateScale = Math.max(0.5f, Math.min(2f, scale));
    }

    /** Ibiko's voice: Obi's voice made a touch brighter and a touch slower ({@link VoiceTuning#ROBOT_BRIGHT}). */
    public void setRobot(boolean on) {
        robot = on;
    }

    public void speak(boolean ivie, String text, TalkDone done) {
        say(ivie, text, done, false);
    }

    /**
     * The sentence that will be said next (reading aloud): its sound is made now, while this one plays, so there is no
     * wait at the full stop. Only for the human voice; the same speed and voice {@link #speak} will use.
     */
    public void prepare(boolean ivie, String text) {
        ivie = woman();
        String said = SentenceStream.speakable(text);
        if (neural == null || !humanChosen() || said == null || said.trim().isEmpty()) {
            return;
        }
        VoiceTuning t = tuning[ivie ? 1 : 0];
        neural.prepare(said, speakerFor(ivie), t.rate * rateScale * playerSpeed);
    }

    /** The inner-thought voice: same voice, softer, a touch lower and slower. */
    public void whisper(boolean ivie, String text, TalkDone done) {
        say(ivie, text, done, true);
    }

    private void say(boolean ivie, String text, TalkDone done, boolean thought) {
        say(ivie, text, done, thought, false);
    }

    private void say(boolean ivie, String text, TalkDone done, boolean thought, boolean again) {
        ivie = woman();
        if (!again) {
            dropPause(); // something new to read: what was paused is let go
        }
        text = SentenceStream.speakable(text); // the words only: no **, <, " or other symbols read out
        lines.clear();
        final int token = ++generation;
        pending = done;
        queued = 0; // a flush drops the queued sentences
        whenQuiet = null;
        if (!(ready || humanChosen()) || text == null || text.trim().isEmpty()) {
            main.postDelayed(() -> finish(token), 280);
            return;
        }
        VoiceTuning t = tuning[ivie ? 1 : 0];
        if (humanChosen()) {
            speaking = text;
            sayText = text;
            sayIvie = ivie;
            sayThought = thought;
            lastIvie = ivie;
            if (tts != null) {
                tts.stop();
            }
            takeOver();
            if (!sounding) {
                preparing(true);
            }
            neural.speak(voiceEvents, (ivie ? "IVIE-" : "OBI-") + token, text, speakerFor(ivie),
                    (thought ? t.thoughtRate() : t.rate) * rateScale * playerSpeed, thought ? VoiceTuning.THOUGHT_VOLUME : 1f, true);
            return;
        }
        Voice v = t.voice == null ? null : byName.get(t.voice);
        if (v != null) {
            tts.setVoice(v);
        }
        String g = t.voice == null ? "" : genders.getOrDefault(t.voice, "");
        float pitch = t.pitchFor(ivie, g);
        float bright = bright(ivie), quick = quick(ivie);
        tts.setPitch(VoiceTuning.warm((thought ? pitch * t.thoughtPitch() / Math.max(0.01f, t.pitch) : pitch) * bright));
        tts.setSpeechRate((thought ? t.thoughtRate() : t.rate) * quick * rateScale * playerSpeed);
        takeOver();
        speaking = text;
        sayText = text;
        sayIvie = ivie;
        sayThought = thought;
        lastIvie = ivie;
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, thought ? VoiceTuning.THOUGHT_VOLUME : 1f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, (ivie ? "IVIE-" : "OBI-") + token);
    }

    /** Sentences queued by {@link #queue} that haven't finished being spoken. */
    private int queued;
    private int serial;
    private Runnable whenQuiet;

    /**
     * Speaks a sentence after those already queued, without cutting them off: the reply is read out sentence by
     * sentence while the model is still writing the rest.
     */
    public void queue(boolean ivie, String text) {
        queue(ivie, text, false);
    }

    private void queue(boolean ivie, String text, boolean again) {
        ivie = woman();
        if (!again && paused) {
            pausedLines.add(new Line(ivie, SentenceStream.clean(text), "")); // a reply still coming in: kept for Resume
            return;
        }
        text = SentenceStream.speakable(text);
        if (!(ready || humanChosen()) || text == null || text.trim().isEmpty()) {
            return;
        }
        VoiceTuning t = tuning[ivie ? 1 : 0];
        if (humanChosen()) {
            speaking = text;
            queued++;
            lastIvie = ivie;
            String id = (ivie ? "IVIE-Q" : "OBI-Q") + generation + "x" + queued + "n" + (++serial);
            lines.add(new Line(ivie, text, id));
            takeOver();
            if (!sounding) {
                preparing(true);
            }
            neural.speak(voiceEvents, id, text, speakerFor(ivie), t.rate * rateScale * playerSpeed, 1f, false);
            return;
        }
        Voice v = t.voice == null ? null : byName.get(t.voice);
        if (v != null && queued == 0) {
            tts.setVoice(v);
        }
        String g = t.voice == null ? "" : genders.getOrDefault(t.voice, "");
        float bright = bright(ivie), quick = quick(ivie);
        tts.setPitch(VoiceTuning.warm(t.pitchFor(ivie, g) * bright));
        tts.setSpeechRate(t.rate * quick * rateScale * playerSpeed);
        takeOver();
        speaking = text;
        queued++;
        lastIvie = ivie;
        String id = (ivie ? "IVIE-Q" : "OBI-Q") + generation + "x" + queued + "n" + (++serial);
        lines.add(new Line(ivie, text, id));
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, id);
    }

    /** Whether queued sentences are still being read. */
    public boolean talking() {
        return queued > 0;
    }

    /** Runs {@code then} once the queued sentences have all been read (at once if there are none). */
    public void whenQuiet(Runnable then) {
        if (queued <= 0) {
            then.run();
        } else {
            whenQuiet = then;
        }
    }

    private void queueDone(String utteranceId) {
        String gen = utteranceId.substring(utteranceId.indexOf("-Q") + 2, utteranceId.indexOf('x'));
        main.post(() -> {
            if (!gen.equals(String.valueOf(generation)) || queued <= 0) {
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).id.equals(utteranceId)) {
                    lines.remove(i);
                    break;
                }
            }
            queued--;
            if (queued == 0) {
                maybeEnded();
            }
            if (queued == 0 && whenQuiet != null) {
                Runnable r = whenQuiet;
                whenQuiet = null;
                r.run();
            }
        });
    }

    public void stop() {
        generation++;
        pending = null;
        queued = 0;
        whenQuiet = null;
        lines.clear();
        boolean was = paused;
        dropPause();
        if (tts != null) {
            tts.stop();
        }
        neural.stop();
        preparing(false);
        if (was) {
            tell(false, false, "", lastIvie);
        }
        maybeEnded();
    }

    private void dropPause() {
        paused = false;
        pausedLines = new ArrayList<Line>();
        pausedSay = null;
        pausedDone = null;
        pausedQuiet = null;
    }

    public void shutdown() {
        stop();
        AudioGate.leave(gate); // the shared voice stays loaded for the rest of the app
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
            list.add(new VoiceTuning.Candidate(v.getName(), l.getCountry(), v.isNetworkConnectionRequired(), g, v.getQuality()));
        }
        ranked = VoiceTuning.rank(list, natural);
        missing = VoiceTuning.missing(ranked);
        String[] picks = VoiceTuning.pick(ranked);
        // once, when the device has African English voices: the personas move to them from a voice chosen before
        // (afterwards a voice the user picks by hand is kept)
        if (!prefs.getBoolean("voices_african", false) && VoiceTuning.african(ranked, picks[0]) && VoiceTuning.african(ranked, picks[1])) {
            tuning[0].voice = picks[0];
            tuning[1].voice = picks[1];
            prefs.edit().putBoolean("voices_african", true).apply();
        }
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
        ivie = woman();
        if (human()) {
            VoicePacks.Pack p = neural.pack();
            VoicePacks.Speaker sp = p.speaker(!ivie, prefs.getString(ivie ? "voice_human_woman" : "voice_human_man", ""));
            return sp.title + ", a human voice on the device";
        }
        VoiceTuning t = tuning[ivie ? 1 : 0];
        String g = t.voice == null ? "" : genders.getOrDefault(t.voice, "");
        return (t.voice == null ? "the device's default voice" : t.voice) + (g.isEmpty() ? "" : " (" + g + ")");
    }

    @Override
    public void onStart(String utteranceId) {
        if (utteranceId != null) {
            tell(true, false, speakingFor(utteranceId), utteranceId.startsWith("IVIE"));
        }
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
        if (utteranceId != null && utteranceId.contains("-Q")) {
            queueDone(utteranceId);
            return;
        }
        finish(utteranceId);
    }

    @Override
    public void onError(String utteranceId) {
        if (utteranceId != null && utteranceId.contains("-Q")) {
            queueDone(utteranceId);
            return;
        }
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
        maybeEnded();
    }

    /** The sentence an utterance reads (onStart comes on the engine's thread: a copy is read). */
    private String speakingFor(String utteranceId) {
        if (utteranceId.contains("-Q")) {
            List<Line> copy;
            try {
                copy = new ArrayList<Line>(lines); // written on the main thread; a copy taken mid-change is skipped
            } catch (RuntimeException busy) {
                copy = new ArrayList<Line>();
            }
            for (Line l : copy) {
                if (l.id.equals(utteranceId)) {
                    return l.text;
                }
            }
        }
        String t = sayText;
        return t == null ? speaking : t;
    }
}
