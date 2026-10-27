package ai.dvine.human;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.dvine.brain.BrainAnswer;
import ai.dvine.brain.OfflineBrain;
import ai.dvine.human.scene.DualScene;

/** Both personas in one world: they walk in (no teleport), stop at the right distance, face each other and never overlap. */
public class DualSceneTest {
    private static float run(DualScene s, float seconds, float[] closest) {
        float t = 0f;
        for (; t < seconds && !s.settled(); t += 1 / 30f) {
            s.step(1 / 30f);
            if (s.actor[0].present && s.actor[1].present) {
                closest[0] = Math.min(closest[0], s.gap());
            }
            for (DualScene.Actor a : s.actor) {
                // no jump bigger than one walking step
                assertTrue(Math.abs(a.speed) <= DualScene.SPEED + 1e-3f);
            }
        }
        return t;
    }

    @Test
    public void theSecondWalksInAndTheyStandSideBySide() {
        DualScene s = new DualScene();
        s.setFocus(false);
        assertTrue(s.onStage(false));
        assertFalse("Ivie is away in the single view", s.onStage(true));
        assertTrue(s.arrange(DualScene.SIDE));
        assertTrue(s.onStage(true));
        assertTrue("Ivie starts off screen", Math.abs(s.actor[1].x) >= DualScene.ENTRANCE_X - 0.01f);
        s.step(1 / 30f);
        assertTrue("she walks", s.actor[1].walking);
        float[] closest = {Float.MAX_VALUE};
        float took = run(s, 20f, closest);
        assertTrue("arrives in a few seconds, not instantly: " + took, took > 2f && took < 12f);
        assertTrue(s.settled());
        assertEquals(DualScene.SIDE_GAP, s.gap(), 0.06f);
        assertEquals(0f, s.actor[0].yaw, 1f);
        assertEquals(0f, s.actor[1].yaw, 1f);
        assertTrue("never closer than the minimum gap", closest[0] >= DualScene.MIN_GAP - 1e-3f);
    }

    @Test
    public void theyWalkToEachOtherAndTurnFaceToFace() {
        DualScene s = new DualScene();
        s.setFocus(true);
        s.arrange(DualScene.MEET);
        float[] closest = {Float.MAX_VALUE};
        run(s, 20f, closest);
        assertTrue(s.settled());
        assertEquals(DualScene.TALK_GAP, s.gap(), 0.06f);
        assertTrue("Obi on the left turned toward Ivie", s.actor[0].x < s.actor[1].x && s.actor[0].yaw > 45f);
        assertTrue("Ivie turned toward Obi", s.actor[1].yaw < -45f);
        assertTrue(closest[0] >= DualScene.MIN_GAP - 1e-3f);
    }

    @Test
    public void theyWalkTogetherAndEndSideBySide() {
        DualScene s = new DualScene();
        s.arrange(DualScene.WALK);
        float[] closest = {Float.MAX_VALUE};
        float minX = 0f, maxX = 0f;
        for (float t = 0f; t < 40f && !s.settled(); t += 1 / 30f) {
            s.step(1 / 30f);
            minX = Math.min(minX, s.actor[0].x);
            maxX = Math.max(maxX, s.actor[1].x);
            if (s.actor[0].present && s.actor[1].present) {
                closest[0] = Math.min(closest[0], s.gap());
            }
        }
        assertTrue("finished", s.settled());
        assertEquals(DualScene.SIDE, s.arrangement());
        assertTrue("they crossed the stage", minX < -3f && maxX > 3f);
        assertEquals(DualScene.SIDE_GAP, s.gap(), 0.06f);
        assertTrue(closest[0] >= DualScene.MIN_GAP - 1e-3f);
        // the camera frames where they are going
        float[] span = s.span();
        assertEquals(0f, span[0], 0.05f);
    }

    @Test
    public void everydayPhrasesArrangeThem() {
        OfflineBrain brain = ai.dvine.brain.TestPack.brain();
        String[][] cases = {
                {"Can both of them walk and stand side by side?", "walk"},
                {"walk together", "walk"},
                {"stand side by side", "side"},
                {"walk to each other", "meet"},
                {"Obi, face each other", "meet"},
                {"just one of you", "single"},
        };
        for (String[] c : cases) {
            BrainAnswer a = brain.respond(c[0], "obi");
            assertEquals(c[0], "SCENE", a.command);
            assertEquals(c[0], c[1], a.commandValue);
        }
        // a single persona's walk is still a pose
        BrainAnswer walk = brain.respond("can you walk", "obi");
        assertEquals("POSE", walk.command);
    }
}
