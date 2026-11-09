package ai.dvine.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** DVINE 1 operates the app by the same function codes as every model, documented in docs/APP_FUNCTIONS.md. */
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
        assertNull("a question is answered, not carried out", Commands.code("What is a new chat?", c -> true));
        assertNull("a tool that is off can't be used", Commands.code("search for cats", c -> !c.equals("SR")));
        assertEquals("dvine://dvine1/v1/chat", Endpoint.ADDRESS);
    }

    @Test
    public void theFunctionsFileMatchesTheCode() throws Exception {
        // the generated text is always left in build/, to copy over docs/APP_FUNCTIONS.md when a function changes
        new File("build").mkdirs();
        Files.write(new File("build/APP_FUNCTIONS.md").toPath(), ai.dvine.suit.FunctionCode.document().getBytes(StandardCharsets.UTF_8));
        File f = new File("../docs/APP_FUNCTIONS.md");
        assertEquals("docs/APP_FUNCTIONS.md is generated: copy app/build/APP_FUNCTIONS.md over it",
                ai.dvine.suit.FunctionCode.document(), new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }
}
