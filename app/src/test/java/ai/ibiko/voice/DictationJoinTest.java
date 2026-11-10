package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** A pause keeps what was said: the next words go after it. */
public class DictationJoinTest {
    @Test
    public void wordsAfterAPauseGoAfterTheEarlierOnes() {
        String box = Dictation.join("", "hello there");
        // the stretch ended: what is in the box is kept, and the next stretch is written after it
        box = Dictation.join(box, "how are you");
        assertEquals("hello there how are you", box);
        assertEquals("typed words stay", "Note: hello", Dictation.join("Note: ", "hello"));
        assertEquals("abc", Dictation.join("abc", ""));
    }

    @Test
    public void theVoiceLevelIsZeroToOne() {
        assertEquals(0f, ListenBridge.level(-5f), 0.001f);
        assertEquals(1f, ListenBridge.level(12f), 0.001f);
        assertEquals(0.5f, ListenBridge.level(4f), 0.001f);
    }

    @Test
    public void theMostNaturalVoiceIsPickedFirst() {
        java.util.List<VoiceTuning.Candidate> all = java.util.Arrays.asList(
                new VoiceTuning.Candidate("en-ng-x-plain", "NG", false, "male", 300),
                new VoiceTuning.Candidate("en-gb-x-rich", "GB", false, "male", 500),
                new VoiceTuning.Candidate("en-us-x-net", "US", true, "male", 500));
        assertEquals("an African voice first, whatever its rating", "en-ng-x-plain", VoiceTuning.rank(all).get(0).name);
        assertEquals("natural voices on: still the African one", "en-ng-x-plain", VoiceTuning.rank(all, true).get(0).name);
        assertEquals(2, VoiceTuning.rank(all).size());
        java.util.List<VoiceTuning.Candidate> ng = java.util.Arrays.asList(
                new VoiceTuning.Candidate("en-ng-x-plain", "NG", false, "male", 300),
                new VoiceTuning.Candidate("en-ng-x-rich", "NG", false, "male", 500),
                new VoiceTuning.Candidate("en-ke-x-rich", "KE", false, "male", 500));
        assertEquals("of the same accent, the more natural one", "en-ng-x-rich", VoiceTuning.rank(ng).get(0).name);
        assertEquals("Nigerian before other African", "en-ke-x-rich", VoiceTuning.rank(ng).get(2).name);
    }
}
