package ai.ibiko.learn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A log the brain only adds lines to (answers learned from the models, the learner's journal), kept in IBK codes one
 * line at a time: adding a line writes only that line, packed (format 2), never the whole file again, so a log of many
 * megabytes costs the same to add to as an empty one.
 * <p>
 * File: {@code IBKL}, version 1, the words' order fingerprint, then one record a line: its length (varint), the line's
 * codes, a CRC-32 of the line. A damaged record ends the reading there; what came before is kept. A log written as
 * plain lines (older versions, or before the dictionary is there) is still read, and is converted the first time a line
 * is added with the dictionary open. Pure Java.
 */
public final class CodeLog {
    private CodeLog() {
    }

    static final byte[] HEAD = {'I', 'B', 'K', 'L', 1};

    private static boolean coded(byte[] head) {
        if (head.length < HEAD.length) {
            return false;
        }
        for (int i = 0; i < HEAD.length; i++) {
            if (head[i] != HEAD[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] head(File f, int n) throws IOException {
        byte[] h = new byte[n];
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            int got = in.read(h);
            return got == n ? h : Arrays.copyOf(h, Math.max(0, got));
        }
    }

    /** Whether the file is a coded log made with the words' order in use now. */
    private static boolean current(File f) throws IOException {
        byte[] fp = WordRank.fingerprint();
        if (fp == null || !f.isFile() || f.length() < HEAD.length + 8) {
            return false;
        }
        byte[] h = head(f, HEAD.length + 8);
        return coded(h) && Arrays.equals(fp, Arrays.copyOfRange(h, HEAD.length, HEAD.length + 8));
    }

    /** Adds one line (its ending newline, if any, is not part of it). */
    public static synchronized void append(File f, String line) throws IOException {
        String l = line == null ? "" : line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        WordBook.ensure();
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        if (!WordRank.ready()) {
            if (f.isFile() && f.length() >= 4 && coded(head(f, HEAD.length))) {
                throw new IOException("the log is in codes and the dictionary isn't open");
            }
            Files.write(f.toPath(), (l + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return;
        }
        if (f.isFile() && f.length() > 0 && !current(f)) {
            List<String> all = lines(f); // plain lines (or an older form) become codes, the same lines
            all.add(l);
            write(f, all);
            return;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream(l.length() / 2 + 16);
        if (!f.isFile() || f.length() == 0) {
            o.write(HEAD, 0, HEAD.length);
            byte[] fp = WordRank.fingerprint();
            o.write(fp, 0, fp.length);
        }
        record(o, l);
        Files.write(f.toPath(), o.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void record(ByteArrayOutputStream o, String line) {
        byte[] body = Pack.body(line);
        int crc = Pack.crc(line);
        Pack.varint(o, body.length + 4);
        o.write(body, 0, body.length);
        o.write(crc >>> 24);
        o.write(crc >>> 16);
        o.write(crc >>> 8);
        o.write(crc);
    }

    /** Writes the whole log as these lines (to trim it), beside the file first, then over it. */
    public static synchronized void write(File f, List<String> lines) throws IOException {
        WordBook.ensure();
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        if (WordRank.ready()) {
            o.write(HEAD, 0, HEAD.length);
            byte[] fp = WordRank.fingerprint();
            o.write(fp, 0, fp.length);
            for (String l : lines) {
                record(o, l);
            }
        } else {
            byte[] b = (lines.isEmpty() ? "" : String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
            o.write(b, 0, b.length);
        }
        File part = new File(f.getPath() + ".part");
        Files.write(part.toPath(), o.toByteArray());
        try {
            Files.move(part.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(part.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * The log's lines: from codes, from a whole coded file ({@link CodeFile}), or from plain lines. A log made with
     * another order of words is refused (an error), not read wrongly.
     */
    public static synchronized List<String> lines(File f) throws IOException {
        List<String> out = new ArrayList<String>();
        if (!f.isFile()) {
            return out;
        }
        byte[] b = Files.readAllBytes(f.toPath());
        if (b.length >= 4 && b[0] == 'I' && b[1] == 'B' && b[2] == 'K' && b[3] == 'Z') {
            return CodeFile.lines(f);
        }
        if (!coded(b)) {
            String t = new String(b, StandardCharsets.UTF_8);
            if (t.isEmpty()) {
                return out;
            }
            out.addAll(Arrays.asList(t.split("\n", -1)));
            if (out.get(out.size() - 1).isEmpty()) {
                out.remove(out.size() - 1);
            }
            for (int i = 0; i < out.size(); i++) {
                String l = out.get(i);
                if (l.endsWith("\r")) {
                    out.set(i, l.substring(0, l.length() - 1)); // as readAllLines read them
                }
            }
            return out;
        }
        WordBook.ensure();
        byte[] fp = WordRank.fingerprint();
        if (fp == null || b.length < HEAD.length + 8 || !Arrays.equals(fp, Arrays.copyOfRange(b, HEAD.length, HEAD.length + 8))) {
            throw new IOException("the log was kept with another order of words");
        }
        int at = HEAD.length + 8;
        while (at < b.length) {
            long len = 0;
            int shift = 0, x;
            do {
                if (at >= b.length || shift > 28) {
                    return out; // cut short: what came before is kept
                }
                x = b[at++] & 0xFF;
                len |= (long) (x & 0x7F) << shift;
                shift += 7;
            } while (x >= 0x80);
            int end = at + (int) len;
            if (len < 4 || end > b.length) {
                return out;
            }
            String line;
            try {
                line = Pack.unbody(b, at, end - 4);
            } catch (IOException e) {
                return out;
            }
            int crc = (b[end - 4] & 0xFF) << 24 | (b[end - 3] & 0xFF) << 16 | (b[end - 2] & 0xFF) << 8 | (b[end - 1] & 0xFF);
            if (crc != Pack.crc(line)) {
                return out;
            }
            out.add(line);
            at = end;
        }
        return out;
    }
}
