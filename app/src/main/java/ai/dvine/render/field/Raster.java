package ai.dvine.render.field;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/**
 * Draws what the particles should become into pixels (off screen, never shown), then samples them into a
 * {@link Shape}: text, the download bar, a {@link DrawSpec} drawing, or any bitmap. Android drawing only; the
 * sampling is pure Java.
 */
public final class Raster {
    private Raster() {
    }

    /** Words as particles: wrapped to {@code maxLines}, centred, {@code width} units across. */
    public static Shape text(String words, int color, int maxPoints, float width, int maxLines) {
        int w = 1100, h = 104 * maxLines;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(52f);
        p.setFakeBoldText(false);
        p.setLetterSpacing(0.01f);
        StaticLayout l = StaticLayout.Builder.obtain(words, 0, words.length(), p, w - 20).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        c.translate(10f, Math.max(0f, (h - l.getHeight()) / 2f));
        l.draw(c);
        return sample(b, maxPoints, width, 0f); // flat: words read crisply only on one plane
    }

    /**
     * The download bar: a title, a framed track with the filled part in {@code color} (brighter at its leading
     * edge), the percent, and a detail line.
     */
    public static Shape progress(String title, float fraction, String detail, int color, int maxPoints, float width) {
        int w = 900, h = 300;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(0xFFE8EEF2);
        t.setTextSize(58f);
        t.setFakeBoldText(true);
        t.setLetterSpacing(0.08f);
        c.drawText(title, 20f, 70f, t);
        String pct = Math.round(fraction * 100) + "%";
        t.setColor(color);
        t.setTextAlign(Paint.Align.RIGHT);
        c.drawText(pct, w - 20f, 70f, t);
        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(6f);
        frame.setColor(0xFF8A939A);
        RectF track = new RectF(20f, 110f, w - 20f, 180f);
        c.drawRoundRect(track, 35f, 35f, frame);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        float fx = track.left + 10f + (track.width() - 20f) * Math.max(0f, Math.min(1f, fraction));
        if (fraction > 0.005f) {
            c.drawRoundRect(new RectF(track.left + 10f, track.top + 10f, Math.max(track.left + 50f, fx), track.bottom - 10f), 25f, 25f, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(Math.max(track.left + 30f, fx - 18f), track.centerY(), 14f, fill);
        }
        t.setColor(0xFFB5BDC1);
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(44f);
        t.setFakeBoldText(false);
        t.setLetterSpacing(0f);
        c.drawText(ellipsize(detail, t, w - 40f), 20f, 260f, t);
        return sample(b, maxPoints, width, 0f);
    }

    /** A drawing the language model described, {@code width} units across. */
    public static Shape drawing(DrawSpec d, int maxPoints, float width) {
        int s = 600;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float k = s / 100f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (DrawSpec.Item it : d.items) {
            p.setColor(it.color);
            p.setStyle(it.fill ? Paint.Style.FILL : Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * k);
            float[] v = it.v;
            switch (it.kind) {
                case CIRCLE:
                    c.drawCircle(v[0] * k, v[1] * k, v[2] * k, p);
                    break;
                case ELLIPSE:
                    c.drawOval(new RectF((v[0] - v[2]) * k, (v[1] - v[3]) * k, (v[0] + v[2]) * k, (v[1] + v[3]) * k), p);
                    break;
                case RECT:
                    c.drawRect(v[0] * k, v[1] * k, (v[0] + v[2]) * k, (v[1] + v[3]) * k, p);
                    break;
                case LINE:
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1.5f, v[4]) * k);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    c.drawLine(v[0] * k, v[1] * k, v[2] * k, v[3] * k, p);
                    break;
                case POLYGON: {
                    Path path = new Path();
                    path.moveTo(v[0] * k, v[1] * k);
                    for (int i = 2; i + 1 < v.length; i += 2) {
                        path.lineTo(v[i] * k, v[i + 1] * k);
                    }
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case TEXT: {
                    TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    t.setColor(it.color);
                    t.setTextSize(v[2] * k);
                    t.setTextAlign(Paint.Align.CENTER);
                    c.drawText(it.text, v[0] * k, v[1] * k, t);
                    break;
                }
                default:
                    break;
            }
        }
        return sample(b, maxPoints, width, 0.25f);
    }

    /**
     * Any picture as particles (its own colours), {@code width} units across. A photo's edges fade out in an oval
     * and its darkest parts drop away, so the subject floats in the stage instead of a hard rectangle.
     */
    public static Shape image(Bitmap source, int maxPoints, float width) {
        int max = 360;
        float scale = Math.min(1f, max / (float) Math.max(source.getWidth(), source.getHeight()));
        Bitmap b = scale < 1f ? Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
                Math.max(1, Math.round(source.getHeight() * scale)), true) : source;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        vignette(px, w, h);
        return Shape.fromPixels(px, w, h, maxPoints, width * Math.min(1f, w / (float) h), 0.35f);
    }

    /** Fades the picture's alpha out toward an oval edge (pure: on the pixel array). */
    static void vignette(int[] px, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = (x - w / 2f) / (w / 2f), dy = (y - h / 2f) / (h / 2f);
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float k = r < 0.75f ? 1f : Math.max(0f, 1f - (r - 0.75f) / 0.25f);
                int p = px[y * w + x];
                int a = (int) (((p >>> 24) & 255) * k);
                px[y * w + x] = (a << 24) | (p & 0xFFFFFF);
            }
        }
    }

    /** Pictures from bytes (PNG, JPEG, WebP), or null. */
    public static Bitmap decode(byte[] bytes) {
        return bytes == null ? null : android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private static Shape sample(Bitmap b, int maxPoints, float width, float depth) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        return Shape.fromPixels(px, w, h, maxPoints, width, depth);
    }

    private static String ellipsize(String s, TextPaint p, float width) {
        return android.text.TextUtils.ellipsize(s == null ? "" : s, p, width, android.text.TextUtils.TruncateAt.END).toString();
    }
}
