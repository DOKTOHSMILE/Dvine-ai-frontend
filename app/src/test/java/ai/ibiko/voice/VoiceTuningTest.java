package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The parts of the voice that decide how sharp the app sounds: the defaults
 * each persona starts at, the anti-sharp {@link VoiceTuning#warm} shaping every
 * pitch passes through, the gender shift, and the spoken "higher/lower/slower"
 * commands. Plain Java, no Android.
 */
public class VoiceTuningTest {

    /** The sharpest pitch warm() can ever hand to the speech engine. */
    private static final float SHARPEST = 1.08f;

    @Test
    public void warmNeverReachesTheThinZone() {
        // anything requested above neutral is eased towards 1.08, never past it
        for (float p = 1f; p <= 3f; p += 0.05f) {
            float w = VoiceTuning.warm(p);
            assertTrue("warm(" + p + ")=" + w + " went above the sharp ceiling", w <= SHARPEST + 1e-4f);
            assertTrue("warm(" + p + ")=" + w + " must stay above neutral", w >= 1f);
        }
        // and it still goes up: a higher request still sounds higher
        assertTrue(VoiceTuning.warm(1.4f) > VoiceTuning.warm(1.1f));
    }

    @Test
    public void warmKeepsADeliberateVoiceDeep() {
        // at or below neutral the pitch passes through (bar the floor), so Obi stays deep
        assertEquals(0.80f, VoiceTuning.warm(0.80f), 1e-4f);
        assertEquals(0.85f, VoiceTuning.warm(0.85f), 1e-4f);
        // anything in the buzzy bottom is lifted to the floor
        assertEquals(0.78f, VoiceTuning.warm(0.60f), 1e-4f);
        assertEquals(0.78f, VoiceTuning.warm(0.40f), 1e-4f);
        assertEquals(0.78f, VoiceTuning.warm(0.78f), 1e-4f);
    }

    @Test
    public void defaultsAreCalmAndGendered() {
        VoiceTuning obi = VoiceTuning.defaults(false);
        VoiceTuning ivie = VoiceTuning.defaults(true);
        assertTrue("Obi must be the deeper voice", obi.pitch < ivie.pitch);
        assertTrue("both defaults must sit in the calm middle",
                VoiceTuning.warm(obi.pitch) <= 1f && VoiceTuning.warm(ivie.pitch) <= SHARPEST);
        for (VoiceTuning t : new VoiceTuning[]{obi, ivie}) {
            float warm = VoiceTuning.warm(t.pitchFor(false, "male"));
            assertTrue("default pitch past the sharp ceiling: " + warm, warm <= SHARPEST);
            assertTrue("default pitch below the buzzy floor: " + warm, warm >= 0.78f - 1e-4f);
        }
    }

    @Test
    public void genderShiftStaysInsideTheCalmMiddle() {
        VoiceTuning obi = VoiceTuning.defaults(false);
        VoiceTuning ivie = VoiceTuning.defaults(true);
        // a male voice used for Ivie is lifted, a female voice for Obi is dropped — both warmed after
        for (boolean persona : new boolean[]{false, true}) {
            for (String gender : new String[]{"male", "female", ""}) {
                VoiceTuning t = persona ? ivie : obi;
                float w = VoiceTuning.warm(t.pitchFor(persona, gender));
                assertTrue("pitch for ivie=" + persona + " gender=" + gender + " is " + w,
                        w >= 0.78f - 1e-4f && w <= SHARPEST + 1e-4f);
            }
        }
    }

    @Test
    public void aThoughtIsSofterAndSlower() {
        for (VoiceTuning t : new VoiceTuning[]{VoiceTuning.defaults(false), VoiceTuning.defaults(true)}) {
            assertTrue(t.thoughtPitch() < t.pitch);
            assertTrue(t.thoughtRate() < t.rate);
            assertTrue(t.thoughtPitch() >= 0.78f - 1e-4f);
        }
        assertEquals(VoiceTuning.THOUGHT_VOLUME, 0.6f, 1e-4f);
    }

    @Test
    public void spokenTuningCommands() {
        VoiceTuning t = VoiceTuning.defaults(false);
        float start = t.pitch;

        assertTrue(t.apply("VOICE_HIGHER", false, ranked(), ""));
        assertTrue("higher must actually be higher", t.pitch > start);
        assertTrue("a higher request must still be warm",
                VoiceTuning.warm(t.pitchFor(false, "")) <= SHARPEST);

        float high = t.pitch;
        assertTrue(t.apply("VOICE_LOWER", false, ranked(), ""));
        assertTrue(t.pitch < high);
        assertTrue("lower must not fall into the buzzy bottom", t.pitch >= 0.78f - 1e-4f);

        float rate = t.rate;
        assertTrue(t.apply("slower", false, ranked(), ""));
        assertTrue(t.rate < rate);
        float slow = t.rate;
        assertTrue(t.apply("faster", false, ranked(), ""));
        assertTrue(t.rate > slow);

        // a command that isn't a voice command changes nothing
        assertFalse(t.apply("what is the weather", false, ranked(), ""));
        assertFalse(t.apply(null, false, ranked(), ""));
    }

    /**
     * The Ibiko (robot) lift: brighter than Obi's own pitch but never into the thin, whiny
     * zone, and a touch slower rather than hurried — hurrying is what makes a voice sound
     * like a machine. TalkBridge applies both on top of the persona's pitch and rate.
     */
    @Test
    public void robotVoiceIsBrighterButNotSharpOrHurried() {
        assertTrue("Ibiko must be brighter than Obi's own pitch", VoiceTuning.ROBOT_BRIGHT > 1f);
        assertTrue("Ibiko must be calmer than his pace, not hurried", VoiceTuning.ROBOT_QUICK < 1f);

        for (boolean ivie : new boolean[]{false, true}) {
            for (String gender : new String[]{"male", "female", ""}) {
                VoiceTuning t = VoiceTuning.defaults(ivie);
                float lit = VoiceTuning.warm(t.pitchFor(ivie, gender) * VoiceTuning.ROBOT_BRIGHT);
                assertTrue("robot-lit pitch above the sharp ceiling: " + lit, lit <= SHARPEST + 1e-4f);
                assertTrue("robot-lit pitch below the buzzy floor: " + lit, lit >= 0.78f - 1e-4f);
            }
        }

        // Obi lit up is still the deeper of the two personas' default voices
        float obiLit = VoiceTuning.warm(VoiceTuning.defaults(false).pitch * VoiceTuning.ROBOT_BRIGHT);
        float iviePlain = VoiceTuning.warm(VoiceTuning.defaults(true).pitch);
        assertTrue("Ibiko's lit voice must stay deeper than Ivie's", obiLit < iviePlain);

        // a slower pace must not fall out of the usable band either
        float ibikoRate = VoiceTuning.defaults(false).rate * VoiceTuning.ROBOT_QUICK;
        assertTrue("Ibiko's pace must stay in the usable band", ibikoRate >= 0.6f && ibikoRate <= 1.8f);
    }

    /** Two voices, one of each gender, so a "VOICE …" command has something to pick. */
    private static List<VoiceTuning.Candidate> ranked() {
        return new ArrayList<>(Arrays.asList(
                new VoiceTuning.Candidate("en-us-x-male1-local", "US", false, "male"),
                new VoiceTuning.Candidate("en-us-x-fem1-local", "US", false, "female")));
    }
}