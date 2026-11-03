package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

/** Zip and unzip in the workspace (never outside it), and which tasks are Ibiko's to be handed over. */
public class ZipsAndHandoffTest {
    @Test
    public void zipUnzipAndNoEscape() throws Exception {
        File ws = Files.createTempDirectory("ibiko").toFile();
        File app = new File(ws, "app/src");
        app.mkdirs();
        Files.write(new File(app, "Main.java").toPath(), "class Main {}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(ws, "app/README.md").toPath(), "# app".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, Zips.zip(new File(ws, "app"), new File(ws, "app.zip")));
        assertEquals(2, Zips.unzip(new File(ws, "app.zip"), new File(ws, "copy")));
        assertEquals("class Main {}", new String(Files.readAllBytes(new File(ws, "copy/app/src/Main.java").toPath()), StandardCharsets.UTF_8));
        File evil = new File(ws, "evil.zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(evil))) {
            z.putNextEntry(new ZipEntry("../../escaped.txt"));
            z.write(1);
            z.closeEntry();
        }
        try {
            Zips.unzip(evil, new File(ws, "x"));
            assertTrue("should refuse", false);
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("outside"));
        }
        assertFalse(new File(ws.getParentFile(), "escaped.txt").exists());
        // the agent's tools
        CodeAgentTestHelper.Script brain = new CodeAgentTestHelper.Script("<tool name=\"zip\" path=\"app\" to=\"out/app.zip\"/>",
                "<tool name=\"unzip\" path=\"out/app.zip\" to=\"back\"/>", "<final>Done.</final>");
        assertEquals("Done.", new CodeAgent(ws, brain, new CodeAgentTestHelper.Quiet(), "", 20_000).run("zip and unzip", ""));
        assertTrue(new File(ws, "back/app/README.md").isFile());
    }

    @Test
    public void onlyIbikosKindOfWorkIsHandedOver() {
        assertTrue(Handoff.forIbiko("Fix the login crash in my Android app"));
        assertTrue(Handoff.forIbiko("Clone my GitHub repo and run the tests"));
        assertTrue(Handoff.forIbiko("Write a Python script that renames my photos"));
        assertTrue(Handoff.forIbiko("Can you write a function to sort these names in Java?"));
        assertFalse(Handoff.forIbiko("What is an API?"));
        assertFalse(Handoff.forIbiko("Explain how Git branches work"));
        assertFalse(Handoff.forIbiko("Make a PDF of that"));
        assertFalse(Handoff.forIbiko("Plan my week"));
        assertEquals("Fix the login crash in my Android app", Handoff.title("fix the login crash in my Android app"));
    }

    @Test
    public void agentsAreOnTheBoardWhileTheyWork() {
        String a = AgentBoard.start("Ibiko · lead", "Audit the repo");
        String b = AgentBoard.start("Ibiko · mini agent", "security");
        assertTrue(a.matches("[A-Z]+-\\d+") && !a.equals(b));
        assertEquals(2, AgentBoard.now().stream().filter(e -> e.code.equals(a) || e.code.equals(b)).count());
        AgentBoard.end(a);
        AgentBoard.end(b);
        assertEquals(0, AgentBoard.now().stream().filter(e -> e.code.equals(a) || e.code.equals(b)).count());
    }
}
