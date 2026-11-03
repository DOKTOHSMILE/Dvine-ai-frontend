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
    private static class Script implements CodeAgent.Brain {
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

    @Test
    public void gitToolClonesAndAsksBeforePushing() throws Exception {
        ai.dvine.connect.GitHubGitTest.FakeHub hub = new ai.dvine.connect.GitHubGitTest.FakeHub();
        java.util.Map<String, String> f = new java.util.TreeMap<String, String>();
        f.put("app.py", "x = 1\n");
        hub.refs.put("main", hub.commit(f, null, "first"));
        File ws = Files.createTempDirectory("ibiko").toFile();
        Phone phone = new Phone();
        CodeAgent agent = new CodeAgent(ws, new Script(
                "<tool name=\"git\" action=\"clone\" repo=\"me/app\"/>",
                "<tool name=\"edit_file\" path=\"app/app.py\"><old>x = 1</old><new>x = 2</new></tool>",
                "<tool name=\"git\" action=\"push\" path=\"app\" message=\"Set x to 2\"/>",
                "Done."), phone, "", 20_000).useGit(new ai.dvine.connect.GitHubGit(hub, "tok"));
        assertTrue(agent.system().contains("action=\"push\""));
        phone.allow = false;
        assertEquals("Done.", agent.run("bump x", ""));
        assertEquals(1, phone.asked.size());
        assertTrue(phone.asked.get(0), phone.asked.get(0).startsWith("git commit and push app"));
        assertEquals("x = 1\n", hub.filesAt("main").get("app.py")); // not allowed: nothing pushed
    }

    @Test
    public void readsTheToolCallFormsOtherModelsWrite() {
        // exactly what GPT-5 wrote on the phone
        CodeAgent.Call a = CodeAgent.parse("I'll clone the repo first, then audit it.<tool_call><function=git action=\"clone\" "
                + "repo=\"DOKTOHSMILE/Dvine-ai-frontend.git\"/></function></tool_call>");
        assertEquals("git", a.name);
        assertEquals("clone", a.a("action"));
        assertEquals("DOKTOHSMILE/Dvine-ai-frontend.git", a.a("repo"));
        CodeAgent.Call b = CodeAgent.parse("<tool_call><function=fetch><parameter=url>https://brewkeg.dev/install.sh</parameter></function></tool_call>");
        assertEquals("fetch", b.name);
        assertEquals("https://brewkeg.dev/install.sh", b.a("url"));
        CodeAgent.Call c = CodeAgent.parse("<tool_call><function=list_files><parameter=path>.</parameter></function></tool_call>");
        assertEquals("list_files", c.name);
        assertEquals(".", c.a("path"));
        CodeAgent.Call d = CodeAgent.parse("<tool_call>{\"name\": \"write_file\", \"arguments\": {\"path\": \"a.py\", \"content\": \"x = 1\\n\"}}</tool_call>");
        assertEquals("write_file", d.name);
        assertEquals("a.py", d.a("path"));
        assertEquals("x = 1\n", d.body);
        CodeAgent.Call e = CodeAgent.parse("<invoke name=\"bash\"><parameter name=\"command\">ls -la</parameter></invoke>");
        assertEquals("run", e.name);
        assertEquals("ls -la", e.a("command"));
        CodeAgent.Call f = CodeAgent.parse("<function=web_search><parameter=query>Google history</parameter></function>");
        assertEquals("connector", f.name);
        assertEquals("web.search Google history", f.a("call"));
        assertNull(CodeAgent.parse("Here is the file you asked for."));
    }

    @Test
    public void aPromiseIsNotAnAnswer() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        Script brain = new Script("I'll research Google and compile a full overview.",
                "<function=web_search><parameter=query>Google</parameter></function>",
                "Google was founded in 1998 by Larry Page and Sergey Brin.");
        Phone phone = new Phone();
        String answer = new CodeAgent(ws, brain, phone, "", 20_000).run("Research google", "");
        assertEquals("Google was founded in 1998 by Larry Page and Sergey Brin.", answer);
        // the promise was not taken as the answer: the model was asked, and it carried on with a tool
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("You wrote text without a tool call"));
        assertTrue(brain.seen.get(2), brain.seen.get(2).contains("connector said: web.search Google"));
    }

    @Test
    public void longWorkGoesInRoundsWithAProgressNoteBetween() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        Script brain = new Script(
                "<tool name=\"write_file\" path=\"app/PLAN.md\">- [ ] build\n- [ ] test</tool>",
                "<tool name=\"list_files\" path=\".\"/>",
                "Planned the work in app/PLAN.md; next I build the first item.", // the note when the round's steps are used
                "<final>Built and tested: both items ticked.</final>");
        Phone phone = new Phone();
        CodeAgent first = new CodeAgent(ws, brain, phone, "", 20_000).steps(2).keepGoing(true);
        assertTrue(first.system().contains("PLAN.md") && first.system().contains("plan, build, test"));
        String note = first.run("Build me a notes app", "");
        assertTrue(first.outOfSteps());
        assertEquals("Planned the work in app/PLAN.md; next I build the first item.", note);
        assertTrue(brain.seen.get(2), brain.seen.get(2).contains("Write a short progress note"));
        assertTrue(new File(ws, "app/PLAN.md").isFile());
        // the next round goes on from the same work
        CodeAgent next = new CodeAgent(ws, brain, phone, "", 20_000).steps(2).keepGoing(true);
        next.resume(first.work());
        assertEquals("Built and tested: both items ticked.", next.run("Carry on", ""));
        assertFalse(next.outOfSteps());
        assertTrue(brain.seen.get(3), brain.seen.get(3).contains("Planned the work in app/PLAN.md"));
        // without rounds, the old way: a report and "continue"
        assertFalse(new CodeAgent(ws, new Script(), phone, "", 20_000).system().contains("PLAN.md"));
    }

    @Test
    public void aSmallModelsPlainAnswerIsTheAnswerWithoutItsNameLabel() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        Script brain = new Script("ibiko: The next step is to review activity_chat.xml, then add the tabs.", "never asked");
        Phone phone = new Phone();
        String out = new CodeAgent(ws, brain, phone, "", 20_000).compact(true).run("What's the next step?", "");
        assertEquals("The next step is to review activity_chat.xml, then add the tabs.", out);
        assertEquals(1, brain.seen.size()); // not asked a second time whether it is done (minutes on a phone)
        assertTrue(phone.steps.isEmpty()); // and the answer is not shown as a step
        // a full-size model is still asked once; the step says so, the answer itself goes to the chat
        Script big = new Script("I'll read the files next.", "<final>Done: nothing to change.</final>");
        Phone p2 = new Phone();
        assertEquals("Done: nothing to change.", new CodeAgent(ws, big, p2, "", 20_000).run("check it", ""));
        assertEquals(Arrays.asList("Checking that the answer is complete"), p2.steps);
        // a small model's own-tag call (<search query="car"/>) is carried out, not shown as the answer
        CodeAgent.Call c = CodeAgent.parse("<search query=\"car\"/>");
        assertEquals("search", c.name);
        assertEquals("car", c.a("query"));
        assertEquals("app/Main.java", CodeAgent.parse("<read_file path=\"app/Main.java\"/>").a("path"));
        Script searching = new Script("<search query=\"car\"/>", "No file mentions a car.");
        assertEquals("No file mentions a car.", new CodeAgent(ws, searching, new Phone(), "", 20_000).compact(true).run("Search me a car", ""));
        assertTrue(searching.seen.get(1), searching.seen.get(1).contains("<result>"));
        // it writes a <result> itself (imitating the work log): told once, and no tag ever reaches the user
        Script faking = new Script("<result> Files listed: - a.java </result>", "Yes, I can read code: tell me which file.");
        assertEquals("Yes, I can read code: tell me which file.", new CodeAgent(ws, faking, new Phone(), "", 20_000).compact(true).run("Can you read code?", ""));
        assertTrue(faking.seen.get(1), faking.seen.get(1).contains("Don't write <result> yourself"));
        Script stubborn = new Script("<result>I can read code.</result>", "<result>I can read code.</result>", "<result>I can read code.</result>");
        assertEquals("I can read code.", new CodeAgent(ws, stubborn, new Phone(), "", 20_000).compact(true).run("Can you read code?", ""));
        assertEquals("Hi", CodeAgent.unlabel("**Ibiko**: Hi"));
        assertEquals("Obi said yes", CodeAgent.unlabel("Obi said yes")); // only a leading label
    }

    @Test
    public void planModeReadsAndHandsInAPlanWithoutChangingAnything() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        File repo = new File(ws, "app");
        repo.mkdirs();
        Files.write(new File(repo, "Main.java").toPath(), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        Script brain = new Script("<tool name=\"read_file\" path=\"app/Main.java\"/>",
                "<tool name=\"write_file\" path=\"app/Main.java\">gone</tool>",
                "<tool name=\"run\" command=\"rm -r app\"/>",
                "<final>1. Add a main method to app/Main.java.\n2. Run it.</final>");
        Phone phone = new Phone();
        phone.allow = true;
        CodeAgent ibiko = new CodeAgent(ws, brain, phone, "", 20_000).as("Ibiko", false).planning(true);
        assertTrue(ibiko.system(), ibiko.system().startsWith("You are Ibiko, DVINE's coding agent") && ibiko.system().contains("Plan mode"));
        assertFalse(ibiko.system().contains("write_file") || ibiko.system().contains("name=\"run\""));
        String plan = ibiko.run("Make Main runnable", "");
        assertTrue(plan, plan.contains("1. Add a main method"));
        assertTrue(brain.seen.get(2), brain.seen.get(2).contains("Plan mode: nothing is changed yet"));
        assertTrue(phone.asked.isEmpty()); // nothing ran, nothing was even asked
        assertEquals("class Main {}\n", new String(Files.readAllBytes(new File(repo, "Main.java").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void obiOrIvieOnlyReadAndTheStepLimitIsTheUsers() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        File repo = new File(ws, "app");
        repo.mkdirs();
        Files.write(new File(repo, "Main.java").toPath(), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        Script brain = new Script("<tool name=\"read_file\" path=\"app/Main.java\"/>",
                "<tool name=\"write_file\" path=\"app/Main.java\">gone</tool>", "Main.java declares an empty class Main.");
        CodeAgent obi = new CodeAgent(ws, brain, new Phone(), "", 20_000).as("Obi", true);
        assertTrue(obi.system().startsWith("You are Obi, reading the files"));
        assertFalse(obi.system().contains("Local sandbox")); // reading only: no sandbox, no git
        CodeAgent ibiko = new CodeAgent(ws, brain, new Phone(), "", 20_000)
                .useGit(new ai.dvine.connect.GitHubGit(new ai.dvine.connect.GitHubGitTest.FakeHub(), "tok"));
        assertTrue(ibiko.system().contains("Local sandbox") && ibiko.system().contains("sandbox/<short-name>"));
        assertTrue(ibiko.system().contains("git pull, then push again") && ibiko.system().contains("open a pull request with git pr"));
        String full = ibiko.system();
        String small = ibiko.compact(true).system(); // a small model's memory: the walkthroughs go, the tools stay
        assertFalse(small.contains("Local sandbox") || small.contains("Git, step by step"));
        assertTrue(small.contains("action=\"push\"") && small.length() < full.length() - 1000);
        assertFalse(obi.system().contains("write_file") || obi.system().contains("name=\"run\""));
        assertEquals("Main.java declares an empty class Main.", obi.run("What is in the cloned app's Main.java?", ""));
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("1\tclass Main {}"));
        assertTrue(brain.seen.get(2), brain.seen.get(2).contains("Only reading here"));
        assertEquals("class Main {}\n", new String(Files.readAllBytes(new File(repo, "Main.java").toPath()), StandardCharsets.UTF_8));

        Script looping = new Script("<tool name=\"list_files\" path=\".\"/>", "<tool name=\"list_files\" path=\".\"/>",
                "<tool name=\"list_files\" path=\".\"/>");
        String out = new CodeAgent(ws, looping, new Phone(), "", 20_000).steps(2).run("look", "");
        assertTrue(out, out.startsWith("I used all 2 steps"));
    }

    @Test
    public void tuningKeepsNumbersInRange() {
        assertEquals(30, ai.dvine.chat.Tuning.AGENT_STEPS.get(null));
        assertEquals(5000, ai.dvine.chat.Tuning.CLONE_FILES.get(null)); // the clone limit is a setting, not a fixed 600
        assertEquals(1500, ai.dvine.chat.Tuning.PASTE_CHARS.get(null));
        assertTrue(ai.dvine.chat.Attachments.longPaste("x".repeat(300), 200));
        assertFalse(ai.dvine.chat.Attachments.longPaste("x".repeat(300)));
    }

    @Test
    public void aLongTaskGetsTheFullResultNotAStatusLine() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        String report = "Audit of app/: 3 problems.\n1. MainActivity.java is 3,000 lines (split it).\n2. No tests for the network layer.\n"
                + "3. Keys are logged in debug builds (RemoteBrain.java:120): remove the log.\n" + "x".repeat(400);
        Script brain = new Script("Auditing your codebase now \u2014 mapping structure and checking key files.", report);
        String answer = new CodeAgent(ws, brain, new Phone(), "", 20_000).run("Give me the result of the audit", "");
        assertEquals(report, answer);
        assertTrue(brain.seen.get(1), brain.seen.get(1).contains("You wrote text without a tool call")); // asked: done, or go on?
        assertTrue(CodeAgent.bigTask("RE clone the repo and Audit my codebase then share findings"));
        assertFalse(CodeAgent.bigTask("hello"));
    }

    @Test
    public void aBigTaskNeverEndsOnAStatusLineAndCarriesOn() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        Files.write(new File(ws, "Main.java").toPath(), "class Main {}\n".getBytes(StandardCharsets.UTF_8));
        final List<String> longAsks = new ArrayList<String>();
        String report = "Audit: 2 problems.\n1. Main.java:1 is an empty class.\n2. No tests.\n" + "detail ".repeat(300).trim();
        CodeAgent.Brain brain = new CodeAgent.Brain() {
            final List<String> replies = new ArrayList<String>(Arrays.asList("<tool name=\"read_file\" path=\"Main.java\"/>",
                    "Re-cloning and auditing your codebase \u2014 now reading the core files to surface issues and fixes.",
                    "Here's where things stand. Want me to continue straight into the full findings + fixes, or pause here?",
                    "Got it \u2014 auditing your clone now, I'll pull the findings together."));

            @Override
            public String ask(String system, String transcript) {
                return replies.isEmpty() ? null : replies.remove(0);
            }

            @Override
            public String askLong(String system, String transcript) {
                longAsks.add(transcript);
                return report;
            }
        };
        CodeAgent agent = new CodeAgent(ws, brain, new Phone(), "", 20_000);
        assertEquals(report, agent.run("RE clone the repo and Audit my codebase then share findings and solutions", ""));
        assertEquals(1, longAsks.size());
        assertTrue(longAsks.get(0).contains("Now write the complete final report"));
        assertTrue(longAsks.get(0).contains("1\tclass Main {}")); // with what was read
        assertTrue(agent.work().contains("<tool name=\"read_file\" path=\"Main.java\"/>"));

        // "give me the result": the same work goes on, nothing read again
        final List<String> seen = new ArrayList<String>();
        CodeAgent.Brain again = new CodeAgent.Brain() {
            @Override
            public String ask(String system, String transcript) {
                seen.add(transcript);
                return report;
            }
        };
        String out = new CodeAgent(ws, again, new Phone(), "", 50_000).resume(agent.work()).run("give me the result of the audit", "");
        assertEquals(report, out);
        assertTrue(seen.get(0), seen.get(0).contains("1\tclass Main {}") && seen.get(0).contains("The user now says: give me the result of the audit"));

        // out of steps: the report from what was found, not "I used all the steps"
        CodeAgent.Brain looping = new CodeAgent.Brain() {
            @Override
            public String ask(String system, String transcript) {
                return "<tool name=\"list_files\" path=\".\"/>";
            }

            @Override
            public String askLong(String system, String transcript) {
                return report;
            }
        };
        String capped = new CodeAgent(ws, looping, new Phone(), "", 50_000).steps(3).run("audit it", "");
        assertTrue(capped, capped.startsWith(report) && capped.contains("3 steps used. Say \u201ccontinue\u201d"));
    }

    @Test
    public void finalAnswersEmptyRepliesAndQuestions() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        // <final> ends the work, its words are the answer
        assertEquals("Hello! I'm Ibiko.", new CodeAgent(ws, new Script("<final>Hello! I'm Ibiko.</final>"), new Phone(), "", 20_000).run("hello", ""));
        // an empty reply (a dropped connection) is asked again once
        Script flaky = new Script("", "Paris.");
        assertEquals("Paris.", new CodeAgent(ws, flaky, new Phone(), "", 20_000).run("capital of France?", ""));
        // a plain request may end on a plain answer; asking leave never ends a job
        Script asking = new Script("I listed the files. Shall I proceed?", "<final>Done: 3 files, all fine.</final>");
        Phone phone = new Phone();
        assertEquals("Done: 3 files, all fine.", new CodeAgent(ws, asking, phone, "", 20_000).run("check the files", ""));
        assertTrue(asking.seen.get(1), asking.seen.get(1).contains("don't describe it and don't ask"));
        assertTrue(phone.steps.toString(), phone.steps.contains("Checking that the answer is complete")); // the held text itself is not a step
        // no word lists: whatever the wording, text without a tool call is asked about once; what the model does
        // next decides. A tool call: the work goes on. Plain text again: that is the answer.
        Script narrating = new Script("Fixing your build errors \u2014 digging into the failing sources.",
                "<tool name=\"list_files\" path=\".\"/>", "<final>Fixed: the missing import in Main.java.</final>");
        assertEquals("Fixed: the missing import in Main.java.", new CodeAgent(ws, narrating, new Phone(), "", 20_000).run("fix the build", ""));
        // asked whether it was done, the request failed: that is not "nothing to add", the user hears why
        Script dropped = new Script("Clearing your phone sandbox \u2014 wiping the local workspace clean.") {
            @Override
            public String why() {
                return "the connection to the gateway broke";
            }
        };
        String stopped = new CodeAgent(ws, dropped, new Phone(), "", 20_000).run("clear my sandbox", "");
        assertTrue(stopped, stopped.contains("The model didn't answer (the connection to the gateway broke)")
                && stopped.startsWith("Clearing your phone sandbox"));
        Script plain = new Script("Fixed it \u2014 missing import added in Main.java.", "Fixed it \u2014 missing import added in Main.java.");
        assertEquals("Fixed it \u2014 missing import added in Main.java.", new CodeAgent(ws, plain, new Phone(), "", 20_000).run("fix the build", ""));
    }
}
