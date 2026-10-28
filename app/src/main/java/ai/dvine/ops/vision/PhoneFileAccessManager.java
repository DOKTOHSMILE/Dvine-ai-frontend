package ai.dvine.ops.vision;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Walks only a directory the user authorized. Depth and count are capped. */
public final class PhoneFileAccessManager {
    public static final class ImageFile {
        public final File file;
        public final int score;

        public ImageFile(File file, int score) {
            this.file = file;
            this.score = score;
        }
    }

    private final int maxFiles;
    private final int maxDepth;

    public PhoneFileAccessManager() {
        this(200, 4);
    }

    public PhoneFileAccessManager(int maxFiles, int maxDepth) {
        this.maxFiles = maxFiles;
        this.maxDepth = maxDepth;
    }

    public List<ImageFile> searchImages(File root, String query) {
        List<ImageFile> out = new ArrayList<ImageFile>();
        if (root == null || !root.isDirectory()) {
            return out;
        }
        String q = query == null ? "" : query.toLowerCase(Locale.US);
        walk(root, 0, q, out);
        out.sort(Comparator.comparingInt((ImageFile i) -> i.score).reversed());
        return out;
    }

    public byte[] readSmall(File file, int maxBytes) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            byte[] all = Files.readAllBytes(file.toPath());
            if (all.length > maxBytes) {
                byte[] cut = new byte[maxBytes];
                System.arraycopy(all, 0, cut, 0, maxBytes);
                return cut;
            }
            return all;
        } catch (IOException e) {
            return null;
        }
    }

    private void walk(File dir, int depth, String query, List<ImageFile> out) {
        if (depth > maxDepth || out.size() >= maxFiles) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (out.size() >= maxFiles) {
                return;
            }
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    walk(f, depth + 1, query, out);
                }
            } else if (isImageName(f.getName())) {
                out.add(new ImageFile(f, score(f.getName(), query)));
            }
        }
    }

    static boolean isImageName(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    static int score(String name, String query) {
        String n = name.toLowerCase(Locale.US);
        if (query.isEmpty()) {
            return 1;
        }
        int s = 0;
        for (String w : query.split("[^a-z0-9]+")) {
            if (w.length() < 3) {
                continue;
            }
            if (n.contains(w)) {
                s += 5;
            }
            if ((w.equals("motorcycle") || w.equals("bike")) && (n.contains("motorcycle") || n.contains("bike") || n.contains("moto"))) {
                s += 8;
            }
            if (w.equals("vehicle") && (n.contains("car") || n.contains("motor") || n.contains("bike") || n.contains("vehicle"))) {
                s += 4;
            }
        }
        return s;
    }
}
