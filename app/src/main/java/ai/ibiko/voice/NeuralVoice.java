package ai.ibiko.voice;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Speaks with a downloaded voice pack ({@link VoicePacks}) instead of the device's text-to-speech: the neural model
 * (sherpa-onnx, on the device, no internet) makes the sound sentence by sentence and an AudioTrack plays it while the
 * rest is still being made. There is no pitch shifting: each voice speaks at its own natural pitch, and only the pace
 * follows the settings. A short pause after each sentence keeps the rhythm of someone talking.
 */
public final class NeuralVoice {
    /** Told as each sentence starts and ends, with the id it was given (the same calls the device's voice makes). */
    public interface Events {
        void onStart(String id);

        void onDone(String id);

        void onError(String id);

    }

    private static final class Item {
        final String id, text;
        final int speaker, epoch;
        final float speed, volume;
        final Events events;
        /** For a {@link #MARK}: run when everything queued before it has been made. */
        Runnable after;
        /** The message ("<chat>/<message>") whose voice this is: its sound is kept under that id. Null: none. */
        String owner;

        Item(Events events, String id, String text, int speaker, float speed, float volume, int epoch) {
            this.events = events;
            this.id = id;
            this.text = text;
            this.speaker = speaker;
            this.speed = speed;
            this.volume = volume;
            this.epoch = epoch;
        }
    }

    /** Why the human voice last failed to start (Settings › Voice shows it), in files/voices/. */
    public static final String PROBLEM = "voice_problem.txt";

    /** The pause after each sentence, in seconds. */
    static final float GAP = 0.16f;

    private final File filesDir;
    private final LinkedBlockingQueue<Item> queue = new LinkedBlockingQueue<Item>();
    /** Sentences to make ahead that arrived before the voice had loaded: held, not dropped, and made once it has. */
    private final java.util.ArrayList<Item> waitingAhead = new java.util.ArrayList<Item>();
    /** Marks that must run only after {@link #waitingAhead} has been made. */
    private final java.util.ArrayList<Item> waitingMarks = new java.util.ArrayList<Item>();
    private volatile int epoch;
    private volatile VoicePacks.Pack pack;
    private volatile boolean ready, closed;
    private volatile String failed;
    private com.k2fsa.sherpa.onnx.OfflineTts tts;
    private AudioTrack track;
    private int rate;
    private final Thread worker;

    private static NeuralVoice shared;

    /**
     * The one voice engine of the app, loaded once and kept on standby while Ibiko runs: every screen speaks through
     * it, so nothing waits for the model to load again and two voices can never play over each other.
     */
    public static synchronized NeuralVoice shared(File filesDir) {
        if (shared == null || shared.closed || !shared.filesDir.equals(filesDir)) {
            if (shared != null && !shared.closed) {
                shared.shutdown(); // another files folder (only in tests): a fresh engine for it
            }
            shared = new NeuralVoice(filesDir);
        }
        return shared;
    }

    /** Loads the chosen voice as soon as Ibiko starts (off the main thread), before anything is said. */
    public static void preload(File filesDir, String key) {
        VoicePacks.Pack p = VoicePacks.byKey(key == null ? "" : key);
        if (p != null && VoicePacks.installed(filesDir, p)) {
            shared(filesDir).use(p);
        }
    }

    private NeuralVoice(File filesDir) {
        this.filesDir = filesDir;
        worker = new Thread(this::run, "ibiko-voice");
        worker.setDaemon(true);
        worker.start();
    }

    /** Loads a pack (on the voice's own thread); null or a pack not downloaded turns the neural voice off. */
    public synchronized void use(VoicePacks.Pack p) {
        String key = p == null ? null : p.key;
        if (key != null ? key.equals(wanted) : wanted == null) {
            return; // already loaded or on its way: kept on standby, not loaded again
        }
        wanted = key;
        stop();
        loading = p != null;
        queue.offer(new Item(null, null, key, 0, 0, 0, -1)); // a "load" order, in turn with the speech
    }

    /** The pack last asked for (loaded, loading, or failed). */
    private String wanted;

    /** A pack is being loaded: sentences wait for it rather than going to the device's voice. */
    public boolean loading() {
        return loading && !closed;
    }

    private volatile boolean loading;

    /** Ready to speak: a pack is loaded. */
    public boolean ready() {
        return ready && !closed;
    }

    public VoicePacks.Pack pack() {
        return pack;
    }

    /** Why the last pack didn't load, or null. */
    public String failed() {
        return failed;
    }

    /** Speaks a sentence after those waiting; {@code flush} drops them (and what is playing) first. */
    public void speak(Events events, String id, String text, int speaker, float speed, float volume, boolean flush) {
        if (flush) {
            stop();
        }
        Item it = new Item(events, id, text, speaker, speed, volume, epoch);
        it.owner = owner;
        queue.offer(it);
    }

    /** The message being read or prepared now ("<chat>/<message>"): what is made is kept under it. Null: none. */
    private volatile String owner;

    public void setOwner(String messageOwner) {
        owner = messageOwner;
    }

    /** An item that only makes a sentence's sound ahead, to keep (not to play): marked by this epoch. */
    static final int AHEAD = -2;
    /** A mark in the queue: its runnable runs once all the sound queued before it is made (or couldn't be). */
    static final int MARK = -3;

    /** Runs {@code then} (on the voice's thread) once all the sentences prepared before this call are made. */
    public void afterMade(Runnable then) {
        Item mark = new Item(null, "mark", "", 0, 0, 0, MARK);
        mark.after = then;
        queue.offer(mark);
    }

    /** Whether this sentence's sound is made and kept (in memory, or on the device for a week). */
    public boolean has(String text, int speaker, float speed) {
        String key = madeKey(text, speaker, speed);
        SpeechCache c = said();
        if (owner != null) {
            return c != null && c.has(owner, key); // this message's own kept voice
        }
        synchronized (made) {
            if (made.containsKey(key)) {
                return true;
            }
        }
        return c != null && c.has(key);
    }

    /**
     * Sentences made ahead (text, speaker and speed to the sound): reading aloud asks for the next sentence while this
     * one plays, so when it comes it starts at once instead of being made after the full stop (seconds on a device).
     */
    private final java.util.LinkedHashMap<String, float[]> made = new java.util.LinkedHashMap<String, float[]>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, float[]> e) {
            return size() > 4;
        }
    };

    /** Sentences' sound kept on the device for a day ({@link SpeechCache}): Listen plays a prepared reply at once. */
    private volatile SpeechCache said;

    private SpeechCache said() {
        if (said == null && filesDir != null) {
            said = new SpeechCache(filesDir);
        }
        return said;
    }

    /** How high the voice is played (1 normal; above 1 the child's voice): applied as it plays, the speed unchanged. */
    private volatile float pitch = 1f;
    private float pitchSet = 1f;
    /** How fast the kept sound is played (the player's 0.75x … 2x): time-stretched as it plays, the pitch kept. */
    private volatile float tempo = 1f;
    private float tempoSet = 1f;

    public void setTempo(float s) {
        tempo = Math.max(0.5f, Math.min(2.5f, s));
    }

    /** Pitch and speed for the track, when either changed since it was last set. */
    private void applyParams(AudioTrack t) {
        float p = pitch, s = tempo;
        if (p == pitchSet && s == tempoSet) {
            return;
        }
        try {
            t.setPlaybackParams(new android.media.PlaybackParams().setPitch(p).setSpeed(s));
        } catch (RuntimeException ignored) {
            // this device can't change them: played as it is
        }
        pitchSet = p;
        tempoSet = s;
    }

    public void setPitch(float p) {
        pitch = Math.max(0.5f, Math.min(2f, p));
    }

    static String madeKey(String text, int speaker, float speed) {
        return speaker + "|" + Math.round(Math.max(0.5f, Math.min(2f, speed)) * 100) + "|" + (text == null ? "" : text.trim());
    }

    /** Makes the sound of a sentence that is coming next, while the voice is busy saying this one. */
    public void prepare(String text, int speaker, float speed) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String key = madeKey(text, speaker, speed);
        SpeechCache c = said();
        if (c != null && c.has(owner, key)) {
            return; // already made and kept for this message
        }
        Item it = new Item(null, "ahead", text, speaker, speed, 1f, AHEAD);
        it.owner = owner;
        if (queuedAhead(key, it.owner)) {
            return; // already waiting to be made: a second copy would make the same sentence twice
        }
        queue.offer(it);
    }

    /** Whether this sentence is already waiting to be made ahead (the same words, voice and message). */
    private boolean queuedAhead(String key, String who) {
        for (Item i : queue) {
            if (i.epoch != AHEAD) {
                continue;
            }
            boolean sameOwner = who == null ? i.owner == null : who.equals(i.owner);
            if (sameOwner && key.equals(madeKey(i.text, i.speaker, i.speed))) {
                return true;
            }
        }
        return false;
    }

    /** Stops at once: what is playing and everything waiting. */
    public void stop() {
        epoch++;
        queue.removeIf(i -> i.epoch >= 0);
        ready2play.clear();
        sounding = false;
        AudioTrack t = track;
        if (t != null) {
            try {
                t.pause(); // silent at once; the voice's thread clears what is left before it speaks again
            } catch (IllegalStateException ignored) {
                // released
            }
        }
    }

    public void shutdown() {
        closed = true;
        stop();
        worker.interrupt();
    }

    private void run() {
        try {
            while (!closed) {
                Item it = queue.take();
                if (it.epoch == MARK) {
                    if (!waitingAhead.isEmpty() && !ready) {
                        if (loading) {
                            waitingMarks.add(it); // the voice is still loading: light Listen only once the sound is kept
                            continue;
                        }
                        waitingAhead.clear(); // no voice is coming: nothing will be kept
                    }
                    try {
                        if (it.after != null) {
                            it.after.run();
                        }
                    } catch (RuntimeException ignored) {
                        // a screen gone away: nothing to light up
                    }
                    continue;
                }
                if (it.epoch == AHEAD) {
                    if (!ready) {
                        if (loading) {
                            waitingAhead.add(it); // made when the voice finishes loading, not thrown away
                        }
                        continue;
                    }
                    makeAhead(it);
                    continue;
                }
                if (it.epoch < 0) {
                    load(it.text);
                    continue;
                }
                if (it.epoch != epoch) {
                    continue;
                }
                if (!ready) {
                    // the voice didn't load: nothing is said in another voice (one voice only); the sentence ends
                    it.events.onError(it.id);
                    continue;
                }
                make(it);
            }
        } catch (InterruptedException ended) {
            // shut down
        }
        release();
    }

    private void load(String key) {
        release();
        ready = false;
        VoicePacks.Pack p = key == null ? null : VoicePacks.byKey(key);
        pack = p;
        if (p != null && VoicePacks.installed(filesDir, p)) {
            File d = VoicePacks.folder(filesDir, p);
            String data = new File(d, "espeak-ng-data").getPath(), tokens = new File(d, "tokens.txt").getPath();
            String model = new File(d, p.model).getPath();
            int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            try {
                com.k2fsa.sherpa.onnx.OfflineTtsModelConfig.Builder m = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig.builder()
                        .setNumThreads(threads).setProvider("cpu").setDebug(false);
                if ("kokoro".equals(p.kind)) {
                    m.setKokoro(com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig.builder().setModel(model)
                            .setVoices(new File(d, "voices.bin").getPath()).setTokens(tokens).setDataDir(data).build());
                } else {
                    m.setVits(com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig.builder().setModel(model).setTokens(tokens)
                            .setDataDir(data).build());
                }
                tts = new com.k2fsa.sherpa.onnx.OfflineTts(com.k2fsa.sherpa.onnx.OfflineTtsConfig.builder()
                        .setModel(m.build()).setMaxNumSentences(1).build());
                rate = tts.getSampleRate();
                int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(Math.max(min, rate * 4 / 2)).setTransferMode(AudioTrack.MODE_STREAM).build();
                // a warm-up sentence, not played: the model's first run sets itself up, so the first real one is quick
                try {
                    tts.generate("Hello.", 0, 1f);
                } catch (Throwable ignored) {
                    // only a warm-up
                }
                failed = null;
                ready = true;
                new File(VoicePacks.root(filesDir), PROBLEM).delete();
            } catch (Throwable e) {
                // an engine that won't start says nothing in another voice; why is kept for Settings › Voice and told once
                failed = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                release();
                try {
                    java.nio.file.Files.write(new File(VoicePacks.root(filesDir), PROBLEM).toPath(),
                            (p.title + "\n" + failed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (java.io.IOException | RuntimeException ignored) {
                    // nowhere to write it
                }
            }
        }
        loading = false;
        if (ready) {
            for (Item w : waitingAhead) {
                queue.offer(w); // held while the voice loaded: made now, under the message they belong to
            }
        }
        waitingAhead.clear();
        for (Item m : waitingMarks) {
            queue.offer(m);
        }
        waitingMarks.clear();
    }

    /** Frames written since the track was last cleared, and the speech run (epoch) they belong to. */
    private long totalWritten;
    private int playedEpoch = -1;

    /** One sentence's sound, made in full, waiting to be played. */
    private static final class Clip {
        final Item item;
        final float[] samples;
        /** The first piece of its sentence (it starts the sentence) and the last (the pause after it, then done). */
        final boolean first, last;

        Clip(Item item, float[] samples, boolean first, boolean last) {
            this.item = item;
            this.samples = samples;
            this.first = first;
            this.last = last;
        }
    }

    /** Sound is being written to the speaker now. */
    private volatile boolean sounding;

    /** The first piece said after silence is at most this long, so the voice starts with the text, not after it. */
    static final int LEAD_LETTERS = 48;

    /**
     * The parts of a sentence, with the first one cut short (at a comma, else a space, after a few words) when the
     * voice is silent: a short piece is made in a fraction of the time, so speech starts at once and the rest is made
     * while it is heard.
     */
    static java.util.List<String> partsFrom(String text, boolean quick) {
        java.util.List<String> parts = parts(text);
        if (!quick || parts.isEmpty() || parts.get(0).length() <= LEAD_LETTERS + 12) {
            return parts;
        }
        String head = parts.get(0);
        int cut = -1;
        for (String mark : new String[] {", ", "; ", ": ", " - ", " "}) {
            int at = head.lastIndexOf(mark, LEAD_LETTERS);
            if (at >= 12) {
                cut = at + mark.length() - 1;
                break;
            }
        }
        if (cut < 0) {
            return parts;
        }
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add(head.substring(0, cut + 1).trim());
        out.add(head.substring(cut + 1).trim());
        out.addAll(parts.subList(1, parts.size()));
        return out;
    }

    /**
     * Made sentences waiting to play: the voice makes the next sentence while this one is heard, so playback never
     * waits for the model mid-word (on a slow device the model can make sound slower than it is spoken, and playing
     * it as it came out stuttered and broke words apart).
     */
    private final java.util.concurrent.LinkedBlockingQueue<Clip> ready2play = new java.util.concurrent.LinkedBlockingQueue<Clip>(3);
    private Thread player;

    /** Most letters the model is given at once: longer sentences are made in parts (long input made it unstable). */
    static final int MOST_LETTERS = 220;

    /** A long sentence in parts at its commas, semicolons or spaces, each at most {@link #MOST_LETTERS}. */
    static java.util.List<String> parts(String text) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String t = text == null ? "" : text.trim();
        while (t.length() > MOST_LETTERS) {
            int cut = -1;
            for (String mark : new String[] {"; ", ", ", ": ", " - ", " "}) {
                int at = t.lastIndexOf(mark, MOST_LETTERS);
                if (at > MOST_LETTERS / 3) {
                    cut = at + mark.length() - 1;
                    break;
                }
            }
            if (cut < 0) {
                cut = MOST_LETTERS;
            }
            out.add(t.substring(0, cut + 1).trim());
            t = t.substring(cut + 1).trim();
        }
        if (!t.isEmpty()) {
            out.add(t);
        }
        return out;
    }

    /**
     * Makes a sentence's sound piece by piece and hands each piece to the player as soon as it is made: the first is
     * heard while the next is being made. After silence the first piece is short, so the voice keeps up with the text.
     */
    private void makeAhead(Item it) {
        String known = madeKey(it.text, it.speaker, it.speed);
        SpeechCache kept = said();
        if (kept != null && kept.has(it.owner, known)) {
            return;
        }
        float[] already;
        synchronized (made) {
            already = made.get(known);
        }
        if (already == null && kept != null && it.owner != null) {
            already = kept.get(known); // said live a moment ago: the same sound is filed under its message
        }
        if (already != null && already.length > 0 && kept != null) {
            kept.put(it.owner, known, already);
            return;
        }
        java.util.List<float[]> pieces = new java.util.ArrayList<float[]>();
        int n = 0;
        try {
            for (String part : parts(it.text)) {
                com.k2fsa.sherpa.onnx.GeneratedAudio a = tts.generate(part, it.speaker, Math.max(0.5f, Math.min(2f, it.speed)));
                float[] got = a == null ? null : a.getSamples();
                if (got != null && got.length > 0) {
                    pieces.add(clean(got));
                    n += got.length;
                }
                Item next = queue.peek();
                if (next != null && next.epoch >= 0) {
                    queue.offer(it); // something to say now comes first; this sentence is made again right after
                    return;
                }
            }
        } catch (RuntimeException e) {
            return;
        }
        float[] all = new float[n];
        int at = 0;
        for (float[] p : pieces) {
            System.arraycopy(p, 0, all, at, p.length);
            at += p.length;
        }
        String key = madeKey(it.text, it.speaker, it.speed);
        synchronized (made) {
            made.put(key, all);
        }
        SpeechCache c = said();
        if (c != null) {
            c.put(it.owner, key, all);
        }
    }

    private void make(Item it) throws InterruptedException {
        float[] ahead;
        String key = madeKey(it.text, it.speaker, it.speed);
        synchronized (made) {
            ahead = made.remove(key);
        }
        if (ahead == null && said() != null) {
            ahead = said().get(it.owner, key); // this message's kept voice, made when it arrived
            if (ahead == null && it.owner != null) {
                ahead = said().get(key);
            }
        }
        if (ahead != null && ahead.length > 0) {
            // made ahead while the last sentence played: it starts now, with no wait after the full stop
            ensurePlayer();
            Clip c = new Clip(it, ahead, true, true);
            while (!ready2play.offer(c, 200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (it.epoch != epoch || closed) {
                    return;
                }
            }
            return;
        }
        boolean quick = ready2play.isEmpty() && !sounding;
        java.util.List<String> pieces = partsFrom(it.text, quick);
        boolean first = true;
        java.util.List<float[]> whole = new java.util.ArrayList<float[]>(); // kept, so the next Listen plays at once
        int total = 0;
        for (int i = 0; i < pieces.size(); i++) {
            if (it.epoch != epoch) {
                return; // stopped while it was being made
            }
            float[] got;
            try {
                com.k2fsa.sherpa.onnx.GeneratedAudio a = tts.generate(pieces.get(i), it.speaker,
                        Math.max(0.5f, Math.min(2f, it.speed)));
                got = a == null ? null : a.getSamples();
            } catch (RuntimeException e) {
                it.events.onError(it.id);
                return;
            }
            boolean last = i == pieces.size() - 1;
            if ((got == null || got.length == 0) && !last) {
                continue;
            }
            float[] sound = got == null ? new float[0] : clean(got);
            if (it.epoch != epoch) {
                return;
            }
            ensurePlayer();
            whole.add(sound);
            total += sound.length;
            if (last && said() != null && total > 0) {
                float[] all = new float[total];
                int at = 0;
                for (float[] w : whole) {
                    System.arraycopy(w, 0, all, at, w.length);
                    at += w.length;
                }
                said().put(it.owner, key, all);
            }
            Clip c = new Clip(it, sound, first, last);
            first = false;
            while (!ready2play.offer(c, 200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (it.epoch != epoch || closed) {
                    return;
                }
            }
        }
    }

    /** Keeps the sound within range (no clicks or crackle from samples past full scale) and fades its edges. */
    static float[] clean(float[] s) {
        int fade = Math.min(120, s.length / 4);
        for (int i = 0; i < s.length; i++) {
            float v = s[i];
            if (Float.isNaN(v)) {
                v = 0f;
            }
            v = Math.max(-0.98f, Math.min(0.98f, v));
            if (i < fade) {
                v *= i / (float) fade;
            } else if (i >= s.length - fade) {
                v *= (s.length - 1 - i) / (float) fade;
            }
            s[i] = v;
        }
        return s;
    }

    private synchronized void ensurePlayer() {
        if (player != null && player.isAlive()) {
            return;
        }
        player = new Thread(() -> {
            try {
                while (!closed) {
                    play(ready2play.take());
                }
            } catch (InterruptedException ended) {
                // shut down
            }
        }, "ibiko-voice-player");
        player.setDaemon(true);
        player.start();
    }

    private void play(Clip c) {
        Item it = c.item;
        final int mine = it.epoch;
        final AudioTrack t = track;
        if (mine != epoch || t == null) {
            return;
        }
        try {
            if (mine != playedEpoch) {
                // new speech after a stop: anything left in the track from the old speech is dropped here, on this thread
                t.pause();
                t.flush();
                totalWritten = 0;
                playedEpoch = mine;
            }
            if (c.first) {
                it.events.onStart(it.id);
            }
            t.setVolume(it.volume);
            applyParams(t); // the child's voice played higher, and the player's speed: neither makes the sound again
            t.play();
            sounding = true;
            int step = rate / 10; // a tenth of a second at a time, so a stop is heard at once
            for (int i = 0; i < c.samples.length && mine == epoch; i += step) {
                applyParams(t); // a speed picked mid-sentence is heard within a tenth of a second
                int len = Math.min(step, c.samples.length - i);
                t.write(c.samples, i, len, AudioTrack.WRITE_BLOCKING);
                totalWritten += len;
            }
            if (!c.last) {
                return; // the next piece of the same sentence follows straight on, with no pause
            }
            if (mine == epoch) {
                float[] gap = new float[(int) (rate * GAP)];
                t.write(gap, 0, gap.length, AudioTrack.WRITE_BLOCKING);
                totalWritten += gap.length;
            }
            // done when the last of it has been heard, not when it was written
            while (mine == epoch && !closed && (t.getPlaybackHeadPosition() & 0xffffffffL) < totalWritten) {
                Thread.sleep(15);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException e) {
            if (mine == epoch) {
                it.events.onError(it.id);
            }
            return;
        }
        sounding = false;
        if (mine == epoch) {
            it.events.onDone(it.id);
        }
    }

    private void release() {
        ready = false;
        if (tts != null) {
            try {
                tts.release();
            } catch (Throwable ignored) {
                // gone
            }
            tts = null;
        }
        if (track != null) {
            try {
                track.release();
            } catch (RuntimeException ignored) {
                // gone
            }
            track = null;
        }
        totalWritten = 0;
        playedEpoch = -1;
    }
}
