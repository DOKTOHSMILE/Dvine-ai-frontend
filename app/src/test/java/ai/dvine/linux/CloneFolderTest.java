package ai.dvine.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CloneFolderTest {
    @Test
    public void aCloneLandsInTheProjectsFolder() {
        String placed = CloneFolder.place("git clone https://github.com/DOKTOHSMILE/Dvine-ai-frontend.git /root/workspace/Dvine-ai-frontend");
        assertTrue(placed.contains("mkdir -p /workspace/memory/projects"));
        assertTrue(placed.contains("/workspace/memory/projects/Dvine-ai-frontend"));
        assertFalse(placed.contains("/root/workspace"));
        assertEquals("Dvine-ai-frontend", CloneFolder.nameOf("https://github.com/DOKTOHSMILE/Dvine-ai-frontend.git"));
    }

    @Test
    public void aCloneIsRetargetedEvenWhenTheCommandAlsoListsTheProjectsFolder() {
        String placed = CloneFolder.place("ls memory/projects\n"
                + "git -c http.version=HTTP/1.1 clone --depth 1 https://github.com/a/b.git /root/workspace/b");
        assertTrue(placed.contains("/workspace/memory/projects/b"));
        assertTrue(placed.contains("--depth"));
        assertTrue(placed.contains("checkout -f HEAD"));
        assertFalse(placed.contains("/root/workspace/b"));
    }

    @Test
    public void progressIsOneLine() {
        String noisy = "Cloning into '/root/workspace/Dvine-ai-frontend'...\n"
                + "remote: Counting objects: 21% (98/463)\n"
                + "remote: Counting objects: 37% (172/463)\n"
                + "Receiving objects: 10% (46/463)\n"
                + "done\n";
        String quiet = CloneFolder.quiet(noisy);
        assertFalse(quiet.contains("21%"));
        assertFalse(quiet.contains("Counting"));
        assertFalse(quiet.contains("Cloning into"));
        assertTrue(quiet.contains("done"));
        assertTrue(CloneFolder.mostlyProgress(noisy));
        assertEquals("Cloning the repository.", CloneFolder.quiet("remote: Counting objects: 37% (172/463)\n"
                + "remote: Counting objects: 38% (176/463)"));
        String fenced = "```yaml\nCloning into '/root/workspace/Dvine-ai-frontend'...\n"
                + "remote: Counting objects: 36% (167/463)\nremote: Counting objects: 37% (172/463)\n```";
        String card = CloneFolder.quiet(fenced);
        assertFalse(card.contains("```"));
        assertFalse(card.contains("Counting"));
        assertFalse(card.contains("/root/workspace"));
    }
}
