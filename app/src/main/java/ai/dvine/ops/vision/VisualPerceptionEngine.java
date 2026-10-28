package ai.dvine.ops.vision;

import java.io.File;
import java.util.List;

public final class VisualPerceptionEngine {
    private final PhoneFileAccessManager files;
    private final ImageContentAnalyzer analyzer = new ImageContentAnalyzer();
    private final VisualMemoryRepository memory = new VisualMemoryRepository();

    public VisualPerceptionEngine(PhoneFileAccessManager files) {
        this.files = files == null ? new PhoneFileAccessManager() : files;
    }

    public ImageContentAnalyzer analyzer() {
        return analyzer;
    }

    public VisualMemoryRepository memory() {
        return memory;
    }

    public PhoneFileAccessManager files() {
        return files;
    }

    public List<PhoneFileAccessManager.ImageFile> find(File root, String query) {
        return files.searchImages(root, query);
    }

    public ImageContentAnalyzer.Report inspect(File file) {
        byte[] bytes = files.readSmall(file, 2_000_000);
        ImageContentAnalyzer.Report report = analyzer.analyze(file.getName(), bytes);
        memory.remember(file.getAbsolutePath(), report.note);
        return report;
    }
}
