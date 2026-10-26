package ai.dvine.voice;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * A soft, slow, low instrumental pad, synthesised on the phone (no audio
 * files). It swells gently while Obi or Ivie speak and fades in silence.
 * Obi's chord is lower and darker; Ivie's is warmer and brighter.
 */
public final class AmbientPad implements Runnable {
    private static final int RATE = 22050;
    // A minor add9 for Obi, C major 9 for Ivie, both voiced low and wide
    private static final double[] OBI = {55.0, 82.41, 110.0, 130.81, 164.81, 246.94};
    private static final double[] IVIE = {65.41, 98.0, 130.81, 164.81, 196.0, 293.66};

    private volatile boolean running;
    private volatile boolean enabled = true;
    private volatile boolean ivie;
    private volatile float target;
    private Thread thread;
    private AudioTrack track;

    public void setEnabled(boolean on) {
        enabled = on;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setPersona(boolean ivie) {
        this.ivie = ivie;
    }

    /** 0 silent .. 1 full (which is still very quiet). */
    public void setLevel(float level) {
        target = Math.max(0f, Math.min(1f, level));
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            min = RATE * 4; // one second of stereo 16-bit audio
        }
        try {
            open(min);
        } catch (RuntimeException refused) {
            // the music is a nicety: if the phone refuses audio, carry on silently
            track = null;
            running = false;
        }
    }

    private void open(int min) {
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(Math.max(min, RATE * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        running = true;
        track.play();
        thread = new Thread(this, "dvine-ambient");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
                // already stopped
            }
            track.release();
            track = null;
        }
    }

    @Override
    public void run() {
        short[] buf = new short[1024 * 2];
        double[] phase = new double[6];
        double t = 0;
        float level = 0f;
        float mixIvie = ivie ? 1f : 0f;
        AudioTrack out = track;
        while (running && out != null) {
            float goal = enabled ? target : 0f;
            for (int i = 0; i < buf.length; i += 2) {
                level += (goal - level) * 0.00004f;
                mixIvie += ((ivie ? 1f : 0f) - mixIvie) * 0.00002f;
                double left = 0;
                double right = 0;
                for (int k = 0; k < 6; k++) {
                    double f = OBI[k] + (IVIE[k] - OBI[k]) * mixIvie;
                    phase[k] += 2 * Math.PI * f / RATE;
                    if (phase[k] > Math.PI * 2) {
                        phase[k] -= Math.PI * 2;
                    }
                    // each voice breathes on its own slow cycle
                    double swell = 0.55 + 0.45 * Math.sin(t * (0.07 + k * 0.013) + k * 1.7);
                    double tone = Math.sin(phase[k]) + 0.18 * Math.sin(phase[k] * 2.0) * (k < 3 ? 1 : 0.3);
                    double v = tone * swell / (1.0 + k * 0.35);
                    double pan = (k % 2 == 0) ? 0.62 : 0.38;
                    left += v * pan;
                    right += v * (1 - pan);
                }
                double amp = level * 900.0; // far under the voice
                buf[i] = (short) (left * amp);
                buf[i + 1] = (short) (right * amp);
                t += 1.0 / RATE;
            }
            try {
                if (out.write(buf, 0, buf.length) < 0) {
                    return;
                }
            } catch (RuntimeException released) {
                return;
            }
        }
    }
}
