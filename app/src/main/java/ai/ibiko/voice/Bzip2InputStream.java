package ai.ibiko.voice;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a .bz2 stream (the voice packs come as .tar.bz2, and Android has no bzip2 of its own). The format as the
 * reference decoder reads it: Huffman-coded move-to-front symbols, the inverse Burrows-Wheeler transform, then the
 * run-length step; each block's CRC is checked, so a damaged download fails instead of unpacking garbage. Several
 * streams one after another are read as one. Pure Java.
 */
public final class Bzip2InputStream extends InputStream {
    private static final int MAX_GROUPS = 6, MAX_ALPHA = 258, MAX_CODE_LEN = 23, GROUP_SIZE = 50, MAX_SELECTORS = 18002;
    private static final int[] CRC = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int c = i << 24;
            for (int k = 0; k < 8; k++) {
                c = (c & 0x80000000) != 0 ? (c << 1) ^ 0x04c11db7 : c << 1;
            }
            CRC[i] = c;
        }
    }

    private final InputStream in;
    private long buf;
    private int live;
    private int level;
    private int[] tt;
    private int nblock, k, tPos;
    private int blockCrc, crc;
    private int lastCh = -1, count, repeatLeft, runCh;
    private boolean ended;

    public Bzip2InputStream(InputStream source) throws IOException {
        in = source instanceof BufferedInputStream ? source : new BufferedInputStream(source, 65536);
        if (!header(true)) {
            throw new IOException("Not a bzip2 file.");
        }
        nextBlock();
    }

    /** "BZh1".."BZh9"; false at the end of the input (only allowed after the first stream). */
    private boolean header(boolean first) throws IOException {
        live = 0;
        int b = in.read();
        if (b < 0 && !first) {
            return false;
        }
        if (b != 'B' || in.read() != 'Z' || in.read() != 'h') {
            if (first) {
                throw new IOException("Not a bzip2 file.");
            }
            return false; // something after the last stream: ignored, as bzip2 does
        }
        int l = in.read() - '0';
        if (l < 1 || l > 9) {
            throw new IOException("Bad bzip2 block size.");
        }
        level = l;
        if (tt == null || tt.length < l * 100000) {
            tt = new int[l * 100000];
        }
        return true;
    }

    private int bits(int n) throws IOException {
        while (live < n) {
            int c = in.read();
            if (c < 0) {
                throw new IOException("The bzip2 data ends too soon.");
            }
            buf = (buf << 8) | c;
            live += 8;
        }
        live -= n;
        return (int) ((buf >>> live) & ((1L << n) - 1));
    }

    private boolean bit() throws IOException {
        return bits(1) != 0;
    }

    /** Reads and decodes the next block; false when the stream (and any after it) has ended. */
    private boolean nextBlock() throws IOException {
        while (true) {
            long magic = ((long) bits(24) << 24) | bits(24);
            if (magic == 0x177245385090L) {
                bits(16);
                bits(16); // the whole stream's CRC: every block was checked already
                if (!header(false)) {
                    ended = true;
                    return false;
                }
                continue;
            }
            if (magic != 0x314159265359L) {
                throw new IOException("Bad bzip2 block.");
            }
            break;
        }
        blockCrc = (bits(16) << 16) | bits(16);
        if (bit()) {
            throw new IOException("Randomised bzip2 blocks are not supported.");
        }
        int origPtr = bits(24);
        // the bytes in use
        int[] seqToUnseq = new int[256];
        int nInUse = 0;
        int used16 = bits(16);
        for (int i = 0; i < 16; i++) {
            if ((used16 & (0x8000 >>> i)) != 0) {
                int u = bits(16);
                for (int j = 0; j < 16; j++) {
                    if ((u & (0x8000 >>> j)) != 0) {
                        seqToUnseq[nInUse++] = i * 16 + j;
                    }
                }
            }
        }
        if (nInUse == 0) {
            throw new IOException("Bad bzip2 block.");
        }
        int alphaSize = nInUse + 2;
        int nGroups = bits(3);
        if (nGroups < 2 || nGroups > MAX_GROUPS) {
            throw new IOException("Bad bzip2 block.");
        }
        int nSelectors = bits(15);
        if (nSelectors < 1) {
            throw new IOException("Bad bzip2 block.");
        }
        byte[] selector = new byte[Math.min(nSelectors, MAX_SELECTORS)];
        byte[] pos = new byte[MAX_GROUPS];
        for (int v = 0; v < nGroups; v++) {
            pos[v] = (byte) v;
        }
        for (int i = 0; i < nSelectors; i++) {
            int j = 0;
            while (bit()) {
                if (++j >= nGroups) {
                    throw new IOException("Bad bzip2 block.");
                }
            }
            byte tmp = pos[j];
            while (j > 0) {
                pos[j] = pos[j - 1];
                j--;
            }
            pos[0] = tmp;
            if (i < MAX_SELECTORS) {
                selector[i] = tmp;
            }
        }
        nSelectors = Math.min(nSelectors, MAX_SELECTORS);
        // the code lengths, and the decode tables made from them
        int[][] limit = new int[nGroups][MAX_CODE_LEN + 2], base = new int[nGroups][MAX_CODE_LEN + 2], perm = new int[nGroups][MAX_ALPHA];
        int[] minLens = new int[nGroups];
        int[] len = new int[MAX_ALPHA];
        for (int t = 0; t < nGroups; t++) {
            int curr = bits(5);
            int min = 32, max = 0;
            for (int i = 0; i < alphaSize; i++) {
                while (true) {
                    if (curr < 1 || curr > 20) {
                        throw new IOException("Bad bzip2 block.");
                    }
                    if (!bit()) {
                        break;
                    }
                    curr += bit() ? -1 : 1;
                }
                len[i] = curr;
                min = Math.min(min, curr);
                max = Math.max(max, curr);
            }
            tables(limit[t], base[t], perm[t], len, min, max, alphaSize);
            minLens[t] = min;
        }
        // the symbols: move-to-front indices and runs, into tt with the byte counts
        int eob = nInUse + 1;
        int nblockMax = level * 100000;
        int[] unzftab = new int[256];
        int[] yy = new int[256];
        for (int i = 0; i < 256; i++) {
            yy[i] = i;
        }
        int groupNo = -1, groupPos = 0;
        int[] gLimit = null, gBase = null, gPerm = null;
        int gMin = 0;
        nblock = 0;
        int sym;
        // one symbol at a time
        while (true) {
            if (groupPos == 0) {
                if (++groupNo >= nSelectors) {
                    throw new IOException("Bad bzip2 block.");
                }
                groupPos = GROUP_SIZE;
                int g = selector[groupNo];
                gLimit = limit[g];
                gBase = base[g];
                gPerm = perm[g];
                gMin = minLens[g];
            }
            groupPos--;
            int zn = gMin, zvec = bits(zn);
            while (zvec > gLimit[zn]) {
                if (++zn > 20) {
                    throw new IOException("Bad bzip2 block.");
                }
                zvec = (zvec << 1) | bits(1);
            }
            int at = zvec - gBase[zn];
            if (at < 0 || at >= MAX_ALPHA) {
                throw new IOException("Bad bzip2 block.");
            }
            sym = gPerm[at];
            if (sym == eob) {
                break;
            }
            if (sym <= 1) {
                // RUNA and RUNB: a run of the byte at the front of the list, its length in bijective base 2
                int es = 0, n = 1;
                do {
                    es += (sym + 1) * n;
                    n <<= 1;
                    if (n > (1 << 21)) {
                        throw new IOException("Bad bzip2 block.");
                    }
                    if (groupPos == 0) {
                        if (++groupNo >= nSelectors) {
                            throw new IOException("Bad bzip2 block.");
                        }
                        groupPos = GROUP_SIZE;
                        int g = selector[groupNo];
                        gLimit = limit[g];
                        gBase = base[g];
                        gPerm = perm[g];
                        gMin = minLens[g];
                    }
                    groupPos--;
                    zn = gMin;
                    zvec = bits(zn);
                    while (zvec > gLimit[zn]) {
                        if (++zn > 20) {
                            throw new IOException("Bad bzip2 block.");
                        }
                        zvec = (zvec << 1) | bits(1);
                    }
                    at = zvec - gBase[zn];
                    if (at < 0 || at >= MAX_ALPHA) {
                        throw new IOException("Bad bzip2 block.");
                    }
                    sym = gPerm[at];
                } while (sym <= 1);
                int uc = seqToUnseq[yy[0]];
                unzftab[uc] += es;
                if (nblock + es > nblockMax) {
                    throw new IOException("Bad bzip2 block.");
                }
                for (int i = 0; i < es; i++) {
                    tt[nblock++] = uc;
                }
                if (sym == eob) {
                    break;
                }
            }
            // a move-to-front index
            int nn = sym - 1;
            int v = yy[nn];
            System.arraycopy(yy, 0, yy, 1, nn);
            yy[0] = v;
            int uc = seqToUnseq[v];
            unzftab[uc]++;
            if (nblock >= nblockMax) {
                throw new IOException("Bad bzip2 block.");
            }
            tt[nblock++] = uc;
        }
        if (origPtr < 0 || origPtr >= Math.max(1, nblock)) {
            throw new IOException("Bad bzip2 block.");
        }
        // the inverse Burrows-Wheeler transform: each entry keeps its byte below and its successor above
        int[] cftab = new int[257];
        for (int i = 0; i < 256; i++) {
            cftab[i + 1] = cftab[i] + unzftab[i];
        }
        for (int i = 0; i < nblock; i++) {
            int uc = tt[i] & 0xff;
            tt[cftab[uc]++] |= i << 8;
        }
        tPos = tt[origPtr] >>> 8;
        k = 0;
        crc = 0xffffffff;
        lastCh = -1;
        count = 0;
        repeatLeft = 0;
        return true;
    }

    private static void tables(int[] limit, int[] base, int[] perm, int[] len, int min, int max, int alphaSize) {
        int pp = 0;
        for (int i = min; i <= max; i++) {
            for (int j = 0; j < alphaSize; j++) {
                if (len[j] == i) {
                    perm[pp++] = j;
                }
            }
        }
        java.util.Arrays.fill(base, 0);
        for (int i = 0; i < alphaSize; i++) {
            base[len[i] + 1]++;
        }
        for (int i = 1; i < base.length; i++) {
            base[i] += base[i - 1];
        }
        java.util.Arrays.fill(limit, 0);
        int vec = 0;
        for (int i = min; i <= max; i++) {
            vec += base[i + 1] - base[i];
            limit[i] = vec - 1;
            vec <<= 1;
        }
        for (int i = min + 1; i <= max; i++) {
            base[i] = ((limit[i - 1] + 1) << 1) - base[i];
        }
    }

    private void endBlock() throws IOException {
        if (~crc != blockCrc) {
            throw new IOException("The bzip2 data is damaged (checksum).");
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (ended) {
            return -1;
        }
        int n = 0;
        while (n < len) {
            int ch;
            if (repeatLeft > 0) {
                repeatLeft--;
                ch = runCh;
            } else {
                if (k == nblock) {
                    endBlock();
                    if (!nextBlock()) {
                        break;
                    }
                    continue;
                }
                tPos = tt[tPos];
                ch = tPos & 0xff;
                tPos >>>= 8;
                k++;
                if (count == 4) {
                    // the byte after four equal ones says how many more of them follow
                    repeatLeft = ch;
                    runCh = lastCh;
                    count = 0;
                    lastCh = -1;
                    continue;
                }
                if (ch == lastCh) {
                    count++;
                } else {
                    lastCh = ch;
                    count = 1;
                }
            }
            crc = (crc << 8) ^ CRC[((crc >>> 24) ^ ch) & 0xff];
            b[off + n++] = (byte) ch;
        }
        return n == 0 && ended ? -1 : n;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
