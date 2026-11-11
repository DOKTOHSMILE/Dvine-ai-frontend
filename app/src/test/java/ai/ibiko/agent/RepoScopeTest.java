package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/** One repository ticked in Tools › GitHub: the agent lists, clones and reads only that one. */
public class RepoScopeTest {
    @Test
    public void onlyTheChosenRepository() {
        RepoScope s = RepoScope.of(Arrays.asList("DOKTOHSMILE/Ibiko"), "Audit the app and fix the crash");
        assertTrue(s.limited());
        assertTrue(s.allows("https://github.com/DOKTOHSMILE/Ibiko.git"));
        assertTrue(s.allows("doktohsmile/ibiko"));
        assertTrue(s.allows("Ibiko")); // a bare name of a chosen one
        assertFalse(s.allows("DOKTOHSMILE/diane-dapp"));
        assertFalse(s.allows("ibiko-ai/ibiko")); // the same name under another owner is another repository
        assertTrue(s.refusal("ibiko-ai/ibiko").contains("DOKTOHSMILE/Ibiko".toLowerCase()));
        // the account's whole list is answered with the chosen one only
        String list = s.check("github.repos 1");
        assertTrue(list, list.contains("doktohsmile/ibiko") && !list.contains("diane"));
        assertNull(s.check("github.issues DOKTOHSMILE/Ibiko"));
        assertTrue(s.check("github.issues DOKTOHSMILE/IBK-Wallet").startsWith("Not done"));
        assertNull(s.check("web.search kotlin")); // not GitHub: not limited
        // a shell clone of another repository is refused too
        assertTrue(s.checkCommand("cd repos && git clone https://github.com/ibiko-ai/ibiko-ai.git").startsWith("Not done"));
        assertNull(s.checkCommand("git clone --depth 1 https://github.com/DOKTOHSMILE/Ibiko.git repos/Ibiko"));
        assertNull(s.checkCommand("ls repos"));
    }

    @Test
    public void aRepositoryNamedInTheMessageIsAllowedAndNoChoiceMeansNoLimit() {
        RepoScope s = RepoScope.of(Arrays.asList("me/app"), "compare me/app with https://github.com/me/lib please");
        assertEquals(Arrays.asList("me/app", "me/lib"), s.repos());
        assertTrue(s.allows("me/lib"));
        RepoScope open = RepoScope.of(Collections.<String>emptyList(), "clone everything");
        assertFalse(open.limited());
        assertTrue(open.allows("anyone/anything"));
        assertNull(open.check("github.repos 1"));
        assertEquals("", open.brief());
    }
}
