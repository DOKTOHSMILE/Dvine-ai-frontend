package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.dvine.voice.VoiceTuning;

public class VoiceTest {
    private static List<VoiceTuning.Candidate> voices() {
        return VoiceTuning.rank(Arrays.asList(
                new VoiceTuning.Candidate("en-us-x-a", "US", false, ""),
                new VoiceTuning.Candidate("en-ng-x-b", "NG", false, ""),
                new VoiceTuning.Candidate("en-ng-x-c", "NG", false, ""),
                new VoiceTuning.Candidate("en-ng-x-net", "NG", true, ""),
                new VoiceTuning.Candidate("en-gb-x-d", "GB", false, "")));
    }

    @Test
    public void nigerianVoicesFirstAndDifferentPerPersona() {
        List<VoiceTuning.Candidate> r = voices();
        assertEquals("NG", r.get(0).country);
        assertTrue(r.stream().noneMatch(c -> c.network));
        String[] picks = VoiceTuning.pick(r);
        assertTrue(picks[0].startsWith("en-ng"));
        assertTrue(picks[1].startsWith("en-ng"));
        assertNotEquals(picks[0], picks[1]);
    }

    @Test
    public void genderHintsWinWhenPresent() {
        String[] picks = VoiceTuning.pick(VoiceTuning.rank(Arrays.asList(
                new VoiceTuning.Candidate("en-ng-1", "NG", false, "female"),
                new VoiceTuning.Candidate("en-ng-2", "NG", false, "male"))));
        assertEquals("en-ng-2", picks[0]);
        assertEquals("en-ng-1", picks[1]);
    }

    @Test
    public void tuningCommandsAndThoughtVoice() {
        VoiceTuning t = VoiceTuning.defaults(false);
        float rate = t.rate;
        t.apply("VOICE_SLOWER", false, voices(), null);
        assertTrue(t.rate < rate);
        assertTrue(t.thoughtRate() < t.rate && t.thoughtPitch() < t.pitch);
        t.voice = "en-ng-x-b";
        t.apply("VOICE_NEXT", false, voices(), "en-ng-x-c");
        assertNotEquals("en-ng-x-c", t.voice);
        assertNotEquals("en-ng-x-b", t.voice);
        VoiceTuning back = VoiceTuning.parse(t.serialize(), false);
        assertEquals(t.voice, back.voice);
        assertEquals(t.rate, back.rate, 0.01f);
        assertTrue(VoiceTuning.THOUGHT_VOLUME > 0.5f && VoiceTuning.THOUGHT_VOLUME < 1f);
    }

    @Test
    public void brainRoutesVoiceCommands() throws Exception {
        OfflineBrain b = TestPack.brain(TestPack.tempDir());
        BrainAnswer a = b.respond("Ivie, try a different voice", "obi");
        assertEquals("VOICE_NEXT", a.command);
        assertTrue(a.lookIvie);
        assertEquals("VOICE_SLOWER", b.respond("speak slower", "obi").command);
        assertEquals("VOICE_LOWER", b.respond("deeper voice", "obi").command);
        assertEquals(null, b.respond("the economy is slower this year", "obi").command);
    }
}
