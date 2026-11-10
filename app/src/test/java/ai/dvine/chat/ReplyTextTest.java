package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class ReplyTextTest {
    @Test
    public void paragraphsStayAndToolTagsGo() {
        String raw = "Nothing happened next.\n\nWhere things stand:\n1. **Give me the repo**\n"
                + "2. Paste the script\n\nI'll clone it.<tool_call><function=git action=\"clone\" "
                + "repo=\"DOKTOHSMILE/Dvine-ai-frontend.git\"/></function></tool_call>";
        String out = ReplyText.prepare(raw);
        assertEquals("Nothing happened next.\n\nWhere things stand:\n1. **Give me the repo**\n2. Paste the script\n\nI'll clone it.", out);
        assertFalse(out.contains("tool_call"));
        assertFalse(out.contains("function="));
    }

    @Test
    public void deepSeekToolMarkupIsNotShown() {
        String raw = "Looking now.\n<|DSML|calls>\n<|DSML|invoke name=\"read_file\" path=\"memory/projects/PROJECT_PLAN.md\"/>\n"
                + "</|DSML|calls>\nOpenRouter didn't answer (the account is out of credit (402)).";
        String out = ReplyText.prepare(raw);
        assertFalse(out.contains("DSML"));
        assertFalse(out.contains("read_file"));
        assertEquals("Looking now.\n\nOpenRouter didn't answer (the account is out of credit (402)).", out);
    }

    @org.junit.Test
    public void aGatewaysOwnTagsAreNotTheReply() {
        org.junit.Assert.assertEquals("", ReplyText.prepare("<severity>0</severity>").trim());
        org.junit.Assert.assertEquals("Good morning.", ReplyText.prepare("<severity>0</severity>\nGood morning.").trim());
    }
}
