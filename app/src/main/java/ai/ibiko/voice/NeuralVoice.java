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
        queue.offer(new Item(events, id, text, speaker, speed, volume, epoch));
    }

    /** Stops at once: what is playing and everything waiting. */
    public void stop() {
        epoch++;
        queue.removeIf(i -> i.epoch >= 0);
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
                say(it);
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
        if (p == null || !VoicePacks.installed(filesDir, p)) {
            loading = false;
            return;
        }
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
        } finally {
            loading = false;
        }
    }

    /** Frames written since the track was last cleared, and the speech run (epoch) they belong to. */
    private long totalWritten;
    private int playedEpoch = -1;

    private void say(Item it) {
        final int mine = it.epoch;
        final AudioTrack t = track;
        try {
            if (mine != playedEpoch) {
                // new speech after a stop: anything left in the track from the old speech is dropped here, on this thread
                t.pause();
                t.flush();
                totalWritten = 0;
                playedEpoch = mine;
            }
            it.events.onStart(it.id);
            t.setVolume(it.volume);
            t.play();
            tts.generateWithCallback(it.text, it.speaker, Math.max(0.5f, Math.min(2f, it.speed)), samples -> {
                if (mine != epoch) {
                    return 0; // stopped: nothing more is made
                }
                t.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
                totalWritten += samples.length;
                return mine == epoch ? 1 : 0;
            });
            if (mine == epoch) {
                float[] gap = new float[(int) (rate * GAP)];
                t.write(gap, 0, gap.length, AudioTrack.WRITE_BLOCKING);
                totalWritten += gap.length;
            }
            // done when the last of it has been heard, not when it was made
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
