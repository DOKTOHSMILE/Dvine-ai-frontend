package ai.dvine.create;

import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Paints a picture from the words. It is a local drawing model: shapes and a
 * palette, written to pixels. It is not a diffusion model and not a photograph.
 */
public final class DrawingModel {
    public static final String METHOD = "procedural-drawing-model";

    public static final class Picture {
        public final String id;
        public final String prompt;
        public final String subject;
        public final String method;
        public final int width;
        public final int height;
        public final int[] argb;
        public final byte[] png;
        public final boolean photograph;
        public final boolean diffusion;

        Picture(String id, String prompt, String subject, int width, int height, int[] argb, byte[] png) {
            this.id = id;
            this.prompt = prompt == null ? "" : prompt;
            this.subject = subject;
            this.method = METHOD;
            this.width = width;
            this.height = height;
            this.argb = argb;
            this.png = png;
            this.photograph = false;
            this.diffusion = false;
        }
    }

    public Picture draw(String prompt) {
        int w = 256, h = 256;
        int[] px = new int[w * h];
        int ink = colour(prompt);
        String subject = subject(prompt);
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF10141A;
        }
        if ("motorcycle".equals(subject)) {
            disc(px, w, 78, 168, 36, 0xFF1A1C20);
            disc(px, w, 176, 168, 36, 0xFF1A1C20);
            disc(px, w, 78, 168, 18, ink);
            disc(px, w, 176, 168, 18, ink);
            rect(px, w, 70, 150, 120, 16, ink);
            rect(px, w, 108, 118, 46, 22, shift(ink));
        } else if ("house".equals(subject)) {
            rect(px, w, 70, 120, 120, 90, ink);
            tri(px, w, 60, 120, 128, 70, 196, 120, shift(ink));
            rect(px, w, 114, 160, 28, 50, 0xFF1A1C20);
        } else if ("person".equals(subject)) {
            disc(px, w, 128, 70, 28, ink);
            rect(px, w, 108, 100, 40, 70, ink);
            rect(px, w, 70, 110, 36, 14, shift(ink));
            rect(px, w, 150, 110, 36, 14, shift(ink));
            rect(px, w, 112, 170, 14, 50, ink);
            rect(px, w, 132, 170, 14, 50, ink);
        } else {
            disc(px, w, 128, 120, 48, ink);
            rect(px, w, 96, 150, 64, 28, shift(ink));
        }
        String id = java.util.UUID.randomUUID().toString();
        return new Picture(id, prompt, subject, w, h, px, png(w, h, px));
    }

    static String subject(String prompt) {
        String n = prompt == null ? "" : prompt.toLowerCase(Locale.US);
        if (n.contains("motorcycle") || n.contains("motorbike")) {
            return "motorcycle";
        }
        if (n.contains("house") || n.contains("home")) {
            return "house";
        }
        if (n.contains("person") || n.contains("human") || n.contains("robot")) {
            return "person";
        }
        return "shape";
    }

    private static int colour(String prompt) {
        String n = prompt == null ? "" : prompt.toLowerCase(Locale.US);
        if (n.contains("red")) {
            return 0xFFD23A3A;
        }
        if (n.contains("blue")) {
            return 0xFF3A7BD2;
        }
        if (n.contains("green")) {
            return 0xFF3AAA62;
        }
        if (n.contains("yellow")) {
            return 0xFFE0C14A;
        }
        return 0xFF7FE3FF;
    }

    private static int shift(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return 0xFF000000 | ((r * 2 / 3) << 16) | ((g * 2 / 3) << 8) | (b * 2 / 3);
    }

    private static void rect(int[] px, int w, int x, int y, int rw, int rh, int color) {
        int h = px.length / w;
        for (int yy = y; yy < y + rh; yy++) {
            for (int xx = x; xx < x + rw; xx++) {
                if (xx >= 0 && yy >= 0 && xx < w && yy < h) {
                    px[yy * w + xx] = color;
                }
            }
        }
    }

    private static void disc(int[] px, int w, int cx, int cy, int radius, int color) {
        int h = px.length / w;
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                int dx = x - cx, dy = y - cy;
                if (x >= 0 && y >= 0 && x < w && y < h && dx * dx + dy * dy <= radius * radius) {
                    px[y * w + x] = color;
                }
            }
        }
    }

    private static void tri(int[] px, int w, int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        int minY = Math.max(0, Math.min(y0, Math.min(y1, y2)));
        int maxY = Math.min(px.length / w - 1, Math.max(y0, Math.max(y1, y2)));
        for (int y = minY; y <= maxY; y++) {
            int minX = w, maxX = 0;
            minX = edge(minX, x0, y0, x1, y1, y);
            maxX = edgeMax(maxX, x0, y0, x1, y1, y);
            minX = Math.min(minX, edge(w, x1, y1, x2, y2, y));
            maxX = Math.max(maxX, edgeMax(0, x1, y1, x2, y2, y));
            minX = Math.min(minX, edge(w, x2, y2, x0, y0, y));
            maxX = Math.max(maxX, edgeMax(0, x2, y2, x0, y0, y));
            for (int x = Math.max(0, minX); x <= Math.min(w - 1, maxX); x++) {
                if (inside(x, y, x0, y0, x1, y1, x2, y2)) {
                    px[y * w + x] = color;
                }
            }
        }
    }

    private static int edge(int min, int x0, int y0, int x1, int y1, int y) {
        if ((y0 <= y && y <= y1) || (y1 <= y && y <= y0)) {
            if (y0 == y1) {
                return Math.min(min, Math.min(x0, x1));
            }
            int x = x0 + (x1 - x0) * (y - y0) / (y1 - y0);
            return Math.min(min, x);
        }
        return min;
    }

    private static int edgeMax(int max, int x0, int y0, int x1, int y1, int y) {
        if ((y0 <= y && y <= y1) || (y1 <= y && y <= y0)) {
            if (y0 == y1) {
                return Math.max(max, Math.max(x0, x1));
            }
            int x = x0 + (x1 - x0) * (y - y0) / (y1 - y0);
            return Math.max(max, x);
        }
        return max;
    }

    private static boolean inside(int x, int y, int x0, int y0, int x1, int y1, int x2, int y2) {
        int a = (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0);
        int b = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1);
        int c = (x0 - x2) * (y - y2) - (y0 - y2) * (x - x2);
        return (a >= 0 && b >= 0 && c >= 0) || (a <= 0 && b <= 0 && c <= 0);
    }

    static byte[] png(int w, int h, int[] argb) {
        byte[] raw = new byte[(w * 3 + 1) * h];
        int p = 0;
        for (int y = 0; y < h; y++) {
            raw[p++] = 0;
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                raw[p++] = (byte) ((c >> 16) & 255);
                raw[p++] = (byte) ((c >> 8) & 255);
                raw[p++] = (byte) (c & 255);
            }
        }
        Deflater def = new Deflater();
        def.setInput(raw);
        def.finish();
        byte[] zip = new byte[raw.length + 64];
        int n = def.deflate(zip);
        def.end();
        byte[] idat = new byte[n];
        System.arraycopy(zip, 0, idat, 0, n);
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, w);
        putInt(ihdr, 4, h);
        ihdr[8] = 8;
        ihdr[9] = 2;
        byte[] sig = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        byte[] a = chunk("IHDR", ihdr);
        byte[] b = chunk("IDAT", idat);
        byte[] c = chunk("IEND", new byte[0]);
        byte[] out = new byte[sig.length + a.length + b.length + c.length];
        System.arraycopy(sig, 0, out, 0, sig.length);
        System.arraycopy(a, 0, out, sig.length, a.length);
        System.arraycopy(b, 0, out, sig.length + a.length, b.length);
        System.arraycopy(c, 0, out, sig.length + a.length + b.length, c.length);
        return out;
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] out = new byte[12 + data.length];
        putInt(out, 0, data.length);
        for (int i = 0; i < 4; i++) {
            out[4 + i] = (byte) type.charAt(i);
        }
        System.arraycopy(data, 0, out, 8, data.length);
        CRC32 crc = new CRC32();
        crc.update(out, 4, 4 + data.length);
        putInt(out, 8 + data.length, (int) crc.getValue());
        return out;
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }
}
