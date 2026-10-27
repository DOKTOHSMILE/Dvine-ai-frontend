package ai.dvine.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The movement engine: every pose and move stays inside the joint limits; impossible bends are clamped; words name moves. */
public class MotionTest {
    @Test
    public void everyPoseStaysInsideTheJointLimits() {
        float[] a = new float[Motions.CHANNELS];
        for (String pose : Motions.POSES) {
            for (float t = 0f; t < 6f; t += 0.37f) {
                Motions.poseAngles(pose, t, a);
                assertEquals(pose + " at " + t, 0, Motions.limit(a));
            }
        }
    }

    @Test
    public void impossibleBendsAreClamped() {
        float[] a = new float[Motions.CHANNELS];
        a[Motions.SHIN_L * 3] = -40f;          // knee backwards
        a[Motions.NECK * 3 + 1] = 170f;        // head turned round
        a[Motions.FORE_R * 3 + 1] = -60f;      // right elbow the wrong way (mirrored sign)
        assertEquals(3, Motions.limit(a));
        assertTrue("the knee no longer bends backwards", a[Motions.SHIN_L * 3] > -10f);
        assertEquals(75f, a[Motions.NECK * 3 + 1], 0f);
        assertEquals("all inside the limits now", 0, Motions.limit(a));
    }

    @Test
    public void wordsNameTheMoves() {
        assertEquals("pushups", Motions.poseFor("do 10 push ups"));
        assertEquals("situps", Motions.poseFor("do some sit-ups"));
        assertEquals("backflip", Motions.poseFor("do a backflip"));
        assertEquals("walk", ai.dvine.brain.OfflineBrainAccess.pose("make it walk"));
        assertEquals("dance", ai.dvine.brain.OfflineBrainAccess.pose("can it dance"));
        assertEquals(null, ai.dvine.brain.OfflineBrainAccess.pose("how do I run a business"));
    }
}
