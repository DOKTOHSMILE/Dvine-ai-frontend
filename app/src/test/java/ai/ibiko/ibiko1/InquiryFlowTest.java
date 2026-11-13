package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The whole way through Ibiko 1: a question it doesn't know is looked up, it asks what the person means, that is looked
 * up too, the closer answer comes with "I may be wrong", and "no, it's …" replaces it in memory.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class InquiryFlowTest {
    /** Wikipedia knows "zorblat" as a software library; a search on the person's words finds the dance. */
    private static final Plug.Web WEB = new Plug.Web() {
        @Override
        public String get(String url) {
            if (url.contains("/page/summary/Zorblat")) {
                return "{\"type\":\"standard\",\"extract\":\"Zorblat is a software library for parsing spreadsheets."
                        + " It was released in 2019.\"}";
            }
            return null;
        }

        @Override
        public String search(String question) {
            if (!question.toLowerCase(java.util.Locale.US).contains("dance")) {
                return null;
            }
            return "Web results for " + question + "\n"
                    + "[1] Zorblat dance — https://dance.example/zorblat\n"
                    + "From [1] Zorblat dance:\n"
                    + "The zorblat is a dance from Lagos that people perform at weddings and festivals.\n";
        }
    };

    @After
    public void noWeb() {
        Plug.web = Plug.NONE;
    }

    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());

        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return false; }
        @Override public void clearRating() { }
        @Override public boolean online() { return true; }
        @Override public String sum(String message) { return null; }
        @Override public String recallTalk(String question) { return null; }
        @Override public void learnTalk(String question, String answer) { }
        @Override public void forgetTalk(String question) { }
        @Override public int talkSize() { return 0; }
        @Override public int shapes() { return 0; }
        @Override public List<String> aboutPilot() { return new ArrayList<String>(); }
        @Override public void answered(Ibiko1.How how) { }
        @Override public void note(String text) { }
        @Override public void later(Runnable r, long ms) { r.run(); }
        @Override public void onScreen(Runnable r) { r.run(); }
        @Override public android.content.Context context() { return RuntimeEnvironment.getApplication(); }

        /** The next thing said, waiting for the research thread. */
        String next(int index) throws InterruptedException {
            for (int i = 0; i < 200 && said.size() <= index; i++) {
                Thread.sleep(25);
            }
            return said.size() > index ? said.get(index) : null;
        }
    }

    @Test(timeout = 60000)
    public void itAsksBackComparesAndTakesACorrection() throws Exception {
        Plug.web = WEB;
        File dir = Files.createTempDirectory("ibiko1").toFile();
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, dir);

        brain.answer("What is a zorblat?");
        String asked = chat.next(0);
        assertNotNull("it asks back", asked);
        assertTrue(asked, asked.contains("what do you mean by “zorblat”"));

        brain.answer("I mean a dance people do at weddings in Lagos");
        String answer = chat.next(1);
        assertNotNull("it answers after comparing", answer);
        assertTrue(answer, answer.contains("The zorblat is a dance from Lagos"));
        assertTrue(answer, answer.contains("If you meant the other: Zorblat is a software library"));
        assertTrue(answer, answer.contains(Inquiry.MAY_BE_WRONG));

        brain.answer("No, it's a wedding dance from Ibadan.");
        String thanks = chat.next(2);
        assertNotNull(thanks);
        assertTrue(thanks, thanks.startsWith("Thank you for correcting me."));
        assertTrue(thanks, thanks.contains("A wedding dance from Ibadan."));
        Knowledge.Fact kept = brain.knowledge().recall("What is a zorblat?");
        assertNotNull("the correction is kept", kept);
        assertEquals("A wedding dance from Ibadan.", kept.answer);
        assertTrue(kept.sure);
    }

    @Test(timeout = 60000)
    public void justAnswerGivesTheFirstSearch() throws Exception {
        Plug.web = WEB;
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
        brain.answer("What is a zorblat?");
        assertNotNull(chat.next(0));
        brain.answer("just answer");
        String answer = chat.next(1);
        assertNotNull(answer);
        assertTrue(answer, answer.contains("Zorblat is a software library"));
        assertTrue(answer, answer.contains(Inquiry.MAY_BE_WRONG));
    }
}
