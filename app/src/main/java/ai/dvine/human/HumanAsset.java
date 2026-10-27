package ai.dvine.human;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

import ai.dvine.human.mesh.TriMesh;

/**
 * One digital human, ready to animate: a skinned triangle mesh (head, body,
 * hands, eyes, teeth) plus line-only vertices for feature lines and hair,
 * bind joints, facial blend shapes and named landmarks. Saved as a small
 * binary file with a CRC so a damaged asset is detected, never drawn.
 */
public final class HumanAsset {
    public static final int FORMAT = 3;
    private static final int MAGIC = 0x4456484D; // "DVHM"

    public boolean female;
    /** Content version: bumped whenever the asset is rebuilt from a changed recipe. */
    public int version;
    public TriMesh mesh;
    /** Extra line segments (feature lines, iris rings, hair), vertex index pairs. */
    public int[] lines = new int[0];
    /** Hair sway amplitude per vertex (0 for everything else). */
    public float[] sway;
    /** Bind joint positions, xyz per bone. */
    public float[] joints;
    /** Vertex ranges of the two eyes (start, count) and their centres (body units). */
    public int[] eyeRange = new int[4];
    public float[] eyeCenter = new float[6];
    /** Vertex ranges of the iris and pupil rings (start, count per eye); they turn with the eyes. */
    public int[] irisRange = new int[4];
    public Morph[] morphs = new Morph[0];
    public String[] landmarkNames = new String[0];
    public int[] landmarkVertex = new int[0];
    /**
     * The inside of the body for the anatomical view (format 2): one mesh
     * (region = part index, bone0 = the bone it rides), and per part its name
     * and layer (skeleton, muscles, organs, vessels). Visual only.
     */
    public TriMesh inner;
    public String[] innerNames = new String[0];
    public byte[] innerLayer = new byte[0];
    /**
     * The head's frame (format 3): origin x, y, z and scale x, y, z mapping
     * the standard head layout onto this head (see build.HeadFrame). Older
     * assets used the fixed sculpted head.
     */
    public float[] headFrame = {0f, 0.66f, 0f, 0.34f, 0.34f, 0.34f};
    /** CRC32 of the payload, filled in by {@link #write} and checked by {@link #read}. */
    public long checksum;

    /** A facial blend shape: sparse vertex offsets in body units. */
    public static final class Morph {
        public final String name;
        public final int[] index;
        public final float[] delta;

        public Morph(String name, int[] index, float[] delta) {
            this.name = name;
            this.index = index;
            this.delta = delta;
        }
    }

    public int morph(String name) {
        for (int i = 0; i < morphs.length; i++) {
            if (morphs[i].name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public int landmark(String name) {
        for (int i = 0; i < landmarkNames.length; i++) {
            if (landmarkNames[i].equals(name)) {
                return landmarkVertex[i];
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ file format

    public byte[] write() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(body);
        o.writeBoolean(female);
        o.writeInt(version);
        floats(o, mesh.pos);
        ints(o, mesh.tri);
        ints(o, mesh.edge);
        bytes(o, mesh.region);
        bytes(o, mesh.bone0);
        bytes(o, mesh.bone1);
        floats(o, mesh.weight0);
        ints(o, lines);
        floats(o, sway);
        floats(o, joints);
        ints(o, eyeRange);
        floats(o, eyeCenter);
        ints(o, irisRange);
        o.writeInt(morphs.length);
        for (Morph m : morphs) {
            o.writeUTF(m.name);
            ints(o, m.index);
            floats(o, m.delta);
        }
        o.writeInt(landmarkNames.length);
        for (int i = 0; i < landmarkNames.length; i++) {
            o.writeUTF(landmarkNames[i]);
            o.writeInt(landmarkVertex[i]);
        }
        o.writeBoolean(inner != null);
        if (inner != null) {
            floats(o, inner.pos);
            ints(o, inner.tri);
            bytes(o, inner.region);
            bytes(o, inner.bone0);
            o.writeInt(innerNames.length);
            for (String n : innerNames) {
                o.writeUTF(n);
            }
            bytes(o, innerLayer);
        }
        floats(o, headFrame);
        o.flush();
        byte[] payload = body.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        checksum = crc.getValue();
        ByteArrayOutputStream file = new ByteArrayOutputStream(payload.length + 20);
        DataOutputStream f = new DataOutputStream(file);
        f.writeInt(MAGIC);
        f.writeInt(FORMAT);
        f.writeLong(checksum);
        f.writeInt(payload.length);
        f.write(payload);
        f.flush();
        return file.toByteArray();
    }

    /** Reads and verifies an asset. Throws if the file is damaged or from another format. */
    public static HumanAsset read(InputStream in) throws IOException {
        DataInputStream f = new DataInputStream(in);
        if (f.readInt() != MAGIC) {
            throw new IOException("not a DVINE human asset");
        }
        int format = f.readInt();
        if (format < 1 || format > FORMAT) {
            throw new IOException("unsupported asset format");
        }
        long sum = f.readLong();
        int len = f.readInt();
        if (len < 0 || len > 64 * 1024 * 1024) {
            throw new IOException("bad asset length");
        }
        byte[] payload = new byte[len];
        f.readFully(payload);
        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != sum) {
            throw new IOException("asset checksum mismatch");
        }
        DataInputStream o = new DataInputStream(new java.io.ByteArrayInputStream(payload));
        HumanAsset a = new HumanAsset();
        a.checksum = sum;
        a.female = o.readBoolean();
        a.version = o.readInt();
        TriMesh m = new TriMesh();
        m.pos = floats(o);
        m.tri = ints(o);
        m.edge = ints(o);
        m.region = bytes(o);
        m.bone0 = bytes(o);
        m.bone1 = bytes(o);
        m.weight0 = floats(o);
        a.mesh = m;
        a.lines = ints(o);
        a.sway = floats(o);
        a.joints = floats(o);
        a.eyeRange = ints(o);
        a.eyeCenter = floats(o);
        a.irisRange = ints(o);
        int nm = o.readInt();
        a.morphs = new Morph[nm];
        for (int i = 0; i < nm; i++) {
            a.morphs[i] = new Morph(o.readUTF(), ints(o), floats(o));
        }
        int nl = o.readInt();
        a.landmarkNames = new String[nl];
        a.landmarkVertex = new int[nl];
        for (int i = 0; i < nl; i++) {
            a.landmarkNames[i] = o.readUTF();
            a.landmarkVertex[i] = o.readInt();
        }
        if (format >= 2 && o.readBoolean()) {
            TriMesh in2 = new TriMesh();
            in2.pos = floats(o);
            in2.tri = ints(o);
            in2.region = bytes(o);
            in2.bone0 = bytes(o);
            int n = in2.pos.length / 3;
            if (in2.region.length != n || in2.bone0.length != n) {
                throw new IOException("bad inner anatomy");
            }
            for (int t : in2.tri) {
                if (t < 0 || t >= n) {
                    throw new IOException("bad inner anatomy");
                }
            }
            in2.bone1 = in2.bone0.clone();
            in2.weight0 = new float[n];
            java.util.Arrays.fill(in2.weight0, 1f);
            in2.computeNormals();
            int np = o.readInt();
            a.innerNames = new String[np];
            for (int i = 0; i < np; i++) {
                a.innerNames[i] = o.readUTF();
            }
            a.innerLayer = bytes(o);
            a.inner = in2;
        }
        if (format >= 3) {
            a.headFrame = floats(o);
            if (a.headFrame == null || a.headFrame.length < 6 || a.headFrame.length > 7 || !(a.headFrame[3] > 0f && a.headFrame[4] > 0f && a.headFrame[5] > 0f)) {
                throw new IOException("bad head frame");
            }
        }
        m.computeNormals();
        return a;
    }

    private static void floats(DataOutputStream o, float[] a) throws IOException {
        o.writeInt(a == null ? -1 : a.length);
        if (a != null) {
            for (float v : a) {
                o.writeFloat(v);
            }
        }
    }

    private static void ints(DataOutputStream o, int[] a) throws IOException {
        o.writeInt(a == null ? -1 : a.length);
        if (a != null) {
            for (int v : a) {
                o.writeInt(v);
            }
        }
    }

    private static void bytes(DataOutputStream o, byte[] a) throws IOException {
        o.writeInt(a == null ? -1 : a.length);
        if (a != null) {
            o.write(a);
        }
    }

    private static float[] floats(DataInputStream o) throws IOException {
        int n = o.readInt();
        if (n < 0) {
            return null;
        }
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = o.readFloat();
        }
        return a;
    }

    private static int[] ints(DataInputStream o) throws IOException {
        int n = o.readInt();
        if (n < 0) {
            return null;
        }
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = o.readInt();
        }
        return a;
    }

    private static byte[] bytes(DataInputStream o) throws IOException {
        int n = o.readInt();
        if (n < 0) {
            return null;
        }
        byte[] a = new byte[n];
        o.readFully(a);
        return a;
    }
}
