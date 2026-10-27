package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

public class OfflineBrainTest {
    private File dir;
    private OfflineBrain brain;

    @Before
    public void setUp() throws Exception {
        dir = TestPack.tempDir();
        brain = TestPack.brain(dir);
    }

    private String say(String q) {
        return brain.respond(q, "obi").spoken();
    }

    @Test
    public void packLoads() throws Exception {
        KnowledgePack p = TestPack.load();
        assertTrue(p.intents.size() > 10);
        assertTrue(p.facts.size() > 50);
        assertTrue(p.words.size() > 40);
        assertEquals(KnowledgePack.DEFAULT_OFFLINE_LINE, p.offlineLine);
    }

    @Test
    public void basics() {
        BrainAnswer hi = brain.respond("Hello", "obi");
        assertTrue(hi.answered);
        assertEquals("PACK", hi.source);
        assertEquals("That's 84.", say("what is 12 times 7?"));
        assertTrue(say("what is the capital of Nigeria").contains("Abuja"));
        assertTrue(say("define integrity").startsWith("Integrity means"));
        assertTrue(say("what does stewardship mean").startsWith("Stewardship means"));
        assertTrue(say("100 km to miles").contains("62.14 miles"));
        assertTrue(say("30 celsius to fahrenheit").contains("86"));
        assertEquals("Today is Monday, 26 October 2026.", say("what day is today"));
        assertEquals("It's 9:30 AM.", say("what time is it"));
    }

    @Test
    public void personaPhrasing() {
        String ivie = brain.respond("who are you", "ivie").spoken();
        assertTrue(ivie.startsWith("I'm Ivie"));
        assertTrue(say("who are you").startsWith("I'm Obi"));
    }

    @Test
    public void unknownAsksForInternet() {
        BrainAnswer a = brain.respond("what was the closing price of gold yesterday", "obi");
        assertFalse(a.answered);
        assertTrue(a.needsOnline);
        assertTrue(a.spoken().startsWith("I'm unable to connect to my database"));
    }

    @Test
    public void asksWhatAStatementItDoesNotFollowMeans() {
        BrainAnswer a = brain.respond("the blue one goes sideways", "ivie");
        assertFalse(a.answered);
        assertTrue(a.listenAfter);
        assertTrue(a.spoken(), a.spoken().contains("What do you mean by"));
    }

    @Test
    public void learnsAndRecallsAcrossRestart() throws Exception {
        assertTrue(say("remember that my brother's name is Tobi").contains("Tobi"));
        OfflineBrain again = TestPack.brain(dir);
        assertEquals("My brother's name is Tobi.", again.respond("what is my brother's name?", "obi").spoken());
        again.respond("no, it's Tobenna", "obi");
        assertEquals("My brother's name is Tobenna.", again.respond("what is my brother's name", "obi").spoken());
    }

    @Test
    public void refusesToStoreSecrets() {
        assertTrue(say("remember that my bank pin is 4455").contains("won't"));
        assertTrue(say("remember that my card is 4111 1111 1111 1111").contains("won't"));
    }

    @Test
    public void consequentialIsRefused() {
        String s = say("send 50000 naira to my friend");
        assertTrue(s.contains("Nothing was sent"));
    }

    @Test
    public void sadnessGetsCare() {
        BrainAnswer a = brain.respond("my grandmother passed away", "obi");
        assertEquals("ivie", a.lines.get(1).agent);
        assertTrue(a.spoken().contains("sorry"));
    }
}
