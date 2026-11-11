package ai.ibiko.main;

/**
 * Reading a reply aloud, sentence by sentence, and the drawer setting that decides whether replies are spoken.
 *
 * <p>Two jobs live here. First the setting: "always" (the default), "mic" (only after a dictated message) or
 * "off", read from the drawer's read-aloud choice, and whether a given reply therefore gets spoken as it is
 * written. Second the Listen button on a reply: it reads the reply sentence by sentence; tapped while reading,
 * it pauses (the phone's voice can't stop mid-word and carry on, so it stops and remembers the sentence);
 * tapped again, it carries on from that sentence. A new message, or Stop on the reading notification, ends
 * reading an old reply aloud.
 *
 * <p>The screen side lives in {@link ai.ibiko.MainActivity}: it implements {@link Host}, hands over the
 * {@link ai.ibiko.voice.TalkBridge}, the current persona, the prefs, and clears the Listen state on screen
 * when reading stops.
 */
public final class ReadAloud {

    /** What the reading needs from the screen it serves. */
    public interface Host {
        /** The text-to-speech bridge, or null when it isn't there. */
        ai.ibiko.voice.TalkBridge talk();

        /** The persona now on stage, whose voice the sentences are read in. */
        String agent();

        /** The drawer prefs, holding the read-aloud choice. */
        android.content.SharedPreferences prefs();

        /** The message came from the microphone: its reply is spoken too. */
        boolean voiceTurn();

        /** Reading stopped on screen: the Listen button goes back to its resting state. */
        void readingStopped();
    }

    private final Host host;

    public ReadAloud(Host host) {
        this.host = host;
    }

    /** The reply being read aloud by its Listen button, its sentences, and the one it is on. */
    private String readingText;
    private java.util.List<String> readingSentences = new java.util.ArrayList<String>();
    private int readingAt;
    private boolean readingOn;

    /** "always" (the default), "mic" (only after the microphone) or "off". */
    public String setting() {
        android.content.SharedPreferences prefs = host.prefs();
        return prefs == null ? "always" : prefs.getString("read_aloud", "always");
    }

    /** Whether this reply is read out: always, after a dictated message, or never, as chosen in the drawer. */
    public boolean speakReply() {
        String mode = setting();
        return "always".equals(mode) || ("mic".equals(mode) && host.voiceTurn());
    }

    /**
     * Listen: reads the reply sentence by sentence; tapped while reading, it pauses (the phone's voice can't stop
     * mid-word and carry on, so it stops and remembers the sentence); tapped again, it carries on from that
     * sentence. True when it is reading now.
     */
    public boolean toggleReading(String text) {
        return toggleReading(text, host.agent());
    }

    /** As {@link #toggleReading(String)}, in {@code agent}'s own voice: the one who wrote the reply reads it. */
    public boolean toggleReading(String text, String agent) {
        ai.ibiko.voice.TalkBridge talk = host.talk();
        if (talk == null || agent == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        if (readingOn && text.equals(readingText)) {
            readingOn = false;
            talk.stop();
            return false;
        }
        if (!text.equals(readingText)) {
            readingText = text;
            readingSentences = ai.ibiko.voice.SentenceStream.split(text);
            readingAt = 0;
        }
        if (readingAt >= readingSentences.size()) {
            readingAt = 0; // finished last time: from the start
        }
        readingOn = true;
        readingAs = agent;
        readNext(agent);
        return true;
    }

    private void readNext(final String persona) {
        ai.ibiko.voice.TalkBridge talk = host.talk();
        if (!readingOn || talk == null) {
            return;
        }
        if (readingAt >= readingSentences.size()) {
            readingOn = false;
            readingAt = 0;
            host.readingStopped();
            return;
        }
        talk.setRobot("ibiko".equals(persona)); // Ibiko's own voice, not Obi's
        talk.speak("ivie".equals(persona), readingSentences.get(readingAt), () -> {
            if (!readingOn) {
                return;
            }
            readingAt++;
            readNext(persona);
        });
    }

    /** The persona whose reply is being read (to carry on in the same voice after a skip). */
    private String readingAs;

    /** A reply is being read by its Listen button (back and forward work on it). */
    public boolean reading() {
        return readingOn;
    }

    /**
     * Back (negative) or forward by about {@code seconds}: whole sentences, counting about 15 letters a second at
     * the speed it reads, then it carries on from there. False when nothing is being read by Listen.
     */
    public boolean skip(int seconds, float speed) {
        if (!readingOn || readingSentences.isEmpty()) {
            return false;
        }
        readingAt = skipTo(readingSentences, readingAt, seconds, speed);
        readNext(readingAs);
        return true;
    }

    /** Where a skip lands: the sentence about {@code seconds} away (at 15 letters a second, times the speed). */
    static int skipTo(java.util.List<String> sentences, int at, int seconds, float speed) {
        double letters = Math.abs(seconds) * 15.0 * Math.max(0.5f, speed);
        int i = Math.max(0, Math.min(at, sentences.size() - 1));
        double n = 0;
        if (seconds < 0) {
            while (i > 0 && n < letters) {
                i--;
                n += sentences.get(i).length();
            }
            return i;
        }
        while (i < sentences.size() - 1 && n < letters) {
            n += sentences.get(i).length();
            i++;
        }
        return i;
    }

    /**
     * Reading ends: a new message starts, or Stop is tapped on the reading notification. Stops the voice and
     * says so on screen. True when it was reading.
     */
    public boolean stop() {
        if (!readingOn) {
            return false;
        }
        readingOn = false;
        host.readingStopped();
        return true;
    }
}