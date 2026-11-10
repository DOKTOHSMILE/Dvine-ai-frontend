package ai.dvine.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import org.junit.Test;

/** Posts planned for later: once or repeating, read from the brain's §SP line. */
public class PostPlanTest {
    static final TimeZone LAGOS = TimeZone.getTimeZone("Africa/Lagos");

    static long at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(LAGOS);
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void theBrainsLineIsReadOnceOrRepeating() {
        long now = at(2026, 11, 9, 10, 0); // a Monday
        PostPlan p = PostPlan.parse("x 2026-11-10 09:30: Our sale starts today #deals", now, LAGOS);
        assertNotNull(p);
        assertEquals("x", p.network);
        assertEquals(at(2026, 11, 10, 9, 30), p.at);
        assertEquals(PostPlan.Repeat.ONCE, p.repeat);
        assertEquals("Our sale starts today #deals", p.text);
        assertNull(p.next(p.at, LAGOS));

        PostPlan d = PostPlan.parse("facebook 9am daily: Good morning from the shop", now, LAGOS);
        assertEquals("9am has passed: tomorrow", at(2026, 11, 10, 9, 0), d.at);
        assertEquals(at(2026, 11, 11, 9, 0), d.next(d.at, LAGOS).at);

        PostPlan h = PostPlan.parse("instagram in 2h every 6h: New drop", now, LAGOS);
        assertEquals(now + 2 * 3_600_000L, h.at);
        assertEquals(h.at + 6 * 3_600_000L, h.next(h.at, LAGOS).at);

        PostPlan w = PostPlan.parse("linkedin 2026-11-14 08:00 weekdays: Tip of the day", now, LAGOS);
        assertEquals("Saturday moves to Monday", at(2026, 11, 16, 8, 0), w.at);
        assertEquals(at(2026, 11, 17, 8, 0), w.next(w.at, LAGOS).at);

        assertNull("no time", PostPlan.parse("x: hello", now, LAGOS));
        assertNull("not a network Ibiko posts to", PostPlan.parse("myspace 9am: hi", now, LAGOS));
        assertNull("a single post needs a time", PostPlan.parse("x soon: hi", now, LAGOS));
    }

    @Test
    public void plansAreKeptAsLines() {
        PostPlan p = PostPlan.parse("x 21:15 weekly: Line one\nline two", at(2026, 11, 9, 10, 0), LAGOS);
        PostPlan back = PostPlan.readAll(PostPlan.writeAll(Collections.singletonList(p))).get(0);
        assertEquals(p.id, back.id);
        assertEquals(p.at, back.at);
        assertEquals(PostPlan.Repeat.WEEKLY, back.repeat);
        assertEquals("Line one\nline two", back.text);
        assertEquals("Every week", back.repeatWords());
    }

    @Test
    public void theBrainSchedulesByCode() {
        ai.dvine.llm.BrainOrder o = ai.dvine.llm.BrainOrder.leading("§SP x 9am daily: gm\nScheduling it.");
        assertEquals(ai.dvine.llm.BrainOrder.Kind.SCHEDULEPOST, o.kind);
        assertEquals("x 9am daily: gm", o.arg);
    }
}
