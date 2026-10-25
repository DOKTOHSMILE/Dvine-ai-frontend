package ai.dvine.face;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

/**
 * One face. Obi and Ivie never share the screen.
 * The current head dissolves and the other assembles when that persona
 * has a line, or when the user calls them.
 */
public final class MatrixStageView extends View {
    public interface Listener {
        void onHeadTap();

        void onSwipeTo(boolean ivie);

        void onMic();

        void onType();
    }

    private static final int OBI = 0xFF00E5FF;
    private static final int IVIE = 0xFFFFB300;
    private static final int INK = 0xFFE8E8E8;

    private final FaceRig obi = new FaceRig(false);
    private final FaceRig ivie = new FaceRig(true);
    private final LowPolyHead obiHead = LowPolyHead.create(true);
    private final LowPolyHead ivieHead = LowPolyHead.create(false);
    private final float[] deformed = new float[Math.max(obiHead.rest.length, ivieHead.rest.length)];
    private final float[] projected = new float[deformed.length];
    private final Path path = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint captionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Choreographer choreographer = Choreographer.getInstance();

    private Listener listener;
    private boolean showingIvie;
    private boolean pendingIvie;
    private int phase; // 0 hold, 1 leave, 2 enter
    private final Spring1D dissolve = new Spring1D(8.2f, 0.95f, 0f);
    private Runnable pendingReady;
    private int readyToken;
    private int firedToken = -1;
    private String caption = "";
    private StaticLayout captionLayout;
    private int captionWidth;
    private String notice = "";
    private boolean listening;
    private float density = 1f;
    private int padTop;
    private int padBottom;
    private long lastNanos;
    private RadialGradient obiGlow;
    private RadialGradient ivieGlow;
    private float glowUnit;
    private boolean framePosted;

    private float downX;
    private float downY;
    private boolean decided;
    private boolean swiping;
    private boolean dragged;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long nanos) {
            float dt = lastNanos == 0L ? 0.016f : (nanos - lastNanos) / 1_000_000_000f;
            lastNanos = nanos;
            tick(dt);
            invalidate();
            if (framePosted) {
                choreographer.postFrameCallback(this);
            }
        }
    };

    public MatrixStageView(Context context) {
        super(context);
        init();
    }

    public MatrixStageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        soft.setStyle(Paint.Style.STROKE);
        soft.setStrokeJoin(Paint.Join.ROUND);
        soft.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(INK);
        text.setTextAlign(Paint.Align.CENTER);
        captionPaint.setColor(INK);
        captionPaint.setTextAlign(Paint.Align.CENTER);
        setContentDescription("DVINE. One face. Swipe to call the other.");
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isIvie() {
        return showingIvie;
    }

    public String agent() {
        return showingIvie ? "ivie" : "obi";
    }

    public void setMode(FaceMode mode) {
        rig().setMode(mode);
    }

    public void setCaption(String value) {
        caption = value == null ? "" : value;
        captionLayout = null;
    }

    public void setNotice(String value) {
        notice = value == null ? "" : value;
    }

    public void setListening(boolean on) {
        listening = on;
        if (on) {
            rig().setMode(FaceMode.LISTENING);
        }
    }

    public void ensureFace(boolean wantIvie, Runnable onReady) {
        pendingIvie = wantIvie;
        pendingReady = onReady;
        readyToken++;
        if (wantIvie == showingIvie && phase == 0) {
            firedToken = readyToken;
            pendingReady = null;
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (phase == 0) {
            phase = 1;
        }
    }

    private FaceRig rig() {
        return showingIvie ? ivie : obi;
    }

    private void tick(float dt) {
        if (phase == 1) {
            dissolve.step(1f, dt);
            if (dissolve.value > 0.97f) {
                showingIvie = pendingIvie;
                phase = 2;
                rig().setMode(FaceMode.CONTENT);
            }
        } else if (phase == 2) {
            dissolve.step(0f, dt);
            if (firedToken != readyToken && dissolve.value < 0.34f && showingIvie == pendingIvie) {
                firedToken = readyToken;
                Runnable ready = pendingReady;
                pendingReady = null;
                if (ready != null) {
                    ready.run();
                }
            }
            if (dissolve.value < 0.025f) {
                dissolve.value = 0f;
                dissolve.velocity = 0f;
                phase = 0;
                if (pendingIvie != showingIvie) {
                    phase = 1;
                }
            }
        } else if (pendingIvie != showingIvie) {
            phase = 1;
        }
        obi.tick(dt);
        ivie.tick(dt);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(0xFF000000);
        FaceRig rig = rig();
        boolean ivieOn = showingIvie;
        int color = ivieOn ? IVIE : OBI;
        float faceAlpha = 1f - dissolve.value;
        float unit = Math.min(w * 0.46f, (h - padTop - padBottom - dp(150f)) * 0.50f);
        float cx = w * 0.5f;
        float cy = padTop + (h - padTop - padBottom) * 0.44f;
        ensureGlow(unit);
        glowPaint.setShader(ivieOn ? ivieGlow : obiGlow);
        glowPaint.setAlpha((int) (255 * faceAlpha * clamp(rig.glow.value, 0f, 1.2f)));
        canvas.save();
        canvas.translate(cx, cy + unit * 0.15f);
        canvas.drawCircle(0f, 0f, unit * 1.35f, glowPaint);
        canvas.restore();

        float away = (ivieOn ? -16f : 16f) * dissolve.value;
        drawParticles(canvas, cx, cy, unit, color, faceAlpha, rig.time());
        drawSkull(canvas, ivieOn ? ivieHead : obiHead, rig, cx, cy, unit, color, faceAlpha, away);
        drawFeatures(canvas, rig, ivieOn, cx, cy, unit, color, faceAlpha, away);
        if (ivieOn) {
            drawHair(canvas, rig, cx, cy, unit, color, faceAlpha, away);
        }
        if (rig.mode() == FaceMode.SPEAKING && faceAlpha > 0.45f) {
            drawWave(canvas, cx, cy + unit * 1.02f, unit, color, rig.jaw.value);
        }

        text.setTextSize(dp(13f));
        text.setLetterSpacing(0.22f);
        text.setAlpha((int) (220 * Math.max(faceAlpha, 0.35f)));
        canvas.drawText(ivieOn ? "IVIE" : "OBI", cx, cy + unit * 1.22f, text);

        if (caption.length() > 0) {
            int width = Math.max(1, (int) (w * 0.78f));
            if (captionLayout == null || captionWidth != width) {
                captionWidth = width;
                captionPaint.setTextSize(dp(15f));
                captionLayout = StaticLayout.Builder.obtain(caption, 0, caption.length(), captionPaint, width)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(3)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build();
            }
            captionPaint.setAlpha((int) (210 * Math.max(faceAlpha, 0.4f)));
            canvas.save();
            canvas.translate((w - width) / 2f, cy + unit * 1.30f);
            captionLayout.draw(canvas);
            canvas.restore();
        }

        text.setLetterSpacing(0.28f);
        text.setTextSize(dp(12f));
        text.setAlpha(180);
        canvas.drawText("DVINE", w * 0.5f, padTop + dp(28f), text);
        text.setTextSize(dp(10f));
        text.setAlpha(140);
        canvas.drawText(listening ? "LISTENING" : "LOCAL", w - dp(48f), padTop + dp(28f), text);

        float controls = h - padBottom - dp(36f);
        stroke.setShader(null);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setColor(color);
        stroke.setAlpha(listening ? 230 : 150);
        canvas.drawCircle(w * 0.5f, controls, dp(22f), stroke);
        fill.setColor(color);
        fill.setAlpha(listening ? 180 : 40);
        canvas.drawCircle(w * 0.5f, controls, listening ? dp(8f) : dp(5f), fill);
        text.setTextSize(dp(11f));
        text.setLetterSpacing(0.08f);
        text.setAlpha(160);
        canvas.drawText("mic", w * 0.5f, controls + dp(40f), text);
        canvas.drawText("type", w - dp(36f), controls + dp(4f), text);

        if (notice.length() > 0) {
            text.setTextSize(dp(12f));
            text.setAlpha(170);
            canvas.drawText(notice, w * 0.5f, controls - dp(36f), text);
        }
    }

    private void ensureGlow(float unit) {
        if (obiGlow != null && Math.abs(unit - glowUnit) < 1f) {
            return;
        }
        glowUnit = unit;
        obiGlow = new RadialGradient(0f, 0f, unit * 1.35f, new int[]{0x6600E5FF, 0x00000000}, null, Shader.TileMode.CLAMP);
        ivieGlow = new RadialGradient(0f, 0f, unit * 1.35f, new int[]{0x66FFB300, 0x00000000}, null, Shader.TileMode.CLAMP);
    }

    private void drawParticles(Canvas canvas, float cx, float cy, float unit, int color, float faceAlpha, float time) {
        fill.setColor(color);
        for (int i = 0; i < 8; i++) {
            float seed = i * 0.77f;
            float ang = seed * 6.2f + time * (0.18f + (i % 3) * 0.04f);
            float rad = unit * (0.10f + 0.05f * (float) Math.sin(time * 0.35f + seed));
            rad += unit * dissolve.value * 0.50f;
            float px = cx + (float) Math.cos(ang) * rad * 0.85f;
            float py = cy + unit * 0.78f + (float) Math.sin(ang) * rad * 0.45f;
            int alpha = (int) ((28 + dissolve.value * 90f) * Math.max(faceAlpha, 0.25f));
            fill.setAlpha(Math.min(160, alpha));
            path.rewind();
            path.moveTo(px, py - dp(2.2f));
            path.lineTo(px + dp(2.4f), py + dp(1.6f));
            path.lineTo(px - dp(2.4f), py + dp(1.6f));
            path.close();
            canvas.drawPath(path, fill);
        }
    }

    private void drawSkull(Canvas canvas, LowPolyHead head, FaceRig rig, float cx, float cy, float unit,
                           int color, float faceAlpha, float awayYaw) {
        float breath = 1f + (float) Math.sin(rig.time() * 1.35f) * 0.012f;
        head.deform(rig.smile.value, rig.jaw.value, rig.browL.value, rig.browR.value, deformed);
        int count = head.rest.length / 3;
        float yaw = (float) Math.toRadians(rig.yaw.value + awayYaw);
        float pitch = (float) Math.toRadians(rig.pitch.value);
        float roll = (float) Math.toRadians(rig.roll.value);
        for (int i = 0; i < count; i++) {
            project(deformed[i * 3] * breath, deformed[i * 3 + 1] * breath, deformed[i * 3 + 2],
                    yaw, pitch, roll, cx, cy, unit, projected, i * 3);
        }
        int glowA = (int) (36 * faceAlpha * clamp(rig.glow.value, 0.2f, 1.2f));
        int lineA = (int) (210 * faceAlpha * clamp(rig.glow.value, 0.35f, 1.15f));
        soft.setColor(color);
        soft.setAlpha(Math.max(0, Math.min(255, glowA)));
        soft.setStrokeWidth(Math.max(dp(1.6f), unit * 0.012f));
        stroke.setColor(color);
        stroke.setAlpha(Math.max(0, Math.min(255, lineA)));
        stroke.setStrokeWidth(Math.max(dp(1.05f), unit * 0.0048f));
        fill.setColor(color);
        for (int t = 0; t < head.tris.length; t += 3) {
            int ia = head.tris[t] * 3;
            int ib = head.tris[t + 1] * 3;
            int ic = head.tris[t + 2] * 3;
            float ax = projected[ib] - projected[ia];
            float ay = projected[ib + 1] - projected[ia + 1];
            float bx = projected[ic] - projected[ia];
            float by = projected[ic + 1] - projected[ia + 1];
            if (ax * by - ay * bx >= 0f) {
                continue;
            }
            path.rewind();
            path.moveTo(projected[ia], projected[ia + 1]);
            path.lineTo(projected[ib], projected[ib + 1]);
            path.lineTo(projected[ic], projected[ic + 1]);
            path.close();
            fill.setAlpha((int) (18 * faceAlpha));
            canvas.drawPath(path, fill);
            canvas.drawPath(path, soft);
            canvas.drawPath(path, stroke);
        }
    }

    private void drawFeatures(Canvas canvas, FaceRig rig, boolean ivieOn, float cx, float cy, float unit,
                              int color, float faceAlpha, float awayYaw) {
        float yaw = (float) Math.toRadians(rig.yaw.value + awayYaw);
        float pitch = (float) Math.toRadians(rig.pitch.value);
        float roll = (float) Math.toRadians(rig.roll.value);
        stroke.setColor(color);
        stroke.setAlpha((int) (235 * faceAlpha));
        stroke.setStrokeWidth(Math.max(dp(1.7f), unit * 0.008f));
        float smile = rig.smile.value;
        float jaw = rig.jaw.value;
        float wide = rig.wide.value;
        float blink = clamp(rig.blink.value, 0f, 1f);
        float happy = ivieOn ? 0.28f : 0.12f;

        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, ear(ivieOn, -1f));
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, ear(ivieOn, 1f));
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, new float[]{
                0f, 0.30f, 0.55f,
                0f, 0.10f, 0.78f,
                0f, -0.04f, 0.70f
        });

        float eyeY = 0.12f;
        float eyeR = ivieOn ? 0.13f : 0.11f;
        drawEye(canvas, yaw, pitch, roll, cx, cy, unit, -0.30f, eyeY, eyeR, blink, smile, happy, rig.lookX.value, rig.lookY.value);
        drawEye(canvas, yaw, pitch, roll, cx, cy, unit, 0.30f, eyeY, eyeR, blink, smile, happy, rig.lookX.value, rig.lookY.value);

        stroke.setStrokeWidth(Math.max(dp(2.1f), unit * 0.010f));
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, brow(-1f, ivieOn, rig.browL.value));
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, brow(1f, ivieOn, rig.browR.value));

        stroke.setStrokeWidth(Math.max(dp(2.0f), unit * 0.009f));
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, lip(false, smile, jaw, wide));
        float[] lower = lip(true, smile, jaw, wide);
        hinge(lower, jaw);
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, lower);
        if (jaw > 0.08f) {
            stroke.setAlpha((int) (120 * faceAlpha));
            drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, new float[]{
                    -0.08f, -0.22f - jaw * 0.05f, 0.60f,
                    0.08f, -0.22f - jaw * 0.05f, 0.60f
            });
        }
    }

    private void drawEye(Canvas canvas, float yaw, float pitch, float roll, float cx, float cy, float unit,
                         float ex, float ey, float rx, float blink, float smile, float happy, float lookX, float lookY) {
        int steps = 7;
        float ry = rx * 0.42f * (1f - 0.92f * blink) * (1f - smile * happy);
        float[] upper = new float[steps * 3];
        float[] lower = new float[steps * 3];
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            float x = ex + (t * 2f - 1f) * rx;
            float arch = (float) Math.sin(t * Math.PI);
            upper[i * 3] = x;
            upper[i * 3 + 1] = ey + arch * ry;
            upper[i * 3 + 2] = 0.64f;
            lower[i * 3] = x;
            lower[i * 3 + 1] = ey - arch * ry * 0.55f + smile * 0.012f;
            lower[i * 3 + 2] = 0.64f;
        }
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, upper);
        drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, lower);
        float iris = Math.max(dp(2.2f), unit * 0.018f * (1f - blink));
        float[] p = projectTo(ex + lookX * 0.04f, ey + lookY * 0.03f, 0.70f, yaw, pitch, roll, cx, cy, unit);
        fill.setColor(stroke.getColor());
        fill.setAlpha(stroke.getAlpha());
        canvas.drawCircle(p[0], p[1], iris, fill);
    }

    private void drawHair(Canvas canvas, FaceRig rig, float cx, float cy, float unit, int color, float faceAlpha, float awayYaw) {
        float yaw = (float) Math.toRadians(rig.yaw.value + awayYaw);
        float pitch = (float) Math.toRadians(rig.pitch.value);
        float roll = (float) Math.toRadians(rig.roll.value);
        stroke.setColor(color);
        stroke.setAlpha((int) (180 * faceAlpha));
        stroke.setStrokeWidth(Math.max(dp(1.2f), unit * 0.004f));
        float time = rig.time();
        for (int s = 0; s < 7; s++) {
            float side = -1f + s * (2f / 6f);
            boolean edge = s == 0 || s == 6;
            float[] strand = new float[8 * 3];
            for (int k = 0; k < 8; k++) {
                float t = k / 7f;
                float drift = (float) Math.sin(time * 0.55f + s * 0.7f + t * 2.2f) * 0.012f * t;
                float x;
                float y;
                float z;
                if (edge) {
                    x = side * (0.52f + t * 0.34f) + drift;
                    y = 0.58f - t * 0.70f;
                    z = 0.12f - t * 0.08f;
                } else {
                    x = side * 0.40f + side * t * 0.18f + drift;
                    y = 0.74f - Math.abs(side) * 0.08f - t * 0.42f;
                    z = -0.05f - t * 0.30f;
                }
                strand[k * 3] = x;
                strand[k * 3 + 1] = y;
                strand[k * 3 + 2] = z;
            }
            drawPoly(canvas, yaw, pitch, roll, cx, cy, unit, strand);
        }
    }

    private void drawWave(Canvas canvas, float cx, float y, float unit, int color, float jaw) {
        fill.setColor(color);
        for (int i = 0; i < 5; i++) {
            float shift = (i - 2) * dp(7f);
            float h = dp(4f) + jaw * unit * (0.08f + 0.04f * (i % 2));
            fill.setAlpha(160);
            canvas.drawRect(cx + shift, y - h, cx + shift + dp(2.2f), y, fill);
        }
    }

    private static float[] ear(boolean ivie, float side) {
        float s = ivie ? 0.92f : 1.05f;
        return new float[]{
                side * 0.72f * s, 0.26f, 0.02f,
                side * 0.96f * s, 0.16f, 0.00f,
                side * 1.00f * s, -0.02f, 0.00f,
                side * 0.86f * s, -0.16f, 0.02f,
                side * 0.70f * s, -0.02f, 0.06f
        };
    }

    private static float[] brow(float side, boolean ivie, float raise) {
        float[] pts = new float[6 * 3];
        for (int i = 0; i < 6; i++) {
            float t = i / 5f;
            float arch = (float) Math.sin(t * Math.PI);
            float x = side < 0f ? lerp(-0.50f, -0.12f, t) : lerp(0.12f, 0.50f, t);
            float outer = side < 0f ? 1f - t : t;
            float y = (ivie ? 0.36f : 0.33f) + arch * (ivie ? 0.055f : 0.018f) + raise * 0.055f
                    + (ivie ? outer * 0.02f : 0f);
            pts[i * 3] = x;
            pts[i * 3 + 1] = y;
            pts[i * 3 + 2] = 0.60f;
        }
        return pts;
    }

    private static float[] lip(boolean lower, float smile, float jaw, float wide) {
        float[] pts = new float[8 * 3];
        for (int i = 0; i < 8; i++) {
            float t = i / 7f;
            float arch = (float) Math.sin(t * Math.PI);
            float corner = 1f - arch;
            float x = lerp(-0.30f, 0.30f, t) * (0.78f + wide * 0.35f);
            float y;
            if (!lower) {
                y = -0.16f + arch * 0.040f + smile * 0.055f * corner;
            } else {
                y = -0.20f - arch * (0.035f + jaw * 0.13f) + smile * 0.050f * corner;
            }
            pts[i * 3] = x;
            pts[i * 3 + 1] = y;
            pts[i * 3 + 2] = 0.66f + arch * 0.03f;
        }
        return pts;
    }

    private static void hinge(float[] pts, float jaw) {
        float ang = jaw * 0.45f;
        float ca = (float) Math.cos(ang);
        float sa = (float) Math.sin(ang);
        float hingeY = -0.08f;
        for (int i = 0; i < pts.length; i += 3) {
            float y = pts[i + 1];
            float z = pts[i + 2];
            float dy = y - hingeY;
            pts[i + 1] = hingeY + dy * ca - (z - 0.2f) * sa;
            pts[i + 2] = 0.2f + dy * sa + (z - 0.2f) * ca;
        }
    }

    private void drawPoly(Canvas canvas, float yaw, float pitch, float roll, float cx, float cy, float unit, float[] local) {
        path.rewind();
        for (int i = 0; i < local.length; i += 3) {
            float[] p = projectTo(local[i], local[i + 1], local[i + 2], yaw, pitch, roll, cx, cy, unit);
            if (i == 0) {
                path.moveTo(p[0], p[1]);
            } else {
                path.lineTo(p[0], p[1]);
            }
        }
        canvas.drawPath(path, stroke);
    }

    private void project(float x, float y, float z, float yaw, float pitch, float roll,
                         float cx, float cy, float unit, float[] out, int o) {
        float[] p = projectTo(x, y, z, yaw, pitch, roll, cx, cy, unit);
        out[o] = p[0];
        out[o + 1] = p[1];
        out[o + 2] = p[2];
    }

    private final float[] tmp = new float[3];

    private float[] projectTo(float x, float y, float z, float yaw, float pitch, float roll,
                              float cx, float cy, float unit) {
        float cr = (float) Math.cos(roll);
        float sr = (float) Math.sin(roll);
        float x1 = x * cr - y * sr;
        float y1 = x * sr + y * cr;
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float y2 = y1 * cp - z * sp;
        float z2 = y1 * sp + z * cp;
        float cyaw = (float) Math.cos(yaw);
        float syaw = (float) Math.sin(yaw);
        float x3 = x1 * cyaw + z2 * syaw;
        float z3 = -x1 * syaw + z2 * cyaw;
        float depth = 4.5f - z3;
        float s = unit * (3.15f / depth);
        tmp[0] = cx + x3 * s;
        tmp[1] = cy - y2 * s;
        tmp[2] = depth;
        return tmp;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float slop = dp(10f);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                decided = false;
                swiping = false;
                dragged = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - downX;
                float dy = y - downY;
                if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    decided = true;
                    swiping = Math.abs(dx) > Math.abs(dy) * 1.35f && y < getHeight() - padBottom - dp(80f);
                }
                if (decided && !swiping) {
                    dragged = true;
                    rig().setDrag(dx / density * 0.11f, -dy / density * 0.08f, true);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                rig().setDrag(0f, 0f, false);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    return true;
                }
                float ux = x - downX;
                float uy = y - downY;
                if (swiping && Math.abs(ux) > dp(72f)) {
                    if (listener != null) {
                        listener.onSwipeTo(!showingIvie);
                    }
                    return true;
                }
                if (!dragged && Math.hypot(ux, uy) < dp(14f)) {
                    float controls = getHeight() - padBottom - dp(36f);
                    if (Math.hypot(x - getWidth() * 0.5f, y - controls) < dp(36f)) {
                        if (listener != null) {
                            listener.onMic();
                        }
                    } else if (x > getWidth() - dp(72f) && y > controls - dp(28f)) {
                        if (listener != null) {
                            listener.onType();
                        }
                    } else if (listener != null) {
                        listener.onHeadTap();
                    }
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFrames();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFrames();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            startFrames();
        } else {
            stopFrames();
        }
    }

    private void startFrames() {
        if (framePosted) {
            return;
        }
        framePosted = true;
        lastNanos = 0L;
        choreographer.postFrameCallback(frame);
    }

    private void stopFrames() {
        framePosted = false;
        choreographer.removeFrameCallback(frame);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            padTop = bars.top;
            padBottom = bars.bottom;
        } else {
            padTop = insets.getSystemWindowInsetTop();
            padBottom = insets.getSystemWindowInsetBottom();
        }
        return insets;
    }

    private float dp(float v) {
        return v * density;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
