package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The check after a code edit: which command it picks, how its result is read, and the agent acting on it. */
public class EditCheckTest {
    private static File ws() throws Exception {
        return Files.createTempDirectory("ibiko-check").toFile();
    }

    private static File write(File ws, String path, String text) throws Exception {
        File f = new File(ws, path);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void aLoneFileGetsASyntaxCheckAndNotesGetNone() throws Exception {
        File ws = ws();
        assertEquals("python3 -m py_compile 'app/main.py'", EditCheck.command(ws, write(ws, "app/main.py", "x = 1\n")));
        assertEquals("node --check 'web/a.js'", EditCheck.command(ws, write(ws, "web/a.js", "let a = 1;\n")));
        assertEquals("sh -n 'run.sh'", EditCheck.command(ws, write(ws, "run.sh", "echo hi\n")));
        assertNull(EditCheck.command(ws, write(ws, "notes.md", "# notes\n")));
        assertNull(EditCheck.command(ws, write(ws, "data.json", "{}\n")));
        assertNull("TypeScript has no single-file check here", EditCheck.command(ws, write(ws, "x.ts", "let a = 1\n")));
    }

    @Test
    public void theProjectsOwnTestsComeFirstFromItsFolder() throws Exception {
        File ws = ws();
        write(ws, "shop/package.json", "{\"name\":\"shop\",\"scripts\":{\"test\":\"jest\"}}");
        assertEquals("cd 'shop' && npm test --silent", EditCheck.command(ws, write(ws, "shop/src/cart.js", "export {}\n")));

        write(ws, "tool/tests/test_x.py", "def test_x():\n    assert True\n");
        assertEquals("cd 'tool' && python3 -m pytest -q -x", EditCheck.command(ws, write(ws, "tool/pkg/x.py", "x = 1\n")));

        write(ws, "lib/Makefile", "all:\n\tcc a.c\ntest: all\n\t./a.out\n");
        assertEquals("cd 'lib' && make test", EditCheck.command(ws, write(ws, "lib/a.c", "int main(){return 0;}\n")));

        write(ws, "Cargo.toml", "[package]\nname = \"x\"\n");
        assertEquals("tests at the workspace root run there", "cargo test -q", EditCheck.command(ws, write(ws, "src/lib.rs", "\n")));
    }

    @Test
    public void npmsPlaceholderTestIsNoTest() throws Exception {
        File ws = ws();
        write(ws, "package.json", "{\"scripts\":{\"test\":\"echo \\\"Error: no test specified\\\" && exit 1\"}}");
        assertEquals("node --check 'index.js'", EditCheck.command(ws, write(ws, "index.js", "1;\n")));
    }

    @Test
    public void aPathIsQuotedWhateverItHolds() throws Exception {
        File ws = ws();
        assertEquals("python3 -m py_compile 'my dir/it'\\''s.py'", EditCheck.command(ws, write(ws, "my dir/it's.py", "x = 1\n")));
    }

    @Test
    public void theResultIsReadFromTheExitCode() {
        assertEquals(EditCheck.Verdict.PASSED, EditCheck.verdict("(no output)\n[exit 0]"));
        assertEquals(EditCheck.Verdict.PASSED, EditCheck.verdict("3 passed in 0.1s"));
        assertEquals(EditCheck.Verdict.FAILED, EditCheck.verdict("SyntaxError: invalid syntax\n[exit 1]"));
        assertEquals(EditCheck.Verdict.FAILED, EditCheck.verdict("still going\n[stopped: it ran longer than 5 minutes]"));
        assertEquals(EditCheck.Verdict.NOT_AVAILABLE, EditCheck.verdict("sh: cargo: not found\n[exit 127]"));
        assertEquals(EditCheck.Verdict.NOT_AVAILABLE, EditCheck.verdict("/usr/bin/python3: No module named pytest\n[exit 1]"));
    }

    /** A phone whose shell answers from a script, and remembers what it was asked. */
    private static final class Phone implements CodeAgent.Host {
        final List<String> asked = new ArrayList<String>(), ran = new ArrayList<String>(), steps = new ArrayList<String>();
        final List<String> outputs;
        boolean allow = true;

        Phone(String... outputs) {
            this.outputs = new ArrayList<String>(Arrays.asList(outputs));
        }

        @Override
        public boolean allowRun(String command) {
            asked.add(command);
            return allow;
        }

        @Override
        public String run(String command, File workspace) {
            ran.add(command);
            return outputs.isEmpty() ? "[exit 0]" : outputs.remove(0);
        }

        @Override
        public String fetch(String url) {
            return null;
        }

        @Override
        public String connector(String call) {
            return "";
        }

        @Override
        public void step(String line) {
            steps.add(line);
        }

        @Override
        public boolean stopped() {
            return false;
        }
    }

    private static final class Script implements CodeAgent.Brain {
        final List<String> replies, seen = new ArrayList<String>();
        final List<String> systems = new ArrayList<String>();

        Script(String... replies) {
            this.replies = new ArrayList<String>(Arrays.asList(replies));
        }

        @Override
        public String ask(String system, String transcript) {
            systems.add(system);
            seen.add(transcript);
            return replies.isEmpty() ? null : replies.remove(0);
        }
    }

    @Test
    public void aFailedCheckReachesTheModelAndTheFixIsCheckedAgain() throws Exception {
        File ws = ws();
        Script brain = new Script(
                "<tool name=\"write_file\" path=\"calc.py\">def add(a, b)\n    return a + b\n</tool>",
                "<tool name=\"edit_file\" path=\"calc.py\"><old>def add(a, b)</old><new>def add(a, b):</new></tool>",
                "Done: calc.py adds two numbers, and it compiles.");
        Phone phone = new Phone("  File \"calc.py\", line 1\n    def add(a, b)\n                 ^\nSyntaxError: expected ':'\n[exit 1]",
                "(no output)\n[exit 0]");
        CodeAgent agent = new CodeAgent(ws, brain, phone, "", 20_000).checkEdits(true);
        String answer = agent.run("make a calc script", "");
        assertTrue(answer, answer.startsWith("Done"));
        assertEquals(Arrays.asList("python3 -m py_compile 'calc.py'", "python3 -m py_compile 'calc.py'"), phone.ran);
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("FAILED") && brain.seen.get(1).contains("SyntaxError: expected ':'"));
        assertTrue(brain.seen.get(2), brain.seen.get(2).contains("Checked with `python3 -m py_compile 'calc.py'`: passed."));
        assertTrue(phone.steps.toString(), phone.steps.contains("Checked calc.py: failed"));
        assertTrue(brain.systems.get(0).contains("Ibiko runs the project's tests"));
    }

    @Test
    public void aDeclinedCheckIsNotAskedAgainInTheSameTask() throws Exception {
        File ws = ws();
        Script brain = new Script(
                "<tool name=\"write_file\" path=\"a.py\">x = 1\n</tool>",
                "<tool name=\"write_file\" path=\"b.py\">y = 2\n</tool>",
                "Done: wrote a.py and b.py.");
        Phone phone = new Phone();
        phone.allow = false;
        CodeAgent agent = new CodeAgent(ws, brain, phone, "", 20_000).checkEdits(true);
        agent.run("make two scripts", "");
        assertEquals(1, phone.asked.size());
        assertTrue(phone.ran.isEmpty());
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("Not checked: the user didn't allow"));
    }

    @Test
    public void offMeansNoCheckAndNoWordAboutIt() throws Exception {
        File ws = ws();
        Script brain = new Script("<tool name=\"write_file\" path=\"a.py\">x = 1\n</tool>", "Done: wrote a.py.");
        Phone phone = new Phone();
        new CodeAgent(ws, brain, phone, "", 20_000).run("make a script", "");
        assertTrue(phone.asked.isEmpty());
        assertFalse(brain.systems.get(0).contains("Ibiko runs the project's tests"));
    }
}
