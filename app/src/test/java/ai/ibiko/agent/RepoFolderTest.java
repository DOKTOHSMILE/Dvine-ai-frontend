package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RepoFolderTest {
    @Test
    public void projectsWithoutMemoryIsTheCloneFolder() {
        assertEquals("repos/Dvine-ai-frontend", RepoFolder.place("projects/Dvine-ai-frontend"));
        assertEquals("repos/Dvine-ai-frontend/PLAN.md", RepoFolder.place("projects/Dvine-ai-frontend/PLAN.md"));
        assertEquals("/workspace/repos/Dvine-ai-frontend", RepoFolder.place("/workspace/projects/Dvine-ai-frontend"));
    }

    @Test
    public void theRealFolderIsLeftAlone() {
        assertEquals("the old folder is the new one", "repos/Dvine-ai-frontend", RepoFolder.place("memory/projects/Dvine-ai-frontend"));
        assertEquals("repos/Dvine-ai-frontend", RepoFolder.place("repos/Dvine-ai-frontend"));
        assertEquals("memory/records/x/RECORD.md", RepoFolder.place("memory/records/x/RECORD.md"));
        assertEquals("cd repos/Dvine-ai-frontend && ls", RepoFolder.place("cd repos/Dvine-ai-frontend && ls"));
        assertEquals("app/src/Main.java", RepoFolder.place("app/src/Main.java"));
    }

    @Test
    public void aPathCorrectionIsTheSamePlanStep() {
        String old = "# Blueprint: audit\n## Steps\n- [ ] 1. Read projects/Dvine-ai-frontend/PLAN.md (files: projects/Dvine-ai-frontend/PLAN.md; check: the path exists)\n";
        String fixed = old.replace("projects/Dvine-ai-frontend", "repos/Dvine-ai-frontend");
        assertNull(Blueprint.kept(old, fixed));
        assertTrue(Blueprint.RULES.contains("repos/<name>"));
        assertTrue(Blueprint.RULES.contains("projects/<name>"));
    }
}
