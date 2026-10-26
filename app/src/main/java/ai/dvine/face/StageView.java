package ai.dvine.face;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;

import java.util.Random;

import ai.dvine.face.nano.DotBody;
import ai.dvine.face.nano.DotHead;
import ai.dvine.face.nano.Forms;
import ai.dvine.face.nano.NanoPainter;
import ai.dvine.face.nano.Swarm;

/**
 * The whole app on screen: one persona on black, their hands, an explanation
 * canvas, sparkles and a faint subtitle. No buttons, no menus.
 */
public final class StageView extends View implements Choreographer.FrameCallback {
    private static final int OBI = 0xFF00E5FF;
    private static final int IVIE = 0xFFFFB300;
    private static final int GREY = 0xFF5A5A5A;

    private final FaceRig obi = new FaceRig(false);
    private final FaceRig ivie = new FaceRig(true);
    // everything is nano dots: one swarm for the persona, one for drawings and objects.
    // Heads and bodies are sculpted on a background thread; until then the dots wait as a cloud.
    private volatile Forms.Head obiFace;
    private volatile Forms.Head ivieFace;
    private volatile DotBody obiBody;
    private volatile DotBody ivieBody;
    private final Look[] looks = {Look.defaults(false), Look.defaults(true)};
    private final int[] lookToken = new int[2];
    private String bodyPose = "stand";
    private final Swarm swarm = new Swarm(10000);
    private final NanoPainter nano = new NanoPainter(10000);
    private final Swarm drawSwarm = new Swarm(4000);
    private final NanoPainter drawNano = new NanoPainter(4000);
    private Swarm.Form drawForm;
    private float drawUntil;
    private boolean bodyMode;
    private final HandPainter hands = new HandPainter();
    private final WireHead.Pose scratchPose = new WireHead.Pose();
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF headHit = new RectF();

    private StageListener listener;
    private boolean showingIvie;
    private boolean pendingIvie;
    private int phase;
    private final Spring1D dissolve = new Spring1D(8.2f, 0.95f, 1f);
    private final Spring1D sketchMix = new Spring1D(5f, 0.95f, 0f);
    private Runnable pendingReady;
    private int readyToken;
    private int firedToken = -1;
    private boolean awake;

    private String subtitle = "";
    private StaticLayout subLayout;
    private int subWidth;
    private boolean subtitles = true;
    private boolean online;
    private boolean listening;

    private Sketch sketch;
    private float sketchStart;
    private float clock;

    // sparkles
    private static final int SPARKS = 48;
    private final float[] sx = new float[SPARKS];
    private final float[] sy = new float[SPARKS];
    private final float[] svx = new float[SPARKS];
    private final float[] svy = new float[SPARKS];
    private final float[] slife = new float[SPARKS];
    private final Random random = new Random(7L);
    private float sparkDebt;

    private float density = 1f;
    private int padTop;
    private int padBottom;
    private long lastNanos;
    private boolean framePosted;
    private RadialGradient obiGlow;
    private RadialGradient ivieGlow;
    private float glowUnit;
    private float lastCx;
    private float lastCy;
    private float lastUnit;

    private float downX;
    private float downY;
    private long downAt;
    private boolean decided;
    private boolean dragged;
    private boolean swipe;
    private boolean longFired;
    private final Runnable longPress = () -> {
        longFired = true;
        if (listener != null) {
            listener.onType();
        }
    };

    public StageView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        subPaint.setColor(0xFFBDBDBD);
        subPaint.setTextSize(15f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(16f * density);
        setContentDescription("DVINE. Swipe anywhere to speak. Tap the face to hear them. Long-press to type.");
        sculpt(false);
        sculpt(true);
    }

    /** Sculpts a persona's head and body off the UI thread, then swaps them in. */
    private void sculpt(boolean forIvie) {
        final int side = forIvie ? 1 : 0;
        final int token = ++lookToken[side];
        final Look look = looks[side].copy();
        Thread t = new Thread(() -> {
            DotHead head = new DotHead(forIvie, look);
            Forms.Head face = new Forms.Head(head);
            DotBody body = new DotBody(head);
            post(() -> {
                if (token != lookToken[side]) {
                    return; // a newer look is on its way
                }
                body.setPose(bodyPose);
                if (forIvie) {
                    ivieFace = face;
                    ivieBody = body;
                } else {
                    obiFace = face;
                    obiBody = body;
                }
                if (forIvie == showingIvie && awake) {
                    redraw();
                }
            });
        }, "dvine-sculpt");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    // ------------------------------------------------------------ public API

    public void setListener(StageListener listener) {
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
        if (mode == FaceMode.THINKING) {
            sparkDebt += 4f;
        } else if (mode == FaceMode.DONE) {
            burst(14);
        }
    }

    public void setOnline(boolean value) {
        online = value;
    }

    public void setListening(boolean on) {
        listening = on;
        if (on) {
            rig().setMode(FaceMode.LISTENING);
        }
    }

    public void setSubtitle(String text) {
        subtitle = text == null ? "" : text;
        subLayout = null;
    }

    public void setSubtitles(boolean on) {
        subtitles = on;
    }

    /**
     * Redraws a persona from a new look. The head and body are re-sculpted
     * once in the background, then the dots fly into the new shape.
     */
    public void setLook(boolean forIvie, Look look) {
        int side = forIvie ? 1 : 0;
        if (look == null || !looks[side].shapeDiffers(look)) {
            return;
        }
        looks[side] = look.copy();
        sculpt(forIvie);
    }

    /** Full standing body (true) or the face (false). The swarm flies between them. */
    public void setBodyMode(boolean on) {
        if (on != bodyMode) {
            bodyMode = on;
            swarm.scatter(1.2f);
        }
    }

    /** Full body in a pose ("tpose", "squat", "sit", "run"…); the dots flow between poses. */
    public void setPose(String pose) {
        if (pose == null) {
            return;
        }
        bodyPose = pose;
        setBodyMode(true);
        DotBody o = obiBody, i = ivieBody;
        if (o != null) {
            o.setPose(pose);
        }
        if (i != null) {
            i.setPose(pose);
        }
    }

    /** One slow turn of the full body, like orbiting a model in a 3D studio. */
    public void turnAround() {
        setBodyMode(true);
        DotBody b = showingIvie ? ivieBody : obiBody;
        if (b != null) {
            b.turnAround();
        }
    }

    /** The nano dots fly apart and reassemble: "redraw yourself". */
    public void redraw() {
        swarm.scatter(4f);
        burst(16);
    }

    /** A slowly turning 3D object (globe, cube, pyramid, torus, dna, atom, sphere) built by the swarm. */
    public void showObject(String kind) {
        sketch = null;
        drawForm = new Forms.Object3D(kind);
        sketchStart = clock;
        drawUntil = clock + 22f;
    }

    public void gesture(String key) {
        if (bodyMode) {
            // the whole body answers instead of floating hands
            String k = key == null ? "" : key;
            if (k.startsWith("👋")) {
                setPose("wave");
            } else if (k.startsWith("👉") || k.startsWith("☝")) {
                setPose("point");
            } else if (k.startsWith("🙌") || k.startsWith("🙏")) {
                setPose(k.startsWith("🙌") ? "arms up" : "bow");
            }
            return;
        }
        hands.show(key, 2.8f);
    }

    public void showSketch(Sketch s) {
        sketch = s;
        drawForm = new Forms.Drawing(s);
        sketchStart = clock;
        drawUntil = clock + s.length + 18f;
    }

    public void clearSketch() {
        sketch = null;
        drawForm = null;
    }

    /** A soft burst of sparkles: answer ready, copy done, hand-off done. */
    public void burst(int count) {
        for (int i = 0; i < count; i++) {
            spawn(true);
        }
    }

    /** Draws the persona in from scattered points the first time. */
    public void wake() {
        awake = true;
    }

    public void ensureFace(boolean wantIvie, Runnable onReady) {
        pendingIvie = wantIvie;
        pendingReady = onReady;
        readyToken++;
        if (wantIvie == showingIvie && phase == 0 && dissolve.value < 0.05f) {
            firedToken = readyToken;
            pendingReady = null;
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        if (phase == 0) {
            phase = wantIvie == showingIvie ? 2 : 1;
        }
    }

    // ------------------------------------------------------------ frame

    @Override
    public void doFrame(long nanos) {
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (nanos - lastNanos) / 1_000_000_000f);
        lastNanos = nanos;
        tick(dt);
        invalidate();
        if (framePosted) {
            choreographer.postFrameCallback(this);
        }
    }

    private FaceRig rig() {
        return showingIvie ? ivie : obi;
    }

    private void tick(float dt) {
        clock += dt;
        if (!awake) {
            dissolve.value = 1f;
        } else if (phase == 1) {
            dissolve.step(1f, dt);
            if (dissolve.value > 0.97f) {
                showingIvie = pendingIvie;
                phase = 2;
                rig().setMode(FaceMode.CONTENT);
                swarm.scatter(2.2f); // the dots fly apart and become the other persona
                burst(10);
            }
        } else if (phase == 2 || dissolve.value > 0.03f) {
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
                phase = pendingIvie != showingIvie ? 1 : 0;
            }
        } else if (pendingIvie != showingIvie) {
            phase = 1;
        }
        boolean sketching = drawForm != null && clock < drawUntil;
        if (drawForm != null && !sketching) {
            drawForm = null;
            sketch = null;
        }
        sketchMix.step(sketching ? 1f : 0f, dt);
        obi.tick(dt);
        ivie.tick(dt);
        FaceRig r = rig();
        Swarm.Form form = null;
        WireHead.Pose pose = scratchPose;
        if (bodyMode && !sketching) {
            DotBody b = showingIvie ? ivieBody : obiBody;
            if (b != null) {
                form = b;
                pose = b.pose();
            }
        } else {
            Forms.Head f = showingIvie ? ivieFace : obiFace;
            if (f != null) {
                form = f;
                pose = f.pose;
            }
        }
        pose.jaw = r.jaw.value;
        pose.smile = r.smile.value;
        pose.browL = r.browL.value;
        pose.browR = r.browR.value;
        pose.blink = r.blink.value;
        pose.lookX = r.lookX.value;
        pose.lookY = r.lookY.value;
        pose.breathe = (float) Math.sin(clock * 1.35f) * 0.010f;
        swarm.shape(form, clock);
        swarm.step(dt);
        drawSwarm.shape(drawForm, clock);
        drawSwarm.step(dt);
        hands.tick(dt);
        FaceMode m = rig().mode();
        if (m == FaceMode.THINKING) {
            sparkDebt += dt * 9f;
        } else if (m == FaceMode.SPEAKING) {
            sparkDebt += dt * 2.5f;
        }
        while (sparkDebt >= 1f) {
            sparkDebt -= 1f;
            spawn(false);
        }
        for (int i = 0; i < SPARKS; i++) {
            if (slife[i] <= 0f) {
                continue;
            }
            slife[i] -= dt * 0.55f;
            sx[i] += svx[i] * dt;
            sy[i] += svy[i] * dt;
            svy[i] -= dt * 0.12f;
        }
    }

    private void spawn(boolean burst) {
        for (int i = 0; i < SPARKS; i++) {
            if (slife[i] <= 0f) {
                double a = random.nextDouble() * Math.PI * 2;
                float r = 0.9f + random.nextFloat() * 0.5f;
                sx[i] = (float) Math.cos(a) * r;
                sy[i] = (float) Math.sin(a) * r * 1.2f;
                float speed = burst ? 0.5f + random.nextFloat() * 0.6f : 0.12f + random.nextFloat() * 0.15f;
                svx[i] = (float) Math.cos(a) * speed * 0.6f;
                svy[i] = burst ? (float) Math.sin(a) * speed : 0.25f + random.nextFloat() * 0.25f;
                slife[i] = 1f;
                return;
            }
        }
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
        float mix = sketchMix.value;

        float usable = h - padTop - padBottom;
        float bigUnit = Math.min(w * 0.30f, usable * 0.17f);
        float smallUnit = bigUnit * 0.55f;
        float unit = lerp(bigUnit, smallUnit, mix);
        float cx = w * 0.5f;
        float cy = padTop + lerp(usable * 0.40f, smallUnit * 1.35f + 12f * density, mix);
        boolean body = bodyMode && mix < 0.05f;
        float floorY = -2.25f;
        if (body) {
            // the body arrives scaled to camera units, feet on DotBody.FLOOR_OUT
            unit = Math.min(w * 0.42f, usable * 0.4f);
            cy = padTop + usable * 0.5f;
            floorY = DotBody.FLOOR_OUT - 0.02f;
        }
        lastCx = cx;
        lastCy = cy;
        lastUnit = unit;
        if (body) {
            headHit.set(cx - unit * 0.6f, cy - unit * 1.1f, cx + unit * 0.6f, cy + unit * 1.1f);
        } else {
            headHit.set(cx - unit * 1.3f, cy - unit * 1.3f, cx + unit * 1.6f, cy + unit * 1.6f);
        }

        // online dot: persona colour when online, grey when not
        fill.setColor(online ? color : GREY);
        fill.setAlpha(online ? 200 : 140);
        canvas.drawCircle(w * 0.5f, padTop + 10f * density, 3f * density, fill);

        ensureGlow(unit);
        canvas.save();
        canvas.translate(cx, cy + unit * 0.1f);
        glowPaint.setShader(ivieOn ? ivieGlow : obiGlow);
        glowPaint.setAlpha((int) (140 * faceAlpha * Math.max(0.4f, rig.glow.value)));
        canvas.drawCircle(0f, 0f, unit * 1.35f, glowPaint);
        canvas.restore();

        // a Blender-like floor, then the persona as nano dots facing the user
        float yaw = body ? rig.yaw.value * 0.3f : rig.yaw.value;
        float pitch = body ? 8f : rig.pitch.value + 6f;
        nano.drawGrid(canvas, yaw * 0.3f, 14f, cx, cy, unit, floorY, 1f - mix, density);
        nano.drawSwarm(canvas, swarm, yaw, pitch, cx, cy, unit, color,
                Math.max(0.35f, faceAlpha) * clamp(rig.glow.value + 0.25f, 0.5f, 1.2f), density, clock);
        if (!body) {
            hands.draw(canvas, nano, cx, cy, unit, color, faceAlpha, density);
        }
        drawSparks(canvas, cx, cy, unit, color);

        if (listening) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 5f);
            stroke.setColor(color);
            stroke.setStrokeWidth(1.5f * density);
            stroke.setAlpha((int) (60 + 80 * pulse));
            canvas.drawCircle(cx, cy + unit * 0.1f, unit * (1.45f + 0.04f * pulse), stroke);
        }

        if (drawForm != null && mix > 0.05f) {
            float top = cy + smallUnit * 1.9f;
            float bottom = h - padBottom - 70f * density;
            float half = Math.min(w * 0.42f, (bottom - top) * 0.5f);
            float dcy = (top + bottom) * 0.5f;
            boolean solid = drawForm instanceof Forms.Object3D;
            nano.drawGrid(canvas, 0f, 14f, cx, dcy, half, solid ? -1.15f : -1.25f, mix * 0.8f, density);
            drawNano.drawSwarm(canvas, drawSwarm, 0f, 0f, cx, dcy + half * 0.35f, half, color, mix, density, clock);
            if (sketch != null) {
                drawLabels(canvas, sketch, clock - sketchStart, cx, dcy, half, color, mix);
            }
        }

        if (subtitles && subtitle.length() > 0) {
            int width = (int) (w - 48f * density);
            if (subLayout == null || subWidth != width) {
                subWidth = width;
                subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length(), subPaint, width)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            }
            canvas.save();
            canvas.translate(24f * density, h - padBottom - subLayout.getHeight() - 22f * density);
            subPaint.setAlpha(200);
            subLayout.draw(canvas);
            canvas.restore();
        }
    }

    private void drawSparks(Canvas canvas, float cx, float cy, float unit, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(1.2f * density);
        for (int i = 0; i < SPARKS; i++) {
            if (slife[i] <= 0f) {
                continue;
            }
            float x = cx + sx[i] * unit;
            float y = cy - sy[i] * unit;
            float s = (1.5f + 3.5f * slife[i]) * density;
            stroke.setAlpha((int) (210 * slife[i] * slife[i]));
            canvas.drawLine(x - s, y, x + s, y, stroke);
            canvas.drawLine(x, y - s, x, y + s, stroke);
        }
    }

    /** Sketch labels as glowing text; the strokes themselves are nano dots. */
    private void drawLabels(Canvas canvas, Sketch s, float t, float cx, float cy, float half, int color, float alpha) {
        labelPaint.setColor(color);
        labelPaint.setTextSize(Math.max(14f * density, half * 0.09f));
        labelPaint.setShadowLayer(8f * density, 0f, 0f, color);
        for (Sketch.Stroke st : s.strokes) {
            if (st.kind != Sketch.Kind.LABEL) {
                continue;
            }
            float p = clamp((t - st.start) / Math.max(0.01f, st.duration), 0f, 1f);
            if (p <= 0f) {
                continue;
            }
            labelPaint.setAlpha((int) (230 * alpha * p));
            canvas.drawText(st.text, cx + st.pts[0] * half,
                    cy - st.pts[1] * half + half * 0.35f + labelPaint.getTextSize() * 0.35f, labelPaint);
        }
    }

    private void ensureGlow(float unit) {
        if (obiGlow != null && Math.abs(unit - glowUnit) < 1f) {
            return;
        }
        glowUnit = unit;
        obiGlow = new RadialGradient(0f, 0f, unit * 1.35f, new int[]{0x5500E5FF, 0x00000000}, null, Shader.TileMode.CLAMP);
        ivieGlow = new RadialGradient(0f, 0f, unit * 1.35f, new int[]{0x55FFB300, 0x00000000}, null, Shader.TileMode.CLAMP);
    }

    // ------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float slop = 10f * density;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downAt = event.getEventTime();
                decided = false;
                dragged = false;
                swipe = false;
                longFired = false;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout() + 200L);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = x - downX;
                float dy = y - downY;
                if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    decided = true;
                    removeCallbacks(longPress);
                    boolean onHead = headHit.contains(downX, downY);
                    swipe = !onHead || Math.abs(dx) > Math.abs(dy) * 2.2f && Math.abs(dx) > 40f * density;
                }
                if (decided && !swipe) {
                    dragged = true;
                    rig().setDrag(dx / density * 0.11f, -dy / density * 0.08f, true);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                rig().setDrag(0f, 0f, false);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || longFired || listener == null) {
                    return true;
                }
                float travel = (float) Math.hypot(x - downX, y - downY);
                if (swipe && travel > 56f * density) {
                    listener.onMic();
                } else if (!dragged && travel < 14f * density && headHit.contains(x, y)) {
                    listener.onHeadTap();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    // ------------------------------------------------------------ lifecycle

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
        android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        padTop = bars.top;
        padBottom = bars.bottom;
        return insets;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
