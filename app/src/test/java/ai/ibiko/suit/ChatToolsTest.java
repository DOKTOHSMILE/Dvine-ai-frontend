package ai.ibiko.suit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/** Each page's tools: what its persona offers, what the pilot switched on, and only those codes for its brain. */
public class ChatToolsTest {
    @Test
    public void eachPersonaOffersItsOwnTools() {
        // the chat is a group: every tool is on offer whoever answers
        for (String who : new String[] { "obi", "ivie", "ibiko" }) {
            assertEquals(Arrays.asList(ChatTools.Tool.values()), ChatTools.offered(who));
        }
        assertTrue(ChatTools.defaults("obi").workers());
    }

    @Test
    public void onlyTheToolsSwitchedOnReachTheBrain() {
        ChatTools t = ChatTools.defaults("ibiko");
        assertFalse("deep thinking starts off", t.deep());
        assertTrue(t.allowedCodes().contains(FunctionCode.SH));
        t.set(ChatTools.Tool.SHELL, false);
        assertFalse(t.allowedCodes().contains(FunctionCode.SH));
        assertTrue("the suit's own controls stay", t.allowedCodes().contains(FunctionCode.NC));
        assertFalse("one assistant: there is no one to hand over or switch to", t.allowedCodes().contains(FunctionCode.HO)
                || t.allowedCodes().contains(FunctionCode.SW));
        assertTrue("the codes only a brain gives: start the agents, plan the writing, think together, carry on, yes and no",
                t.allowedCodes().containsAll(Arrays.asList(FunctionCode.WK, FunctionCode.WP, FunctionCode.TT, FunctionCode.CO,
                        FunctionCode.YS, FunctionCode.CX)));
        assertTrue("pictures are on by default", t.allowedCodes().contains(FunctionCode.IM));
        t.set(ChatTools.Tool.IMAGES, false);
        assertFalse("a tool switched off is never used", t.allowedCodes().contains(FunctionCode.IM));
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
