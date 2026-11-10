package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SquadTest {
    @Test
    public void thePartsOfACall() {
        List<String> p = Squad.parts("<part>Write app/login.py</part>\n<part>\nWrite tests/test_login.py\n</part>");
        assertEquals(2, p.size());
        assertEquals("Write tests/test_login.py", p.get(1));
        assertEquals("a list is read a line a part", 3, Squad.parts("1. a\n2. b\n- c").size());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            many.append("<part>p").append(i).append("</part>");
        }
        assertEquals(Squad.MOST, Squad.parts(many.toString()).size());
    }

    @Test
    public void workersRunAtTheSameTimeAndReportInOrder() {
        final CountDownLatch together = new CountDownLatch(3);
        final AtomicInteger met = new AtomicInteger();
        String out = Squad.run(java.util.Arrays.asList("one", "two", "three"), (n, part) -> {
            together.countDown();
            try {
                if (together.await(5, TimeUnit.SECONDS)) {
                    met.incrementAndGet(); // all three were at work at once
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "did " + part;
        }, 3, () -> false);
        assertEquals(3, met.get());
        assertTrue(out.indexOf("did one") < out.indexOf("did two") && out.indexOf("did two") < out.indexOf("did three"));
        assertTrue(out.contains("3 workers finished"));
    }

    @Test
    public void onePartIsNotASquadAndAFailedWorkerIsSaid() {
        assertTrue(Squad.run(java.util.Arrays.asList("only"), (n, p) -> "x", 2, null).startsWith("Error"));
        String out = Squad.run(java.util.Arrays.asList("a", "b"), (n, p) -> {
            if (n == 2) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        }, 1, null);
        assertTrue(out.contains("ok") && out.contains("did not finish"));
    }

    @Test
    public void aWorkerKnowsItsPartAndThatItIsOneOfSeveral() {
        String b = Squad.brief(2, 3, "Write the tests", "Build a login page");
        assertTrue(b.contains("worker 2 of 3") && b.contains("Write the tests") && b.contains("Build a login page"));
    }

    @Test
    public void eachPartHasItsAgentAndItsTime() {
        List<Squad.Part> p = Squad.assignments("<part role=\"UCIX\" minutes=\"10\">Audit res/layout line by line</part>"
                + "<part role='ucux'>Trace every button to its handler</part><part role=\"ARCH\" minutes=\"999\">x</part><part>plain</part>");
        assertEquals(4, p.size());
        assertEquals(Org.UCIX, p.get(0).role);
        assertEquals(10, p.get(0).minutes);
        assertEquals(Org.UCUX, p.get(1).role);
        assertEquals("no time named: the usual one", Squad.MINUTES, p.get(1).minutes);
        assertEquals("the lead's own role is not a worker", null, p.get(2).role);
        assertEquals("never more than the most a part may take", Squad.MOST_MINUTES, p.get(2).minutes);
        assertEquals(null, p.get(3).role);
        assertEquals("the old form still reads", 2, Squad.parts("<part role=\"QATX\">a</part><part>b</part>").size());
    }

    @Test
    public void theOrganisationHasItsRolesAndTheLeadKnowsThem() {
        for (String n : new String[] { "ARCH", "CPGC", "ROLX", "SCRB", "UCIX", "UCUX", "CORX", "DATX", "QATX", "SECX", "REVX", "RSRX", "DOCX", "RELX" }) {
            assertTrue(n, Org.of(n) != null);
        }
        assertTrue(!Org.ARCH.worker() && !Org.ROLX.worker() && Org.UCIX.worker());
        assertTrue(Org.lead().contains("UCIX") && Org.lead().contains("ROLX"));
        assertTrue(Org.UCUX.brief().contains("opens the wrong screen"));
        assertEquals(Org.UCIX, Org.of("interface"));
    }

    @Test
    public void aFailedOrLatePartIsSentBack() {
        String out = Squad.run(java.util.Arrays.asList("a", "b"), (n, p) -> n == 1 ? "Time is up for this part" : "done", 2, null);
        assertTrue(out.contains("CPGC") && out.contains("send that part back"));
        assertTrue(!Squad.run(java.util.Arrays.asList("a", "b"), (n, p) -> "done", 2, null).contains("CPGC"));
    }
}
