package ai.dvine.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class LinuxCwdTest {
    @Test
    public void theNextCommandStartsWhereTheLastOneFinished() throws Exception {
        File root = Files.createTempDirectory("dvine-cwd").toFile();
        assertEquals("/root", LinuxCwd.read(root));
        File home = new File(root, "root");
        home.mkdirs();
        Files.write(new File(home, ".dvine_cwd").toPath(), "/usr/bin\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("/usr/bin", LinuxCwd.read(root));
        String script = LinuxCwd.script(root, "ls");
        assertTrue(script.contains("cd '/usr/bin'"));
        assertTrue(script.contains("ls"));
    }
}
