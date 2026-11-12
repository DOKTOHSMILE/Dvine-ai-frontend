package ai.ibiko.learn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A file of the brain kept in codes: its text packed by word id ({@link Pack}) and squeezed, about a third of the
 * size of the words. What the brain knows (answers learned, facts, what things mean) is kept this way; it reads back
 * as exactly the same text.
 * <p>
 * A file written as plain words (by an older version, or before the dictionary was there) is still read: the first
 * bytes say which it is. A coded file that can't be read (another dictionary, damage) is never guessed at: it is
 * copied aside as {@code <name>.unreadable} and reported, so nothing is overwritten with emptiness.
 */
public final class CodeFile {
    private CodeFile() {
    }

    static final byte[] MAGIC = {'I', 'B', 'K', 'Z'};

    /** The bytes a text is kept as. Plain words when there is no dictionary to code with. */
    public static byte[] encode(String text) {
        if (!WordBook.ready() && !WordBook.ensure()) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        byte[] pack = Pack.pack(text);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(pack);
        d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream(pack.length / 2 + 16);
        o.write(MAGIC, 0, MAGIC.length);
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            o.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return o.toByteArray();
    }

    /** The text these bytes stand for (coded, or plain words). */
    public static String decode(byte[] b) throws IOException {
        if (b.length < 4 || b[0] != 'I' || b[1] != 'B' || b[2] != 'K' || b[3] != 'Z') {
            return new String(b, StandardCharsets.UTF_8);
        }
        WordBook.ensure();
        Inflater in = new Inflater();
        in.setInput(b, 4, b.length - 4);
        ByteArrayOutputStream o = new ByteArrayOutputStream(b.length * 3);
        byte[] buf = new byte[8192];
        try {
            while (!in.finished()) {
                int n = in.inflate(buf);
                if (n == 0 && (in.needsInput() || in.needsDictionary())) {
                    throw new IOException("the coded file is cut short");
                }
                o.write(buf, 0, n);
            }
        } catch (DataFormatException e) {
            throw new IOException("the coded file is damaged");
        } finally {
            in.end();
        }
        return Pack.unpack(o.toByteArray());
    }

    /** Writes a text as codes, beside the file first and then moved over it, so a crash never leaves half a file. */
    public static void write(File f, String text) throws IOException {
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        File part = new File(f.getPath() + ".part");
        Files.write(part.toPath(), encode(text));
        try {
            Files.move(part.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(part.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Reads a file's text. One that can't be read is copied aside before the error is thrown. */
    public static String read(File f) throws IOException {
        byte[] b = Files.readAllBytes(f.toPath());
        try {
            return decode(b);
        } catch (IOException e) {
            try {
                Files.copy(f.toPath(), new File(f.getPath() + ".unreadable").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException kept) {
                // nothing more to do
            }
            throw e;
        }
    }

    /** The file's lines, as {@code Files.readAllLines} gives them. */
    public static List<String> lines(File f) throws IOException {
        String t = read(f);
        if (t.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> out = new ArrayList<String>(Arrays.asList(t.split("\n", -1)));
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
