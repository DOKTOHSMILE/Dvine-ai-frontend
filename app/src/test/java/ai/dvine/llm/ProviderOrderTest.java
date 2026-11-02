package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The switching order: a primary and up to two backups. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ProviderOrderTest {
    @Test
    public void primaryThenBackupsAtMostThree() {
        ProviderStore s = new ProviderStore(RuntimeEnvironment.getApplication());
        assertEquals(Providers.ON_PHONE, s.active());
        assertTrue(s.order().isEmpty());
        s.setActive(Providers.ANTHROPIC);
        assertTrue(s.addBackup(Providers.OPENAI));
        assertTrue(s.addBackup(Providers.GEMINI));
        assertFalse(s.addBackup(Providers.OPENROUTER)); // three is the most
        assertEquals(Arrays.asList(Providers.ANTHROPIC, Providers.OPENAI, Providers.GEMINI), s.order());
        assertEquals(2, s.place(Providers.GEMINI));
        s.setActive(Providers.GEMINI); // a backup made primary moves to the front
        assertEquals(Arrays.asList(Providers.GEMINI, Providers.ANTHROPIC, Providers.OPENAI), s.order());
        s.removeBackup(Providers.ANTHROPIC);
        assertEquals(Arrays.asList(Providers.GEMINI, Providers.OPENAI), s.order());
        s.clear(Providers.GEMINI); // forgetting the primary hands the lead on
        assertEquals(Providers.ON_PHONE, s.active());
        assertEquals(Arrays.asList(Providers.OPENAI), s.order());
        assertTrue(s.autoSwitch());
        s.setAutoSwitch(false);
        assertFalse(s.autoSwitch());
    }
}
