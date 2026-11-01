package ai.dvine.ops.shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A few Linux-shaped commands run inside the app. The Termux window is not opened.
 * A request that is not one of these commands returns null, so ordinary talk is left alone.
 */
public final class LocalShell {
    private LocalShell() {
    }

    public static String run(String utterance, List<File> roots, File work) {
        String said = utterance == null ? "" : utterance.trim().toLowerCase(Locale.US);
        if (said.isEmpty()) {
            return null;
        }
        boolean unzip = said.matches(".*(unzip|extract|unpack).*") && said.contains("zip");
        boolean find = said.matches(".*(find|search|where|look for|locate).*") && said.contains("zip");
        boolean list = said.matches("^(ls|list( the)? files|what('s| is) (in )?(here|the folder|the shell))[.!?]*$")
                || said.contains("list the files");
        if (!unzip && !find && !list) {
            return null;
        }
        if (work != null) {
            work.mkdirs();
        }
        if (list) {
            return list(work);
        }
        List<File> zips = findZips(roots);
        if (zips.isEmpty()) {
            return "I searched this app's folders and didn't find a zip file. "
                    + "Put the zip in Downloads or send it here, then ask again. I didn't open Termux.";
        }
        File chosen = pick(zips, said);
        if (find && !unzip) {
            StringBuilder b = new StringBuilder();
            b.append("find . -name '*.zip'\n");
            for (File z : zips) {
                b.append(z.getName()).append('\n');
            }
            b.append("I found ").append(zips.size()).append(zips.size() == 1 ? " zip" : " zips")
                    .append(" in this app. I didn't open Termux.");
            return fence(b.toString().trim());
        }
        File dest = new File(work == null ? chosen.getParentFile() : work,
                "unpacked/" + chosen.getName().replaceAll("(?i)\\.zip$", ""));
        int n;
        try {
            n = unzip(chosen, dest);
        } catch (IOException e) {
            return "unzip " + chosen.getName() + " failed. I didn't open Termux.";
        }
        StringBuilder b = new StringBuilder();
        b.append("unzip ").append(chosen.getName()).append('\n');
        File[] out = dest.listFiles();
        if (out != null) {
            for (File f : out) {
                b.append(f.getName()).append('\n');
            }
        }
        b.append("Unzipped ").append(n).append(n == 1 ? " file" : " files")
                .append(" from ").append(chosen.getName()).append(" into this chat's folder. I didn't open Termux.");
        return fence(b.toString().trim());
    }

    private static String list(File work) {
        StringBuilder b = new StringBuilder("ls\n");
        File[] files = work == null ? null : work.listFiles();
        if (files == null || files.length == 0) {
            b.append("(empty)");
        } else {
            for (File f : files) {
                b.append(f.getName()).append('\n');
            }
        }
        return fence(b.toString().trim());
    }

    private static String fence(String body) {
        return "```\n" + body + "\n```";
    }

    static List<File> findZips(List<File> roots) {
        List<File> found = new ArrayList<File>();
        if (roots == null) {
            return found;
        }
        for (File root : roots) {
            walk(root, 0, found);
        }
        Collections.sort(found, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return found;
    }

    private static void walk(File dir, int depth, List<File> found) {
        if (dir == null || !dir.isDirectory() || depth > 5 || found.size() > 40) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (f.isDirectory()) {
                walk(f, depth + 1, found);
            } else if (f.getName().toLowerCase(Locale.US).endsWith(".zip")) {
                found.add(f);
            }
        }
    }

    private static File pick(List<File> zips, String said) {
        for (File z : zips) {
            String name = z.getName().toLowerCase(Locale.US);
            if (said.contains(name) || said.contains(name.replace(".zip", ""))) {
                return z;
            }
        }
        return zips.get(0);
    }

    static int unzip(File zip, File dest) throws IOException {
        dest.mkdirs();
        int n = 0;
        byte[] buf = new byte[8192];
        try (ZipInputStream in = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..") || name.startsWith("/")) {
                    continue;
                }
                File out = new File(dest, name);
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath())) {
                    continue;
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int r;
                    int total = 0;
                    while ((r = in.read(buf)) > 0) {
                        total += r;
                        if (total > 20 * 1024 * 1024) {
                            break;
                        }
                        fos.write(buf, 0, r);
                    }
                }
                n++;
                if (n >= 200) {
                    break;
                }
            }
        }
        return n;
    }

    static void writeZip(File dest, String entryName, byte[] bytes) throws IOException {
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(dest))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(bytes);
            out.closeEntry();
        }
    }
}
