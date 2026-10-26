package ai.dvine.brain;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TimeZone;

public final class TestPack {
    private TestPack() {
    }

    static KnowledgePack load() throws Exception {
        File f = new File("src/main/assets/knowledge/base_knowledge.json");
        return KnowledgePack.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    static OfflineBrain brain(File dir) throws Exception {
        // 2026-10-26 09:30 UTC, a Monday
        final long now = 1793007000000L;
        return new OfflineBrain(load(), new LearningJournal(dir), () -> now, TimeZone.getTimeZone("UTC"));
    }

    /** A brain with a fresh journal, for tests outside this package. */
    public static OfflineBrain brain() {
        try {
            return brain(tempDir());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static File tempDir() throws Exception {
        return Files.createTempDirectory("dvine").toFile();
    }
}
