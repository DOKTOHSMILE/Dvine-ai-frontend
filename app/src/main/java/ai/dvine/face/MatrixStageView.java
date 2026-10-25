package ai.dvine.face;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
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

import ai.dvine.gl.MeshGlView;
import ai.dvine.face.HeadWebView;

/**
 * One face. Obi and Ivie never share the screen.
 * The current head dissolves and the other assembles when that persona
 * has a line, or when the user calls them.
 */
public final class MatrixStageView extends View implements Choreographer.FrameCallback {

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

    private StageListener listener;
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
    private boolean staged;
    private String draft = "";
    private final String[] activity = new String[5];
    private final String[] activityTime = new String[5];
    private int activityCount;
    private final RectF composer = new RectF();
    private final RectF micHit = new RectF();
    private final RectF keyHit = new RectF();
    private final RectF sendHit = new RectF();
    private final RectF shortHit = new RectF();
    private final RectF headHit = new RectF();
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
    private boolean dragged;
    private boolean micSwipe;

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
        setContentDescription("DVINE. One face. Swipe to speak. Say Obi switch or Ivie switch.");
    }

    private MeshGlView meshGl;
    private HeadWebView headWeb;

    public void setListener(StageListener listener) {
        this.listener = listener;
    }

    
    public void setHeadWeb(HeadWebView headWeb) {
        this.headWeb = headWeb;
    }

    public void setMesh(MeshGlView meshGl) {
        this.meshGl = meshGl;
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

    public void setStaged(boolean on, String body) {
        staged = on;
        draft = body == null ? "" : body;
        captionLayout = null;
    }

    public void clearActivity() {
        activityCount = 0;
    }

    public void pushActivity(String label) {
        if (label == null || label.length() == 0) {
            return;
        }
        if (activityCount > 0 && label.equals(activity[activityCount - 1])) {
            activityTime[activityCount - 1] = clock();
            return;
        }
        String when = clock();
        if (activityCount < activity.length) {
            activity[activityCount] = label;
            activityTime[activityCount] = when;
            activityCount++;
        } else {
            for (int i = 1; i < activity.length; i++) {
                activity[i - 1] = activity[i];
                activityTime[i - 1] = activityTime[i];
            }
            activity[activity.length - 1] = label;
            activityTime[activity.length - 1] = when;
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
        float side = dp(20f);

        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(INK);
        text.setLetterSpacing(0.28f);
        text.setTextSize(dp(15f));
        text.setAlpha(230);
        float headerY = padTop + dp(22f);
        canvas.drawText("DVINE", side, headerY, text);

        String link = staged ? "LOCK: STAGED" : "WS: LIVE";
        text.setTextSize(dp(10f));
        text.setLetterSpacing(0.08f);
        float linkW = text.measureText(link) + dp(22f);
        RectF linkBox = new RectF(w - side - linkW, headerY - dp(14f), w - side, headerY + dp(6f));
        stroke.setColor(color);
        stroke.setAlpha(160);
        stroke.setStrokeWidth(dp(1f));
        canvas.drawRoundRect(linkBox, dp(12f), dp(12f), stroke);
        text.setColor(color);
        text.setAlpha(220);
        canvas.drawText(link, linkBox.left + dp(11f), headerY, text);

        float unit = Math.min(w * 0.26f, dp(108f));
        float cx = w * 0.5f;
        float cy = headerY + dp(28f) + unit * 1.05f;
        headHit.set(cx - unit * 1.15f, cy - unit * 1.2f, cx + unit * 1.15f, cy + unit * 1.15f);
        if (meshGl != null && meshGl.isLive()) {
            meshGl.place(Math.round(headHit.left), Math.round(headHit.top),
                    Math.round(headHit.right), Math.round(headHit.bottom));
        }
        ensureGlow(unit);
        glowPaint.setShader(ivieOn ? ivieGlow : obiGlow);
        glowPaint.setAlpha((int) (180 * faceAlpha));
        canvas.drawCircle(cx, cy + unit * 0.15f, unit * 1.25f, glowPaint);
        float pose = 0f;
        float away = (ivieOn ? -12f : 12f) * dissolve.value;
        
        if (headWeb != null) {
            headWeb.drive(ivieOn, rig.smile.value, rig.jaw.value, (rig.browL.value + rig.browR.value) * 0.5f, rig.yaw.value);
        }

        boolean gl = meshGl != null && meshGl.isLive();
        if (gl) {
            meshGl.publish(ivieOn, pose + away + rig.yaw.value, unit,
                    rig.smile.value, rig.jaw.value, rig.browL.value, rig.browR.value,
                    rig.time(), faceAlpha);
        } else {
            drawSkull(canvas, ivieOn ? ivieHead : obiHead, rig, cx, cy, unit, color, faceAlpha, pose + away);
        }

        float nameY = cy + unit * 1.22f;
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(INK);
        text.setLetterSpacing(0.04f);
        text.setTextSize(dp(26f));
        text.setAlpha((int) (240 * Math.max(faceAlpha, 0.5f)));
        canvas.drawText(ivieOn ? "Ivie" : "Obi", cx, nameY, text);
        text.setTextSize(dp(12f));
        text.setColor(0xFF9A9A9A);
        text.setLetterSpacing(0.02f);
        text.setAlpha(180);
        canvas.drawText(ivieOn ? "Thoughtful Assistant" : "Analytical Assistant", cx, nameY + dp(20f), text);

        float signalY = nameY + dp(48f);
        if (staged) {
            drawOrb(canvas, cx, signalY, color);
        } else {
            drawSignal(canvas, cx, signalY, color, rig);
        }

        String status = statusLabel(rig.mode());
        text.setTextSize(dp(11f));
        text.setLetterSpacing(0.06f);
        float statusW = text.measureText(status) + dp(28f);
        float pillY = signalY + dp(22f);
        RectF statusBox = new RectF(cx - statusW / 2f, pillY - dp(12f), cx + statusW / 2f, pillY + dp(10f));
        stroke.setColor(color);
        stroke.setAlpha(170);
        canvas.drawRoundRect(statusBox, dp(14f), dp(14f), stroke);
        text.setColor(color);
        text.setAlpha(230);
        canvas.drawText(status, cx, pillY + dp(4f), text);

        float cardTop = statusBox.bottom + dp(18f);
        int rows = staged ? 4 : Math.max(activityCount, 1);
        float cardH = staged ? dp(176f) : dp(36f) + rows * dp(26f) + dp(18f);
        float cardMax = h - padBottom - dp(88f) - cardTop;
        if (cardH > cardMax) {
            cardH = Math.max(dp(72f), cardMax);
        }
        RectF card = new RectF(side, cardTop, w - side, cardTop + cardH);
        fill.setColor(0xFF0A0A0A);
        fill.setAlpha(255);
        canvas.drawRoundRect(card, dp(16f), dp(16f), fill);
        stroke.setColor(color);
        stroke.setAlpha(50);
        canvas.drawRoundRect(card, dp(16f), dp(16f), stroke);
        if (staged) {
            drawDraft(canvas, card, color);
        } else {
            drawActivity(canvas, card, color);
        }

        float barTop = h - padBottom - dp(60f);
        composer.set(side, barTop, w - side, barTop + dp(48f));
        stroke.setColor(0xFF3A3A3A);
        stroke.setAlpha(220);
        canvas.drawRoundRect(composer, dp(24f), dp(24f), stroke);
        float micCx = composer.left + dp(24f);
        float micCy = composer.centerY();
        micHit.set(composer.left, composer.top, composer.left + dp(48f), composer.bottom);
        keyHit.set(composer.right - dp(48f), composer.top, composer.right, composer.bottom);
        stroke.setColor(color);
        stroke.setAlpha(listening ? 230 : 160);
        canvas.drawCircle(micCx, micCy, dp(11f), stroke);
        fill.setColor(color);
        fill.setAlpha(listening ? 200 : 70);
        canvas.drawCircle(micCx, micCy, listening ? dp(4.5f) : dp(3f), fill);
        drawKeys(canvas, composer.right - dp(18f), micCy, color);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFF8A8A8A);
        text.setTextSize(dp(13f));
        text.setLetterSpacing(0.01f);
        text.setAlpha(notice.length() > 0 ? 210 : 160);
        String hint = notice.length() > 0 ? notice : "Speak aloud or type a command...";
        canvas.drawText(hint, composer.left + dp(46f), micCy + dp(4f), text);
    }

    private String statusLabel(FaceMode mode) {
        if (staged || mode == FaceMode.APPROVAL) {
            return "AWAITING VERBAL CONFIRMATION";
        }
        if (listening || mode == FaceMode.LISTENING) {
            return "LISTENING  ·  TIER 0 ACTIVE";
        }
        if (mode == FaceMode.THINKING) {
            return "THINKING";
        }
        if (mode == FaceMode.SPEAKING) {
            return "SPEAKING";
        }
        if (mode == FaceMode.DONE) {
            return "COMPLETED";
        }
        if (mode == FaceMode.ERROR) {
            return "ERROR";
        }
        return "IDLE  ·  TIER 0";
    }

    private void drawSignal(Canvas canvas, float cx, float y, int color, FaceRig rig) {
        int count = 19;
        float gap = dp(5.2f);
        boolean live = listening || rig.mode() == FaceMode.SPEAKING || rig.mode() == FaceMode.LISTENING;
        stroke.setColor(color);
        stroke.setStrokeWidth(dp(1.6f));
        for (int i = 0; i < count; i++) {
            float env = (float) Math.sin((i + 0.5f) / count * Math.PI);
            float wobble = (float) Math.sin(rig.time() * (live ? 8f : 1.4f) + i * 0.85f);
            float amp = live ? 0.55f + Math.abs(wobble) * 0.9f : 0.42f + Math.abs(wobble) * 0.28f;
            float half = dp(20f) * env * amp;
            if (half < dp(1.4f)) {
                half = dp(1.4f);
            }
            stroke.setAlpha(live ? 230 : 150);
            float x = cx + (i - (count - 1) / 2f) * gap;
            canvas.drawLine(x, y - half, x, y + half, stroke);
        }
    }

    private void drawKeys(Canvas canvas, float right, float cy, int color) {
        stroke.setColor(color);
        stroke.setAlpha(170);
        stroke.setStrokeWidth(dp(1.15f));
        float w = dp(16f);
        float h = dp(11f);
        float left = right - w;
        float top = cy - h / 2f;
        canvas.drawRoundRect(new RectF(left, top, right, top + h), dp(1.6f), dp(1.6f), stroke);
        stroke.setStrokeWidth(dp(1f));
        for (int row = 0; row < 2; row++) {
            float y = top + dp(3.2f) + row * dp(3.6f);
            int keys = row == 0 ? 4 : 3;
            float span = w - dp(4f);
            float gap = span / keys;
            float start = left + dp(2.4f) + (row == 1 ? gap * 0.35f : 0f);
            for (int k = 0; k < keys; k++) {
                float x = start + k * gap;
                canvas.drawLine(x, y, x + dp(1.6f), y, stroke);
            }
        }
    }

    private void drawOrb(Canvas canvas, float cx, float y, int color) {
        fill.setColor(color);
        fill.setAlpha(220);
        canvas.drawCircle(cx, y, dp(4f), fill);
        stroke.setColor(color);
        stroke.setAlpha(140);
        stroke.setStrokeWidth(dp(1f));
        for (int i = 0; i < 10; i++) {
            double ang = i / 10.0 * Math.PI * 2.0;
            canvas.drawCircle(cx + (float) Math.cos(ang) * dp(12f), y + (float) Math.sin(ang) * dp(12f), dp(1.3f), fill);
        }
    }

    private void drawActivity(Canvas canvas, RectF card, int color) {
        sendHit.setEmpty();
        shortHit.setEmpty();
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(dp(11f));
        text.setLetterSpacing(0.12f);
        text.setColor(color);
        text.setAlpha(200);
        canvas.drawText("LIVE ACTIVITY", card.left + dp(14f), card.top + dp(22f), text);
        float y = card.top + dp(48f);
        if (activityCount == 0) {
            text.setColor(0xFF8A8A8A);
            text.setLetterSpacing(0.02f);
            text.setTextSize(dp(13f));
            canvas.drawText("Waiting for a command", card.left + dp(14f), y, text);
            return;
        }
        int start = Math.max(0, activityCount - 5);
        for (int i = start; i < activityCount; i++) {
            if (y > card.bottom - dp(16f)) {
                break;
            }
            fill.setColor(color);
            fill.setAlpha(180);
            canvas.drawCircle(card.left + dp(18f), y - dp(4f), dp(2.4f), fill);
            text.setColor(INK);
            text.setAlpha(220);
            text.setTextSize(dp(12f));
            text.setLetterSpacing(0.01f);
            String row = activity[i];
            float max = card.width() - dp(78f);
            canvas.drawText(fit(row, max), card.left + dp(28f), y, text);
            text.setColor(0xFF8A8A8A);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(activityTime[i], card.right - dp(12f), y, text);
            text.setTextAlign(Paint.Align.LEFT);
            y += dp(26f);
        }
    }

    private void drawDraft(Canvas canvas, RectF card, int color) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(dp(11f));
        text.setLetterSpacing(0.08f);
        text.setColor(color);
        text.setAlpha(210);
        canvas.drawText("STAGED DRAFT", card.left + dp(14f), card.top + dp(22f), text);
        int width = Math.max(1, (int) (card.width() - dp(28f)));
        String body = draft.length() > 0 ? draft : caption;
        if (captionLayout == null || captionWidth != width) {
            captionWidth = width;
            captionPaint.setTextAlign(Paint.Align.LEFT);
            captionPaint.setColor(INK);
            captionPaint.setTextSize(dp(14f));
            captionLayout = StaticLayout.Builder.obtain(body, 0, body.length(), captionPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(5)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
        }
        canvas.save();
        canvas.translate(card.left + dp(14f), card.top + dp(34f));
        captionLayout.draw(canvas);
        canvas.restore();
        text.setTextSize(dp(13f));
        text.setLetterSpacing(0.02f);
        float actionY = card.bottom - dp(18f);
        text.setColor(color);
        canvas.drawText("Send it", card.left + dp(14f), actionY, text);
        float sendW = text.measureText("Send it");
        sendHit.set(card.left + dp(8f), actionY - dp(18f), card.left + dp(20f) + sendW, actionY + dp(8f));
        text.setColor(0xFF9A9A9A);
        canvas.drawText("or", card.left + dp(28f) + sendW, actionY, text);
        text.setColor(color);
        float shortX = card.left + dp(48f) + sendW;
        canvas.drawText("Make it shorter", shortX, actionY, text);
        shortHit.set(shortX - dp(6f), actionY - dp(18f), shortX + text.measureText("Make it shorter") + dp(6f), actionY + dp(8f));
    }

    private String fit(String value, float max) {
        if (value == null) {
            return "";
        }
        if (text.measureText(value) <= max) {
            return value;
        }
        String ellipsis = "...";
        int end = value.length();
        while (end > 1 && text.measureText(value.substring(0, end) + ellipsis) > max) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }

    private static String clock() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = calendar.get(java.util.Calendar.MINUTE);
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
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
        head.deform(rig.smile.value, rig.jaw.value, rig.browL.value, rig.browR.value, rig.time(), deformed);
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
                dragged = false;
                micSwipe = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - downX;
                float dy = y - downY;
                if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    decided = true;
                    micSwipe = Math.abs(dx) > Math.abs(dy) * 1.2f && !composer.contains(downX, downY);
                }
                if (decided && !micSwipe && headHit.contains(downX, downY)) {
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
                float travel = Math.abs(x - downX);
                if (micSwipe && travel > dp(64f)) {
                    if (listener != null) {
                        listener.onMic();
                    }
                    return true;
                }
                if (dragged || Math.hypot(x - downX, y - downY) > dp(14f) || listener == null) {
                    return true;
                }
                if (micHit.contains(x, y)) {
                    listener.onMic();
                } else if (keyHit.contains(x, y) || composer.contains(x, y)) {
                    listener.onType();
                } else if (staged && sendHit.contains(x, y)) {
                    listener.onDraft(true);
                } else if (staged && shortHit.contains(x, y)) {
                    listener.onDraft(false);
                } else if (headHit.contains(x, y)) {
                    listener.onHeadTap();
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
        choreographer.postFrameCallback(this);
    }

    private void stopFrames() {
        framePosted = false;
        choreographer.removeFrameCallback(this);
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
