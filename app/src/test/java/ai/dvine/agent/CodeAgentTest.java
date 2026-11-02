package ai.dvine.agent;

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

import ai.dvine.llm.TaskSize;

/** Ibiko's coding agent with a scripted brain, its workspace walls, and the task-size guess. */
public class CodeAgentTest {
    private static final class Script implements CodeAgent.Brain {
        final List<String> replies, seen = new ArrayList<String>();

        Script(String... replies) {
            this.replies = new ArrayList<String>(Arrays.asList(replies));
        }

        @Override
        public String ask(String system, String transcript) {
            seen.add(transcript);
            return replies.isEmpty() ? null : replies.remove(0);
        }
    }

    private static final class Phone implements CodeAgent.Host {
        final List<String> steps = new ArrayList<String>(), asked = new ArrayList<String>();
        boolean allow;

        @Override
        public boolean allowRun(String command) {
            asked.add(command);
            return allow;
        }

        @Override
        public String run(String command, File workspace) {
            return SandboxShell.run(command, workspace, 5000, 2000);
        }

        @Override
        public String fetch(String url) {
            return "page";
        }

        @Override
        public String connector(String call) {
            return "connector said: " + call;
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

    @Test
    public void worksStepByStepThenAnswers() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        Script brain = new Script(
                "I'll look first.\n<tool name=\"list_files\" path=\".\"/>",
                "<tool name=\"write_file\" path=\"src/hello.py\">print(\"hello\")\nprint(\"bye\")\n</tool>",
                "<tool name=\"read_file\" path=\"src/hello.py\"/>",
                "<tool name=\"edit_file\" path=\"src/hello.py\"><old>print(\"bye\")</old><new>print(\"goodbye\")</new></tool>",
                "<tool name=\"run\" command=\"python3 src/hello.py\"/>",
                "<tool name=\"search\" query=\"goodbye\"/>",
                "Done. I wrote `src/hello.py`:\n```python\nprint(\"hello\")\nprint(\"goodbye\")\n```");
        Phone phone = new Phone();
        CodeAgent agent = new CodeAgent(ws, brain, phone, "", 20_000);
        String answer = agent.run("make a hello script", "");
        assertTrue(answer, answer.startsWith("Done. I wrote"));
        assertEquals("print(\"hello\")\nprint(\"goodbye\")\n",
                new String(Files.readAllBytes(new File(ws, "src/hello.py").toPath()), StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("src/hello.py"), agent.changed());
        assertEquals(Arrays.asList("python3 src/hello.py"), phone.asked); // the command was asked about
        assertTrue(phone.steps.contains("Created src/hello.py (3 lines)"));
        assertTrue(phone.steps.contains("Edited src/hello.py"));
        assertTrue(phone.steps.contains("Not run: python3 src/hello.py"));
        String last = brain.seen.get(brain.seen.size() - 1);
        assertTrue(last.contains("src/hello.py:2: print(\"goodbye\")")); // the search result reached the brain
        assertTrue(brain.seen.get(3).contains("1\tprint(\"hello\")")); // a file is read back with line numbers
    }

    @Test
    public void staysInsideItsWorkspace() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        Script brain = new Script("<tool name=\"write_file\" path=\"../../evil.txt\">x</tool>",
                "<tool name=\"read_file\" path=\"/etc/hosts\"/>", "OK");
        CodeAgent agent = new CodeAgent(ws, brain, new Phone(), "", 20_000);
        assertEquals("OK", agent.run("try to escape", ""));
        assertTrue(brain.seen.get(1).contains("Error: that path is outside the workspace"));
        assertTrue(brain.seen.get(2).contains("Error: that path is outside the workspace"));
        assertFalse(new File(ws.getParentFile().getParentFile(), "evil.txt").exists());
    }

    @Test
    public void readsToolCallsAndRunsAllowedCommands() throws Exception {
        CodeAgent.Call c = CodeAgent.parse("Plan: check.\n<tool name=\"run\" command=\"echo \\\"hi\\\"\"/>");
        assertEquals("run", c.name);
        assertEquals("echo \"hi\"", c.a("command"));
        assertNull(CodeAgent.parse("All done, here's the answer."));
        File ws = Files.createTempDirectory("ibiko").toFile();
        Phone phone = new Phone();
        phone.allow = true;
        CodeAgent agent = new CodeAgent(ws, new Script("<tool name=\"run\" command=\"echo made > note.txt; cat note.txt\"/>", "fine"),
                phone, "", 20_000);
        assertEquals("fine", agent.run("x", ""));
        assertTrue(new File(ws, "note.txt").isFile()); // the command ran in the workspace
        String slow = SandboxShell.run("sleep 5", ws, 300, 100);
        assertTrue(slow, slow.contains("stopped after"));
    }

    @Test
    public void bigWorkGoesToTheStrongModel() {
        assertEquals(TaskSize.Tier.FAST, TaskSize.of("What's the capital of Nigeria?", false));
        assertEquals(TaskSize.Tier.FAST, TaskSize.of("hi there", false));
        assertEquals(TaskSize.Tier.STRONG, TaskSize.of("Write a business plan for a bakery in Lagos", false));
        assertEquals(TaskSize.Tier.STRONG, TaskSize.of("Can you debug this function for me?", false));
        assertEquals(TaskSize.Tier.STRONG, TaskSize.of("hello", true)); // Ibiko: always strong
        assertEquals(TaskSize.Tier.FAST, TaskSize.ofCall("write an essay", 48, false)); // a one-line order is fast
        assertEquals(TaskSize.Tier.STRONG, TaskSize.ofCall("x", 2000, false));
    }
}
