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
}
