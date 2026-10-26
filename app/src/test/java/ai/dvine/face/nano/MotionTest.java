package ai.dvine.face.nano;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.dvine.brain.Scenes;

public class MotionTest {
    @Test
    public void everyPoseStaysInsideTheJointLimits() {
        float[] a = new float[DotBody.CHANNELS];
        for (String pose : DotBody.POSES) {
            for (float t = 0f; t < 6f; t += 0.13f) {
                DotBody.poseAngles(pose, t, a);
                assertEquals(pose + " at " + t, 0, DotBody.limit(a));
            }
        }
    }

    @Test
    public void impossibleBendsAreClamped() {
        float[] a = new float[DotBody.CHANNELS];
        a[DotBody.SHIN_L * 3] = -40f;          // knee backwards
        a[DotBody.NECK * 3 + 1] = 170f;        // head turned round
        a[DotBody.FORE_R * 3 + 1] = -60f;      // right elbow the wrong way (mirrored sign)
        assertEquals(3, DotBody.limit(a));
        assertEquals(-5f, a[DotBody.SHIN_L * 3], 0f);
        assertEquals(75f, a[DotBody.NECK * 3 + 1], 0f);
        assertEquals(-10f, a[DotBody.FORE_R * 3 + 1], 0f);
    }

    @Test
    public void exercisesAndStuntsStayOnOrAboveTheFloor() {
        DotBody b = new DotBody(DotHead.create(false));
        float[] xyz = new float[10000 * 3];
        float[] lit = new float[10000];
        float t = 0f;
        for (String pose : DotBody.POSES) {
            b.setPose(pose);
            float low = 9f;
            for (int i = 0; i < 300; i++, t += 1 / 60f) {
                int n = b.fill(xyz, lit, 10000, t);
                if (i > 60) {
                    for (int k = 0; k < n; k++) {
                        low = Math.min(low, xyz[k * 3 + 1]);
                    }
                }
            }
            assertTrue(pose + " sinks into the floor: " + low, low > DotBody.FLOOR_OUT - 0.06f);
        }
    }

    @Test
    public void talkingAndTapsKnowTheBody() {
        DotBody b = new DotBody(DotHead.create(true));
        float[] xyz = new float[10000 * 3];
        float[] lit = new float[10000];
        b.setTalking(true);
        int n = b.fill(xyz, lit, 10000, 1f);
        assertEquals("face", b.partAt(n - 1));
        // the highest dot outside the head is never a foot, the lowest is a foot
        int low = 0;
        for (int i = 0; i < n; i++) {
            if (xyz[i * 3 + 1] < xyz[low * 3 + 1]) {
                low = i;
            }
        }
        assertTrue(b.partAt(low), b.partAt(low).endsWith("foot"));
    }

    @Test
    public void scenesPairEveryLineWithAPose() {
        Scenes.Scene s = Scenes.forRequest("act a scene", 0);
        assertNotNull(s);
        assertEquals(s.lines.length, s.poses.length);
        assertEquals(s.lines.length, s.seconds().length);
        for (String p : s.poses) {
            boolean known = false;
            for (String q : DotBody.POSES) {
                known |= q.equals(p);
            }
            assertTrue(p, known);
        }
        assertNotNull(Scenes.forRequest("let's do a morning workout", 0));
        assertNotNull(Scenes.forRequest("show me some stunts", 0));
        assertEquals(null, Scenes.forRequest("what does a tortoise eat", 0));
        assertEquals("pushups", DotBody.poseFor("do 10 push ups"));
        assertEquals("situps", DotBody.poseFor("do some sit-ups"));
        assertEquals("backflip", DotBody.poseFor("do a backflip"));
    }
}
