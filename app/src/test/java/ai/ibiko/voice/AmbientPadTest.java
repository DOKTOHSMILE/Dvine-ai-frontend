package ai.ibiko.voice;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** No audio stream is held open while nothing plays: an open stream carries the device's electrical hum to a speaker. */
public class AmbientPadTest {
    @Test
    public void offItOpensNothing() {
        AmbientPad pad = new AmbientPad();
        pad.setEnabled(false);
        pad.start();
        pad.setLevel(1f);
        assertFalse(pad.streamOpen());
        pad.stop();
    }

    @Test
    public void onButSilentItOpensNoStream() throws Exception {
        AmbientPad pad = new AmbientPad();
        pad.setEnabled(true);
        pad.start(); // the model is working, nothing is read aloud
        Thread.sleep(400);
        assertFalse("silence is no stream, not a paused one", pad.streamOpen());
        pad.stop();
    }
}
