package ai.ibiko.agent;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import ai.ibiko.llm.FakeTurn;

/** A file is read by line, and a "User:" inside code is not a new prompt. */
public class SourceReadTest {
    @Test
    public void readsARangeAndSaysWhenTheFileGoesOn() throws Exception {
        File dir = Files.createTempDirectory("ibiko-src").toFile();
        File f = new File(dir, "A.java");
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            body.append("line ").append(i).append(" of the file\n");
        }
        Files.write(f.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));
        String part = SourceRead.read(f, "10", "12");
        assertTrue(part.contains("Lines 10–12 of"));
        assertTrue(part.contains("10\tline 10 of the file"));
        String all = SourceRead.read(f, "", "");
        assertTrue(all.contains("1\tline 1 of the file"));
        assertTrue(all.contains("50\tline 50 of the file"));
        File nest = new File(dir, "pkg");
        nest.mkdirs();
        Files.write(new File(nest, "B.java").toPath(), "int stop = 1;\nvoid login() {}\n".getBytes(StandardCharsets.UTF_8));
        String found = SourceRead.search(dir, "void login", "");
        assertTrue(found.contains("pkg/B.java:2:") || found.contains("pkg\\B.java:2:"));
        String named = SourceRead.search(dir, "name:B.java", "");
        assertTrue(named.contains("pkg/B.java") || named.contains("pkg\\B.java"));
        assertTrue(!named.contains("void login"));
    }

    @Test
    public void aUserLineInsideCodeIsNotANewPrompt() {
        String code = "Here is the handler.\n```\nUser: stop\n```\nAnd the rest of the answer.";
        assertTrue(FakeTurn.at(code) < 0);
        assertTrue(FakeTurn.at("Answer.\nUser: do the next thing") > 0);
    }
}
