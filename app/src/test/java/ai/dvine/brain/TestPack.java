package ai.dvine.brain;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TimeZone;

final class TestPack {
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

    static File tempDir() throws Exception {
        return Files.createTempDirectory("dvine").toFile();
    }
}
