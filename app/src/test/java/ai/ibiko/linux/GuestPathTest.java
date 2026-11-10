package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class GuestPathTest {
    @Test
    public void aProjectIsTheSameFolderTheFileToolsSee() {
        File root = new File("/data/user/0/ai.ibiko/files/ibiko");
        assertEquals("/workspace", GuestPath.of(root));
        assertEquals("/workspace/Dvine-ai-frontend", GuestPath.of(new File(root, "Dvine-ai-frontend")));
        String run = GuestPath.at(new File(root, "Dvine-ai-frontend"), "git pull");
        assertTrue(run.startsWith("cd '/workspace/Dvine-ai-frontend'"));
        assertTrue(run.contains("git pull"));
    }
}
