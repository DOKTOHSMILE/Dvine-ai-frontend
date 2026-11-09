package ai.dvine.suit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/** Each page's tools: what its persona offers, what the pilot switched on, and only those codes for its brain. */
public class ChatToolsTest {
    @Test
    public void eachPersonaOffersItsOwnTools() {
        assertTrue(ChatTools.offered("ibiko").contains(ChatTools.Tool.GITHUB));
        assertFalse(ChatTools.offered("obi").contains(ChatTools.Tool.GITHUB));
        assertTrue(ChatTools.offered("ivie").contains(ChatTools.Tool.DESIGN3D));
        assertTrue(ChatTools.offered("obi").contains(ChatTools.Tool.DEEP));
    }

    @Test
    public void onlyTheToolsSwitchedOnReachTheBrain() {
        ChatTools t = ChatTools.defaults("ibiko");
        assertFalse("deep thinking starts off", t.deep());
        assertTrue(t.allowedCodes().contains(FunctionCode.SH));
        t.set(ChatTools.Tool.SHELL, false);
        assertFalse(t.allowedCodes().contains(FunctionCode.SH));
        assertTrue("the suit's own controls stay", t.allowedCodes().contains(FunctionCode.NC));
        t.set(ChatTools.Tool.DESIGN3D, true); // not Ibiko's: stays off
        assertFalse(t.isOn(ChatTools.Tool.DESIGN3D));
        String brief = FunctionCode.instructions("", t.allowedCodes());
        assertFalse(brief.contains("§SH"));
    }

    @Test
    public void repositoriesAndChoicesAreKept() {
        ChatTools t = ChatTools.defaults("ibiko");
        t.setRepos(Arrays.asList("doktohsmile/dvine-ai-frontend", "not a repo", "doktohsmile/dvine-ai-backend"));
        t.set(ChatTools.Tool.DEEP, true);
        ChatTools back = ChatTools.read("ibiko", t.write());
        assertEquals(Arrays.asList("doktohsmile/dvine-ai-frontend", "doktohsmile/dvine-ai-backend"), back.repos());
        assertTrue(back.deep());
        assertTrue(back.brief().contains("doktohsmile/dvine-ai-backend"));
        assertTrue("a broken line gives the defaults", ChatTools.read("obi", "garbage").isOn(ChatTools.Tool.WEB));
    }
}
