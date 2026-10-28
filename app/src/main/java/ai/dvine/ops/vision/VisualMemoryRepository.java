package ai.dvine.ops.vision;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VisualMemoryRepository {
    public static final class Memory {
        public final String path;
        public final String note;

        public Memory(String path, String note) {
            this.path = path;
            this.note = note;
        }
    }

    private final Map<String, Memory> items = new LinkedHashMap<String, Memory>();
    private String lastPath;

    public void remember(String path, String note) {
        if (path == null) {
            return;
        }
        items.put(path, new Memory(path, note == null ? "" : note));
        lastPath = path;
    }

    public String lastPath() {
        return lastPath;
    }

    public Memory get(String path) {
        return items.get(path);
    }
}
