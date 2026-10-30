package ai.dvine.stage;

import android.content.Context;
import android.graphics.BlurMaskFilter;
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

import ai.dvine.render.pod.PodLayout;
import ai.dvine.render.pod.PodMotion;

/**
 * The whole app on screen: the pod, floating in space, and above it whatever the user asks DVINE to draw or build,
 * in 3D. There is no face. One finger on the thing drawn turns it; two fingers pinch to zoom in for detail or out,
 * and move it. The pod shows what DVINE is doing (listening, thinking, speaking), and while it thinks its inner
 * voice writes each step along the top of the screen, the older lines fading away like smoke. Captions and the
 * download bar are particles in the same scene. Touch and hold to speak, let go to send; double-tap to type.
 * This view is a transparent overlay over the GL scene ({@link ai.dvine.render.PodView}); if the GL scene can't
 * run, it draws a simple pod itself.
 */
public final class StageView extends View implements Choreographer.FrameCallback, ai.dvine.engine.BodyPort {
    public static final String IVIE = "ivie", OBI = "obi", IBIKO = "ibiko";

    private StageMode mode = StageMode.CONTENT;
    private final PodMotion motion = new PodMotion();
    private PodLayout layout = PodLayout.compute(1080, 1920, 0, 0);
    private ai.dvine.render.StageRenderer renderer;
    private ai.dvine.render.PodView glView;
    /** Drawings, captions and the download bar, as particles in the scene. */
    public final ai.dvine.render.field.ParticleDisplay display = new ai.dvine.render.field.ParticleDisplay();
    /** The thing being designed in 3D ("design me a house"), shown above the pod while it's worked on. */
    public final ai.dvine.create.ObjectSession objects = new ai.dvine.create.ObjectSession();
    private boolean objectOnShow;
    private SolidSurface solidMesh;
    private ai.dvine.create.PartTree partTree;
    private final java.util.HashSet<String> treeCollapsed = new java.util.HashSet<String>();
    private boolean treeDown;
    /** Finger and spin pose for a triangle mesh. Particles keep their own pose. */
    private float meshYaw, meshPitch, meshSize = 1f, meshDx, meshDy, meshSpin, meshSizeAtPinch;
    private final ai.dvine.render.pod.Space fallbackSky = new ai.dvine.render.pod.Space();
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint voicePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint smokePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    // the download bar (particles in the scene)
    private boolean dlShown, dlBusy;
    private int dlPercent;
    private String dlTitle = "", dlDetail = "";
    private float barAge;
    /** Live activity: the latest few {tag, text, time} entries, newest last. */
    private final java.util.ArrayList<String[]> activityLog = new java.util.ArrayList<String[]>();

    // the inner voice: what DVINE is thinking, line by line, the oldest drifting up and fading
    private static final int VOICE_LINES = 6;
    private final java.util.ArrayList<String> voiceText = new java.util.ArrayList<String>();
    private final java.util.ArrayList<Float> voiceBorn = new java.util.ArrayList<Float>();
    private float voiceQuietSince = -100f;
    /** The last inner-voice line is a live progress line and may be replaced. */
    private boolean voiceProgress;

    private StageListener listener;
    private String speaker = IVIE;
    private String subtitle = "";
    private StaticLayout subLayout;
    private int subWidth;
    private boolean subtitles = true;
    private boolean online;
    private boolean listening;
    private float clock;
    private float density = 1f;
    private int padTop;
    private int padBottom;
    private long lastNanos;
    private boolean framePosted;
    private String shownCaption, shownBar;
    private boolean fallbackReported;
    private int layoutTop = -1, layoutBottom = -1;

    // touch
    private float downX, downY, lastX, lastY;
    private boolean decided, dragged, longFired, holding, twoFingers;
    private float pinchStart, sizeAtPinch, midX, midY;
    // a long press anywhere speaks (until the finger lifts); a double tap anywhere opens typing
    private final Runnable longPress = () -> {
        longFired = true;
        holding = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) {
            listener.onMic();
        }
    };
    private static final long DOUBLE_TAP_MS = 280L;
    private long lastTapAt;
    private float lastTapX, lastTapY;
    private Runnable pendingTap;

    public StageView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        subPaint.setColor(0xFFBDBDBD);
        subPaint.setTextSize(13f * density);
        voicePaint.setColor(0xFFBFE6FF);
        voicePaint.setTextSize(12.5f * density);
        voicePaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        smokePaint.set(voicePaint);
        smokePaint.setMaskFilter(new BlurMaskFilter(2.2f * density, BlurMaskFilter.Blur.NORMAL));
        setContentDescription("DVINE. Touch and hold anywhere to speak, let go to send. Double-tap to type.");
    }

    // ------------------------------------------------------------ public API

    public void setListener(StageListener listener) {
        this.listener = listener;
    }

    /** Whether Ivie is the voice speaking now. */
    public boolean isIvie() {
        return IVIE.equals(speaker);
    }

    /** The persona whose voice speaks now: "ivie", "obi" or "ibiko". */
    public String agent() {
        return speaker;
    }

    public void setMode(StageMode m) {
        mode = m;
        if (m == StageMode.DONE) {
            motion.flash();
        }
        if (m != StageMode.THINKING) {
            voiceQuietSince = clock;
        }
    }

    public StageMode mode() {
        return mode;
    }

    /** Attaches the GL scene. */
    public void attach(ai.dvine.render.PodView view) {
        glView = view;
        renderer = view.renderer;
        renderer.displayDensity = density;
    }

    /** A line of the inner voice: what DVINE is doing now ("Heard: …", "Checking I can do that…"). */
    public void innerVoice(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        String t = line.trim();
        if (!voiceText.isEmpty() && voiceText.get(voiceText.size() - 1).equals(t)) {
            return;
        }
        voiceText.add(t);
        voiceBorn.add(clock);
        voiceProgress = false;
        while (voiceText.size() > VOICE_LINES + 2) {
            voiceText.remove(0);
            voiceBorn.remove(0);
        }
        voiceQuietSince = mode == StageMode.THINKING ? Float.MAX_VALUE : clock;
        invalidate();
    }

    /**
     * Replaces the live progress line ("Reading your words · 2s") so a long
     * thought does not fill the screen with the same step.
     */
    public void progress(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        String t = line.trim();
        if (!voiceText.isEmpty() && voiceProgress) {
            if (t.equals(voiceText.get(voiceText.size() - 1))) {
                return;
            }
            voiceText.set(voiceText.size() - 1, t);
            voiceBorn.set(voiceBorn.size() - 1, clock);
        } else {
            voiceText.add(t);
            voiceBorn.add(clock);
            voiceProgress = true;
            while (voiceText.size() > VOICE_LINES + 2) {
                voiceText.remove(0);
                voiceBorn.remove(0);
            }
        }
        voiceQuietSince = Float.MAX_VALUE;
        invalidate();
    }

    /** The thought while thinking goes to the inner voice. */
    public void setThought(String text) {
        innerVoice(text);
    }

    // ------------------------------------------------------------ things shown above the pod

    /** A thing designed in 3D: its triangles, when they validate, otherwise points. */
    public boolean becomeObject(ai.dvine.create.ObjectModel m) {
        SolidSurface packed = SolidSurface.pack(m, objects.selected());
        if (packed != null) {
            solidMesh = packed;
            meshYaw = 0f;
            meshPitch = 0f;
            meshSize = 1f;
            meshDx = meshDy = 0f;
            meshSpin = 0.45f;
            display.drawing(null, null, 0f);
            objectOnShow = true;
            motion.flash();
            layoutTree();
            return true;
        }
        ai.dvine.render.field.Shape s = ObjectPoints.of(m, ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS);
        if (s.count == 0) {
            return false;
        }
        solidMesh = null;
        meshSpin = 0f;
        display.drawing(m.title, s, 3600f, true);
        objectOnShow = true;
        motion.flash();
        return true;
    }

    /** The design changed ("make it three storeys"): the points re-form. */
    public boolean objectChanged() {
        ai.dvine.create.ObjectModel m = objects.model();
        return m != null && becomeObject(m);
    }

    /** The design is put away. */
    public boolean restoreBody() {
        boolean was = objectOnShow;
        objectOnShow = false;
        solidMesh = null;
        meshSpin = 0f;
        display.drawing(null, null, 0f);
        partTree = null;
        return was;
    }

    /** Whether a design is on show right now. */
    public boolean objectShown() {
        if (objectOnShow && solidMesh == null && !display.drawingShown()) {
            objectOnShow = false;
        }
        return objectOnShow;
    }

    /** A rigged body is on show only when the model actually has a skeleton and it is visible. */
    public boolean bodyShown() {
        return objectShown() && ai.dvine.create.BodyRig.shown(objects.model());
    }

    public void setOnline(boolean value) {
        online = value;
    }

    /** Offline or unable to answer: the pod dims, and stays. */
    public void setDimmed(boolean on) {
        motion.setDim(on);
    }

    /** Notes what just happened ("VOICE", "what was heard"); keeps the latest four for the status report. UI thread. */
    public void activity(String tag, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
        activityLog.add(new String[]{tag, text.trim(), time});
        while (activityLog.size() > 4) {
            activityLog.remove(0);
        }
        invalidate();
    }

    /** The download bar: {@code visible} shows or hides it, {@code busy} animates it while bytes arrive. */
    public void setDownload(boolean visible, String title, int percent, boolean busy, String detail) {
        dlShown = visible;
        dlTitle = title == null ? "" : title;
        dlPercent = Math.max(0, Math.min(100, percent));
        dlBusy = busy;
        dlDetail = detail == null ? "" : detail;
        invalidate();
    }

    public boolean downloadShown() {
        return dlShown;
    }

    /** A selected photograph, held as a dense skin until something else replaces it. The platform stays hidden. */
    public void showPhotograph(ai.dvine.render.field.Shape picture, String what) {
        if (picture == null || picture.count == 0) {
            return;
        }
        objectOnShow = false;
        solidMesh = null;
        meshSpin = 0f;
        display.showPhotograph(what, picture);
        motion.flash();
    }

    /** A picture painted by the local drawing model. Not a photograph. */
    public void showGenerated(ai.dvine.create.DrawingModel.Picture picture) {
        if (picture == null || picture.argb == null) {
            return;
        }
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                picture.argb, picture.width, picture.height, android.graphics.Bitmap.Config.ARGB_8888);
        showPicture(ai.dvine.render.field.Raster.image(bitmap, 6000, 1.6f), picture.subject);
    }

    private void layoutTree() {
        if (!objectOnShow || objects.model() == null) {
            partTree = null;
            return;
        }
        float row = 18f * Math.max(1f, density);
        float width = Math.min(176f * Math.max(1f, density), Math.max(80f, getWidth()) * 0.42f);
        partTree = ai.dvine.create.PartTree.layout(objects.model(), objects.selected(), treeCollapsed,
                12f * Math.max(1f, density), padTop + 36f * Math.max(1f, density), width, row);
    }

    /** A picture (a drawing the language model planned) above the workspace for a while. */
    public void showPicture(ai.dvine.render.field.Shape picture, String what) {
        if (picture == null || picture.count == 0) {
            return;
        }
        objectOnShow = false;
        solidMesh = null;
        meshSpin = 0f;
        display.drawing(what, picture, 60f);
        motion.flash();
        layoutTree();
    }

    /** A thing built in 3D, turning slowly above the pod; it stays while it's being worked on. */
    public void showSolid(ai.dvine.render.field.Shape solid, String what) {
        if (solid == null || solid.count == 0) {
            return;
        }
        objectOnShow = false;
        solidMesh = null;
        meshSpin = 0f;
        display.drawing(what, solid, 600f, true);
        motion.flash();
    }

    /** The thing on show, changed part by part ("take out the tyres"): it re-forms where it is. */
    public boolean reshapeSolid(ai.dvine.render.field.Shape solid) {
        boolean ok = display.reshape(solid);
        if (ok) {
            motion.flash();
        }
        return ok;
    }

    /** A change to the thing on show ("make it bigger"); false when nothing is shown. */
    public boolean changePicture(ai.dvine.render.field.ShapeCommand c) {
        return c != null && display.apply(c);
    }

    public boolean pictureShown() {
        return display.drawingShown();
    }

    /** Clears whatever is shown above the pod. */
    public void clearDrawing() {
        objectOnShow = false;
        solidMesh = null;
        meshSpin = 0f;
        display.drawing(null, null, 0f);
    }

    public void setListening(boolean on) {
        listening = on;
        if (on) {
            mode = StageMode.LISTENING;
        } else if (mode == StageMode.LISTENING) {
            mode = StageMode.CONTENT;
        }
    }

    public void setSubtitle(String text) {
        subtitle = text == null ? "" : text;
        subLayout = null;
    }

    public void setSubtitles(boolean on) {
        subtitles = on;
    }

    /** A word the voice is starting to say: the emitter beats with it. */
    public void speechWord(String word) {
        motion.word(word);
    }

    /** A report on the stage: what is shown, the quality level and frame times. */
    public String inspect() {
        if (renderer == null) {
            return "The 3D scene isn't attached, so I'm drawing a simple pod.";
        }
        String[] q = {"low", "medium", "high"};
        return "The galaxy is on, the scene is at " + q[renderer.pod.quality] + " quality, frames " + renderer.stats.summary()
                + (solidMesh != null ? ", and a designed mesh is on the board"
                        : display.photographShown() ? ", and " + display.drawingName() + " is on the skin"
                        : display.drawingShown() ? ", and " + display.drawingName() + " is in the workspace" : "")
                + (renderer.glErrors > 0 ? ", " + renderer.glErrors + " graphics errors" : "") + ".";
    }

    @Override
    public void setMood(float smile, float brow, float tiltDeg) {
    }

    @Override
    public void setPose(String pose) {
        // the movement engine drives a designed body; none is on show yet
    }

    @Override
    public void playScene(String[] poses, float[] seconds) {
    }

    /** The thing on show turns all the way round. */
    @Override
    public void turnAround() {
        if (solidMesh != null) {
            meshSpin = 0f;
            meshYaw += (float) Math.PI;
            return;
        }
        display.apply(ai.dvine.render.field.ShapeCommand.parse("turn it around"));
    }

    /** A turning 3D object (globe, cube, pyramid, torus, dna, atom, sphere…) above the pod. */
    @Override
    public void showObject(String kind) {
        showSolid(ObjectPoints.of(new ai.dvine.render.forms.Forms.Object3D(kind), ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS), kind);
    }

    @Override
    public void showSketch(Sketch s) {
        if (s != null) {
            showPicture(ObjectPoints.of(new ai.dvine.render.forms.Forms.Drawing(s), ai.dvine.render.field.ParticleDisplay.DRAWING_POINTS), "sketch");
        }
    }

    @Override
    public void clearSketch() {
        clearDrawing();
    }

    /** A brief brightening: answer ready, copy done. */
    public void burst(int count) {
        motion.flash();
    }

    public void wake() {
    }

    /** The voice speaking next ("ivie", "obi" or "ibiko"): the pod's light takes their colour; {@code onReady} runs. */
    public void ensureFace(String agent, Runnable onReady) {
        speaker = IBIKO.equals(agent) || OBI.equals(agent) ? agent : IVIE;
        motion.speaker(speaker);
        if (onReady != null) {
            onReady.run();
        }
    }

    public void ensureFace(boolean wantIvie, Runnable onReady) {
        ensureFace(wantIvie ? IVIE : OBI, onReady);
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

    private boolean glOn() {
        return renderer != null && renderer.glErrors < 5;
    }

    private void tick(float dt) {
        clock += dt;
        barAge += dt;
        reportFallback();
        motion.step(dt, mode);
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        if (layout.width != w || layout.height != h || layoutTop != padTop || layoutBottom != padBottom) {
            layout = PodLayout.compute(w, h, padTop, padBottom);
            layoutTop = padTop;
            layoutBottom = padBottom;
        }
        if (glOn()) {
            feedDisplay();
            display.frame(dt, renderer, layout, renderer.aspect());
            renderer.setProjection(layout.proj);
            renderer.setSolid(solidMesh);
            if (solidMesh != null) {
                meshYaw += meshSpin * dt;
                renderer.setSolidPose(meshYaw, meshPitch, meshSize, meshDx, meshDy);
            }
            renderer.pod.submit(layout, motion, display.drawingShown(), density, false);
            if (glView != null) {
                glView.requestRender();
            }
        }
    }

    /** Gives the particle display what it should show now: the captions and the download bar. */
    private void feedDisplay() {
        ai.dvine.render.field.ParticleDisplay d = display;
        String cap = subtitles && subtitle.length() > 0 ? subtitle : null;
        if (!java.util.Objects.equals(cap, shownCaption)) {
            shownCaption = cap;
            d.caption(cap, cap == null ? null
                    : ai.dvine.render.field.Raster.text(cap, 0xFFE4E8EA, ai.dvine.render.field.ParticleDisplay.CAPTION_POINTS, 2f, 2));
        }
        // the bar's picture changes with each percent (the detail line only every few seconds, so it doesn't churn)
        String bar = dlShown ? dlTitle + "|" + dlPercent + "|" + (dlBusy ? dlDetail : "") : null;
        if (!java.util.Objects.equals(bar, shownBar) && (bar == null || shownBar == null || barAge > 2.5f || !samePercent(bar, shownBar))) {
            shownBar = bar;
            barAge = 0f;
            d.panel(bar, bar == null ? null : ai.dvine.render.field.Raster.progress(dlTitle.toUpperCase(java.util.Locale.US), dlPercent / 100f,
                    dlDetail, 0xFF7FE3FF, ai.dvine.render.field.ParticleDisplay.PANEL_POINTS, 2f));
        }
    }

    private static boolean samePercent(String a, String b) {
        String[] x = a.split("\\|", 3), y = b.split("\\|", 3);
        return x.length > 1 && y.length > 1 && x[0].equals(y[0]) && x[1].equals(y[1]);
    }

    /** Says once, in the subtitles, why the simple pod is showing (instead of switching silently). */
    private void reportFallback() {
        if (fallbackReported || renderer == null || clock < 6f || renderer.glErrors < 5) {
            return;
        }
        fallbackReported = true;
        setSubtitle("The 3D scene hit a " + (renderer.fault == null ? "graphics problem" : renderer.fault)
                + ", so I'm showing a simpler version. Say \"status report\" for details.");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (glOn()) {
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR); // the GL scene shows through
        } else {
            drawFallback(canvas, w, h);
        }
        drawInnerVoice(canvas, w);
        drawPartTree(canvas);
        // online: a quiet dot at the top; grey when offline
        fill.setColor(online ? 0xFF7FE3FF : 0xFF5A5A5A);
        fill.setAlpha(online ? 170 : 120);
        canvas.drawCircle(w * 0.5f, padTop + 8f * density, 2.6f * density, fill);
    }

    /**
     * The inner voice along the top: the newest line clear at the bottom of the block, each older one higher, fainter
     * and softer, until the oldest fades away like smoke; the block fades once DVINE stops thinking.
     */
    private void drawInnerVoice(Canvas canvas, float w) {
        if (voiceText.isEmpty()) {
            return;
        }
        float quiet = clock - voiceQuietSince;
        float block = quiet <= 0f ? 1f : Math.max(0f, 1f - (quiet - 2.5f) / 1.5f);
        if (block <= 0f) {
            voiceText.clear();
            voiceBorn.clear();
            return;
        }
        float line = voicePaint.getTextSize() * 1.45f;
        float x = 18f * density, baseY = padTop + 22f * density + line * (VOICE_LINES - 1);
        int n = voiceText.size();
        for (int i = 0; i < n; i++) {
            int fromNewest = n - 1 - i;
            if (fromNewest >= VOICE_LINES) {
                continue;
            }
            float age = clock - voiceBorn.get(i);
            float appear = Math.min(1f, age / 0.25f);
            float fade = 1f - fromNewest / (float) VOICE_LINES;
            float rise = fromNewest * line + (fromNewest == VOICE_LINES - 1 ? (age % 3f) * 2f * density : 0f);
            String t = voiceText.get(i);
            TextPaint p = fromNewest >= 3 ? smokePaint : voicePaint;
            p.setAlpha((int) (235 * appear * fade * fade * block));
            String shown = android.text.TextUtils.ellipsize(t, p, w - x * 2, android.text.TextUtils.TruncateAt.END).toString();
            // the newest line writes itself out
            if (fromNewest == 0 && age < 0.6f) {
                shown = shown.substring(0, Math.max(1, (int) (shown.length() * age / 0.6f)));
            }
            canvas.drawText(shown, x, baseY - rise, p);
        }
    }

    private void drawPartTree(Canvas canvas) {
        layoutTree();
        if (partTree == null || partTree.rows.isEmpty()) {
            return;
        }
        float pad = 6f * density;
        fill.setColor(0xCC10141A);
        canvas.drawRoundRect(partTree.x - pad, partTree.y - pad, partTree.x + partTree.width + pad,
                partTree.y + partTree.height + pad, 8f * density, 8f * density, fill);
        for (ai.dvine.create.PartTree.Row row : partTree.rows) {
            voicePaint.setAlpha(row.selected ? 255 : 190);
            voicePaint.setColor(row.selected ? 0xFF7FE3FF : (row.visible ? 0xFFE4E8EA : 0xFF8A9098));
            String mark = row.group ? (row.expanded ? "v " : "> ") : (row.selected ? "* " : "- ");
            String text = mark + row.label;
            float max = row.width - row.depth * 10f * density;
            String shown = android.text.TextUtils.ellipsize(text, voicePaint, Math.max(20f, max), android.text.TextUtils.TruncateAt.END).toString();
            canvas.drawText(shown, row.x + row.depth * 10f * density, row.y + row.height * 0.78f, voicePaint);
        }
        voicePaint.setAlpha(255);
        voicePaint.setColor(0xFFBFE6FF);
    }

    /** The pod without OpenGL: a dark sky with the same stars and the pod as glowing rings. */
    private void drawFallback(Canvas canvas, float w, float h) {
        canvas.drawColor(0xFF020309);
        float[] st = fallbackSky.stars;
        for (int i = 0; i < 400; i++) {
            int o = i * ai.dvine.render.pod.Space.STAR_STRIDE;
            float tw = st[o + 4] > 0f ? 0.6f + 0.4f * (float) Math.sin(clock * st[o + 4] + st[o + 5]) : 1f;
            fill.setColor(0xFFDDE8FF);
            fill.setAlpha((int) (200 * st[o + 3] * tw));
            canvas.drawCircle((st[o] + 1f) * 0.5f * w, (1f - st[o + 1]) * 0.5f * h, st[o + 2] * 0.5f * density, fill);
        }
        PodLayout l = layout;
        float[] pod = l.project(0f, l.podY, -0.25f);
        float pr = l.podDiameter * 0.5f * l.unit;
        stroke.setColor(0xFF7FE3FF);
        stroke.setStrokeWidth(2f * density);
        stroke.setAlpha(200);
        canvas.drawOval(pod[0] - pr, pod[1] - pr * 0.22f, pod[0] + pr, pod[1] + pr * 0.22f, stroke);
        stroke.setAlpha(90);
        canvas.drawOval(pod[0] - pr * 0.6f, pod[1] - pr * 0.13f, pod[0] + pr * 0.6f, pod[1] + pr * 0.13f, stroke);
        float glow = 0.6f + 0.4f * motion.voice + 0.3f * motion.listen;
        fill.setShader(new RadialGradient(pod[0], pod[1], pr * 0.25f, (int) (0xAA * Math.min(1f, glow)) << 24 | 0xB8F2FF,
                0x00000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(pod[0], pod[1], pr * 0.25f, fill);
        fill.setShader(null);
        if (subtitles && subtitle.length() > 0) {
            int width = (int) Math.min(w - 48f * density, 640f * density); // a readable line on a tablet too
            if (subLayout == null || subWidth != width) {
                subWidth = width;
                subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length(), subPaint, width)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            }
            canvas.save();
            canvas.translate((w - width) * 0.5f, h - padBottom - 22f * density - subLayout.getHeight());
            subLayout.draw(canvas);
            canvas.restore();
        }
    }

    // ------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float slop = 10f * density;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                layoutTree();
                if (partTree != null && partTree.hit(x, y) != ai.dvine.create.PartTree.MISS) {
                    treeDown = true;
                    downX = lastX = x;
                    downY = lastY = y;
                    removeCallbacks(longPress);
                    return true;
                }
                treeDown = false;
                downX = lastX = x;
                downY = lastY = y;
                decided = false;
                dragged = false;
                longFired = false;
                holding = false;
                twoFingers = false;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2) {
                    removeCallbacks(longPress);
                    twoFingers = true;
                    dragged = true;
                    pinchStart = spread(event);
                    sizeAtPinch = display.drawingSize();
                    meshSizeAtPinch = meshSize;
                    midX = (event.getX(0) + event.getX(1)) / 2f;
                    midY = (event.getY(0) + event.getY(1)) / 2f;
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (twoFingers && event.getPointerCount() >= 2) {
                    // pinch to zoom, two fingers together to move the thing on show
                    float s = spread(event);
                    if (pinchStart > 1f && (display.drawingShown() || solidMesh != null)) {
                        if (solidMesh != null) {
                            meshSpin = 0f;
                            meshSize = Math.max(0.25f, Math.min(6f, meshSizeAtPinch * s / pinchStart));
                        } else {
                            float want = sizeAtPinch * s / pinchStart;
                            display.zoomBy(want / Math.max(0.01f, display.drawingSize()));
                        }
                    }
                    float mx = (event.getX(0) + event.getX(1)) / 2f, my = (event.getY(0) + event.getY(1)) / 2f;
                    if (solidMesh != null) {
                        meshDx = Math.max(-1.5f, Math.min(1.5f, meshDx + (mx - midX) / layout.unit));
                        meshDy = Math.max(-1.5f, Math.min(1.5f, meshDy - (my - midY) / layout.unit));
                    } else {
                        display.panBy((mx - midX) / layout.unit, -(my - midY) / layout.unit);
                    }
                    midX = mx;
                    midY = my;
                    return true;
                }
                if (holding) {
                    return true; // speaking: the finger stays down until the user is done
                }
                float dx = x - downX;
                float dy = y - downY;
                if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    decided = true;
                    removeCallbacks(longPress);
                }
                if (decided && (display.drawingShown() || solidMesh != null)) {
                    // one finger turns the thing drawn round and tips it
                    dragged = true;
                    if (solidMesh != null) {
                        meshSpin = 0f;
                        meshYaw += (x - lastX) / (layout.unit * 0.6f);
                        meshPitch = Math.max(-1.45f, Math.min(1.45f, meshPitch + (y - lastY) / (layout.unit * 0.6f)));
                    } else {
                        display.rotateBy((x - lastX) / (layout.unit * 0.6f), (y - lastY) / (layout.unit * 0.6f));
                    }
                }
                lastX = x;
                lastY = y;
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (treeDown) {
                    treeDown = false;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && partTree != null
                            && Math.hypot(x - downX, y - downY) < 14f * density) {
                        String command = partTree.command(x, y);
                        if (command != null && listener != null) {
                            listener.onHistory(command);
                        } else {
                            String group = partTree.toggle(x, y);
                            if (group != null) {
                                if (!treeCollapsed.add(group)) {
                                    treeCollapsed.remove(group);
                                }
                            } else {
                                int id = partTree.hit(x, y);
                                if (id >= 0) {
                                    objects.select(id);
                                    ai.dvine.create.PartTree.Row row = partTree.rowAt(x, y);
                                    if (row != null && listener != null) {
                                        listener.onPart(row.label);
                                    }
                                }
                            }
                        }
                        layoutTree();
                        if (objects.model() != null) {
                            SolidSurface packed = SolidSurface.pack(objects.model(), objects.selected());
                            if (packed != null) {
                                solidMesh = packed;
                            }
                        }
                    }
                    return true;
                }
                if (holding) {
                    holding = false;
                    if (listener != null) {
                        listener.onMicRelease(); // let go: what was said is sent
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || longFired || listener == null || dragged || twoFingers) {
                    return true;
                }
                float travel = (float) Math.hypot(x - downX, y - downY);
                if (travel >= 14f * density) {
                    return true;
                }
                long now = event.getEventTime();
                if (now - lastTapAt < DOUBLE_TAP_MS && Math.hypot(x - lastTapX, y - lastTapY) < 48f * density) {
                    if (pendingTap != null) {
                        removeCallbacks(pendingTap);
                        pendingTap = null;
                    }
                    lastTapAt = 0L;
                    listener.onType();
                    return true;
                }
                lastTapAt = now;
                lastTapX = x;
                lastTapY = y;
                float[] pod = layout.project(0f, layout.podY, -0.25f);
                if (Math.abs(x - pod[0]) < layout.podDiameter * 0.5f * layout.unit && Math.abs(y - pod[1]) < layout.unit * 0.3f) {
                    pendingTap = () -> {
                        pendingTap = null;
                        motion.flash();
                        listener.onHeadTap();
                    };
                    postDelayed(pendingTap, DOUBLE_TAP_MS);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private static float spread(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
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
            stopFrames(); // nothing is drawn while the app isn't on screen
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
        android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        padTop = bars.top;
        padBottom = bars.bottom;
        return insets;
    }
}
