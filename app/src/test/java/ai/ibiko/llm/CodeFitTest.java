package ai.ibiko.llm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** From the pilot's screenshot: a small model on the device picked actions nobody asked for. They are dropped now. */
public class CodeFitTest {
    private static BrainOrder code(String line) {
        return BrainOrder.leading(line + "\nok");
    }

    @Test
    public void actionsTheWordsDidNotAskForAreDropped() {
        assertFalse("plan mode for a greeting", CodeFit.fits(code("§PM"), "Hello"));
        assertFalse("a share for a question", CodeFit.fits(code("§AC share: public class JavaCode {}"), "Can you write Java codes?"));
        assertFalse("automatic commands for a question", CodeFit.fits(code("§AO"), "How are you?"));
        assertFalse("a picture for a web page", CodeFit.fits(code("§IM a sales page"), "Design for me a test website page for sales"));
        assertFalse("the question heard back is not a yes", CodeFit.fits(code("§YS"), "Say yes or no"));
    }

    @Test
    public void actionsTheWordsAskForStillRun() {
        assertTrue(CodeFit.fits(code("§AC share: hello"), "Share this on WhatsApp"));
        assertTrue(CodeFit.fits(code("§WK"), "Design for me a test website page for sales"));
        assertTrue(CodeFit.fits(code("§IM a cat"), "Draw a cat"));
        assertTrue(CodeFit.fits(code("§PM"), "Turn plan mode on"));
        assertTrue(CodeFit.fits(code("§YS"), "yes"));
        assertTrue("a look-up is always fine", CodeFit.fits(code("§SR weather Lagos"), "Hello"));
        assertTrue(CodeFit.fits(null, "anything"));
    }
}
