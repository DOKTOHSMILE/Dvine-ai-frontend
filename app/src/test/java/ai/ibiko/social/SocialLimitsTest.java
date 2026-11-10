package ai.ibiko.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class SocialLimitsTest {
    static final long T0 = 1_800_000_000_000L;

    @Test
    public void pacesLikeAPersonAndCapsTheHour() {
        SocialLimits l = new SocialLimits(new Random(1));
        assertEquals(4, l.cap(Platform.X, SocialLimits.Kind.POST, false));
        assertTrue(l.check("a", Platform.X, SocialLimits.Kind.POST, T0).ok);
        l.record("a", SocialLimits.Kind.POST, T0);
        SocialLimits.Verdict soon = l.check("a", Platform.X, SocialLimits.Kind.POST, T0 + 1000);
        assertFalse("a gap after each post", soon.ok);
        assertTrue(soon.why, soon.waitMs >= 10 * 60_000L - 1000 && soon.waitMs <= 16 * 60_000L);
        assertTrue(soon.why.contains("Pacing"));
        assertTrue("after the gap", l.check("a", Platform.X, SocialLimits.Kind.POST, T0 + 17 * 60_000L).ok);
        // four in the hour (each after its gap): the fifth waits for the hour to roll
        for (int i = 1; i < 4; i++) {
            l.record("a", SocialLimits.Kind.POST, T0 + i * 17 * 60_000L / 2);
        }
        SocialLimits.Verdict full = l.check("a", Platform.X, SocialLimits.Kind.POST, T0 + 58 * 60_000L);
        assertFalse(full.ok);
        assertTrue(full.why, full.why.contains("This hour's limit"));
        assertEquals(2 * 60_000L, full.waitMs);
        // another account has its own counts
        assertTrue(l.check("b", Platform.X, SocialLimits.Kind.POST, T0).ok);
    }

    @Test
    public void capsTheDay() {
        SocialLimits l = new SocialLimits(new Random(2));
        long now = T0;
        int day = l.cap(Platform.INSTAGRAM, SocialLimits.Kind.POST, true);
        for (int i = 0; i < day; i++) {
            l.record("ig", SocialLimits.Kind.POST, now);
            now += 2 * SocialLimits.HOUR;
        }
        SocialLimits.Verdict v = l.check("ig", Platform.INSTAGRAM, SocialLimits.Kind.POST, now);
        assertFalse(v.ok);
        assertEquals(-1, v.waitMs);
        assertTrue(v.why, v.why.contains("Today's limit"));
        // a day after the first, it frees up
        assertTrue(l.check("ig", Platform.INSTAGRAM, SocialLimits.Kind.POST, T0 + SocialLimits.DAY + 1).ok
                || l.used("ig", SocialLimits.Kind.POST, T0 + SocialLimits.DAY + 1) < day);
    }

    @Test
    public void carefulHalvesNeverRaises() {
        SocialLimits l = new SocialLimits();
        int normal = l.cap(Platform.REDDIT, SocialLimits.Kind.READ, true);
        l.scale(0.5f);
        assertEquals(normal / 2, l.cap(Platform.REDDIT, SocialLimits.Kind.READ, true));
        l.scale(5f);
        assertEquals(normal, l.cap(Platform.REDDIT, SocialLimits.Kind.READ, true));
    }

    @Test
    public void aWarningPausesUntilResumed() {
        SocialLimits l = new SocialLimits();
        l.pause("fb", "the site said “unusual activity”", T0 + SocialLimits.DAY);
        SocialLimits.Verdict v = l.check("fb", Platform.FACEBOOK, SocialLimits.Kind.READ, T0);
        assertFalse(v.ok);
        assertTrue(v.why.contains("paused"));
        l.resume("fb");
        assertTrue(l.check("fb", Platform.FACEBOOK, SocialLimits.Kind.READ, T0).ok);
        l.pause("fb", "x", T0 + 10);
        assertNull("a pause ends by itself", l.pausedWhy("fb", T0 + 11));
    }

    @Test
    public void keptAcrossRestarts() {
        SocialLimits l = new SocialLimits(new Random(3));
        l.record("x1", SocialLimits.Kind.LIKE, T0);
        l.record("x1", SocialLimits.Kind.LIKE, T0 + 40_000);
        l.pause("x2", "captcha", T0 + 1000);
        SocialLimits back = new SocialLimits();
        back.restore(l.save());
        assertEquals(2, back.used("x1", SocialLimits.Kind.LIKE, T0 + 50_000));
        assertEquals("captcha", back.pausedWhy("x2", T0));
        assertFalse("the pacing gap is kept too", back.check("x1", Platform.X, SocialLimits.Kind.LIKE, T0 + 41_000).ok);
        back.restore("E\tbroken\tnot-a-number\n");
    }
}
