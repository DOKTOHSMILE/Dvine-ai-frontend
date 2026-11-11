package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class TarGzTest {
    @Test
    public void unpacksAFileAndASymlinkAndSkipsAnEscape() throws Exception {
        byte[] gz = gzip(tar(
                entry("bin/note", "hello", '0', 0755, ""),
                entry("bin/sh", "", '2', 0777, "busybox"),
                entry("../ibiko-tar-escape.txt", "no", '0', 0644, "")));
        File dest = Files.createTempDirectory("ibiko-tar").toFile();
        TarGz.extract(new ByteArrayInputStream(gz), dest);
        assertEquals("hello", new String(Files.readAllBytes(new File(dest, "bin/note").toPath()), StandardCharsets.UTF_8));
        assertTrue(new File(dest, "bin/note").canExecute());
        assertTrue(Files.isSymbolicLink(new File(dest, "bin/sh").toPath()));
        assertEquals("busybox", Files.readSymbolicLink(new File(dest, "bin/sh").toPath()).toString());
        assertFalse(new File(dest.getParentFile(), "ibiko-tar-escape.txt").exists());
        assertFalse(new File(dest, "ibiko-tar-escape.txt").exists());
    }

    @Test
    public void aHardLinkThatArrivesBeforeItsFileIsStillCopied() throws Exception {
        byte[] gz = gzip(tar(
                entry("bin/ls", "", '1', 0755, "bin/busybox"),
                entry("bin/busybox", "box", '0', 0755, "")));
        File dest = Files.createTempDirectory("ibiko-link").toFile();
        TarGz.extract(new ByteArrayInputStream(gz), dest);
        assertEquals("box", new String(Files.readAllBytes(new File(dest, "bin/ls").toPath()), StandardCharsets.UTF_8));
        java.util.List<String> once = ProotCommand.once(new File("/lib/libproot.so"), dest, new File("/work"), "pkg install python3");
        assertTrue(once.contains("-c"));
        assertTrue(once.contains("pkg install python3"));
        assertTrue(once.contains("/work:/work") || once.contains(new File("/work").getAbsolutePath() + ":/work"));
    }

    @Test
    public void thePackageCommandIsApkUnderTheTermuxNames() {
        assertTrue(ProotCommand.PKG.contains("apk add"));
        assertTrue(ProotCommand.PKG.contains("pkg install"));
        assertTrue(ProotCommand.PKG.contains("apk update && apk upgrade"));
        File root = new File("/tmp/rootfs");
        java.util.List<String> line = ProotCommand.line(new File("/lib/libproot.so"), root);
        assertEquals("/lib/libproot.so", line.get(0));
        assertTrue(line.contains("--link2symlink"));
        assertTrue(line.contains(root.getAbsolutePath()));
        assertEquals("/bin/sh", line.get(line.size() - 1));
    }

    private static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    private static byte[] tar(byte[]... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] e : entries) {
            out.write(e, 0, e.length);
        }
        out.write(new byte[1024], 0, 1024);
        return out.toByteArray();
    }

    private static byte[] entry(String name, String body, char type, int mode, String link) {
        byte[] header = new byte[512];
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        put(header, 0, name);
        put(header, 100, Integer.toOctalString(mode));
        put(header, 124, Integer.toOctalString(bytes.length));
        header[156] = (byte) type;
        put(header, 157, link);
        put(header, 257, "ustar");
        byte[] out = new byte[512 + ((bytes.length + 511) / 512) * 512];
        System.arraycopy(header, 0, out, 0, 512);
        System.arraycopy(bytes, 0, out, 512, bytes.length);
        return out;
    }

    private static void put(byte[] header, int at, String text) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, header, at, Math.min(raw.length, header.length - at));
    }
}
