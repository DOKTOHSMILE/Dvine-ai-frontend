package ai.ibiko.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Ibiko on the phone's own model, as the app runs it: the real CodeAgent, its real instructions, LocalModel.task with
 * the agent's allowance, the memory the app asks for. Runs only with IBIKO_NATIVE_LIB and IBIKO_MODEL set (a desktop
 * build of the same native code and a GGUF file), like NativeBridgeTest.
 */
public class OfflineIbikoTest {
    @Test
    public void ibikoAnswersOnTheOfflineModel() throws Exception {
        String lib = System.getenv("IBIKO_NATIVE_LIB"), gguf = System.getenv("IBIKO_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "Gemma"), true);
        m.context(Integer.parseInt(System.getenv().getOrDefault("IBIKO_CONTEXT", "8192")));
        assertTrue(m.load(4));
        System.out.println("OFFLINE window: " + m.localWindow());
        File ws = Files.createTempDirectory("ibiko").toFile();
        // a workspace with a cloned project in it, as on the user's phone
        for (int i = 0; i < Integer.parseInt(System.getenv().getOrDefault("IBIKO_FILES", "300")); i++) {
            File d = new File(ws, "Dvine-ai-frontend/app/src/main/java/ai/ibiko/pkg" + (i % 30));
            d.mkdirs();
            Files.write(new File(d, "SomeLongClassName" + i + ".java").toPath(), "class X {}\n".getBytes("UTF-8"));
        }
        // Obi, as the app sends a short message: the meaning step first, then the persona's reply
        String meaning = m.task(ai.ibiko.turn.Meaning.prompt(false), "Hello, how are you?", 24, null);
        System.out.println("OFFLINE meaning: " + meaning + " → " + ai.ibiko.turn.Meaning.read(meaning) + " (problem " + m.problem() + ")");
        String hello = m.reply(PersonaPrompt.system("obi", "Val", "Monday 2 November 2026", ""), "Hello, how are you?", null,
                ai.ibiko.chat.Tuning.CHAT_TOKENS.get(null), null);
        System.out.println("OFFLINE obi: " + hello + " (problem " + m.problem() + ")");
        assertTrue("Obi said nothing: " + m.problem(), hello != null && !hello.trim().isEmpty());
        String only = System.getenv("IBIKO_TASK"); // one task, for a quicker run
        for (String task : only != null ? new String[]{only} : new String[]{"Can you read code?", "Search me a car"}) {
            ai.ibiko.agent.CodeAgent agent = new ai.ibiko.agent.CodeAgent(ws, new ai.ibiko.agent.CodeAgent.Brain() {
                @Override
                public String ask(String system, String transcript) {
                    System.out.println("OFFLINE step: system " + system.length() + " chars, transcript " + transcript.length() + " chars");
                    String said = m.task(system, transcript, ai.ibiko.chat.Tuning.AGENT_TOKENS.get(null), null);
                    System.out.println("OFFLINE   said: " + (said == null ? "null" : said.replace('\n', ' ').substring(0, Math.min(200, said.length()))));
                    return said;
                }

                @Override
                public String why() {
                    return m.problem();
                }
            }, new Quiet(), "", m.window() * 3).steps(4).compact(m.window() < 6000);
            String answer = agent.run(task, "");
            System.out.println("OFFLINE task: " + task + "\nOFFLINE answer: " + answer + "\nOFFLINE problem: " + m.problem());
            assertTrue(task + " → " + answer, answer != null && !answer.trim().isEmpty() && !answer.contains("too long for the model's memory"));
        }
    }

    /** No commands run and no network: only the model's own steps are under test. */
    static final class Quiet implements ai.ibiko.agent.CodeAgent.Host {
        @Override
        public boolean allowRun(String command) {
            return false;
        }

        @Override
        public String run(String command, File workspace) {
            return "";
        }

        @Override
        public String fetch(String url) {
            return "";
        }

        @Override
        public String connector(String call) {
            return "";
        }

        @Override
        public void step(String line) {
            System.out.println("OFFLINE   · " + line);
        }

        @Override
        public boolean stopped() {
            return false;
        }
    }
}
