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
public final class StageView extends View implements Choreographer.FrameCallback, ai.dvine.engine.BodyPort {
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
    /**
     * Wire look only: 1 while the body has broken into dots that build the
     * drawing, 2 while the dots fly home to the body, 0 otherwise.
     */
    private int dotsPhase;
    private float dotsSince;
    /** How much of the GL body shows (0 while its dots are out drawing). */
    private float humanFade = 1f;
    /** The drawing frame in wire mode (screen): centre and half size. */
    private float drawCx, drawCy, drawHalf = 1f;
    /** Where each dot belongs on the body, in drawing-frame units, refreshed every frame on the way home. */
    private final float[] homeDots = new float[4000 * 3];
    private int homeCount;
    private final Swarm.Form homeForm = (xyz, lit, max, time) -> {
        int n = Math.min(max, homeCount);
        System.arraycopy(homeDots, 0, xyz, 0, n * 3);
        java.util.Arrays.fill(lit, 0, n, 1f);
        return n;
    };
    private boolean bodyMode = true; // the main screen is the full-body 3D stage
    // zoom between the close-up face (0) and the full body (1); while zooming everything is in body units
    private final Spring1D zoom = new Spring1D(3.2f, 1f, 1f); // opens on the whole body
    private boolean bodySpace;
    private final Swarm.Form faceInBody = new Swarm.Form() {
        @Override
        public int fill(float[] xyz, float[] lit, int max, float time) {
            Forms.Head f = showingIvie ? ivieFace : obiFace;
            if (f == null) {
                return 0;
            }
            int n = f.fill(xyz, lit, max, time);
            for (int i = 0; i < n * 3; i += 3) {
                xyz[i] *= DotBody.FACE_SCALE;
                xyz[i + 1] = xyz[i + 1] * DotBody.FACE_SCALE + DotBody.FACE_Y;
                xyz[i + 2] *= DotBody.FACE_SCALE;
            }
            return n;
        }
    };
    // scenes: a list of poses with how long each lasts
    private String[] scenePoses;
    private float[] sceneEnds;
    private float sceneStart;
    private int sceneBeat = -1;
    private float[] tapScreen = new float[0];
    // the 3D wireframe human (OpenGL, under this transparent overlay)
    private ai.dvine.render.HumanStage human;
    private ai.dvine.render.HumanView humanView;
    private final ai.dvine.human.touch.TouchClassifier touches = new ai.dvine.human.touch.TouchClassifier();
    private boolean bodyTouchDown;
    private float lastYaw;
    private float lastPitch;
    private final HandPainter hands = new HandPainter();
    private final WireHead.Pose scratchPose = new WireHead.Pose();
    private final Choreographer choreographer = Choreographer.getInstance();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    // the download bar (made of particles in the stage): shown while the model downloads (or when asked)
    private boolean dlShown, dlBusy;
    private int dlPercent;
    private String dlTitle = "", dlDetail = "";

    /** Live activity: the latest few {tag, text, time} entries, newest last. */
    private final java.util.ArrayList<String[]> activityLog = new java.util.ArrayList<String[]>();
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
    /** The inner thought shown in the bubble above the head (null: animated thinking dots). */
    private String thought;
    private StaticLayout thoughtLayout;
    private float thoughtShown;
    private final TextPaint thoughtPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

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
    // a long press anywhere speaks; a double tap anywhere opens typing; a single tap waits a moment to be sure
    // it isn't the first of a double tap (no swiping: it fights the phone's own swipe to minimise)
    private final Runnable longPress = () -> {
        longFired = true;
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
        stroke.setStrokeJoin(Paint.Join.ROUND);
        subPaint.setColor(0xFFBDBDBD);
        subPaint.setTextSize(15f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(16f * density);
        setContentDescription("DVINE. Touch and hold anywhere to speak. Double-tap to type. Tap the face to hear them.");
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

    // ------------------------------------------------------------ ANRE: the particles as a 3D object

    /** The particles leave the body and build this validated object (or re-form into it). */
    public boolean becomeObject(ai.dvine.create.ObjectModel m) {
        return human != null && human.becomeObject(showingIvie, m);
    }

    /** The object or its view changed: the particles re-form. */
    public boolean objectChanged() {
        return human != null && human.objectChanged(showingIvie);
    }

    /** The object dissolves; the body comes back, head first. */
    public boolean restoreBody() {
        return human != null && human.restoreBody(showingIvie);
    }

    /** Whether the particles are an object (complete or being edited) right now. */
    public boolean objectShown() {
        ai.dvine.anre.TransformationController.State st = human == null ? null : human.morphState(showingIvie);
        return st == ai.dvine.anre.TransformationController.State.OBJECT_COMPLETE
                || st == ai.dvine.anre.TransformationController.State.OBJECT_MODIFYING;
    }

    /** Mode B: show the inside ("skeleton", "heart", "chest", … or "off"); returns the structures shown. */
    public java.util.List<String> anatomy(String what) {
        if (human == null) {
            return new java.util.ArrayList<String>();
        }
        if (!"off".equals(what)) {
            setBodyMode(true);
        }
        return human.anatomy(showingIvie, what);
    }

    /** A progress bar built from the body's particles (0..1); a negative value ends it. */
    public void progress(float fraction) {
        if (human != null) {
            human.progress(showingIvie, fraction);
        }
    }

    public ai.dvine.render.HumanStage humanStage() {
        return human;
    }

    /** Shows an inner thought in a bubble above the head while thinking; null shows thinking dots. */
    public void setThought(String text) {
        thought = text == null || text.trim().isEmpty() ? null : text.trim();
        thoughtLayout = null;
    }

    public void setOnline(boolean value) {
        online = value;
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

    /**
     * The download card: {@code visible} shows or hides it, {@code busy} animates the bar while bytes arrive
     * (indeterminate while connecting is shown as the same shimmer at the current percent).
     */
    public void setDownload(boolean visible, String title, int percent, boolean busy, String detail) {
        dlShown = visible;
        dlTitle = title == null ? "" : title;
        dlPercent = Math.max(0, Math.min(100, percent));
        dlBusy = busy;
        dlDetail = detail == null ? "" : detail;
        invalidate();
    }

    /** A picture (a drawing the language model planned, or a photo found online) formed by the particles beside the persona for a while. */
    public void showPicture(ai.dvine.render.field.Shape picture, String what) {
        if (human == null || picture == null || picture.count == 0) {
            return;
        }
        human.display.drawing(what, picture, 40f);
        burst(14);
    }

    /** A thing built in 3D, turning slowly above the persona; it stays while it's being worked on. */
    public void showSolid(ai.dvine.render.field.Shape solid, String what) {
        if (human == null || solid == null || solid.count == 0) {
            return;
        }
        human.display.drawing(what, solid, 90f, true);
        burst(14);
    }

    /** A change to the thing on show ("make it bigger"); false when nothing is shown. */
    public boolean changePicture(ai.dvine.render.field.ShapeCommand c) {
        return human != null && c != null && human.display.apply(c);
    }

    public boolean pictureShown() {
        return human != null && human.display.drawingShown();
    }

    /** Clears a drawing the particles formed. */
    public void clearDrawing() {
        if (human != null) {
            human.display.drawing(null, null, 0f);
        }
    }

    private String shownCaption, shownThought, shownBar;

    /**
     * Gives the particle display what it should show now: the captions, the thought while thinking, the download
     * bar. Each is drawn into a picture only when it changes.
     */
    private void feedDisplay(boolean wire, int color) {
        if (human == null) {
            return;
        }
        ai.dvine.render.field.ParticleDisplay d = human.display;
        String cap = wire && subtitles && subtitle.length() > 0 ? subtitle : null;
        if (!java.util.Objects.equals(cap, shownCaption)) {
            shownCaption = cap;
            d.caption(cap, cap == null ? null
                    : ai.dvine.render.field.Raster.text(cap, 0xFFE4E8EA, ai.dvine.render.field.ParticleDisplay.CAPTION_POINTS, 2f, 4));
        }
        String th = wire && thoughtShown > 0.3f ? (thought == null ? "\u2022  \u2022  \u2022" : thought) : null;
        if (!java.util.Objects.equals(th, shownThought)) {
            shownThought = th;
            d.thought(th, th == null ? null
                    : ai.dvine.render.field.Raster.text(th, mixColor(color, 0xFFFFFFFF, 0.35f), ai.dvine.render.field.ParticleDisplay.THOUGHT_POINTS, 2f, 3));
        }
        // the bar's picture changes with each percent (the detail line only every few seconds, so it doesn't churn)
        String bar = wire && dlShown ? dlTitle + "|" + dlPercent + "|" + (dlBusy ? dlDetail : "") : null;
        if (!java.util.Objects.equals(bar, shownBar) && (bar == null || shownBar == null || barAge > 2.5f || !samePercent(bar, shownBar))) {
            shownBar = bar;
            barAge = 0f;
            d.panel(bar, bar == null ? null : ai.dvine.render.field.Raster.progress(dlTitle.toUpperCase(java.util.Locale.US), dlPercent / 100f,
                    dlDetail, color, ai.dvine.render.field.ParticleDisplay.PANEL_POINTS, 2f));
        }
    }

    private float barAge;

    private static boolean samePercent(String a, String b) {
        String[] x = a.split("\\|", 3), y = b.split("\\|", 3);
        return x.length > 1 && y.length > 1 && x[0].equals(y[0]) && x[1].equals(y[1]);
    }

    public boolean downloadShown() {
        return dlShown;
    }

    /** The 3D bodies are drawn as cartoons (the Ghibli-style look, the default). */
    private boolean cartoonOn() {
        return looks[showingIvie ? 1 : 0].style == Look.Style.TOON;
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
        if (look == null) {
            return;
        }
        boolean shape = looks[side].shapeDiffers(look);
        looks[side] = look.copy();
        if (shape) {
            sculpt(forIvie);
            if (human != null) {
                human.rebuild(forIvie, look); // the 3D mesh is rebuilt in the background and swapped in when valid
            }
        }
    }

    /** Attaches the 3D human renderer; until its assets load, the nano dots stand in. */
    public void attachHuman(ai.dvine.render.HumanStage stage, ai.dvine.render.HumanView view) {
        human = stage;
        humanView = view;
        stage.renderer.displayDensity = density; // the particle display's dots, the same size on every screen
    }

    private boolean fallbackReported;

    /** Says once, in the subtitles, why the 3D body gave way to the dots (instead of switching silently). */
    private void reportFallback() {
        if (fallbackReported || human == null || clock < 6f) { // after the greeting, so it isn't overwritten
            return;
        }
        String why = human.loadFault;
        if (why == null && human.renderer.glErrors >= 5) {
            why = "My 3D body hit a " + (human.renderer.fault == null ? "graphics problem" : human.renderer.fault);
        }
        if (why != null) {
            fallbackReported = true;
            setSubtitle(why + ", so I'm showing a simpler version of me. Say \"status report\" for details.");
        }
    }

    /** True while the wireframe human draws this persona (else the nano-dot swarm does). */
    private boolean wireOn() {
        Look.Style st = looks[showingIvie ? 1 : 0].style;
        return human != null && human.ready(showingIvie) && st != Look.Style.DOTS && human.renderer.glErrors < 5;
    }

    /** A word the voice is starting to say: drives the mouth shapes. */
    public void speechWord(String word) {
        if (human != null) {
            human.speechWord(showingIvie, word);
        }
    }

    @Override
    public void setExpression(String name) {
        if (human != null) {
            human.setExpression(showingIvie, name);
        }
        rig().setMode(FaceMode.DONE); // a brief glow in either style
    }

    @Override
    public void setDemo(boolean on) {
        if (human != null) {
            human.setDemo(on);
        }
        if (on) {
            setBodyMode(true);
        }
    }

    @Override
    public void arrange(String arrangement) {
        if (human != null) {
            human.arrange(arrangement);
        }
        setBodyMode(!"single".equals(arrangement) || bodyMode);
    }

    /** Self-inspection of the visible persona's 3D body (with a local repair if needed). */
    public String inspect() {
        if (human == null) {
            return "My 3D body isn't attached.";
        }
        String s = human.inspect(showingIvie);
        return s + " Frames: " + human.renderer.stats.summary() + ".";
    }

    /**
     * Full body (true) or the face (false). The app opens on the whole body
     * in the 3D stage; "show me your face" brings the camera in to the head.
     */
    public void setBodyMode(boolean on) {
        if (on != bodyMode) {
            bodyMode = on;
            swarm.scatter(0.35f);
            burst(10);
            if (!on) {
                scenePoses = null;
            }
        }
    }

    /** Expression bias from the engine's affect state, applied to both personas. */
    @Override
    public void setMood(float smile, float brow, float tiltDeg) {
        obi.setMood(smile, brow, tiltDeg);
        ivie.setMood(smile, brow, tiltDeg);
    }

    public boolean isBodyMode() {
        return bodyMode;
    }

    /**
     * Acts out a scene: each pose holds for its seconds, in order, while the
     * persona narrates. Ends standing.
     */
    public void playScene(String[] poses, float[] seconds) {
        if (poses == null || seconds == null || poses.length == 0 || poses.length != seconds.length) {
            return;
        }
        sceneEnds = new float[seconds.length];
        float t = 0f;
        for (int i = 0; i < seconds.length; i++) {
            t += Math.max(0.5f, seconds[i]);
            sceneEnds[i] = t;
        }
        scenePoses = poses.clone();
        sceneStart = clock;
        sceneBeat = -1;
        setBodyMode(true);
    }

    private void tickScene() {
        String[] poses = scenePoses;
        if (poses == null) {
            return;
        }
        float t = clock - sceneStart;
        int beat = 0;
        while (beat < sceneEnds.length && t >= sceneEnds[beat]) {
            beat++;
        }
        if (beat >= poses.length) {
            scenePoses = null;
            setPose("stand");
            return;
        }
        if (beat != sceneBeat) {
            sceneBeat = beat;
            applyPose(poses[beat]);
        }
    }

    /** Full body in a pose ("tpose", "squat", "sit", "run"…); the dots flow between poses. */
    public void setPose(String pose) {
        if (pose == null) {
            return;
        }
        scenePoses = null;
        setBodyMode(true);
        applyPose(pose);
    }

    private void applyPose(String pose) {
        bodyPose = pose;
        if (human != null) {
            human.setPose(pose);
        }
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
        if (human != null) {
            human.turnAround();
        }
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
        scatterBody();
    }

    public void gesture(String key) {
        // the 3D persona makes the gesture with its own hands; floating hands only stand in for the dot swarm
        if (wireOn() && human.gesture(showingIvie, key, 2.8f)) {
            return;
        }
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
        scatterBody();
    }

    public void clearSketch() {
        if (dotsPhase == 1) {
            dotsHome();
            return;
        }
        sketch = null;
        drawForm = null;
    }

    /**
     * Wire look: the body breaks into dots where its vertices are, the dots
     * burst apart, and the swarm then builds the drawing from them.
     */
    private void scatterBody() {
        if (dotsPhase == 2) {
            dotsPhase = 1; // already dots on their way home: turn them round to the new drawing
            dotsSince = clock;
            return;
        }
        if (!wireOn() || dotsPhase == 1 || !captureHome()) {
            return;
        }
        int n = homeCount;
        for (int i = 0; i < drawSwarm.capacity; i++) {
            int k = i < n ? i : i % Math.max(1, n);
            drawSwarm.pos[i * 3] = homeDots[k * 3] + (i < n ? 0f : Swarm.noise(i * 5) * 0.01f);
            drawSwarm.pos[i * 3 + 1] = homeDots[k * 3 + 1] + (i < n ? 0f : Swarm.noise(i * 5 + 1) * 0.01f);
            drawSwarm.pos[i * 3 + 2] = 0f;
            drawSwarm.vel[i * 3] = 0f;
            drawSwarm.vel[i * 3 + 1] = 0f;
            drawSwarm.vel[i * 3 + 2] = 0f;
            drawSwarm.light[i] = 1f;
        }
        drawSwarm.scatter(2.6f);
        dotsPhase = 1;
        dotsSince = clock;
        burst(18);
    }

    /** The drawing is done: its dots fly back to their places on the body, then the body returns. */
    private void dotsHome() {
        sketch = null;
        drawForm = homeForm;
        dotsPhase = 2;
        dotsSince = clock;
    }

    /** Projects the body's vertices into the drawing frame (one dot per vertex, up to the swarm's size). */
    private boolean captureHome() {
        ai.dvine.human.HumanInstance hi = human == null ? null : human.instance(showingIvie);
        if (hi == null) {
            return false;
        }
        float[] p = hi.pos;
        int verts = p.length / 3;
        int n = Math.min(drawSwarm.capacity, verts);
        int stride = Math.max(1, verts / n);
        float cy = drawCy + drawHalf * 0.35f;
        for (int i = 0; i < n; i++) {
            int v = Math.min(verts - 1, i * stride);
            float[] sp = human.camera.project(p[v * 3], p[v * 3 + 1], p[v * 3 + 2]);
            homeDots[i * 3] = (sp[0] - drawCx) / drawHalf;
            homeDots[i * 3 + 1] = -(sp[1] - cy) / drawHalf;
            homeDots[i * 3 + 2] = 0f;
        }
        homeCount = n;
        return n > 0;
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
        barAge += dt;
        reportFallback();
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
        boolean sketching = drawForm != null && drawForm != homeForm && clock < drawUntil;
        if (drawForm != null && drawForm != homeForm && !sketching) {
            if (dotsPhase == 1) {
                dotsHome();
            } else {
                drawForm = null;
                sketch = null;
            }
        }
        if (dotsPhase == 1 && !wireOn()) {
            dotsPhase = 0;
        } else if (dotsPhase == 2 && clock - dotsSince > 1.5f) {
            dotsPhase = 0;
            drawForm = null;
        }
        // freshly scattered body dots glow as dust until the drawing claims them
        drawSwarm.parkedLight = dotsPhase == 1 ? Math.max(0f, 0.55f * (1f - (clock - dotsSince) / 5f)) : 0f;
        float bodyWant = dotsPhase == 1 || dotsPhase == 2 && clock - dotsSince < 0.9f ? 0f : 1f;
        humanFade += (bodyWant - humanFade) * Math.min(1f, dt * (bodyWant > humanFade ? 4f : 9f));
        sketchMix.step(sketching ? 1f : 0f, dt);
        obi.tick(dt);
        ivie.tick(dt);
        FaceRig r = rig();
        tickScene();
        // while the particles are (becoming) an object the camera shows the whole scene
        boolean objectScene = human != null && human.objectMode(showingIvie);
        boolean wantBody = (bodyMode || objectScene) && !sketching;
        zoom.step(wantBody ? 1f : 0f, dt);
        boolean space = wantBody || zoom.value > 0.002f;
        if (space != bodySpace) {
            // carry the dots into the other camera frame so nothing jumps
            if (space) {
                swarm.remap(DotBody.FACE_SCALE, DotBody.FACE_Y);
            } else {
                swarm.remap(1f / DotBody.FACE_SCALE, -DotBody.FACE_Y / DotBody.FACE_SCALE);
                zoom.value = 0f;
                zoom.velocity = 0f;
            }
            bodySpace = space;
        }
        Swarm.Form form = null;
        WireHead.Pose pose = scratchPose;
        DotBody b = showingIvie ? ivieBody : obiBody;
        if (b != null) {
            b.setTalking(r.mode() == FaceMode.SPEAKING);
        }
        Forms.Head f = showingIvie ? ivieFace : obiFace;
        if (wireOn()) {
            form = null; // the wireframe human is drawn by OpenGL; the dots rest
        } else if (wantBody && b != null) {
            form = b;
            pose = b.pose();
        } else if (f != null) {
            form = bodySpace ? faceInBody : f;
            pose = f.pose;
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
        float want = m == FaceMode.THINKING ? 1f : 0f;
        thoughtShown += (want - thoughtShown) * Math.min(1f, dt * (want > thoughtShown ? 7f : 3f));
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
        boolean wire = wireOn();
        if (wire) {
            canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR); // the GL human shows through
        } else {
            canvas.drawColor(0xFF000000);
        }
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
        float floorY = -2.25f;
        float e = 0f;
        float yaw = rig.yaw.value;
        float pitch = rig.pitch.value + 6f;
        float faceUnit = unit, faceCy = cy;
        if (bodySpace) {
            // zoom from the face to the whole body: the head stays put while the camera pulls back
            float z = clamp(zoom.value, 0f, 1f);
            e = z * z * (3f - 2f * z);
            float u0 = unit / DotBody.FACE_SCALE;
            float u1 = Math.min(w * 0.42f, usable * 0.36f);
            unit = (float) Math.exp(lerp((float) Math.log(u0), (float) Math.log(u1), e));
            float focus = lerp(DotBody.FACE_Y, 0f, e);
            cy = lerp(faceCy, padTop + usable * 0.55f, e) + focus * unit;
            floorY = lerp(-2.25f * DotBody.FACE_SCALE + DotBody.FACE_Y, DotBody.FLOOR_OUT - 0.02f, e);
            yaw = lerp(yaw, yaw * 0.3f, e);
            pitch = lerp(pitch, 8f, e);
        }
        boolean body = bodySpace && e > 0.5f;
        if (wire) {
            // the GL camera always works in body units: the face close-up is the camera near the head
            float u0 = faceUnit / DotBody.FACE_SCALE, u1 = Math.min(w * 0.42f, usable * 0.36f);
            float midX = 0f;
            if (human.demo()) {
                // both on stage: centre on where they are going and keep both bodies (and their hands) in frame
                float[] span = human.sceneSpan();
                midX = span[0];
                float across = (2f * span[1] + 2.4f) * ai.dvine.human.HumanInstance.OUT;
                u1 = Math.min(u1, w * 0.92f / across);
            }
            ai.dvine.render.Camera c = human.camera;
            c.width = w;
            c.height = h;
            c.unit = (float) Math.exp(lerp((float) Math.log(u0), (float) Math.log(u1), e));
            c.cx = cx - midX * ai.dvine.human.HumanInstance.OUT * c.unit * e;
            float headY = human.headCameraY(showingIvie); // frame the real head, not the dot head's height
            c.cy = lerp(faceCy, padTop + usable * 0.55f, e) + lerp(headY, 0f, e) * c.unit;
            c.distance = (float) Math.exp(lerp((float) Math.log(0.75f), (float) Math.log(4.4f), e));
            c.pivot = lerp(headY, 0f, e);
            c.yawDeg = yaw;
            c.pitchDeg = pitch;
            c.update();
            human.renderer.setCamera(c, -4.22f);
            boolean cartoon = cartoonOn();
            human.cartoon = cartoon;
            human.renderer.toon = cartoon;
            for (int k = 0; k < 2; k++) {
                Look.Style st = looks[k].style;
                human.renderer.setSkin(k, st == Look.Style.SKIN || st == Look.Style.TOON, looks[k].skinColor());
            }
            human.frame(clock, showingIvie, rig, e > 0.5f, rig.mode() == FaceMode.SPEAKING, faceAlpha * humanFade);
            // the drawing frame: in the wire look the body's own dots draw, so the drawing gets the whole stage
            float top = padTop + 56f * density, bottom = h - padBottom - 70f * density;
            drawCx = cx;
            drawCy = (top + bottom) * 0.5f;
            drawHalf = Math.min(w * 0.44f, (bottom - top) * 0.5f);
            if (dotsPhase == 2) {
                captureHome();
            }
            humanView.requestRender();
        }
        lastYaw = yaw;
        lastPitch = pitch;
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

        // glow, sparkles and the listening ring stay with the head as the camera zooms out
        float hu = bodySpace ? unit * DotBody.FACE_SCALE : unit;
        float hy = bodySpace ? cy - DotBody.FACE_Y * unit : cy;
        ensureGlow(hu);
        canvas.save();
        canvas.translate(cx, hy + hu * 0.1f);
        glowPaint.setShader(ivieOn ? ivieGlow : obiGlow);
        glowPaint.setAlpha((int) (140 * faceAlpha * (wire ? humanFade : 1f) * Math.max(0.4f, rig.glow.value)));
        canvas.drawCircle(0f, 0f, hu * 1.35f, glowPaint);
        canvas.restore();

        // a Blender-like floor, then the persona as nano dots facing the user (the GL human draws its own)
        if (!wire) {
            nano.drawGrid(canvas, yaw * 0.3f, 14f, cx, cy, unit, floorY, 1f - mix, density);
            nano.drawSwarm(canvas, swarm, yaw, pitch, cx, cy, unit, color,
                    Math.max(0.35f, faceAlpha) * clamp(rig.glow.value + 0.25f, 0.5f, 1.2f), density, clock);
        }
        if (!bodySpace && !wire) {
            hands.draw(canvas, nano, cx, cy, unit, color, faceAlpha, density);
        }
        drawSparks(canvas, cx, hy, hu, color);
        if (thoughtShown > 0.02f && !wire) { // in the stage the thought is made of particles
            float headX = cx, headTop = hy - hu * 1.25f;
            if (wire) {
                ai.dvine.human.HumanInstance hi = human.instance(showingIvie);
                int crown = hi == null ? -1 : hi.asset.landmark("crown");
                if (crown >= 0) {
                    float[] p = human.camera.project(hi.pos[crown * 3], hi.pos[crown * 3 + 1], hi.pos[crown * 3 + 2]);
                    headX = p[0];
                    headTop = p[1];
                }
            }
            drawThought(canvas, headX, headTop, w, color, thoughtShown);
        }

        if (listening) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 5f);
            stroke.setColor(color);
            stroke.setStrokeWidth(1.5f * density);
            stroke.setAlpha((int) (60 + 80 * pulse));
            canvas.drawCircle(cx, hy + hu * 0.1f, hu * (1.45f + 0.04f * pulse), stroke);
        }

        if (wire && dotsPhase != 0 && drawForm != null) {
            // the body's dots, building the drawing or flying home
            float alpha = dotsPhase == 2 ? 1f - humanFade : 1f;
            float gridAlpha = dotsPhase == 1 ? Math.min(1f, (clock - dotsSince) * 0.8f) : 0f;
            boolean solid = drawForm instanceof Forms.Object3D;
            nano.drawGrid(canvas, 0f, 14f, drawCx, drawCy, drawHalf, solid ? -1.15f : -1.25f, gridAlpha * 0.8f, density);
            drawNano.drawSwarm(canvas, drawSwarm, 0f, 0f, drawCx, drawCy + drawHalf * 0.35f, drawHalf, color, alpha, density, clock);
            if (sketch != null && dotsPhase == 1) {
                drawLabels(canvas, sketch, clock - sketchStart, drawCx, drawCy, drawHalf, color, 1f);
            }
        } else if (drawForm != null && drawForm != homeForm && mix > 0.05f) {
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

        // in the stage the captions, the thought and the download bar are particles; the dots look draws them flat
        feedDisplay(wire, color);
        if (subtitles && subtitle.length() > 0 && !wire) {
            int width = (int) (w - 48f * density);
            if (subLayout == null || subWidth != width) {
                subWidth = width;
                subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length(), subPaint, width)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            }
            canvas.save();
            float subBottom = h - padBottom - 22f * density;
            canvas.translate(24f * density, subBottom - subLayout.getHeight());
            subPaint.setAlpha(200);
            subLayout.draw(canvas);
            canvas.restore();
        }
    }

    private static int mixColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /**
     * A cloud-shaped thought bubble above and to the side of the head, with
     * three shrinking puffs leading down to it. Inside: the thought, or three
     * dots that pulse while the persona thinks.
     */
    private void drawThought(Canvas canvas, float headX, float headTop, float w, int color, float alpha) {
        float d = density;
        float bw = Math.min(w * 0.62f, 300f * d);
        float textW = bw * 0.72f;
        float bh;
        if (thought != null) {
            if (thoughtLayout == null || thoughtLayout.getWidth() != (int) textW) {
                thoughtPaint.setTextSize(14f * d);
                thoughtLayout = StaticLayout.Builder.obtain(thought, 0, thought.length(), thoughtPaint, (int) textW)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
            }
            bh = Math.max(64f * d, thoughtLayout.getHeight() + 40f * d);
        } else {
            bh = 58f * d;
            bw = Math.min(bw, 130f * d);
        }
        float bx = clamp(headX + bw * 0.22f, bw * 0.5f + 12f * d, w - bw * 0.5f - 12f * d);
        float by = Math.max(padTop + 24f * d + bh * 0.5f, headTop - bh * 0.75f - 26f * d);
        float rise = (1f - alpha) * 14f * d;
        by += rise;
        int a = (int) (255 * alpha);
        // the cloud: overlapping puffs around an oval, filled dark, outlined in the persona colour
        android.graphics.Path cloud = new android.graphics.Path();
        int puffs = 9;
        for (int i = 0; i < puffs; i++) {
            double t = Math.PI * 2 * i / puffs;
            float px = bx + (float) Math.cos(t) * bw * 0.42f;
            float py = by + (float) Math.sin(t) * bh * 0.36f;
            float r = Math.min(bw, bh) * (0.26f + 0.04f * (float) Math.sin(i * 1.7f + clock * 1.2f));
            cloud.addCircle(px, py, r, android.graphics.Path.Direction.CW);
        }
        cloud.addOval(bx - bw * 0.42f, by - bh * 0.36f, bx + bw * 0.42f, by + bh * 0.36f, android.graphics.Path.Direction.CW);
        cloud.setFillType(android.graphics.Path.FillType.WINDING);
        android.graphics.Path outline = new android.graphics.Path();
        outline.op(cloud, android.graphics.Path.Op.UNION);
        fill.setColor(0xFF05080B);
        fill.setAlpha((int) (225 * alpha));
        canvas.drawPath(cloud, fill);
        stroke.setColor(color);
        stroke.setStrokeWidth(1.6f * d);
        stroke.setAlpha((int) (200 * alpha));
        stroke.setShadowLayer(6f * d, 0f, 0f, color);
        canvas.drawPath(outline, stroke);
        // three puffs trailing down to the head
        float tx = headX + (bx - headX) * 0.35f, ty = headTop - 6f * d;
        for (int i = 0; i < 3; i++) {
            float f = (i + 1) / 4f;
            float px = tx + (bx - bw * 0.15f - tx) * f, py = ty + (by + bh * 0.42f - ty) * f;
            float r = (3f + 2.5f * i) * d;
            canvas.drawCircle(px, py, r, fill);
            canvas.drawCircle(px, py, r, stroke);
        }
        stroke.clearShadowLayer();
        if (thought != null && thoughtLayout != null) {
            thoughtPaint.setColor(color);
            thoughtPaint.setAlpha(a);
            canvas.save();
            canvas.translate(bx - thoughtLayout.getWidth() * 0.5f, by - thoughtLayout.getHeight() * 0.5f);
            thoughtLayout.draw(canvas);
            canvas.restore();
        } else {
            fill.setColor(color);
            for (int i = 0; i < 3; i++) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 5f - i * 0.9f);
                fill.setAlpha((int) (a * (0.35f + 0.65f * pulse)));
                canvas.drawCircle(bx + (i - 1) * 14f * d, by - pulse * 3f * d, (3f + pulse) * d, fill);
            }
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
        if (human != null && human.objectMode(showingIvie) && objectGesture(event)) {
            return true;
        }
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
                bodyTouchDown = false;
                if (wireOn()) {
                    // a finger on the body: a tap or a repeat touches it (holding still speaks, as anywhere)
                    ai.dvine.human.touch.BodyTouch t = human.pick(x, y, showingIvie, ai.dvine.human.touch.BodyTouch.Kind.TAP,
                            1, downAt, 0L);
                    if (t != null) {
                        bodyTouchDown = true;
                        touches.down(t.region, downAt);
                    }
                }
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = x - downX;
                float dy = y - downY;
                if (!decided && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    decided = true;
                    removeCallbacks(longPress);
                    boolean onHead = headHit.contains(downX, downY);
                    swipe = !onHead; // a drag on the head turns it; elsewhere a drag does nothing
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
                if (dragged || swipe || travel >= 14f * density) {
                    return true;
                }
                long now = event.getEventTime();
                // the second tap of a double tap: typing, and the first tap's action is dropped
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
                final Runnable tap;
                if (bodyTouchDown && human != null) {
                    long held = now - downAt;
                    ai.dvine.human.touch.BodyTouch.Kind k = touches.up(now);
                    final ai.dvine.human.touch.BodyTouch t = k == null ? null
                            : human.pick(x, y, showingIvie, k, touches.count(), now, held);
                    tap = t == null ? null : () -> {
                        human.react(t, showingIvie);
                        listener.onBodyTouch(t);
                    };
                } else {
                    final String part = bodySpace && zoom.value > 0.9f ? partAt(x, y) : null;
                    final boolean head = headHit.contains(x, y);
                    tap = part != null ? () -> listener.onBodyTap(part) : head ? () -> listener.onHeadTap() : null;
                }
                if (tap != null) {
                    pendingTap = () -> {
                        pendingTap = null;
                        tap.run();
                    };
                    postDelayed(pendingTap, DOUBLE_TAP_MS);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private float pinchStart, zoomStart, twoX, yawStart, twoY, pitchStart;
    private boolean twoFingers;

    /**
     * Inspecting an object: two fingers pinch to zoom and drag to turn or tilt
     * it; a one-finger tap selects the part under it. (Holding a finger still
     * speaks and a double tap types, handled by the caller.)
     */
    private boolean objectGesture(MotionEvent e) {
        ai.dvine.create.ObjectSession os = human.objects;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) {
                    twoFingers = true;
                    removeCallbacks(longPress);
                    pinchStart = spread(e);
                    zoomStart = os.zoom;
                    twoX = (e.getX(0) + e.getX(1)) / 2;
                    twoY = (e.getY(0) + e.getY(1)) / 2;
                    yawStart = os.yaw;
                    pitchStart = os.pitch;
                    os.spin = false;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (twoFingers && e.getPointerCount() >= 2) {
                    float k = spread(e) / Math.max(1f, pinchStart);
                    os.zoom = Math.max(0.4f, Math.min(2.5f, zoomStart * k));
                    float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                    os.yaw = yawStart + (mx - twoX) / density * 0.6f;
                    os.pitch = Math.max(-10f, Math.min(85f, pitchStart + (my - twoY) / density * 0.4f));
                    return true;
                }
                return false;
            case MotionEvent.ACTION_POINTER_UP:
                return true;
            case MotionEvent.ACTION_UP:
                if (twoFingers) {
                    twoFingers = false;
                    return true;
                }
                if (Math.hypot(e.getX() - downX, e.getY() - downY) < 14f * density && listener != null) {
                    removeCallbacks(longPress);
                    ai.dvine.create.Component c = human.pickObject(e.getX(), e.getY(), showingIvie);
                    if (c != null) {
                        listener.onObjectPart(c);
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    private static float spread(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    /** The body part under a screen point, from the dot nearest to it, or null. */
    private String partAt(float x, float y) {
        DotBody b = showingIvie ? ivieBody : obiBody;
        if (b == null || !bodyMode) {
            return null;
        }
        int n = swarm.active();
        if (tapScreen.length < n * 3) {
            tapScreen = new float[swarm.capacity * 3];
        }
        WireHead.project(swarm.pos, n, lastYaw, lastPitch, 0f, lastCx, lastCy, lastUnit, tapScreen);
        float best = (28f * density) * (28f * density);
        int hit = -1;
        for (int i = 0; i < n; i++) {
            float dx = tapScreen[i * 3] - x, dy = tapScreen[i * 3 + 1] - y;
            float d = dx * dx + dy * dy;
            // prefer the nearer surface when parts overlap on screen
            d -= tapScreen[i * 3 + 2] * 40f * density;
            if (d < best) {
                best = d;
                hit = i;
            }
        }
        return b.partAt(hit);
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
