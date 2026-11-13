package ai.ibiko.ibiko1;

import ai.ibiko.ibiko1.formulas.Commands;
import ai.ibiko.ibiko1.formulas.Grammar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Ibiko 1 operates the app by the same function codes as every model, documented in docs/APP_FUNCTIONS.md. */
public class CommandsTest {
    @Test
    public void clearCommandsBecomeFunctionCodes() {
        assertEquals("§SR weather in Lagos", Commands.code("Search for weather in Lagos", c -> true));
        assertEquals("§NC", Commands.code("Start a new chat.", c -> true));
        assertEquals("§SW ibiko", Commands.code("switch to Ibiko", c -> true));
        assertEquals("§OS", Commands.code("Open settings", c -> true));
        assertEquals("§TM", Commands.code("What time is it?", c -> true));
        assertEquals("§NM Valentine", Commands.code("Call me Valentine", c -> true));
        assertEquals("§PH a red panda", Commands.code("Show me a photo of a red panda", c -> true));
        assertEquals("§MF pdf", Commands.code("make a PDF of that", c -> true));
        assertEquals("§SP LIST", Commands.code("show my scheduled posts", c -> true));
        assertEquals("§AC post on x: We're live", Commands.code("post on x: We're live", c -> true));
        assertEquals("§HO ibiko", Commands.code("hand this to ibiko", c -> true));
        assertEquals("§ST focus on the api", Commands.code("tell the job to focus on the api", c -> true));
        assertEquals("§SO SEARCH x: cats", Commands.code("search twitter for cats", c -> true));
        assertEquals("§SO TRENDING reddit", Commands.code("what's trending on reddit", c -> true));
        assertEquals("§SP x 9:30 daily: Good morning", Commands.code("schedule a post on x 9:30 daily: Good morning", c -> true));
        assertEquals("§TL github.repos", Commands.code("list my github repos", c -> true));
        assertEquals("§TL github.issues DOKTOHSMILE/Ibiko-ai", Commands.code("list github issues for DOKTOHSMILE/Ibiko-ai", c -> true));
        assertEquals("§SI social.accounts.list", Commands.code("list my social accounts", c -> true));
        assertNull("a question is answered, not carried out", Commands.code("What is a new chat?", c -> true));
        assertNull("a tool that is off can't be used", Commands.code("search for cats", c -> !c.equals("SR")));
        assertEquals("ibiko://ibiko1/v1/chat", Endpoint.ADDRESS);
    }

    @Test
    public void theFunctionsFileMatchesTheCode() throws Exception {
        // the generated text is always left in build/, to copy over docs/APP_FUNCTIONS.md when a function changes
        new File("build").mkdirs();
        Files.write(new File("build/APP_FUNCTIONS.md").toPath(), ai.ibiko.suit.FunctionCode.document().getBytes(StandardCharsets.UTF_8));
        File f = new File("../docs/APP_FUNCTIONS.md");
        assertEquals("docs/APP_FUNCTIONS.md is generated: copy app/build/APP_FUNCTIONS.md over it",
                ai.ibiko.suit.FunctionCode.document(), new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        String sheet = ai.ibiko.suit.FunctionCode.directions();
        for (ai.ibiko.suit.FunctionCode c : ai.ibiko.suit.FunctionCode.values()) {
            assertTrue(c.name(), sheet.contains(c.code()));
        }
        assertTrue(sheet.contains("/workspace/repos/"));
        assertTrue(sheet.contains("Never put a token"));
    }
}
