package ai.dvine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** "Try again" asks for the message before it; it is never asked again itself (that looped forever). */
public class RetryWordsTest {
    @Test
    public void retryWords() {
        assertTrue(MainActivity.isRetry("Try again"));
        assertTrue(MainActivity.isRetry("try that again."));
        assertTrue(MainActivity.isRetry("please retry"));
        assertTrue(MainActivity.isRetry("Ask again!"));
        assertFalse(MainActivity.isRetry("Who is the current president of Nigeria"));
        assertFalse(MainActivity.isRetry("try again with a shorter answer about cars"));
    }
}
