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
    private final WirePainter obiHead = new WirePainter(WireHead.create(false));
    private final WirePainter ivieHead = new WirePainter(WireHead.create(true));
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

    private boolean online;

    public void setListener(StageListener listener) {
        this.listener = listener;
    }

    /** Real network state, shown in the badge. */
    public void setOnline(boolean value) {
        online = value;
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

        String link = staged ? "LOCK: STAGED" : online ? "ONLINE" : "OFFLINE";
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

        float faceRoom = h - padBottom - dp(300f) - headerY;
        float unit = Math.max(dp(48f), Math.min(w * 0.30f, faceRoom * 0.30f));
        float cx = w * 0.5f;
        float cy = headerY + dp(16f) + unit * 1.25f;
        headHit.set(cx - unit * 1.3f, cy - unit * 1.3f, cx + unit * 1.3f, cy + unit * 1.6f);
        ensureGlow(unit);
        canvas.save();
        canvas.translate(cx, cy + unit * 0.1f);
        glowPaint.setShader(ivieOn ? ivieGlow : obiGlow);
        glowPaint.setAlpha((int) (150 * faceAlpha));
        canvas.drawCircle(0f, 0f, unit * 1.35f, glowPaint);
        canvas.restore();
        float away = (ivieOn ? -14f : 14f) * dissolve.value;
        WirePainter painter = ivieOn ? ivieHead : obiHead;
        painter.draw(canvas, rig, away, cx, cy, unit, color, faceAlpha, density,
                cy + unit * 1.05f, cy + unit * 1.62f);

        float nameY = cy + unit * 1.62f + dp(16f);
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
