package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RepoFolderTest {
    @Test
    public void projectsWithoutMemoryIsTheCloneFolder() {
        assertEquals("memory/projects/Dvine-ai-frontend", RepoFolder.place("projects/Dvine-ai-frontend"));
        assertEquals("memory/projects/Dvine-ai-frontend/PLAN.md", RepoFolder.place("projects/Dvine-ai-frontend/PLAN.md"));
        assertEquals("/workspace/memory/projects/Dvine-ai-frontend", RepoFolder.place("/workspace/projects/Dvine-ai-frontend"));
    }

    @Test
    public void theRealFolderIsLeftAlone() {
        assertEquals("memory/projects/Dvine-ai-frontend", RepoFolder.place("memory/projects/Dvine-ai-frontend"));
        assertEquals("cd memory/projects/Dvine-ai-frontend && ls", RepoFolder.place("cd memory/projects/Dvine-ai-frontend && ls"));
        assertEquals("app/src/Main.java", RepoFolder.place("app/src/Main.java"));
    }

    @Test
    public void aPathCorrectionIsTheSamePlanStep() {
        String old = "# Blueprint: audit\n## Steps\n- [ ] 1. Read projects/Dvine-ai-frontend/PLAN.md (files: projects/Dvine-ai-frontend/PLAN.md; check: the path exists)\n";
        String fixed = old.replace("projects/Dvine-ai-frontend", "memory/projects/Dvine-ai-frontend");
        assertNull(Blueprint.kept(old, fixed));
        assertTrue(Blueprint.RULES.contains("memory/projects/<name>"));
        assertTrue(Blueprint.RULES.contains("projects/<name>"));
    }
}
