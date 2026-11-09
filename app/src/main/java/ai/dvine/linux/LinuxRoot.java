package ai.dvine.linux;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Alpine Linux, downloaded once into the app and started with the bundled {@code proot}.
 * Termux is not installed and not opened. {@code pkg} is the same shape of command Termux uses.
 */
public final class LinuxRoot {
    /** Alpine 3.22. apk in 3.24 uses calls this phone rejects, so packages never install. */
    public static final String RELEASE = "3.22.5";
    public static final String ROOTFS_URL =
            "https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/aarch64/alpine-minirootfs-3.22.5-aarch64.tar.gz";
    public static final String PROOT_URL =
            "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot";
    private static final byte[] ELF = new byte[] {0x7f, 'E', 'L', 'F'};

    public interface Progress {
        void say(String line);
    }

    private LinuxRoot() {
    }

    public static File root(Context c) {
        return new File(c.getFilesDir(), "linux/alpine");
    }

    public static boolean ready(File rootfs) {
        return new File(rootfs, "bin/busybox").isFile() || new File(rootfs, "bin/sh").exists();
    }

    /** The proot program. It has to live where the phone allows programs to run. */
    public static File proot(Context c) {
        File nativeOne = new File(c.getApplicationInfo().nativeLibraryDir, "libproot.so");
        if (nativeOne.isFile() && nativeOne.length() > 10000) {
            return nativeOne;
        }
        return new File(c.getFilesDir(), "linux/libproot.so");
    }

    public static File loader(Context c) {
        File nativeOne = new File(c.getApplicationInfo().nativeLibraryDir, "libproot_loader.so");
        if (nativeOne.isFile() && nativeOne.length() > 100) {
            return nativeOne;
        }
        return new File(c.getFilesDir(), "linux/libproot_loader.so");
    }

    public static void install(Context c, Progress progress) throws IOException {
        File rootfs = root(c);
        File stamp = new File(rootfs, "etc/dvine-release");
        if (ready(rootfs) && new File(rootfs, "usr/local/bin/pkg").isFile() && same(stamp, RELEASE)) {
            refreshDns(c);
            return;
        }
        boolean replacing = ready(rootfs);
        say(progress, replacing
                ? "Replacing Linux with Alpine 3.22 so pkg can install packages on this phone."
                : "Preparing Linux. The first time downloads about 4 MB.");
        ensureProot(c, progress);
        File gz = new File(c.getCacheDir(), "alpine-minirootfs.tar.gz");
        say(progress, "Downloading Alpine Linux…");
        download(ROOTFS_URL, gz, progress);
        if (rootfs.exists()) {
            deleteTree(rootfs);
        }
        rootfs.mkdirs();
        say(progress, "Unpacking Linux…");
        try (InputStream in = new FileInputStream(gz)) {
            TarGz.extract(in, rootfs);
        }
        gz.delete();
        if (!ready(rootfs)) {
            throw new IOException("Linux unpacked, but its shell is missing.");
        }
        File bin = new File(rootfs, "usr/local/bin");
        bin.mkdirs();
        File pkg = new File(bin, "pkg");
        write(new File(bin, "pkg"), ProotCommand.PKG);
        pkg.setExecutable(true, false);
        write(new File(rootfs, "etc/apk/repositories"), repositories());
        write(stamp, RELEASE + "\n");
        refreshDns(c);
        say(progress, "Linux is ready.");
    }

    /** Plain http. apk still checks the package signature. https fails on this phone with "Function not implemented". */
    static String repositories() {
        return "http://dl-cdn.alpinelinux.org/alpine/v3.22/main\n"
                + "http://dl-cdn.alpinelinux.org/alpine/v3.22/community\n";
    }

    /** The phone's own DNS first. Public servers are only a fallback, and many networks block them. */
    public static void refreshDns(Context c) {
        if (c == null) {
            return;
        }
        File conf = new File(root(c), "etc/resolv.conf");
        File parent = conf.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm == null ? null : cm.getActiveNetwork();
            LinkProperties link = net == null || cm == null ? null : cm.getLinkProperties(net);
            if (link != null) {
                for (InetAddress address : link.getDnsServers()) {
                    String host = address.getHostAddress();
                    if (host == null || host.indexOf(':') >= 0 || text.length() > 240) {
                        continue;
                    }
                    text.append("nameserver ").append(host).append('\n');
                }
            }
        } catch (Throwable ignored) {
            // keep the public servers below
        }
        if (text.indexOf("8.8.8.8") < 0) {
            text.append("nameserver 8.8.8.8\n");
        }
        if (text.indexOf("1.1.1.1") < 0) {
            text.append("nameserver 1.1.1.1\n");
        }
        try {
            write(conf, text.toString());
        } catch (IOException ignored) {
            // a command can still run; only package installs need this file
        }
    }

    private static boolean same(File stamp, String want) {
        try {
            String got = new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).trim();
            return want.equals(got);
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureProot(Context c, Progress progress) throws IOException {
        File have = proot(c);
        if (have.isFile() && have.length() > 10000 && have.getAbsolutePath().contains("native")) {
            carveIfNeeded(c, have);
            return;
        }
        if (have.isFile() && have.length() > 10000) {
            carveIfNeeded(c, have);
            return;
        }
        say(progress, "Downloading the Linux starter…");
        File dir = new File(c.getFilesDir(), "linux");
        dir.mkdirs();
        File to = new File(dir, "libproot.so");
        download(PROOT_URL, to, null);
        to.setExecutable(true, false);
        carveIfNeeded(c, to);
    }

    /** The starter carries its loader inside. Pull that out when the build did not ship it separately. */
    static void carveIfNeeded(Context c, File prootBin) throws IOException {
        File dest = loader(c);
        if (dest.isFile() && dest.length() > 100 && dest.getAbsolutePath().contains(c.getApplicationInfo().nativeLibraryDir)) {
            return;
        }
        if (new File(c.getApplicationInfo().nativeLibraryDir, "libproot_loader.so").isFile()) {
            return;
        }
        byte[] data = Files.readAllBytes(prootBin.toPath());
        int first = indexOf(data, ELF, 1);
        int second = first < 0 ? -1 : indexOf(data, ELF, first + 4);
        if (first < 0 || second <= first) {
            return;
        }
        File out = new File(c.getFilesDir(), "linux/libproot_loader.so");
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), java.util.Arrays.copyOfRange(data, first, second));
        out.setExecutable(true, false);
    }

    private static int indexOf(byte[] data, byte[] needle, int from) {
        for (int i = Math.max(0, from); i + needle.length <= data.length; i++) {
            boolean ok = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return i;
            }
        }
        return -1;
    }

    private static void download(String url, File to, Progress progress) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "DVINE");
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Download failed (" + code + ").");
        }
        long len = conn.getContentLengthLong();
        File tmp = new File(to.getAbsolutePath() + ".part");
        tmp.getParentFile().mkdirs();
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            long got = 0;
            int last = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                if (len > 0 && progress != null) {
                    int pct = (int) (got * 100 / len);
                    if (pct >= last + 10) {
                        last = pct;
                        progress.say("Downloading Linux… " + pct + "%");
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        if (to.exists() && !to.delete()) {
            throw new IOException("Couldn't replace " + to.getName());
        }
        if (!tmp.renameTo(to)) {
            throw new IOException("Couldn't save " + to.getName());
        }
    }

    private static void write(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteTree(File root) throws IOException {
        if (!root.exists()) {
            return;
        }
        java.util.List<java.nio.file.Path> all = new java.util.ArrayList<java.nio.file.Path>();
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(root.toPath())) {
            walk.forEach(all::add);
        }
        java.util.Collections.reverse(all);
        for (java.nio.file.Path path : all) {
            Files.deleteIfExists(path);
        }
    }

    private static void say(Progress progress, String line) {
        if (progress != null) {
            progress.say(line);
        }
    }
}
